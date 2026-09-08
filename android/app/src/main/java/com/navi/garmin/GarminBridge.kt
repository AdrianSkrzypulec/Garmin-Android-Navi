package com.navi.garmin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.ConnectIQ.IQConnectType
import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Custom ContextWrapper intercepting registerReceiver calls from older SDKs
 * to automatically append RECEIVER_EXPORTED flag required on Android 14+ (API 34+).
 */
private class ExportedReceiverContextWrapper(base: Context) : ContextWrapper(base) {

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter ?: IntentFilter(),
                ContextCompat.RECEIVER_EXPORTED
            )
        } else {
            super.registerReceiver(receiver, filter)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        flags: Int
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasExportFlag = (flags and Context.RECEIVER_EXPORTED != 0) ||
                                (flags and Context.RECEIVER_NOT_EXPORTED != 0)
            val updatedFlags = if (!hasExportFlag) flags or Context.RECEIVER_EXPORTED else flags
            super.registerReceiver(receiver, filter, updatedFlags)
        } else {
            super.registerReceiver(receiver, filter, flags)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(
                receiver,
                filter,
                broadcastPermission,
                scheduler,
                Context.RECEIVER_EXPORTED
            )
        } else {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?,
        flags: Int
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasExportFlag = (flags and Context.RECEIVER_EXPORTED != 0) ||
                                (flags and Context.RECEIVER_NOT_EXPORTED != 0)
            val updatedFlags = if (!hasExportFlag) flags or Context.RECEIVER_EXPORTED else flags
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler, updatedFlags)
        } else {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags)
        }
    }
}

/**
 * Manages communication between Android and the Garmin vívoactive 4 watch app via Connect IQ Mobile SDK.
 */
object GarminBridge {

    private const val TAG = "GarminBridge"

    // Matches the application UUID in garmin/manifest.xml
    const val GARMIN_APP_UUID = "c7e29789-9a25-4c07-b371-d648b29df920"

    private var connectIQ: ConnectIQ? = null
    private var targetApp: IQApp? = null
    private var connectedDevice: IQDevice? = null
    private var appContext: Context? = null

    @Volatile
    private var isSdkReady = false

    @Volatile
    private var isInitializing = false

    private val _connectionState = MutableStateFlow("Niepołączono")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _lastSentData = MutableStateFlow<NavigationData?>(null)
    val lastSentData: StateFlow<NavigationData?> = _lastSentData.asStateFlow()

    // Throttling control
    private var lastSentTimestamp: Long = 0
    private var lastSentAction: ManeuverType = ManeuverType.NONE
    private const val THROTTLE_INTERVAL_MS = 800L

