package com.onesignal.example

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

import com.onesignal.OneSignal

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        OneSignal.idsAvailable { userId, registrationId ->
            var text = "OneSignal UserID:\n$userId\n\n"

            text += if (registrationId != null)
                "Google Registration Id:\n$registrationId"
            else
                "Google Registration Id:\nCould not subscribe for push"

            val textView: TextView = findViewById(R.id.debug_view)
            textView.text = text
        }
    }
}
