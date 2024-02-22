import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNodeJsExtension

plugins {
    `embedded-kotlin`
}

group = "build-logic"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jib.core)
    implementation(libs.google.cloud.run)
    implementation(gradleApi())
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.symbol.processing.gradle.plugin)
}