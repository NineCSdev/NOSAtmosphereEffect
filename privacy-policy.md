## Privacy Policy

**Last updated: July 22, 2026**

Atmo Engine is a live wallpaper application designed to process wallpapers locally on your Android device. It does not require an account and does not contain advertising or developer-operated analytics.

### Wallpaper and Image Data

Images selected for wallpapers are cropped, stored, rendered, and analyzed locally on the device. Atmo Engine does not upload wallpaper images, subject masks, or generated sketches to the developer or to a remote server.

### Theme and Color Processing

Theme Playlists read Android's current Light or Dark appearance state so the app can choose the matching local playlist. When **Sync System Colors** is enabled, Atmo Engine extracts colors from the active wallpaper on-device and publishes them through Android's wallpaper color API. The device manufacturer ultimately decides whether and how those colors update the wider system palette.

The hidden Palette Diagnostics screen can display the locally extracted colors, Android's wallpaper API colors, current system accent resources, device manufacturer and model, and local wallpaper-engine status or error messages. This information is shown only on the device and is not uploaded or shared with the developer.

### Canvas Sketch Subject Segmentation

Canvas Sketch can optionally isolate a prominent foreground subject before drawing the sketch. Wallpaper pixels, model input, confidence masks, and generated sketches remain in app memory or local app storage and are not transmitted to the developer. If a model does not find a confident subject, Canvas Sketch falls back to sketching the complete wallpaper. The feature is off by default and can be changed in Fine Tuning.

The model-delivery method depends on where Atmo Engine was obtained:

* **Google Play build:** Subject segmentation uses Google ML Kit. Atmo Engine checks whether the optional subject model is already installed without starting a download. If it is missing, the user can explicitly request it with the **Download subject model** button. Google Play services downloads and installs that module and may communicate with Google for that purpose under the [Google Privacy Policy](https://policies.google.com/privacy). Atmo Engine does not access Google account information or collect model-download diagnostics. Once installed, wallpaper segmentation is performed on-device.
* **F-Droid build:** Subject segmentation uses the open-source U2NetP model bundled in the APK and a F-Droid-compatible, source-built LiteRT runtime. It requires no runtime model download and contains no ML Kit or Google Play services dependency.

### Permissions

Atmo Engine uses Android's wallpaper service and the `SET_WALLPAPER` permission to provide and apply live wallpapers. Neither distribution requests `INTERNET` or `ACCESS_NETWORK_STATE`, restricted permissions, broad file access, location, camera, microphone, or contacts. In the Google Play build, an explicitly requested ML Kit module is delivered by the separately installed Google Play services application using its own permissions.

### Local Storage and Deletion

Wallpaper files, standard and theme-based playlist content, crops, preferences, and palette diagnostic traces are stored locally in the app's private storage. They can be removed by clearing Atmo Engine's app data or uninstalling the app.

### Third-Party Services

The F-Droid build uses open-source Android libraries, an open-source on-device inference runtime, and an open-source bundled model. These components do not provide an external service or transmit wallpaper data.

The Google Play build additionally uses Google Play services and ML Kit solely to check, download on explicit request, install, and execute the optional subject-segmentation model. Segmentation itself is performed on-device. Atmo Engine does not use third-party advertising, tracking, developer-operated analytics, or cloud image processing in either build.

### Changes

Updates to this policy will be documented in the source-code repository and published at the privacy-policy URL used by the app's store listings.

If you have questions, contact the developer at: khansaad45678900@gmail.com
