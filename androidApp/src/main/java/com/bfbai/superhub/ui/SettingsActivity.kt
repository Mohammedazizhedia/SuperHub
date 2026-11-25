// Built with BFB.AI
package com.bfbai.superhub.ui

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bfbai.superhub.service.AppIconSwitcher
import com.bfbai.superhub.service.AppPrefs
import com.bfbai.superhub.service.ClipboardAccessibilityService
import com.bfbai.superhub.service.IconOptions
import com.bfbai.superhub.service.ShelfSettings
import com.bfbai.superhub.service.ShelfStyle
import com.bfbai.superhub.service.SuperHubTileService
import com.bfbai.superhub.service.ThemeEngine
import com.bfbai.superhub.service.tr
import com.bfbai.superhub.utils.DiagLog
import com.bfbai.superhub.utils.HistoryRepository
import com.bfbai.superhub.utils.PrivacyPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeEngine.init(this)
        PrivacyPolicy.init(this)
        HistoryRepository.init(this)
        setContent { AppRoot { SettingsScreen { finish() } } }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var page by remember { mutableStateOf("main") }
    BackHandler(enabled = page != "main") { page = "main" }
    if (page == "apps") AppPickerPage { page = "main" } else MainSettings(onBack) { page = "apps" }
}

// ---------- مكونات خاصة بالإعدادات ----------

@Composable
private fun Sw(title: String, key: String, hint: String? = null) {
    ShelfSettings.changes.collectAsState().value
    SwitchLine(title, hint, ShelfSettings.flag(key)) { ShelfSettings.setFlag(key, it) }
}

@Composable
private fun Sl(
    title: String,
    key: String,
    min: Float,
    max: Float,
    fmt: (Float) -> String = { it.toInt().toString() }
) {
    val tick = ShelfSettings.changes.collectAsState().value
    var v by remember(key, tick) { mutableStateOf(ShelfSettings.num(key).coerceIn(min, max)) }
    SliderLine(title, v, min, max, fmt, onChange = { v = it }) { ShelfSettings.setNum(key, v) }
}

@Composable
private fun ColorLine(title: String, key: String, onPick: () -> Unit) {
    ShelfSettings.changes.collectAsState().value
    val p = LocalPal.current
    val c = Color(ShelfSettings.col(key))
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onPick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(32.dp).clip(CircleShape).background(c).border(2.dp, p.stroke, CircleShape))
    }
}

@Composable
private fun Label(text: String) {
    val p = LocalPal.current
    Text(text, color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun Note(text: String) {
    val p = LocalPal.current
    Text(text, color = p.sub, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun Confirm(title: String, text: String, ok: String, onOk: () -> Unit, onCancel: () -> Unit) {
    val p = LocalPal.current
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = p.card,
        title = { Text(title, color = p.text) },
        text = { Text(text, color = p.sub) },
        confirmButton = { TextButton(onClick = onOk) { Text(ok, color = p.danger) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(t("إلغاء", "Cancel"), color = p.sub) } }
    )
}

// ---------- معاينة حية للرف ----------

@Composable
private fun PreviewCard(label: String, color: Color, body: String, mono: Boolean = false) {
    val fill = if (ShelfStyle.cardTintByKind) color else Color(ShelfStyle.cardFillColor)
    val shape = RoundedCornerShape(ShelfStyle.cardCornerDp.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(fill.copy(alpha = ShelfStyle.cardFillAlpha / 255f))
            .border(
                1.dp,
                Color(ShelfStyle.cardStrokeColor).copy(alpha = ShelfStyle.cardStrokeAlpha / 255f),
                shape
            )
            .padding(10.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(
            body,
            color = if (mono) color else Color(ShelfStyle.textColor),
            fontSize = (if (mono) 22f * ShelfStyle.textScale else 13f * ShelfStyle.textScale).sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (ShelfStyle.boldText || mono) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ShelfPreview() {
    ShelfSettings.changes.collectAsState().value
    val p = LocalPal.current
    val shape = RoundedCornerShape(ShelfStyle.cornerDp.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(p.card2, p.bg)))
            .border(1.dp, p.stroke, RoundedCornerShape(20.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(250.dp)
                .clip(shape)
                .background(Color(ShelfStyle.panelColor))
                .border(1.dp, Color(ShelfStyle.panelStroke), shape)
                .padding(12.dp)
        ) {
            Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HubSync", color = Color(ShelfStyle.textColor), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(t("٣ عناصر", "3 items"), color = Color(ShelfStyle.subTextColor), fontSize = 11.sp)
                }
                Text("⋮", color = Color(ShelfStyle.accent), fontSize = 22.sp)
            }
            PreviewCard(t("كود تحقق", "OTP"), Color(0xFFFFB020), "482913", true)
            PreviewCard(t("رابط", "Link"), Color(0xFF4DA3FF), "nile-store.com/orders/48213")
            PreviewCard(
                t("نص", "Text"), Color(ShelfStyle.neutralColor),
                t("العنوان: شارع النيل ١٢، المنيا", "Address: 12 Nile St, Minya")
            )
        }
    }
}

// ---------- منتقي الألوان ----------

@Composable
private fun ColorPickerDialog(title: String, initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPal.current
    val init = remember {
        val f = FloatArray(3)
        android.graphics.Color.colorToHSV(initial, f)
        f
    }
    var h by remember { mutableStateOf(init[0]) }
    var s by remember { mutableStateOf(init[1]) }
    var v by remember { mutableStateOf(init[2]) }
    var a by remember { mutableStateOf(android.graphics.Color.alpha(initial) / 255f) }
    val cur = android.graphics.Color.HSVToColor((a * 255f).toInt().coerceIn(0, 255), floatArrayOf(h, s, v))
    var hex by remember(cur) { mutableStateOf(String.format("%06X", cur and 0xFFFFFF)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(title, color = p.text) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o ->
                                    s = (o.x / size.width).coerceIn(0f, 1f)
                                    v = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                },
                                onDrag = { change, _ ->
                                    s = (change.position.x / size.width).coerceIn(0f, 1f)
                                    v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                s = (o.x / size.width).coerceIn(0f, 1f)
                                v = 1f - (o.y / size.height).coerceIn(0f, 1f)
                            }
                        }
                ) {
                    DrawCanvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(h, 1f, 1f))))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        drawCircle(
                            Color.White, 10.dp.toPx(),
                            Offset(s * size.width, (1f - v) * size.height),
                            style = Stroke(2.dp.toPx())
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o -> h = (o.x / size.width).coerceIn(0f, 1f) * 360f },
                                onDrag = { change, _ -> h = (change.position.x / size.width).coerceIn(0f, 1f) * 360f }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { o -> h = (o.x / size.width).coerceIn(0f, 1f) * 360f }
                        }
                ) {
                    DrawCanvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 1f, 1f) }))
                        drawCircle(
                            Color.White, 10.dp.toPx(),
                            Offset(h / 360f * size.width, size.height / 2f),
                            style = Stroke(2.dp.toPx())
                        )
                    }
                }
                Slider(
                    value = a,
                    onValueChange = { a = it },
                    colors = SliderDefaults.colors(
                        thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.stroke
                    )
                )
                Text(
                    t("الشفافية", "Opacity") + ": " + (a * 100).toInt() + "%",
                    color = p.sub, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(cur)).border(2.dp, p.stroke, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = hex,
                        onValueChange = { x ->
                            val clean = x.uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(6)
                            hex = clean
                            if (clean.length == 6) {
                                val rgb = clean.toLong(16).toInt() or (0xFF shl 24)
                                val f = FloatArray(3)
                                android.graphics.Color.colorToHSV(rgb, f)
                                h = f[0]; s = f[1]; v = f[2]
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = p.text, fontSize = 16.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(p.accent),
                        decorationBox = { inner ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(p.card2)
                                    .border(1.dp, p.stroke, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("#", color = p.sub, fontSize = 16.sp)
                                inner()
                            }
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(cur) }) { Text(t("حفظ", "Save"), color = p.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("إلغاء", "Cancel"), color = p.sub) } }
    )
}

