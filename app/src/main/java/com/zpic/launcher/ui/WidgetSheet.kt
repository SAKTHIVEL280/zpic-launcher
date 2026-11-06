package com.zpic.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun Preview(p: AppWidgetProviderInfo) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, p) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val d: Drawable? = p.loadPreviewImage(context, 0) ?: p.loadIcon(context, 0)
                d?.toBitmap(d.intrinsicWidth.coerceIn(1, 600), d.intrinsicHeight.coerceIn(1, 600))?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(164.dp, 124.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), Alignment.Center) {
        image?.let { Image(it, null, Modifier.padding(8.dp), contentScale = ContentScale.Fit) }
    }
}

/** Every widget, with its preview, under the app it belongs to. */
@Composable
fun WidgetSheet(onDismiss: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val byApp = remember {
        AppWidgetManager.getInstance(context).installedProviders.groupBy { p ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString() }.getOrDefault(p.provider.packageName)
        }.toSortedMap().toList()
    }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Widgets", Modifier.padding(start = 24.dp, bottom = 12.dp), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(byApp, key = { it.first }) { (name, list) ->
                Column {
                    Text(name, Modifier.padding(start = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list, key = { it.provider.flattenToString() }) { p ->
                            Column(Modifier.width(164.dp).clip(RoundedCornerShape(20.dp)).clickable { onPick(p) }) {
                                Preview(p)
                                Text(p.loadLabel(pm), Modifier.padding(6.dp).height(36.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
