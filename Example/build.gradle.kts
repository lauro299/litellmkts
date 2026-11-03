plugins {
    kotlin("jvm")
}

group = "org.llmlitekt.example"
version = "0.0.1"

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}
repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":litellmkts"))
}

tasks.test {
    useJUnitPlatform()
}