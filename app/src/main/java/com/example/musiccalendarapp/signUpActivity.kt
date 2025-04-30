package com.example.musiccalendarapp

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.database
import java.io.IOException
import androidx.browser.customtabs.CustomTabsIntent

class signUpActivity : AppCompatActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var newUsername: EditText
    private lateinit var newPassword: EditText
    private lateinit var userEmail: EditText
    private lateinit var confirmNewPassword: EditText
    private lateinit var buttonCreateAccount: Button
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)
        firebaseAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        intent?.let { setIntent(it) }
        handleSpotifyRedirect(intent)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        newUsername = findViewById(R.id.newUsername)
        newPassword = findViewById(R.id.newPassword)
        confirmNewPassword = findViewById(R.id.confirmNewPassword)
        buttonCreateAccount = findViewById(R.id.buttonCreateAccount)
        userEmail = findViewById(R.id.userEmail)


        // i need to create a function that doesnt allow users to sign up if their two passwords in the password confirm don't match
        setupPasswordValidation()

        buttonCreateAccount.setOnClickListener{
            val inputtedUsername: String = newUsername.text.toString().trim()
            val inputtedPassword: String = newPassword.text.toString().trim()
            val inputtedEmail: String = userEmail.text.toString().trim()

            firebaseAuth.createUserWithEmailAndPassword(inputtedEmail,inputtedPassword).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val currUser = firebaseAuth.currentUser
                    if (currUser == null) {
                        Toast.makeText(this, "User not found after signup", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }
                    val uid = currUser.uid
                    val userData = mapOf(
                        "username" to inputtedUsername,
                        "email" to inputtedEmail
                    )
                    database.child("users").child(uid).setValue(userData)

                    Toast.makeText(this,"Created user: ${newUsername}",Toast.LENGTH_LONG).show()


                    val clientID = getString(R.string.spotifyClientID)
                    val redirectUri = "musiccalendarapp://callback"
                    val scopes = "user-read-recently-played user-read-playback-state user-read-currently-playing"

                    val authUri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
                        .appendQueryParameter("client_id", clientID)
                        .appendQueryParameter("response_type", "code")
                        .appendQueryParameter("redirect_uri", redirectUri)
                        .appendQueryParameter("scope", scopes)
                        .build()
                    // start the spotify OAuth
                    Log.d("DEBUG", authUri.toString())
                    Log.d("DEBUG", "bro moment")
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    customTabsIntent.launchUrl(this, authUri)
                    //startActivity(intent)

                } else {
                    val exception = task.exception
                    Toast.makeText(this, "Failed:$exception",Toast.LENGTH_LONG).show()
                    // make this a more informative thing that appears longer on the screen
                    // write instructions of how to write a valid password, that it needs to be this, this, and that according to using the firebase
                    // login stuff
                }
            }
            // insert it into the database with the user name shit too at least insert them into the users table so i can store them
            // uniquely to associate them with their calendar activity

        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSpotifyRedirect(intent)
    }

    private fun handleSpotifyRedirect(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.toString().startsWith("musiccalendarapp://callback")) {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                Log.d("DEBUG", "handleSpotifyRedirect and im getting some kind of code")
                Log.d("DEBUG", code.toString())
                exchangeCodeForToken(code)
            }
        }
    }

    private fun exchangeCodeForToken(code: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val redirectUri = "musiccalendarapp://callback"

        if (uid == null) {
            Log.e("SPOTIFY_DEBUG", "No Firebase user found.")
            return
        }

        val json = JSONObject().apply {
            put("code", code)
            put("redirect_uri", redirectUri)
            put("uid", uid)
        }

        Log.d("SPOTIFY_DEBUG", "Starting token exchange: $json")

        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://exchangespotifycode-cvxurm3g4q-uc.a.run.app")
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@signUpActivity, "Token exchange failed: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("SPOTIFY_DEBUG", "Spotify token exchange failed", e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("SPOTIFY_DEBUG", "Token exchange response: $responseBody")

                try {
                    val tokenJson = JSONObject(responseBody ?: "{}")

                    if (tokenJson.has("access_token")) {
                        val accessToken = tokenJson.getString("access_token")
                        val refreshToken = tokenJson.getString("refresh_token")
                        val fetchedAt = tokenJson.getString("fetched_at")

                        val tokensMap = mapOf(
                            "code" to code,
                            "access_token" to accessToken,
                            "refresh_token" to refreshToken,
                            "fetched_at" to fetchedAt
                        )

                        Firebase.database.reference
                            .child("users")
                            .child(uid)
                            .child("spotify_auth")
                            .setValue(tokensMap)

                        // this only runs when the firebase token has been successfully stored
                        runOnUiThread {
                            Toast.makeText(this@signUpActivity, "Spotify Connected!", Toast.LENGTH_LONG).show()
                            val redirectIntent = Intent(this@signUpActivity, MainActivity::class.java)
                            redirectIntent.putExtra("showDialog", true)
                            Log.d("DEBUG", "attempting to redirect to the login now")
                            startActivity(redirectIntent)
                            finish()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@signUpActivity, "Spotify token error.", Toast.LENGTH_LONG).show()
                            Log.e("SPOTIFY_DEBUG", "No access_token in response: $tokenJson")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@signUpActivity, "Token parsing error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    Log.e("SPOTIFY_DEBUG", "Failed to parse token response", e)
                }
            }
        })
    }

    private fun setupPasswordValidation() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val password = newPassword.text.toString()
                val confirm = confirmNewPassword.text.toString()

                if (password.isNotEmpty() && confirm.isNotEmpty()) {
                    if (password != confirm) {
                        confirmNewPassword.error = "Passwords do not match"
                        buttonCreateAccount.isEnabled = false
                    } else {
                        confirmNewPassword.error = null
                        buttonCreateAccount.isEnabled = true
                    }
                } else {
                    buttonCreateAccount.isEnabled = false
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        newPassword.addTextChangedListener(watcher)
        confirmNewPassword.addTextChangedListener(watcher)
    }


}