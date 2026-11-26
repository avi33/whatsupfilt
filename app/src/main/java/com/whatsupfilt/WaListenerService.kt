package com.whatsupfilt

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Phase 0: observe only. Logs every WhatsApp notification so we can see exactly
 * how group name / sender are encoded on this device. Nothing is cancelled yet.
 */
class WaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationLogStore.setConnected(true)
        NotificationLogStore.load(applicationContext)
    }

    override fun onListenerDisconnected() {
        NotificationLogStore.setConnected(false)
        // Ask the system to bind us again (e.g. after an OEM battery saver killed us).
        requestRebind(ComponentName(this, WaListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        try {
            val entry = NotificationParser.parse(sbn)
            NotificationLogStore.append(applicationContext, entry)
            Log.d(TAG, "group=${entry.resolvedGroup} sender=${entry.resolvedSender} via=${entry.resolvedFrom} summary=${entry.isGroupSummary}")
        } catch (t: Throwable) {
            Log.e(TAG, "parse failed", t)
        }
    }

    companion object { private const val TAG = "WaListener" }
}
