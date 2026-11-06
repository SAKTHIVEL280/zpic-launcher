package com.zpic.launcher.system

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

enum class WallpaperTarget(val flags: Int) {
    Both(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
    Home(WallpaperManager.FLAG_SYSTEM),
    Lock(WallpaperManager.FLAG_LOCK),
}

/** The most colourful of the wallpaper's main colours; a mostly black or white image has a grey primary. */
fun accentOf(colors: android.app.WallpaperColors): Int = listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor)
    .map { it.toArgb() }
    .maxBy { c ->
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(c, hsl)
        hsl[1] * (1f - kotlin.math.abs(hsl[2] - .5f) * 2f)
    }

class WallpaperController(private val context: Context, private val onApplied: (Int) -> Unit) {
    private val manager = WallpaperManager.getInstance(context)
    private val metrics get() = context.resources.displayMetrics

    /** What the system reports now, trying each source; null if this phone reports nothing. */
    fun currentAccent(): Int? {
        for (which in listOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK)) {
            runCatching { manager.getWallpaperColors(which) }.getOrNull()?.let { return accentOf(it) }
        }
        return runCatching {
            (manager.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { accentOf(android.app.WallpaperColors.fromBitmap(it)) }
        }.getOrNull()
    }

    val hasSeparateLock: Boolean get() = runCatching { manager.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0 }.getOrDefault(false)

    suspend fun applyImage(uri: Uri, target: WallpaperTarget) = withContext(Dispatchers.IO) {
        val w = metrics.widthPixels; val h = metrics.heightPixels
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Decode no larger than needed, then centre-crop to the screen so the photo isn't stretched.
            val scale = max(w.toFloat() / info.size.width, h.toFloat() / info.size.height).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        apply(centerCrop(bitmap, w, h), target)
    }

    suspend fun applyArt(design: ArtDesign, spec: ArtSpec, target: WallpaperTarget) = withContext(Dispatchers.Default) {
        apply(WallpaperArt.render(design, spec, metrics.widthPixels, metrics.heightPixels), target)
    }

    /** Drops the separate lock wallpaper, after which the system shows the home wallpaper on the lock screen too. */
    suspend fun makeLockFollowHome() = withContext(Dispatchers.IO) {
        manager.clear(WallpaperManager.FLAG_LOCK)
    }

    private fun apply(bitmap: Bitmap, target: WallpaperTarget) {
        // Written one target at a time, because a combined write may leave no plain home wallpaper
        // file on some phones, and that file is what colour tools read.
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        if (target != WallpaperTarget.Lock) manager.setStream(ByteArrayInputStream(png), null, true, WallpaperManager.FLAG_SYSTEM)
        if (target != WallpaperTarget.Home) manager.setStream(ByteArrayInputStream(png), null, true, WallpaperManager.FLAG_LOCK)
        if (target != WallpaperTarget.Lock) runCatching { onApplied(accentOf(android.app.WallpaperColors.fromBitmap(bitmap))) }
    }

    /** Hands an image to the phone's own wallpaper app, so the system itself is what sets it. */
    fun systemSetIntent(bitmap: Bitmap): Intent {
        val dir = File(context.cacheDir, "wallpapers").apply { mkdirs() }
        val file = File(dir, "zpic-wallpaper.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = runCatching { manager.getCropAndSetWallpaperIntent(uri) }.getOrNull()
            ?: Intent.createChooser(Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(uri, "image/png").putExtra("mimeType", "image/png"), "Set as wallpaper")
        return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun renderFull(design: ArtDesign, spec: ArtSpec): Bitmap = withContext(Dispatchers.Default) {
        WallpaperArt.render(design, spec, metrics.widthPixels, metrics.heightPixels)
    }

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val m = Matrix().apply {
            postScale(scale, scale)
            postTranslate((w - src.width * scale) / 2f, (h - src.height * scale) / 2f)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
