package top.lyuy.luystatus.queue


import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

object QueueNotificationBuilder {

    private const val TAG = "QueueNotificationBuilder"

    /**
     * 将后端返回的 Queue 数据（JsonObject）转换为可直接展示在通知中的文本内容。
     *
     * 设计目标：
     * 1. 优先输出结构化、对用户友好的摘要信息（时间 / 名称 / 状态 / 错误 / 消息）
     * 2. 在无法识别关键字段时，保证“稳定可读”的 fallback 输出
     * 3. 不抛异常，不因字段异常导致通知构建失败
     *
     * 解析策略：
     * - timestamp：
     *   支持两种格式：
     *     1) 数字时间戳（毫秒）
     *     2) ISO-8601 字符串（通过 parseIsoTime 解析）
     *
     * - name：
     *   优先读取顶层字段 "name"，
     *   若不存在，则尝试 data.site.name。
     *
     * - status：
     *   仅当 "status" 为 JsonObject 时解析：
     *     - 同时存在 previous / current → 显示“从 A 变为 B”
     *     - 仅存在 current → 显示 current
     *     - 否则忽略
     *
     * - customMessage / errorMessage：
     *   若存在且非空，则追加为附加行。
     *
     * 输出规则：
     * - 当 timestamp 与 name 均存在时：
     *     输出结构化文本：
     *         时间：yyyy-MM-dd HH:mm:ss
     *         名称：xxx
     *         状态：xxx（可选）
     *         错误消息：xxx（可选）
     *         消息：xxx（可选）
     *
     * - 否则：
     *     进入 fallback 模式，
     *     将 JsonObject 按 key:value 逐行输出，
     *     保证信息不丢失且结构稳定。
     * 容错原则：
     * - 任意字段缺失或类型异常都不会抛出异常
     * - data 为空或无字段时返回默认提示文本
     *
     * 返回值：
     * - 可直接用于 Notification 展示的多行字符串
     **/
    fun buildNotification(data: JsonObject?): String {
        Log.d(TAG, "raw data:$data")

        if (data == null || data.entrySet().isEmpty()) {
            return "收到一个空 Queue 事件"
        }

        val timestamp = data.get("timestamp")?.let {
            when {
                it.isJsonPrimitive && it.asJsonPrimitive.isNumber ->
                    it.asLong
                it.isJsonPrimitive && it.asJsonPrimitive.isString ->
                    parseIsoTime(it.asString)
                else -> null
            }
        }

        val name =
            data.getStringOrNull("name")
                ?: data.get("site")
                    ?.asJsonObject
                    ?.getStringOrNull("name")

        val customMessage =
            data.getStringOrNull("customMessage")

        val errorMessage =
            data.getStringOrNull("errorMessage")

        val statusText: String? =
            data.get("status")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.let { statusObj ->
                    val current = statusObj.getStringOrNull("current")
                    val previous = statusObj.getStringOrNull("previous")

                    when {
                        !current.isNullOrBlank() && !previous.isNullOrBlank() ->
                            "从 $previous 变为 $current"

                        !current.isNullOrBlank() ->
                            current

                        else -> null
                    }
                }


        if (timestamp != null && !name.isNullOrBlank()) {
            return buildString {
                appendLine("时间：${formatTime(timestamp)}")
                append("名称：").append(name)

                appendOptionalLine("状态", statusText)
                appendOptionalLine("错误消息", errorMessage)
                appendOptionalLine("消息", customMessage)
            }
        }


        // fallback：稳定 key:value 输出
        return buildString {
            data.entrySet().forEachIndexed { index, entry ->
                append(entry.key).append(": ")
                append(renderJsonValue(entry.value))
                if (index != data.entrySet().size - 1) append('\n')
            }
        }
    }

    // 将 JsonElement 渲染为稳定、可读的字符串
    private fun renderJsonValue(value: JsonElement): String =
        when {
            value.isJsonNull -> "null"
            value.isJsonPrimitive -> {
                val p = value.asJsonPrimitive
                when {
                    p.isString -> p.asString
                    p.isNumber -> p.asNumber.toString()
                    p.isBoolean -> p.asBoolean.toString()
                    else -> p.toString()
                }
            }
            else -> value.toString()
        }

    // 转换 ISO 时间
    private fun parseIsoTime(value: String): Long? =
        try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            null
        }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun JsonObject.getStringOrNull(key: String): String? {
        val e = get(key) ?: return null
        if (!e.isJsonPrimitive) return null
        val p = e.asJsonPrimitive
        if (!p.isString) return null
        return p.asString
    }
}

    private fun StringBuilder.appendOptionalLine(
        label: String,
        value: String?
    ) {
        if (!value.isNullOrBlank()) {
            append('\n')
            append(label).append("：").append(value)
        }
    }
