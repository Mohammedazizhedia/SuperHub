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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bfbai.superhub.service.ShelfItem
import com.bfbai.superhub.service.ThemeEngine
import com.bfbai.superhub.service.tr
import com.bfbai.superhub.utils.HistoryRepository
import com.bfbai.superhub.utils.ImageStore
import com.bfbai.superhub.utils.ItemKind
import com.bfbai.superhub.utils.SmartDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeEngine.init(this)
        HistoryRepository.init(this)
        setContent { AppRoot { HistoryScreen { finish() } } }
    }
}

private sealed class HRow {
    class Head(val title: String) : HRow()
    class Entry(val item: ShelfItem) : HRow()
}

private fun startOfDay(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun dayLabel(ts: Long, ar: Boolean): String {
    val diff = ((startOfDay(System.currentTimeMillis()) - startOfDay(ts)) / 86_400_000L).toInt()
    return when (diff) {
        0 -> if (ar) "اليوم" else "Today"
        1 -> if (ar) "أمس" else "Yesterday"
        else -> SimpleDateFormat("EEEE d MMM yyyy", Locale(if (ar) "ar" else "en")).format(Date(ts))
    }
}

private fun kindColor(k: ItemKind?, sub: Color): Color = when (k) {
    ItemKind.OTP -> Color(0xFFFFB020)
    ItemKind.LINK -> Color(0xFF4DA3FF)
    ItemKind.PHONE -> Color(0xFF3DDC97)
    ItemKind.EMAIL -> Color(0xFFC792FF)
    ItemKind.TEXT -> sub
    null -> Color(0xFFFF8FB1)
}

@Composable
private fun kindLabel(k: ItemKind?): String = when (k) {
    ItemKind.OTP -> t("كود تحقق", "OTP")
    ItemKind.LINK -> t("رابط", "Link")
    ItemKind.PHONE -> t("هاتف", "Phone")
    ItemKind.EMAIL -> t("بريد", "Email")
    ItemKind.TEXT -> t("نص", "Text")
    null -> t("صورة", "Image")
}

@Composable
private fun ImageThumb(path: String, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { ImageStore.decodeSampled(path, 700) }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(Color(0x22FFFFFF)))
    }
}

