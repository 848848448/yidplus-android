## New in 2.15.0 (test build — fixes from your testing)
Fixes for the things you flagged:
- **Sending a photo:** the keyboard no longer pops up and blocks the Send button — you can send right away (caption is optional).
- **Tap a chat's photo:** the quick popup now opens reliably (Message / Call / Info).
- **Favorites:** you can now add/remove a favorite from the chat's ⋮ menu too (not only long-press), so it's easy to find.
- **@mentions** now work with Yiddish/Hebrew names (they show green and are tappable).
- Still improving the look of the Status composer and the video-note recorder — more to come.

## New in 2.14.0 (test build — work in progress)
Everything from 2.13.0 plus more to try:
- **Tap a chat's photo** in the list → a quick popup with the big photo and **Message / Call / Info** (tap the photo for full screen).
- **Status viewer menu** on someone's update now has **Message / Voice call / Video call** (plus Mute).
- **Default reactions:** Chat settings → **Default reactions** — tap any of the 6 to change your quick-reaction set.
- **Groups:** an **Add group description** prompt when a group has none (admins).
- **@mentions** now show in WhatsApp green.

## New in 2.13.0 (test build — work in progress)
This is a test build with a big batch of new things to try:
- **Status — more ways to post, like WhatsApp:** a **font** button (Aa) for text statuses (serif / mono / handwriting); a **voice** status (record and post, plays in the viewer); a **camera** button to shoot straight into a status; and **Layout** — pick 2–4 photos and they're combined into one collage status.
- **Photo editing before sending:** rotate a photo in the send preview, and an **HD** toggle to send photos in higher quality.
- **Favorites:** a Favorites filter at the top of Chats; star a chat from the selection bar.
- **Broadcast:** New message → **New broadcast** — pick several people, type once, and it's sent to each of them privately.
- From the earlier test build: WhatsApp-style **video-note recorder**, **Archive** chats, **Mark as unread**, status **quick-reactions** and **Mute**, and native admin **Health check / Attacks / IP logs**.
- Still being built for the full update: music & AI-image statuses, "message yourself", and more (these need server-side work).

