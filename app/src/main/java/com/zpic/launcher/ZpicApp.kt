package com.zpic.launcher

import android.app.Application
import com.zpic.launcher.ui.ZpicWidgetHost
import com.zpic.launcher.data.AppRepository
import com.zpic.launcher.data.SettingsRepo
import com.zpic.launcher.icons.IconEngine
import com.zpic.launcher.system.WallpaperController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ZpicApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings by lazy { SettingsRepo(this, scope) }
    val apps by lazy { AppRepository(this, scope) }
    val icons by lazy { IconEngine(this) }
    val wallpaper by lazy { WallpaperController(this) { seed -> settings.update { it.copy(wallpaperSeed = seed) } } }
    val widgetHost by lazy { ZpicWidgetHost(this) }
}
