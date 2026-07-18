# Atmo Engine

**Atmo Engine** is an Android application designed to replicate the distinctive "Atmosphere" transition effect found in Nothing OS.

## 📥 Download
Atmo Engine is available to download from the Play Store, F-Droid and Orion Store.

<a href="https://play.google.com/store/apps/details?id=com.saad_khan_rind.atmosphere_effect">
<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80">
</a>

<a href="https://f-droid.org/packages/com.saad_khan_rind.atmosphere_effect/">
<img alt="Get it on F-Droid" src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="80">
</a>

## ⚠️ Device Support & Disclaimer

**Current Testing Status:**
This application has currently been tested **exclusively on the Samsung Galaxy S25 Ultra and Nothing Phone 1**.

While it may work on other Android devices running Android 13+ (API 33+), behavior on different manufacturers' skins (FuntouchOS, OxygenOS, etc.) is not guaranteed.

## Usage Guide

Follow these steps to set up the effect properly on your device.

### ⚡ Quick Setup: Share an Image

The fastest way to apply a wallpaper. Instead of opening the app and browsing for a file, you can send an image straight to Atmo Engine from anywhere:

1. Find an image in your **Gallery**, or in any **wallpaper app**.
2. Tap **Share** and choose **Atmo Engine** ("Set with Atmo Engine") from the share sheet.
3. Pick the effect you want — the app takes you straight to the crop screen and applies it.
   You can share a **single image** for a normal wallpaper, or **select multiple images** before sharing to build a rotating playlist. This skips the extra steps of saving the image, opening the app, and digging through your files to find it again. The regular in-app flow below still works exactly the same.

### 1\. Select Your Effect

Open the app and choose your desired atmosphere style from the selection screen:

* **Original Atmosphere:** Signature style. Drifting ambient atmospheric clouds that transition to blur when unlocked.
* **Reverse Atmosphere:** Mysterious reveal. Deep ambient clouds fade to a sharp clear view when unlocked.
* **Simple Frosted:** Modern minimalism. Applies a clean, uniform frosted glass blur (no clouds).
* **Simple Frosted (Reverse):** Elegant clarity. Wakes up from a heavy frosted blur into a crystal clear wallpaper.
* **Halftone Print:** Retro aesthetic. Sharp view dissolves into comic-book CMYK dots when locked.
* **Halftone Print (Reverse):** Retro aesthetic. CMYK dots seamlessly expand into continuous color when unlocked.
* **Color Fill:** Liquid awakening. Colors flow outward from your fingerprint.
* **Color Fill (Reverse):** Fluid drain. Colors wash away into grayscale.
* **Canvas Sketch:** A soft monochrome sketch transitions into the full wallpaper when unlocked.
* **Canvas Sketch (Reverse):** The full wallpaper transitions back into its monochrome sketch.

#### Canvas Sketch and Subject Segmentation

Canvas Sketch works fully offline without an additional model. In that mode, it traces the complete wallpaper. For wallpapers with a prominent person, character, animal, or object, optional subject segmentation can isolate that subject before drawing the sketch:

1. Open **Fine Tuning** for Canvas Sketch.
2. Tap **Download Subject Model**. The model is requested only after this explicit action and is installed by Google Play services.
3. Turn on **Subject Segmentation** after the model status changes to **Downloaded**.

The subject model is downloaded on demand and reused while it remains installed; Google Play services may manage or update system modules later. Segmentation itself runs on-device, and wallpaper image contents are not uploaded. The Atmo Engine APK does not request the `INTERNET` or `ACCESS_NETWORK_STATE` permission; Google Play services handles the optional model download through its own system service and may use Wi-Fi or mobile data according to the device's settings. The rest of the app, including Canvas Sketch without segmentation, remains offline. See the [privacy policy](privacy-policy.md) for ML Kit's limited technical diagnostics disclosure.

### 2\. Select Image & Playlist Mode
After selecting an effect, you will be prompted to choose your wallpaper mode:

* **Single Image:** Standard mode. Pick one image, crop it, and apply.

* **Multiple Images (Playlist):** Select multiple images from your gallery to create a Wallpaper Playlist. You can simply apply the play list as it or adjust & crop any image from the playlist you want. Once finished, the app will automatically rotate through these wallpapers based on your settings.

* **Edit Existing Playlist:** If you already have a playlist running, this option loads your currently saved wallpapers (including your exact zoom and crop settings). You can easily remove old images, add new ones from your gallery, or tweak existing crops without having to start from scratch.

### 3\. Application & Activation

Please follow these simple steps to apply the wallpaper:

1. **Apply the Wallpaper:** Once you are happy with your crop or playlist selection, tap the **"Apply"** button.
2. **Review Instructions:** A dialog box will appear with instructions to set the wallpaper to both screens. Tap **"Set Wallpaper"** to proceed.
3. **Set Wallpaper:** The app will redirect you to the Android System's Live Wallpaper preview screen. Tap **"Set Wallpaper"** (or the checkmark/apply icon, depending on your device).
4. **MANDATORY Selection:** When prompted, you must select **"Home screen and Lock screen"**.
   > *Why? Both screens must be controlled by the live wallpaper to ensure a smooth transition when you unlock your device.*
