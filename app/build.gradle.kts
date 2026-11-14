import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ar.escusub.controlips"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.escusub.controlips"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "2.0"

        // Opcional: dejar el servidor preconfigurado en el APK.
        // En local.properties o gradle.properties: supabase.url=https://xxxx.supabase.co  y  supabase.key=...
        val props = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        fun prop(k: String) = (props.getProperty(k) ?: (project.findProperty(k) as String?) ?: "").trim()
        buildConfigField("String", "SUPABASE_URL", "\"${prop("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${prop("supabase.key")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firmado con la clave de debug para poder instalar el APK directamente en el celular.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Mirror de Maven Central para que Robolectric descargue android-all.
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("org.postgresql:postgresql:42.7.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
