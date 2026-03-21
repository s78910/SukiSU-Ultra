import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.api.AndroidBasePlugin

plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.lsplugin.cmaker)
}

cmaker {
    default {
        arguments.addAll(
            arrayOf(
                "-DANDROID_STL=none",
            )
        )
        abiFilters("arm64-v8a", "x86_64", "armeabi-v7a")
    }
    buildTypes {
        if (it.name == "release") {
            arguments += "-DDEBUG_SYMBOLS_PATH=${layout.buildDirectory.asFile.get().absolutePath}/symbols"
        }
    }
}

val androidMinSdkVersion = 26
val androidTargetSdkVersion = 36
val androidCompileSdkVersion = 36
val androidBuildToolsVersion = "36.1.0"
val androidCompileNdkVersion by extra(libs.versions.ndk.get())
val androidCmakeVersion by extra("3.22.0+")
val androidSourceCompatibility = JavaVersion.VERSION_21
val androidTargetCompatibility = JavaVersion.VERSION_21
val fallbackManagerVersionCode = 40545
val fallbackManagerVersionName = "v4.1.2"
val managerVersionCode by extra(
    System.getenv("MANAGER_VERSION_CODE")?.toIntOrNull()
        ?: getGitCommitCount()?.let { 4 * 10000 + it - 2815 }
        ?: fallbackManagerVersionCode
)
val managerVersionName by extra(
    System.getenv("MANAGER_VERSION_NAME")
        ?: getGitDescribe()
        ?: fallbackManagerVersionName
)

fun runGitCommand(vararg args: String): String? {
    return runCatching {
        providers.exec {
            commandLine(listOf("git", *args))
        }.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()
}

fun getGitCommitCount(): Int? = runGitCommand("rev-list", "--count", "HEAD")?.toIntOrNull()

fun getGitDescribe(): String? = runGitCommand("describe", "--tags", "--always", "--abbrev=0")

subprojects {
    plugins.withType(AndroidBasePlugin::class.java) {
        extensions.configure(CommonExtension::class.java) {
            compileSdk = androidCompileSdkVersion
            ndkVersion = androidCompileNdkVersion
            buildToolsVersion = androidBuildToolsVersion

            defaultConfig {
                minSdk = androidMinSdkVersion
                if (this is ApplicationDefaultConfig) {
                    targetSdk = androidTargetSdkVersion
                    versionCode = managerVersionCode
                    versionName = managerVersionName
                }
                ndk {
                    abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a")
                }
            }

            lint {
                abortOnError = true
                checkReleaseBuilds = false
            }

            compileOptions {
                sourceCompatibility = androidSourceCompatibility
                targetCompatibility = androidTargetCompatibility
            }
        }
    }
}
