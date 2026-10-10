package com.navi.garmin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android NotificationListenerService intercepting turn-by-turn navigation updates
 * from Google Maps and Mapy.cz / Mapy.com.
 */
class MapsNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "MapsNotifService"
        const val MAPS_PACKAGE = "com.google.android.apps.maps"
        const val MAPY_CZ_PACKAGE = "cz.seznam.mapy"

        private const val NOTIFICATION_CHANNEL_ID = "garmin_navi_status_channel"
        private const val FOREGROUND_NOTIF_ID = 1001

        private val _currentNavData = MutableStateFlow<NavigationData?>(null)
        val currentNavData: StateFlow<NavigationData?> = _currentNavData.asStateFlow()

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        fun isSupportedPackage(packageName: String?): Boolean {
            if (packageName == null) return false
            val lower = packageName.lowercase()
            if (lower == MAPS_PACKAGE) return true
            if (lower.contains("mapy") || lower.contains("seznam")) return true
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Reads the current phone battery level as a percentage (0–100), or -1 on failure.
     */
    private fun getPhoneBatteryLevel(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return -1
        return (level * 100 / scale)
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
        if (sbn == null || !isSupportedPackage(sbn.packageName)) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val appName = if (sbn.packageName.lowercase().contains("mapy") || sbn.packageName.lowercase().contains("seznam")) {
            "Mapy.cz/Mapy.com"
        } else {
            "Google Maps"
        }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val ticker = notification.tickerText

        // Extract Icon info (resource name and/or bitmap graphic arrow)
        val (iconResName, iconBitmap) = extractIconInfo(sbn.packageName, notification)

        val rawDebugText = "[$appName] T: '$title' | X: '$text' | S: '$subText' | Icon: '${iconResName ?: (if (iconBitmap != null) "Bmp ${iconBitmap.width}x${iconBitmap.height}" else "Brak")}'"
        Log.d(TAG, rawDebugText)

        // Parse notification fields & graphic icon into structured NavigationData
        val navData = ManeuverParser.parse(title ?: ticker, text, subText, bigText, iconResName, iconBitmap)

        // Ignore empty or non-navigation alerts
        if (navData.hasRoute) {
            val enriched = navData.copy(
                phoneBattery = getPhoneBatteryLevel(),
                rawDebugText = rawDebugText
            )
            _currentNavData.value = enriched
            GarminBridge.sendNavigationUpdate(enriched)
            updateSystemNotification("Nawigacja ($appName): ${enriched.distance} - ${enriched.action.name.lowercase().replace('_', ' ')}")
        }
    }

    private fun extractIconInfo(pkgName: String, notification: Notification): Pair<String?, Bitmap?> {
        var resName: String? = null
        var bitmap: Bitmap? = null

        val icons = mutableListOf<Icon>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notification.getLargeIcon()?.let { icons.add(it) }
            notification.smallIcon?.let { icons.add(it) }
        }

        for (icon in icons) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (icon.type == Icon.TYPE_RESOURCE) {
                    try {
                        val resPackage = if (!icon.resPackage.isNullOrEmpty()) icon.resPackage else pkgName
                        val resId = icon.resId
                        if (resId != 0 && !resPackage.isNullOrEmpty()) {
                            val res = packageManager.getResourcesForApplication(resPackage)
                            resName = res.getResourceEntryName(resId)
                            if (!resName.isNullOrEmpty()) {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not resolve icon resource name: ${e.message}")
                    }
                }
            }

            if (bitmap == null) {
                try {
                    val pkgContext = try {
                        createPackageContext(pkgName, 0)
                    } catch (e: Exception) {
                        this
                    }
                    val drawable = icon.loadDrawable(pkgContext) ?: icon.loadDrawable(this)
                    if (drawable != null) {
                        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
                        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
                        val targetW = w.coerceAtMost(64)
                        val targetH = h.coerceAtMost(64)
                        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, targetW, targetH)
                        drawable.draw(canvas)
                        bitmap = bmp
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load icon bitmap: ${e.message}")
                }
            }
        }

        // Also check extras for EXTRA_LARGE_ICON Bitmap
        if (bitmap == null && notification.extras != null) {
            try {
                @Suppress("DEPRECATION")
                val extraBmp = notification.extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)
                if (extraBmp != null) {
                    bitmap = extraBmp
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return Pair(resName, bitmap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || !isSupportedPackage(sbn.packageName)) {
            return
        }

        Log.i(TAG, "Navigation notification removed (${sbn.packageName}). Resetting watch route state.")
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
