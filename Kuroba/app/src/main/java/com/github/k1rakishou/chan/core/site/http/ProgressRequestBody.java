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
package com.github.k1rakishou.chan.core.site.http;

import java.io.IOException;
import java.util.concurrent.CancellationException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

public class ProgressRequestBody extends RequestBody {
    private final int fileIndex;
    private final int totalFiles;
    protected RequestBody delegate;
    protected ProgressRequestListener listener;
    protected ProgressSink progressSink;

    private static final int maxPercent = 100;
    private static final int percentStep = 5;

    public ProgressRequestBody(
            RequestBody delegate,
            ProgressRequestListener listener
    ) {
        this.fileIndex = 1;
        this.totalFiles = 1;
        this.delegate = delegate;
        this.listener = listener;
    }

    public ProgressRequestBody(
            int fileIndex,
            int totalFiles,
            RequestBody delegate,
            ProgressRequestListener listener
    ) {
        this.fileIndex = fileIndex;
        this.totalFiles = totalFiles;
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public MediaType contentType() {
        return delegate.contentType();
    }

    @Override
    public long contentLength() throws IOException {
        return delegate.contentLength();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        progressSink = new ProgressSink(sink);
        BufferedSink bufferedSink = Okio.buffer(progressSink);

        delegate.writeTo(bufferedSink);
        bufferedSink.flush();
    }

    protected final class ProgressSink extends ForwardingSink {
        private long bytesWritten = 0;
        private int lastPercent = 0;

        public ProgressSink(Sink delegate) {
            super(delegate);
        }

        @Override
        public void write(Buffer source, long byteCount) throws IOException {
            super.write(source, byteCount);

            if (bytesWritten == 0) {
                try {
                    // so we can know that the uploading has just started
                    listener.onRequestProgress(fileIndex, totalFiles, 0);
                } catch (CancellationException cancellationException) {
                    throw new IOException("Canceled");
                }
            }

            bytesWritten += byteCount;

            if (contentLength() > 0) {
                int percent = (int) (maxPercent * bytesWritten / contentLength());
                if (percent - lastPercent >= percentStep) {
                    lastPercent = percent;

                    // OkHttp will explode if the listener throws anything other than IOException
                    // so we need to wrap those exceptions into IOException. For now only
                    // CancellationException was found to be thrown somewhere deep inside the listener.
                    try {
                        listener.onRequestProgress(fileIndex, totalFiles, percent);
                    } catch (CancellationException cancellationException) {
                        throw new IOException("Canceled");
                    }
                }
            }
        }
    }

    public interface ProgressRequestListener {
        void onRequestProgress(int fileIndex, int totalFiles, int percent);
    }
}
