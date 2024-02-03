package com.github.k1rakishou.chan.utils

import com.github.k1rakishou.common.groupOrNull
import java.util.regex.Pattern

object ReleaseHelpers {
    private val RELEASE_VERSION_CODE_PATTERN = Pattern.compile("v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})-release$")
    private val BETA_VERSION_CODE_PATTERN = Pattern.compile("v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d+))?-beta\$")

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun calculateReleaseVersionCode(versionCodeString: String?): Long {
        if (versionCodeString.isNullOrBlank()) {
            return 0
        }

        val versionMatcher = RELEASE_VERSION_CODE_PATTERN.matcher(versionCodeString)
        if (!versionMatcher.find()) {
            return 0
        }

        return versionMatcher.group(3).toLong() +
                versionMatcher.group(2).toLong() * 100L +
                versionMatcher.group(1).toLong() * 10000L
    }

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun calculateBetaVersionCode(versionCodeString: String?): BetaVersionCode {
        if (versionCodeString.isNullOrBlank()) {
            return BetaVersionCode()
        }

        val versionMatcher = BETA_VERSION_CODE_PATTERN.matcher(versionCodeString)
        if (!versionMatcher.find()) {
            return BetaVersionCode()
        }

        val versionCode = versionMatcher.group(3).toLong() +
                versionMatcher.group(2).toLong() * 100L +
                versionMatcher.group(1).toLong() * 10000L

        val buildNumber = versionMatcher.groupOrNull(4)
            ?.toLongOrNull()
            ?: 0L

        return BetaVersionCode(
            versionCode = versionCode,
            buildNumber = buildNumber
        )
    }

    data class BetaVersionCode(
        val versionCode: Long = 0,
        val buildNumber: Long = 0
    )

}