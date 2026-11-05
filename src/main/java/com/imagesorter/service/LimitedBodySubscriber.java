package com.imagesorter.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Enforces the download limit while receiving data, before allocating the complete image. */
final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final long limit;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.Subscription subscription;

    LimitedBodySubscriber(long limit) { this.limit = limit; }
    public CompletionStage<byte[]> getBody() { return result; }
    public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
    public void onNext(List<ByteBuffer> buffers) {
        for (ByteBuffer buffer : buffers) {
            if ((long) bytes.size() + buffer.remaining() > limit) {
                subscription.cancel();
                result.completeExceptionally(new IOException("Image exceeds the 25 MB download limit."));
                return;
            }
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            bytes.writeBytes(chunk);
        }
        subscription.request(1);
    }
    public void onError(Throwable failure) { result.completeExceptionally(failure); }
    public void onComplete() { result.complete(bytes.toByteArray()); }
}
