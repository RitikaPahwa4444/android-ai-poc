plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "org.commons.ai.runtime"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    consumerProguardFiles("consumer-rules.pro")
    publishing { singleVariant("release") { withSourcesJar() } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation(files("onnxruntime-android-1.22.0-reduced.aar"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "org.commons"
            artifactId = "commons-ai-runtime"
            version = "0.1.0"
            afterEvaluate { from(components["release"]) }
        }
    }
}
