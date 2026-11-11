plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.king365.fire"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.king365.fire"
        minSdk = 22
        targetSdk = 34
        // Chaque compilation GitHub porte un numéro plus grand : la mise à jour s'installe par-dessus
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
        // Fire TV : processeurs ARM uniquement (garde l'appli plus légère avec VLC)
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        // Dépôt GitHub qui publie les mises à jour (rempli automatiquement pendant la compilation)
        val repo = System.getenv("GITHUB_REPOSITORY") ?: "falken2009/falken-iptv-fire"
        buildConfigField("String", "UPDATE_REPO", "\"$repo\"")
    }

    // Toujours la même clé : les mises à jour s'installent par-dessus sans perdre tes réglages
    signingConfigs {
        create("king") {
            storeFile = file("king365.keystore")
            storePassword = "king365pass"
            keyAlias = "king365"
            keyPassword = "king365pass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("king")
        }
        debug {
            signingConfig = signingConfigs.getByName("king")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // perso = ta version (GitHub, tout débloqué) · store = version Amazon Appstore avec Premium
    flavorDimensions += "distribution"
    productFlavors {
        create("perso") {
            dimension = "distribution"
            buildConfigField("boolean", "PREMIUM", "false")
            buildConfigField("boolean", "TMDB_ALLOWED", "true")
        }
        create("store") {
            dimension = "distribution"
            buildConfigField("boolean", "PREMIUM", "true")
            // La clé TMDB gratuite est réservée à l'usage non commercial
            buildConfigField("boolean", "TMDB_ALLOWED", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-extractor:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    // Lecteur VLC de secours (chaînes que le lecteur de la Fire TV n'arrive pas à lire)
    implementation("org.videolan.android:libvlc-all:3.5.1")
    // Achats intégrés Amazon (version store uniquement)
    "storeImplementation"("com.amazon.device:amazon-appstore-sdk:3.0.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
