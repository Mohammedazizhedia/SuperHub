// Built with BFB.AI
package com.bfbai.superhub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bfbai.superhub.service.ClipboardAccessibilityService
import com.bfbai.superhub.service.IconOptions
import com.bfbai.superhub.service.ShelfSettings
import com.bfbai.superhub.service.SuperHubTileService
import com.bfbai.superhub.utils.HistoryRepository
import com.bfbai.superhub.utils.PrivacyPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B1B3A)
private val CardBg = Color(0xFF13264D)
private val Teal = Color(0xFF4DE1E1)
private val TextMain = Color.White
private val TextSub = Color(0xFF9FB3D1)
private val Danger = Color(0xFFFF6B6B)

private val PALETTE: List<Int> = listOf(
    0xFF4DE1E1.toInt(), 0xFF06B6D4.toInt(), 0xFFA855F7.toInt(), 0xFFFF8FB1.toInt(),
    0xFFF28B82.toInt(), 0xFFFFB020.toInt(), 0xFFFFD54F.toInt(), 0xFF3DDC97.toInt(),
    0xFF4DA3FF.toInt(), 0xFF1E3A6D.toInt(), 0xFF0B1B3A.toInt(), 0xFF0F172A.toInt(),
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFF5F7FB.toInt(), 0xFF9FB3D1.toInt()
)

private class AppEntry(val pkg: String, val label: String)

private fun rgbOf(c: Int): Int = c and 0x00FFFFFF
private fun withRgb(old: Int, rgb: Int): Int = ((old ushr 24) shl 24) or rgb

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

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShelfSettings.init(this)
        PrivacyPolicy.init(this)
        HistoryRepository.init(this)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ---------- مكونات صغيرة ----------

@Composable
private fun Section(title: String, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(shape)
            .background(CardBg)
            .border(1.dp, Teal.copy(alpha = 0.25f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(if (open) "▾" else "▸", color = Teal)
        }
        if (open) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                content = content
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Teal else Color(0x1FFFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Bg else TextMain,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun ActionButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (danger) Color(0x33FF6B6B) else Color(0x264DE1E1))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (danger) Danger else Teal, fontSize = 14.sp)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SwitchRow(title: String, key: String, hint: String? = null) {
    ShelfSettings.changes.collectAsState().value
    val checked = ShelfSettings.flag(key)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 14.sp)
            if (hint != null) Text(hint, color = TextSub, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = { ShelfSettings.setFlag(key, it) })
    }
}

@Composable
private fun NumSlider(
    title: String,
    key: String,
    min: Float,
    max: Float,
    fmt: (Float) -> String = { it.toInt().toString() }
) {
    val tick = ShelfSettings.changes.collectAsState().value
    var value by remember(key, tick) { mutableStateOf(ShelfSettings.num(key).coerceIn(min, max)) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(title, color = TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(fmt(value), color = Teal, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = min..max,
            onValueChangeFinished = { ShelfSettings.setNum(key, value) },
            colors = SliderDefaults.colors(thumbColor = Teal, activeTrackColor = Teal)
        )
    }
}

@Composable
private fun ColorRow(title: String, key: String) {
    ShelfSettings.changes.collectAsState().value
    val cur = ShelfSettings.col(key)
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(title, color = TextMain, fontSize = 14.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (p in PALETTE) {
                val sel = rgbOf(cur) == rgbOf(p)
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(p))
                        .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Color(0x55FFFFFF), CircleShape)
                        .clickable { ShelfSettings.setCol(key, withRgb(cur, rgbOf(p))) }
                )
            }
        }
    }
}

