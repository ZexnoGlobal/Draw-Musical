plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    id("com.cleveradssolutions.gradle-plugin") version "4.8.0"
}

android {
    namespace = "com.wcs.drawing.game.draw.with.music.fun"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.wcs.drawing.game.draw.with.music.fun"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf(
                    "androidx.**",
                    "kotlin.**",
                    "kotlinx.**"
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

cas {
    adapters {
        appLovin = true
        audienceNetwork = true
        bigoAds = true
        casExchange = true
        chartboost = true
        dtExchange = true
        googleAds = true
        hyprMX = true
        inMobi = true
        ironSource = true
        kidoz = true
        liftoffMonetize = true
        mintegral = true
        ogury = true
        pangle = true
        prado = true
        startIO = true
        superAwesome = true
        unityAds = true
        yangoAds = true
        ysoNetwork = true
        smaato = true
        verve = true
        maticoo = true
        monetrix = true
    }
}

dependencies {
    implementation("com.android.billingclient:billing:9.1.0")

    // CAS plugin manages the SDK and selected adapters.

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.junit)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )
    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
    debugImplementation(libs.androidx.compose.ui.tooling)
}