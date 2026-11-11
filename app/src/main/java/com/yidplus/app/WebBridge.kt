package com.yidplus.app

import android.graphics.Color
import android.webkit.JavascriptInterface

/** State-only bridge: the page reports which section is open and asks for the native bars. Nothing private crosses it. */
class WebBridge(
    private val onPullable: (Boolean) -> Unit,
    private val onBars: (Int?, Int?) -> Unit,
    private val onSection: (String) -> Unit = {},
    private val onFocusNav: () -> Unit = {},
    private val onFocusTop: () -> Unit = {},
    private val onTab: (Int) -> Unit = {},
    private val onNav: (String) -> Unit = {},
) {
    @JavascriptInterface fun setPullable(value: Boolean) = onPullable(value)

    @JavascriptInterface fun setBars(top: String?, bottom: String?) = onBars(parseCss(top), parseCss(bottom))

    @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface fun setSection(json: String?) { if (json != null && json.length < 2000) onSection(json) }

    @JavascriptInterface fun focusNav() = onFocusNav()

    @JavascriptInterface fun focusTop() = onFocusTop()

    @JavascriptInterface fun tab(n: Int) = onTab(n)

    /** The site's own bottom nav as the page shows it (items the site's admin turned off are already left out). */
    @JavascriptInterface fun setNav(json: String?) { if (json != null && json.length < 4000) onNav(json) }

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
