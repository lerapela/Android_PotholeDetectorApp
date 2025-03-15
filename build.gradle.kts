// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.9.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
buildscript {
    dependencies {
        classpath ("com.google.gms:google-services:4.3.10")
        classpath ("com.google.firebase:firebase-crashlytics-gradle:2.9.9")
    }
}
