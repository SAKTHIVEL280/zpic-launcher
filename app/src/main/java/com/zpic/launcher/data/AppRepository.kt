package com.zpic.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator

data class AppEntry(
    val key: String,
    val systemLabel: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWork: Boolean,
    val info: LauncherActivityInfo,
    val category: Int,
    val capability: AppGroup?,
) {
    val packageName: String get() = component.packageName
}

class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    val launcher = context.getSystemService(LauncherApps::class.java)!!
    private val users = context.getSystemService(UserManager::class.java)!!
    private val self = android.os.Process.myUserHandle()
    private val state = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = state
    private var job: Job? = null

    init {
        launcher.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(p: String?, u: UserHandle?) = refresh()
            override fun onPackageAdded(p: String?, u: UserHandle?) = refresh()
            override fun onPackageChanged(p: String?, u: UserHandle?) = refresh()
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
        }, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun readCapabilities(): Map<String, AppGroup> {
        val pm = context.packageManager
        val out = HashMap<String, AppGroup>()
        fun scheme(uri: String, group: AppGroup) = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(uri)), PackageManager.MATCH_ALL).forEach { out.putIfAbsent(it.activityInfo.packageName, group) }
        }
        fun role(category: String, group: AppGroup) = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), PackageManager.MATCH_ALL).forEach { out.putIfAbsent(it.activityInfo.packageName, group) }
        }
        scheme("upi://pay", AppGroup.Finance)
        role(Intent.CATEGORY_APP_EMAIL, AppGroup.Chat)
        role(Intent.CATEGORY_APP_MESSAGING, AppGroup.Chat)
        role(Intent.CATEGORY_APP_CONTACTS, AppGroup.Chat)
        role(Intent.CATEGORY_APP_MUSIC, AppGroup.Media)
        role(Intent.CATEGORY_APP_GALLERY, AppGroup.Photos)
        role(Intent.CATEGORY_APP_MAPS, AppGroup.Travel)
        role(Intent.CATEGORY_APP_MARKET, AppGroup.Shopping)
        role(Intent.CATEGORY_APP_CALENDAR, AppGroup.Work)
        role(Intent.CATEGORY_APP_CALCULATOR, AppGroup.Tools)
        role(Intent.CATEGORY_APP_FILES, AppGroup.Tools)
        role(Intent.CATEGORY_APP_FITNESS, AppGroup.Health)
        role(Intent.CATEGORY_APP_WEATHER, AppGroup.News)
        scheme("geo:0,0", AppGroup.Travel)
        return out
    }

    fun refresh() {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            val collator = Collator.getInstance()
            val capabilities = readCapabilities()
            state.value = launcher.profiles.flatMap { user ->
                val serial = users.getSerialNumberForUser(user)
                launcher.getActivityList(null, user).map { info ->
                    AppEntry(
                        key = "${info.componentName.flattenToShortString()}#$serial",
                        systemLabel = info.label.toString(),
                        component = info.componentName,
                        user = user,
                        isWork = user != self,
                        info = info,
                        category = info.applicationInfo.category,
                        capability = capabilities[info.componentName.packageName],
                    )
                }
            }.sortedWith(compareBy(collator) { it.systemLabel })
        }
    }
}

/** An app as this user sees it: with their rename applied. */
fun AppEntry.label(s: Settings) = s.renamed[key] ?: systemLabel

fun AppEntry.group(s: Settings): AppGroup =
    s.groupOverrides[key]?.let { name -> AppGroup.entries.firstOrNull { it.name == name } }
        ?: classify(packageName, systemLabel, category, capability)
