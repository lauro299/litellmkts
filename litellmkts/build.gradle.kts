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
version = "0.1.0-beta01"

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
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.koin.core)
            api(libs.koin.annotation)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.contentNegotiation)
            implementation(libs.ktor.serialization)
            implementation(libs.kotlinx.serialization.json)
            api(libs.ktor.client.logging)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.engine)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.contentNegotiation)
            implementation(libs.ktor.serialization)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotest.extensions.koin)
        }
        jvmMain.dependencies {
            api(libs.ktor.client.okhttp)
        }
        linuxX64Test.dependencies {
            implementation(libs.stately.common.linuxx64)
            implementation(libs.kotest.assertions.api.linuxx64)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }
        linuxX64Main.dependencies {
            implementation(libs.ktor.client.curl)
        }
        // iOS and macOS targets via the default hierarchy template
        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
    sourceSets.named("commonMain").configure {
        kotlin.srcDirs("build/generated/ksp/metadata/commonMain/kotlin")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
    /*add("kspAndroid", libs.koin.annotation.ksp)
    add("kspMacosArm64", libs.koin.annotation.ksp)
    add("kspMacosX64", libs.koin.annotation.ksp)
    add("kspLinuxX64", libs.koin.annotation.ksp)
    add("kspIosX64", libs.koin.annotation.ksp)
    add("kspIosArm64", libs.koin.annotation.ksp)
    add("kspIosSimulatorArm64", libs.koin.annotation.ksp)
    add("kspWasmJs", libs.koin.annotation.ksp)*/
}

// Target-specific KSP tasks read the Koin code generated for commonMain
project.tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
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
