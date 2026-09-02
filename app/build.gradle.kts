import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.isFile) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun String.toBuildConfigStringLiteral(): String =
    "\"" +
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n") +
        "\""

val ackBaseUrl = localProperties.getProperty("ackline.ackBaseUrl").orEmpty()
val payloadEncryptionKid = "ackline-main"

android {
    namespace = "com.edu.ackline"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.edu.ackline"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ACK_BASE_URL", ackBaseUrl.toBuildConfigStringLiteral())
        buildConfigField(
            "String",
            "PAYLOAD_ENCRYPTION_KID",
            payloadEncryptionKid.toBuildConfigStringLiteral(),
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Firebase Messaging (BoM-managed; no firebase-messaging-ktx).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Jetpack Compose (BoM-managed Material 3).
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.kotlinx.serialization.bom))
    // Kept for the existing XML theme (Theme.MaterialComponents.*).
    implementation(libs.material)
    implementation(libs.androidx.appcompat)

    debugImplementation(libs.compose.ui.tooling)

    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    // Provide the JVM implementation for tests that exercise the existing Android org.json parser.
    testImplementation("org.json:json:20250517")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}
