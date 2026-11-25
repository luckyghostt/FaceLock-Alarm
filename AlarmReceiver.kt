package com.facelock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Schedule the next occurrence so the selected alarm repeats daily.
        val prefs = context.getSharedPreferences("facelock", Context.MODE_PRIVATE)
        val hour = prefs.getInt("hour", 7)
        val minute = prefs.getInt("minute", 0)
        AlarmScheduler.scheduleDaily(context, hour, minute)

        val service = Intent(context, AlarmRingingService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(context, service)
    }
}
