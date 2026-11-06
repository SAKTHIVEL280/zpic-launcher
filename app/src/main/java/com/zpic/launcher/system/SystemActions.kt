package com.zpic.launcher.system

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import com.zpic.launcher.data.AppEntry

object SystemActions {
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        runCatching {
            val service = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        }
    }

    /** Returns false when the accessibility service hasn't been enabled yet. */
    fun lockScreen(): Boolean = LockService.lock()

    fun isDefaultHome(context: Context) =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    fun requestDefaultHome(activity: Activity) {
        activity.startActivityForResult(
            activity.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME), 1,
        )
    }

    fun isLockServiceOn(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(ComponentName(context, LockService::class.java).flattenToString()) == true

    fun isDotsOn(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(ComponentName(context, DotsService::class.java).flattenToString()) == true

    fun openClock(context: Context) = start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun openCalendar(context: Context) = start(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")),
    )

    fun webSearch(context: Context, query: String) = start(
        context, Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query),
    ) || start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))

    fun nextAlarm(context: Context): Long? =
        context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime

    fun uninstall(context: Context, app: AppEntry) = start(
        context,
        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .putExtra(Intent.EXTRA_USER, app.user),
    )

    fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
