# PikSpot V1.2 Handoff

## Current Status

- V1.2 feature work is complete.
- Debug build succeeds.
- V1.2 APK has been exported to `dist/PikSpot-v1.2.apk`.
- The app version is `versionCode 12` / `versionName "1.2"`.
- Ready for GitHub release upload.

## APK

- APK path: `C:\Users\ffitz\Documents\Codex\PikSpot\dist\PikSpot-v1.2.apk`
- Size: `300,358 bytes`
- SHA256: `B42261C4BFD6B16EAED4582D320B2B507F396E7E4158930CF19917BA5F494D9C`
- Build command used:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'C:\Users\ffitz\.gradle\wrapper\dists\gradle-9.0.0-bin\d6wjpkvcgsg3oed0qlfss3wgl\gradle-9.0.0\bin\gradle.bat' assembleDebug --no-daemon
```

## V1.2 Completed Features

- Renamed history concept to `座標剪貼簿`.
- Added JSON-backed coordinate storage via `CoordinateStore`.
- Migrates V1.1 legacy text history into JSON entries.
- Keeps up to 99 coordinate entries.
- Added note management for each coordinate.
- Added icon-only row actions:
  - Note: pencil-note icon
  - Pin: star icon
  - Share: node-share icon
  - Delete: x-bin icon
- Added batch management:
  - Select multiple coordinates
  - Selection order is shown with a small numbered circle
  - Copy selected coordinates as text
  - Optionally include notes in text export
  - Export selected coordinates as a GPX route file
  - Batch pin
  - Batch unpin
  - Batch delete
  - Select all
- Batch selection order matters:
  - Manual selection exports in tap order.
  - Select all exports old-to-new by `createdAt`.
- Clipboard UI now uses partial refresh for most operations to reduce flicker.
- Scroll position is preserved when editing notes and selecting items in batch mode.

## Important Files

- `app/src/main/java/com/kizakir/pikspot/MainActivity.java`
  - Main UI and coordinate clipboard management.
  - Batch management, text export, GPX export, note dialog, partial refresh behavior.
- `app/src/main/java/com/kizakir/pikspot/InterceptorActivity.java`
  - Transparent capture flow.
  - Now saves coordinates through `CoordinateStore`.
- `app/src/main/java/com/kizakir/pikspot/CoordinateStore.java`
  - JSON-backed coordinate store.
  - Legacy history migration.
  - Add/update/delete/pin operations.
- `app/src/main/res/values/strings.xml`
  - V1.2 UI strings.
- `app/src/main/res/drawable/`
  - New action icon candidates and selected icons.
- `README.md`
  - Updated for V1.2.
- `RELEASE_NOTES_v1.2.md`
  - GitHub release body can be copied from here.
- `app/build.gradle`
  - Version is set to `1.2`.

## GitHub Release Plan

- Tag: `v1.2`
- Title: `PikSpot V1.2`
- Upload APK: `dist/PikSpot-v1.2.apk`
- Release body: use `RELEASE_NOTES_v1.2.md`.

## Current Git State Notes

- Tracked files modified:
  - `README.md`
  - `app/build.gradle`
  - `app/src/main/java/com/kizakir/pikspot/InterceptorActivity.java`
  - `app/src/main/java/com/kizakir/pikspot/MainActivity.java`
  - `app/src/main/res/values/strings.xml`
- New important files:
  - `HANDOFF_V1.2.md`
  - `RELEASE_NOTES_v1.2.md`
  - `app/src/main/java/com/kizakir/pikspot/CoordinateStore.java`
  - new drawable icon XML files under `app/src/main/res/drawable/`
- Existing untracked generated/support folders may appear:
  - `artifacts/`
  - `qrcode_lib/`
  - `tools/`
- `.gitignore` ignores:
  - `NOTES.md`
  - `dist/*.apk`
  - build output folders

## UX Notes / Known Decisions

- Full item-move animation for pinning is not implemented.
  - Current behavior preserves/animates scroll better, but a true list item move animation would require migrating the clipboard list to `RecyclerView`.
- Batch selection no longer rebuilds the list on each tap.
  - This was done to avoid flicker and scroll jumps.
- GPX export uses a `rte` route with `rtept` entries.
  - Notes are exported as GPX point names.

## Next Chat Suggested First Steps

1. Run `git status --short`.
2. Confirm whether to commit all V1.2 source files.
3. Do not accidentally add ignored APK unless explicitly needed for release asset handling.
4. If creating a GitHub release, upload `dist/PikSpot-v1.2.apk` as the release asset.
5. If the user wants a final visual QA pass, ask them to test on device and send screenshots rather than using ADB for every UI check.
