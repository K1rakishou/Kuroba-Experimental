package com.github.k1rakishou.chan.ui.captcha;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.common.StringUtils;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaptchaHolder {
    private static final String TAG = "CaptchaHolder";
    private static final long INTERVAL = 5000;
    private static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
    private AtomicBoolean running = new AtomicBoolean(false);

    private Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private Timer timer;

    @Nullable
    private CaptchaValidationListener catalogCaptchaValidationListener;
    @Nullable
    private CaptchaValidationListener threadCaptchaValidationListener;

    @GuardedBy("itself")
    private final List<CaptchaInfo> captchaQueue = new ArrayList<>();

    public void setListener(ChanDescriptor descriptor, CaptchaValidationListener listener) {
        BackgroundUtils.ensureMainThread();

        if (descriptor.isCatalogDescriptor()) {
            catalogCaptchaValidationListener = listener;
        } else {
            threadCaptchaValidationListener = listener;
        }

        notifyListener();
    }

    public void clearCallbacks() {
        BackgroundUtils.ensureMainThread();

        catalogCaptchaValidationListener = null;
        threadCaptchaValidationListener = null;
    }

    public void addNewToken(String token, long tokenLifetime) {
        BackgroundUtils.ensureMainThread();
        removeNotValidTokens();

        synchronized (captchaQueue) {
            captchaQueue.add(
                    0,
                    new CaptchaInfo(token, tokenLifetime + System.currentTimeMillis())
            );

            Logger.d(TAG, "A new token has been added, validCount = " + captchaQueue.size()
                    + ", token = " + StringUtils.trimToken(token));
        }

        notifyListener();
        startTimer();
    }

    public boolean hasToken() {
        BackgroundUtils.ensureMainThread();
        removeNotValidTokens();

        synchronized (captchaQueue) {
            if (captchaQueue.isEmpty()) {
                stopTimer();
                return false;
            }
        }

        return true;
    }

    @Nullable
    public String getToken() {
        BackgroundUtils.ensureMainThread();
        removeNotValidTokens();

        synchronized (captchaQueue) {
            if (captchaQueue.isEmpty()) {
                stopTimer();
                return null;
            }

            int lastIndex = captchaQueue.size() - 1;

            String token = captchaQueue.get(lastIndex).getToken();
            captchaQueue.remove(lastIndex);
            Logger.d(TAG, "getToken() token = " + StringUtils.trimToken(token));

            notifyListener();
            return token;
        }
    }

    private void startTimer() {
        if (running.compareAndSet(false, true)) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new CheckCaptchaFreshnessTask(), INTERVAL, INTERVAL);

            Logger.d(TAG, "Timer started");
        }
    }

    private void stopTimer() {
        if (running.compareAndSet(true, false)) {
            timer.cancel();
            timer.purge();

            Logger.d(TAG, "Timer stopped");
        }
    }

    private void removeNotValidTokens() {
        long now = System.currentTimeMillis();
        boolean captchasCountDecreased = false;

        synchronized (captchaQueue) {
            ListIterator<CaptchaInfo> it = captchaQueue.listIterator(captchaQueue.size());
            while (it.hasPrevious()) {
                CaptchaInfo captchaInfo = it.previous();
                if (now > captchaInfo.getValidUntil()) {
                    captchasCountDecreased = true;
                    it.remove();

                    Logger.d(TAG, "Captcha token got expired, now = " + sdf.format(now)
                            + ", token validUntil = "
                            + sdf.format(captchaInfo.getValidUntil()) + ", token = "
                            + StringUtils.trimToken(captchaInfo.getToken())
                    );
                }
            }

            if (captchaQueue.isEmpty()) {
                stopTimer();
            }
        }

        if (captchasCountDecreased) {
            notifyListener();
        }
    }

    private void notifyListener() {
        mainThreadHandler.post(() -> {
            int count = 0;

            synchronized (captchaQueue) {
                count = captchaQueue.size();
            }

            if (catalogCaptchaValidationListener != null) {
                catalogCaptchaValidationListener.onCaptchaCountChanged(count);
            }

            if (threadCaptchaValidationListener != null) {
                threadCaptchaValidationListener.onCaptchaCountChanged(count);
            }
        });
    }

    private class CheckCaptchaFreshnessTask
            extends TimerTask {
        @Override
        public void run() {
            removeNotValidTokens();
        }
    }

    public interface CaptchaValidationListener {
        void onCaptchaCountChanged(int validCaptchaCount);
    }

    private static class CaptchaInfo {
        private String token;
        private long validUntil;

        public CaptchaInfo(String token, long validUntil) {
            this.token = token;
            this.validUntil = validUntil;
        }

        public String getToken() {
            return token;
        }

        public long getValidUntil() {
            return validUntil;
        }

        @Override
        public int hashCode() {
            return token.hashCode()
                    + 31 * (int) (validUntil & 0x00000000FFFFFFFFL)
                    + 31 * (int) ((validUntil >> 32) & 0x00000000FFFFFFFFL
            );
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (other == null) {
                return false;
            }

            if (this == other) {
                return true;
            }

            if (this.getClass() != other.getClass()) {
                return false;
            }

            CaptchaInfo otherCaptchaInfo = (CaptchaInfo) other;
            return token.equals(otherCaptchaInfo.token) && validUntil == otherCaptchaInfo.validUntil;
        }

        @NonNull
        @Override
        public String toString() {
            return "validUntil = " + sdf.format(validUntil) + ", token = " + StringUtils.trimToken(token);
        }
    }
}
