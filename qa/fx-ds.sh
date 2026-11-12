# Helper DS (YID PLUS 2.1): design system, Material Symbols icons, app shell, look settings — signed in with the QA fixture account.
# Sourced by qa/signedin.sh fx_block (helpers from qa/native.sh: log shot key check tab size restart).
ds_texts() { timeout 20 adb shell uiautomator dump /sdcard/ds.xml >/dev/null 2>&1; log "ds texts $1: $(timeout 10 adb shell cat /sdcard/ds.xml 2>/dev/null | grep -oE ' (text|content-desc)="[^"]+"' | head -30 | tr '\n' ' ')"; }
# Set a look setting exactly as Settings does (MainActivity QA_LOOK → LookPrefs / UiPrefs / Prefs).
ds_tap() {
  local want="$1" b
  timeout 20 adb shell uiautomator dump /sdcard/ds.xml >/dev/null 2>&1
  b=$(timeout 10 adb shell cat /sdcard/ds.xml 2>/dev/null | tr '>' '\n' | grep -F -e "text=\"$want\"" -e "content-desc=\"$want\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -z "$b" ]; then log "ds_tap: '$want' not on screen"; return 1; fi
  set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.2; log "ds tapped '$want'"
}
ds_look() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_LOOK --es "$1" "$2" >/dev/null 2>&1; sleep 2; log "ds look $1=$2"; }
# Only screens answered by the fixture files (no GETs to the live site): Chats, Settings, the join page.
# 1. Chats + Settings at 480x640 (Material Symbols bar; selected tab = filled icon in a pill)
tab Chats; sleep 4; shot DS-01-chats; ds_texts chats
tab Profile; sleep 4; shot DS-05-settings
# 2. Tap Chats twice: the 2nd tap is the WhatsApp "back to top" (Nav.reselect)
tab Chats; sleep 2; key 20 20 20 20 20 20 20 20; sleep 1; shot DS-07-dpad-focus
# 3. Colour themes on Chats + Settings (chat-area bars follow the theme only after patch P6 in ChatUi.kt)
for i in 1 4 6; do ds_look color $i; tab Chats; sleep 3; shot DS-1$i-theme-$i-chats; tab Profile; sleep 3; shot DS-1$i-theme-$i-settings; done
ds_look color 0
# 4. Text size S / L, higher contrast with the 3 dp focus ring
ds_look text 0; tab Chats; sleep 3; shot DS-20-text-small
ds_look text 2; tab Chats; sleep 3; shot DS-21-text-large
ds_look text 1
ds_look contrast 1; tab Chats; sleep 3; key 20 20 20; sleep 1; shot DS-23-contrast-focus
ds_look contrast 0
# 5. Light top bar (option) and dark mode (WhatsApp dark tones)
ds_look bar light; tab Profile; sleep 3; shot DS-27-lightbar-settings; tab Chats; sleep 3; shot DS-26-lightbar-chats
ds_look bar green
ds_look dark 2; tab Chats; sleep 3; shot DS-28-dark-chats; tab Profile; sleep 3; shot DS-29-dark-settings
ds_look dark 0
# 6. Deep link: group invite ?join= → Join page with the fixture group's preview
view "https://yidplus.com/chat?join=fxjoin123"; sleep 4; shot DS-30-deeplink-join; key 4; sleep 1
# 6b. App-wide snackbar with Undo (above the bottom bar)
tab Chats; sleep 2; ds_look snack Archived; sleep 1; shot DS-32-snackbar-undo; sleep 5
# 6c. Shared pieces sample (DsDemoPage): SearchPill at the top, hides on scroll-down, back on scroll-up; last row above the FAB
ds_look demo 1; sleep 2; shot DS-33-searchpill-and-chips-top
adb shell input swipe 240 520 240 300 300; sleep 1; shot DS-34-searchpill-hides-on-scroll-down
for k in 1 2 3 4 5; do adb shell input swipe 240 560 240 120 150; sleep 0.4; done; sleep 1; shot DS-35-last-row-above-fab
adb shell input swipe 240 260 240 480 300; sleep 1; shot DS-36-searchpill-back-on-scroll-up
for k in 1 2 3 4 5 6; do adb shell input swipe 240 200 240 560 120; sleep 0.3; done; sleep 1
ds_tap "Unread"; sleep 1; shot DS-37-filter-chip-unread-selected
ds_tap "Search…"; sleep 1.5; shot DS-38-search-open
adb shell input text "Yanky"; sleep 1; shot DS-39-search-typed
key 4; sleep 1; key 4; sleep 1; shot DS-39b-search-closed-by-back
key 4; sleep 1
# 6d. Screen video (480x640): bottom bar, themes, dark, text size, D-pad
adb shell rm -f /sdcard/DS-flow.mp4
(timeout 40 adb shell screenrecord --size 480x640 --bit-rate 2000000 --time-limit 32 /sdcard/DS-flow.mp4 >/dev/null 2>&1 &)
sleep 1; tab Chats; sleep 2; tab Profile; sleep 2
ds_look color 1; sleep 1; ds_look color 4; sleep 1; ds_look color 6; sleep 1; ds_look color 0
ds_look dark 2; tab Chats; sleep 2; ds_look dark 0
ds_look text 2; sleep 2; ds_look text 0; sleep 2; ds_look text 1
key 20 20 20 20; sleep 1; key 22 22; sleep 1
sleep 4; timeout 30 adb pull /sdcard/DS-flow.mp4 "$OUT/DS-flow.mp4" >/dev/null 2>&1; log "ds video $(ls -la $OUT/DS-flow.mp4 2>&1 | tail -c 80)"
# 7. Normal phone 1080x2400
size 1080x2400 420; tab Chats; sleep 4; shot DS-40-phone-chats; tab Profile; sleep 3; shot DS-41-phone-settings
ds_look dark 2; tab Chats; sleep 3; shot DS-42-phone-dark-chats; ds_look dark 0
size 480x640 240; sleep 2
tab Chats; sleep 2
