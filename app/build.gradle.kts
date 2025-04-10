plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.havenspure_kotlin_prototype"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.havenspure_kotlin_prototype"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.0" // For Kotlin 1.9.0
    }

    packaging {
        resources {
            //excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("META-INF/LICENSE.md")
            excludes.add("META-INF/NOTICE.md")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/LICENSE-notice.md")  // Add this line
        }
    }
}

dependencies {

    // Core dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose dependencies - use the BOM and don't specify versions
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.foundation.android)
    implementation("androidx.compose.material:material-icons-extended")

    // Google services
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // OSMDroid - the main library
    implementation ("org.osmdroid:osmdroid-android:6.1.16")

    // Core OSMDroid
    implementation("org.osmdroid:osmdroid-android:6.1.16")

    // OSMBonusPack - required for routing features
    implementation("com.github.MKergall:osmbonuspack:6.9.0")

    implementation("org.osmdroid:osmdroid-mapsforge:6.1.16")


    // Needed by OSMBonusPack
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // For JSON serialization
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    // Add GraphHopper Core
    //implementation ("com.graphhopper:graphhopper-core:1.0")
    // GraphHopper dependencies
    //implementation("com.graphhopper:graphhopper-core:6.2")
    //implementation("com.graphhopper:graphhopper-api:6.2")
    //implementation("com.graphhopper:graphhopper-web-api:6.2")
    //implementation("com.graphhopper:graphhopper-core:2.4")

    implementation("com.graphhopper:graphhopper-reader-osm:2.4")
    implementation(libs.androidx.preference.ktx)
    implementation(libs.screenshot.validation.junit.engine)
    // If you need specific map matching features
    //implementation ("com.graphhopper:graphhopper-map-matching:1.0")
    // Retrofit for network requests
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")


// Room Database
    implementation("androidx.room:room-runtime:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")
    ksp("androidx.room:room-compiler:2.5.2") // If using KSP
// OR kapt("androidx.room:room-compiler:2.5.2") // If using KAPT

// Kotlin Coroutines (may already be included through other dependencies)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// Media Player - for audio playback
    implementation("androidx.media:media:1.6.0")

// Lifecycle components
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")


    // Add Timber for logging
    implementation ("com.jakewharton.timber:timber:5.0.1")  // Use the latest version

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)


}
configurations.all {
    resolutionStrategy.force(
        "org.jetbrains.kotlin:kotlin-stdlib:1.9.21",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.21",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.21"
    )
}