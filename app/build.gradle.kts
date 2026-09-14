plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.objectbox")
}

android {
    namespace = "com.oppolocation.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oppolocation.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 72
        versionName = "7.2"
        val mapTilerKey = project.findProperty("MAPTILER_API_KEY")?.toString() ?: "YOUR_MAPTILER_API_KEY"
        buildConfigField("String", "MAPTILER_API_KEY", "\"$mapTilerKey\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-location:21.4.0")
}