## New in 2.11.0
- More of the owner admin panel is now native in the app (no website): Admin → More tools now has native **Sessions** (see who's signed in, sign out one person or everyone), **Banned devices** (view blocked IPs/devices and unban), and **Ad-Free** (the people who see no ads, with remove).

## New in 2.10.0
- Video notes now play reliably: a round video message shows its first frame and plays when you tap it, with sound and a progress ring around the circle. (They used to try to auto-play all at once and often never started on some phones.)
- Cleaner notifications, like WhatsApp: message previews are now one tidy line instead of long multi-line text, and a video note shows "📹 Video message".
- Add a chat to your home screen: open a chat → menu → Add to home screen to pin a shortcut that opens that chat directly.
- Mute someone's status: long-press a person in the Updates (Status) screen → Mute. Muted people move to a "Muted updates" section at the bottom, like WhatsApp.

## New in 2.9.0
- Storage and data: a new Settings → Storage and data screen shows how much space the downloaded photos, videos, GIFs and stickers take on your phone, with a one-tap "Clear media cache" to free it up (media downloads again when you reopen a chat; nothing is removed from your chats or the server).
- Export chat: open a chat → menu → Export chat to share the conversation as plain text (with dates and "time – name: message"), like WhatsApp's "Export chat".
- Scheduled dark mode: Settings → Appearance → Dark mode now has a "Timed" option — set when dark mode turns on and off (e.g. 19:00 to 07:00) and the app switches by itself.

## New in 2.8.0
- Enter key sends: a new switch in chat settings lets the Enter key send the message (on) or start a new line (off), just like WhatsApp.
- Status is reachable from everywhere: the small Status row at the top of the chat list now opens the full Updates screen too (not only the menu).

## New in 2.7.0
- Status is now a proper screen like WhatsApp: open it (the Status chip, or menu → Status) to see "My status", "Recent updates" and "Viewed updates" as a list with the story rings and time, and buttons to post a photo or text status — instead of jumping straight into someone's status.
- Video notes play right in the chat: the round video message now plays by itself, looping and muted, like WhatsApp; tap to open it full screen with sound.
- Each chat can have its own wallpaper (open the chat → menu → Wallpaper).
- Message info: long-press your own message → Info to see when it was sent and whether it was delivered/read (or how many saw it in a group).

## New in 2.6.0
- Chat lock: lock any single chat behind your fingerprint or PIN (open the chat → menu → Lock chat). Locked chats hide their last message in the list and ask to unlock when you open them.
- React with any emoji: the "+" on the reaction bar now opens a full emoji grid, not just the six quick ones.

## New in 2.5.0
- Roomier chat list, exactly like WhatsApp: bigger rows, bigger pictures and clearer text — not cramped.
- More than one account: add another account and switch between them from the menu, like WhatsApp.
- A Calls screen (menu → Calls): your recent calls with incoming / outgoing / missed, and a tap to call back.
- Share your location: the attach (+) menu now has Location — it sends a tappable map link to where you are.
- App lock: turn on Settings → Security → App lock to open YID PLUS with your fingerprint or PIN.
- GIFs: the attach (+) menu has a GIF search — pick one and it sends as an animated GIF.
- Video notes: record a short round video message, just like WhatsApp.
- "Online" is now real in groups too (it was sometimes showing people as online when they weren't).
- Known gap: calls, the GIF picker, video notes and the lock screen were checked by building and on an emulator; the camera, microphone and GIF search still need a real phone to confirm.

## New in 2.4.0
- Calls ring your phone even when the app is closed, with a full-screen Answer / Decline, like WhatsApp.
- Real "online" and exact "last seen today at 2:40 PM" in private chats, instead of a vague "last seen recently".
- Fixed: the voice-note button and some chat buttons had turned white-on-white (invisible) with the white bar — they're green and clear again.

## New in 2.3.2
- The WhatsApp 2024 look by default: a clean white top bar with a green title and green buttons, links and checkmarks (instead of a solid green bar), so the app matches today's WhatsApp. Dark mode keeps WhatsApp's dark bars. You can switch back to the solid green bar in Settings.

## New in 2.3.1
- A fresh WhatsApp-style look: the app now uses WhatsApp's own colours — the teal-green for bars, buttons and links, the green message bubbles, and the familiar dark mode — for one clean, consistent look on every screen.

## New in 2.3.0
- Calls are now native in the app: voice and video, 1-on-1 and group, built on the same calls the website uses — so an app call and a web call connect to each other. A full call screen: ringing, Accept/Decline, your camera in the corner, and buttons for mute, camera, flip camera, speaker and end, with a live timer. Group calls show everyone in a grid.
- Shorts play on by themselves: when a video ends it glides to the next one, like the website. The last one replays. A new switch in Settings → App ("Auto-play next Short") turns it off, and then a Short repeats until you swipe
- Shorts: "Who liked this" in a video's ⋯ menu opens the list of people who liked it, newest first, with their picture and name
- Admin: an "Outside videos" switch right in the app (Admin → Features) turns the source/outside videos in Shorts on or off, without opening the website admin

## New in 2.2.0
- Video channels, like the website: a "Video channels" block at the top of Channels (newest creators, "See all"), a Videos list with search, and each creator's page with their videos
- Shorts, Following: shows the videos of the people you follow. "Follow more people" in the top row opens a searchable list of creators with Follow buttons. With nobody followed, or followed people with no videos, it says so and offers "Find people to follow"
- Shorts: a video is collapsed only when another entry has exactly the same file link. Size, length or picture never remove a video, so the same video posted again under a different link can still show twice
- Status: the viewers list of your own status opens with who saw it
- Chats: Back while a chat is open no longer also closes the chat list's search or selection behind it
- Chats: in an open chat the arrow keys have fixed targets: Right reaches Attach, Left returns to the message field
- Join by invite link: the Back bar, label and field stay visible with the keyboard open, in portrait and in landscape
- Music shows only when the website's Music switch is on
- Known gaps:
  - The chat arrow keys were checked on an emulator with sample data. An earlier, intermittent miss could not be reproduced on demand, and nothing has been tried on a physical keypad phone
  - Join by invite link was checked with the standard Android keyboard on an emulator only; other keyboards were not tried
  - Video channels and playing a creator's video were checked on an emulator and against the live site as a guest, not on a physical phone
  - The admin website page (More on the website, Open the full admin panel) has not been checked with a real owner account inside the app
  - Voice message playback has not been checked with real audio

## New in 2.1.1
- Join by invite link: the page scrolls, so "Request to join" is always reachable on a small screen
- "Revoke link" sits with the invite dialog's buttons - never scrolled out of sight
- Everything from the 2.1.0 chat update:
- Chats like WhatsApp: the top bar is a search field, filter chips under it (Unread, Groups…), no pencil (the green button starts a new chat), more chats fit on a small screen
- WhatsApp-style search: recent searches, results grouped by Chats, Contacts, Groups, Channels and Messages, your word in bold
- Yiddish and Hebrew sit on the right, in the chat list and in messages
- Open chat: WhatsApp bubbles and date chips, long-press bar (Reply, Star, Copy, Forward, Delete) with reactions, photo albums like the site, opens at the first unread
- New Google icons, colour themes and dark mode on every bar, Undo after delete/archive
- Lighter on the server: the chat list refreshes every 20 s and chats load 100 messages at a time

## New in 2.0.3
- Notifications: each chat shows its own notification with the group photo; Reply and Mark as read work

## New in 2.0.2
- Chats open instantly; the list shows the real last message ("Yanky: text"), no more "System message"
- Every message shows: stickers, polls, voice, files, calls, deleted messages; links show a preview card, group links with Join
- Swipe a message to reply, like WhatsApp; tap a photo to open it big and zoom
- Notifications: the group's picture, Reply sends, Mark as read really marks it read
- Green new-chat button, statuses, YID PLUS AI, Saved items and History
- Native admin panel (users, reports, channels, broadcasts, switches); notifications page with pictures
- Text size, 7 colour themes, higher contrast; a cleaner look everywhere
- Fixed: updating carries on after you allow installs

## New in 2.0.1
- Chats like the website: new chat, group or channel, search, group info and members, mute, leave, reply, forward, edit, pin, reactions, voice notes, emoji, stickers, polls, attach photo, video or file
- Notifications per chat like WhatsApp, with Reply and Mark as read
- Channels: members' channels with follow, likes, comments and share; Telegram channels show a real picture on videos
- Shorts: upload, topics, creator pages; the same video never shows twice
- Photos and videos open full screen: swipe for the next one, pinch or double-tap to zoom, drag down to close
- Search, notifications bell and your profile menu at the top
- Full Settings like the site: edit profile and photo, privacy, security, notifications, blocked people and more; admins get the admin page
- New look: green top bar, clear lists, picture or initials for every name, dark mode
- Fixed: the app could close by itself while updating from the notification
