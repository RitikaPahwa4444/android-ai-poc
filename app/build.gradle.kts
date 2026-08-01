plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.aipoc"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.aipoc"
        // ONNX Runtime Android 1.20.0 is the newest version verified here with API 21.
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
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