// ---------- اختيار التطبيقات (صفحة كاملة) ----------

private class AppEntry(val pkg: String, val label: String)

private fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return try {
        pm.queryIntentActivities(i, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    d.setBounds(0, 0, size, size)
    d.draw(c)
    return b
}

private val SENSITIVE_WORDS = listOf(
    "bank", "pay", "wallet", "vault", "authenticator", "password", "2fa", "otp", "fawry", "insta",
    "بنك", "محفظة", "فوري", "كلمات", "مصرف"
)

@Composable
private fun AppRow(a: AppEntry, on: Boolean, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val icon by produceState<ImageBitmap?>(null, a.pkg) {
        value = withContext(Dispatchers.IO) {
            try {
                drawableToBitmap(ctx.packageManager.getApplicationIcon(a.pkg), 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(p.card2)) {
            val ic = icon
            if (ic != null) Image(ic, null, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(a.label, color = p.text, fontSize = 14.sp, maxLines = 1)
            Text(a.pkg, color = p.sub, fontSize = 10.sp, maxLines = 1)
        }
        Switch(
            checked = on,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onAccent, checkedTrackColor = p.accent,
                uncheckedThumbColor = p.sub, uncheckedTrackColor = p.card2, uncheckedBorderColor = p.stroke
            )
        )
    }
}

@Composable
private fun AppPickerPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    var mode by remember { mutableStateOf(PrivacyPolicy.mode()) }
    var blocked by remember { mutableStateOf(PrivacyPolicy.blockedApps()) }
    var allowed by remember { mutableStateOf(PrivacyPolicy.allowedApps()) }
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }
    val allow = mode == PrivacyPolicy.MODE_ALLOW_ONLY
    val sel = if (allow) allowed else blocked

    fun commit(n: Set<String>) {
        if (allow) {
            allowed = n
            PrivacyPolicy.setAllowedApps(n)
        } else {
            blocked = n
            PrivacyPolicy.setBlockedApps(n)
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Header(t("اختيار التطبيقات", "Choose apps"), onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            ChipRow {
                Chip(t("استثناء تطبيقات", "Exclude apps"), !allow) {
                    mode = PrivacyPolicy.MODE_ALL
                    PrivacyPolicy.setMode(mode)
                }
                Chip(t("التقاط من تطبيقات محددة بس", "Capture only from"), allow) {
                    mode = PrivacyPolicy.MODE_ALLOW_ONLY
                    PrivacyPolicy.setMode(mode)
                }
            }
            Text(
                if (allow) t("هيتلقط من التطبيقات المختارة بس.", "Only selected apps are captured.")
                else t("التطبيقات المختارة مش هيتلقط منها أي نسخ.", "Selected apps are never captured."),
                color = p.sub, fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${sel.size} " + t("مختار", "selected"), color = p.accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (!allow) {
                    Btn(t("اقتراح تلقائي", "Auto-suggest")) {
                        val list = apps ?: emptyList()
                        val found = list.filter { a ->
                            val k = (a.label + " " + a.pkg).lowercase()
                            SENSITIVE_WORDS.any { k.contains(it) }
                        }.map { it.pkg }
                        commit(sel + found)
                        Toast.makeText(ctx, tr("اتضاف ", "Added ") + found.size, Toast.LENGTH_SHORT).show()
                    }
                }
                Btn(t("مسح الاختيار", "Clear"), danger = true) { commit(emptySet()) }
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                decorationBox = { inner ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.card2)
                            .border(1.dp, p.stroke, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, tint = p.sub)
                        Spacer(Modifier.width(8.dp))
                        Box {
                            if (query.isEmpty()) Text(t("ابحث عن تطبيق", "Search apps"), color = p.sub, fontSize = 14.sp)
                            inner()
                        }
                    }
                }
            )
        }
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
        } else {
            val q = query.trim().lowercase()
            val filtered = list
                .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }
                .sortedByDescending { it.pkg in sel }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.pkg }) { a ->
                    AppRow(a, a.pkg in sel) { commit(if (a.pkg in sel) sel - a.pkg else sel + a.pkg) }
                }
            }
        }
    }
}

