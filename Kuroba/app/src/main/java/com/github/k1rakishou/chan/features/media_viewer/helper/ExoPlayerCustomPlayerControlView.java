package com.github.k1rakishou.chan.features.media_viewer.helper;

import static com.google.android.exoplayer2.Player.COMMAND_SEEK_BACK;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_FORWARD;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_NEXT;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_PREVIOUS;
import static com.google.android.exoplayer2.Player.EVENT_AVAILABLE_COMMANDS_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_IS_PLAYING_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_PLAY_WHEN_READY_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_POSITION_DISCONTINUITY;
import static com.google.android.exoplayer2.Player.EVENT_REPEAT_MODE_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_TIMELINE_CHANGED;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerToolbar;
import com.github.k1rakishou.chan.utils.AnimationUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ui.DefaultTimeBar;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.RepeatModeUtil;
import com.google.android.exoplayer2.util.Util;

import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import kotlin.Unit;

public class ExoPlayerCustomPlayerControlView extends FrameLayout {

    static {
        ExoPlayerLibraryInfo.registerModule("goog.exo.ui");
    }

    /** Listener to be notified about changes of the visibility of the UI control. */
    public interface VisibilityListener {

        /**
         * Called when the visibility changes.
         *
         * @param visibility The new visibility. Either {@link View#VISIBLE} or {@link View#GONE}.
         */
        void onVisibilityChange(int visibility);
    }

    /** Listener to be notified when progress has been updated. */
    public interface ProgressUpdateListener {

        /**
         * Called when progress needs to be updated.
         *
         * @param position The current position.
         * @param bufferedPosition The current buffered position.
         */
        void onProgressUpdate(long position, long bufferedPosition);
    }

    /** The default show timeout, in milliseconds. */
    public static final int DEFAULT_SHOW_TIMEOUT_MS = 5000;
    /** The default repeat toggle modes. */
    public static final @RepeatModeUtil.RepeatToggleModes int DEFAULT_REPEAT_TOGGLE_MODES =
            RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE;
    /** The default minimum interval between time bar position updates. */
    public static final int DEFAULT_TIME_BAR_MIN_UPDATE_INTERVAL_MS = 200;
    /** The maximum number of windows that can be shown in a multi-window time bar. */
    public static final int MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR = 100;

    /** The maximum interval between time bar position updates. */
    private static final int MAX_UPDATE_INTERVAL_MS = 1000;

    private ValueAnimator hideShowAnimation = null;
    private VideoMediaViewCallbacks videoMediaViewCallbacks = null;

    private final ComponentListener componentListener;
    private final CopyOnWriteArrayList<ExoPlayerCustomPlayerControlView.VisibilityListener> visibilityListeners;
    @Nullable
    private final View previousButton;
    @Nullable private final View nextButton;
    @Nullable private final View playButton;
    @Nullable private final View pauseButton;
    @Nullable private final View fastForwardButton;
    @Nullable private final View rewindButton;
    @Nullable private final ImageView repeatToggleButton;
    @Nullable private final ImageView shuffleButton;
    @Nullable private final View vrButton;
    @Nullable private final TextView durationView;
    @Nullable private final TextView positionView;
    @Nullable private final TimeBar timeBar;
    private final StringBuilder formatBuilder;
    private final Formatter formatter;
    private final Timeline.Period period;
    private final Timeline.Window window;
    private final Runnable updateProgressAction;
    private final Runnable hideAction;

    private final Drawable repeatOffButtonDrawable;
    private final Drawable repeatOneButtonDrawable;
    private final Drawable repeatAllButtonDrawable;
    private final String repeatOffButtonContentDescription;
    private final String repeatOneButtonContentDescription;
    private final String repeatAllButtonContentDescription;
    private final Drawable shuffleOnButtonDrawable;
    private final Drawable shuffleOffButtonDrawable;
    private final float buttonAlphaEnabled;
    private final float buttonAlphaDisabled;
    private final String shuffleOnContentDescription;
    private final String shuffleOffContentDescription;

    @Nullable private Player player;
    @Nullable private ExoPlayerCustomPlayerControlView.ProgressUpdateListener progressUpdateListener;

    private boolean isAttachedToWindow;
    private boolean showMultiWindowTimeBar;
    private boolean multiWindowTimeBar;
    private boolean scrubbing;
    private int showTimeoutMs;
    private int timeBarMinUpdateIntervalMs;
    private @RepeatModeUtil.RepeatToggleModes int repeatToggleModes;
    private boolean showRewindButton;
    private boolean showFastForwardButton;
    private boolean showPreviousButton;
    private boolean showNextButton;
    private boolean showShuffleButton;
    private long hideAtMs;
    private long[] adGroupTimesMs;
    private boolean[] playedAdGroups;
    private long[] extraAdGroupTimesMs;
    private boolean[] extraPlayedAdGroups;
    private long currentWindowOffset;
    private long currentPosition;
    private long currentBufferedPosition;

    public ExoPlayerCustomPlayerControlView(Context context) {
        this(context, /* attrs= */ null);
    }

