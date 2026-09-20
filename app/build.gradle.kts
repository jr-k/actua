plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

composeCompiler {
    val composeMetricsDir = layout.buildDirectory.dir("compose_metrics")
    metricsDestination = composeMetricsDir
    reportsDestination = composeMetricsDir
}

val releaseKeystorePath = providers.environmentVariable("ACTUA_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ACTUA_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ACTUA_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ACTUA_KEY_PASSWORD").orNull

android {
    namespace = "com.azimulkabir.actua"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.azimulkabir.actua"
        minSdk = 28
        targetSdk = 37
        versionCode = 20260901
        versionName = "2026.9.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            releaseKeystorePath?.let { storeFile = file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    // F-Droid rejects the opaque dependency metadata in APK signing blocks.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug { }
        create("instrumented") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".test"
            matchingFallbacks += listOf("debug")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
        }
    }
    testBuildType = "instrumented"
    // AGP built-in Kotlin inherits this target, keeping Java and Kotlin bytecode aligned.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

baselineProfile {
    // Skip regenerating the profile on every build; run `:app:generateBaselineProfile`
    // manually (on a connected device/emulator) when app flows change meaningfully.
    automaticGenerationDuringBuild = false
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.profileinstaller)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.pdfbox.android)
    testImplementation(libs.junit)
    // Real org.json impl for JVM unit tests — the Android stub jar throws on use.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    add("instrumentedImplementation", libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
