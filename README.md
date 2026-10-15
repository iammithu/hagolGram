# hagolGram

Instagram-style reels + DMs for you and a friend. Package id stays `com.reelchat.app` (must match Firebase).

## Setup
1. Firebase: create project, add Android app `com.reelchat.app`, put `google-services.json` in `app/`.
2. Authentication -> enable Email/Password.
3. Firestore -> create database (production mode) -> Rules -> paste `firestore.rules` -> Publish
   (re-publish whenever this file changes, e.g. DMs need the `chats` rules).
4. Build the APK: push to GitHub (workflow `.github/workflows/build.yml`) -> Actions -> download `hagolGram-apk`.

## What's inside
- Reels: full-screen vertical feed, tap = pause, double-tap = like, like/comment/share/save.
- Share button sends the reel to a mutual friend's DM (or "Share via…").
- DMs between mutual followers, unread badge.
- Profiles: followers/following counts, reels grid, saved tab, public/private switch.
- Comments with replies, any-emoji reactions, and secret comments.
- Activity tab (notifications).

## Playback note
The reel video is read from Instagram's public embed page and played in our own player (unofficial, can break).
If that fails the app falls back to the plain Instagram embed.

## Background push (optional, needs Firebase Blaze)
`firebase deploy --only functions`, then set `USE_CLOUD_FUNCTION = true` in Repo.kt.