@Composable
private fun MiniAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HCard(
    item: ShelfItem,
    kind: ItemKind?,
    value: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val p = LocalPal.current
    val kc = kindColor(kind, p.sub)
    val shape = RoundedCornerShape(18.dp)
    val tf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.card, p.card2)))
            .border(1.dp, p.stroke, shape)
            .clickable { onCopy() }
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(kc))
        Column(Modifier.weight(1f).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(kc.copy(alpha = 0.16f))
                        .padding(horizontal = 9.dp, vertical = 2.dp)
                ) {
                    Text(kindLabel(kind), color = kc, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                val gn = item.groupName
                if (!gn.isNullOrBlank()) {
                    Text("  " + gn, color = p.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.weight(1f))
                if (item.isPinned) Icon(Icons.Default.Star, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            val path = item.imagePath
            if (path != null) {
                ImageThumb(path, Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)))
            } else if (kind == ItemKind.OTP) {
                Text(
                    value, color = kc, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            } else {
                Text(
                    item.text.take(600), color = p.text, fontSize = 14.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.source + "  •  " + tf.format(Date(item.timestamp)),
                color = p.sub, fontSize = 11.sp
            )
            Row(Modifier.padding(top = 4.dp)) {
                MiniAction(t("نسخ", "Copy"), p.accent, onCopy)
                MiniAction(t("مشاركة", "Share"), p.accent, onShare)
                MiniAction(if (item.isPinned) t("إلغاء التثبيت", "Unpin") else t("تثبيت", "Pin"), Color(0xFFFFB020), onPin)
                Box(Modifier.weight(1f))
                MiniAction(t("حذف", "Delete"), p.danger, onDelete)
            }
        }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val ar = LocalLang.current != "en"
    val all by HistoryRepository.items.collectAsState()
    val loaded by HistoryRepository.loaded.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var confirmClear by remember { mutableStateOf(false) }
    val kinds = remember { HashMap<String, ItemKind>() }

    fun kindOf(i: ShelfItem): ItemKind? {
        if (i.imagePath != null) return null
        return kinds.getOrPut(i.text) {
            if (i.text.length > 1000) ItemKind.TEXT else SmartDetector.detect(i.text).kind
        }
    }

    val shown = remember(all, query, filter) {
        val q = query.trim()
        all.filter { i ->
            val k = kindOf(i)
            val okF = when (filter) {
                "text" -> i.imagePath == null
                "links" -> k == ItemKind.LINK
                "codes" -> k == ItemKind.OTP
                "images" -> i.imagePath != null
                "pinned" -> i.isPinned
                else -> true
            }
            val okQ = q.isEmpty() || i.text.contains(q, true) || i.source.contains(q, true) ||
                (i.groupName ?: "").contains(q, true)
            okF && okQ
        }.sortedByDescending { it.timestamp }
    }

    val rows = remember(shown, ar) {
        val out = ArrayList<HRow>()
        val pinned = shown.filter { it.isPinned }
        if (pinned.isNotEmpty()) {
            out.add(HRow.Head(if (ar) "المثبّت" else "Pinned"))
            pinned.forEach { out.add(HRow.Entry(it)) }
        }
        var last = ""
        for (i in shown) {
            if (i.isPinned) continue
            val d = dayLabel(i.timestamp, ar)
            if (d != last) {
                out.add(HRow.Head(d))
                last = d
            }
            out.add(HRow.Entry(i))
        }
        out
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = p.card,
            title = { Text(t("مسح السجل؟", "Clear history?"), color = p.text) },
            text = { Text(t("هيتمسح كل شيء ما عدا العناصر المثبّتة.", "Everything except pinned items will be deleted."), color = p.sub) },
            confirmButton = {
                TextButton(onClick = { HistoryRepository.clearUnpinned(); confirmClear = false }) {
                    Text(t("مسح", "Clear"), color = p.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(t("إلغاء", "Cancel"), color = p.sub) } }
        )
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Header(t("السجل", "History"), onBack) {
            Text("${all.size}", color = p.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
            if (all.isNotEmpty()) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable { confirmClear = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Delete, null, tint = p.danger) }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.card2)
                            .border(1.dp, p.stroke, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, tint = p.sub)
                        Spacer(Modifier.width(8.dp))
                        Box {
                            if (query.isEmpty()) Text(t("ابحث في السجل", "Search history"), color = p.sub, fontSize = 14.sp)
                            inner()
                        }
                    }
                }
            )
            ChipRow {
                Chip(t("الكل", "All"), filter == "all") { filter = "all" }
                Chip(t("نصوص", "Text"), filter == "text") { filter = "text" }
                Chip(t("روابط", "Links"), filter == "links") { filter = "links" }
                Chip(t("أكواد", "Codes"), filter == "codes") { filter = "codes" }
                Chip(t("صور", "Images"), filter == "images") { filter = "images" }
                Chip(t("المثبت", "Pinned"), filter == "pinned") { filter = "pinned" }
            }
        }

        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
        } else if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (all.isEmpty()) t("السجل فاضي. ابدأ بالنسخ أو المشاركة!", "History is empty. Start copying or sharing!")
                    else t("مفيش نتائج", "No results"),
                    color = p.sub, fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    rows,
                    key = { r ->
                        when (r) {
                            is HRow.Head -> "h:" + r.title
                            is HRow.Entry -> "i:" + r.item.timestamp + ":" + (r.item.imagePath ?: r.item.text.hashCode().toString())
                        }
                    }
                ) { r ->
                    when (r) {
                        is HRow.Head -> Text(
                            r.title, color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                        )
                        is HRow.Entry -> {
                            val item = r.item
                            val kind = kindOf(item)
                            val det = if (item.imagePath == null && item.text.length <= 1000) SmartDetector.detect(item.text) else null
                            val value = if (kind == ItemKind.OTP && det != null) det.value else item.text
                            HCard(
                                item = item,
                                kind = kind,
                                value = value,
                                onCopy = {
                                    val path = item.imagePath
                                    if (path != null) {
                                        val ok = ImageStore.copyImage(ctx, path)
                                        Toast.makeText(ctx, if (ok) tr("تم نسخ الصورة", "Image copied") else tr("تعذر نسخ الصورة", "Could not copy image"), Toast.LENGTH_SHORT).show()
                                    } else {
                                        HistoryRepository.ignoreClipUntil = System.currentTimeMillis() + 1500
                                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("hubsync", value))
                                        Toast.makeText(ctx, tr("تم النسخ", "Copied"), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onShare = {
                                    try {
                                        val path = item.imagePath
                                        if (path != null) {
                                            ctx.startActivity(ImageStore.shareIntent(ctx, path))
                                        } else {
                                            val i = Intent(Intent.ACTION_SEND)
                                            i.type = "text/plain"
                                            i.putExtra(Intent.EXTRA_TEXT, item.text)
                                            ctx.startActivity(Intent.createChooser(i, "HubSync"))
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, tr("تعذرت المشاركة", "Share failed"), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onPin = { HistoryRepository.togglePin(item) },
                                onDelete = { HistoryRepository.remove(item) }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
