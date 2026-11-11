# ---- LK: the look (bars, rows, avatars), Channels and Shorts, signed in with the fixture account ----
# Helper LK (2.0.2). Shots LK-NN-what. Every write lands in fx-requests.txt (tag YidFx) and is checked against home.js / shorts.js.
lk_mark() { timeout 10 adb shell log -t YidFx "LK-STEP $1" >/dev/null 2>&1; log "lk step $1"; }
lk_tap() { timeout 10 adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
# 1. Channels tab: Telegram list, chips, Members (followers + verified), Joined
tab Home; sleep 6; shot LK-01-channels
tapui "Members"; sleep 5; shot LK-02-channels-members
tapui "Joined"; sleep 3; shot LK-03-channels-joined
tapui "Telegram"; sleep 3
# 2. A Telegram channel: posts, react, share, gallery swipe + zoom
lk_tap 240 262 7; shot LK-04-tg-channel
adb shell input swipe 240 520 240 260 300; sleep 2; shot LK-05-tg-channel-scrolled
key 4; sleep 2
# 3. My own channel: post, like, comment
view "https://yidplus.com/?channel=demo-me"; sleep 6; shot LK-06-my-channel
lk_mark like-post; tapui "Like"; sleep 2; shot LK-07-my-channel-liked
lk_mark comment-post; tapui "Comment"; sleep 3; shot LK-08-post-comments
adb shell input text "Shkoyach"; sleep 1; lk_mark send-comment; tapui "Send"; sleep 2; shot LK-09-comment-sent
key 4; sleep 1; key 4; sleep 1
view "https://yidplus.com/?channel=demo-me"; sleep 5
lk_mark new-post; tapui "New post"; sleep 3; adb shell input text "Test%spost%sfrom%sthe%sapp"; sleep 1; shot LK-10-new-post
tapui "Post"; sleep 3; shot LK-11-new-post-sent
key 4; sleep 2; key 4; sleep 2
# 4. Shorts: like, comment, save, share, follow, creator page, upload page
tab Shorts; sleep 10; shot LK-12-shorts
lk_mark like-short; tapui "Like"; sleep 2; shot LK-13-short-liked
lk_mark comment-short; tapui "Comment"; sleep 3; adb shell input text "Nice"; sleep 1; shot LK-14-short-comments
tapui "Post"; sleep 2; key 4; sleep 1; key 4; sleep 1
lk_mark swipe-short; adb shell input swipe 240 480 240 120 220; sleep 4; shot LK-15-short-swiped
adb shell input swipe 240 480 240 120 220; sleep 4; shot LK-16-short-swiped2
tapui "Upload a Short"; sleep 3; shot LK-17-upload
key 4; sleep 2
# 5. Signed out first open: the guest bar must be slim
fx false; restart 7; tab Chats; sleep 4; shot LK-18-guest-chats; tab Home; sleep 4; shot LK-19-guest-channels
fx true; restart 7
# 6. Dark mode: channels, my channel, shorts
adb shell cmd uimode night yes; sleep 3; tab Home; sleep 4; shot LK-20-channels-dark
view "https://yidplus.com/?channel=demo-me"; sleep 5; shot LK-21-my-channel-dark; key 4; sleep 1
adb shell cmd uimode night no; sleep 3
