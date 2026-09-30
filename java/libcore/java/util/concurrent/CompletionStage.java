package java.util.concurrent;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public interface CompletionStage<T> {
    <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn);

    CompletionStage<Void> thenAccept(Consumer<? super T> action);

    CompletionStage<Void> thenRun(Runnable action);

    <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

    CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn);

    <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

    CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    CompletableFuture<T> toCompletableFuture();
}
