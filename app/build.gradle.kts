plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

group = "com.tuapp"
version = "1.0-SNAPSHOT"

android {
    namespace = "com.example.citofono"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.citofono"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        signingConfig = signingConfigs.getByName("debug")
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
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
    buildFeatures {
        compose = true
    }
    buildToolsVersion = "35.0.1"
    ndkVersion = "28.0.12674087 rc2"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Jetpack Compose - using the Bill of Materials (BOM)
    // The BOM ensures that all Compose libraries use compatible versions.
    implementation(platform(libs.androidx.compose.bom)) // Assuming libs.versions.toml is updated to a recent version like 2024.05.00
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material) // For Material 2 components
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.material3) // For Material 3 components

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.mongodb:bson:5.6.1")

    // Third-party libraries (e.g., Apache POI)
    implementation(libs.poi.ooxml)

    // Testing - Unit Tests
    testImplementation(libs.junit)
    testImplementation("com.google.code.gson:gson:2.13.2")
    testImplementation("org.mongodb:bson:5.6.1")

    // Testing - Android Instrumented Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom)) // Also use BOM for testing
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debugging - Only included in debug builds
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register<JavaExec>("runChat") {
    // Ejecutar la compilación de las clases main/debug antes de correr
    dependsOn("compileDebugKotlin")
    group = "application"
    description = "Ejecuta el chat interactivo de forma aislada"

    // Usar el output del compilador para el source set `debug`
    val debugClassesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile
    // En un módulo Android la configuración se llama 'debugRuntimeClasspath'
    classpath = files(debugClassesDir) + configurations.getByName("debugRuntimeClasspath")

    mainClass.set("com.example.citofono.ChatInteractiveKt")

    // Habilitar entrada estándar
    standardInput = System.`in`
}