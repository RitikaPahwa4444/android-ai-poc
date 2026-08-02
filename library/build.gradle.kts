plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "org.commons.ai"
    compileSdk = 36
    defaultConfig { minSdk = 21 }
    publishing { singleVariant("release") { withSourcesJar() } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    api(files("src/main/onnxruntime-android-1.22.0-reduced.aar"))
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "org.commons"
            artifactId = "commons-ai"
            version = "0.1.0"
            afterEvaluate {
                from(components["release"])
                pom.withXml {
                    asNode().appendNode("dependencies").appendNode("dependency").apply {
                        appendNode("groupId", "org.commons")
                        appendNode("artifactId", "commons-ai-runtime")
                        appendNode("version", "0.1.0")
                        appendNode("scope", "runtime")
                    }
                }
            }
        }
        register<MavenPublication>("runtime") {
            groupId = "org.commons"
            artifactId = "commons-ai-runtime"
            version = "0.1.0"
            artifact("src/main/onnxruntime-android-1.22.0-reduced.aar")
        }
    }
}
