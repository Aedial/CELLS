package com.cells.profiling;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;


/**
 * Typed timing boundaries for supplied calls and caller-selected profiling entries.
 */
public final class Profiler {

    @FunctionalInterface
    public interface IntCall<T, R> {
        R call(T target, int argument);
    }

    @FunctionalInterface
    public interface Call3<A, B, C, R> {
        R call(A first, B second, C third);
    }

    private Profiler() {}

    public static <T, R> R wrap(IntCall<T, R> call, ProfileOperation operation,
                                @Nullable ProfileEntry entry, T target, int argument) {
        if (entry == null) return call.call(target, argument);

        long started = entry.begin(operation);
        try {
            return call.call(target, argument);
        } finally {
            entry.end(operation, started);
        }
    }

    public static <A, R> R wrap(Function<A, R> call, ProfileOperation operation,
                                @Nullable ProfileEntry entry, A argument) {
        if (entry == null) return call.apply(argument);

        long started = entry.begin(operation);
        try {
            return call.apply(argument);
        } finally {
            entry.end(operation, started);
        }
    }

    public static <A, B, C, R> R wrap(Call3<A, B, C, R> call, ProfileOperation operation,
                                      @Nullable ProfileEntry entry, A first, B second, C third) {
        if (entry == null) return call.call(first, second, third);

        long started = entry.begin(operation);
        try {
            return call.call(first, second, third);
        } finally {
            entry.end(operation, started);
        }
    }

    public static <T> boolean wrapPredicate(Predicate<T> call, ProfileOperation operation,
                                          @Nullable ProfileEntry entry, T argument) {
        if (entry == null) return call.test(argument);

        long started = entry.begin(operation);
        try {
            return call.test(argument);
        } finally {
            entry.end(operation, started);
        }
    }

    public static <T> void wrapConsumer(Consumer<T> call, ProfileOperation operation,
                                    @Nullable ProfileEntry entry, T argument) {
        if (entry == null) {
            call.accept(argument);
            return;
        }

        long started = entry.begin(operation);
        try {
            call.accept(argument);
        } finally {
            entry.end(operation, started);
        }
    }
}
