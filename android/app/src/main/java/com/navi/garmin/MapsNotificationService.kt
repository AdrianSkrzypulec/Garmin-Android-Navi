package com.navi.garmin

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Android NotificationListenerService intercepting turn-by-turn navigation updates from Google Maps.
 */
class MapsNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "MapsNotifService"
        const val MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val NOTIFICATION_CHANNEL_ID = "garmin_navi_status_channel"
        private const val FOREGROUND_NOTIF_ID = 1001

        private val _currentNavData = MutableStateFlow<NavigationData?>(null)
        val currentNavData: StateFlow<NavigationData?> = _currentNavData.asStateFlow()

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListenerService connected and active.")
        _isServiceConnected.value = true
        // Initialize Garmin SDK bridge if not yet started
        GarminBridge.initialize(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "NotificationListenerService disconnected.")
        _isServiceConnected.value = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Garmin Maps Navi Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Utrzymuje aktywne połączenie Bluetooth z zegarkiem Garmin vívoactive 4"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateSystemNotification(statusText: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Garmin Maps Navi")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(FOREGROUND_NOTIF_ID, notification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != MAPS_PACKAGE) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Google Maps navigation notifications are ongoing notifications
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)

        Log.d(TAG, "Maps notification: Title='$title', Text='$text', SubText='$subText', BigText='$bigText'")

        // Parse notification fields into structured NavigationData
        val navData = ManeuverParser.parse(title, text, subText, bigText)

        // Ignore empty or non-navigation alerts (e.g. traffic in area without active route)
        if (navData.hasRoute) {
            _currentNavData.value = navData
            GarminBridge.sendNavigationUpdate(navData)
            updateSystemNotification("Nawigacja: ${navData.distance} - ${navData.action.name.lowercase().replace('_', ' ')}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != MAPS_PACKAGE) {
            return
        }

        Log.i(TAG, "Google Maps navigation notification was removed. Resetting watch route state.")
        val emptyData = NavigationData(
            action = ManeuverType.NONE,
            distance = "--",
            street = "",
            hasRoute = false
        )
        _currentNavData.value = emptyData
        GarminBridge.sendNavigationUpdate(emptyData)

        // Cancel status notification
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(FOREGROUND_NOTIF_ID)
    }
}