5. **Finish:** Setup is complete! Lock and unlock your screen to see the applied effect in action.


## Advanced Customization
Take full control of the animation and look. You can now tweak the following settings dynamically:
### Visual Adjustments
* **Dimness Level:** Adjust the darkening overlay to ensure your home screen icons remain readable against bright wallpapers.
* **Blob Saturation:** (Original Atmosphere & Reverse Atmosphere Effects Only) Adjusts the color intensity of the drifting atmospheric clouds. Increase to make the colors vibrant and punchy, or decrease to zero for a muted, grayscale cloud effect.
* **Blob Contrast:** (Original Atmosphere & Reverse Atmosphere Effects Only) Adjusts the harshness of the atmospheric clouds. Higher values create distinct, separated color pools, while lower values blend the colors softly and smoothly together.
* **Blur Strength:** (Frosted Effects Only) Use the slider to fine-tune the intensity of the blur radius, from a light mist to heavy glass.
* **Noise Grain:** Enable a film-grain texture on top of the blur. You can customize:
    * **Noise Strength:** How visible the grain is.
    * **Noise Scale:** The size/coarseness of the grain particles.
* **Halftone Pixel Size:** (Halftone Effects Only) Dynamically adjust the size of the printed dots. Setting this to `0` renders the original continuous tones instead of dots.
* **Black & White Effect:** (Halftone Effects Only) Converts the CMYK color halftone pattern into a single-channel grayscale newspaper print.
* **Fingerprint Location:** (Color Fill Effects Only) Two sliders to adjust the horizontal and vertical position of effect start place sync with the fingerprint location.
* **Sketch Detail:** (Canvas Sketch Only) Controls how many wallpaper contours are retained.
* **Line Thickness:** (Canvas Sketch Only) Adjusts the width of the monochrome sketch lines.
* **Subject Segmentation:** (Canvas Sketch Only) Optionally limits the sketch to a detected foreground subject after the on-device model has been downloaded.
### Animation & Behavior
* **Animation Duration:** Control the total transition duration.
* **Lock Delay (Anti-Flicker):** Adds a configurable pause before the wallpaper resets when you lock the phone. This prevents the visual glitch where the wallpaper "snaps" back to its initial state before the screen turns fully black.
* **Unlock Check Interval:** Adjusts how frequently the app detects unlock events. Tuning this eliminates "delayed start" issues, ensuring the animation begins immediately when you wake your device.
### Playlist & Rotation
(Only available when using Multiple Images mode)
* **Rotation Interval:** Controls how often the wallpaper changes from your playlist.
    * **Options:** System Theme (Light/Dark), Every Lock (Instant), 15 Minutes, 1 Hour, up to 24 Hours.
    * **System Theme Sync:** When selected, the wallpaper will only change when your device switches between Light and Dark mode.
    * **Smart Rotation:** To prevent lag or visual glitches, the wallpaper only rotates when the screen is OFF.
    * *Example:* If you set "15 Minutes", the app checks the time whenever you lock your phone. If 15 minutes have passed since the last change, it swaps the wallpaper in the background so it's ready the next time you unlock.

## Screenshots
<div align="center">
  <img src="https://github.com/user-attachments/assets/5ca9fd98-880a-4377-973b-9192771aa185" width="45%" alt="1st" />
  <img src="https://github.com/user-attachments/assets/e8bda8f0-821d-43c4-8194-421916560c64" width="45%" alt="2nd" />
  <br/>
  <img src="https://github.com/user-attachments/assets/160d8da4-fe29-40f2-90c1-74f0fc003fdc" width="45%" alt="3rd" />
  <img src="https://github.com/user-attachments/assets/1e0734af-2e51-4941-9d03-42cd032099c0" width="45%" alt="4th" />
</div>

## Telegram Group
I've made a telegram group for the discussion of issues and feature suggestion. You can join it using [this link](https://t.me/atmosphereEffect).

## Known Issues

* **Samsung Adaptive Clock:** As mentioned, programmatically setting the lock screen interferes with Samsung's Adaptive Clock on OneUI.

## Build & Installation

This project is built using Kotlin and Gradle.

1.  Clone the repository.
2.  Open in Android Studio (Ladybug or newer recommended).
3.  Sync Gradle.
4.  Build and Run on your device.

<!-- end list -->

```bash
git clone https://github.com/yourusername/NOSAtmosphereEffect.git
```

## Author

**Saad Ullah Khan**
📍 Passau, Germany
📧 [khansaad45678900@gmail.com](mailto:khansaad45678900@gmail.com)
🔗 [LinkedIn](https://www.linkedin.com/in/saadullahkhan456)
💻 [GitHub](https://github.com/saad-khan-rind)
📄 [Download Resume](https://drive.google.com/uc?export=download&id=1CyeubsV7WKZeDb6N-XZbwBq42C6JF3Sn)
🌐 [Portfolio](https://portfolio-frontend-lovat-nine.vercel.app)

## License

This project is open-source and available under the [MIT License](LICENSE).

## Privacy Policy

The privacy policy is [this](https://saad-khan-rind.github.io/NOSAtmosphereEffect/privacy-policy).

---

⭐️ **Feel free to fork, star, and use this code!**

---
