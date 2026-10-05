// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val publicContent = rootProject.file("../content")
val contentFiles = publicContent.resolve("public-files.txt").readLines().filter { it.isNotBlank() }
val syncTestContent by tasks.registering(Sync::class) {
    from(publicContent) { include(contentFiles) }
    into(layout.buildDirectory.dir("generated/testContent/content"))
    inputs.file(publicContent.resolve("public-files.txt"))
}

android {
    namespace = "xyz.steier.sidetube.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        // Schema-Abzuege werden versioniert; sie sind die Grundlage der Migrationstests.
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }
    sourceSets { getByName("test") {
        assets.srcDir("$projectDir/schemas")
        resources.srcDir(layout.buildDirectory.dir("generated/testContent"))
    } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

tasks.withType<Test>().configureEach { dependsOn(syncTestContent) }
tasks.matching { it.name.contains("UnitTestJavaRes") }.configureEach { dependsOn(syncTestContent) }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    // Die Datenbank gehoert zur Schnittstelle des Kerns, deshalb weitergereicht.
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
