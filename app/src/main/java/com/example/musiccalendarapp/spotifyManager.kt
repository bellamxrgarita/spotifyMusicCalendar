package com.example.musiccalendarapp
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject

class spotifyManager(private val accessToken:String){
    private lateinit var okHttpClient: OkHttpClient

    init{
        val builder=OkHttpClient.Builder()
        val loggingInterceptor=HttpLoggingInterceptor()
        loggingInterceptor.level= HttpLoggingInterceptor.Level.BODY
        builder.addInterceptor(loggingInterceptor)
        okHttpClient=builder.build()
        Log.d("SpotifyApiManager", "Initialized with token")
    }

    // have this for currenlty playing track for the home page display
    suspend fun fetchCurrentlyPlayingTrack(): currentTrack = withContext(Dispatchers.IO)  {
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/player/currently-playing")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val response:Response = okHttpClient.newCall(request).execute()
        val responseBody=response.body?.string()
        if (!response.isSuccessful) {
            Log.d("spotify manager", "the api call failed")
        }

        if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
            val json = JSONObject(responseBody)
            val trackInfo = json.getJSONObject("item")
            val trackName = trackInfo.getString("name")

            val artistsArray = trackInfo.getJSONArray("artists")
            val artistNames = mutableListOf<String>()
            for (i in 0 until artistsArray.length()) {
                artistNames.add(artistsArray.getJSONObject(i).getString("name"))
            }

            val artistName = artistNames.joinToString(", ")
            val trackImage = trackInfo
                .getJSONObject("album")
                .getJSONArray("images")
                .getJSONObject(0)
                .getString("url")

            return@withContext currentTrack(
                trackName = trackName,
                artistName = artistName,
                imageUrl = trackImage,
            )
        }

        return@withContext currentTrack()
    }


    suspend fun fetchMostRecentTrack(): playedTrack = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/player/recently-played?limit=1")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val response:Response = okHttpClient.newCall(request).execute()
        val responseBody=response.body?.string()
        if (!response.isSuccessful) {
            Log.d("spotify manager", "the api call failed")
        }
        if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
            val json = JSONObject(responseBody)
            val rawTrack = json.getJSONArray("items").getJSONObject(0)
            val playedAt = rawTrack.getString("played_at")

            val trackInfo = rawTrack.getJSONObject("track")

            val trackName = trackInfo.getString("name")
            val artistsArray = trackInfo.getJSONArray("artists")
            val artistNames = mutableListOf<String>()
            for (i in 0 until artistsArray.length()) {
                artistNames.add(artistsArray.getJSONObject(i).getString("name"))
            }

            val artistName = artistNames.joinToString(", ")
            val trackImage = trackInfo.getJSONObject("album")
                .getJSONArray("images")
                .getJSONObject(0)
                .getString("url")

            return@withContext playedTrack(
                trackName = trackName,
                artistName = artistName,
                imageUrl = trackImage,
                playedAt = playedAt
            )
        }
        return@withContext playedTrack()
    }


}