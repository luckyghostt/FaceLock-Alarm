package com.facelock.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AlarmRingingService : Service() {
    private var player: MediaPlayer? = null
    private val channelId = "facelock_alarm"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val openAlarm = PendingIntent.getActivity(
            this, 100, Intent(this, AlarmActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("FaceLock Alarm")
            .setContentText("Wake up — complete live face verification")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(openAlarm, true)
            .setContentIntent(openAlarm)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(77, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(77, notification)

        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingingService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) { }
        vibrateLoop()
    }

    private fun vibrateLoop() {
        val pattern = longArrayOf(0, 900, 250, 900, 250)
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(VibratorManager::class.java)
            manager.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(VIBRATOR_SERVICE) as Vibrator)
                .vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(channelId, "Alarm ringing", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Ongoing FaceLock alarm"
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        player?.runCatching { stop(); release() }
        player = null
        if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).cancel()
        else {
            @Suppress("DEPRECATION")
            (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
