package com.vigil.security

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.vigil.security.di.VigilContainer

class VigilApplication : Application() {

    lateinit var container: VigilContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = VigilContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                CHANNEL_SECURITY_ALERTS,
                "Security Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority link threat and phishing warnings"
                enableVibration(true)
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_STATUS,
                "Protection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background protection active state notification"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(alertChannel)
            notificationManager?.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_SECURITY_ALERTS = "vigil_threat_alerts"
        const val CHANNEL_SERVICE_STATUS = "vigil_service_status"
    }
}
