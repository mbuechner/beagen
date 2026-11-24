/*
 * Copyright 2019-2026 Michael Büchner, Deutsche Digitale Bibliothek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ddb.labs.beagen.backend.helper;

import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Call;

/**
 * Tracks cancellation for one scheduler lifecycle.
 * It can abort both import processing and an active HTTP response body read.
 */
public final class ImportCancellation {

    private final AtomicReference<Call> activeCall = new AtomicReference<>();
    private volatile boolean cancelled;

    /**
     * Creates a tracker for a scheduler that has not been cancelled.
     */
    public ImportCancellation() {
    }

    /**
     * Permanently marks the tracker as cancelled and aborts its active HTTP call.
     * The shutdown thread can call this method while an import is in progress.
     */
    public void cancel() {
        cancelled = true;
        final Call call = activeCall.get();
        if (call != null) {
            call.cancel();
        }
    }

    /**
     * Checks whether an import may continue, including between network requests.
     *
     * @throws InterruptedIOException if cancellation has been requested
     */
    public void check() throws InterruptedIOException {
        if (cancelled) {
            throw new InterruptedIOException("BEACON import cancelled");
        }
    }

    void begin(Call call) throws InterruptedIOException {
        if (!activeCall.compareAndSet(null, call)) {
            throw new IllegalStateException("Another import request is already active");
        }
        try {
            check();
        } catch (InterruptedIOException e) {
            finish(call);
            throw e;
        }
    }

    void finish(Call call) {
        activeCall.compareAndSet(call, null);
    }
}
