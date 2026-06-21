# KJV Bible

A fast, ad-free King James Version Bible app for Android.

## Features
- **Red-letter text** — the words of the Lord Jesus Christ shown in red.
- **Light & dark modes** — follows the system theme, or force Light/Dark.
- **Bookmarks** — save verses and manage/navigate them from one place.
- **Sharing** — share a single verse, multiple selected verses, or a whole chapter as clean plain text.
- **Full-text search** — search the entire Bible quickly.
- **Adjustable font size** — with a comfortable default.
- **Snappy navigation** — pick any book and chapter in two taps; swipe-free Prev/Next.

No ads. No tracking. No network access required — the entire KJV text is bundled in the app.

## Build
```bash
./gradlew :app:assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

Toolchain: Gradle 8.11.1, AGP 8.7.3, JDK 17, compileSdk 35, minSdk 26.

## Bible text
King James Version (public domain). Red-letter and small-caps (divine name) markup
derived from the [jburson/bible-data](https://github.com/jburson/bible-data) dataset,
pre-processed into compact per-book JSON assets under `app/src/main/assets/bible/`.
