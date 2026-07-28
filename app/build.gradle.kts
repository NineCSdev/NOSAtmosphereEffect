import java.io.File
import java.util.Properties

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

// ========================================================================
// CUSTOM VULKAN SHADER COMPILATION TASK
// ========================================================================
tasks.register("compileVulkanShaders") {
    group = "build"
    description = "Compiles raw GLSL shaders to SPIR-V using the NDK's glslc"

    val shaderSrcDir = file("src/main/res/shaders")
    val shaderOutputDir = file("src/main/assets/shaders/vulkan")

    val inputFiles = fileTree(shaderSrcDir) {
        include("**/*.frag", "**/*.vert")
    }

    // Force Gradle to ALWAYS run this task by disabling the UP-TO-DATE cache check
    outputs.upToDateWhen { false }

    doLast {
        println("--- VULKAN SHADER COMPILER ---")
        val filesToCompile = inputFiles.files
        println("Searching for shaders in: ${shaderSrcDir.absolutePath}")
        println("Found ${filesToCompile.size} shaders to compile.")

        if (filesToCompile.isEmpty()) {
            println("WARNING: No .frag or .vert files were found. Skipping compilation.")
            return@doLast
        }

        // Resolve SDK path manually to completely avoid AGP deprecation warnings
        val sdkDir = File(project.rootDir, "local.properties").let { propFile ->
            if (propFile.exists()) {
                val props = Properties()
                props.load(propFile.inputStream())
                props.getProperty("sdk.dir")
            } else null
        } ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: throw GradleException("Could not locate Android SDK.")

        val ndkDir = File(sdkDir, "ndk")

        // Locate glslc dynamically within the installed NDK
        val glslc = ndkDir.walkTopDown().firstOrNull { it.name == "glslc" || it.name == "glslc.exe" }
            ?: throw GradleException("glslc compiler not found in NDK path: $ndkDir")

        shaderOutputDir.mkdirs()

        filesToCompile.forEach { shaderFile ->
            // Calculate relative path to maintain subdirectory structure
            val relativePath = shaderFile.relativeTo(shaderSrcDir)
            val outFile = File(shaderOutputDir, "${relativePath.path}.spv")

            outFile.parentFile.mkdirs()
            println("Compiling: ${relativePath.path} -> .spv")

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

// Hook the compilation task into the build lifecycle before anything else runs
tasks.named("preBuild") {
    dependsOn("compileVulkanShaders")
}