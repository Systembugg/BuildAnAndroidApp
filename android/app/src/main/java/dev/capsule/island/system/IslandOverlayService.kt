package dev.capsule.island.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import dev.capsule.island.R
import dev.capsule.island.app.MainActivity

/** Hosts the island in an app overlay when the accessibility host is off. */
class IslandOverlayService : Service() {
    private var host: OverlayHost? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Island", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Keeps the island running"
            setShowBadge(false)
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_island)
            .setContentTitle("Island is running")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, notification)
        host = OverlayHost(this, getSystemService(WindowManager::class.java), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, Hosts.overlayActive)
            .also { it.attach() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        host?.detach()
        host = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "island"
        const val ID = 1
    }
}
