package com.pluto.mediacodec

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat

class AudioAdapter(
    private val audioList: MutableList<AudioFile>,
    private val onItemClick: (AudioFile) -> Unit
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.audio_item, parent, false)
        return AudioViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val audioFile = audioList[position]
        holder.fileName.text = "${audioFile.file.name} (${formatFileSize(audioFile.size)})"

        holder.itemView.setOnClickListener {
            onItemClick(audioFile)
        }
    }

    override fun getItemCount(): Int = audioList.size

    private fun formatFileSize(size: Long): String {
        val format = DecimalFormat("#.##")
        return when {
            size >= 1024 * 1024 -> format.format(size / (1024.0 * 1024.0)) + " MB"
            size >= 1024 -> format.format(size / 1024.0) + " KB"
            else -> format.format(size) + " B"
        }
    }

    inner class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.fileName)
    }
}
