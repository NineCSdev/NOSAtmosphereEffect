plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.app.nosatmosphereeffect"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.saad_khan_rind.atmosphere_effect"
        versionName = "7.1.1"
        versionCode = 500711
    }

    flavorDimensions += "apiLevel"
    flavorDimensions += "distribution"

    productFlavors {

        create("v36") {
            dimension = "apiLevel"
            minSdk = 36
            targetSdk = 36
            versionCode = 500711
        }

        create("v35") {
            dimension = "apiLevel"
            minSdk = 35
            targetSdk = 36
            versionCode = 400711
        }

        create("v33") {
            dimension = "apiLevel"
            minSdk = 33
            targetSdk = 33
            versionCode = 300711
        }

        create("play") {
            dimension = "distribution"
        }

        create("fdroid") {
            dimension = "distribution"
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += "tflite"
        noCompress += "spv"
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.palette.ktx)
    testImplementation(libs.junit)

    // Google Play builds use the higher-quality optional ML Kit module.
    "playImplementation"("com.google.android.gms:play-services-base:18.10.0")
    "playImplementation"("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")

    // F-Droid builds bundle U2NetP and use the source-built FOSS runtime.
    "fdroidImplementation"(libs.litert.api)
    "fdroidImplementation"(libs.litert.fdroid)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.ui.tooling)

    "v36Implementation"("androidx.core:core-ktx:1.19.0")
    "v36Implementation"("androidx.lifecycle:lifecycle-service:2.11.0")
    "v36Implementation"("androidx.appcompat:appcompat:1.7.1")
    "v36Implementation"("com.google.android.material:material:1.14.0")

    "v35Implementation"("androidx.core:core-ktx:1.15.0")
    "v35Implementation"("androidx.lifecycle:lifecycle-service:2.8.7")
    "v35Implementation"("androidx.appcompat:appcompat:1.7.0")
    "v35Implementation"("com.google.android.material:material:1.12.0")

    "v33Implementation"("androidx.core:core-ktx:1.12.0")
    "v33Implementation"("androidx.lifecycle:lifecycle-service:2.6.2")
    "v33Implementation"("androidx.appcompat:appcompat:1.6.1")
    "v33Implementation"("com.google.android.material:material:1.11.0")
}
