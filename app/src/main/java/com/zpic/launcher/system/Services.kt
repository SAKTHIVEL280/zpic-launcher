package com.zpic.launcher.system

import android.accessibilityservice.AccessibilityService
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Exists only to reach GLOBAL_ACTION_LOCK_SCREEN, the one lock API that keeps fingerprint unlock working. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        private var instance: LockService? = null
        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}

/** One notification, kept so the shelf can show it without opening the notification panel. */
data class ShelfNote(
    val key: String, val title: String, val text: String, val time: Long, val action: android.app.PendingIntent?, val autoCancel: Boolean,
)

data class WaitingApp(val packageName: String, val count: Int, val latest: Long, val notes: List<ShelfNote> = emptyList())

class DotsService : NotificationListenerService() {
    override fun onListenerConnected() { instance = this; publish() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()
    override fun onListenerDisconnected() { instance = null; state.value = emptyList() }

    private fun publish() {
        state.value = runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray())
            .filter { it.isClearable && !it.isOngoing && it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0 }
            .groupBy { it.packageName }
            .map { (p, list) -> WaitingApp(p, list.size, list.maxOf { it.postTime }, list.sortedByDescending { it.postTime }.map(::note)) }
            .sortedByDescending { it.latest }
    }

    private fun note(sbn: StatusBarNotification): ShelfNote {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT))?.toString().orEmpty()
        return ShelfNote(sbn.key, title, text, sbn.postTime, sbn.notification.contentIntent, sbn.notification.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0)
    }

    companion object {
        private var instance: DotsService? = null

        /** Opens a notification the way tapping it in the panel would. Returns false if there was nothing to open. */
        fun open(context: android.content.Context, note: ShelfNote): Boolean {
            val action = note.action ?: return false
            val sent = runCatching {
                val options = android.app.ActivityOptions.makeBasic()
                // The shelf is on screen when tapped, so the app may start its activity with our foreground standing.
                if (android.os.Build.VERSION.SDK_INT >= 36) options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
                else if (android.os.Build.VERSION.SDK_INT >= 34) @Suppress("DEPRECATION") options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                action.send(context, 0, null, null, null, null, options.toBundle())
            }.isSuccess
            if (sent && note.autoCancel) dismiss(note.key)
            return sent
        }

        /** Clears one notification, as swiping it away in the panel would. */
        fun dismiss(key: String) { runCatching { instance?.cancelNotification(key) } }

        private val state = MutableStateFlow<List<WaitingApp>>(emptyList())
        val waiting: StateFlow<List<WaitingApp>> = state
    }
}
