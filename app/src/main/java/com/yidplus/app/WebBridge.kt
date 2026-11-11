package com.yidplus.app

import android.graphics.Color
import android.webkit.JavascriptInterface

/** State-only bridge: the page reports scroll position and edge colours. Nothing private crosses it. */
class WebBridge(
    private val onPullable: (Boolean) -> Unit,
    private val onBars: (Int?, Int?) -> Unit,
) {
    @JavascriptInterface fun setPullable(value: Boolean) = onPullable(value)

    @JavascriptInterface fun setBars(top: String?, bottom: String?) = onBars(parseCss(top), parseCss(bottom))

    @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME

    companion object {
        private val RGB = Regex("""rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)""")
        fun parseCss(value: String?): Int? {
            if (value.isNullOrBlank()) return null
            RGB.find(value)?.let { m ->
                val (r, g, b) = m.destructured
                return Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
            }
            return runCatching { Color.parseColor(value.trim()) }.getOrNull()
        }
    }
}
