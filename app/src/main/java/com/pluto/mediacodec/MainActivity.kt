package com.pluto.mediacodec

import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var audioAdapter: AudioAdapter
    private val audioList = mutableListOf<AudioFile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_recorder)

        // Inicializa os botões
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Passa a função onItemClick que chama a compressão
        audioAdapter = AudioAdapter(audioList) { audioFile ->
            compressAudio(audioFile)
        }
        recyclerView.adapter = audioAdapter

        // Defina o caminho de armazenamento para o arquivo .m4a
        val outputDirectory = getExternalFilesDir(Environment.DIRECTORY_MUSIC)

        // Verifica se o diretório de saída existe, senão cria
        if (outputDirectory != null && !outputDirectory.exists()) {
            outputDirectory.mkdirs() // Cria o diretório se não existir
        }

        // Inicializa o arquivo de saída para gravação
        val timestamp = System.currentTimeMillis()
        outputFile = File(outputDirectory, "audio_$timestamp.m4a")

        // Iniciar gravação
        startButton.setOnClickListener {
            startRecording()
        }

        // Parar gravação
        stopButton.setOnClickListener {
            stopRecording()
        }
    }

    private fun startRecording() {
        val timestamp = System.currentTimeMillis()
        outputFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "audio_$timestamp.m4a")

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC) // Fonte de áudio (microfone)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // Formato de saída .m4a
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC) // Codec AAC para áudio comprimido
            setOutputFile(outputFile?.absolutePath) // Caminho do arquivo de saída
        }

        try {
            mediaRecorder?.prepare() // Prepara o MediaRecorder
            mediaRecorder?.start() // Inicia a gravação
            Toast.makeText(this, "Gravação iniciada", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao iniciar a gravação", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop() // Para a gravação
            release() // Libera os recursos
        }
        mediaRecorder = null

        // Verifica se o arquivo foi gravado corretamente
        if (outputFile?.exists() == true) {
            // Obtém o tamanho do arquivo em bytes
            val fileSize = outputFile?.length() ?: 0
            val audioFile = AudioFile(outputFile!!, fileSize)

            audioList.add(audioFile) // Adiciona o arquivo gravado à lista
            audioAdapter.notifyDataSetChanged() // Notifica o adapter para atualizar a lista
            Toast.makeText(this, "Gravação finalizada", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Falha ao gravar áudio", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        stopRecording() // Garante que a gravação seja parada quando a atividade parar
    }

    private fun compressAudio(audioFile: AudioFile) {
        val compressedFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "compressed_${audioFile.file.name}")

        if (AudioCompressor.compressAudio(audioFile.file, compressedFile)) {
            val compressedSize = compressedFile.length()
            val compressedAudioFile = AudioFile(compressedFile, compressedSize)

            audioList.remove(audioFile)
            audioList.add(compressedAudioFile)

            audioAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Compressão concluída! Novo tamanho: ${compressedSize / 1024} KB", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Erro ao comprimir áudio", Toast.LENGTH_SHORT).show()
        }
    }
}


data class AudioFile(
    val file: File,
    val size: Long // Tamanho do arquivo em bytes
)