package com.videoketana.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.videoketana.app.data.Prefs

/** Kulay ng VideokeTana: graphite at teal na parang LCD ng videoke machine. */
@Immutable
data class VkColors(
    val bg: Color, val surface: Color, val surface2: Color, val line: Color,
    val text: Color, val muted: Color, val accent: Color, val accentInk: Color, val danger: Color,
    val display: Color, val displayLine: Color, val displayFg: Color, val displayGhost: Color, val displayText: Color, val displayMuted: Color
)

val DarkVk = VkColors(
    bg = Color(0xFF15171C), surface = Color(0xFF1D2027), surface2 = Color(0xFF252932), line = Color(0xFF2E333D),
    text = Color(0xFFECEEF1), muted = Color(0xFF949BAA), accent = Color(0xFF62E3D2), accentInk = Color(0xFF062320), danger = Color(0xFFFF7A7A),
    display = Color(0xFF0C0E11), displayLine = Color(0xFF23272F), displayFg = Color(0xFF62E3D2), displayGhost = Color(0x1A62E3D2),
    displayText = Color(0xFFE6E8EB), displayMuted = Color(0xFF7D8593)
)
val LightVk = DarkVk.copy(
    bg = Color(0xFFEEF0F3), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF5F6F8), line = Color(0xFFDDE0E6),
    text = Color(0xFF171A20), muted = Color(0xFF5D6472), accent = Color(0xFF0B8576), accentInk = Color(0xFFFFFFFF), danger = Color(0xFFD0384A)
)

val LocalVk = staticCompositionLocalOf { DarkVk }
val vk: VkColors @Composable get() = LocalVk.current

