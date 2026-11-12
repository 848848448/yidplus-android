# Helper DS (YID PLUS 2.1): design system, Material Symbols icons, app shell — signed in with the QA fixture account.
# Sourced by qa/signedin.sh fx_block (helpers from qa/native.sh: log shot key check tab size restart).
ds_texts() { timeout 20 adb shell uiautomator dump /sdcard/ds.xml >/dev/null 2>&1; log "ds texts $1: $(timeout 10 adb shell cat /sdcard/ds.xml 2>/dev/null | grep -oE ' (text|content-desc)="[^"]+"' | head -30 | tr '\n' ' ')"; }
# 1. Every tab at 480x640 (shell: top bar, bottom bar, icons)
tab Chats; sleep 4; shot DS-01-chats; ds_texts chats
tab Home; sleep 4; shot DS-02-channels
tab Shorts; sleep 5; shot DS-03-shorts
tab Music; sleep 4; shot DS-04-music
tab Profile; sleep 4; shot DS-05-settings; ds_texts settings
adb shell input swipe 240 520 240 140 300; sleep 1; shot DS-06-settings-scrolled
# 2. D-pad focus ring on the bottom bar
tab Chats; sleep 3; key 20 20 20 20 20 20 20 20; sleep 1; shot DS-07-dpad-focus
tab Chats; sleep 2
