/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.database;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.utils.Logger;
import com.j256.ormlite.misc.TransactionManager;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class DatabaseManager {
    private static final String TAG = "DatabaseManager";

    private final ExecutorService backgroundExecutor;
    private Thread executorThread;

    @Inject
    DatabaseHelper helper;

    private final DatabaseFilterManager databaseFilterManager;

    @Inject
    public DatabaseManager() {
        inject(this);

        backgroundExecutor = new ThreadPoolExecutor(1, 1, 1000L, TimeUnit.DAYS, new LinkedBlockingQueue<>());

        databaseFilterManager = new DatabaseFilterManager(helper);
    }

    public DatabaseFilterManager getDatabaseFilterManager() {
        return databaseFilterManager;
    }


    public <T> T runTask(final Callable<T> taskCallable) {
        try {
            return executeTask(taskCallable, null).get();
        } catch (InterruptedException e) {
            // Since we don't rethrow InterruptedException we need to at least restore the
            // "interrupted" flag.
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> Future<T> executeTask(final Callable<T> taskCallable, final TaskResult<T> taskResult) {
        if (Thread.currentThread() == executorThread) {
            DatabaseCallable<T> databaseCallable = new DatabaseCallable<>(taskCallable, taskResult);
            T result = databaseCallable.call();

            return new Future<T>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public T get() {
                    return result;
                }

                @Override
                public T get(long timeout, @NonNull TimeUnit unit) {
                    return result;
                }
            };
        } else {
            return backgroundExecutor.submit(new DatabaseCallable<>(taskCallable, taskResult));
        }
    }

    private class DatabaseCallable<T>
            implements Callable<T> {
        private final Callable<T> taskCallable;
        private final TaskResult<T> taskResult;

        public DatabaseCallable(Callable<T> taskCallable, TaskResult<T> taskResult) {
            this.taskCallable = taskCallable;
            this.taskResult = taskResult;
        }

        @Override
        public T call() {
            executorThread = Thread.currentThread();

            try {
                final T result = TransactionManager.callInTransaction(helper.getConnectionSource(), taskCallable);
                if (taskResult != null) {
                    new Handler(Looper.getMainLooper()).post(() -> taskResult.onComplete(result));
                }
                return result;
            } catch (Exception e) {
                Logger.e(TAG, "executeTask", e);
                throw new RuntimeException(e);
            }
        }
    }

    public interface TaskResult<T> {
        void onComplete(T result);
    }
}
