// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val productVersion = Properties().apply {
    rootProject.file("../version.properties").inputStream().use { load(it) }
}

val publicContent = rootProject.file("../content")
val contentFiles = publicContent.resolve("public-files.txt").readLines().filter { it.isNotBlank() }
contentFiles.forEach { require(publicContent.resolve(it).isFile) { "Missing public content: $it" } }
val syncContent by tasks.registering(Sync::class) {
    from(publicContent) { include(contentFiles) }
    into(layout.buildDirectory.dir("generated/sidetubeAssets/content"))
    inputs.file(publicContent.resolve("public-files.txt"))
}

android {
    namespace = "xyz.steier.sidetube"
    compileSdk = 36

    defaultConfig {
        applicationId = "xyz.steier.sidetube"
        minSdk = 26
        targetSdk = 36
        versionCode = productVersion.getProperty("ANDROID_VERSION_CODE").toInt()
        versionName = productVersion.getProperty("MARKETING_VERSION")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Der Schluessel kommt ausschliesslich aus local.properties (nicht versioniert).
        // Ohne ihn laeuft die App: freigegebene Videos spielen, Kanalseiten nachladen und
        // die Kanalsuche brauchen ihn.
        buildConfigField("String", "YOUTUBE_API_KEY",
            "\"${localProperties.getProperty("YOUTUBE_API_KEY", "")}\"")

        resourceConfigurations += listOf("de", "en")
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/sidetubeAssets"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

tasks.named("preBuild") { dependsOn(syncContent) }

dependencies {
    // Tink references these compile-time annotations; supply them to R8 without packaging them.
    compileOnly(libs.errorprone.annotations)
    compileOnly(libs.jsr305)
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.security.crypto)
    // QR-Scan des Einrichtungscodes (ADR 0005): CameraX fuer das Kamerabild, ZXing core zum Erkennen –
    // beides ohne Google-Dienste, damit es auch auf dem SidePhone laeuft.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    // Nur PreviewView wird gebraucht; camera-video (und damit Guava) bleibt draussen – es kollidierte mit
    // den error_prone_annotations oben und vergroesserte die APK ohne Nutzen.
    implementation(libs.androidx.camera.view) { exclude(group = "androidx.camera", module = "camera-video") }
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
