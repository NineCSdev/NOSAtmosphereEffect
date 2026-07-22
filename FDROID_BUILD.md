# F-Droid Build

Atmo Engine uses one source tree with separate `play` and `fdroid` product flavors. F-Droid must build the Android 13+ `v33FdroidRelease` variant only.

## Release Configuration

- Version name: `7.0.2`
- Version code: `300702`
- Minimum SDK: 33 (Android 13)
- Target SDK: 33
- Gradle task: `assembleV33FdroidRelease`

The F-Droid variant bundles the Apache-2.0 U2NetP model from `app/src/fdroid/assets/models` and uses `de.schliweb:tensorflow-lite-fdroid`. It does not compile or package ML Kit, Google Play services, or anything from `app/src/play`.

## F-Droid Metadata

Use the following fields in the `Builds` entry. Replace `commit` with the full commit hash for the 7.0.2 release.

```yaml
- versionName: 7.0.2
  versionCode: 300702
  commit: REPLACE_WITH_FULL_COMMIT_HASH
  subdir: app
  gradle:
    - v33
    - fdroid
  scandelete:
    - app/src/play
  prebuild:
    - sed -i -e '/play-services-base/d' -e '/play-services-mlkit-subject-segmentation/d' build.gradle.kts
```

`scandelete` removes the unselected proprietary implementation before F-Droid scans the source. The `prebuild` command removes its two unselected dependency declarations. Neither action changes the F-Droid implementation or the resulting APK.

## Local Verification

```bash
./gradlew assembleV33FdroidRelease
```

The resulting APK is written under `app/build/outputs/apk/v33Fdroid/release/`. Verify the merged manifest does not contain `INTERNET` or `ACCESS_NETWORK_STATE`, and verify the APK does not contain `com/google/mlkit`, `com/google/android/gms`, or the `play` source set.
