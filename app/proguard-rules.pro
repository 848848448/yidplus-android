# Native app (v2). Libraries ship their own rules.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# QA: opens the new pages (ui/SavedHistory.kt) by name from the QA_OPEN intent
-keep class com.yidplus.app.ui.SavedHistoryKt { public static void qaOpenNew(java.lang.String); }
