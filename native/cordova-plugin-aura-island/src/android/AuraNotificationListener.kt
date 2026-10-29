package dev.aura.island

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Reads posted notifications for display only. It never cancels or alters them. */
class AuraNotificationListener : NotificationListenerService() {
    private var last = ""

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val key = sbn.key + "|" + title + "|" + text
        if (key == last) return          // duplicate suppression
        last = key
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) { sbn.packageName }
        val hide = getSharedPreferences("aura", Context.MODE_PRIVATE).getBoolean("hide_preview", false)
        OverlayService.post(
            if (hide) label else title.ifBlank { label },
            if (hide) "New notification" else text,
            2
        )
    }
}
