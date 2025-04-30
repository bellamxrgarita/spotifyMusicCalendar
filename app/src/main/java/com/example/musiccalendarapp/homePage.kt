package com.example.musiccalendarapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


class homePage : AppCompatActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var trackImageView: ImageView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var navCalendar: ImageButton
    private lateinit var navHome: ImageButton
    private lateinit var navLogout: ImageButton
    private lateinit var usernameDisplay: TextView
    private lateinit var database: DatabaseReference
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_page)
        firebaseAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        navLogout = findViewById(R.id.navLogout)
        trackImageView = findViewById(R.id.trackImage)
        trackTitle = findViewById(R.id.songTitle)
        trackArtist = findViewById(R.id.songArtist)
        navCalendar = findViewById(R.id.navCalendar)
        navHome = findViewById(R.id.navHome)
        usernameDisplay = findViewById(R.id.usernameDisplay)
        swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        val currUser = firebaseAuth.currentUser
        // the current logged in user

        navCalendar.setOnClickListener {
            val calendarIntent= Intent(this, calendarView::class.java)
            startActivity(calendarIntent)
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

        if (currUser != null) {
            val currUID = currUser.uid

            lifecycleScope.launch {
                val username = fetchUsername(currUID)
                usernameDisplay.text = username
            }

            lifecycleScope.launch{
                Log.d("DEBUG", "polling live songs right now")
                val uid = currUID
                val token = ensureSpotifyAccessTokenIsValid(uid)
                // start polling and storing stuff
                if (token != null) {
                    startPollingMostRecentTrack(uid,token)

                } else {
                    Log.d("DEBUG","the accesss token is null and an error is occuring")
                    showSpotifyErrorDialog()
                }
            }

            // fallback display if the user isn't currently playing any music
            lifecycleScope.launch {
                val mostRecent = databaseFetchMostRecentlyPlayed(currUser.uid)
                if (mostRecent != null) {
                    trackTitle.text = mostRecent.trackName
                    trackArtist.text = mostRecent.artistName
                    Picasso.get().load(mostRecent.imageUrl).into(trackImageView)
                }
            }

            swipeRefreshLayout.setOnRefreshListener {
                lifecycleScope.launch {
                    Log.d("DEBUG", "swipe refresh most recently played song")
                    val uid = currUID
                    val token = ensureSpotifyAccessTokenIsValid(uid)
                    if (token != null) {
                        val apiManager = spotifyManager(token)
                        val newTrack = apiManager.fetchCurrentlyPlayingTrack()

                        Log.d("current track attempting to be displayed rn", newTrack.toString())
                        if (newTrack != null) {
                            trackTitle.text = newTrack.trackName
                            trackArtist.text = newTrack.artistName
                            if (!newTrack.imageUrl.isNullOrEmpty()) {
                                Picasso.get().load(newTrack.imageUrl).into(trackImageView)
                            } else {
                                // fetch the most recently played song from firebase this is the fallback display
                                val mostRecent = databaseFetchMostRecentlyPlayed(uid)
                                if (mostRecent != null) {
                                    trackTitle.text = mostRecent.trackName
                                    trackArtist.text = mostRecent.artistName
                                    Picasso.get().load(mostRecent.imageUrl).into(trackImageView)
                                }
                            }
                        }
                    } else {
                        Log.d("DEBUG", "the new track wasn't able to be fetched proper")
                        showSpotifyErrorDialog()
                    }
                    swipeRefreshLayout.isRefreshing = false
                }
            }

        }
    }

    suspend fun startPollingMostRecentTrack(uid: String, accessToken: String) {
        val apiManager = spotifyManager(accessToken)

        while (true) {
            try {
                val latestTrack = apiManager.fetchMostRecentTrack()
                if (latestTrack != null) {
                    val dateOnly = latestTrack.playedAt.substring(0, 10)
                    val timeOnly = latestTrack.playedAt.substring(11, 19)

                    val ref = FirebaseDatabase.getInstance()
                        .getReference("users/$uid/tracksByDay/$dateOnly")

                    val snapshot = ref.get().await()
                    val alreadyExists = snapshot.children.any {
                        it.child("playedAt").value == timeOnly
                    }

                    if (!alreadyExists) {
                        val trackMap = mapOf(
                            "trackName" to latestTrack.trackName,
                            "artistName" to latestTrack.artistName,
                            "imageUrl" to latestTrack.imageUrl,
                            "playedAt" to timeOnly,
                        )

                        ref.push().setValue(trackMap).await()
                        Log.d("DEBUG", "Inserted ${latestTrack.trackName}")
                    } else {
                        Log.d("DEBUG", "it's the same track as the one that was last stored")
                        Log.d("DEBUG", "Duplicate track skipped: ${latestTrack.trackName}")
                    }
                }

                delay(30_000L)
            } catch (e: Exception) {
                Log.e("Polling", "Error polling track: ${e.message}")
                delay(60_000L)
            }
        }
    }


    suspend fun databaseFetchMostRecentlyPlayed(uid:String): playedTrack?  = withContext(Dispatchers.IO) {
        val ref = FirebaseDatabase.getInstance()
            .getReference("users/$uid/tracksByDay")

        val dateSnapshot = ref.get().await()
        if (!dateSnapshot.exists()) return@withContext null

        // get the most recent date key
        val dateKeys = dateSnapshot.children.map { it.key!! }.sortedDescending()
        for (date in dateKeys) {
            val dayTracks = ref.child(date).get().await()
            val mostRecent = dayTracks.children.lastOrNull()
            if (mostRecent != null) {
                val trackName = mostRecent.child("trackName").value as? String ?: continue
                val artistName = mostRecent.child("artistName").value as? String ?: ""
                val imageUrl = mostRecent.child("imageUrl").value as? String ?: ""
                val playedAt = mostRecent.child("playedAt").value as? String ?: ""
                return@withContext playedTrack(trackName, artistName, imageUrl, playedAt)
            }
        }
        return@withContext null
    }

    // reuse this and always check this
    suspend fun ensureSpotifyAccessTokenIsValid(uid: String): String? = withContext(Dispatchers.IO) {
        val dbRef = FirebaseDatabase.getInstance().getReference("users/$uid/spotify_auth")
        val snapshot = dbRef.get().await()

        val accessToken = snapshot.child("access_token").value as? String
        val refreshToken = snapshot.child("refresh_token").value as? String
        val expiresIn = (snapshot.child("expires_in").value as? Long) ?: 3600L
        val fetchedAt = (snapshot.child("fetched_at").value as? Long) ?: 0L

        // current time in millis
        val now = System.currentTimeMillis()
        val expirationTime = fetchedAt + (expiresIn * 1000)

        return@withContext if (now >= expirationTime) {
            // token expired need to refresh it
            val json = JSONObject().apply {
                put("refresh_token", refreshToken)
            }

            val request = Request.Builder()
                .url("https://refreshspotifytoken-cvxurm3g4q-uc.a.run.app") // swap this with your real endpoint
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            try {
                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null

                val responseJson = JSONObject(body)
                val newAccessToken = responseJson.getString("access_token")
                val newExpiresIn = responseJson.optLong("expires_in", 3600)
                Log.d("DEBUG", "inside the try catch that is updating the access_token")
                // update access token in firebase
                dbRef.child("access_token").setValue(newAccessToken)
                dbRef.child("expires_in").setValue(newExpiresIn)
                dbRef.child("fetched_at").setValue(System.currentTimeMillis())

                newAccessToken
            } catch (e: Exception) {
                Log.e("SpotifyAuth", "Refresh failed: ${e.message}")
                null
            }
        } else {
            // Token is still valid
            Log.d("DEBUG", "Don't need to update the access token")
            accessToken
        }
    }

    private fun showSpotifyErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("Spotify Error")
            .setMessage("We couldn’t access your Spotify information at this time. Please try again later.")
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    suspend fun fetchUsername(uid: String): String? = withContext(Dispatchers.IO) {
        val ref = FirebaseDatabase.getInstance()
            .getReference("users/$uid/username")

        val snapshot = ref.get().await()
        return@withContext snapshot.getValue(String::class.java)
    }
}