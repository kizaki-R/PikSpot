# PikSpot V1.2 GitHub 上架流程

## 1. 上架前確認

確認目前版本資訊：

- `app/build.gradle`
  - `versionCode 12`
  - `versionName "1.2"`
- Release APK:
  - `dist/PikSpot-v1.2.apk`
- Release notes:
  - `RELEASE_NOTES_v1.2.md`

確認 APK 雜湊：

```powershell
Get-FileHash 'C:\Users\ffitz\Documents\Codex\PikSpot\dist\PikSpot-v1.2.apk' -Algorithm SHA256
```

目前 V1.2 APK SHA256：

```text
B42261C4BFD6B16EAED4582D320B2B507F396E7E4158930CF19917BA5F494D9C
```

## 2. 檢查 Git 狀態

```powershell
git status --short
```

注意：

- `dist/*.apk` 被 `.gitignore` 忽略，不需要 commit。
- `NOTES.md` 也被 `.gitignore` 忽略，不需要 commit。
- `artifacts/`、`qrcode_lib/`、`tools/` 若仍是未追蹤，通常不要加入 V1.2 release commit，除非確定要收進 repo。

## 3. 建議要 commit 的檔案

```powershell
git add README.md
git add RELEASE_NOTES_v1.2.md
git add HANDOFF_V1.2.md
git add GITHUB_RELEASE_FLOW_V1.2.md
git add app/build.gradle
git add app/src/main/java/com/kizakir/pikspot/MainActivity.java
git add app/src/main/java/com/kizakir/pikspot/InterceptorActivity.java
git add app/src/main/java/com/kizakir/pikspot/CoordinateStore.java
git add app/src/main/res/values/strings.xml
git add app/src/main/res/drawable/ic_delete_archive.xml
git add app/src/main/res/drawable/ic_delete_bin_round.xml
git add app/src/main/res/drawable/ic_delete_minus.xml
git add app/src/main/res/drawable/ic_delete_sweep.xml
git add app/src/main/res/drawable/ic_delete_x_bin.xml
git add app/src/main/res/drawable/ic_note_bookmark.xml
git add app/src/main/res/drawable/ic_note_bubble.xml
git add app/src/main/res/drawable/ic_note_pencil.xml
git add app/src/main/res/drawable/ic_note_sheet.xml
git add app/src/main/res/drawable/ic_note_tag.xml
git add app/src/main/res/drawable/ic_pin_bookmark.xml
git add app/src/main/res/drawable/ic_pin_flag.xml
git add app/src/main/res/drawable/ic_pin_map.xml
git add app/src/main/res/drawable/ic_pin_push.xml
git add app/src/main/res/drawable/ic_pin_star.xml
git add app/src/main/res/drawable/ic_share_arrow_up.xml
git add app/src/main/res/drawable/ic_share_box_arrow.xml
git add app/src/main/res/drawable/ic_share_link.xml
git add app/src/main/res/drawable/ic_share_nodes_round.xml
git add app/src/main/res/drawable/ic_share_send.xml
```

檢查 staged 內容：

```powershell
git diff --cached --stat
```

## 4. Commit

```powershell
git commit -m "Release PikSpot v1.2"
```

## 5. 建立 tag

```powershell
git tag v1.2
```

若 tag 已存在，先確認原因，不要直接覆蓋。必要時才處理：

```powershell
git tag
```

## 6. Push 到 GitHub

```powershell
git push
git push origin v1.2
```

## 7. 建立 GitHub Release

到 GitHub repo：

```text
https://github.com/kizaki-R/PikSpot
```

操作：

1. 進入 `Releases`
2. 點 `Draft a new release`
3. Tag 選 `v1.2`
4. Release title 填：

```text
PikSpot V1.2
```

5. Release description 貼上 `RELEASE_NOTES_v1.2.md` 的內容
6. 上傳 APK：

```text
C:\Users\ffitz\Documents\Codex\PikSpot\dist\PikSpot-v1.2.apk
```

7. 確認不是 draft 後發布

## 8. 發布後確認

發布後檢查：

- Release 頁面顯示 `PikSpot V1.2`
- Tag 是 `v1.2`
- APK asset 名稱是 `PikSpot-v1.2.apk`
- README 的下載連結指向：

```text
https://github.com/kizaki-R/PikSpot/releases/tag/v1.2
```

## 9. 可選：安裝 Release APK 驗證

下載 GitHub Release 上的 APK，或直接使用本機 dist APK 安裝：

```powershell
& 'C:\Users\ffitz\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r 'C:\Users\ffitz\Documents\Codex\PikSpot\dist\PikSpot-v1.2.apk'
```

檢查：

- App 可開啟
- 版本為 V1.2 APK
- 座標剪貼簿正常
- 批量管理正常
- GPX 匯出正常

## 10. GitHub Release 簡短文案

可貼在 GitHub Release 開頭：

```text
PikSpot V1.2 將歷史紀錄升級為座標剪貼簿，新增備註管理、批量管理、選取順序、GPX 路線匯出，以及 JSON 儲存與 V1.1 舊資料自動轉換。
```
