package com.example.musiccalendarapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var usernameText: EditText
    private lateinit var passwordText: EditText
    private lateinit var buttonLogin: Button
    private lateinit var buttonSignup: Button
    private lateinit var rememberMeCheck: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        firebaseAuth = FirebaseAuth.getInstance()

        usernameText = findViewById(R.id.usernameText)
        passwordText = findViewById(R.id.passwordText)
        buttonLogin = findViewById(R.id.buttonLogin)
        buttonSignup = findViewById(R.id.buttonSignup)
        rememberMeCheck = findViewById(R.id.rememberMeCheck)

        val sharedPrefs = getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)

        val savedEmail = sharedPrefs.getString("email", "")
        val savedPassword = sharedPrefs.getString("password", "")
        val remember = sharedPrefs.getBoolean("rememberMe", false)

        if (remember) {
            rememberMeCheck.isChecked = true
            usernameText.setText(savedEmail)
            passwordText.setText(savedPassword)
        }

        val showDialog = intent.getBooleanExtra("showDialog", false)
        if (showDialog) {
            AlertDialog.Builder(this)
                .setTitle("Account Created 🎉")
                .setMessage("Your Spotify is connected! Please log in to continue.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        usernameText.addTextChangedListener(textWatcher)
        passwordText.addTextChangedListener(textWatcher)

        buttonLogin.setOnClickListener {
            val email = usernameText.text.toString().trim()
            val password = passwordText.text.toString().trim()

            firebaseAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser ?: return@addOnCompleteListener
                    if (rememberMeCheck.isChecked) {
                        sharedPrefs.edit().apply {
                            putBoolean("rememberMe", true)
                            putString("email", email)
                            putString("password", password)
                            apply()
                        }
                    } else {
                        sharedPrefs.edit().clear().apply()
                    }

                    lifecycleScope.launch {
                        val token = ensureSpotifyAccessTokenIsValid(user.uid)
                        if (token != null) {
                            startActivity(Intent(this@MainActivity, homePage::class.java))
                        } else {
                            Toast.makeText(this@MainActivity, "Error getting Spotify token", Toast.LENGTH_LONG).show()
                        }
                    }

                } else {
                    Toast.makeText(this, "Login Failed: ${task.exception}", Toast.LENGTH_LONG).show()
                }
            }
        }

        buttonSignup.setOnClickListener {
            startActivity(Intent(this, signUpActivity::class.java))
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            buttonLogin.isEnabled = usernameText.text.isNotBlank() && passwordText.text.isNotBlank()
        }
        override fun afterTextChanged(s: Editable?) {}
    }

    suspend fun ensureSpotifyAccessTokenIsValid(uid: String): String? = withContext(Dispatchers.IO) {
        val dbRef = FirebaseDatabase.getInstance().getReference("users/$uid/spotify_auth")
        val snapshot = dbRef.get().await()

        val accessToken = snapshot.child("access_token").value as? String
        val refreshToken = snapshot.child("refresh_token").value as? String
        val expiresIn = snapshot.child("expires_in").value as? Long ?: 3600L
        val fetchedAt = snapshot.child("fetched_at").value as? Long ?: 0L

        val now = System.currentTimeMillis()
        val expirationTime = fetchedAt + (expiresIn * 1000)

        return@withContext if (now >= expirationTime) {
            val json = JSONObject().apply { put("refresh_token", refreshToken) }

            val request = Request.Builder()
                .url("https://refreshspotifytoken-cvxurm3g4q-uc.a.run.app")
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            try {
                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val tokenJson = JSONObject(body)

                val newAccessToken = tokenJson.getString("access_token")
                val newExpiresIn = tokenJson.optLong("expires_in", 3600)
                dbRef.child("access_token").setValue(newAccessToken)
                dbRef.child("expires_in").setValue(newExpiresIn)
                dbRef.child("fetched_at").setValue(System.currentTimeMillis())

                newAccessToken
            } catch (e: Exception) {
                Log.e("SpotifyAuth", "Refresh failed: ${e.message}")
                null
            }
        } else {
            accessToken
        }
    }
}
