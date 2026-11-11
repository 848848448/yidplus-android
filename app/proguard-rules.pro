# The JavaScript bridge methods are called by name from the page.
-keepclassmembers class com.yidplus.app.WebBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