@Composable
fun VkTheme(content: @Composable () -> Unit) {
    val base = if (Prefs.dark) DarkVk else LightVk
    // may wallpaper: see-through ang mga kahon ayon sa Settings → Transparency
    val c = if (Prefs.wallpaper.isNotEmpty()) {
        val a = 1f - Prefs.glass / 100f
        base.copy(bg = Color.Transparent, surface = base.surface.copy(alpha = a), surface2 = base.surface2.copy(alpha = a),
            display = base.display.copy(alpha = (a + 0.15f).coerceAtMost(1f)))
    } else base
    val scheme = if (Prefs.dark) darkColorScheme(primary = base.accent, onPrimary = base.accentInk, background = base.bg, surface = base.surface, onSurface = base.text, onBackground = base.text)
    else lightColorScheme(primary = base.accent, onPrimary = base.accentInk, background = base.bg, surface = base.surface, onSurface = base.text, onBackground = base.text)
    CompositionLocalProvider(LocalVk provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

// ---------- mga text style ----------
object VkType {
    val h1 = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, lineHeight = 34.sp)
    val h2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
}

// ---------- dot-matrix na letra (parang LCD ng videoke machine) ----------
private val GLYPHS: Map<Char, Array<String>> = mapOf(
    '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
    '1' to arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
    '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
    '3' to arrayOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
    '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
    '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
    '6' to arrayOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
    '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
    '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
    '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
    'A' to arrayOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
    'B' to arrayOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
    'C' to arrayOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
    'D' to arrayOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
    'E' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
    'F' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
    'G' to arrayOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
    'H' to arrayOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
    'I' to arrayOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
    'J' to arrayOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
    'K' to arrayOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
    'L' to arrayOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
    'M' to arrayOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
    'N' to arrayOf("#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"),
    'O' to arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    'P' to arrayOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
    'Q' to arrayOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
    'R' to arrayOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
    'S' to arrayOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
    'T' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
    'U' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    'V' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
    'W' to arrayOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."),
    'X' to arrayOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
    'Y' to arrayOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
    'Z' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
    ':' to arrayOf(".....", "..#..", "..#..", ".....", "..#..", "..#..", "....."),
    '-' to arrayOf(".....", ".....", ".....", "#####", ".....", ".....", "....."),
    '.' to arrayOf(".....", ".....", ".....", ".....", ".....", "..#..", "..#.."),
    ' ' to arrayOf(".....", ".....", ".....", ".....", ".....", ".....", ".....")
)

/**
 * Text na gawa sa mga tuldok, parang LCD ng videoke machine.
 * @param height taas ng isang letra
 * @param ghost ipakita ang mga patay na tuldok (parang naka-off na LED)
 */
@Composable
fun DotText(
    text: String, height: Dp, color: Color = vk.displayFg, modifier: Modifier = Modifier,
    ghost: Boolean = true, glow: Boolean = true, style: String? = null
) {
    // ibang estilo ayon sa Settings → Estilo ng numero at orasan
    when (style ?: Prefs.displayFont) {
        "seg" -> if (text.all { it.isDigit() || it == ':' || it == ' ' || it == '-' || it == '.' }) { SegText(text, height, color, modifier, ghost, glow); return }
            else { StyledText(text, height, color, modifier, FontWeight.Bold); return }
        "bold" -> { StyledText(text, height, color, modifier, FontWeight.ExtraBold); return }
        "thin" -> { StyledText(text, height, color, modifier, FontWeight.Light); return }
        "neon" -> { StyledText(text, height, color, modifier, FontWeight.Light, neon = true); return }
        "serif" -> { StyledText(text, height, color, modifier, FontWeight.Bold, family = androidx.compose.ui.text.font.FontFamily.Serif); return }
        "mono" -> { StyledText(text, height, color, modifier, FontWeight.Bold, family = androidx.compose.ui.text.font.FontFamily.Monospace); return }
        "pixel" -> { PixelText(text, height, color, modifier, ghost); return }
    }
    val chars = text.uppercase()
    val cell = height / 7f
    val ghostColor = color.copy(alpha = 0.10f)
    Canvas(modifier.size(width = cell * (chars.length * 6 - 1).toFloat(), height = height)) {
        val c = size.height / 7f
        val r = c * 0.40f
        chars.forEachIndexed { i, ch ->
            val g = GLYPHS[ch] ?: GLYPHS[' ']!!
            for (row in 0 until 7) for (col in 0 until 5) {
                val on = g[row][col] == '#'
                if (!on && !ghost) continue
                val center = Offset((i * 6 + col) * c + c / 2, row * c + c / 2)
                if (on && glow) drawCircle(color.copy(alpha = 0.18f), r * 1.9f, center)
                drawCircle(if (on) color else ghostColor, r, center)
            }
        }
    }
}

/** Numero/orasan bilang ordinaryong letra (makapal o manipis), kasing-taas ng DotText. */
@Composable
private fun StyledText(text: String, height: Dp, color: Color, modifier: Modifier, weight: FontWeight, neon: Boolean = false,
                       family: androidx.compose.ui.text.font.FontFamily? = null) {
    val fs = with(androidx.compose.ui.platform.LocalDensity.current) { (height * 1.32f).toSp() }
    if (neon) {   // neon: manipis na letra na may malakas na liwanag sa paligid
        Box(modifier) {
            Text(text, color = color.copy(alpha = 0.55f), fontSize = fs, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false,
                style = TextStyle(lineHeight = fs, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    shadow = androidx.compose.ui.graphics.Shadow(color, Offset.Zero, 40f)))
            Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = fs, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false,
                style = TextStyle(lineHeight = fs, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    shadow = androidx.compose.ui.graphics.Shadow(color, Offset.Zero, 14f)))
        }
        return
    }
    Text(text, modifier = modifier, color = color, fontSize = fs, fontWeight = weight, fontFamily = family, maxLines = 1, softWrap = false,
        style = TextStyle(letterSpacing = if (weight == FontWeight.Light) (fs.value * 0.02f).sp else (-fs.value * 0.02f).sp,
            lineHeight = fs, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
            shadow = androidx.compose.ui.graphics.Shadow(color.copy(alpha = 0.35f), Offset.Zero, 18f)))
}

/** 7-segment na numero, parang lumang digital na orasan. */
private val SEGS = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc", '5' to "afgcd", '6' to "afgedc",
    '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", '-' to "g", ' ' to ""
)

