package com.example.musiccalendarapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class dateListeningHistory : AppCompatActivity() {
    private lateinit var navCalendar: ImageButton
    private lateinit var navHome: ImageButton
    private lateinit var navLogout: ImageButton
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var selectedDate: String
    private lateinit var selectedDateLabel: TextView
    private lateinit var songRecyclerView: RecyclerView
    private lateinit var adapter: songAdapter
    private val songList = mutableListOf<playedTrack>()
    private lateinit var emptyMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        firebaseAuth = FirebaseAuth.getInstance()
        setContentView(R.layout.activity_date_listening_history)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        Log.d("DEBUG", "entering the date listening history display")
        navCalendar = findViewById(R.id.navCalendar)
        navLogout = findViewById(R.id.navLogout)
        navHome = findViewById(R.id.navHome)
        selectedDate = intent.getStringExtra("selectedDate")?: return
        selectedDateLabel = findViewById(R.id.selectedDateLabel)
        songRecyclerView = findViewById(R.id.trackRecyclerView)
        emptyMessage = findViewById(R.id.emptyMessage)

        adapter = songAdapter(songList)
        songRecyclerView.layoutManager = LinearLayoutManager(this)
        songRecyclerView.adapter = adapter

        val formattedText = getString(R.string.dateHistoryLabel, selectedDate)
        selectedDateLabel.text = formattedText

        val uid = firebaseAuth.currentUser?.uid
        if (selectedDate != null && uid != null) {
            lifecycleScope.launch {
                val tracks = fetchSongsForDate(uid, selectedDate)
                if (tracks.isEmpty()) {
                    emptyMessage.visibility = android.view.View.VISIBLE
                    songRecyclerView.visibility = android.view.View.GONE
                } else {
                    emptyMessage.visibility = android.view.View.GONE
                    songRecyclerView.visibility = android.view.View.VISIBLE
                    adapter.updateData(tracks)
                    adapter.notifyDataSetChanged()
                }

            }
        }

        navCalendar.setOnClickListener {
            val calendarIntent= Intent(this, calendarView::class.java)
            startActivity(calendarIntent)
        }

        navHome.setOnClickListener {
            val homeIntent = Intent(this, homePage::class.java)
            startActivity(homeIntent)
        }

        navLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val prefs = getSharedPreferences("loginPrefs", MODE_PRIVATE)
            prefs.edit().putBoolean("rememberMe", false).apply()

            val loginIntent = Intent(this, MainActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(loginIntent)
            finish()
        }
    }
    private suspend fun fetchSongsForDate(uid: String, date: String): List<playedTrack> =
        withContext(Dispatchers.IO) {
            val trackList = mutableListOf<playedTrack>()
            val ref = FirebaseDatabase.getInstance()
                .getReference("users/$uid/tracksByDay/$date")

            val snapshot = ref.get().await()
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val trackName = child.child("trackName").value as? String ?: continue
                    val artistName = child.child("artistName").value as? String ?: ""
                    val imageUrl = child.child("imageUrl").value as? String ?: ""
                    val playedAt = child.child("playedAt").value as? String ?: ""
                    trackList.add(playedTrack(trackName, artistName, imageUrl, playedAt))
                }
            }
            return@withContext trackList
        }

}