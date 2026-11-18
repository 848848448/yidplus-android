# YID PLUS — Feature requests (big update backlog)

Running list the user is dictating one by one. NOTHING here is built or
released until the user explicitly says to build. For each item the user
sends, Claude describes what it sees, then adds the item below.

Status: BUILDING (user said go, 2026-11-18). Build everything in validated batches on the branch;
accumulate for ONE big update; DO NOT release until the user says. Last live version: 2.11.0.
Mark each item [DONE] as it lands on the branch.

## BUILD PROGRESS (on branch, not released)
- [DONE] #12 Video note recorder: slide-to-lock, timer pill, delete/stop/send, flip, hold-camera-in-composer entry.
- [DONE] #2a Archive chats, #2b Mark as unread (select bar + Archived section + dot).
- [DONE] #18a status viewer quick-reaction emoji bar; #18c status viewer Mute/Hide menu for others.
- [DONE] #4a Photo editor — ROTATE in the send preview (tap to turn 90°, re-saved at send). Crop/draw/text still pending.
- [DONE] #1b Status text fonts — backend (additive `font` column, web branch claude/whatsapp-fixes, NOT merged) + app Aa picker (default/serif/mono/cursive) in composer + viewer renders saved font.
- [DONE] #1c Voice status — mic button in composer records (RECORD_AUDIO), WhatsApp-style recording overlay (timer, Cancel/Send, 60s cap); posts via media path as .m4a (NO backend change); viewer plays audio (StAudio) with avatar + mic badge. Auto-advances on audio length.
- [DONE] #1a Camera capture in status composer (TakePicture -> FileProvider).
- [DONE] #1e Layout/collage — pick 2–4 photos, composed client-side into one image (clean 2/3/4 templates, centre-cropped) and posted as a normal media status. NO backend change.
- Status "Add status" options now: Text ✓ (with fonts), Camera ✓, Voice ✓, Layout ✓. Remaining: Music (#1d, backend+catalogue), AI images (#1f, backend/AI).
- [DONE] #4d HD photos — an "HD" toggle in the media send preview sends photos at 2560px/q92 (Standard stays 1280px/q82). chShrink gained a maxPx param, threaded through sendPicked/sendPhoto.
- [DONE/VERIFIED] #4 admin native — all 15 enumerated admin sections already route to native pages (when(id) in AdminScreens); only unknown ids fall back to the web panel.
- [DONE] #6 Favorites — Favorites filter chip + star toggle in the selection bar (ClLocal.favorites, per-account on phone).
- [DONE] #2c Broadcast — "New broadcast" in the new-message menu: pick people, type one message, it's sent to each person's private chat (client-only, reuses openDm + sendText; no backend change).
- DEFERRED (backend / core-chat risk, do carefully): #2d message yourself (self-room ripples through room model), #1d music status, #1f AI status, #5a chat backup, #4e auto-download (Coil plumbing).
- [ALREADY EXISTS] #8a green unread time, #8b mute bell; #17b voice seek, #17c voice speed (1x/1.5x/2x).
- [ALREADY EXISTS] #2e Pin message (bubble menu + pinned-messages list), #4f View once (👁 photo/video).
- [ALREADY EXISTS] #11 in-chat design: group sender avatars + per-sender name colour, "N members, X online" + typing + exact last-seen header, "N unread messages" divider.
- Next up: genuine gaps only — status fonts/voice/music/AI (backend), sticker maker, home widget, auto-download settings, message-yourself (backend). Most client UI already exists; focus on polish + the few true gaps.

## Already done on the branch (waiting in the big update, not yet released)
- Video note: WhatsApp-style recorder (circular selfie preview, hold-to-record, progress ring, flip) + reliable tap-to-play with progress ring
- Cleaner notifications (one tidy line; video note labelled)
- Storage and data, Export chat, Timed dark mode, Add to home screen, Status mute
- Native admin: Sessions, Banned devices, Ad-Free, Health check, Attacks, IP logs

## New requests (to build when the user says go)

### 1. Status — full WhatsApp "Add status" parity (from 5 screenshots)
App today: text status with 8 colour backgrounds; photo/video with caption; privacy chips.
Build to match WhatsApp newest:
- 1a. "Add status" entry screen with options: **Text, Music, Layout, Voice, AI images, Camera**, + recent photos grid.
- 1b. **Text status fonts**: 8 selectable font styles (in addition to the colour palette). (needs backend: store/return `font`.)
- 1c. **Voice status**: record and post a voice status. (needs backend: voice status type.)
- 1d. **Music status**: attach music to a status. (needs backend + music catalogue.)
- 1e. **Layout status**: collage of up to 6 photos in one status. (client compose → single image.)
- 1f. **AI images status**: generate an AI image for the status. (needs backend/AI service.)
- 1g. **Media composer tools** on a photo/video status: crop/rotate, stickers, text overlay, draw/pen, "Restyle with AI", caption with **@ mention**.
- 1h. **Audience selector** "Status (Contacts)": who can see it — My contacts / My contacts except… / Only share with…. (needs backend privacy lists.)

### 2. Chats
- 2a. **Archive chats** — move chats to an "Archived" list.
- 2b. **Mark as unread** — mark a chat unread (blue dot).
- 2c. **Broadcast lists** — send one message to many people separately.
- 2d. **Message yourself / Notes** — a chat with yourself (reminders, links).
- 2e. **Pin a message** — pin an important message to the top of a chat. (may already exist — verify.)

### 3. Calls
- 3a. **Call links** — create a shareable call link. (needs backend.)
- 3b. **Scheduled calls** — schedule a call for later. (needs backend.)
- 3c. **Screen share** in a video call. (WebRTC.)
- 3d. **Silence unknown callers**.

### 4. Media
- 4a. **Photo editor** — crop/rotate/draw/text before sending.
- 4b. **Sticker maker** — make a sticker from a photo.
- 4c. **Document scanner** — scan paper to PDF.
- 4d. **HD quality** — send photos/videos in high quality.
- 4e. **Auto-download** settings — auto-download on Wi-Fi / data.
- 4f. **View once** — photo/video viewable only once. (viewOnce may already exist — verify.)

### 5. Privacy / Settings
- 5a. **Chat backup** — back up the chats.
- 5b. **Who can see** — last seen / profile photo / about (more detail; some exists).
- 5c. **Two-step verification** — an extra PIN.

### 6. Other
- 6a. **Home-screen widget** — a widget with recent chats.
- 6b. **QR code** — add a contact by QR.
- 6c. **Favorites** — a list of favourite contacts.
- 6d. **Group events** — schedule an event in a group.

### 7. Status/Updates list screen (screenshot: WhatsApp "Updates" tab)
App already has StatusUpdatesPage (My status / Recent updates / Viewed updates, rings, time, camera+text FABs, mute added). New bits from this screenshot:
- 7a. **"Last photo you took?"** smart suggestion on Add status (quick-post the latest gallery photo).
- 7b. **Search** within the status/updates list.
- 7c. (optional) a dedicated **"Updates" bottom tab** with the ring icon (app nav differs today).

### 8. Chats list — design polish to match WhatsApp exactly (user stressed beauty/comfort)
App already has a WhatsApp-style list (big rows, 56dp avatar, 17sp name). Refine to match the screenshot:
- 8a. **Unread timestamp in green** (grey when read).
- 8b. **Mute bell icon** shown next to the time/unread badge on muted chats.
- 8c. **Inline media icons** in the preview (small vector icon + "Video"/"Photo", not only an emoji); keep the "~ sender:" prefix in groups.
- 8d. **Camera icon** in the top bar for a quick photo/status.
- 8e. Verify spacing/divider alignment feels roomy and even (not cramped).

### 9. Avatar quick-preview popup + full-screen profile/group photo
- 9a. Tapping a chat's **profile photo in the chat list** opens a dimmed **quick-preview popup**: an enlarged rounded photo, the chat name, and a 3-button row — **Message**, **Call**, **Info** (open chat info).
- 9b. From there, tapping the photo opens the **full-screen "Group icon" / profile photo viewer** with back + **share**. (viewer may exist in chat info — add the list popup + the quick actions.)

### 10. Chat menu / chat info — full WhatsApp parity (from "Message yourself" screenshots)
App already has: Export chat, Add shortcut (= add to home screen), Lock chat, Media/Files/Links tabs, Mute, Wallpaper.
Add the missing ones:
- 10a. **Message yourself / Notes** chat (same as 2d) — header "@you (You) · Message yourself".
- 10b. **Media visibility** toggle — whether this chat's media shows in the device gallery.
- 10c. **Restricted chat** — restrict/hide a chat.
- 10d. **Chat theme** — a per-chat theme (broader than wallpaper).
- 10e. **Add to contacts** from a chat.
- 10f. **Add to list** — custom chat lists/filters (app has folders — align with WhatsApp "lists").
- 10g. **Search in chat** — search messages inside a conversation.
- 10h. **Profile picture full-screen viewer with edit** (own) + share.
- 10i. **Add to Favorites** (same as 6c) — from the chat info.

### 11. In-chat conversation design — details to match exactly
App already has bubbles, date pills, blue double-checks, reply, reactions, system messages, forwarded, files, scroll-to-bottom. Refine to match the screenshots:
- 11a. **Phone number under the sender name** for non-contacts in groups.
- 11b. **"N unread messages" divider pill** at the first unread message.
- 11c. **Media download size label** (e.g. "5.1 MB") + blurred thumbnail until the user downloads it (tap to download).
- 11d. **"X online" count** in the group header.
- 11e. **Sender avatars** beside grouped messages in groups.
- 11f. Verify colored sender names, reply quote bars, and reaction pills match WhatsApp exactly.

### 12. Video note recorder — refine to match WhatsApp exactly (screenshots)
The branch already has a circular selfie recorder (hold-to-record, progress ring, flip). Add the states from the screenshots:
- 12a. **Slide up to lock** (hands-free) while holding — like voice notes.
- 12b. **"‹ Slide to cancel"** affordance while holding.
- 12c. **Locked state buttons**: trash (delete) · stop (centre) · send (green, right) · flip camera.
- 12d. **Red timer pill** at the top centre while recording.
- 12e. **Entry point = press-and-hold the camera button in the composer** (next to send/mic) to record a video note, like WhatsApp (a short tap = normal camera). Keep the attach-sheet option too.

### 13. Attach sheet + pickers + location (bonus from the same screenshots)
- 13a. Attach (+) sheet: add **Event** and **AI images** options.
- 13b. Media picker: an **HD toggle** for high-quality send.
- 13c. Rich **location picker**: Share live location, "current location · accurate to N m", and a **nearby places** list (not just a map link).

### 14. Emoji / GIF / sticker panel parity (screenshots)
App already has: emoji panel, stickers, reactions (6 + more), long-press actions (reply/star/copy/forward/delete/info/select/react/pin).
Add the missing bits:
- 14a. **Create sticker** button in the sticker panel (= sticker maker 4b).
- 14b. **Sticker packs** — add/download sticker packs, with a "+" to add a pack.
- 14c. **Emoji search** inside the emoji panel.
- 14d. One **unified emoji/GIF/sticker panel** with tabs: search · emoji · GIF · sticker (matches WhatsApp layout).

### 15. Group management parity (permissions / link / info)
App already has: group edit, read-only (who can write), join requests (admin approval), member titles, invite link (copy/share/revoke), leave/report.
Add/refine:
- 15a. **Group permissions screen** laid out like WhatsApp — sections Joining / Messaging / Managing:
  - Add new members (who), Require admin approval (toggle), Send new messages (everyone/admins), **Send message history** (new members see past messages), Edit group settings (who), Group admins list.
- 15b. **Group link** extras: **QR code**, **Reset link**, **Send link via SMS**, Forward, Share.
- 15c. **Create a similar group** (clone members/settings into a new group).
- 15d. **Group info tabs**: Members / Media / Settings; and **"Created by …, date"** footer.
- 15e. Add group to a community (communities exist — wire this entry).

### 16. Group info details (header actions, members, media tab, mentions)
App already has group info with members, admin, member titles, action buttons, media tabs.
Add/refine:
- 16a. Group header **action buttons**: Call (voice/video dropdown), **Status**, Share, **Search**.
- 16b. **"Add group description"** prompt when empty.
- 16c. **@ mentions highlighted** (green) in messages; member-list subtitle in the chat header.
- 16d. **Group Admin** green tags + member titles/tags shown clearly on each member.
- 16e. Per-group **Notifications** settings (All / custom / off), in the Media/Settings tab.
- 16f. **Encryption notice** ONLY if the app is actually end-to-end encrypted; otherwise omit (do not claim E2E falsely). If not E2E, consider an honest "secured" note instead.

### 17. Voice message design parity
App already has: play/pause, a filling waveform, duration, reply preview "🎤 Voice message".
Add/refine:
- 17a. **Sender avatar with a mic badge** next to the voice message; **blue mic = unplayed** indicator (changes once heard).
- 17b. **Draggable scrubber** on the waveform (seek to any point).
- 17c. **Playback speed** toggle (1x / 1.5x / 2x).
- 17d. Duration counts up while playing; shows total when idle.
- 17e. **Background playback** of a voice message with a **media notification** (pause + progress + "Voice message from X"), so it keeps playing with the app backgrounded / screen off.

### 18. Status viewer parity
App already has: status viewer with progress, text reply, heart react, viewers list, mute (added).
Add:
- 18a. **Quick reaction emoji bar** (👍😂🙏) + heart at the bottom of the viewer.
- 18b. **Reply with emoji/sticker/media/voice** (not just text) — full reply composer.
- 18c. **Viewer overflow menu**: Message · Voice call · Video call · View contact · **Get notifications** (notify me when they post) · **Hide** · Report.

### 19. Settings menu structure + new settings (screenshots)
App already has a rich Settings (Profile, Privacy, Security, Chat, Notifications, Storage and data (added), Devices, Blocked, Appearance, Stats, Help, AI).
Add / reorganize to match WhatsApp:
- 19a. **App icon** — pick an alternative launcher icon.
- 19b. **App theme** (light/dark/system) separate from chat theme (dark-schedule exists — group under Appearance).
- 19c. **Parental controls** — family settings. (needs backend/policy — evaluate.)
- 19d. **Accessibility** — increase contrast (exists), reduce animation, larger touch targets.
- 19e. **App language** — choose the app's language in-app.
- 19f. **Linked devices** — use the account on other devices (companion/web). (big; needs backend.)
- 19g. Organize Settings into WhatsApp's clean sections (Account, Privacy, Lists, Chats, Appearance, Events, Notifications, Storage, Help…).

### 20. Settings — granular detail (from the full 147s video)
Match WhatsApp's full settings depth (beyond item 19):
- 20a. **Account**: Passkeys, Password, Email, Two-step verification, Security notifications, Username, Change phone number, Request account info, **Delete account**.
- 20b. **Privacy (granular)**: Last seen & online, Profile picture, About, Links, Status, Read receipts (all with My contacts / Everyone / Nobody / except…), Groups, Live location, **Silence unknown callers**, **Advanced** (protect IP in calls, disable link previews), **Default message timer** (default disappearing).
- 20c. **Lists**: custom chat lists/filters (Unread, Favorites, Groups presets + create custom).
- 20d. **Chats**: Enter is send (exists), **Media visibility**, Font size (exists), **Default reactions** (set your quick-reaction set), **Voice message transcripts** (transcribe voice to text — AI; confirm desired), Keep chats archived, **Chat backup**, Transfer chats, Chat history.
- 20e. **Notifications (granular)**: Reminders, separate Notification tone / Vibrate / Light / high-priority / Reaction notifications for Messages, Groups and Status; App icon badge; **Focus schedules**.
- 20f. **Storage and data**: Free up storage, Network usage counters, **Use less data for calls**, **Proxy**, **Media upload quality (HD)**, **Auto-download quality**, media auto-download per network (mobile/Wi-Fi/roaming). (extends Storage screen already built.)
- 20g. **QR code**: My code / Scan code tabs + **Reset QR**; **Reserved username** (@username) in Profile; Links; About.

## VERIFICATION PASS (2026-11-18) — checked each remaining item against real code
Most of the list ALREADY EXISTS in the app. Confirmed present (not rebuilt):
- 10g Search in chat (searchOn) · 2e Pin · 4f View once · 4b/14a Create sticker (Add sticker in panel)
- 16c @mentions highlighted+tappable (recolored to green now) · 15a group permissions (ChEditGroup)
- 17b voice scrubber · 17c speed · 11d "X online" · 11b unread divider · 11e sender avatars
- revoke invite link · stickers panel (emoji+sticker tabs) · attach sheet (gallery/camera/video/audio/doc/contact/location/gif/videonote/poll/schedule)
NEW this session (in v2.13.0 test build + branch): rotate, status fonts, voice status, camera-in-status, layout/collage, HD photos, favorites, broadcast, avatar quick-preview popup, @mention green.
GENUINE gaps still open (feasible): default reactions set (20d), app language picker (19e), group add-description prompt (16b), status "get notifications"/report menu (18c), group link QR (15b/6b — needs a QR encoder lib), silence unknown callers (3d — touches call handling).
NEED INFRASTRUCTURE (cannot fake): screen share (3c WebRTC), linked devices/companion (19f), AI image status (1f — needs an AI image service), parental controls (19c), full chat backup (5a), music status catalogue (1d).
RECOMMENDATION: test v2.13.0 on a real phone; most of the list is already there. Testing together pinpoints the few real gaps far better than building blind.

<!-- user is still sending more features; keep appending below -->



## TESTING FEEDBACK (user walkthrough, 2026-11-18) — fix after collecting all
- #1b Status fonts: WORKS but layout NOT nice, not really like WhatsApp. Make the whole status composer much nicer/prettier + add the many extra status features WhatsApp has. (big redesign of PostStatus)
- #1c Voice status: needs nicer.
- #1a Status camera: needs nicer.
- #1e Status layout/collage: works poorly + needs nicer (improve collage quality/layout).
- #18c Status viewer reactions + menu: needs nicer.
- #9a Avatar popup: ❌ DOES NOT WORK (tapping the chat photo doesn't open the popup — row click likely intercepts; must make the avatar tap consume the gesture). INVESTIGATE+FIX.
- #6 Favorites: ❌ DOES NOT WORK (filter shows nothing and/or star toggle not working/discoverable). INVESTIGATE+FIX.
- #2c Broadcast: needs nicer.
