package com.example.musiccalendarapp

import android.content.Intent
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.widget.CalendarView
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class calendarView : AppCompatActivity() {
    private lateinit var navCalendar: ImageButton
    private lateinit var navHome: ImageButton
    private lateinit var navLogout: ImageButton
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var calendarView: CalendarView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        firebaseAuth = FirebaseAuth.getInstance()
        setContentView(R.layout.activity_calendar_view)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        navCalendar = findViewById(R.id.navCalendar)
        navHome = findViewById(R.id.navHome)
        navLogout = findViewById(R.id.navLogout)
        calendarView = findViewById<CalendarView>(R.id.calendarView)
        calendarView.date = System.currentTimeMillis()
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

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
            val intent = Intent(this, dateListeningHistory::class.java)
            intent.putExtra("selectedDate", selectedDate)
            Log.d("DEBUG", "user selected this date {$selectedDate}")
            startActivity(intent)
        }

    }

}