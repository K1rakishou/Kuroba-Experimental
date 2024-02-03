package com.github.k1rakishou.persist_state

data class ApkUpdateInfoJson(
    val versionCode: Long? = null,
    val buildNumber: Long? = null,
    val versionName: String? = null
)

data class ApkUpdateInfo(
    val versionCode: Long,
    val buildNumber: Long,
    val versionName: String?
)