    /**
     * Initializes the Garmin Connect IQ SDK.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext

        if (isSdkReady) {
            Log.d(TAG, "Garmin SDK is already ready.")
            discoverAndConnectDevices()
            return
        }

        if (isInitializing) {
            Log.d(TAG, "Garmin SDK initialization is already in progress...")
            _connectionState.value = "Łączenie z usługą Garmin Connect Mobile..."
            return
        }

        isInitializing = true
        _connectionState.value = "Inicjalizacja Garmin SDK..."

        try {
            targetApp = IQApp(GARMIN_APP_UUID)
            val wrappedContext = ExportedReceiverContextWrapper(context.applicationContext)
            val ciq = ConnectIQ.getInstance(wrappedContext, IQConnectType.WIRELESS)
            connectIQ = ciq

            ciq.initialize(wrappedContext, true, object : ConnectIQListener {
                override fun onSdkReady() {
                    Log.i(TAG, "Garmin Connect IQ SDK Ready.")
                    isSdkReady = true
                    isInitializing = false
                    _connectionState.value = "SDK gotowe - szukanie zegarka..."
                    discoverAndConnectDevices()
                }

                override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                    Log.e(TAG, "Garmin SDK Init Error: $errStatus")
                    isSdkReady = false
                    isInitializing = false

                    val message = when (errStatus) {
                        ConnectIQ.IQSdkErrorStatus.GCM_NOT_INSTALLED ->
                            "Brak aplikacji Garmin Connect! Zainstaluj ją ze Sklepu Play."
                        ConnectIQ.IQSdkErrorStatus.GCM_UPGRADE_NEEDED ->
                            "Wymagana aktualizacja aplikacji Garmin Connect w Sklepie Play."
                        ConnectIQ.IQSdkErrorStatus.SERVICE_ERROR ->
                            "Błąd usługi Garmin Connect Mobile. Otwórz aplikację Garmin Connect."
                        else -> "Błąd inicjalizacji SDK: $errStatus"
                    }
                    _connectionState.value = message
                }

                override fun onSdkShutDown() {
                    Log.i(TAG, "Garmin SDK Shut Down")
                    isSdkReady = false
                    isInitializing = false
                    _connectionState.value = "SDK Garmin wyłączone"
                }
            })
        } catch (e: NoClassDefFoundError) {
            isSdkReady = false
            isInitializing = false
            Log.w(TAG, "Garmin Connect IQ SDK not found in classpath. Operating in simulation mode.")
            _connectionState.value = "Tryb symulatora (brak ConnectIQ SDK JAR)"
        } catch (e: Exception) {
            isSdkReady = false
            isInitializing = false
            Log.e(TAG, "Error initializing GarminBridge: ${e.message}", e)
            _connectionState.value = "Błąd inicjalizacji: ${e.message}"
        }
    }

    /**
     * Finds paired Garmin devices and registers for device status.
     */
    fun discoverAndConnectDevices() {
        if (!isSdkReady) {
            Log.w(TAG, "Cannot discover devices: SDK is not ready yet.")
            val ctx = appContext
            if (ctx != null && !isInitializing) {
                initialize(ctx)
            } else if (isInitializing) {
                _connectionState.value = "Oczekiwanie na połączenie z Garmin Connect Mobile..."
            } else {
                _connectionState.value = "SDK niegotowe. Uruchom ponownie aplikację."
            }
            return
        }

        val ciq = connectIQ
        if (ciq == null) {
            _connectionState.value = "Brak instancji SDK"
            return
        }

        try {
            val devices = ciq.knownDevices ?: emptyList()
            if (devices.isEmpty()) {
                Log.w(TAG, "No paired Garmin devices found in Garmin Connect Mobile.")
                _connectionState.value = "Brak sparowanych zegarków w Garmin Connect"
                return
            }

            // Pick the first available device or preferred vívoactive 4
            val device = devices.firstOrNull { it.friendlyName.contains("vivoactive", ignoreCase = true) }
                ?: devices.first()

            connectedDevice = device

            val status = try {
                ciq.getDeviceStatus(device)
            } catch (e: Exception) {
                null
            }

            if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                _connectionState.value = "Połączono z ${device.friendlyName}"
            } else {
                _connectionState.value = "Zegarek: ${device.friendlyName} (${status?.name ?: "niepołączony"})"
            }

            // Register for device events (connect/disconnect)
            ciq.registerForDeviceEvents(device, object : IQDeviceEventListener {
                override fun onDeviceStatusChanged(iqDevice: IQDevice, status: IQDevice.IQDeviceStatus) {
                    Log.i(TAG, "Device status: ${iqDevice.friendlyName} -> $status")
                    if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                        _connectionState.value = "Połączono z ${iqDevice.friendlyName}"
                    } else {
                        _connectionState.value = "Zegarek: ${iqDevice.friendlyName} (${status.name})"
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering devices: ${e.message}", e)
            _connectionState.value = "Błąd wyszukiwania: ${e.message}"
        }
    }

    /**
     * Transmits navigation update to Garmin vívoactive 4.
     */
    fun sendNavigationUpdate(data: NavigationData) {
        val now = System.currentTimeMillis()

        // Throttling: If maneuver is the same and interval < 800ms, skip to save Bluetooth bandwidth
        val isActionChanged = data.action != lastSentAction
        if (!isActionChanged && (now - lastSentTimestamp) < THROTTLE_INTERVAL_MS) {
            return
        }

        lastSentTimestamp = now
        lastSentAction = data.action
        _lastSentData.value = data

        if (!isSdkReady) {
            Log.d(TAG, "SDK not ready, skipping watch transmission: ${data.action} ${data.distance}")
            return
        }

        val ciq = connectIQ
        val device = connectedDevice
        val app = targetApp

        if (ciq == null || device == null || app == null) {
            Log.d(TAG, "Simulation mode update: ${data.action} ${data.distance} -> ${data.street}")
            return
        }

        try {
            val payload = data.toGarminMap()
            ciq.sendMessage(device, app, payload) { _, _, status ->
                if (status == IQMessageStatus.SUCCESS) {
                    Log.d(TAG, "Message delivered to Garmin watch: ${data.action} ${data.distance}")
                } else {
                    Log.w(TAG, "Failed sending to Garmin watch, status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception transmitting to Garmin watch: ${e.message}", e)
        }
    }
}


