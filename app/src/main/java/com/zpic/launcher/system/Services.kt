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

data class WaitingApp(val packageName: String, val count: Int, val latest: Long)

class DotsService : NotificationListenerService() {
    override fun onListenerConnected() = publish()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()
    override fun onListenerDisconnected() { state.value = emptyList() }

    private fun publish() {
        state.value = runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray())
            .filter { it.isClearable && !it.isOngoing && it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0 }
            .groupBy { it.packageName }
            .map { (p, list) -> WaitingApp(p, list.size, list.maxOf { it.postTime }) }
            .sortedByDescending { it.latest }
    }

    companion object {
        private val state = MutableStateFlow<List<WaitingApp>>(emptyList())
        val waiting: StateFlow<List<WaitingApp>> = state
    }
}
