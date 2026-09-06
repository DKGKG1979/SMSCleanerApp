package com.example.smscleaner
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity

class ComposeSmsActivity : AppCompatActivity()

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
