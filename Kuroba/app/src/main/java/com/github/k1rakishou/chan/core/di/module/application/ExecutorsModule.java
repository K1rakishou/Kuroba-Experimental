package com.github.k1rakishou.chan.core.di.module.application;


import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ExecutorsModule {
    public static final String onDemandContentLoaderExecutorName = "OnDemandContentLoaderExecutor";
    private static final AtomicInteger onDemandContentLoaderExecutorThreadIndex = new AtomicInteger(0);

    @Provides
    @Singleton
    @Named(onDemandContentLoaderExecutorName)
    public Executor provideOnDemandContentLoaderExecutor() {
        return createExecutor(
                onDemandContentLoaderExecutorName,
                1,
                onDemandContentLoaderExecutorThreadIndex
        );
    }

    private Executor createExecutor(String name, int threadCount, AtomicInteger threadIndex) {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be > 0");
        }

        return Executors.newFixedThreadPool(threadCount, runnable -> new Thread(
                runnable,
                String.format(
                        Locale.ENGLISH,
                        name,
                        threadIndex.getAndIncrement()
                )
        ));
    }

}