    public ExoPlayerCustomPlayerControlView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public ExoPlayerCustomPlayerControlView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, attrs);
    }

    @SuppressWarnings({
            "nullness:argument",
            "nullness:method.invocation",
            "nullness:methodref.receiver.bound"
    })
    public ExoPlayerCustomPlayerControlView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            @Nullable AttributeSet playbackAttrs) {
        super(context, attrs, defStyleAttr);
        showTimeoutMs = DEFAULT_SHOW_TIMEOUT_MS;
        repeatToggleModes = DEFAULT_REPEAT_TOGGLE_MODES;
        timeBarMinUpdateIntervalMs = DEFAULT_TIME_BAR_MIN_UPDATE_INTERVAL_MS;
        hideAtMs = C.TIME_UNSET;
        showRewindButton = true;
        showFastForwardButton = true;
        showPreviousButton = true;
        showNextButton = true;
        showShuffleButton = false;
        visibilityListeners = new CopyOnWriteArrayList<>();
        period = new Timeline.Period();
        window = new Timeline.Window();
        formatBuilder = new StringBuilder();
        formatter = new Formatter(formatBuilder, Locale.getDefault());
        adGroupTimesMs = new long[0];
        playedAdGroups = new boolean[0];
        extraAdGroupTimesMs = new long[0];
        extraPlayedAdGroups = new boolean[0];
        componentListener = new ExoPlayerCustomPlayerControlView.ComponentListener();
        updateProgressAction = this::updateProgress;
        hideAction = this::hide;

        LayoutInflater.from(context).inflate(R.layout.exo_player_control_view, /* root= */ this);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);

        TimeBar customTimeBar = findViewById(com.google.android.exoplayer2.ui.R.id.exo_progress);
        View timeBarPlaceholder = findViewById(com.google.android.exoplayer2.ui.R.id.exo_progress_placeholder);
        if (customTimeBar != null) {
            timeBar = customTimeBar;
        } else if (timeBarPlaceholder != null) {
            // Propagate playbackAttrs as timebarAttrs so that DefaultTimeBar's custom attributes are
            // transferred, but standard attributes (e.g. background) are not.
            DefaultTimeBar defaultTimeBar = new DefaultTimeBar(context, null, 0, playbackAttrs);
            defaultTimeBar.setId(com.google.android.exoplayer2.ui.R.id.exo_progress);
            defaultTimeBar.setLayoutParams(timeBarPlaceholder.getLayoutParams());
            ViewGroup parent = ((ViewGroup) timeBarPlaceholder.getParent());
            int timeBarIndex = parent.indexOfChild(timeBarPlaceholder);
            parent.removeView(timeBarPlaceholder);
            parent.addView(defaultTimeBar, timeBarIndex);
            timeBar = defaultTimeBar;
        } else {
            timeBar = null;
        }
        durationView = findViewById(com.google.android.exoplayer2.ui.R.id.exo_duration);
        positionView = findViewById(com.google.android.exoplayer2.ui.R.id.exo_position);

        if (timeBar != null) {
            timeBar.addListener(componentListener);
        }
        playButton = findViewById(com.google.android.exoplayer2.ui.R.id.exo_play);
        if (playButton != null) {
            playButton.setOnClickListener(componentListener);
        }
        pauseButton = findViewById(com.google.android.exoplayer2.ui.R.id.exo_pause);
        if (pauseButton != null) {
            pauseButton.setOnClickListener(componentListener);
        }
        previousButton = findViewById(com.google.android.exoplayer2.ui.R.id.exo_prev);
        if (previousButton != null) {
            previousButton.setOnClickListener(componentListener);
        }
        nextButton = findViewById(com.google.android.exoplayer2.ui.R.id.exo_next);
        if (nextButton != null) {
            nextButton.setOnClickListener(componentListener);
        }
        rewindButton = findViewById(com.google.android.exoplayer2.ui.R.id.exo_rew);
        if (rewindButton != null) {
            rewindButton.setOnClickListener(componentListener);
        }
        fastForwardButton = findViewById(com.google.android.exoplayer2.ui.R.id.exo_ffwd);
        if (fastForwardButton != null) {
            fastForwardButton.setOnClickListener(componentListener);
        }
        repeatToggleButton = findViewById(com.google.android.exoplayer2.ui.R.id.exo_repeat_toggle);
        if (repeatToggleButton != null) {
            repeatToggleButton.setOnClickListener(componentListener);
        }
        shuffleButton = findViewById(com.google.android.exoplayer2.ui.R.id.exo_shuffle);
        if (shuffleButton != null) {
            shuffleButton.setOnClickListener(componentListener);
        }
        vrButton = findViewById(com.google.android.exoplayer2.ui.R.id.exo_vr);
        setShowVrButton(false);
        updateButton(false, false, vrButton);

        Resources resources = context.getResources();

        buttonAlphaEnabled = (float) resources.getInteger(com.google.android.exoplayer2.ui.R.integer.exo_media_button_opacity_percentage_enabled) / 100;
        buttonAlphaDisabled = (float) resources.getInteger(com.google.android.exoplayer2.ui.R.integer.exo_media_button_opacity_percentage_disabled) / 100;
        repeatOffButtonDrawable = resources.getDrawable(com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_off);
        repeatOneButtonDrawable = resources.getDrawable(com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_one);
        repeatAllButtonDrawable = resources.getDrawable(com.google.android.exoplayer2.ui.R.drawable.exo_controls_repeat_all);
        shuffleOnButtonDrawable = resources.getDrawable(com.google.android.exoplayer2.ui.R.drawable.exo_controls_shuffle_on);
        shuffleOffButtonDrawable = resources.getDrawable(com.google.android.exoplayer2.ui.R.drawable.exo_controls_shuffle_off);
        repeatOffButtonContentDescription = resources.getString(com.google.android.exoplayer2.ui.R.string.exo_controls_repeat_off_description);
        repeatOneButtonContentDescription = resources.getString(com.google.android.exoplayer2.ui.R.string.exo_controls_repeat_one_description);
        repeatAllButtonContentDescription = resources.getString(com.google.android.exoplayer2.ui.R.string.exo_controls_repeat_all_description);
        shuffleOnContentDescription = resources.getString(com.google.android.exoplayer2.ui.R.string.exo_controls_shuffle_on_description);
        shuffleOffContentDescription = resources.getString(com.google.android.exoplayer2.ui.R.string.exo_controls_shuffle_off_description);

        currentPosition = C.TIME_UNSET;
        currentBufferedPosition = C.TIME_UNSET;
    }

    public void setVideoMediaViewCallbacks(VideoMediaViewCallbacks videoMediaViewCallbacks) {
        this.videoMediaViewCallbacks = videoMediaViewCallbacks;
    }

    /**
     * Returns the {@link Player} currently being controlled by this view, or null if no player is
     * set.
     */
    @Nullable
    public Player getPlayer() {
        return player;
    }

    /**
     * Sets the {@link Player} to control.
     *
     * @param player The {@link Player} to control, or {@code null} to detach the current player. Only
     *     players which are accessed on the main thread are supported ({@code
     *     player.getApplicationLooper() == Looper.getMainLooper()}).
     */
    public void setPlayer(@Nullable Player player) {
        Assertions.checkState(Looper.myLooper() == Looper.getMainLooper());
        Assertions.checkArgument(
                player == null || player.getApplicationLooper() == Looper.getMainLooper());
        if (this.player == player) {
            return;
        }
        if (this.player != null) {
            this.player.removeListener(componentListener);
        }
        this.player = player;
        if (player != null) {
            player.addListener(componentListener);
        }
        updateAll();
    }

    /**
     * Sets whether the time bar should show all windows, as opposed to just the current one. If the
     * timeline has a period with unknown duration or more than {@link
     * #MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR} windows the time bar will fall back to showing a single
     * window.
     *
     * @param showMultiWindowTimeBar Whether the time bar should show all windows.
     */
    public void setShowMultiWindowTimeBar(boolean showMultiWindowTimeBar) {
        this.showMultiWindowTimeBar = showMultiWindowTimeBar;
        updateTimeline();
    }

    /**
     * Sets the millisecond positions of extra ad markers relative to the start of the window (or
     * timeline, if in multi-window mode) and whether each extra ad has been played or not. The
     * markers are shown in addition to any ad markers for ads in the player's timeline.
     *
     * @param extraAdGroupTimesMs The millisecond timestamps of the extra ad markers to show, or
     *     {@code null} to show no extra ad markers.
     * @param extraPlayedAdGroups Whether each ad has been played. Must be the same length as {@code
     *     extraAdGroupTimesMs}, or {@code null} if {@code extraAdGroupTimesMs} is {@code null}.
     */
    public void setExtraAdGroupMarkers(
            @Nullable long[] extraAdGroupTimesMs, @Nullable boolean[] extraPlayedAdGroups) {
        if (extraAdGroupTimesMs == null) {
            this.extraAdGroupTimesMs = new long[0];
            this.extraPlayedAdGroups = new boolean[0];
        } else {
            extraPlayedAdGroups = Assertions.checkNotNull(extraPlayedAdGroups);
            Assertions.checkArgument(extraAdGroupTimesMs.length == extraPlayedAdGroups.length);
            this.extraAdGroupTimesMs = extraAdGroupTimesMs;
            this.extraPlayedAdGroups = extraPlayedAdGroups;
        }
        updateTimeline();
    }

    /**
     * Adds a {@link ExoPlayerCustomPlayerControlView.VisibilityListener}.
     *
     * @param listener The listener to be notified about visibility changes.
     */
    public void addVisibilityListener(ExoPlayerCustomPlayerControlView.VisibilityListener listener) {
        Assertions.checkNotNull(listener);
        visibilityListeners.add(listener);
    }

    /**
     * Removes a {@link ExoPlayerCustomPlayerControlView.VisibilityListener}.
     *
     * @param listener The listener to be removed.
     */
    public void removeVisibilityListener(ExoPlayerCustomPlayerControlView.VisibilityListener listener) {
        visibilityListeners.remove(listener);
    }

    /**
     * Sets the {@link ExoPlayerCustomPlayerControlView.ProgressUpdateListener}.
     *
     * @param listener The listener to be notified about when progress is updated.
     */
    public void setProgressUpdateListener(@Nullable ExoPlayerCustomPlayerControlView.ProgressUpdateListener listener) {
        this.progressUpdateListener = listener;
    }

    /**
     * Sets whether the rewind button is shown.
     *
     * @param showRewindButton Whether the rewind button is shown.
     */
    public void setShowRewindButton(boolean showRewindButton) {
        this.showRewindButton = showRewindButton;
        updateNavigation();
    }

    /**
     * Sets whether the fast forward button is shown.
     *
     * @param showFastForwardButton Whether the fast forward button is shown.
     */
    public void setShowFastForwardButton(boolean showFastForwardButton) {
        this.showFastForwardButton = showFastForwardButton;
        updateNavigation();
    }

    /**
     * Sets whether the previous button is shown.
     *
     * @param showPreviousButton Whether the previous button is shown.
     */
    public void setShowPreviousButton(boolean showPreviousButton) {
        this.showPreviousButton = showPreviousButton;
        updateNavigation();
    }

    /**
     * Sets whether the next button is shown.
     *
     * @param showNextButton Whether the next button is shown.
     */
    public void setShowNextButton(boolean showNextButton) {
        this.showNextButton = showNextButton;
        updateNavigation();
    }

    /**
     * Returns the playback controls timeout. The playback controls are automatically hidden after
     * this duration of time has elapsed without user input.
     *
     * @return The duration in milliseconds. A non-positive value indicates that the controls will
     *     remain visible indefinitely.
     */
    public int getShowTimeoutMs() {
        return showTimeoutMs;
    }

    /**
     * Sets the playback controls timeout. The playback controls are automatically hidden after this
     * duration of time has elapsed without user input.
     *
     * @param showTimeoutMs The duration in milliseconds. A non-positive value will cause the controls
     *     to remain visible indefinitely.
     */
    public void setShowTimeoutMs(int showTimeoutMs) {
        this.showTimeoutMs = showTimeoutMs;
        if (isVisible()) {
            // Reset the timeout.
            hideAfterTimeout();
        }
    }

    /**
     * Returns which repeat toggle modes are enabled.
     *
     * @return The currently enabled {@link RepeatModeUtil.RepeatToggleModes}.
     */
    public @RepeatModeUtil.RepeatToggleModes int getRepeatToggleModes() {
        return repeatToggleModes;
    }

    /**
     * Sets which repeat toggle modes are enabled.
     *
     * @param repeatToggleModes A set of {@link RepeatModeUtil.RepeatToggleModes}.
     */
    public void setRepeatToggleModes(@RepeatModeUtil.RepeatToggleModes int repeatToggleModes) {
        this.repeatToggleModes = repeatToggleModes;
        if (player != null) {
            @Player.RepeatMode int currentMode = player.getRepeatMode();
            if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE
                    && currentMode != Player.REPEAT_MODE_OFF) {
                player.setRepeatMode(Player.REPEAT_MODE_OFF);
            } else if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE
                    && currentMode == Player.REPEAT_MODE_ALL) {
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
            } else if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL
                    && currentMode == Player.REPEAT_MODE_ONE) {
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
            }
        }
        updateRepeatModeButton();
    }

    /** Returns whether the shuffle button is shown. */
    public boolean getShowShuffleButton() {
        return showShuffleButton;
    }

    /**
     * Sets whether the shuffle button is shown.
     *
     * @param showShuffleButton Whether the shuffle button is shown.
     */
    public void setShowShuffleButton(boolean showShuffleButton) {
        this.showShuffleButton = showShuffleButton;
        updateShuffleButton();
    }

    /** Returns whether the VR button is shown. */
    public boolean getShowVrButton() {
        return vrButton != null && vrButton.getVisibility() == VISIBLE;
    }

    /**
     * Sets whether the VR button is shown.
     *
     * @param showVrButton Whether the VR button is shown.
     */
    public void setShowVrButton(boolean showVrButton) {
        if (vrButton != null) {
            vrButton.setVisibility(showVrButton ? VISIBLE : GONE);
        }
    }

    /**
     * Sets listener for the VR button.
     *
     * @param onClickListener Listener for the VR button, or null to clear the listener.
     */
    public void setVrButtonListener(@Nullable OnClickListener onClickListener) {
        if (vrButton != null) {
            vrButton.setOnClickListener(onClickListener);
            updateButton(getShowVrButton(), onClickListener != null, vrButton);
        }
    }

    /**
     * Sets the minimum interval between time bar position updates.
     *
     * <p>Note that smaller intervals, e.g. 33ms, will result in a smooth movement but will use more
     * CPU resources while the time bar is visible, whereas larger intervals, e.g. 200ms, will result
     * in a step-wise update with less CPU usage.
     *
     * @param minUpdateIntervalMs The minimum interval between time bar position updates, in
     *     milliseconds.
     */
    public void setTimeBarMinUpdateInterval(int minUpdateIntervalMs) {
        // Do not accept values below 16ms (60fps) and larger than the maximum update interval.
        timeBarMinUpdateIntervalMs =
                Util.constrainValue(minUpdateIntervalMs, 16, MAX_UPDATE_INTERVAL_MS);
    }

    /**
     * Shows the playback controls. If {@link #getShowTimeoutMs()} is positive then the controls will
     * be automatically hidden after this duration of time has elapsed without user input.
     */
    public void show() {
        if (videoMediaViewCallbacks.isSystemUiHidden()) {
            return;
        }

        hideShowAnimation = AnimationUtils.fadeIn(
                this,
                MediaViewerToolbar.ANIMATION_DURATION_MS,
                hideShowAnimation,
                () -> {
                    updateAll();
                    return Unit.INSTANCE;
                }
        );

        for (ExoPlayerCustomPlayerControlView.VisibilityListener visibilityListener : visibilityListeners) {
            visibilityListener.onVisibilityChange(getVisibility());
        }
        updateAll();
        requestPlayPauseFocus();
        requestPlayPauseAccessibilityFocus();

        // Call hideAfterTimeout even if already visible to reset the timeout.
        hideAfterTimeout();
    }

    /**
     * Hides the controller.
     */
    public void hide() {
        hideShowAnimation = AnimationUtils.fadeOut(
                this,
                MediaViewerToolbar.ANIMATION_DURATION_MS,
                hideShowAnimation,
                null
        );

        for (ExoPlayerCustomPlayerControlView.VisibilityListener visibilityListener : visibilityListeners) {
            visibilityListener.onVisibilityChange(getVisibility());
        }
        removeCallbacks(updateProgressAction);
        removeCallbacks(hideAction);
        hideAtMs = C.TIME_UNSET;
    }

    /** Returns whether the controller is currently visible. */
    public boolean isVisible() {
        return getVisibility() == VISIBLE;
    }

    private void hideAfterTimeout() {
        removeCallbacks(hideAction);
        if (showTimeoutMs > 0) {
            hideAtMs = SystemClock.uptimeMillis() + showTimeoutMs;
            if (isAttachedToWindow) {
                postDelayed(hideAction, showTimeoutMs);
            }
        } else {
            hideAtMs = C.TIME_UNSET;
        }
    }

    private void updateAll() {
        updatePlayPauseButton();
        updateNavigation();
        updateRepeatModeButton();
        updateShuffleButton();
        updateTimeline();
    }

    private void updatePlayPauseButton() {
        if (!isVisible() || !isAttachedToWindow) {
            return;
        }
        boolean requestPlayPauseFocus = false;
        boolean requestPlayPauseAccessibilityFocus = false;
        boolean shouldShowPauseButton = shouldShowPauseButton();
        if (playButton != null) {
            requestPlayPauseFocus |= shouldShowPauseButton && playButton.isFocused();
            requestPlayPauseAccessibilityFocus |=
                    Util.SDK_INT < 21
                            ? requestPlayPauseFocus
                            : (shouldShowPauseButton && ExoPlayerCustomPlayerControlView.Api21.isAccessibilityFocused(playButton));
            playButton.setVisibility(shouldShowPauseButton ? GONE : VISIBLE);
        }
        if (pauseButton != null) {
            requestPlayPauseFocus |= !shouldShowPauseButton && pauseButton.isFocused();
            requestPlayPauseAccessibilityFocus |=
                    Util.SDK_INT < 21
                            ? requestPlayPauseFocus
                            : (!shouldShowPauseButton && ExoPlayerCustomPlayerControlView.Api21.isAccessibilityFocused(pauseButton));
            pauseButton.setVisibility(shouldShowPauseButton ? VISIBLE : GONE);
        }
        if (requestPlayPauseFocus) {
            requestPlayPauseFocus();
        }
        if (requestPlayPauseAccessibilityFocus) {
            requestPlayPauseAccessibilityFocus();
        }
    }

    private void updateNavigation() {
        if (!isVisible() || !isAttachedToWindow) {
            return;
        }

        @Nullable Player player = this.player;
        boolean enableSeeking = false;
        boolean enablePrevious = false;
        boolean enableRewind = false;
        boolean enableFastForward = false;
        boolean enableNext = false;
        if (player != null) {
            enableSeeking = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
            enablePrevious = player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS);
            enableRewind = player.isCommandAvailable(COMMAND_SEEK_BACK);
            enableFastForward = player.isCommandAvailable(COMMAND_SEEK_FORWARD);
            enableNext = player.isCommandAvailable(COMMAND_SEEK_TO_NEXT);
        }

        updateButton(showPreviousButton, enablePrevious, previousButton);
        updateButton(showRewindButton, enableRewind, rewindButton);
        updateButton(showFastForwardButton, enableFastForward, fastForwardButton);
        updateButton(showNextButton, enableNext, nextButton);
        if (timeBar != null) {
            timeBar.setEnabled(enableSeeking);
        }
    }

    private void updateRepeatModeButton() {
        if (!isVisible() || !isAttachedToWindow || repeatToggleButton == null) {
            return;
        }

        if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE) {
            updateButton(/* visible= */ false, /* enabled= */ false, repeatToggleButton);
            return;
        }

        @Nullable Player player = this.player;
        if (player == null) {
            updateButton(/* visible= */ true, /* enabled= */ false, repeatToggleButton);
            repeatToggleButton.setImageDrawable(repeatOffButtonDrawable);
            repeatToggleButton.setContentDescription(repeatOffButtonContentDescription);
            return;
        }

        updateButton(/* visible= */ true, /* enabled= */ true, repeatToggleButton);
        switch (player.getRepeatMode()) {
            case Player.REPEAT_MODE_OFF:
                repeatToggleButton.setImageDrawable(repeatOffButtonDrawable);
                repeatToggleButton.setContentDescription(repeatOffButtonContentDescription);
                break;
            case Player.REPEAT_MODE_ONE:
                repeatToggleButton.setImageDrawable(repeatOneButtonDrawable);
                repeatToggleButton.setContentDescription(repeatOneButtonContentDescription);
                break;
            case Player.REPEAT_MODE_ALL:
                repeatToggleButton.setImageDrawable(repeatAllButtonDrawable);
                repeatToggleButton.setContentDescription(repeatAllButtonContentDescription);
                break;
            default:
                // Never happens.
        }
        repeatToggleButton.setVisibility(VISIBLE);
    }

    private void updateShuffleButton() {
        if (!isVisible() || !isAttachedToWindow || shuffleButton == null) {
            return;
        }

        @Nullable Player player = this.player;
        if (!showShuffleButton) {
            updateButton(/* visible= */ false, /* enabled= */ false, shuffleButton);
        } else if (player == null) {
            updateButton(/* visible= */ true, /* enabled= */ false, shuffleButton);
            shuffleButton.setImageDrawable(shuffleOffButtonDrawable);
            shuffleButton.setContentDescription(shuffleOffContentDescription);
        } else {
            updateButton(/* visible= */ true, /* enabled= */ true, shuffleButton);
            shuffleButton.setImageDrawable(
                    player.getShuffleModeEnabled() ? shuffleOnButtonDrawable : shuffleOffButtonDrawable);
            shuffleButton.setContentDescription(
                    player.getShuffleModeEnabled()
                            ? shuffleOnContentDescription
                            : shuffleOffContentDescription);
        }
    }

    private void updateTimeline() {
        @Nullable Player player = this.player;
        if (player == null) {
            return;
        }
        multiWindowTimeBar =
                showMultiWindowTimeBar && canShowMultiWindowTimeBar(player.getCurrentTimeline(), window);
        currentWindowOffset = 0;
        long durationUs = 0;
        int adGroupCount = 0;
        Timeline timeline = player.getCurrentTimeline();
        if (!timeline.isEmpty()) {
            int currentWindowIndex = player.getCurrentMediaItemIndex();
            int firstWindowIndex = multiWindowTimeBar ? 0 : currentWindowIndex;
            int lastWindowIndex = multiWindowTimeBar ? timeline.getWindowCount() - 1 : currentWindowIndex;
            for (int i = firstWindowIndex; i <= lastWindowIndex; i++) {
                if (i == currentWindowIndex) {
                    currentWindowOffset = Util.usToMs(durationUs);
                }
                timeline.getWindow(i, window);
                if (window.durationUs == C.TIME_UNSET) {
                    Assertions.checkState(!multiWindowTimeBar);
                    break;
                }
                for (int j = window.firstPeriodIndex; j <= window.lastPeriodIndex; j++) {
                    timeline.getPeriod(j, period);
                    int removedGroups = period.getRemovedAdGroupCount();
                    int totalGroups = period.getAdGroupCount();
                    for (int adGroupIndex = removedGroups; adGroupIndex < totalGroups; adGroupIndex++) {
                        long adGroupTimeInPeriodUs = period.getAdGroupTimeUs(adGroupIndex);
                        if (adGroupTimeInPeriodUs == C.TIME_END_OF_SOURCE) {
                            if (period.durationUs == C.TIME_UNSET) {
                                // Don't show ad markers for postrolls in periods with unknown duration.
                                continue;
                            }
                            adGroupTimeInPeriodUs = period.durationUs;
                        }
                        long adGroupTimeInWindowUs = adGroupTimeInPeriodUs + period.getPositionInWindowUs();
                        if (adGroupTimeInWindowUs >= 0) {
                            if (adGroupCount == adGroupTimesMs.length) {
                                int newLength = adGroupTimesMs.length == 0 ? 1 : adGroupTimesMs.length * 2;
                                adGroupTimesMs = Arrays.copyOf(adGroupTimesMs, newLength);
                                playedAdGroups = Arrays.copyOf(playedAdGroups, newLength);
                            }
                            adGroupTimesMs[adGroupCount] = Util.usToMs(durationUs + adGroupTimeInWindowUs);
                            playedAdGroups[adGroupCount] = period.hasPlayedAdGroup(adGroupIndex);
                            adGroupCount++;
                        }
                    }
                }
                durationUs += window.durationUs;
            }
        }
        long durationMs = Util.usToMs(durationUs);
        if (durationView != null) {
            durationView.setText(Util.getStringForTime(formatBuilder, formatter, durationMs));
        }
        if (timeBar != null) {
            timeBar.setDuration(durationMs);
            int extraAdGroupCount = extraAdGroupTimesMs.length;
            int totalAdGroupCount = adGroupCount + extraAdGroupCount;
            if (totalAdGroupCount > adGroupTimesMs.length) {
                adGroupTimesMs = Arrays.copyOf(adGroupTimesMs, totalAdGroupCount);
                playedAdGroups = Arrays.copyOf(playedAdGroups, totalAdGroupCount);
            }
            System.arraycopy(extraAdGroupTimesMs, 0, adGroupTimesMs, adGroupCount, extraAdGroupCount);
            System.arraycopy(extraPlayedAdGroups, 0, playedAdGroups, adGroupCount, extraAdGroupCount);
            timeBar.setAdGroupTimesMs(adGroupTimesMs, playedAdGroups, totalAdGroupCount);
        }
        updateProgress();
    }

    private void updateProgress() {
        if (!isVisible() || !isAttachedToWindow) {
            return;
        }

        @Nullable Player player = this.player;
        long position = 0;
        long bufferedPosition = 0;
        if (player != null) {
            position = currentWindowOffset + player.getContentPosition();
            bufferedPosition = currentWindowOffset + player.getContentBufferedPosition();
        }
        boolean positionChanged = position != currentPosition;
        boolean bufferedPositionChanged = bufferedPosition != currentBufferedPosition;
        currentPosition = position;
        currentBufferedPosition = bufferedPosition;

        // Only update the TextView if the position has changed, else TalkBack will repeatedly read the
        // same position to the user.
        if (positionView != null && !scrubbing && positionChanged) {
            positionView.setText(Util.getStringForTime(formatBuilder, formatter, position));
        }
        if (timeBar != null) {
            timeBar.setPosition(position);
            timeBar.setBufferedPosition(bufferedPosition);
        }
        if (progressUpdateListener != null && (positionChanged || bufferedPositionChanged)) {
            progressUpdateListener.onProgressUpdate(position, bufferedPosition);
        }

        // Cancel any pending updates and schedule a new one if necessary.
        removeCallbacks(updateProgressAction);
        int playbackState = player == null ? Player.STATE_IDLE : player.getPlaybackState();
        if (player != null && player.isPlaying()) {
            long mediaTimeDelayMs =
                    timeBar != null ? timeBar.getPreferredUpdateDelay() : MAX_UPDATE_INTERVAL_MS;

            // Limit delay to the start of the next full second to ensure position display is smooth.
            long mediaTimeUntilNextFullSecondMs = 1000 - position % 1000;
            mediaTimeDelayMs = Math.min(mediaTimeDelayMs, mediaTimeUntilNextFullSecondMs);

            // Calculate the delay until the next update in real time, taking playback speed into account.
            float playbackSpeed = player.getPlaybackParameters().speed;
            long delayMs =
                    playbackSpeed > 0 ? (long) (mediaTimeDelayMs / playbackSpeed) : MAX_UPDATE_INTERVAL_MS;

            // Constrain the delay to avoid too frequent / infrequent updates.
            delayMs = Util.constrainValue(delayMs, timeBarMinUpdateIntervalMs, MAX_UPDATE_INTERVAL_MS);
            postDelayed(updateProgressAction, delayMs);
        } else if (playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE) {
            postDelayed(updateProgressAction, MAX_UPDATE_INTERVAL_MS);
        }
    }

    private void requestPlayPauseFocus() {
        boolean shouldShowPauseButton = shouldShowPauseButton();
        if (!shouldShowPauseButton && playButton != null) {
            playButton.requestFocus();
        } else if (shouldShowPauseButton && pauseButton != null) {
            pauseButton.requestFocus();
        }
    }

    private void requestPlayPauseAccessibilityFocus() {
        boolean shouldShowPauseButton = shouldShowPauseButton();
        if (!shouldShowPauseButton && playButton != null) {
            playButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        } else if (shouldShowPauseButton && pauseButton != null) {
            pauseButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
    }

    private void updateButton(boolean visible, boolean enabled, @Nullable View view) {
        if (view == null) {
            return;
        }
        view.setEnabled(enabled);
        view.setAlpha(enabled ? buttonAlphaEnabled : buttonAlphaDisabled);
        view.setVisibility(visible ? VISIBLE : GONE);
    }

    private void seekToTimeBarPosition(Player player, long positionMs) {
        int windowIndex;
        Timeline timeline = player.getCurrentTimeline();
        if (multiWindowTimeBar && !timeline.isEmpty()) {
            int windowCount = timeline.getWindowCount();
            windowIndex = 0;
            while (true) {
                long windowDurationMs = timeline.getWindow(windowIndex, window).getDurationMs();
                if (positionMs < windowDurationMs) {
                    break;
                } else if (windowIndex == windowCount - 1) {
                    // Seeking past the end of the last window should seek to the end of the timeline.
                    positionMs = windowDurationMs;
                    break;
                }
                positionMs -= windowDurationMs;
                windowIndex++;
            }
        } else {
            windowIndex = player.getCurrentMediaItemIndex();
        }
        seekTo(player, windowIndex, positionMs);
        updateProgress();
    }

    private void seekTo(Player player, int windowIndex, long positionMs) {
        player.seekTo(windowIndex, positionMs);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttachedToWindow = true;
        if (hideAtMs != C.TIME_UNSET) {
            long delayMs = hideAtMs - SystemClock.uptimeMillis();
            if (delayMs <= 0) {
                hide();
            } else {
                postDelayed(hideAction, delayMs);
            }
        } else if (isVisible()) {
            hideAfterTimeout();
        }
        updateAll();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isAttachedToWindow = false;
        removeCallbacks(updateProgressAction);
        removeCallbacks(hideAction);
    }

    @Override
    public final boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            removeCallbacks(hideAction);
        } else if (ev.getAction() == MotionEvent.ACTION_UP) {
            hideAfterTimeout();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return dispatchMediaKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    /**
     * Called to process media key events. Any {@link KeyEvent} can be passed but only media key
     * events will be handled.
     *
     * @param event A key event.
     * @return Whether the key event was handled.
     */
    public boolean dispatchMediaKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        @Nullable Player player = this.player;
        if (player == null || !isHandledMediaKey(keyCode)) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                if (player.getPlaybackState() != Player.STATE_ENDED) {
                    player.seekForward();
                }
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                player.seekBack();
            } else if (event.getRepeatCount() == 0) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    case KeyEvent.KEYCODE_HEADSETHOOK:
                        dispatchPlayPause(player);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        dispatchPlay(player);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        dispatchPause(player);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        player.seekToNext();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        player.seekToPrevious();
                        break;
                    default:
                        break;
                }
            }
        }
        return true;
    }

    private boolean shouldShowPauseButton() {
        return player != null
                && player.getPlaybackState() != Player.STATE_ENDED
                && player.getPlaybackState() != Player.STATE_IDLE
                && player.getPlayWhenReady();
    }

    private void dispatchPlayPause(Player player) {
        @Player.State int state = player.getPlaybackState();
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED || !player.getPlayWhenReady()) {
            dispatchPlay(player);
        } else {
            dispatchPause(player);
        }
    }

    private void dispatchPlay(Player player) {
        @Player.State int state = player.getPlaybackState();
        if (state == Player.STATE_IDLE) {
            player.prepare();
        } else if (state == Player.STATE_ENDED) {
            seekTo(player, player.getCurrentMediaItemIndex(), C.TIME_UNSET);
        }
        player.play();
    }

    private void dispatchPause(Player player) {
        player.pause();
    }

    @SuppressLint("InlinedApi")
    private static boolean isHandledMediaKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    }

    /**
     * Returns whether the specified {@code timeline} can be shown on a multi-window time bar.
     *
     * @param timeline The {@link Timeline} to check.
     * @param window A scratch {@link Timeline.Window} instance.
     * @return Whether the specified timeline can be shown on a multi-window time bar.
     */
    private static boolean canShowMultiWindowTimeBar(Timeline timeline, Timeline.Window window) {
        if (timeline.getWindowCount() > MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR) {
            return false;
        }
        int windowCount = timeline.getWindowCount();
        for (int i = 0; i < windowCount; i++) {
            if (timeline.getWindow(i, window).durationUs == C.TIME_UNSET) {
                return false;
            }
        }
        return true;
    }

    private final class ComponentListener
            implements Player.Listener, TimeBar.OnScrubListener, OnClickListener {

        @Override
        public void onEvents(Player player, Player.Events events) {
            if (events.containsAny(EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED)) {
                updatePlayPauseButton();
            }
            if (events.containsAny(
                    EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_IS_PLAYING_CHANGED)) {
                updateProgress();
            }
            if (events.contains(EVENT_REPEAT_MODE_CHANGED)) {
                updateRepeatModeButton();
            }
            if (events.contains(EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                updateShuffleButton();
            }
            if (events.containsAny(
                    EVENT_REPEAT_MODE_CHANGED,
                    EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    EVENT_POSITION_DISCONTINUITY,
                    EVENT_TIMELINE_CHANGED,
                    EVENT_AVAILABLE_COMMANDS_CHANGED)) {
                updateNavigation();
            }
            if (events.containsAny(EVENT_POSITION_DISCONTINUITY, EVENT_TIMELINE_CHANGED)) {
                updateTimeline();
            }
        }

        @Override
        public void onScrubStart(TimeBar timeBar, long position) {
            scrubbing = true;
            if (positionView != null) {
                positionView.setText(Util.getStringForTime(formatBuilder, formatter, position));
            }
        }

        @Override
        public void onScrubMove(TimeBar timeBar, long position) {
            if (positionView != null) {
                positionView.setText(Util.getStringForTime(formatBuilder, formatter, position));
            }
        }

        @Override
        public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
            scrubbing = false;
            if (!canceled && player != null) {
                seekToTimeBarPosition(player, position);
            }
        }

        @Override
        public void onClick(View view) {
            Player player = ExoPlayerCustomPlayerControlView.this.player;
            if (player == null) {
                if (view == playButton || view == pauseButton) {
                    if (videoMediaViewCallbacks != null) {
                        videoMediaViewCallbacks.initializePlayerAndStartPlaying();
                    }
                }

                return;
            }
            if (nextButton == view) {
                player.seekToNext();
            } else if (previousButton == view) {
                player.seekToPrevious();
            } else if (fastForwardButton == view) {
                if (player.getPlaybackState() != Player.STATE_ENDED) {
                    player.seekForward();
                }
            } else if (rewindButton == view) {
                player.seekBack();
            } else if (playButton == view) {
                dispatchPlay(player);
            } else if (pauseButton == view) {
                dispatchPause(player);
            } else if (repeatToggleButton == view) {
                player.setRepeatMode(
                        RepeatModeUtil.getNextRepeatMode(player.getRepeatMode(), repeatToggleModes));
            } else if (shuffleButton == view) {
                player.setShuffleModeEnabled(!player.getShuffleModeEnabled());
            }
        }
    }

    @RequiresApi(21)
    private static final class Api21 {
        @DoNotInline
        public static boolean isAccessibilityFocused(View view) {
            return view.isAccessibilityFocused();
        }
    }

    public interface VideoMediaViewCallbacks {
        public boolean isSystemUiHidden();
        public void initializePlayerAndStartPlaying();
    }
}
