// Built with BFB.AI
package com.bfbai.superhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bfbai.superhub.service.AppPrefs
import com.bfbai.superhub.service.Pal
import com.bfbai.superhub.service.Palette
import com.bfbai.superhub.service.ShelfSettings
import com.bfbai.superhub.service.ThemeEngine

class CPal(
    val bg: Color,
    val card: Color,
    val card2: Color,
    val stroke: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val dark: Boolean
)

fun toCPal(p: Pal): CPal = CPal(
    Color(p.bg), Color(p.card), Color(p.card2), Color(p.stroke), Color(p.text),
    Color(p.sub), Color(p.accent), Color(p.onAccent), Color(p.danger), p.dark
)

val LocalPal = compositionLocalOf {
    CPal(
        Color(0xFF0B1B3A), Color(0xFF13264D), Color(0xFF0F2040), Color(0x334DE1E1),
        Color.White, Color(0xFF9FB3D1), Color(0xFF4DE1E1), Color(0xFF0A1730), Color(0xFFFF6B6B), true
    )
}

val LocalLang = compositionLocalOf { "ar" }

/** ترجمة جوه الواجهة: بتتحدث فوراً لما اللغة تتغير */
@Composable
fun t(ar: String, en: String): String = if (LocalLang.current == "en") en else ar

@Composable
fun AppRoot(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val tick by ShelfSettings.changes.collectAsState()
    val sysDark = isSystemInDarkTheme()
    LaunchedEffect(sysDark) { ThemeEngine.ensure(ctx) }
    val pal = remember(tick, sysDark) { toCPal(Palette.get(ctx)) }
    val lang = AppPrefs.lang
    val dir = if (lang == "en") LayoutDirection.Ltr else LayoutDirection.Rtl
    val scheme = if (pal.dark) {
        darkColorScheme(
            primary = pal.accent, onPrimary = pal.onAccent, background = pal.bg, surface = pal.card,
            onSurface = pal.text, onBackground = pal.text, error = pal.danger
        )
    } else {
        lightColorScheme(
            primary = pal.accent, onPrimary = pal.onAccent, background = pal.bg, surface = pal.card,
            onSurface = pal.text, onBackground = pal.text, error = pal.danger
        )
    }
    CompositionLocalProvider(
        LocalLayoutDirection provides dir,
        LocalPal provides pal,
        LocalLang provides lang
    ) {
        MaterialTheme(colorScheme = scheme) { content() }
    }
}

@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.card, p.card2)))
            .border(1.dp, p.stroke, shape),
        content = content
    )
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    startOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(startOpen) }
    Glass(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, p.accent)
            Text(
                title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null, tint = p.sub
            )
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), content = content)
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) p.accent else p.card2)
            .border(1.dp, if (selected) p.accent else p.stroke, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) p.onAccent else p.text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun Btn(label: String, danger: Boolean = false, filled: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val c = if (danger) p.danger else p.accent
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .padding(top = 8.dp, end = 8.dp)
            .clip(shape)
            .background(if (filled) c else c.copy(alpha = 0.14f))
            .border(1.dp, c.copy(alpha = 0.4f), shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (filled) p.onAccent else c, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SwitchLine(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    val hap = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 14.sp)
            if (hint != null) Text(hint, color = p.sub, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                hap.performHapticFeedback(HapticFeedbackType.LongPress)
                onChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onAccent,
                checkedTrackColor = p.accent,
                uncheckedThumbColor = p.sub,
                uncheckedTrackColor = p.card2,
                uncheckedBorderColor = p.stroke
            )
        )
    }
}

@Composable
fun SliderLine(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    fmt: (Float) -> String,
    onChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    val p = LocalPal.current
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(fmt(value), color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(
                thumbColor = p.accent,
                activeTrackColor = p.accent,
                inactiveTrackColor = p.stroke
            )
        )
    }
}

@Composable
fun Header(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.10f), Color.Transparent)))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = p.text)
        }
        Text(
            title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        trailing()
    }
}
