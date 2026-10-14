# ReelChat setup

1. Firebase console -> create project -> add Android app, package `com.reelchat.app`
   -> download `google-services.json` into `app/`.
2. Build -> Authentication -> enable **Email/Password**.
3. Build -> Firestore Database -> create -> Rules tab -> paste `firestore.rules` -> Publish.
4. Open this folder in Android Studio, sync, Run. Install on both phones, sign up, follow each other.

## Privacy model
- Public profile: anyone who follows you sees your reels.
- Private profile (toggle on Profile tab): only mutual followers see them.
- Public comments: you + mutual followers. Secret comments: only the people listed (you, the uploader, + anyone you add).

## Notifications
- Works out of the box: Alerts tab with unread badge, plus a phone notification while the app process is alive.
- True background push (app fully closed): needs Firebase Blaze plan.
  `npm i -g firebase-tools && firebase login && firebase deploy --only functions`
  then set `USE_CLOUD_FUNCTION = true` in Repo.kt (stops duplicate local notifications).

NOTE: reels now live at users/{owner}/reels/{id}. If you tested the earlier version, delete the old `posts` collection.

## Build the APK without Android Studio (GitHub Actions)
1. Put `google-services.json` in `app/`.
2. Create a PRIVATE GitHub repo, upload/push this whole folder (including the hidden `.github` folder).
3. Repo -> Actions tab -> "Build APK" runs automatically (or Run workflow).
4. When green, open the run -> Artifacts -> download `ReelChat-apk` -> unzip -> app-release.apk.
