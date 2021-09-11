package com.github.k1rakishou.chan.core.mpv

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.MpvSettings
import com.github.k1rakishou.chan.core.mpv.MPVLib.mpvFormat.MPV_FORMAT_FLAG
import com.github.k1rakishou.chan.core.mpv.MPVLib.mpvFormat.MPV_FORMAT_INT64
import com.github.k1rakishou.chan.core.mpv.MPVLib.mpvFormat.MPV_FORMAT_NONE
import com.github.k1rakishou.chan.core.mpv.MPVLib.mpvFormat.MPV_FORMAT_STRING
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.core_logger.Logger
import kotlin.reflect.KProperty

/**
 * Taken from https://github.com/mpv-android/mpv-android
 *
 * DO NOT RENAME!
 * DO NOT MOVE!
 * NATIVE LIBRARIES DEPEND ON THE CLASS PACKAGE!
 * */

@DoNotStrip
class MPVView(
    context: Context,
    attrs: AttributeSet?
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {
    private var filePath: String? = null

    fun create(applicationContext: Context, appConstants: AppConstants) {
        if (!MPVLib.librariesAreLoaded()) {
            Logger.d(TAG, "create() librariesAreLoaded: false")
            return
        }

        Logger.d(TAG, "create()")

        MPVLib.create(applicationContext)

        // hwdec
        val hwdec = if (MpvSettings.hardwareDecoding.get()) {
            "mediacodec-copy"
        } else {
            "no"
        }

        Logger.d(TAG, "initOptions() hwdec: $hwdec")

        // vo: set display fps as reported by android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val disp = wm.defaultDisplay
            val refreshRate = disp.mode.refreshRate

            Logger.d(TAG, "Display ${disp.displayId} reports FPS of $refreshRate")
            MPVLib.setOptionString("override-display-fps", refreshRate.toString())
        } else {
            Logger.d(TAG, "Android version too old, disabling refresh rate functionality " +
              "(${Build.VERSION.SDK_INT} < ${Build.VERSION_CODES.M})")
        }

        MPVLib.setOptionString("video-sync", "audio")
        MPVLib.setOptionString("interpolation", "no")

        reloadFastVideoDecodeOption()

        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("hwdec", hwdec)
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        // TODO(KurobaEx): mpv
//        MPVLib.setOptionString("tls-verify", "yes")
//        MPVLib.setOptionString("tls-ca-file", "${this.context.filesDir.path}/cacert.pem")
        MPVLib.setOptionString("input-default-bindings", "yes")

        Logger.d(TAG, "initOptions() mpvDemuxerCacheMaxSize: ${appConstants.mpvDemuxerCacheMaxSize}")
        MPVLib.setOptionString("demuxer-max-bytes", "${appConstants.mpvDemuxerCacheMaxSize}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${appConstants.mpvDemuxerCacheMaxSize}")

        MPVLib.init()
        // certain options are hardcoded:
        MPVLib.setOptionString("save-position-on-quit", "no")
        MPVLib.setOptionString("force-window", "no")
        muteUnmute(true)

        surfaceTextureListener = this
        observeProperties()
    }

    fun destroy() {
        if (!MPVLib.librariesAreLoaded()) {
            Logger.d(TAG, "destroy() librariesAreLoaded: false")
            return
        }

        Logger.d(TAG, "destroy()")

        this.filePath = null

        // Disable surface callbacks to avoid using unintialized mpv state
        surfaceTextureListener = null
        MPVLib.destroy()
    }

    fun reloadFastVideoDecodeOption() {
        if (!MPVLib.librariesAreLoaded()) {
            Logger.d(TAG, "reloadFastVideoDecodeOption() librariesAreLoaded: false")
            return
        }

        if (MpvSettings.videoFastCode.get()) {
            Logger.d(TAG, "initOptions() videoFastCode: true")

            MPVLib.setOptionString("vd-lavc-fast", "yes")
            MPVLib.setOptionString("vd-lavc-skiploopfilter", "nonkey")
        } else {
            Logger.d(TAG, "initOptions() videoFastCode: false")

            MPVLib.setOptionString("vd-lavc-fast", "null")
            MPVLib.setOptionString("vd-lavc-skiploopfilter", "null")
        }
    }

    fun playFile(filePath: String) {
        if (!MPVLib.librariesAreLoaded()) {
            Logger.d(TAG, "playFile() librariesAreLoaded: false")
            return
        }

        this.filePath = filePath

        if (ChanSettings.videoAutoLoop.get()) {
            MPVLib.setOptionString("loop-file", "inf")
        } else {
            MPVLib.setOptionString("loop-file", "no")
        }
    }

    private fun observeProperties() {
        // This observes all properties needed by MPVView or MPVActivity
        data class Property(val name: String, val format: Int)
        val p = arrayOf(
            Property("time-pos", MPV_FORMAT_INT64),
            Property("demuxer-cache-duration", MPV_FORMAT_INT64),
            Property("duration", MPV_FORMAT_INT64),
            Property("pause", MPV_FORMAT_FLAG),
            Property("audio", MPV_FORMAT_FLAG),
            Property("mute", MPV_FORMAT_STRING),
            Property("video-params", MPV_FORMAT_NONE),
            Property("video-format", MPV_FORMAT_NONE),
        )

        for ((name, format) in p) {
            MPVLib.observeProperty(name, format)
        }
    }

    fun addObserver(o: MPVLib.EventObserver) {
        MPVLib.addObserver(o)
    }
    fun removeObserver(o: MPVLib.EventObserver) {
        MPVLib.removeObserver(o)
    }

    // Property getters/setters

    var paused: Boolean?
        get() = MPVLib.getPropertyBoolean("pause")
        set(paused) = MPVLib.setPropertyBoolean("pause", paused!!)

    val duration: Int?
        get() = MPVLib.getPropertyInt("duration")

    val demuxerCacheDuration: Int?
        get() = MPVLib.getPropertyInt("demuxer-cache-duration")

    var timePos: Int?
        get() = MPVLib.getPropertyInt("time-pos")
        set(progress) = MPVLib.setPropertyInt("time-pos", progress!!)

    val hwdecActive: Boolean
        get() = (MPVLib.getPropertyString("hwdec-current") ?: "no") != "no"

    var playbackSpeed: Double?
        get() = MPVLib.getPropertyDouble("speed")
        set(speed) = MPVLib.setPropertyDouble("speed", speed!!)

    val filename: String?
        get() = MPVLib.getPropertyString("filename")

    val avsync: String?
        get() = MPVLib.getPropertyString("avsync")

    val decoderFrameDropCount: Int?
        get() = MPVLib.getPropertyInt("decoder-frame-drop-count")

    val frameDropCount: Int?
        get() = MPVLib.getPropertyInt("frame-drop-count")

    val containerFps: Double?
        get() = MPVLib.getPropertyDouble("container-fps")

    val estimatedVfFps: Double?
        get() = MPVLib.getPropertyDouble("estimated-vf-fps")

    val videoW: Int?
        get() = MPVLib.getPropertyInt("video-params/w")

    val videoH: Int?
        get() = MPVLib.getPropertyInt("video-params/h")

    val videoAspect: Double?
        get() = MPVLib.getPropertyDouble("video-params/aspect")

    val videoCodec: String?
        get() = MPVLib.getPropertyString("video-codec")

    val audioCodec: String?
        get() = MPVLib.getPropertyString("audio-codec")

    val audioSampleRate: Int?
        get() = MPVLib.getPropertyInt("audio-params/samplerate")

    val audioChannels: Int?
        get() = MPVLib.getPropertyInt("audio-params/channel-count")

    class TrackDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MPVLib.getPropertyString(property.name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1)
                MPVLib.setPropertyString(property.name, "no")
            else
                MPVLib.setPropertyInt(property.name, value)
        }
    }

    var vid: Int by TrackDelegate()
    var sid: Int by TrackDelegate()
    var aid: Int by TrackDelegate()

    // Commands

    fun cyclePause() = MPVLib.command(arrayOf("cycle", "pause"))

    val isMuted: Boolean
        get() = MPVLib.getPropertyString("mute") != "no"

    fun muteUnmute(mute: Boolean) {
        if (mute) {
            MPVLib.setPropertyString("mute", "yes")
        } else {
            MPVLib.setPropertyString("mute", "no")
        }
    }

    fun cycleHwdec() = MPVLib.command(arrayOf("cycle-values", "hwdec", "mediacodec-copy", "no"))

    fun cycleSpeed() {
        val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val currentSpeed = playbackSpeed ?: 1.0
        val index = speeds.indexOfFirst { it > currentSpeed }
        playbackSpeed = speeds[if (index == -1) 0 else index]
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Logger.d(TAG, "attaching surface")

        MPVLib.attachSurface(Surface(surfaceTexture))
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")

        if (filePath != null) {
            MPVLib.command(arrayOf("loadfile", filePath as String))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            MPVLib.setPropertyString("vo", "gpu")
        }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        Logger.d(TAG, "detaching surface")

        MPVLib.setPropertyString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()

        return true
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
    }

    companion object {
        private const val TAG = "MPVView"
    }
}
