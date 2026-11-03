import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kspGoogle)
    alias(libs.plugins.kotest)
    alias(libs.plugins.kotlinSerialization)
}

group = "io.github.lauro299"
version = "0.0.4-beta01"

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    android {
        namespace = "io.litellmkts"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    linuxX64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.koin.core)
                api(libs.koin.annotation)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.contentNegotiation)
                implementation(libs.ktor.serialization)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.engine)
                implementation(libs.koin.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.contentNegotiation)
                implementation(libs.ktor.serialization)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.kotest.extensions.koin)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(libs.ktor.client.okhttp)
            }
        }
        val linuxX64Test by getting {
            dependencies {
                implementation(libs.stately.common.linuxx64)
                implementation(libs.kotest.assertions.api.linuxx64)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.android)
            }
        }
        val linuxX64Main by getting {
            dependencies {
                implementation(libs.ktor.client.curl)
            }
        }

        val macosArm64Main by getting {
            dependencies {
                dependsOn(commonMain)
                implementation(libs.ktor.client.darwin)
            }
        }

        val macosX64Main by getting{
            dependencies{
                dependsOn(commonMain)
                implementation(libs.ktor.client.darwin)
            }
        }

        val iosX64Main by getting {
            dependencies{
                dependsOn(commonMain)
                implementation(libs.ktor.client.darwin)
            }
        }

        val iosArm64Main by getting {
            dependencies{
                dependsOn(commonMain)
                implementation(libs.ktor.client.darwin)
            }
        }

        val iosSimulatorArm64Main by getting {
            dependencies{
                dependsOn(commonMain)
                implementation(libs.ktor.client.darwin)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
    sourceSets.named("commonMain").configure {
        kotlin.srcDirs("build/generated/ksp/metadata/commonMain/kotlin")
    }
}

// Compile with whatever JDK runs Gradle (e.g. Java 27) but keep emitting Java 17 bytecode.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

mavenPublishing {
    publishToMavenCentral()

    //signAllPublications()

    coordinates(group.toString(), "litellmkts", version.toString())

    pom {
        name = "litellmkts"
        description = "A library for kotlin multiplatform use."
        inceptionYear = "2024"
        url = "https://github.com/lauro299/litellmkts/"
        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "lauro299"
                name = "lau"
            }
        }
        scm {
            url = "https://github.com/lauro299/litellmkts/"
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.koin.annotation.ksp)
    ksp(libs.koin.annotation.ksp)
    /*add("kspAndroid", libs.koin.annotation.ksp)
    add("kspMacosArm64", libs.koin.annotation.ksp)
    add("kspMacosX64", libs.koin.annotation.ksp)
    add("kspLinuxX64", libs.koin.annotation.ksp)
    add("kspIosX64", libs.koin.annotation.ksp)
    add("kspIosArm64", libs.koin.annotation.ksp)
    add("kspIosSimulatorArm64", libs.koin.annotation.ksp)
    add("kspWasmJs", libs.koin.annotation.ksp)*/
}

project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}


tasks.named("sourcesJar") {
    dependsOn("kspCommonMainKotlinMetadata")
}

tasks.named("jvmSourcesJar") {
    dependsOn("kspCommonMainKotlinMetadata")
}
