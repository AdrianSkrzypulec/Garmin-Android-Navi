package com.navi.garmin

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.navi.garmin.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPermissionSection()
        setupGarminSection()
        setupLiveUpdates()
        setupSimulatorSection()
        checkBluetoothPermissions()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupPermissionSection() {
        binding.btnGrantPermission.setOnClickListener {
            // Open Android Notification Access settings
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val isGranted = isNotificationServiceEnabled()
        if (isGranted) {
            binding.tvPermissionTitle.text = getString(R.string.permission_granted)
            binding.tvPermissionTitle.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.tvPermissionDesc.text = "Usługa nasłuchuje powiadomień nawigacji Google Maps."
            binding.btnGrantPermission.visibility = View.GONE
        } else {
            binding.tvPermissionTitle.text = getString(R.string.permission_title)
            binding.tvPermissionTitle.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            binding.tvPermissionDesc.text = getString(R.string.permission_desc)
            binding.btnGrantPermission.visibility = View.VISIBLE
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun setupGarminSection() {
        GarminBridge.initialize(this)

        binding.btnRefreshGarmin.setOnClickListener {
            GarminBridge.discoverAndConnectDevices()
            Toast.makeText(this, "Wyszukiwanie zegarków Garmin...", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            GarminBridge.connectionState.collectLatest { state ->
                binding.tvGarminStatus.text = state
            }
        }
    }

    private fun setupLiveUpdates() {
        lifecycleScope.launch {
            MapsNotificationService.currentNavData.collectLatest { nav ->
                if (nav != null && nav.hasRoute) {
                    binding.tvLiveStatus.text = "Aktywna nawigacja Google Maps"
                    binding.tvLiveAction.text = "Manewr: ${nav.action}"
                    binding.tvLiveDistance.text = "Dystans: ${nav.distance}"
                    binding.tvLiveStreet.text = "Ulica: ${nav.street}"
                    binding.tvLiveTripInfo.text = "ETA: ${nav.eta} • Dystans: ${nav.remainDist} (${nav.remainTime})"
                } else {
                    binding.tvLiveStatus.text = "Oczekiwanie na uruchomienie nawigacji w Google Maps..."
                    binding.tvLiveAction.text = "Manewr: --"
                    binding.tvLiveDistance.text = "Dystans: --"
                    binding.tvLiveStreet.text = "Ulica: --"
                    binding.tvLiveTripInfo.text = "ETA: -- • Dystans całkowity: --"
                }
            }
        }
    }

    private fun setupSimulatorSection() {
        binding.btnSimStraight.setOnClickListener {
            val data = NavigationData(
                action = ManeuverType.STRAIGHT,
                distance = "500 m",
                street = "Al. Jerozolimskie",
                eta = "14:45",
                remainDist = "4.2 km",
                remainTime = "10 min",
                hasRoute = true
            )
            sendSimulated(data)
        }

        binding.btnSimTurnLeft.setOnClickListener {
            val data = NavigationData(
                action = ManeuverType.TURN_LEFT,
                distance = "150 m",
                street = "ul. Marszałkowska",
                eta = "14:45",
                remainDist = "3.8 km",
                remainTime = "9 min",
                hasRoute = true
            )
            sendSimulated(data)
        }

        binding.btnSimTurnRight.setOnClickListener {
            val data = NavigationData(
                action = ManeuverType.TURN_RIGHT,
                distance = "200 m",
                street = "ul. Świętokrzyska",
                eta = "14:44",
                remainDist = "3.5 km",
                remainTime = "8 min",
                hasRoute = true
            )
            sendSimulated(data)
        }

        binding.btnSimRoundabout.setOnClickListener {
            val data = NavigationData(
                action = ManeuverType.ROUNDABOUT,
                distance = "100 m",
                street = "Rondo Dmowskiego",
                exitNumber = 2,
                eta = "14:42",
                remainDist = "2.8 km",
                remainTime = "6 min",
                hasRoute = true
            )
            sendSimulated(data)
        }

        binding.btnSimDestination.setOnClickListener {
            val data = NavigationData(
                action = ManeuverType.DESTINATION,
                distance = "0 m",
                street = "Miejsce docelowe",
                eta = "14:40",
                remainDist = "0 m",
                remainTime = "0 min",
                hasRoute = true
            )
            sendSimulated(data)
        }

        binding.btnSimClear.setOnClickListener {
            val data = NavigationData(
                action = ManeuverType.NONE,
                distance = "--",
                street = "",
                hasRoute = false
            )
            sendSimulated(data)
        }
    }

    private fun sendSimulated(data: NavigationData) {
        val battery = getPhoneBatteryLevel()
        val enriched = if (data.hasRoute) data.copy(phoneBattery = battery) else data
        GarminBridge.sendNavigationUpdate(enriched)
        Toast.makeText(this, "Wysłano: ${data.action} (${data.distance}) • Bateria: $battery%", Toast.LENGTH_SHORT).show()
    }

    private fun getPhoneBatteryLevel(): Int {
        return try {
            val intent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level < 0 || scale <= 0) -1 else (level * 100 / scale)
        } catch (e: Exception) {
            -1
        }
    }

    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val needed = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
            }
        }
    }
}

