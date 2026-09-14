package com.github.k1rakishou.chan.features.view.media.element

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.features.view.media.MediaViewerToolbar
import com.github.k1rakishou.chan.features.view.media.soundpost.SoundPostPlayer
import com.github.k1rakishou.chan.utils.AnimationUtils.fadeIn
import com.github.k1rakishou.chan.utils.AnimationUtils.fadeOut
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.chan.utils.setEnabledFast
import kotlinx.coroutines.launch

class AudioPlayerView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle) {
  private val audioPlayerMuteUnmute: ImageButton
  private val audioPlayerPlayPause: ImageButton
  private val audioPlayerRestart: ImageButton
  private val audioPlayerControlsRoot: LinearLayout
  private val audioPlayerPositionDuration: TextView

  private val scope = KurobaCoroutineScope()

  private var soundPostPlayer: SoundPostPlayer? = null
  private var hideShowAnimation: ValueAnimator? = null
  private var systemUiHidden = false
  private var hasSomethingToShow = false
  private var lastRequestedVisibility = false
  private var statusOnly = false

  init {
    inflate(context, R.layout.audio_player_control_view, this)

    audioPlayerControlsRoot = findViewById(R.id.audio_player_controls_view_root)
    audioPlayerMuteUnmute = findViewById(R.id.audio_player_mute_unmute)
    audioPlayerPlayPause = findViewById(R.id.audio_player_play_pause)
    audioPlayerRestart = findViewById(R.id.audio_player_restart)
    audioPlayerPositionDuration = findViewById(R.id.audio_player_position_duration)
  }

  fun bind(soundPostPlayer: SoundPostPlayer, systemUiHidden: Boolean, statusOnly: Boolean) {
    unbind()

    this.soundPostPlayer = soundPostPlayer
    this.systemUiHidden = systemUiHidden
    this.statusOnly = statusOnly

    val buttonsVisibility = if (statusOnly) GONE else VISIBLE
    audioPlayerMuteUnmute.visibility = buttonsVisibility
    audioPlayerPlayPause.visibility = buttonsVisibility
    audioPlayerRestart.visibility = buttonsVisibility

    if (statusOnly) {
      audioPlayerControlsRoot.setOnClickListener { this.soundPostPlayer?.onLoadClicked() }
    } else {
      audioPlayerMuteUnmute.setOnClickListener { this.soundPostPlayer?.onMuteUnmuteClicked() }
      audioPlayerPlayPause.setOnClickListener { this.soundPostPlayer?.onPlayPauseClicked() }
      audioPlayerRestart.setOnClickListener { this.soundPostPlayer?.onRestartClicked() }
    }

    scope.launch {
      soundPostPlayer.uiState.collect { uiState -> onUiStateChanged(uiState) }
    }
  }

  fun unbind() {
    scope.cancelChildren()

    soundPostPlayer = null
    hasSomethingToShow = false
    lastRequestedVisibility = false

    audioPlayerMuteUnmute.setOnClickListener(null)
    audioPlayerPlayPause.setOnClickListener(null)
    audioPlayerRestart.setOnClickListener(null)
    audioPlayerControlsRoot.setOnClickListener(null)
    audioPlayerControlsRoot.isClickable = false

    hideShowAnimation?.end()
    hideShowAnimation = null
    audioPlayerControlsRoot.visibility = GONE
  }

  fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    this.systemUiHidden = systemUIHidden
    updateVisibility()
  }

  private fun onUiStateChanged(uiState: SoundPostPlayer.UiState) {
    hasSomethingToShow = when (uiState) {
      SoundPostPlayer.UiState.Hidden -> false
      // The media's own controls take over once the sound is ready
      is SoundPostPlayer.UiState.Ready -> !statusOnly
      SoundPostPlayer.UiState.NotLoaded,
      SoundPostPlayer.UiState.Error,
      is SoundPostPlayer.UiState.Loading -> true
    }

    updateVisibility()

    when (uiState) {
      SoundPostPlayer.UiState.Hidden -> {
        // no-op
      }
      SoundPostPlayer.UiState.NotLoaded -> {
        audioPlayerPositionDuration.text = if (statusOnly) {
          getString(R.string.sound_post_not_loaded_tap_to_load)
        } else {
          getString(R.string.sound_post_not_loaded)
        }

        audioPlayerControlsRoot.isClickable = statusOnly
        updateButtons(playPauseEnabled = true, otherButtonsEnabled = false)
        updatePlayIcon(isPlaying = false)
      }
      SoundPostPlayer.UiState.Error -> {
        audioPlayerPositionDuration.text = if (statusOnly) {
          getString(R.string.sound_post_failed_to_load_tap_to_retry)
        } else {
          getString(R.string.sound_post_failed_to_load)
        }

        audioPlayerControlsRoot.isClickable = statusOnly
        updateButtons(playPauseEnabled = true, otherButtonsEnabled = false)
        updatePlayIcon(isPlaying = false)
      }
      is SoundPostPlayer.UiState.Loading -> {
        val progress = uiState.progress

        audioPlayerPositionDuration.text = if (progress == null) {
          getString(R.string.sound_post_loading)
        } else {
          getString(R.string.sound_post_loading_progress, (progress * 100f).toInt())
        }

        audioPlayerControlsRoot.isClickable = false
        updateButtons(playPauseEnabled = false, otherButtonsEnabled = false)
      }
      is SoundPostPlayer.UiState.Ready -> {
        if (statusOnly) {
          return
        }

        val positionFormatted = TimeUtils.formatPeriod(uiState.positionMs)
        val durationFormatted = TimeUtils.formatPeriod(uiState.durationMs)

        audioPlayerPositionDuration.text = "${positionFormatted} / ${durationFormatted}"
        updateButtons(playPauseEnabled = true, otherButtonsEnabled = true)
        updatePlayIcon(isPlaying = uiState.isPlaying)
        updateAudioIcon(isMuted = uiState.isMuted)
      }
    }
  }

  private fun updateButtons(playPauseEnabled: Boolean, otherButtonsEnabled: Boolean) {
    audioPlayerPlayPause.setEnabledFast(playPauseEnabled)
    audioPlayerMuteUnmute.setEnabledFast(otherButtonsEnabled)
    audioPlayerRestart.setEnabledFast(otherButtonsEnabled)
  }

  private fun updateVisibility() {
    val shouldBeVisible = hasSomethingToShow && !systemUiHidden
    if (shouldBeVisible == lastRequestedVisibility) {
      // Don't restart the animation on every UI state update
      return
    }

    lastRequestedVisibility = shouldBeVisible

    hideShowAnimation = if (shouldBeVisible) {
      audioPlayerControlsRoot.fadeIn(
        duration = MediaViewerToolbar.ANIMATION_DURATION_MS,
        animator = hideShowAnimation,
        onEnd = { hideShowAnimation = null }
      )
    } else {
      audioPlayerControlsRoot.fadeOut(
        duration = MediaViewerToolbar.ANIMATION_DURATION_MS,
        animator = hideShowAnimation,
        onEnd = { hideShowAnimation = null }
      )
    }
  }

  private fun updateAudioIcon(isMuted: Boolean) {
    val imageDrawable = if (isMuted) {
      R.drawable.ic_volume_off_white_24dp
    } else {
      R.drawable.ic_volume_up_white_24dp
    }

    audioPlayerMuteUnmute.setImageResource(imageDrawable)
  }

  private fun updatePlayIcon(isPlaying: Boolean) {
    val imageDrawable = if (isPlaying) {
      com.google.android.exoplayer2.ui.R.drawable.exo_controls_pause
    } else {
      com.google.android.exoplayer2.ui.R.drawable.exo_controls_play
    }

    audioPlayerPlayPause.setImageResource(imageDrawable)
  }

}
