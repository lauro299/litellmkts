plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "org.llmlitekt.example"
version = "0.0.1"

kotlin {
    // macosArm64 also runs the tests on the Mac without a simulator.
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64(),
        macosX64()
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            // Name of the module imported from Swift: `import LitellmktsKit`
            baseName = "LitellmktsKit"
            isStatic = true
        }
    }

    sourceSets {
        appleMain.dependencies {
            implementation(project(":litellmkts"))
            implementation(libs.ktor.client.core)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        appleTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
