plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.aipoc"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.aipoc"
        // ORT 1.22.0 requires API 24; do not override the library manifest.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        // Google Play APK upload: ship only the production phone/tablet ABI.
        // This excludes x86 emulator libraries and older 32-bit ARM binaries.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Keep native libraries in the APK with the packaging expected by Android's
    // 16 KB page-size devices. The ORT dependency must also contain 16 KB-aligned
    // ELF LOAD segments; packaging alone cannot repair an incorrectly built .so.
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    // 1.22.0 includes the Android JNI alignment fix needed for 16 KB page-size
    // devices. Keep this version (or newer) for Play compatibility.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

tasks.register("printPocSize") {
    doLast {
        val releaseDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        println("Release APK directory: ${releaseDir.absolutePath}")
        releaseDir.listFiles()?.forEach { println("${it.length()} bytes ${it.name}") }
        val models = file("src/main/assets/models")
        models.listFiles()?.forEach { println("${it.length()} bytes model/${it.name}") }
    }
}
