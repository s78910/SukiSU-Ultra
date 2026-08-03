plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

val androidMinSdkVersion by extra(26)
val androidTargetSdkVersion by extra(37)
val androidCompileSdkVersion by extra(37)
val androidBuildToolsVersion by extra("36.1.0")
val androidCompileNdkVersion by extra(libs.versions.ndk.get())
val androidSourceCompatibility by extra(JavaVersion.VERSION_21)
val androidTargetCompatibility by extra(JavaVersion.VERSION_21)
val managerVersionCode by extra(
    providers.environmentVariable("MANAGER_BUILD_VERSION_CODE").orElse("2100000000").get().toInt()
)
val managerUnsignedVersionCode by extra(
    providers.environmentVariable("MANAGER_VERSION_CODE").orElse("2200510202").get().toLong()
)
val managerVersionName by extra(
    providers.environmentVariable("MANAGER_VERSION_NAME").orElse("22.51.202").get()
)

fun getGitCommitCount(): Int {
    return providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toInt()
}

fun getGitDescribe(): String {
    return providers.exec {
        commandLine("git", "describe", "--tags", "--always", "--abbrev=0")
    }.standardOutput.asText.get().trim()
}
