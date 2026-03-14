package com.github.k1rakishou.deprecated.persist_state

@Deprecated("Deprecated")
data class ApkUpdateInfoJsonDeprecated(
    val versionCode: Long? = null,
    val buildNumber: Long? = null,
    val versionName: String? = null
)

@Deprecated("Deprecated")
data class ApkUpdateInfo(
    val versionCode: Long,
    val buildNumber: Long,
    val versionName: String?
)