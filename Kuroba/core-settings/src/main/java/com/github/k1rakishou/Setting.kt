package com.github.k1rakishou;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.processors.BehaviorProcessor;

public abstract class Setting<T> {
    protected final SettingProvider settingProvider;
    protected final String key;
    protected final T def;
    protected BehaviorProcessor<T> settingState = BehaviorProcessor.create();

    public Setting(SettingProvider settingProvider, String key, T def) {
        this.settingProvider = settingProvider;
        this.key = key;
        this.def = def;
    }

    public abstract T get();

    public abstract void set(T value);

    public abstract void setSync(T value);

    public boolean isDefault() {
        T def = getDefault();
        T curr = get();

        if (def != null && curr == null) {
            return false;
        }

        return curr.equals(def);
    }

    public T getDefault() {
        return def;
    }

    public String getKey() {
        return key;
    }

    public Flowable<T> listenForChanges() {
        if (!settingState.hasValue()) {
            settingState.onNext(get());
        }

        return settingState
          .onBackpressureLatest()
          .hide()
          .observeOn(AndroidSchedulers.mainThread());
    }

}
