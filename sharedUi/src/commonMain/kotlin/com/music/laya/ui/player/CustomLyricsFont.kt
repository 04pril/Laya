package com.music.laya.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
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
    return remember(text, baseSize, separate, size, weight, ks, kw, ls, lw) {
        val builder = AnnotatedString.Builder(text)
        fun korean(c: Char) = c in '\uAC00'..'\uD7AF' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F'
        var start = 0
        while (start < text.length) {
            val ko = korean(text[start]); var end = start + 1
            while (end < text.length && korean(text[end]) == ko) end++
            val s = if (!separate) size else if (ko) ks else ls
            val w = if (!separate) weight else if (ko) kw else lw
            builder.addStyle(SpanStyle(fontSize = (baseSize * s / 34f).sp, fontWeight = FontWeight(w)), start, end)
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
    val scale = (if (separate) maxOf(korean, latin) else all) / 34f
    return style.copy(lineHeight = (style.lineHeight.value * scale).sp)
}
