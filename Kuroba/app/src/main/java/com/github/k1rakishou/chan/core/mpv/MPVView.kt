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
import com.github.k1rakishou.model.util.ChanPostUtils
import java.io.File
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
    private var surfaceAttached = false
    private var _initialized = false

    val initialized: Boolean
        get() = _initialized

    fun create(applicationContext: Context, appConstants: AppConstants) {
        if (!MPVLib.librariesAreLoaded()) {
            Logger.d(TAG, "create() librariesAreLoaded: false")
            _initialized = false
            return
        }

        if (MPVLib.isCreated()) {
            return
        }

        Logger.d(TAG, "create()")

        MPVLib.mpvCreate(applicationContext)
        setupMpvConf(applicationContext)

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
            MPVLib.mpvSetOptionString("override-display-fps", refreshRate.toString())
        } else {
            Logger.d(TAG, "Android version too old, disabling refresh rate functionality " +
              "(${Build.VERSION.SDK_INT} < ${Build.VERSION_CODES.M})")
        }

        MPVLib.mpvSetOptionString("video-sync", "audio")
        MPVLib.mpvSetOptionString("interpolation", "no")

        reloadFastVideoDecodeOption()

        MPVLib.mpvSetOptionString("vo", "gpu")
        MPVLib.mpvSetOptionString("gpu-context", "android")
        MPVLib.mpvSetOptionString("hwdec", hwdec)
        MPVLib.mpvSetOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.mpvSetOptionString("ao", "audiotrack,opensles")

        val mpvCertFile = File(appConstants.mpvCertDir, AppConstants.MPV_CERTIFICATE_FILE_NAME)
        MPVLib.mpvSetOptionString("tls-verify", "yes")
        MPVLib.mpvSetOptionString("tls-ca-file", mpvCertFile.path)

        MPVLib.mpvSetOptionString("input-default-bindings", "yes")

        Logger.d(TAG, "initOptions() mpvDemuxerCacheMaxSize: ${ChanPostUtils.getReadableFileSize(appConstants.mpvDemuxerCacheMaxSize)}")
        MPVLib.mpvSetOptionString("demuxer-max-bytes", "${appConstants.mpvDemuxerCacheMaxSize}")
        MPVLib.mpvSetOptionString("demuxer-max-back-bytes", "${appConstants.mpvDemuxerCacheMaxSize}")

        MPVLib.mpvInit()
        // certain options are hardcoded:
        MPVLib.mpvSetOptionString("save-position-on-quit", "no")
        MPVLib.mpvSetOptionString("force-window", "no")
        muteUnmute(true)

        surfaceTextureListener = this
        observeProperties()

        _initialized = true
    }

    private fun setupMpvConf(applicationContext: Context) {
        if (!ChanSettings.mpvUseConfigFile.get()) {
            MPVLib.mpvSetPropertyString("config", "no")
            return
        }

        val mpvconfDir = File(applicationContext.filesDir, MPV_CONF_DIR)
        val mpvconfFile = File(mpvconfDir, MPV_CONF_FILE)

        if (!mpvconfFile.exists() || mpvconfFile.length() <= 0) {
            Logger.d(TAG, "initOptions() mpv.conf doesn't exist or empty")

            MPVLib.mpvSetPropertyString("config", "no")
        } else {
            Logger.d(TAG, "initOptions() using mpv.conf")

            MPVLib.mpvSetPropertyString("config", "yes")
            MPVLib.mpvSetPropertyString("config-dir", mpvconfDir.absolutePath)
        }
    }

    fun destroy() {
        if (!MPVLib.librariesAreLoaded()) {
            Logger.d(TAG, "destroy() librariesAreLoaded: false")
            _initialized = false
            return
        }

        if (!MPVLib.isCreated()) {
            return
        }

        Logger.d(TAG, "destroy()")

        this.filePath = null

        // Disable surface callbacks to avoid using unintialized mpv state
        surfaceTextureListener = null
        MPVLib.mpvDestroy()

        _initialized = false
    }

    fun reloadFastVideoDecodeOption() {
        if (!MPVLib.librariesAreLoaded()) {
            Logger.d(TAG, "reloadFastVideoDecodeOption() librariesAreLoaded: false")
            return
        }

        if (MpvSettings.videoFastCode.get()) {
            Logger.d(TAG, "initOptions() videoFastCode: true")

            MPVLib.mpvSetOptionString("vd-lavc-fast", "yes")
            MPVLib.mpvSetOptionString("vd-lavc-skiploopfilter", "nonkey")
        } else {
            Logger.d(TAG, "initOptions() videoFastCode: false")

            MPVLib.mpvSetOptionString("vd-lavc-fast", "null")
            MPVLib.mpvSetOptionString("vd-lavc-skiploopfilter", "null")
        }
    }

    fun playFile(filePath: String) {
        if (!MPVLib.librariesAreLoaded()) {
            Logger.d(TAG, "playFile() librariesAreLoaded: false")
            return
        }

        if (!surfaceAttached) {
            this.filePath = filePath
        } else {
            this.filePath = null
            MPVLib.mpvCommand(arrayOf("loadfile", filePath))
        }

        if (ChanSettings.videoAutoLoop.get()) {
            MPVLib.mpvSetOptionString("loop-file", "inf")
        } else {
            MPVLib.mpvSetOptionString("loop-file", "no")
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
        get() = MPVLib.mpvGetPropertyBoolean("pause")
        set(paused) = MPVLib.mpvSetPropertyBoolean("pause", paused!!)

    val duration: Int?
        get() = MPVLib.mpvGetPropertyInt("duration")

    val demuxerCacheDuration: Int?
        get() = MPVLib.mpvGetPropertyInt("demuxer-cache-duration")

    var timePos: Int?
        get() = MPVLib.mpvGetPropertyInt("time-pos")
        set(progress) = MPVLib.mpvSetPropertyInt("time-pos", progress!!)

    val hwdecActive: Boolean
        get() = (MPVLib.mpvGetPropertyString("hwdec-current") ?: "no") != "no"

    var playbackSpeed: Double?
        get() = MPVLib.mpvGetPropertyDouble("speed")
        set(speed) = MPVLib.mpvSetPropertyDouble("speed", speed!!)

    val filename: String?
        get() = MPVLib.mpvGetPropertyString("filename")

    val avsync: String?
        get() = MPVLib.mpvGetPropertyString("avsync")

    val decoderFrameDropCount: Int?
        get() = MPVLib.mpvGetPropertyInt("decoder-frame-drop-count")

    val frameDropCount: Int?
        get() = MPVLib.mpvGetPropertyInt("frame-drop-count")

    val containerFps: Double?
        get() = MPVLib.mpvGetPropertyDouble("container-fps")

    val estimatedVfFps: Double?
        get() = MPVLib.mpvGetPropertyDouble("estimated-vf-fps")

    val videoW: Int?
        get() = MPVLib.mpvGetPropertyInt("video-params/w")

    val videoH: Int?
        get() = MPVLib.mpvGetPropertyInt("video-params/h")

    val videoAspect: Double?
        get() = MPVLib.mpvGetPropertyDouble("video-params/aspect")

    val videoCodec: String?
        get() = MPVLib.mpvGetPropertyString("video-codec")

    val audioCodec: String?
        get() = MPVLib.mpvGetPropertyString("audio-codec")

    val audioSampleRate: Int?
        get() = MPVLib.mpvGetPropertyInt("audio-params/samplerate")

    val audioChannels: Int?
        get() = MPVLib.mpvGetPropertyInt("audio-params/channel-count")

    class TrackDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MPVLib.mpvGetPropertyString(property.name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1)
                MPVLib.mpvSetPropertyString(property.name, "no")
            else
                MPVLib.mpvSetPropertyInt(property.name, value)
        }
    }

    var vid: Int by TrackDelegate()
    var sid: Int by TrackDelegate()
    var aid: Int by TrackDelegate()

    // Commands

    fun cyclePause() = MPVLib.mpvCommand(arrayOf("cycle", "pause"))

    val isMuted: Boolean
        get() = MPVLib.mpvGetPropertyString("mute") != "no"

    fun muteUnmute(mute: Boolean) {
        if (mute) {
            MPVLib.mpvSetPropertyString("mute", "yes")
        } else {
            MPVLib.mpvSetPropertyString("mute", "no")
        }
    }

    fun cycleHwdec() = MPVLib.mpvCommand(arrayOf("cycle-values", "hwdec", "mediacodec-copy", "no"))

    fun cycleSpeed() {
        val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val currentSpeed = playbackSpeed ?: 1.0
        val index = speeds.indexOfFirst { it > currentSpeed }
        playbackSpeed = speeds[if (index == -1) 0 else index]
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Logger.d(TAG, "attaching surface")

        MPVLib.mpvAttachSurface(Surface(surfaceTexture))
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.mpvSetOptionString("force-window", "yes")

        if (filePath != null) {
            MPVLib.mpvCommand(arrayOf("loadfile", filePath as String))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            MPVLib.mpvSetPropertyString("vo", "gpu")
        }

        surfaceAttached = true
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        Logger.d(TAG, "detaching surface")

        MPVLib.mpvSetPropertyString("vo", "null")
        MPVLib.mpvSetOptionString("force-window", "no")
        MPVLib.mpvDetachSurface()
        surfaceAttached = false

        return true
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        MPVLib.mpvSetPropertyString("android-surface-size", "${width}x$height")
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
    }

    companion object {
        private const val TAG = "MPVView"

        const val MPV_CONF_DIR = "mpvconf"
        const val MPV_CONF_FILE = "mpv.conf"
    }
}
