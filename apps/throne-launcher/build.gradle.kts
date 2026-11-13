plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.infinitum.throne.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.infinitum.throne.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // ^KETER signing key lives outside the repo. Set throne.keystore, throne.keystore.password,
    // throne.key.alias and throne.key.password in ~/.gradle/gradle.properties (or THRONE_* env vars).
    // Without them (CI), debug builds use the Android Gradle plugin's per-machine debug key.
    val throneKeystore = providers.gradleProperty("throne.keystore").orElse(providers.environmentVariable("THRONE_KEYSTORE")).orNull
    signingConfigs {
        if (throneKeystore != null) {
            create("throne") {
                storeFile = file(throneKeystore)
                storePassword = providers.gradleProperty("throne.keystore.password").orElse(providers.environmentVariable("THRONE_KEYSTORE_PASSWORD")).get()
                keyAlias = providers.gradleProperty("throne.key.alias").orElse(providers.environmentVariable("THRONE_KEY_ALIAS")).get()
                keyPassword = providers.gradleProperty("throne.key.password").orElse(providers.environmentVariable("THRONE_KEY_PASSWORD")).get()
            }
        }
    }

    buildTypes {
        debug {
            if (throneKeystore != null) signingConfig = signingConfigs.getByName("throne")
        }
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }

    sourceSets.named("main") {
        assets.srcDir(rootProject.file("apps/throne-suite/src/main/assets"))
    }

    flavorDimensions += "edition"
    productFlavors {
        create("keter") {
            dimension = "edition"
            versionNameSuffix = "-keter"
            buildConfigField("String", "EDITION", "\"^KETER\"")
        }
        create("archangel") {
            dimension = "edition"
            versionNameSuffix = "-archangel"
            buildConfigField("String", "EDITION", "\"ARCH//ANGEL\"")
        }
    }
}

dependencies {
    implementation(project(":libs:throne-core"))
    implementation(project(":libs:throne-ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
