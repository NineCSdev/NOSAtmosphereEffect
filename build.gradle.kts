// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    // Latest stable versions projected for late 2025
    val agpVersion = "9.2.1"
    val kotlinVersion = "2.2.10"

    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:$agpVersion")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
}