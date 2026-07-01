package com.gshashank.btcagent.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gshashank.btcagent.MainActivity
import com.gshashank.btcagent.R
import com.gshashank.btcagent.data.repository.NotificationsRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FCM push client — MOBILE-41.
 *
 * Cannot be Hilt constructor-injected (the Android framework instantiates [FirebaseMessagingService]
 * directly), so it uses field injection via [AndroidEntryPoint] — same pattern as
 * [com.gshashank.btcagent.MainActivity].
 *
 * [onNewToken] fires whenever FCM (re)issues a device token (first install, app restore, token
 * rotation) — registers it with the backend, fire-and-forget (no UI to report to at this layer;
 * [NotificationsRepository.register] never throws, and a 404 = global toggle OFF is already
 * treated as inert, so there's nothing actionable to surface here regardless of outcome).
 *
 * [onMessageReceived] only fires for foregrounded delivery of notification-only payloads (per the
 * backend contract, no data payload/deep-link this version); backgrounded delivery is posted by
 * the FCM SDK itself using the manifest's default_notification_channel_id meta-data. Both paths
 * land on the same [CHANNEL_ID] channel, created here if missing (API 26+ requires the channel to
 * exist before a notification targeting it will show).
 */
@AndroidEntryPoint
class BtcMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationsRepository: NotificationsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            notificationsRepository.register(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: "BTC Agent"
        val body = message.notification?.body
        showNotification(title, body)
    }

    override fun onDestroy() {
        // Cancel in-flight register coroutines when the framework tears the service down —
        // a FirebaseMessagingService instance can be destroyed independently of launched work.
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showNotification(title: String, body: String?) {
        createNotificationChannel()

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Trading alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts from the BTC Agent scanner (signals, fills, DEPO events)."
        }
        val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    companion object {
        /** Must match AndroidManifest.xml's com.google.firebase.messaging.default_notification_channel_id. */
        const val CHANNEL_ID = "btc_agent_alerts"
        private const val NOTIFICATION_ID = 1001
    }
}
