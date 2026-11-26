package com.music.laya.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.widget.RemoteViews
import com.music.laya.MainActivity
import com.music.laya.R
import com.music.laya.playback.PlayerDeepLink
import kotlinx.coroutines.*
import kotlin.math.*

/** WidgetPod-style color cards. A single opaque bitmap keeps their colors
 * independent of system light/dark mode; the original widget styles remain. */
abstract class ColorMediaWidget : AppWidgetProvider() {
    protected abstract val wide: Boolean
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, ids, wide)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = update(context, intArrayOf(id), wide)
    private fun update(context: Context, ids: IntArray, wide: Boolean) {
        val pending = goAsync()
        scope.launch { try { withTimeoutOrNull(8000) { render(context.applicationContext, ids, wide) } } finally { pending.finish() } }
    }
    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            scope.launch {
                for ((type, wide) in listOf(ColorMediaWidgetWide::class.java to true, ColorMediaWidgetSquare::class.java to false)) {
                    val ids = manager.getAppWidgetIds(ComponentName(context, type))
                    if (ids.isNotEmpty()) withTimeoutOrNull(8000) { render(context, ids, wide) }
                }
            }
        }
        private suspend fun render(context: Context, ids: IntArray, wide: Boolean) {
            val snapshot = MediaWidgetSnapshot.load(context)
            val cover = MediaWidgetArt.loadArtwork(context, snapshot.artworkUrl, 512)
            val image = colorSurface(cover, wide)
            val manager = AppWidgetManager.getInstance(context)
            for (id in ids) {
                val views = RemoteViews(context.packageName, if (wide) R.layout.widget_color_wide else R.layout.widget_color_square)
                views.setImageViewBitmap(R.id.color_surface, image)
                views.setTextViewText(R.id.color_title, snapshot.title.ifBlank { "Laya" })
                views.setTextViewText(R.id.color_artist, snapshot.artist)
                views.setImageViewResource(R.id.color_play, if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                for ((view, action) in listOf(R.id.color_play to MediaWidgetActions.ACTION_TOGGLE, R.id.color_previous to MediaWidgetActions.ACTION_PREVIOUS, R.id.color_next to MediaWidgetActions.ACTION_NEXT)) {
                    views.setOnClickPendingIntent(view, MediaWidgetActions.pendingIntent(context, action))
                }
                val intent = Intent(context, MainActivity::class.java).putExtra(PlayerDeepLink.EXTRA_OPEN_PLAYER, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                views.setOnClickPendingIntent(R.id.color_root, PendingIntent.getActivity(context, 9400, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(id, views)
            }
        }
        internal fun colorSurface(cover: Bitmap?, wide: Boolean): Bitmap {
            val w = if (wide) 676 else 316; val h = 316
            val small = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            Canvas(small).apply {
                drawColor(Color.rgb(70, 75, 80))
                cover?.let { drawBitmap(it, null, Rect(0, 0, 96, 96), Paint(Paint.FILTER_BITMAP_FLAG)) }
            }
            val pixels = IntArray(96 * 96); small.getPixels(pixels, 0, 96, 0, 0, 96, 96)
            val accent = dominantAccent(pixels)
            MediaWidgetArt.blurInPlace(pixels, IntArray(pixels.size), 96, 96, 11)
            for (i in pixels.indices) {
                val p = pixels[i]; val channels = floatArrayOf(Color.red(p)/255f, Color.green(p)/255f, Color.blue(p)/255f)
                val gray = channels[0]*0.2126f + channels[1]*0.7152f + channels[2]*0.0722f
                fun channel(v: Float): Int {
                    val saturated = (gray + (v-gray)*1.6f).coerceIn(0f,1f)
                    return ((saturated.toDouble().pow(2.2) * 2.0.pow(-1.5)).pow(1/2.2)*255).roundToInt().coerceIn(0,255)
                }
                pixels[i] = Color.rgb(channel(channels[0]), channel(channels[1]), channel(channels[2]))
            }
            small.setPixels(pixels, 0, 96, 0, 0, 96, 96)
            val result = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result);val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(small,null,Rect(0,0,w,h),paint)
            paint.shader=RadialGradient(w*.58f,0f,w*.82f,intArrayOf((accent and 0x00ffffff) or (178 shl 24),accent and 0x00ffffff),null,Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),paint)
            paint.shader=RadialGradient(w*.54f,h*1.02f,w*.8f,intArrayOf(0x40000000,0),null,Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),paint);paint.shader=null
            cover?.let {
                val rect=if(wide) RectF(22f,22f,294f,294f) else RectF(31f,31f,165f,165f)
                val shader=BitmapShader(it,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
                val scale=max(rect.width()/it.width,rect.height()/it.height)
                shader.setLocalMatrix(Matrix().apply { setScale(scale,scale);postTranslate(rect.centerX()-it.width*scale/2,rect.centerY()-it.height*scale/2) })
                paint.shader=shader;canvas.drawRoundRect(rect,rect.width()*.115f,rect.width()*.115f,paint);paint.shader=null
            }
            small.recycle()
            return result
        }
        private fun dominantAccent(pixels: IntArray): Int {
            val counts=IntArray(512);val sums=Array(512){IntArray(3)}
            for(p in pixels) {
                val hsv=FloatArray(3);Color.colorToHSV(p,hsv)
                if(hsv[1]<.2f || hsv[2]<.18f || hsv[2]>.98f) continue
                val index=(Color.red(p)/32)*64+(Color.green(p)/32)*8+Color.blue(p)/32
                counts[index]++;sums[index][0]+=Color.red(p);sums[index][1]+=Color.green(p);sums[index][2]+=Color.blue(p)
            }
            val index=counts.indices.maxByOrNull{counts[it]} ?: 0
            return if(counts[index]==0) Color.rgb(90,110,105) else Color.rgb(sums[index][0]/counts[index],sums[index][1]/counts[index],sums[index][2]/counts[index])
        }
    }
}
class ColorMediaWidgetWide : ColorMediaWidget() { override val wide = true }
class ColorMediaWidgetSquare : ColorMediaWidget() { override val wide = false }
