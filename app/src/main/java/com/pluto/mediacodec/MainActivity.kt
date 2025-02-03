package com.pluto.mediacodec

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.media.*
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private var permissionToRecordAccepted = false
    private val permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private var audioRecord: AudioRecord? = null
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private var isRecording = false
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val recordings = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

        listView = findViewById(R.id.listView)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, recordings)
        listView.adapter = adapter

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startRecording()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopRecording()
            convertPcmToWav()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val filePath = recordings[position].split(" | ")[0]
            playRecording(filePath)
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val filePath = recordings[position].split(" | ")[0]
            val outputFilePath = getExternalFilesDir(null)?.absolutePath + "/compressed_audio.m4a"
            compressAudio(filePath, outputFilePath)
            val file = File(outputFilePath)
            recordings.add("$outputFilePath | ${getFileSize(file)}")
            adapter.notifyDataSetChanged()
            true
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) finish()
    }


    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions()
            return
        }

        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
        audioRecord?.startRecording()
        isRecording = true

        val audioData = ByteArray(bufferSize)
        val outputStream = FileOutputStream(getExternalFilesDir(null)?.absolutePath + "/recording.pcm")

        Thread {
            while (isRecording) {
                val read = audioRecord?.read(audioData, 0, audioData.size) ?: 0
                if (read > 0) {
                    outputStream.write(audioData, 0, read)
                }
            }
            outputStream.close()
        }.start()
    }

    private fun stopRecording() {
        if (isRecording) {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isRecording = false

            val filePath = getExternalFilesDir(null)?.absolutePath + "/recording.pcm"
            val file = File(filePath)
            recordings.add("$filePath | ${getFileSize(file)}")
            adapter.notifyDataSetChanged()
        }
    }

    private fun getFileSize(file: File): String {
        val sizeInBytes = file.length()
        Log.d("sizeInBytes", sizeInBytes.toString())
        val sizeInKB = sizeInBytes / 1024
        val sizeInMB = sizeInKB / 1024
        return when {
            sizeInMB > 0 -> "${sizeInMB}MB"
            sizeInKB > 0 -> "${sizeInKB}KB"
            else -> "${sizeInBytes}B"
        }
    }


    fun compressAudio(inputFilePath: String, outputFilePath: String) {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFilePath)

            // Localiza a trilha de áudio
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    extractor.selectTrack(i)
                    break
                }
            }

            if (audioTrackIndex == -1) {
                throw RuntimeException("Nenhuma trilha de áudio encontrada no arquivo.")
            }

            // Configuração do MediaCodec para codificação AAC
            val outputFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                44100, // Taxa de amostragem
                2      // Número de canais
            )
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000) // Taxa de bits
            outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            // Configuração do MediaMuxer para saída
            val muxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var outputTrackIndex = -1

            // Buffers para processamento
            val inputBuffer = ByteBuffer.allocate(4096)
            val bufferInfo = MediaCodec.BufferInfo()

            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            var isEOS = false

            while (!isEOS) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0) {
                        isEOS = true
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, 0)
                        extractor.advance()
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val encodedData = codec.getOutputBuffer(outputBufferIndex)
                    if (encodedData != null && bufferInfo.size > 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        if (outputTrackIndex == -1) {
                            val newFormat = codec.outputFormat
                            outputTrackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                        }
                        muxer.writeSampleData(outputTrackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }
            }

            // Finaliza e libera recursos
            codec.stop()
            codec.release()
            extractor.release()
            muxer.stop()
            muxer.release()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pcmToWav(pcmFile: File, wavFile: File) {
        val sampleRate = 44100
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8

        val pcmData = pcmFile.readBytes()
        val wavData = ByteArray(44 + pcmData.size)

        // RIFF header
        wavData[0] = 'R'.toByte()
        wavData[1] = 'I'.toByte()
        wavData[2] = 'F'.toByte()
        wavData[3] = 'F'.toByte()

        val chunkSize = 36 + pcmData.size
        wavData[4] = (chunkSize and 0xff).toByte()
        wavData[5] = (chunkSize shr 8 and 0xff).toByte()
        wavData[6] = (chunkSize shr 16 and 0xff).toByte()
        wavData[7] = (chunkSize shr 24 and 0xff).toByte()

        // WAVE header
        wavData[8] = 'W'.toByte()
        wavData[9] = 'A'.toByte()
        wavData[10] = 'V'.toByte()
        wavData[11] = 'E'.toByte()

        // fmt subchunk
        wavData[12] = 'f'.toByte()
        wavData[13] = 'm'.toByte()
        wavData[14] = 't'.toByte()
        wavData[15] = ' '.toByte()

        val subChunk1Size = 16
        wavData[16] = (subChunk1Size and 0xff).toByte()
        wavData[17] = (subChunk1Size shr 8 and 0xff).toByte()
        wavData[18] = (subChunk1Size shr 16 and 0xff).toByte()
        wavData[19] = (subChunk1Size shr 24 and 0xff).toByte()

        val audioFormat = 1
        wavData[20] = (audioFormat and 0xff).toByte()
        wavData[21] = (audioFormat shr 8 and 0xff).toByte()

        wavData[22] = (channels and 0xff).toByte()
        wavData[23] = (channels shr 8 and 0xff).toByte()

        wavData[24] = (sampleRate and 0xff).toByte()
        wavData[25] = (sampleRate shr 8 and 0xff).toByte()
        wavData[26] = (sampleRate shr 16 and 0xff).toByte()
        wavData[27] = (sampleRate shr 24 and 0xff).toByte()

        wavData[28] = (byteRate and 0xff).toByte()
        wavData[29] = (byteRate shr 8 and 0xff).toByte()
        wavData[30] = (byteRate shr 16 and 0xff).toByte()
        wavData[31] = (byteRate shr 24 and 0xff).toByte()

        val blockAlign = (channels * 16) / 8
        wavData[32] = (blockAlign and 0xff).toByte()
        wavData[33] = (blockAlign shr 8 and 0xff).toByte()

        val bitsPerSample = 16
        wavData[34] = (bitsPerSample and 0xff).toByte()
        wavData[35] = (bitsPerSample shr 8 and 0xff).toByte()

        // data subchunk
        wavData[36] = 'd'.toByte()
        wavData[37] = 'a'.toByte()
        wavData[38] = 't'.toByte()
        wavData[39] = 'a'.toByte()

        val subChunk2Size = pcmData.size
        wavData[40] = (subChunk2Size and 0xff).toByte()
        wavData[41] = (subChunk2Size shr 8 and 0xff).toByte()
        wavData[42] = (subChunk2Size shr 16 and 0xff).toByte()
        wavData[43] = (subChunk2Size shr 24 and 0xff).toByte()

        System.arraycopy(pcmData, 0, wavData, 44, pcmData.size)

        wavFile.writeBytes(wavData)
    }


    private fun convertPcmToWav() {
        val pcmFile = File(getExternalFilesDir(null)?.absolutePath + "/recording.pcm")
        val wavFile = File(getExternalFilesDir(null)?.absolutePath + "/recording_${System.currentTimeMillis()}.wav")
        pcmToWav(pcmFile, wavFile)

        val file = File(wavFile.absolutePath)
        val displayText = "$file | ${getFileSize(file)} KB"
        recordings.add(displayText)
        adapter.notifyDataSetChanged()
    }

    private fun playRecording(filePath: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(filePath)
                prepare()
                start()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}