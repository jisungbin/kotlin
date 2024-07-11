plugins {
    id("com.gradle.enterprise")
    id("com.gradle.common-custom-user-data-gradle-plugin") apply false
}

val buildProperties = getKotlinBuildPropertiesForSettings(settings)

val buildScanServer = buildProperties.buildScanServer

if (buildProperties.buildScanServer != null) {
    plugins.apply("com.gradle.common-custom-user-data-gradle-plugin")
}

gradleEnterprise {
    buildScan {
        if (buildScanServer != null) {
            server = buildScanServer
            publishAlways()

            capture {
                isTaskInputFiles = true
                isBuildLogging = true
                isBuildLogging = true
                isUploadInBackground = true
            }
        } else {
            termsOfServiceUrl = "https://gradle.com/terms-of-service"
            termsOfServiceAgree = "yes"
        }

        val overriddenUsername = (buildProperties.getOrNull("kotlin.build.scan.username") as? String)?.trim()
        val overriddenHostname = (buildProperties.getOrNull("kotlin.build.scan.hostname") as? String)?.trim()
        val isTeamCity = buildProperties.isTeamcityBuild
        if (buildProperties.isJpsBuildEnabled) {
            tag("JPS")
        }
        obfuscation {
            ipAddresses { _ -> listOf("0.0.0.0") }
            hostname { _ ->
                when {
                    overriddenHostname != null -> overriddenHostname
                    else -> "concealed"
                }
            }
            username { originalUsername ->
                when {
                    isTeamCity -> "TeamCity"
                    overriddenUsername.isNullOrEmpty() -> "concealed"
                    overriddenUsername == "<default>" -> originalUsername
                    else -> overriddenUsername
                }
            }
        }
    }
}
