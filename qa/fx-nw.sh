# Helper NW (YID PLUS 2.0.2): YID PLUS AI chat, Statuses, Saved Items, History — signed in with the QA fixture account.
# Sourced by qa/signedin.sh fx_block (helpers from qa/native.sh: log shot key check). Pages open through the QA_OPEN intent
# (MainActivity → ui/SavedHistory.kt qaOpenNew) because the coordinator wires the real entry points at merge.
nw_open() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_OPEN --es page "$1" >/dev/null 2>&1; sleep "${2:-4}"; log "nw open $1"; }
# Tap the first on-screen node whose text or content-desc is exactly $1 (Compose semantics show in the uiautomator dump).
nw_tap() {
  local want="$1" b
  timeout 20 adb shell uiautomator dump /sdcard/nw.xml >/dev/null 2>&1
  b=$(timeout 10 adb shell cat /sdcard/nw.xml 2>/dev/null | tr '>' '\n' | grep -F -e "text=\"$want\"" -e "content-desc=\"$want\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -z "$b" ]; then log "nw_tap: '$want' not on screen"; return 1; fi
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.2; log "nw tapped '$want'"
}
nw_hidekb() { if timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true'; then key 4; sleep 1; fi; }
nw_texts() { timeout 20 adb shell uiautomator dump /sdcard/nw.xml >/dev/null 2>&1; log "nw texts $1: $(timeout 10 adb shell cat /sdcard/nw.xml 2>/dev/null | grep -oE ' (text|content-desc)="[^"]+"' | head -40 | tr '\n' ' ')"; }

# 1. The status strip + pinned AI row on a chat list
nw_open ai_row 6; shot NW-01-chatlist-status-ai; nw_texts chatlist; check nw-row
# 2. AI chat: history from the account, then ask (typed in the composer, Enter sends)
nw_tap "YID PLUS AI"; sleep 3; shot NW-02-ai-chat; check nw-ai
nw_tap "Message YID PLUS AI" || true
adb shell input text "Hello%sAI,%swhat%scan%syou%sdo?"; sleep 1; shot NW-03-ai-typed
key 66; sleep 3; nw_hidekb; sleep 1; shot NW-04-ai-asked; check nw-ai-ask
# 3. Clear the AI chat (trash icon → confirm)
nw_hidekb
nw_tap "Clear chat"; sleep 1; shot NW-05-ai-clear-confirm
nw_tap "Clear"; sleep 2; nw_hidekb; shot NW-06-ai-cleared-welcome; check nw-ai-clear
nw_hidekb; nw_tap "Give me an idea"; sleep 3; shot NW-07-ai-chip-asked
nw_hidekb; key 4; sleep 1; key 4; sleep 1
# 4. Status viewer: someone else's (sends the site's view call), D-pad Right / Left, tap right half, reply, heart
nw_open status 5; shot NW-08-status-viewer; nw_texts viewer; check nw-status
key 22; sleep 1.5; shot NW-09-status-next
key 21; sleep 1; key 22; sleep 1; key 22; sleep 3; shot NW-10-status-next-user
nw_tap "Like status"; sleep 2; shot NW-11-status-liked
nw_tap "Reply…" || true
adb shell input text "Amazing"; key 66; sleep 2; shot NW-12-status-replied; check nw-status-reply
nw_hidekb; key 4; sleep 2
# 5. My status: views, viewers sheet
nw_open status_me 5; shot NW-13-my-status
nw_tap "14 views" || nw_tap "6 views" || true; sleep 2; shot NW-14-my-viewers; key 4; sleep 1
key 4; sleep 2
# reopen fresh so the viewer has not auto-advanced, then More → Delete straight away
nw_open status_me 3; nw_tap "More" && { sleep 1; shot NW-15-my-status-menu; nw_tap "Delete"; sleep 2; shot NW-16-my-status-deleted; }; check nw-status-delete
key 4; sleep 2
# 6. Post a text status (typed, colour changed, privacy chosen, posted)
nw_open post_status 4; shot NW-17-post-status
adb shell input text "Gut%sShabbos%sfrom%sthe%sapp"; sleep 1
nw_tap "Change colour"; nw_tap "Change colour"; nw_tap "Followers"; sleep 1; shot NW-18-post-status-typed; nw_hidekb; sleep 1; shot NW-18b-post-status-colours
nw_hidekb; nw_tap "Post status"; sleep 3; shot NW-19-after-post; check nw-post
# 7. Saved items: list, unsave (bookmark), UNDO (saves again), open a short (plays, adds to history)
nw_open saved 6; shot NW-20-saved; nw_texts saved; check nw-saved
nw_tap "Remove from saved"; sleep 1; shot NW-21-unsaved-undo
nw_tap "UNDO"; sleep 2; shot NW-22-undo
adb shell input tap 240 190; sleep 5; shot NW-23-saved-short-playing; key 4; sleep 1; key 4; sleep 1
# 8. History: list, Clear all
nw_open history 6; shot NW-24-history; check nw-history
nw_tap "Clear all"; sleep 1; shot NW-25-history-confirm
nw_tap "Clear"; sleep 2; shot NW-26-history-cleared; key 4; sleep 1
# 9. Dark mode
adb shell cmd uimode night yes; sleep 3
nw_open ai_row 5; shot NW-27-dark-chatlist; nw_tap "YID PLUS AI"; sleep 3; shot NW-28-dark-ai; key 4; sleep 1; key 4; sleep 1; key 4; sleep 1
nw_open saved 5; shot NW-29-dark-saved; key 4; sleep 1
adb shell cmd uimode night no; sleep 3; check nw-dark
log "nw writes so far: $(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -cE '/api/(ai/chat|statuses|saves|history|chat/rooms|chat )')"
