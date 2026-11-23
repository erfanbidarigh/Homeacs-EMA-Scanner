package ir.homeacs.emascanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScannerService : Service() {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private lateinit var engine: ScannerEngine
    private var scheduled = false

    override fun onCreate() {
        super.onCreate()
        engine = ScannerEngine(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startAsForeground()
                if (!scheduled) {
                    scheduled = true
                    executor.scheduleWithFixedDelay({ runScan() }, 0, 30, TimeUnit.SECONDS)
                }
            }
        }
        return START_STICKY
    }

    private fun runScan() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HomeacsEmaScanner:scan")
        try {
            lock.acquire(120_000L)
            val prefs = getSharedPreferences("scanner_settings", MODE_PRIVATE)
            val threshold = prefs.getString("volume_btc", "200")?.toDoubleOrNull() ?: 200.0
            val tolerance = prefs.getString("tolerance_pct", "0.15")?.toDoubleOrNull() ?: 0.15
            val result = engine.scan(threshold, tolerance)
            val summary = result.summary
            val status = buildString {
                append("آخرین اسکن: ")
                append(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
                append("\nBitunix: ${summary.checked} نماد بررسی شد؛ ${summary.eligible} نماد از فیلتر حجم عبور کرد.")
                append("\nسیگنال جدید: ${summary.signals.size}")
                append("\n")
                append(result.status)
            }
            getSharedPreferences("scanner_state", MODE_PRIVATE).edit()
                .putString("last_status", status).putLong("last_scan_ms", System.currentTimeMillis()).apply()
            sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).putExtra(EXTRA_STATUS, status))

            summary.signals.forEachIndexed { index, signal ->
                showSignalNotification(signal, index)
            }
            updateForegroundNotification("در حال اسکن؛ آخرین بررسی ${summary.checked} نماد Bitunix")
        } catch (e: Exception) {
            val msg = "خطای اسکن: ${e.message ?: "نامشخص"}"
            getSharedPreferences("scanner_state", MODE_PRIVATE).edit().putString("last_status", msg).apply()
            sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).putExtra(EXTRA_STATUS, msg))
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SERVICE, "وضعیت اسکنر", NotificationManager.IMPORTANCE_LOW)
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SIGNALS, "هشدارهای EMA", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun startAsForeground() {
        val notification = serviceNotification("اسکنر فعال است؛ در حال دریافت اطلاعات بازار")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }
    }

    private fun serviceNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_SERVICE)
                .setContentTitle("اسکنر EMA هومکس")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("اسکنر EMA هومکس")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateForegroundNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(SERVICE_NOTIFICATION_ID, serviceNotification(text))
    }

    private fun showSignalNotification(signal: MarketSignal, index: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        val direction = if (signal.direction == "LONG") "صعودی LONG" else "نزولی SHORT"
        val body = "${signal.exchange} | ${signal.symbol}\nقیمت: ${signal.lastPrice}\nEMA200 15m: ${signal.ema200}\nحجم: ${"%.1f".format(signal.volumeBtc)} BTC"
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_SIGNALS)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setContentTitle("برخورد قیمت با EMA200 — $direction")
            .setContentText("${signal.exchange} ${signal.symbol}")
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setAutoCancel(true)
            .build()
        val id = (signal.exchange + signal.symbol + signal.direction).hashCode()
        manager.notify(id, notification)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        scheduled = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "ir.homeacs.emascanner.START"
        const val ACTION_STOP = "ir.homeacs.emascanner.STOP"
        const val ACTION_UPDATE = "ir.homeacs.emascanner.UPDATE"
        const val EXTRA_STATUS = "status"
        private const val CHANNEL_SERVICE = "scanner_service"
        private const val CHANNEL_SIGNALS = "scanner_signals"
        private const val SERVICE_NOTIFICATION_ID = 1001
    }
}
