package com.example.musiccalendarapp

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class songAdapter(var songs: List<playedTrack>): RecyclerView.Adapter<songAdapter.ViewHolder>(){
    class ViewHolder(rootLayout: View): RecyclerView.ViewHolder(rootLayout){
        val songName: TextView = rootLayout.findViewById(R.id.songName)
        val songArtist: TextView = rootLayout.findViewById(R.id.artistName)
        val songImage: ImageView = rootLayout.findViewById(R.id.songImage)
        val playedAt: TextView = rootLayout.findViewById(R.id.playedAtTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        Log.d("source recycler view", "inside onCreate View Holder")
        val layoutInflater: LayoutInflater = LayoutInflater.from(parent.context)
        val rootLayout = layoutInflater.inflate(R.layout.songcardview, parent, false)

        return ViewHolder(rootLayout)
    }

    override fun getItemCount(): Int {
        return songs.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currTrack= songs[position]
        holder.songName.text = currTrack.trackName
        holder.songArtist.text = currTrack.artistName
        holder.playedAt.text = currTrack.playedAt

        if (!currTrack.imageUrl.isNullOrEmpty()) {
            Picasso.get()
                .setIndicatorsEnabled(true)
            Picasso.get().load(currTrack.imageUrl)
                .into(holder.songImage)
        }

        Log.d("recycler view", "inside onBindViewHolder at")
    }

    fun updateData(newSources: List<playedTrack>) {
        songs = newSources
    }
}