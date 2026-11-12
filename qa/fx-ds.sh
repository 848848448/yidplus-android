# Helper DS (YID PLUS 2.1): design system, Material Symbols icons, app shell, look settings — signed in with the QA fixture account.
# Sourced by qa/signedin.sh fx_block (helpers from qa/native.sh: log shot key check tab size restart).
ds_texts() { timeout 20 adb shell uiautomator dump /sdcard/ds.xml >/dev/null 2>&1; log "ds texts $1: $(timeout 10 adb shell cat /sdcard/ds.xml 2>/dev/null | grep -oE ' (text|content-desc)="[^"]+"' | head -30 | tr '\n' ' ')"; }
# Set a look setting exactly as Settings does (MainActivity QA_LOOK → LookPrefs / UiPrefs / Prefs).
ds_look() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_LOOK --es "$1" "$2" >/dev/null 2>&1; sleep 2; log "ds look $1=$2"; }
# 1. Every tab at 480x640 (shell: top bar, bottom bar with Material Symbols; selected tab = filled icon in a pill)
tab Chats; sleep 4; shot DS-01-chats; ds_texts chats
tab Home; sleep 4; shot DS-02-channels
tab Shorts; sleep 5; shot DS-03-shorts
tab Music; sleep 4; shot DS-04-music
tab Profile; sleep 4; shot DS-05-settings; ds_texts settings
adb shell input swipe 240 520 240 140 300; sleep 1; shot DS-06-settings-scrolled
# 2. D-pad focus ring on the bottom bar
tab Chats; sleep 3; key 20 20 20 20 20 20 20 20; sleep 1; shot DS-07-dpad-focus
# 3. The site's 7 colour themes (js/theme.js THEME_PRESETS) — each one changes the bar, buttons and soft surfaces
for i in 1 2 3 4 5 6; do ds_look color $i; tab Chats; sleep 3; shot DS-1$i-theme-$i-chats; done
ds_look color 1; tab Profile; sleep 3; shot DS-17-theme-ocean-settings
ds_look color 0
# 4. Text size S / L (site Text Size), then back to M
ds_look text 0; tab Chats; sleep 3; shot DS-20-text-small
ds_look text 2; tab Chats; sleep 3; shot DS-21-text-large
ds_look text 1
# 5. Higher contrast (darker secondary text and lines, 3 dp focus ring)
ds_look contrast 1; tab Chats; sleep 3; shot DS-22-contrast-chats; key 20 20 20; sleep 1; shot DS-23-contrast-focus
ds_look contrast 0
# 6. Reduce motion (every Compose animation jumps to its end): log it, swipe Shorts with it on
ds_look motion 1; tab Shorts; sleep 4; adb shell input swipe 240 500 240 120 200; sleep 0.3; shot DS-24-motion-off-shorts-swipe-0.3s
ds_look motion 0; tab Shorts; sleep 3; adb shell input swipe 240 500 240 120 200; sleep 0.3; shot DS-25-motion-on-shorts-swipe-0.3s
# 7. Top bar style: WhatsApp-2024 light bar (option) vs the brand-green bar (default)
ds_look bar light; tab Chats; sleep 3; shot DS-26-lightbar-chats; tab Profile; sleep 3; shot DS-27-lightbar-settings
ds_look bar green
# 8. Dark mode (WhatsApp dark tones)
ds_look dark 2; tab Chats; sleep 3; shot DS-28-dark-chats; tab Home; sleep 3; shot DS-29-dark-channels
ds_look dark 0
# 9. Deep links: group invite ?join=, ?goto=explore
view "https://yidplus.com/chat?join=fxjoin123"; sleep 4; shot DS-30-deeplink-join; key 4; sleep 1
view "https://yidplus.com/?goto=explore"; sleep 4; shot DS-31-deeplink-goto-explore
# 10. Normal phone 1080x2400
size 1080x2400 420; tab Chats; sleep 4; shot DS-40-phone-chats; tab Profile; sleep 3; shot DS-41-phone-settings
size 480x640 240; sleep 2
tab Chats; sleep 2
