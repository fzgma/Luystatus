package top.lyuy.luystatus

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import retrofit2.HttpException
import top.lyuy.luystatus.api.ApiProvider
import top.lyuy.luystatus.notify.NotificationHelper
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class QueueWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "QueueWorker"
        private const val PREF_NAME = "queue_worker"

        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_LAST_CONFIRMED_INDEX = "last_confirmed_index"

        private const val KEY_PENDING_INDEX = "pending_index"

        private const val KEY_LAST_INDEX = "last_handled_index"
        private const val KEY_LAST_NOTIFY_TIME = "last_notify_time"
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)


    override suspend fun doWork(): Result {
        val prefs = applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        try {
            val listResponse = ApiProvider.api.list()
            val items = listResponse.items

            if (items.isEmpty()) return Result.success()

            var sessionId = prefs.getInt(KEY_SESSION_ID, 0)
            var lastConfirmed = prefs.getLong(KEY_LAST_CONFIRMED_INDEX, -1L)
            var pending: Long = prefs.getLong(KEY_PENDING_INDEX, -1L)
            val lastNotifyTime = prefs.getLong(KEY_LAST_NOTIFY_TIME, 0L)
            val now = System.currentTimeMillis()

            val maxIndex = items.maxOf { it.index }

            //  index 回退 → 新 session
            if (maxIndex < lastConfirmed) {
                sessionId++
                lastConfirmed = -1
                pending = -1

                prefs.edit {
                    putInt(KEY_SESSION_ID, sessionId)
                    putLong(KEY_LAST_CONFIRMED_INDEX, lastConfirmed)
                    putLong(KEY_PENDING_INDEX, pending)
                }
            }

            //  如果当前没有 pending，找下一个
            if (pending.toLong() == -1L) {
                val next = items
                    .filter { it.index > lastConfirmed }
                    .minByOrNull { it.index }

                if (next == null) return Result.success()

                pending = next.index.toLong()
                prefs.edit {
                    putLong(KEY_PENDING_INDEX, pending)
                    putLong(KEY_LAST_NOTIFY_TIME, 0L)
                }
            }

            //  判断是否需要提醒，每60s至多一次
            val shouldNotify =
                lastNotifyTime == 0L || now - lastNotifyTime >= 60_000L

            if (shouldNotify) {
                val item = items.firstOrNull { it.index.toLong() == pending }
                    ?: run {
                        // 如果已被其他程序pop掉，pending 已经不存在，直接清理本地状态
                        prefs.edit {
                            putLong(KEY_PENDING_INDEX, -1L)
                            putLong(KEY_LAST_NOTIFY_TIME, 0L)
                        }
                        return Result.success()
                    }

                NotificationHelper.showQueueNotification(
                    context = applicationContext,
                    content = item.data.toString(),
                    index = pending,
                    sessionId = sessionId
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "queue poll failed", e)
        } finally {
            enqueueNext()
        }

        return Result.success()
    }



    // 每25s轮询一次
    private fun enqueueNext() {
        val request = OneTimeWorkRequestBuilder<QueueWorker>()
            .setInitialDelay(25, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueue(request)
    }
}
