plugins {
    kotlin("multiplatform")
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    @OptIn(org.jetbrains.kotlin.swiftexport.ExperimentalSwiftExportDsl::class)
    swiftexport {
        moduleName = "Shared"
        flattenPackage = "com.github.jetbrains.swiftexport"

        export(project(":subproject")) {
            moduleName = "Subproject"
            flattenPackage = "com.subproject.library"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":subproject"))
        }
    }
}
