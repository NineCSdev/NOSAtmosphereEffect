# Third-Party Notices

Atmo Engine is licensed under the MIT License. Subject segmentation has separate Google Play and F-Droid implementations. The following components retain their respective licenses and terms.

## Google Play Services ML Kit Subject Segmentation

- Distribution: Google Play `play` flavor only.
- Purpose: optional on-device foreground segmentation for Canvas Sketch.
- Dependencies: `com.google.android.gms:play-services-base:18.10.0` and `com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1`.
- Delivery: the subject model is not bundled; Google Play services downloads it only after the user requests it in Advanced Settings.
- Processing: wallpaper segmentation runs on-device after installation.
- Terms and documentation: [ML Kit terms](https://developers.google.com/ml-kit/terms) and [subject segmentation for Android](https://developers.google.com/ml-kit/vision/subject-segmentation/android).

This proprietary integration and its source set are not compiled into the F-Droid flavor.

## U2NetP Foreground Model

- Purpose: offline salient-foreground segmentation for Canvas Sketch.
- Upstream project: [xuebinqin/U-2-Net](https://github.com/xuebinqin/U-2-Net)
- Architecture and original weights: U2NetP by Xuebin Qin and contributors.
- License: Apache License 2.0.
- Distribution: F-Droid `fdroid` flavor only.
- Bundled artifact: `app/src/fdroid/assets/models/u2netp_320x320.tflite`
- Artifact mirror: [abhimanyu666/u2nettflite](https://huggingface.co/abhimanyu666/u2nettflite) at revision `b0a7a49e3647e971e325a4fb10fc14a620315342`.
- SHA-256: `558136589a47e97f53944ac3f65ba56f4efb5f6bae8e92b573e7c463d20fdb58`

The complete model license is included at `app/src/fdroid/assets/models/U2NET_LICENSE.txt`.

## LiteRT Runtime

- Purpose: local execution of the bundled TFLite model.
- Distribution: F-Droid `fdroid` flavor only.
- Dependency: `de.schliweb:tensorflow-lite-fdroid:1.4.1-fdroid`
- Source: [egdels/LiteRT](https://github.com/egdels/LiteRT), tag `v1.4.1-fdroid`.
- License: Apache License 2.0.
- Note: this source-built package removes proprietary Google Play services and AI Delivery dependencies and is used by F-Droid builds of other open-source Android apps.

The Java API is supplied by `com.google.ai.edge.litert:litert-api:1.4.1`, whose source is part of the Apache-2.0 TensorFlow/LiteRT project. The source-built F-Droid runtime supplies the native implementation.
