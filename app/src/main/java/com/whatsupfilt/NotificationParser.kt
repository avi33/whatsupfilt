package com.whatsupfilt

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

object NotificationParser {

    // Fallback for older/non-MessagingStyle notifications: "Group: Sender" or
    // "Group (3 messages): Sender". The "(...)" part is localized, so match any parenthetical.
    private val TITLE_GROUP_SENDER = Regex("""^(.+?)(?:\s*\([^)]*\))?:\s+(.+)$""")

    fun parse(sbn: StatusBarNotification): LoggedNotification {
        val n = sbn.notification
        val extras = n.extras

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val messages = style?.messages?.map {
            Msg(it.person?.name?.toString(), it.text?.toString(), it.timestamp)
        } ?: emptyList()

        val convTitle = style?.conversationTitle?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val isGroup = style?.isGroupConversation
            ?: extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)

        var group: String? = null
        var sender: String? = null
        var from = "none"
        if (isGroup && convTitle != null && messages.isNotEmpty()) {
            group = convTitle
            sender = messages.last().sender ?: "(me)"
            from = "messagingStyle"
        } else {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val m = title?.let { TITLE_GROUP_SENDER.matchEntire(it) }
            if (m != null) {
                group = m.groupValues[1].trim()
                sender = m.groupValues[2].trim()
                from = "titleRegex"
            }
        }

        return LoggedNotification(
            time = sbn.postTime,
            pkg = sbn.packageName,
            key = sbn.key,
            channelId = n.channelId,
            isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            conversationTitle = convTitle,
            isGroupConversation = isGroup,
            messages = messages,
            resolvedGroup = group,
            resolvedSender = sender,
            resolvedFrom = from,
            rawExtras = dumpBundle(extras),
        )
    }

    @Suppress("DEPRECATION")
    private fun dumpBundle(b: Bundle): Map<String, String> =
        b.keySet().sorted().associateWith { describe(b.get(it)) }

    @Suppress("DEPRECATION")
    private fun describe(v: Any?, depth: Int = 0): String {
        val s = when (v) {
            null -> "null"
            is Bundle -> if (depth > 1) "Bundle(${v.keySet()})"
            else v.keySet().sorted().joinToString(prefix = "{", postfix = "}") { k -> "$k=${describe(v.get(k), depth + 1)}" }
            is Array<*> -> v.joinToString(prefix = "[", postfix = "]") { describe(it, depth + 1) }
            is Parcelable -> "${v.javaClass.simpleName}"
            is CharSequence -> v.toString()
            is IntArray -> v.contentToString()
            is LongArray -> v.contentToString()
            else -> v.toString()
        }
        return if (s.length > 1000) s.take(1000) + "…" else s
    }
}
