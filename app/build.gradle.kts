import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        load(localPropsFile.inputStream())
    }
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

    signingConfigs {
        create("release") {
            storeFile = file("keystores/release-key.jks")

            // Checks GitHub Actions environment first, falls back to local.properties for local machine builds
            storePassword = System.getenv("KEY_STORE_PASSWORD") ?: localProperties.getProperty("KEY_STORE_PASSWORD")
            keyAlias = System.getenv("ALIAS") ?: localProperties.getProperty("ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("KEY_PASSWORD")

            // Explicitly enable all available signature schemes
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
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
            signingConfig = signingConfigs.getByName("release")
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
// ========================================================================
// CUSTOM VULKAN SHADER COMPILATION TASK
// ========================================================================
tasks.register("compileVulkanShaders") {
    group = "build"
    description = "Compiles Vulkan GLSL shaders to SPIR-V using the NDK's glslc"

    // STRICTLY scan only res/shaders for Vulkan files
    val shaderSrcDir = file("src/main/shaders")
    val shaderOutputDir = file("src/main/assets/shaders/vulkan")

    val inputFiles = fileTree(shaderSrcDir) {
        include("**/*.frag", "**/*.vert")
    }

    // FIX: Capture the root directory during the configuration phase
    val projectRoot = project.rootDir

    // Force Gradle to ALWAYS run this task (disables caching) as requested
    outputs.upToDateWhen { false }

    doLast {
        println("--- VULKAN SHADER COMPILER ---")
        val filesToCompile = inputFiles.files
        println("Found ${filesToCompile.size} Vulkan shaders in shaders.")

        if (filesToCompile.isEmpty()) {
            println("WARNING: No .frag or .vert files were found in src/main/shaders.")
            return@doLast
        }

        // Reuses the global localProperties object safely initialized at the top
        val sdkDir = localProperties.getProperty("sdk.dir")
            ?: System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: throw GradleException("Could not locate Android SDK.")

        val ndkDir = File(sdkDir, "ndk")

        // Locate glslc dynamically within the installed NDK
        val glslc = ndkDir.walkTopDown().firstOrNull { it.name == "glslc" || it.name == "glslc.exe" }
            ?: throw GradleException("glslc compiler not found in NDK path: $ndkDir")

        shaderOutputDir.mkdirs()

        filesToCompile.forEach { shaderFile ->
            // Extract the immediate parent folder name (the effect name)
            // e.g. "src/main/res/shaders/neon/neon.frag" -> "neon"
            val effectName = shaderFile.parentFile.name

            // Build the exact output path: assets/shaders/vulkan/{effect_name}/{shader.ext}.spv
            val outFile = File(shaderOutputDir, "$effectName/${shaderFile.name}.spv")

            outFile.parentFile.mkdirs()
            println("Compiling: ${shaderFile.name} into vulkan/$effectName/ -> .spv")

            // Use ProcessBuilder to bypass Gradle's nested closure scope issues entirely
            val process = ProcessBuilder(
                glslc.absolutePath,
                shaderFile.absolutePath,
                "-o",
                outFile.absolutePath
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() != 0) {
                throw GradleException("Shader compilation failed for ${shaderFile.name}:\n$output")
            }
        }
        println("--- SHADER COMPILATION COMPLETE ---")
    }
}
