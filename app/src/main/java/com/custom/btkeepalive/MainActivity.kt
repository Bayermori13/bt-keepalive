package com.custom.btkeepalive

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.custom.btkeepalive.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val logBuilder = StringBuilder()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    addLog("دستگاه بلوتوث متصل شد.")
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    addLog("دستگاه بلوتوث قطع شد!")
                }
            }
            updateConnectedDevice()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        addLog("برنامه آماده‌سازی شد.")
        requestPermissions()
        updateConnectedDevice()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        binding.switchKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            val intent = Intent(this, KeepAliveService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                binding.tvStatus.text = "وضعیت: فعال (در حال ارسال پالس امن)"
                binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#38BDF8"))
                addLog("موتور ضد قطعی روشن شد.")
            } else {
                stopService(intent)
                binding.tvStatus.text = "وضعیت: غیرفعال"
                binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                addLog("موتور ضد قطعی خاموش شد.")
            }
        }
    }

    private fun addLog(message: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logBuilder.append("[$timeStr] $message\n")
        binding.tvLogs.text = logBuilder.toString()
    }

    private fun updateConnectedDevice() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            binding.tvDeviceName.text = "بلوتوث گوشی خاموش است"
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            binding.tvDeviceName.text = "نیازمند دسترسی بلوتوث"
            return
        }

        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                val devices = proxy?.connectedDevices
                if (!devices.isNullOrEmpty()) {
                    val name = devices[0].name ?: "ناشناس"
                    binding.tvDeviceName.text = name
                    addLog("شناسایی دستگاه: $name")
                } else {
                    binding.tvDeviceName.text = "هیچ هندزفری فعالی متصل نیست"
                }
                adapter.closeProfileProxy(profile, proxy)
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
    }
}
