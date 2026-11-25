package com.music.laya

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.laya.widget.ColorMediaWidget
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorWidgetInflationTest {
    @Test fun bothColorWidgetsInflateAsRemoteViewsInLightAndDarkMode() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val cover=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(60,160,130)) }
        instrumentation.runOnMainSync {
            for(wide in listOf(false,true)) {
                val image=ColorMediaWidget.colorSurface(cover,wide)
                val expected=IntArray(image.width*image.height).also { image.getPixels(it,0,image.width,0,0,image.width,image.height) }
                assertTrue(expected.all { Color.alpha(it)==255 })
                for(mode in listOf(Configuration.UI_MODE_NIGHT_YES,Configuration.UI_MODE_NIGHT_NO)) {
                    val configuration=Configuration(context.resources.configuration).apply { uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode }
                    val themed=context.createConfigurationContext(configuration)
                    val views=RemoteViews(context.packageName,if(wide) R.layout.widget_color_wide else R.layout.widget_color_square)
                    views.setImageViewBitmap(R.id.color_surface,image)
                    views.setTextViewText(R.id.color_title,"위젯 미리보기")
                    views.setTextViewText(R.id.color_artist,"Laya")
                    val view=views.apply(themed,null)
                    view.measure(View.MeasureSpec.makeMeasureSpec(if(wide)676 else 316,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(316,View.MeasureSpec.EXACTLY))
                    view.layout(0,0,view.measuredWidth,view.measuredHeight)
                    assertNotNull(view.findViewById<View>(R.id.color_play))
                    val after=IntArray(expected.size).also { image.getPixels(it,0,image.width,0,0,image.width,image.height) }
                    assertArrayEquals(expected,after)
                }
                image.recycle()
            }
        }
        cover.recycle()
    }
}
