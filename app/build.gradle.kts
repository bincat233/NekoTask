import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "me.superbear.todolist"
    compileSdk = 37

    defaultConfig {
        applicationId = "me.superbear.todolist"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // No default OpenAI/DeepSeek key in release builds - the dev's local.properties keys
            // must never be compiled into a package meant for real users. Real users enter their
            // own key in Settings; the app runs fine without one, AI features just no-op until then.
            buildConfigField("String", "OPENAI_API_KEY", "\"\"")
            buildConfigField("String", "OPENAI_BASE_URL", "\"${localProperties.getProperty("OPENAI_BASE_URL") ?: "https://api.openai.com"}\"")
            buildConfigField("String", "OPENAI_MODEL", "\"${localProperties.getProperty("OPENAI_MODEL") ?: "gpt-4.1-mini"}\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"\"")
            buildConfigField("String", "TAVILY_API_KEY", "\"\"")
            // Controls whether to force no blur fallback even on supported versions
            buildConfigField("boolean", "DEBUG_FORCE_NO_BLUR_FALLBACK", "false")
            // Peek timeout debug flags
            buildConfigField("boolean", "DEBUG_DISABLE_PEEK_TIMEOUT", "false")
            buildConfigField("long", "DEBUG_PEEK_TIMEOUT_MS", "-1L")
        }
        debug {
            // Debug-only convenience default: read the developer's own keys from
            // local.properties (gitignored) so debug builds work out of the box.
            buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties.getProperty("OPENAI_API_KEY") ?: ""}\"")
            buildConfigField("String", "OPENAI_BASE_URL", "\"${localProperties.getProperty("OPENAI_BASE_URL") ?: "https://api.openai.com"}\"")
            buildConfigField("String", "OPENAI_MODEL", "\"${localProperties.getProperty("OPENAI_MODEL") ?: "gpt-4.1-mini"}\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"${localProperties.getProperty("DEEPSEEK_API_KEY") ?: ""}\"")
            buildConfigField("String", "TAVILY_API_KEY", "\"${localProperties.getProperty("TAVILY_API_KEY") ?: ""}\"")
            buildConfigField("boolean", "FORCE_DELETE_DB", "true")
            // Controls whether to force no blur fallback even on supported versions
            buildConfigField("boolean", "DEBUG_FORCE_NO_BLUR_FALLBACK", "false")
            // Peek timeout debug flags
            buildConfigField("boolean", "DEBUG_DISABLE_PEEK_TIMEOUT", "false")
            buildConfigField("long", "DEBUG_PEEK_TIMEOUT_MS", "-1L")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("ai.koog:agents-core-android:1.0.0")
    val room_version = "2.7.2"
    implementation("androidx.room:room-runtime:${room_version}")
    implementation("androidx.room:room-ktx:${room_version}")
    ksp("androidx.room:room-compiler:$room_version")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("com.github.jeziellago:compose-markdown:0.5.6")
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}