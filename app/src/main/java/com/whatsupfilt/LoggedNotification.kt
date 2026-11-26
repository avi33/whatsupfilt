package com.whatsupfilt

import org.json.JSONArray
import org.json.JSONObject

/** One message line inside a WhatsApp notification. sender == null means "you". */
data class Msg(val sender: String?, val text: String?, val timestamp: Long)

/** Everything we captured about one posted WhatsApp notification. */
data class LoggedNotification(
    val time: Long,
    val pkg: String,
    val key: String,
    val channelId: String?,
    val isGroupSummary: Boolean,
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val messages: List<Msg>,
    /** Best guess at group/sender, from MessagingStyle or falling back to the title text. */
    val resolvedGroup: String?,
    val resolvedSender: String?,
    val resolvedFrom: String,
    val rawExtras: Map<String, String>,
) {
    fun toJson(redact: Boolean = false): JSONObject = JSONObject().apply {
        put("time", time)
        put("pkg", pkg)
        put("key", key)
        put("channelId", channelId)
        put("isGroupSummary", isGroupSummary)
        put("conversationTitle", conversationTitle)
        put("isGroupConversation", isGroupConversation)
        put("resolvedGroup", resolvedGroup)
        put("resolvedSender", resolvedSender)
        put("resolvedFrom", resolvedFrom)
        put("messages", JSONArray().apply {
            messages.forEach { m ->
                put(JSONObject().apply {
                    put("sender", m.sender)
                    put("text", if (redact) redactText(m.text) else m.text)
                    put("timestamp", m.timestamp)
                })
            }
        })
        put("rawExtras", JSONObject().apply {
            rawExtras.forEach { (k, v) ->
                put(k, if (redact && k !in SAFE_EXTRA_KEYS) redactText(v) else v)
            }
        })
    }

    companion object {
        /** Extras that hold names/structure only, not message bodies. Kept as-is in redacted exports. */
        private val SAFE_EXTRA_KEYS = setOf(
            "android.title", "android.conversationTitle", "android.isGroupConversation",
            "android.template", "android.selfDisplayName", "android.subText",
            "android.showWhen", "android.showChronometer", "android.reduced.images",
            "android.messagingUser", "android.messagingStyleUser", "android.hiddenConversationTitle",
        )

        private fun redactText(s: String?): String? = s?.let { "<${it.length} chars>" }

        fun fromJson(o: JSONObject): LoggedNotification {
            val msgs = o.optJSONArray("messages") ?: JSONArray()
            val raw = o.optJSONObject("rawExtras") ?: JSONObject()
            return LoggedNotification(
                time = o.optLong("time"),
                pkg = o.optString("pkg"),
                key = o.optString("key"),
                channelId = o.optNullableString("channelId"),
                isGroupSummary = o.optBoolean("isGroupSummary"),
                conversationTitle = o.optNullableString("conversationTitle"),
                isGroupConversation = o.optBoolean("isGroupConversation"),
                messages = (0 until msgs.length()).map { i ->
                    val m = msgs.getJSONObject(i)
                    Msg(m.optNullableString("sender"), m.optNullableString("text"), m.optLong("timestamp"))
                },
                resolvedGroup = o.optNullableString("resolvedGroup"),
                resolvedSender = o.optNullableString("resolvedSender"),
                resolvedFrom = o.optString("resolvedFrom"),
                rawExtras = raw.keys().asSequence().associateWith { raw.optString(it) },
            )
        }

        private fun JSONObject.optNullableString(k: String): String? =
            if (isNull(k)) null else optString(k)
    }
}