// ---------- التطبيقات المختارة (بتظهر في قسم الخصوصية) ----------

@Composable
private fun SelectedAppRow(pkg: String, allow: Boolean) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val label = remember(pkg) {
        try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            try {
                drawableToBitmap(ctx.packageManager.getApplicationIcon(pkg), 72).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(p.card2)
            .border(1.dp, p.stroke, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(p.card)) {
            val ic = icon
            if (ic != null) Image(ic, null, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(label, color = p.text, fontSize = 13.sp, maxLines = 1)
            Text(pkg, color = p.sub, fontSize = 9.sp, maxLines = 1)
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable {
                    val s = HashSet<String>(if (allow) PrivacyPolicy.allowedApps() else PrivacyPolicy.blockedApps())
                    s.remove(pkg)
                    if (allow) PrivacyPolicy.setAllowedApps(s) else PrivacyPolicy.setBlockedApps(s)
                },
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = p.danger, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SelectedApps(allow: Boolean) {
    val p = LocalPal.current
    PrivacyPolicy.changes.collectAsState().value
    val pkgs = (if (allow) PrivacyPolicy.allowedApps() else PrivacyPolicy.blockedApps()).toList().sorted()
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (pkgs.isEmpty()) {
            Text(
                if (allow) t("لسه ما اخترتش أي تطبيق: مفيش حاجة هتتلقط.", "No apps selected yet: nothing will be captured.")
                else t("مفيش تطبيقات مستثناة.", "No excluded apps."),
                color = p.sub, fontSize = 12.sp
            )
        } else {
            for (pk in pkgs.take(15)) SelectedAppRow(pk, allow)
            if (pkgs.size > 15) {
                Text("+" + (pkgs.size - 15), color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ---------- الشاشة الرئيسية للإعدادات ----------

@Composable
private fun MainSettings(onBack: () -> Unit, openApps: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    ShelfSettings.changes.collectAsState().value
    PrivacyPolicy.changes.collectAsState().value
    var running by remember { mutableStateOf(ClipboardAccessibilityService.instance != null) }
    var shown by remember { mutableStateOf(ClipboardAccessibilityService.instance?.shelfShown == true) }
    LaunchedEffect(Unit) {
        while (true) {
            running = ClipboardAccessibilityService.instance != null
            shown = ClipboardAccessibilityService.instance?.shelfShown == true
            delay(1500)
        }
    }
    var dlg by remember { mutableStateOf("") }
    var pickKey by remember { mutableStateOf("") }
    var pickTitle by remember { mutableStateOf("") }
    var pmode by remember { mutableStateOf(PrivacyPolicy.mode()) }
    var wipe by remember { mutableStateOf(PrivacyPolicy.autoWipeDays()) }
    var priv by remember { mutableStateOf(PrivacyPolicy.isPrivateMode()) }
    val ver = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    if (pickKey.isNotEmpty()) {
        ColorPickerDialog(pickTitle, ShelfSettings.col(pickKey), { c ->
            ShelfSettings.setCol(pickKey, c)
            pickKey = ""
        }, { pickKey = "" })
    }
    if (dlg == "reset") {
        Confirm(
            t("إعادة ضبط كل الإعدادات؟", "Reset all settings?"),
            t("هترجع للشكل والسلوك الافتراضي. السجل ما بيتمسحش.", "Look and behavior return to defaults. History is kept."),
            t("إعادة ضبط", "Reset"),
            {
                ShelfSettings.reset()
                AppPrefs.applied = ""
                ThemeEngine.ensure(ctx, true)
                AppIconSwitcher.apply(ctx, "shelf")
                dlg = ""
            }, { dlg = "" }
        )
    }
    if (dlg == "clear") {
        Confirm(
            t("مسح السجل؟", "Clear history?"),
            t("هيتمسح كل شيء ما عدا العناصر المثبّتة.", "Everything except pinned items will be deleted."),
            t("مسح", "Clear"),
            { HistoryRepository.clearUnpinned(); dlg = "" }, { dlg = "" }
        )
    }
    if (dlg == "uninstall") {
        Confirm(
            t("إزالة HubSync؟", "Uninstall HubSync?"),
            t(
                "السجل والصور والإعدادات هتتمسح مع التطبيق. لو عايز تحتفظ بإعداداتك، انسخها من قسم النسخ الاحتياطي الأول. هيفتح صفحة التطبيق في إعدادات النظام، ودوس \"إلغاء التثبيت\".",
                "History, images and settings are removed with the app. Back up your settings first if you want them. This opens the system app page; tap Uninstall there."
            ),
            t("متابعة", "Continue"),
            {
                dlg = ""
                try {
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                } catch (e: Exception) {
                    Toast.makeText(ctx, tr("تعذر فتح صفحة التطبيق", "Could not open the app page"), Toast.LENGTH_SHORT).show()
                }
            }, { dlg = "" }
        )
    }
    if (dlg == "wipe") {
        Confirm(
            t("مسح كل بيانات التطبيق؟", "Erase all app data?"),
            t("هيمسح السجل والصور والإعدادات ويقفل التطبيق. مفيش تراجع.", "Deletes history, images and settings, then closes the app. No undo."),
            t("امسح كل حاجة", "Erase everything"),
            {
                dlg = ""
                try {
                    (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
                } catch (e: Exception) {
                }
            }, { dlg = "" }
        )
    }
    if (dlg == "stop") {
        Confirm(
            t("إيقاف الخدمة؟", "Stop the service?"),
            t(
                "الرف والالتقاط هيقفلوا لحد ما تشغّل الخدمة تاني من إعدادات الإتاحة. مفيد مع تطبيقات (زي البنوك) بترفض تشتغل والإتاحة شغالة.",
                "The shelf and capture stop until you re-enable it in Accessibility settings. Useful for apps (like banks) that refuse to run while accessibility is on."
            ),
            t("إيقاف", "Stop"),
            {
                dlg = ""
                try {
                    ClipboardAccessibilityService.instance?.disableSelf()
                } catch (e: Exception) {
                }
            }, { dlg = "" }
        )
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Header(t("الإعدادات", "Settings"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            // ---- الحالة ----
            Glass(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val cur = AppIconSwitcher.current(ctx)
                    val res = AppIconSwitcher.options.firstOrNull { it.key == cur }?.res ?: "ic_hubsync"
                    val id = remember(res) { ctx.resources.getIdentifier(res, "mipmap", ctx.packageName) }
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(p.card2)) {
                        if (id != 0) {
                            AndroidView(factory = { c -> ImageView(c).apply { setImageResource(id) } }, modifier = Modifier.size(56.dp))
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("HubSync", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (running) t("● الخدمة شغالة", "● Service running") else t("● الخدمة مقفولة", "● Service is off"),
                            color = if (running) p.accent else p.danger, fontSize = 13.sp
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 8.dp, bottom = 12.dp)
                ) {
                    Btn(t("إعدادات الإتاحة", "Accessibility"), filled = !running) {
                        val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                    }
                    Btn(t("السجل", "History")) { ctx.startActivity(Intent(ctx, HistoryActivity::class.java)) }
                    if (running) {
                        Btn(if (shown) t("إخفاء الرف", "Hide shelf") else t("إظهار الرف", "Show shelf")) {
                            val s = ClipboardAccessibilityService.instance
                            if (s != null) shown = s.toggleShelf()
                        }
                    }
                }
            }
            ShelfPreview()

            // ---- عام ----
            SectionCard(t("عام", "General"), Icons.Default.Settings, true) {
                Label(t("اللغة", "Language"))
                ChipRow {
                    Chip("العربية", AppPrefs.lang == "ar") { AppPrefs.lang = "ar"; ShelfSettings.notifyChanged() }
                    Chip("English", AppPrefs.lang == "en") { AppPrefs.lang = "en"; ShelfSettings.notifyChanged() }
                }
                Label(t("المظهر", "Theme"))
                ChipRow {
                    Chip(t("تلقائي (النظام)", "System"), AppPrefs.themeMode == "system") {
                        AppPrefs.themeMode = "system"; ThemeEngine.ensure(ctx); ShelfSettings.notifyChanged()
                    }
                    Chip(t("داكن", "Dark"), AppPrefs.themeMode == "dark") {
                        AppPrefs.themeMode = "dark"; ThemeEngine.ensure(ctx); ShelfSettings.notifyChanged()
                    }
                    Chip(t("نهاري", "Day"), AppPrefs.themeMode == "day") {
                        AppPrefs.themeMode = "day"; ThemeEngine.ensure(ctx); ShelfSettings.notifyChanged()
                    }
                }
                Label(t("ستايل الوضع الداكن", "Dark style"))
                ChipRow {
                    Chip(t("كلاسيكي", "Classic"), AppPrefs.darkStyle == "classic") {
                        AppPrefs.darkStyle = "classic"; ThemeEngine.ensure(ctx, true)
                    }
                    Chip(t("زجاجي", "Glass"), AppPrefs.darkStyle == "glass") {
                        AppPrefs.darkStyle = "glass"; ThemeEngine.ensure(ctx, true)
                    }
                    Chip("Aurora", AppPrefs.darkStyle == "aurora") { AppPrefs.darkStyle = "aurora"; ThemeEngine.ensure(ctx, true) }
                    Chip("AMOLED", AppPrefs.darkStyle == "amoled") { AppPrefs.darkStyle = "amoled"; ThemeEngine.ensure(ctx, true) }
                }
                Note(t("تغيير الثيم أو الستايل بيرجّع ألوانه الأصلية. الألوان المخصصة بتفضل طالما الثيم ثابت.", "Changing theme/style restores its original colors. Custom colors stay while the theme is unchanged."))
            }

            // ---- المظهر ----
            SectionCard(t("المظهر", "Appearance"), Icons.Default.Star) {
                Sl(t("شفافية اللوحة", "Panel opacity"), "panelAlpha", 0.3f, 1f) { "${(it * 100).toInt()}%" }
                Sl(t("شفافية الكروت", "Card opacity"), "cardFillAlpha", 16f, 255f) { "${(it / 255f * 100).toInt()}%" }
                Sl(t("وضوح حدود الكروت", "Card border"), "cardStrokeAlpha", 0f, 255f) { "${(it / 255f * 100).toInt()}%" }
                Sl(t("استدارة اللوحة", "Panel radius"), "cornerDp", 0f, 32f)
                Sl(t("استدارة الكروت", "Card radius"), "cardCornerDp", 0f, 28f)
                Sl(t("حجم الخط", "Text size"), "textScale", 0.8f, 1.4f) { "%.2fx".format(it) }
                Sw(t("خط عريض", "Bold text"), "boldText")
                Sw(t("تدرج في اللوحة", "Panel gradient"), "panelGradient")
                Sw(t("تدرج في الكروت", "Card gradient"), "cardGradient")
                Sw(t("لون الكارت حسب نوعه", "Color cards by type"), "cardTintByKind", t("كود، رابط، هاتف، بريد", "OTP, link, phone, email"))
                Sw(t("ألوان متبدّلة للنصوص", "Rotating text colors"), "rotateTextColors")
                Label(t("الخط", "Font"))
                val font = ShelfSettings.txt("fontFamily")
                ChipRow {
                    Chip(t("النظام", "System"), font == "") { ShelfSettings.setTxt("fontFamily", "") }
                    Chip(t("عادي", "Sans"), font == "sans-serif") { ShelfSettings.setTxt("fontFamily", "sans-serif") }
                    Chip(t("مضغوط", "Condensed"), font == "sans-serif-condensed") { ShelfSettings.setTxt("fontFamily", "sans-serif-condensed") }
                    Chip(t("مسطّر", "Serif"), font == "serif") { ShelfSettings.setTxt("fontFamily", "serif") }
                    Chip("Mono", font == "monospace") { ShelfSettings.setTxt("fontFamily", "monospace") }
                    Chip(t("حر", "Casual"), font == "casual") { ShelfSettings.setTxt("fontFamily", "casual") }
                    Chip(t("خط يد", "Cursive"), font == "cursive") { ShelfSettings.setTxt("fontFamily", "cursive") }
                }
                Label(t("الألوان (دوس لاختيار لون)", "Colors (tap to pick)"))
                ColorLine(t("لون التمييز", "Accent"), "accent") { pickKey = "accent"; pickTitle = tr("لون التمييز", "Accent") }
                ColorLine(t("لون اللوحة", "Panel"), "panelColor") { pickKey = "panelColor"; pickTitle = tr("لون اللوحة", "Panel") }
                ColorLine(t("لون الكروت", "Cards"), "cardFillColor") { pickKey = "cardFillColor"; pickTitle = tr("لون الكروت", "Cards") }
                ColorLine(t("حدود الكروت", "Card border"), "cardStrokeColor") { pickKey = "cardStrokeColor"; pickTitle = tr("حدود الكروت", "Card border") }
                ColorLine(t("لون النص", "Text"), "textColor") { pickKey = "textColor"; pickTitle = tr("لون النص", "Text") }
                ColorLine(t("النص الثانوي", "Secondary text"), "subTextColor") { pickKey = "subTextColor"; pickTitle = tr("النص الثانوي", "Secondary text") }
                ColorLine(t("لون المقبض", "Handle"), "handleColor") { pickKey = "handleColor"; pickTitle = tr("لون المقبض", "Handle") }
                ColorLine(t("لون القايمة", "Menu"), "menuColor") { pickKey = "menuColor"; pickTitle = tr("لون القايمة", "Menu") }
            }

            // ---- الرف ----
            SectionCard(t("الرف", "Shelf"), Icons.Default.Menu) {
                Sl(t("ارتفاع الرف المفتوح", "Open height"), "expandedHeightDp", 300f, 900f) { "${it.toInt()} dp" }
                Sl(t("عرض الرف المفتوح", "Open width"), "expandedWidthDp", 220f, 380f) { "${it.toInt()} dp" }
                Sl(t("ارتفاع المعاينة", "Preview height"), "peekHeightDp", 120f, 380f) { "${it.toInt()} dp" }
                Sl(t("عرض الشريط", "Bar width"), "closedWidthDp", 14f, 44f) { "${it.toInt()} dp" }
                Sl(t("ارتفاع الشريط", "Bar height"), "closedHeightDp", 80f, 280f) { "${it.toInt()} dp" }
                Sl(t("قرب الحافة اللي بيلتصق عندها", "Snap distance"), "snapThresholdDp", 8f, 72f) { "${it.toInt()} dp" }
                Sw(t("يروح لأقرب جنب بعد فترة", "Drift to nearest edge"), "idleDrift")
                Sl(t("المدة قبل ما يروح جنب", "Drift delay"), "idleDriftSec", 2f, 30f) { "${it.toInt()} s" }
                Sl(t("القفل التلقائي بعد", "Auto close after"), "autoCloseSec", 3f, 60f) { "${it.toInt()} s" }
                Sw(t("ضبابية الخلفية", "Background blur"), "blurEnabled", t("أندرويد 12+ ولو الجهاز بيدعمها", "Android 12+ if supported"))
                Sl(t("شدة الضبابية", "Blur strength"), "blurRadiusDp", 4f, 60f) { "${it.toInt()} dp" }
            }

            // ---- الحركات ----
            SectionCard(t("الحركات", "Gestures"), Icons.Default.Refresh) {
                Label(t("الحذف بالسحب", "Swipe to delete"))
                Sl(t("نسبة السحب للحذف", "Swipe distance"), "swipeThreshold", 0.2f, 0.8f) { "${(it * 100).toInt()}%" }
                ColorLine(t("لون الحذف (البداية)", "Delete color (start)"), "deleteColorStart") { pickKey = "deleteColorStart"; pickTitle = tr("لون الحذف", "Delete color") }
                ColorLine(t("لون الحذف (النهاية)", "Delete color (end)"), "deleteColorEnd") { pickKey = "deleteColorEnd"; pickTitle = tr("لون الحذف", "Delete color") }
                Label(t("الرمي للإغلاق", "Throw to close"))
                Sw(t("ارمي الرف بقوة فيختفي", "Throw the shelf away"), "throwEnabled")
                val ta = ShelfSettings.txt("throwAction")
                ChipRow {
                    Chip(t("يختفي (يرجع من الـ Tile)", "Hide (back via tile)"), ta == "hide") { ShelfSettings.setTxt("throwAction", "hide") }
                    Chip(t("يتطوي لشريط", "Collapse to bar"), ta == "collapse") { ShelfSettings.setTxt("throwAction", "collapse") }
                }
                Sl(t("حساسية الرمي", "Throw sensitivity"), "throwMinSpeedDp", 500f, 3000f) { "${it.toInt()}" }
                Note(t("رقم أقل = أسهل في الرمي.", "Lower = easier to throw."))
            }

            // ---- السحب والإفلات ----
            SectionCard(t("السحب والإفلات", "Drag & drop"), Icons.Default.Share) {
                Sw(t("السحب من أي تطبيق يظهر منطقة الإفلات", "Dragging from apps shows the drop zone"), "dragWakesShelf")
                Sw(t("السحب داخل الرف يضم الكارت لمجموعة", "Drag inside the shelf to group"), "dragToGroup")
                Sl(t("عرض منطقة الإفلات", "Drop zone width"), "dropZoneWidthDp", 60f, 220f) { "${it.toInt()} dp" }
                Sl(t("ارتفاع منطقة الإفلات", "Drop zone height"), "dropZoneHeightDp", 120f, 420f) { "${it.toInt()} dp" }
                Note(t("سحب الصور محتاج صلاحية الصور. المشاركة (Share) هي الأضمن.", "Dragging images needs the Photos permission. Share is the most reliable."))
            }

            // ---- الالتقاط ----
            SectionCard(t("الالتقاط", "Capture"), Icons.Default.Search) {
                Sw(t("الالتقاط التلقائي عند النسخ", "Auto capture on copy"), "autoCapture", t("بيستخدم تركيز لحظي فممكن تلاحظ رمشة", "Uses a brief focus, you may notice a flicker"))
                Sw(t("تجاهل المحتوى الحساس", "Ignore sensitive content"), "ignoreSensitive", t("زي كلمات المرور", "e.g. passwords"))
                Label(t("لما يجي نسخ جديد", "On new copy"))
                val oc = ShelfSettings.txt("openOnCopy")
                ChipRow {
                    Chip(t("معاينة صغيرة", "Small peek"), oc == "peek") { ShelfSettings.setTxt("openOnCopy", "peek") }
                    Chip(t("يفتح كامل", "Open fully"), oc == "full") { ShelfSettings.setTxt("openOnCopy", "full") }
                    Chip(t("نبضة بس", "Pulse only"), oc == "pulse") { ShelfSettings.setTxt("openOnCopy", "pulse") }
                }
                Sl(t("مدة المعاينة", "Peek duration"), "peekMs", 1000f, 8000f) { "%.1f s".format(it / 1000f) }
                Sl(t("أقصى طول للنص في الكارت", "Max text in card"), "previewChars", 100f, 1500f) { "${it.toInt()}" }
                Sw(t("رسائل التشخيص الصغيرة", "Debug toasts"), "debug", t("اقفلها لما كل حاجة تشتغل", "Turn off when all works"))
            }

            // ---- المجموعات واللصق ----
            SectionCard(t("المجموعات واللصق", "Groups & paste"), Icons.Default.Add) {
                Sw(t("تجميع تلقائي للنسخ المتقارب", "Auto-group close copies"), "autoGroup")
                Sl(t("المدة بين النسختين", "Time between copies"), "groupWindowSec", 15f, 600f) { "${it.toInt()} s" }
                Sw(t("نفس التطبيق بس", "Same app only"), "groupSameApp")
                Sw(t("كشف ملف كود (يوسّع المدة لـ 5 دقايق)", "Code-file detection (5 min window)"), "groupCodeFile")
                Sw(t("اسم المجموعة من اسم الملف", "Name group after file"), "groupAutoName")
                Sw(t("المجموعات تبدأ مطوية", "Groups start collapsed"), "groupsStartCollapsed")
                Sl(t("المجموعة اليدوية تتقفل بعد", "Manual group closes after"), "manualIdleMin", 0f, 120f) {
                    if (it.toInt() == 0) tr("أبداً", "never") else "${it.toInt()} min"
                }
                Label(t("بعد اللصق", "After paste"))
                Sw(t("اخفّت الكارت اللي اتلصق", "Dim pasted cards"), "pasteDim")
                Sl(t("درجة الخفوت", "Dim level"), "pastedAlpha", 0.2f, 1f) { "${(it * 100).toInt()}%" }
                Sw(t("احذف العنصر بعد لصقه", "Remove after paste"), "autoRemoveAfterPaste")
                Sw(t("اللي لسه ما اتلصقش قبل اللي اتلصق", "Unpasted first"), "sortUnpastedFirst")
                Sw(t("امسح أكواد التحقق لوحدها", "Auto-delete OTP codes"), "otpAutoDelete")
                Sl(t("مدة كود التحقق", "OTP lifetime"), "otpTtlSec", 15f, 300f) { "${it.toInt()} s" }
            }

            // ---- الخصوصية ----
            SectionCard(t("الخصوصية", "Privacy"), Icons.Default.Lock) {
                Label(t("مين يتلقط منه؟", "Capture from"))
                ChipRow {
                    Chip(t("الكل ما عدا المستثنى", "All except excluded"), pmode == PrivacyPolicy.MODE_ALL) {
                        pmode = PrivacyPolicy.MODE_ALL; PrivacyPolicy.setMode(pmode)
                    }
                    Chip(t("المختار بس", "Selected only"), pmode == PrivacyPolicy.MODE_ALLOW_ONLY) {
                        pmode = PrivacyPolicy.MODE_ALLOW_ONLY; PrivacyPolicy.setMode(pmode)
                    }
                }
                val allowMode = pmode == PrivacyPolicy.MODE_ALLOW_ONLY
                Note(
                    if (allowMode) t("هيتلقط من التطبيقات المختارة بس.", "Only selected apps are captured.")
                    else t("التطبيقات المختارة مش هيتلقط منها أي نسخ.", "Selected apps are never captured.")
                )
                SelectedApps(allowMode)
                Btn(t("اختيار التطبيقات", "Choose apps"), filled = true) { openApps() }

                SwitchLine(
                    t("تجاهل النسخ لو مصدرها مجهول", "Ignore copies from unknown sources"),
                    t(
                        "تطبيقات بتمنع الإتاحة (زي البنوك) ما بنقدرش نعرف اسمها. بيشتغل لو فيه تطبيقات مستثناة.",
                        "Apps that block accessibility (banks) cannot be identified. Applies when you have excluded apps."
                    ),
                    PrivacyPolicy.blockUnknown()
                ) { PrivacyPolicy.setBlockUnknown(it) }

                Label(t("الوضع الخاص", "Private mode"))
                Btn(if (priv) t("إيقاف الوضع الخاص", "Stop private mode") else t("تشغيل الوضع الخاص (30 دقيقة)", "Start private mode (30 min)")) {
                    if (priv) PrivacyPolicy.stopPrivate() else PrivacyPolicy.startPrivate(30)
                    priv = PrivacyPolicy.isPrivateMode()
                }
                Label(t("المسح التلقائي للأقدم", "Auto-wipe older items"))
                ChipRow {
                    Chip(t("مقفول", "Off"), wipe == 0) { wipe = 0; PrivacyPolicy.setAutoWipeDays(0) }
                    Chip(t("7 أيام", "7 days"), wipe == 7) { wipe = 7; PrivacyPolicy.setAutoWipeDays(7) }
                    Chip(t("30 يوم", "30 days"), wipe == 30) { wipe = 30; PrivacyPolicy.setAutoWipeDays(30) }
                    Chip(t("90 يوم", "90 days"), wipe == 90) { wipe = 90; PrivacyPolicy.setAutoWipeDays(90) }
                }
                Note(t("بيتطبق لما التطبيق يشتغل. المثبّت بيفضل.", "Applied on app start. Pinned items are kept."))
                Btn(t("امسح السجل دلوقتي", "Clear history now"), danger = true) { dlg = "clear" }
            }

            // ---- الأيقونات ----
            SectionCard(t("الأيقونات", "Icons"), Icons.Default.Home) {
                Label(t("أيقونة التطبيق", "App icon"))
                val curApp = AppIconSwitcher.current(ctx)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (o in AppIconSwitcher.options) {
                        val id = remember(o.res) { ctx.resources.getIdentifier(o.res, "mipmap", ctx.packageName) }
                        val selected = curApp == o.key
                        val shape = RoundedCornerShape(16.dp)
                        Column(
                            modifier = Modifier
                                .clip(shape)
                                .background(if (selected) p.accent.copy(alpha = 0.16f) else p.card2)
                                .border(if (selected) 2.dp else 1.dp, if (selected) p.accent else p.stroke, shape)
                                .clickable {
                                    ShelfSettings.setAppIcon(o.key)
                                    val ok = AppIconSwitcher.apply(ctx, o.key)
                                    Toast.makeText(
                                        ctx,
                                        if (ok) tr("اتغيّرت. لو ما ظهرتش في Apps استخدم زرار الاختصار تحت.", "Changed. If Apps still shows the old one, use the shortcut button below.")
                                        else tr("تعذر التبديل", "Could not switch"),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (id != 0) {
                                AndroidView(factory = { c -> ImageView(c).apply { setImageResource(id) } }, modifier = Modifier.size(52.dp))
                            } else {
                                Box(Modifier.size(52.dp))
                            }
                            Text(if (Lang_isAr()) o.ar else o.en, color = p.text, fontSize = 12.sp)
                        }
                    }
                }
                Btn(t("أضف اختصاراً بالأيقونة دي على الشاشة الرئيسية", "Add a home-screen shortcut with this icon")) {
                    val ok = AppIconSwitcher.pinShortcut(ctx, curApp)
                    if (!ok) Toast.makeText(ctx, tr("اللانشر ما بيدعمش إضافة الاختصارات", "Launcher does not support pinned shortcuts"), Toast.LENGTH_LONG).show()
                }
                val vis = AppIconSwitcher.visibleLaunchers(ctx)
                Note(t("اللانشر شايف: ", "Launcher sees: ") + (if (vis.isEmpty()) "—" else vis.joinToString(", ")))
                Label(t("أيقونة زرار لوحة التحكم", "Quick-settings tile icon"))
                val curTile = ShelfSettings.tileIcon()
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (o in IconOptions.tiles) {
                        val id = remember(o.third) { ctx.resources.getIdentifier(o.third, "drawable", ctx.packageName) }
                        val selected = curTile == o.first
                        val shape = RoundedCornerShape(16.dp)
                        Column(
                            modifier = Modifier
                                .clip(shape)
                                .background(if (selected) p.accent.copy(alpha = 0.16f) else p.card2)
                                .border(if (selected) 2.dp else 1.dp, if (selected) p.accent else p.stroke, shape)
                                .clickable {
                                    ShelfSettings.setTileIcon(o.first)
                                    SuperHubTileService.live?.refresh()
                                    try {
                                        TileService.requestListeningState(ctx, ComponentName(ctx, SuperHubTileService::class.java))
                                    } catch (e: Exception) {
                                    }
                                }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (id != 0) {
                                AndroidView(
                                    factory = { c -> ImageView(c).apply { setImageResource(id); setColorFilter(p.text.toArgb()) } },
                                    modifier = Modifier.size(40.dp)
                                )
                            } else {
                                Box(Modifier.size(40.dp))
                            }
                            Text(o.second, color = p.text, fontSize = 12.sp)
                        }
                    }
                }
            }

                        // ---- نسخ احتياطي ----
            SectionCard(t("نسخ احتياطي واستعادة", "Backup & restore"), Icons.Default.Check) {
                Btn(t("نسخ الإعدادات (JSON) للحافظة", "Copy settings (JSON)")) {
                    HistoryRepository.ignoreClipUntil = System.currentTimeMillis() + 1500
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("hubsync-settings", ShelfSettings.exportJson()))
                    Toast.makeText(ctx, tr("اتنسخت الإعدادات", "Settings copied"), Toast.LENGTH_SHORT).show()
                }
                Btn(t("مشاركة الإعدادات", "Share settings")) {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, ShelfSettings.exportJson())
                    ctx.startActivity(Intent.createChooser(i, "HubSync"))
                }
                Btn(t("استيراد من الحافظة", "Import from clipboard")) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val txt = try {
                        cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                    } catch (e: Exception) {
                        null
                    }
                    if (txt.isNullOrBlank()) {
                        Toast.makeText(ctx, tr("الحافظة فاضية", "Clipboard is empty"), Toast.LENGTH_SHORT).show()
                    } else if (ShelfSettings.importJson(txt)) {
                        AppIconSwitcher.apply(ctx, ShelfSettings.appIcon())
                        Toast.makeText(ctx, tr("اتستوردت الإعدادات", "Settings imported"), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, tr("ده مش ملف إعدادات HubSync", "Not a HubSync settings file"), Toast.LENGTH_LONG).show()
                    }
                }
                Btn(t("إعادة ضبط كل الإعدادات", "Reset all settings"), danger = true) { dlg = "reset" }
            }

            // ---- استكشاف الأخطاء ----
            SectionCard(t("استكشاف الأخطاء", "Troubleshooting"), Icons.Default.Warning) {
                Note(
                    if (running) t("الخدمة شغالة.", "The service is running.")
                    else t("الخدمة مقفولة: فعّلها من إعدادات الإتاحة.", "The service is off: enable it in Accessibility settings.")
                )
                Btn(t("فتح إعدادات الإتاحة", "Open Accessibility settings")) {
                    val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                }
                Btn(t("نسخ سجل التشخيص", "Copy diagnostics log")) {
                    HistoryRepository.ignoreClipUntil = System.currentTimeMillis() + 1500
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val dump = DiagLog.dump(300).ifBlank { "(empty)" }
                    cm.setPrimaryClip(ClipData.newPlainText("hubsync-diag", dump))
                    Toast.makeText(ctx, tr("اتنسخ سجل التشخيص", "Diagnostics copied"), Toast.LENGTH_SHORT).show()
                }
                Btn(t("مسح سجل التشخيص", "Clear diagnostics log")) {
                    DiagLog.clear()
                    Toast.makeText(ctx, tr("اتمسح", "Cleared"), Toast.LENGTH_SHORT).show()
                }
                Btn(t("إيقاف الخدمة الآن", "Stop the service now"), danger = true) { dlg = "stop" }
                Note(
                    t(
                        "بعض التطبيقات (زي البنوك وانستا باي) بترفض تفتح لو أي خدمة إتاحة شغالة. وقّف الخدمة قبل ما تفتحها، وفعّلها تاني بعدين.",
                        "Some apps (banks, payment apps) refuse to open while any accessibility service is on. Stop the service before opening them and enable it again after."
                    )
                )
            }

            // ---- حول وإزالة ----
            SectionCard(t("حول التطبيق", "About"), Icons.Default.Info) {
                Text("HubSync $ver", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Note(t("الرف الذكي لنسخ ولصق النصوص والصور.", "A smart shelf for copied text and images."))
                Btn(t("إزالة التطبيق", "Uninstall"), danger = true) { dlg = "uninstall" }
                Btn(t("مسح كل بيانات التطبيق", "Erase all app data"), danger = true) { dlg = "wipe" }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// اختصار صغير لاستخدام اللغة جوه الشرط (بيتحدث مع التركيب لأنه بيقرا الحالة)
@Composable
private fun Lang_isAr(): Boolean = LocalLang.current != "en"
