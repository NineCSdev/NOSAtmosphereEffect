## Privacy Policy

**Last updated: July 19, 2026**

Atmo Engine is a live wallpaper application designed to process wallpapers locally on your Android device. It does not require an account and does not contain advertising or developer-operated analytics.

### Wallpaper and Image Data

Images selected for wallpapers are cropped, stored, rendered, and analyzed locally on the device. Atmo Engine does not upload wallpaper images, subject masks, or generated sketches to the developer or to a remote server.

### Theme and Color Processing

Theme Playlists read Android's current Light or Dark appearance state so the app can choose the matching local playlist. When **Sync System Colors** is enabled, Atmo Engine extracts colors from the active wallpaper on-device and publishes them through Android's wallpaper color API. The device manufacturer ultimately decides whether and how those colors update the wider system palette.

The hidden Palette Diagnostics screen can display the locally extracted colors, Android's wallpaper API colors, current system accent resources, device manufacturer and model, and local wallpaper-engine status or error messages. This information is shown only on the device and is not uploaded or shared with the developer.

### Optional Canvas Sketch Subject Model

Canvas Sketch can optionally use Google ML Kit subject segmentation. The feature is off by default. Its unbundled model is requested from Google Play services only after the user taps **Download Subject Model** in Fine Tuning. Once installed, subject segmentation is performed on-device. Canvas Sketch continues to work without this model by sketching the complete wallpaper.

The Atmo Engine APK does not request the Android `INTERNET` or `ACCESS_NETWORK_STATE` permission. Google Play services performs the user-requested model download through its own system process and may use Wi-Fi or mobile data according to the device's settings.

### ML Kit Technical Diagnostics

Atmo Engine includes the Google Play services ML Kit subject-segmentation SDK. According to Google's ML Kit data-disclosure documentation, ML Kit may collect limited technical data for diagnostics and usage analytics, including:

* Device information such as manufacturer, model, Android version, build, and available ML hardware.
* Application information such as package name and app version.
* Device or per-installation identifiers.
* Performance metrics, API configuration, input/output sizes, and feature version.
* Feature events such as initialization, model download, detection, and resource release, together with related error codes.

Google states that this technical data is encrypted in transit using HTTPS and is not transferred onward to third parties. Atmo Engine does not operate a server that receives this data. For details, see [Google's ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) and [Google's Privacy Policy](https://policies.google.com/privacy).

### Permissions

Atmo Engine uses Android's wallpaper service and the `SET_WALLPAPER` permission to provide and apply live wallpapers. It does not request restricted permissions, broad file access, location, camera, microphone, contacts, or network access.

### Local Storage and Deletion

Wallpaper files, standard and theme-based playlist content, crops, preferences, and palette diagnostic traces are stored locally in the app's private storage. They can be removed by clearing Atmo Engine's app data or uninstalling the app.

### Third-Party Services

Other than Google Play services and ML Kit for the optional subject model and its on-device processing, Atmo Engine does not use third-party advertising, tracking, or analytics SDKs.

### Changes

Updates to this policy will be documented in the source-code repository and published at the privacy-policy URL used by the app's store listings.

If you have questions, contact the developer at: khansaad45678900@gmail.com
