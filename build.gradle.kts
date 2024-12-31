plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply  false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    alias(libs.plugins.kspGoogle) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}
