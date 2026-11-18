# YID PLUS — Feature requests (big update backlog)

Running list the user is dictating one by one. NOTHING here is built or
released until the user explicitly says to build. For each item the user
sends, Claude describes what it sees, then adds the item below.

Status: COLLECTING. Do not build. Do not release. Last live version: 2.11.0.

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

