## Privacy Policy

**Last updated: July 21, 2026**

Atmo Engine is a live wallpaper application designed to process wallpapers locally on your Android device. It does not require an account and does not contain advertising or developer-operated analytics.

### Wallpaper and Image Data

Images selected for wallpapers are cropped, stored, rendered, and analyzed locally on the device. Atmo Engine does not upload wallpaper images, subject masks, or generated sketches to the developer or to a remote server.

### Theme and Color Processing

Theme Playlists read Android's current Light or Dark appearance state so the app can choose the matching local playlist. When **Sync System Colors** is enabled, Atmo Engine extracts colors from the active wallpaper on-device and publishes them through Android's wallpaper color API. The device manufacturer ultimately decides whether and how those colors update the wider system palette.

The hidden Palette Diagnostics screen can display the locally extracted colors, Android's wallpaper API colors, current system accent resources, device manufacturer and model, and local wallpaper-engine status or error messages. This information is shown only on the device and is not uploaded or shared with the developer.

### Canvas Sketch Subject Segmentation

Canvas Sketch can optionally isolate a prominent foreground subject before drawing the sketch. The open-source U2NetP model is bundled inside Atmo Engine and runs locally through a F-Droid-compatible, source-built LiteRT runtime. It is not downloaded at runtime and does not use Google ML Kit or Google Play services.

Wallpaper pixels, model input, confidence masks, and generated sketches remain in app memory or local app storage and are not transmitted. If the model does not find a confident subject, Canvas Sketch falls back to sketching the complete wallpaper. The feature is off by default and can be changed in Fine Tuning.

### Permissions

Atmo Engine uses Android's wallpaper service and the `SET_WALLPAPER` permission to provide and apply live wallpapers. It does not request restricted permissions, broad file access, location, camera, microphone, contacts, or network access.

### Local Storage and Deletion

Wallpaper files, standard and theme-based playlist content, crops, preferences, and palette diagnostic traces are stored locally in the app's private storage. They can be removed by clearing Atmo Engine's app data or uninstalling the app.

### Third-Party Services

Atmo Engine uses open-source Android libraries, an open-source on-device inference runtime, and an open-source bundled model. These components do not provide an external service and do not transmit wallpaper data. Atmo Engine does not use third-party advertising, tracking, analytics, or cloud-processing SDKs.

### Changes

Updates to this policy will be documented in the source-code repository and published at the privacy-policy URL used by the app's store listings.

If you have questions, contact the developer at: khansaad45678900@gmail.com