@Composable
private fun IconPreview(
    resName: String,
    folder: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    val id = remember(resName) { ctx.resources.getIdentifier(resName, folder, ctx.packageName) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color(0x334DE1E1) else Color(0x14FFFFFF))
            .border(if (selected) 2.dp else 1.dp, if (selected) Teal else Color(0x33FFFFFF), shape)
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (id != 0) {
            AndroidView(
                factory = { c -> ImageView(c).apply { setImageResource(id) } },
                modifier = Modifier.size(52.dp)
            )
        } else {
            Box(modifier = Modifier.size(52.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextMain, fontSize = 12.sp)
    }
}

@Composable
private fun AppPickerDialog(
    title: String,
    initial: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val apps by produceState<List<AppEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadApps(ctx) }
    }
    var sel by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val list = apps
            if (list == null) {
                CircularProgressIndicator()
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.pkg }) { a ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sel = if (a.pkg in sel) sel - a.pkg else sel + a.pkg }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = a.pkg in sel,
                                onCheckedChange = { c -> sel = if (c) sel + a.pkg else sel - a.pkg }
                            )
                            Text(a.label, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(sel) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

// ---------- الشاشة ----------

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    ShelfSettings.changes.collectAsState().value
    var confirmReset by remember { mutableStateOf(false) }
    var pickApps by remember { mutableStateOf(false) }
    var pmode by remember { mutableStateOf(PrivacyPolicy.mode()) }
    var wipe by remember { mutableStateOf(PrivacyPolicy.autoWipeDays()) }
    var priv by remember { mutableStateOf(PrivacyPolicy.isPrivateMode()) }
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("إعادة ضبط كل الإعدادات؟") },
            text = { Text("هترجع للشكل والسلوك الافتراضي. السجل ما بيتمسحش.") },
            confirmButton = {
                TextButton(onClick = {
                    ShelfSettings.reset()
                    IconOptions.applyApp(ctx, "shelf")
                    confirmReset = false
                    Toast.makeText(ctx, "اترجعت الإعدادات الافتراضية", Toast.LENGTH_SHORT).show()
                }) { Text("إعادة ضبط") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("إلغاء") } }
        )
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
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } }
        )
    }

    if (pickApps) {
        val allowOnly = pmode == PrivacyPolicy.MODE_ALLOW_ONLY
        AppPickerDialog(
            title = if (allowOnly) "التقط من هذي التطبيقات بس" else "تطبيقات مستثناة من الالتقاط",
            initial = if (allowOnly) PrivacyPolicy.allowedApps() else PrivacyPolicy.blockedApps(),
            onDone = { s ->
                if (allowOnly) PrivacyPolicy.setAllowedApps(s) else PrivacyPolicy.setBlockedApps(s)
                pickApps = false
            },
            onDismiss = { pickApps = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("رجوع", color = Teal, fontSize = 14.sp, modifier = Modifier.clickable { onBack() })
            Text(
                text = "إعدادات HubSync",
                color = TextMain,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ---- الحالة ----
            val running = ClipboardAccessibilityService.instance != null
            Section("الحالة", true) {
                Text(
                    text = if (running) "✓ خدمة الإتاحة شغالة" else "✗ خدمة الإتاحة مقفولة، الرف مش هيشتغل",
                    color = if (running) Teal else Danger,
                    fontSize = 14.sp
                )
                ActionButton("فتح إعدادات الإتاحة") {
                    val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                }
                ActionButton("فتح السجل") { ctx.startActivity(Intent(ctx, HistoryActivity::class.java)) }
            }

            // ---- المظهر ----
            Section("المظهر") {
                Text("ثيمات جاهزة", color = TextSub, fontSize = 12.sp)
                ChipRow {
                    Chip("كلاسيكي", false) { ShelfSettings.applyPreset("classic") }
                    Chip("زجاجي", false) { ShelfSettings.applyPreset("glass") }
                    Chip("Aurora", false) { ShelfSettings.applyPreset("aurora") }
                    Chip("نهاري", false) { ShelfSettings.applyPreset("day") }
                    Chip("AMOLED", false) { ShelfSettings.applyPreset("amoled") }
                }
                NumSlider("شفافية اللوحة", "panelAlpha", 0.3f, 1f) { "${(it * 100).toInt()}%" }
                NumSlider("شفافية الكروت", "cardFillAlpha", 16f, 255f) { "${(it / 255f * 100).toInt()}%" }
                NumSlider("وضوح حدود الكروت", "cardStrokeAlpha", 0f, 255f) { "${(it / 255f * 100).toInt()}%" }
                NumSlider("استدارة اللوحة", "cornerDp", 0f, 32f)
                NumSlider("استدارة الكروت", "cardCornerDp", 0f, 28f)
                NumSlider("حجم الخط", "textScale", 0.8f, 1.4f) { "%.2fx".format(it) }
                SwitchRow("خط عريض", "boldText")
                SwitchRow("تدرج في اللوحة", "panelGradient")
                SwitchRow("تدرج في الكروت", "cardGradient")
                SwitchRow("لون الكارت حسب نوعه", "cardTintByKind", "كود، رابط، هاتف، بريد")
                SwitchRow("ألوان متبدّلة للنصوص", "rotateTextColors")
                Text("الخط", color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                val font = ShelfSettings.txt("fontFamily")
                ChipRow {
                    Chip("النظام", font == "") { ShelfSettings.setTxt("fontFamily", "") }
                    Chip("عادي", font == "sans-serif") { ShelfSettings.setTxt("fontFamily", "sans-serif") }
                    Chip("مضغوط", font == "sans-serif-condensed") { ShelfSettings.setTxt("fontFamily", "sans-serif-condensed") }
                    Chip("مسطّر", font == "serif") { ShelfSettings.setTxt("fontFamily", "serif") }
                    Chip("آلة كاتبة", font == "monospace") { ShelfSettings.setTxt("fontFamily", "monospace") }
                    Chip("حر", font == "casual") { ShelfSettings.setTxt("fontFamily", "casual") }
                    Chip("خط يد", font == "cursive") { ShelfSettings.setTxt("fontFamily", "cursive") }
                }
                ColorRow("لون التمييز", "accent")
                ColorRow("لون اللوحة", "panelColor")
                ColorRow("لون الكروت", "cardFillColor")
                ColorRow("لون حدود الكروت", "cardStrokeColor")
                ColorRow("لون النص", "textColor")
                ColorRow("لون النص الثانوي", "subTextColor")
                ColorRow("لون المقبض", "handleColor")
                ColorRow("لون القايمة", "menuColor")
            }

            // ---- الرف ----
            Section("الرف") {
                NumSlider("ارتفاع الرف المفتوح", "expandedHeightDp", 300f, 900f) { "${it.toInt()} dp" }
                NumSlider("عرض الرف المفتوح", "expandedWidthDp", 220f, 380f) { "${it.toInt()} dp" }
                NumSlider("ارتفاع المعاينة", "peekHeightDp", 120f, 380f) { "${it.toInt()} dp" }
                NumSlider("عرض الشريط", "closedWidthDp", 14f, 44f) { "${it.toInt()} dp" }
                NumSlider("ارتفاع الشريط", "closedHeightDp", 80f, 280f) { "${it.toInt()} dp" }
                NumSlider("قرب الحافة اللي بيلتصق عندها", "snapThresholdDp", 8f, 72f) { "${it.toInt()} dp" }
                SwitchRow("يروح لأقرب جنب بعد فترة", "idleDrift", "لو سبته عايم في نص الشاشة")
                NumSlider("المدة قبل ما يروح جنب", "idleDriftSec", 2f, 30f) { "${it.toInt()} ث" }
                NumSlider("القفل التلقائي بعد", "autoCloseSec", 3f, 60f) { "${it.toInt()} ث" }
                SwitchRow("ضبابية الخلفية", "blurEnabled", "أندرويد 12 وما فوق، لو الجهاز بيدعمها")
                NumSlider("شدة الضبابية", "blurRadiusDp", 4f, 60f) { "${it.toInt()} dp" }
            }

            // ---- الحركات ----
            Section("الحركات") {
                Text("الحذف بالسحب", color = TextSub, fontSize = 12.sp)
                NumSlider("نسبة السحب للحذف", "swipeThreshold", 0.2f, 0.8f) { "${(it * 100).toInt()}%" }
                ColorRow("لون الحذف (البداية)", "deleteColorStart")
                ColorRow("لون الحذف (النهاية)", "deleteColorEnd")
                Text("الرمي للإغلاق", color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                SwitchRow("ارمي الرف بقوة فيختفي", "throwEnabled")
                val ta = ShelfSettings.txt("throwAction")
                ChipRow {
                    Chip("يختفي (يرجع من الـ Tile)", ta == "hide") { ShelfSettings.setTxt("throwAction", "hide") }
                    Chip("يتطوي لشريط", ta == "collapse") { ShelfSettings.setTxt("throwAction", "collapse") }
                }
                NumSlider("حساسية الرمي (السرعة)", "throwMinSpeedDp", 500f, 3000f) { "${it.toInt()}" }
                Note("رقم أقل = أسهل في الرمي. ارفعه لو بيختفي من أي تحريك.")
            }

            // ---- السحب والإفلات ----
            Section("السحب والإفلات") {
                SwitchRow("السحب من أي تطبيق يظهر منطقة الإفلات", "dragWakesShelf")
                SwitchRow("السحب داخل الرف يضم الكارت لمجموعة", "dragToGroup")
                NumSlider("عرض منطقة الإفلات", "dropZoneWidthDp", 60f, 220f) { "${it.toInt()} dp" }
                NumSlider("ارتفاع منطقة الإفلات", "dropZoneHeightDp", 120f, 420f) { "${it.toInt()} dp" }
                Note("سحب الصور من التطبيقات بيحتاج صلاحية الصور. والمشاركة (Share) هي الأضمن.")
            }

            // ---- الالتقاط ----
            Section("الالتقاط") {
                SwitchRow("الالتقاط التلقائي عند النسخ", "autoCapture", "بيستخدم تركيز لحظي، فممكن تلاحظ رمشة")
                SwitchRow("تجاهل المحتوى الحساس", "ignoreSensitive", "زي كلمات المرور اللي بتعلّمها تطبيقاتها")
                Text("لما يجي نسخ جديد", color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                val oc = ShelfSettings.txt("openOnCopy")
                ChipRow {
                    Chip("معاينة صغيرة", oc == "peek") { ShelfSettings.setTxt("openOnCopy", "peek") }
                    Chip("يفتح كامل", oc == "full") { ShelfSettings.setTxt("openOnCopy", "full") }
                    Chip("نبضة بس", oc == "pulse") { ShelfSettings.setTxt("openOnCopy", "pulse") }
                }
                NumSlider("مدة المعاينة", "peekMs", 1000f, 8000f) { "%.1f ث".format(it / 1000f) }
                NumSlider("أقصى طول للنص في الكارت", "previewChars", 100f, 1500f) { "${it.toInt()} حرف" }
                SwitchRow("رسائل التشخيص الصغيرة", "debug", "اقفلها لما كل حاجة تشتغل")
            }

            // ---- المجموعات واللصق ----
            Section("المجموعات واللصق") {
                SwitchRow("تجميع تلقائي للنسخ المتقارب", "autoGroup")
                NumSlider("المدة بين النسختين عشان يتجمعوا", "groupWindowSec", 15f, 600f) { "${it.toInt()} ث" }
                SwitchRow("نفس التطبيق بس", "groupSameApp")
                SwitchRow("كشف ملف كود (يوسّع المدة لـ 5 دقايق)", "groupCodeFile")
                SwitchRow("اسم المجموعة من اسم الملف", "groupAutoName")
                SwitchRow("المجموعات تبدأ مطوية", "groupsStartCollapsed")
                NumSlider("المجموعة اليدوية تتقفل بعد (دقايق)", "manualIdleMin", 0f, 120f) {
                    if (it.toInt() == 0) "أبداً" else "${it.toInt()} د"
                }
                Text("بعد اللصق", color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                SwitchRow("اخفّت الكارت اللي اتلصق", "pasteDim")
                NumSlider("درجة الخفوت", "pastedAlpha", 0.2f, 1f) { "${(it * 100).toInt()}%" }
                SwitchRow("احذف العنصر بعد لصقه", "autoRemoveAfterPaste")
                SwitchRow("اللي لسه ما اتلصقش قبل اللي اتلصق", "sortUnpastedFirst")
                SwitchRow("امسح أكواد التحقق لوحدها", "otpAutoDelete")
                NumSlider("مدة كود التحقق", "otpTtlSec", 15f, 300f) { "${it.toInt()} ث" }
            }

            // ---- الخصوصية ----
            Section("الخصوصية") {
                Text("مين يتلقط منه؟", color = TextSub, fontSize = 12.sp)
                ChipRow {
                    Chip("كل التطبيقات ما عدا المستثنى", pmode == PrivacyPolicy.MODE_ALL) {
                        pmode = PrivacyPolicy.MODE_ALL
                        PrivacyPolicy.setMode(pmode)
                    }
                    Chip("التطبيقات المختارة بس", pmode == PrivacyPolicy.MODE_ALLOW_ONLY) {
                        pmode = PrivacyPolicy.MODE_ALLOW_ONLY
                        PrivacyPolicy.setMode(pmode)
                    }
                }
                val cnt = if (pmode == PrivacyPolicy.MODE_ALLOW_ONLY) {
                    PrivacyPolicy.allowedApps().size
                } else {
                    PrivacyPolicy.blockedApps().size
                }
                Note("المختار حالياً: $cnt تطبيق")
                ActionButton("اختيار التطبيقات") { pickApps = true }

                Text("الوضع الخاص", color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                ActionButton(if (priv) "إيقاف الوضع الخاص" else "تشغيل الوضع الخاص (30 دقيقة)") {
                    if (priv) PrivacyPolicy.stopPrivate() else PrivacyPolicy.startPrivate(30)
                    priv = PrivacyPolicy.isPrivateMode()
                }

                Text("المسح التلقائي للأقدم", color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                ChipRow {
                    Chip("مقفول", wipe == 0) { wipe = 0; PrivacyPolicy.setAutoWipeDays(0) }
                    Chip("7 أيام", wipe == 7) { wipe = 7; PrivacyPolicy.setAutoWipeDays(7) }
                    Chip("30 يوم", wipe == 30) { wipe = 30; PrivacyPolicy.setAutoWipeDays(30) }
                    Chip("90 يوم", wipe == 90) { wipe = 90; PrivacyPolicy.setAutoWipeDays(90) }
                }
                Note("بيتطبق لما التطبيق يشتغل. المثبّت بيفضل دايماً.")
                ActionButton("امسح السجل دلوقتي", danger = true) { confirmClear = true }
            }

            // ---- الأيقونات ----
            Section("الأيقونات") {
                Text("أيقونة التطبيق", color = TextSub, fontSize = 12.sp)
                val curApp = ShelfSettings.appIcon()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (o in IconOptions.apps) {
                        IconPreview(o.third, "mipmap", o.second, curApp == o.first) {
                            ShelfSettings.setAppIcon(o.first)
                            IconOptions.applyApp(ctx, o.first)
                            Toast.makeText(ctx, "الأيقونة هتتغير خلال لحظات (ممكن تحتاج تعيد فتح الشاشة الرئيسية)", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                Text("أيقونة زرار لوحة التحكم", color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                val curTile = ShelfSettings.tileIcon()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (o in IconOptions.tiles) {
                        IconPreview(o.third, "drawable", o.second, curTile == o.first) {
                            ShelfSettings.setTileIcon(o.first)
                            SuperHubTileService.live?.refresh()
                            try {
                                TileService.requestListeningState(
                                    ctx, ComponentName(ctx, SuperHubTileService::class.java)
                                )
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
                Note("أيقونة الـ Tile بتتحدث لما تفتح لوحة الإعدادات السريعة.")
            }

            // ---- النسخ الاحتياطي ----
            Section("النسخ الاحتياطي والاستعادة") {
                ActionButton("نسخ الإعدادات (JSON) للحافظة") {
                    HistoryRepository.ignoreClipUntil = System.currentTimeMillis() + 1500
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("hubsync-settings", ShelfSettings.exportJson()))
                    Toast.makeText(ctx, "اتنسخت الإعدادات. الصقها في أي مكان تحتفظ فيه", Toast.LENGTH_LONG).show()
                }
                ActionButton("مشاركة الإعدادات") {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, ShelfSettings.exportJson())
                    ctx.startActivity(Intent.createChooser(i, "مشاركة إعدادات HubSync"))
                }
                ActionButton("استيراد من الحافظة") {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val t = try {
                        cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                    } catch (e: Exception) {
                        null
                    }
                    if (t.isNullOrBlank()) {
                        Toast.makeText(ctx, "الحافظة فاضية", Toast.LENGTH_SHORT).show()
                    } else if (ShelfSettings.importJson(t)) {
                        IconOptions.applyApp(ctx, ShelfSettings.appIcon())
                        Toast.makeText(ctx, "اتستوردت الإعدادات", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "النص اللي في الحافظة مش ملف إعدادات HubSync", Toast.LENGTH_LONG).show()
                    }
                }
                ActionButton("إعادة ضبط كل الإعدادات", danger = true) { confirmReset = true }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
