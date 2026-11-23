package ir.homeacs.emascanner

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var volumeInput: EditText
    private lateinit var toleranceInput: EditText

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusView.text = intent?.getStringExtra(ScannerService.EXTRA_STATUS) ?: statusView.text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        buildUi()
        val saved = getSharedPreferences("scanner_settings", MODE_PRIVATE)
        volumeInput.setText(saved.getString("volume_btc", "200"))
        toleranceInput.setText(saved.getString("tolerance_pct", "0.15"))
        statusView.text = getSharedPreferences("scanner_state", MODE_PRIVATE)
            .getString("last_status", "هنوز اسکن انجام نشده است.")
        // User asked for scanning to begin whenever the app is opened.
        startScannerService()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ScannerService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onStop()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(18))
            layoutDirection = ViewGroup.LAYOUT_DIRECTION_RTL
            setBackgroundColor(0xFFF5F7FA.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(TextView(this).apply {
            text = "اسکنر EMA هومکس"
            textSize = 25f
            setTextColor(0xFF12243A.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = "Bitunix Futures בלבד"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFF52708A.toInt())
            setPadding(0, 0, 0, dp(18))
        })

        volumeInput = field(content, "حداقل حجم ۲۴ ساعته (BTC)", "200")
        toleranceInput = field(content, "فاصله مجاز قیمت تا EMA200 (درصد)", "0.15")

        content.addView(TextView(this).apply {
            text = "قوانین: EMA50 بالاتر از EMA200 در ۱h و ۱۵m = LONG؛ EMA50 پایین‌تر از EMA200 در هر دو = SHORT. سپس برخورد قیمت با EMA200 در ۱۵m هشدار می‌دهد."
            textSize = 14f
            setTextColor(0xFF26384A.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFE7EEF5.toInt())
        })

        val start = Button(this).apply {
            text = "شروع اسکن پس‌زمینه"
            setOnClickListener {
                saveSettings()
                startScannerService()
                statusView.text = "سرویس اسکن در حال راه‌اندازی است..."
            }
        }
        content.addView(start, buttonParams())

        val stop = Button(this).apply {
            text = "توقف اسکن"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScannerService::class.java))
                statusView.text = "اسکن متوقف شد."
            }
        }
        content.addView(stop, buttonParams())

        content.addView(TextView(this).apply {
            text = "وضعیت و گزارش آخرین اسکن"
            textSize = 18f
            setTextColor(0xFF12243A.toInt())
            setPadding(0, dp(20), 0, dp(8))
        })
        statusView = TextView(this).apply {
            text = "آماده"
            textSize = 13f
            setTextColor(0xFF26384A.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        content.addView(statusView)

        setContentView(scroll)
    }

    private fun field(parent: LinearLayout, label: String, default: String): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(0xFF26384A.toInt())
            setPadding(0, dp(8), 0, dp(4))
        })
        val edit = EditText(this).apply {
            setSingleLine(true)
            setText(default)
            textSize = 16f
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        parent.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        return edit
    }

    private fun startScannerService() {
        val intent = Intent(this, ScannerService::class.java).setAction(ScannerService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun saveSettings() {
        val volume = volumeInput.text.toString().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 200.0
        val tolerance = toleranceInput.text.toString().toDoubleOrNull()?.coerceIn(0.01, 2.0) ?: 0.15
        getSharedPreferences("scanner_settings", MODE_PRIVATE).edit()
            .putString("volume_btc", volume.toString())
            .putString("tolerance_pct", tolerance.toString())
            .apply()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
    }

    private fun buttonParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
    ).apply { topMargin = dp(10) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
