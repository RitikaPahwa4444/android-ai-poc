plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "org.commons.ai"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    publishing { singleVariant("release") { withSourcesJar() } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    api(project(":commons-ai:vision"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "org.commons"
            artifactId = "commons-ai"
            version = "0.1.0"
            afterEvaluate { from(components["release"]) }
        }
    }
}
