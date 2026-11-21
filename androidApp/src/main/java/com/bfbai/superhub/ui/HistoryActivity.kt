// Built with BFB.AI
package com.bfbai.superhub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bfbai.superhub.service.ShelfItem
import com.bfbai.superhub.utils.HistoryRepository
import com.bfbai.superhub.utils.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BgColor = Color(0xFF0B1B3A)
private val CardColor = Color(0xFF1E3A6D)
private val Teal = Color(0xFF4DE1E1)
private val TextMain = Color(0xFFFFFFFF)
private val TextSub = Color(0xFF9FB3D1)
private val Gold = Color(0xFFFFD54F)
private val Danger = Color(0xFFFF6B6B)

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HistoryRepository.init(this)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HistoryScreen(onNavigateBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val all by HistoryRepository.items.collectAsState()
    val loaded by HistoryRepository.loaded.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = remember(all, query, filter) {
        val q = query.trim()
        all.filter { item ->
            val okType = when (filter) {
                "text" -> item.imagePath == null
                "images" -> item.imagePath != null
                "pinned" -> item.isPinned
                else -> true
            }
            val okQuery = q.isEmpty() ||
                item.text.contains(q, ignoreCase = true) ||
                item.source.contains(q, ignoreCase = true)
            okType && okQuery
        }.sortedByDescending { it.isPinned }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح السجل؟") },
            text = { Text("هيتمسح كل شيء ما عدا العناصر المثبّتة.") },
            confirmButton = {
                TextButton(onClick = {
                    HistoryRepository.clearUnpinned()
                    confirmClear = false
                }) { Text("مسح") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("إلغاء") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // الترويسة
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "رجوع",
                color = Teal,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onNavigateBack() }
            )
            Text(
                text = "سجل HubSync",
                color = TextMain,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
            if (all.isNotEmpty()) {
                Text(
                    text = "مسح",
                    color = Danger,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { confirmClear = true }
                )
            }
        }

        // البحث
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = TextMain, fontSize = 14.sp),
            cursorBrush = SolidColor(Teal),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1FFFFFFF))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (query.isEmpty()) {
                        Text("ابحث في السجل", color = TextSub, fontSize = 14.sp)
                    }
                    inner()
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // الفلاتر
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("الكل", filter == "all") { filter = "all" }
            FilterChip("نصوص", filter == "text") { filter = "text" }
            FilterChip("صور", filter == "images") { filter = "images" }
            FilterChip("المثبت", filter == "pinned") { filter = "pinned" }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal)
            }
        } else if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (all.isEmpty()) "السجل فارغ. ابدأ بالنسخ أو المشاركة!" else "مفيش نتائج",
                    color = TextSub,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(
                    items = shown,
                    key = { "${it.timestamp}_${it.imagePath ?: it.text.hashCode()}" }
                ) { item ->
                    HistoryCard(
                        item = item,
                        onCopy = {
                            val path = item.imagePath
                            if (path != null) {
                                val ok = ImageStore.copyImage(context, path)
                                Toast.makeText(
                                    context,
                                    if (ok) "تم نسخ الصورة" else "تعذر نسخ الصورة",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                HistoryRepository.ignoreClipUntil = System.currentTimeMillis() + 1500
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("hubsync", item.text))
                                Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShare = {
                            try {
                                val path = item.imagePath
                                if (path != null) {
                                    context.startActivity(ImageStore.shareIntent(context, path))
                                } else {
                                    val i = Intent(Intent.ACTION_SEND)
                                    i.type = "text/plain"
                                    i.putExtra(Intent.EXTRA_TEXT, item.text)
                                    context.startActivity(Intent.createChooser(i, "مشاركة"))
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "تعذرت المشاركة", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPin = { HistoryRepository.togglePin(item) },
                        onDelete = { HistoryRepository.remove(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Teal else Color(0x1FFFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) BgColor else TextMain,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ImageThumb(path: String, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { ImageStore.decodeSampled(path, 700) }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.background(Color(0x22FFFFFF)))
    }
}

@Composable
fun HistoryCard(
    item: ShelfItem,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardColor)
            .border(1.dp, Teal.copy(alpha = 0.33f), shape)
            .clickable { onCopy() }
            .padding(12.dp)
    ) {
        val path = item.imagePath
        if (path != null) {
            ImageThumb(
                path = path,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Text(
                text = item.text.take(600),
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${item.source} • ${formatTime(item.timestamp)}",
            color = Teal,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = if (item.isPinned) "★ مثبت" else "☆ تثبيت",
                color = if (item.isPinned) Gold else TextSub,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onPin() }
            )
            Text(
                text = "مشاركة",
                color = Teal,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onShare() }
            )
            Text(
                text = "حذف",
                color = Danger,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onDelete() }
            )
        }
    }
}

fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("hh:mm a - dd MMM", Locale.getDefault()).format(Date(timestamp))
}
