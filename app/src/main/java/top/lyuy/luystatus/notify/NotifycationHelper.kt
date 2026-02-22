package top.lyuy.luystatus.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import top.lyuy.luystatus.PopReceiver
import top.lyuy.luystatus.R

object NotificationHelper {

    /**
     * PopReceiver / Worker 都会用到
     */
    const val NOTIFICATION_ID = 1

    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_INDEX = "extra_index"

    private const val CHANNEL_ID = "queue_channel"
    private const val CHANNEL_NAME = "Queue 通知"

    /**
     * 显示队列通知（带确认 Action）
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showQueueNotification(
        context: Context,
        content: String,
        sessionId: Int,
        index: Long
    ) {
        createChannelIfNeeded(context)

        // ✅ 用户点击「收到」
        val popIntent = Intent(context, PopReceiver::class.java).apply {
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_INDEX, index)
        }

        /**
         * ⚠️ requestCode 必须和 index 相关
         * 否则 PendingIntent 会被系统复用
         */
        val popPendingIntent = PendingIntent.getBroadcast(
            context,
            index.toInt(), // index 单调递增，正好用
            popIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("收到一个 Queue")
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(content)
            )
            .setOngoing(true)          //  不让用户一划就“确认”
            .setAutoCancel(false)      //  必须点「收到」
            .addAction(
                R.drawable.ic_done,
                "收到",
                popPendingIntent
            )
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID, notification)
    }

    /**
     * 取消队列通知（在确认后调用）
     */
    fun cancelQueueNotification(context: Context) {
        NotificationManagerCompat.from(context)
            .cancel(NOTIFICATION_ID)
    }

    private fun createChannelIfNeeded(context: Context) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "来自队列服务的通知"
        }

        manager.createNotificationChannel(channel)
    }
}
