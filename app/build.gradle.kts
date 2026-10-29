import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose.compiler)
}

android {
    namespace = "app.unitconverter"
    compileSdk = 36

    defaultConfig {
        // Production application ID. Future update APKs must keep this exact
        // value and the existing production signing identity.
        applicationId = "com.kirilliodas.unitconverter"
        minSdk = 26
        targetSdk = 36
        // V2.1.0 added the native GitHub in-app update system; v2.1.1
        // adds optional Material You dynamic color. Every future
        // distributed update must increase both values.
        versionCode = 3
        versionName = "2.1.1"
    }

    androidResources {
        // Keep exactly the complete offline locales offered in Settings.
        // (localeFilters is the AGP 8.13 replacement for the deprecated
        // resourceConfigurations DSL.)
        localeFilters.addAll(
            listOf("en", "fr", "es", "pt", "de", "it", "zh-rCN", "ja", "ko", "ar")
        )
    }

    signingConfigs {
        create("release") {
            storeFile = file(
                System.getenv("UNIT_CONVERTER_KEYSTORE")
                    ?: "${System.getProperty("user.home")}/Documents/AndroidSigning/unit-converter-v2-release.jks"
            )
            storePassword = System.getenv("UNIT_CONVERTER_STORE_PASSWORD") ?: ""
            keyAlias = "unitconverter-release"
            keyPassword = System.getenv("UNIT_CONVERTER_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        // Targeted workaround validated on the Windows development machine:
        // the machine-local local.properties SDK path triggered a
        // PropertyEscape error even with forward slashes. local.properties
        // is git-ignored and never part of the shipped source.
        disable += "PropertyEscape"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