@Composable
private fun SegText(text: String, height: Dp, color: Color, modifier: Modifier, ghost: Boolean, glow: Boolean) {
    val w = height * 0.55f
    val gap = height * 0.14f
    val colonW = height * 0.22f
    val total = text.fold(0f) { acc, ch -> acc + (if (ch == ':' || ch == '.') colonW.value else w.value + gap.value) }
    Canvas(modifier.size(width = total.dp, height = height)) {
        val h = size.height; val cw = w.toPx(); val g = gap.toPx(); val t = h * 0.11f; val cwC = colonW.toPx()
        var x = 0f
        fun seg(on: Boolean, x0: Float, y0: Float, x1: Float, y1: Float) {
            val col = if (on) color else color.copy(alpha = 0.08f)
            if (!on && !ghost) return
            if (on && glow) drawLine(color.copy(alpha = 0.2f), Offset(x0, y0), Offset(x1, y1), strokeWidth = t * 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(col, Offset(x0, y0), Offset(x1, y1), strokeWidth = t, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        text.forEach { ch ->
            if (ch == ':' || ch == '.') {
                val cx = x + cwC / 2
                if (ch == ':') { drawCircle(color, t * 0.75f, Offset(cx, h * 0.32f)); drawCircle(color, t * 0.75f, Offset(cx, h * 0.68f)) }
                else drawCircle(color, t * 0.75f, Offset(cx, h - t))
                x += cwC; return@forEach
            }
            val on = SEGS[ch] ?: ""
            val l = x + t; val r = x + cw - t; val top = t; val mid = h / 2; val bot = h - t; val p = t * 0.7f
            seg('a' in on, l + p, top, r - p, top)
            seg('b' in on, r, top + p, r, mid - p)
            seg('c' in on, r, mid + p, r, bot - p)
            seg('d' in on, l + p, bot, r - p, bot)
            seg('e' in on, l, mid + p, l, bot - p)
            seg('f' in on, l, top + p, l, mid - p)
            seg('g' in on, l + p, mid, r - p, mid)
            x += cw + g
        }
    }
}

// ---------- mga button ----------
@Composable
fun VkIcon(name: String, size: Dp = 20.dp, tint: Color = vk.text, modifier: Modifier = Modifier) =
    Icon(VkIcons.get(name), contentDescription = null, tint = tint, modifier = modifier.size(size))

/** Pindutan na may focus ring (para sa remote ng Smart TV). */
@Composable
fun VkButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false,
    icon: String? = null, enabled: Boolean = true, danger: Boolean = false, height: Dp = 48.dp, shape: Shape = RoundedCornerShape(14.dp)
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val c = vk
    val bg = when { primary -> c.accent; else -> c.surface }
    val fg = when { primary -> c.accentInk; danger -> c.danger; else -> c.text }
    Row(
        modifier
            .defaultMinSize(minHeight = height)
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .border(if (focused) 3.dp else 1.dp, if (focused) c.text else if (primary) c.accent else c.line, shape)
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { VkIcon(icon, 18.dp, fg); if (text.isNotEmpty()) Spacer(Modifier.width(8.dp)) }
        if (text.isNotEmpty()) Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

/** Bilog na pindutan na icon lang. */
@Composable
fun VkIconButton(icon: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, primary: Boolean = false, tint: Color? = null) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val c = vk
    Box(
        modifier.size(size).clip(RoundedCornerShape(50))
            .background(if (primary) c.accent else c.surface)
            .border(if (focused) 3.dp else 1.dp, if (focused) c.text else c.line, RoundedCornerShape(50))
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { VkIcon(icon, size * 0.45f, tint ?: if (primary) c.accentInk else c.text) }
}

/** Kahon na parang card. */
@Composable
fun Panel(modifier: Modifier = Modifier, display: Boolean = false, content: @Composable () -> Unit) {
    val c = vk
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.clip(shape).background(if (display) c.display else c.surface).border(1.dp, if (display) c.displayLine else c.line, shape)) { content() }
}

@Composable
fun RowScope.Gap() = Spacer(Modifier.weight(1f))


/** Lahat ng estilo ng numero at orasan (kapareho ng web). */
val DISPLAY_FONTS = listOf("dot", "seg", "bold", "thin", "pixel", "neon", "serif", "mono")

/** Parisukat na pixel (parang lumang arcade), gamit ang parehong 5x7 na hugis ng LCD. */
@Composable
private fun PixelText(text: String, height: Dp, color: Color, modifier: Modifier, ghost: Boolean) {
    val chars = text.uppercase()
    val ghostColor = color.copy(alpha = 0.08f)
    Canvas(modifier.size(width = height / 7f * (chars.length * 6 - 1).toFloat(), height = height)) {
        val c = size.height / 7f
        val gap = c * 0.12f
        chars.forEachIndexed { i, ch ->
            val g = GLYPHS[ch] ?: GLYPHS[' ']!!
            for (row in 0 until 7) for (col in 0 until 5) {
                val on = g[row][col] == '#'
                if (!on && !ghost) continue
                drawRect(if (on) color else ghostColor, Offset((i * 6 + col) * c + gap / 2, row * c + gap / 2), androidx.compose.ui.geometry.Size(c - gap, c - gap))
            }
        }
    }
}
