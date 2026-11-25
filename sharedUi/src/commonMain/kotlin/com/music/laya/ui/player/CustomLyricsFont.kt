package com.music.laya.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.music.laya.data.settings.IosCustomization as P

/** Keeps all character offsets intact, including the word timing/sweep ranges. */
@Composable
internal fun customizedLyricText(text: String, baseSize: Float): AnnotatedString {
    if (!P.installed) return AnnotatedString(text)
    val separate by P.separateFonts.collectAsState()
    val size by P.fontSize.collectAsState(); val weight by P.fontWeight.collectAsState()
    val ks by P.koreanSize.collectAsState(); val kw by P.koreanWeight.collectAsState()
    val ls by P.latinSize.collectAsState(); val lw by P.latinWeight.collectAsState()
    val customSizes by P.customFontSizeKeys.collectAsState()
    return remember(text, baseSize, separate, size, weight, ks, kw, ls, lw, customSizes) {
        val builder = AnnotatedString.Builder(text)
        fun korean(c: Char) = c in '\uAC00'..'\uD7AF' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F'
        var start = 0
        while (start < text.length) {
            val ko = korean(text[start]); var end = start + 1
            while (end < text.length && korean(text[end]) == ko) end++
            val s = if (!separate) size else if (ko) ks else ls
            val w = if (!separate) weight else if (ko) kw else lw
            val key = if (!separate) "font_size" else if (ko) "korean_size" else "latin_size"
            builder.addStyle(SpanStyle(fontSize = (baseSize * lyricFontScale(baseSize, s, key in customSizes)).sp, fontWeight = FontWeight(w)), start, end)
            start = end
        }
        builder.toAnnotatedString()
    }
}

@Composable
internal fun customizedLyricStyle(style: androidx.compose.ui.text.TextStyle): androidx.compose.ui.text.TextStyle {
    if (!P.installed) return style
    val separate by P.separateFonts.collectAsState()
    val all by P.fontSize.collectAsState()
    val korean by P.koreanSize.collectAsState()
    val latin by P.latinSize.collectAsState()
    val customSizes by P.customFontSizeKeys.collectAsState()
    val base = style.fontSize.value
    val scale = if (separate) maxOf(
        lyricFontScale(base, korean, "korean_size" in customSizes),
        lyricFontScale(base, latin, "latin_size" in customSizes),
    ) else lyricFontScale(base, all, "font_size" in customSizes)
    return scaledLyricLineHeight(style, scale)
}

/** Unspecified means automatic layout, not a numeric sp value. Preserve relative em units too. */
internal fun scaledLyricLineHeight(style: androidx.compose.ui.text.TextStyle, scale: Float): androidx.compose.ui.text.TextStyle {
    if (style.lineHeight.isUnspecified || !scale.isFinite() || scale <= 0f) return style
    val height = style.lineHeight * scale
    if (!height.value.isFinite()) return style
    return style.copy(lineHeight = height)
}

/** Device defaults enlarge full lyrics; the one-line strip retains its original size unless customized. */
internal fun lyricFontScale(baseSize: Float, configuredSize: Int, customized: Boolean): Float =
    if (baseSize < 24f && !customized) 1f else configuredSize / 34f
