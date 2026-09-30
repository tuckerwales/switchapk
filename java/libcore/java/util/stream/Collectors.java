package java.util.stream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

public final class Collectors {
    private Collectors() {
    }

    static final class CollectorImpl<T, A, R> implements Collector<T, A, R> {
        private final Supplier<A> supplier;
        private final BiConsumer<A, T> accumulator;
        private final BinaryOperator<A> combiner;
        private final Function<A, R> finisher;

        CollectorImpl(Supplier<A> supplier, BiConsumer<A, T> accumulator, BinaryOperator<A> combiner, Function<A, R> finisher) {
            this.supplier = supplier;
            this.accumulator = accumulator;
            this.combiner = combiner;
            this.finisher = finisher;
        }

        public BiConsumer<A, T> accumulator() {
            return accumulator;
        }

        public Supplier<A> supplier() {
            return supplier;
        }

        public BinaryOperator<A> combiner() {
            return combiner;
        }

        public Function<A, R> finisher() {
            return finisher;
        }

        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
    }

    @SuppressWarnings("unchecked")
    static <I, R> Function<I, R> castingIdentity() {
        return new Function<I, R>() {
            public R apply(I i) {
                return (R) i;
            }
        };
    }

    private static <T> BinaryOperator<T> noCombiner() {
        return new BinaryOperator<T>() {
            public T apply(T a, T b) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static <T, C extends Collection<T>> Collector<T, ?, C> toCollection(final Supplier<C> collectionFactory) {
        return new CollectorImpl<T, C, C>(collectionFactory, new BiConsumer<C, T>() {
            public void accept(C c, T t) {
                c.add(t);
            }
        }, Collectors.<C>noCombiner(), Collectors.<C, C>castingIdentity());
    }

    public static <T> Collector<T, ?, List<T>> toList() {
        return toCollection(new Supplier<List<T>>() {
            public List<T> get() {
                return new ArrayList<T>();
            }
        });
    }

    public static <T> Collector<T, ?, List<T>> toUnmodifiableList() {
        return collectingAndThen(Collectors.<T>toList(), new Function<List<T>, List<T>>() {
            public List<T> apply(List<T> l) {
                return Collections.unmodifiableList(l);
            }
        });
    }

    public static <T> Collector<T, ?, Set<T>> toSet() {
        return toCollection(new Supplier<Set<T>>() {
            public Set<T> get() {
                return new HashSet<T>();
            }
        });
    }

    public static <T> Collector<T, ?, Set<T>> toUnmodifiableSet() {
        return collectingAndThen(Collectors.<T>toSet(), new Function<Set<T>, Set<T>>() {
            public Set<T> apply(Set<T> s) {
                return Collections.unmodifiableSet(s);
            }
        });
    }

    public static Collector<CharSequence, ?, String> joining() {
        return joining("", "", "");
    }

    public static Collector<CharSequence, ?, String> joining(CharSequence delimiter) {
        return joining(delimiter, "", "");
    }

    public static Collector<CharSequence, ?, String> joining(final CharSequence delimiter, final CharSequence prefix,
            final CharSequence suffix) {
        return new CollectorImpl<CharSequence, StringJoiner, String>(new Supplier<StringJoiner>() {
            public StringJoiner get() {
                return new StringJoiner(delimiter, prefix, suffix);
            }
        }, new BiConsumer<StringJoiner, CharSequence>() {
            public void accept(StringJoiner j, CharSequence s) {
                j.add(s);
            }
        }, Collectors.<StringJoiner>noCombiner(), new Function<StringJoiner, String>() {
            public String apply(StringJoiner j) {
                return j.toString();
            }
        });
    }

    public static <T, U, A, R> Collector<T, ?, R> mapping(final Function<? super T, ? extends U> mapper,
            final Collector<? super U, A, R> downstream) {
        final BiConsumer<A, ? super U> acc = downstream.accumulator();
        return new CollectorImpl<T, A, R>(downstream.supplier(), new BiConsumer<A, T>() {
            @SuppressWarnings("unchecked")
            public void accept(A a, T t) {
                ((BiConsumer<A, U>) acc).accept(a, mapper.apply(t));
            }
        }, downstream.combiner(), downstream.finisher());
    }

    public static <T, A, R> Collector<T, ?, R> filtering(final Predicate<? super T> predicate,
            final Collector<? super T, A, R> downstream) {
        final BiConsumer<A, ? super T> acc = downstream.accumulator();
        return new CollectorImpl<T, A, R>(downstream.supplier(), new BiConsumer<A, T>() {
            @SuppressWarnings("unchecked")
            public void accept(A a, T t) {
                if (predicate.test(t)) {
                    ((BiConsumer<A, T>) acc).accept(a, t);
                }
            }
        }, downstream.combiner(), downstream.finisher());
    }

    public static <T, A, R, RR> Collector<T, A, RR> collectingAndThen(Collector<T, A, R> downstream,
            final Function<R, RR> finisher) {
        final Function<A, R> f = downstream.finisher();
        return new CollectorImpl<T, A, RR>(downstream.supplier(), downstream.accumulator(), downstream.combiner(),
                new Function<A, RR>() {
                    public RR apply(A a) {
                        return finisher.apply(f.apply(a));
                    }
                });
    }

    public static <T> Collector<T, ?, Long> counting() {
        return new CollectorImpl<T, long[], Long>(new Supplier<long[]>() {
            public long[] get() {
                return new long[1];
            }
        }, new BiConsumer<long[], T>() {
            public void accept(long[] a, T t) {
                a[0]++;
            }
        }, Collectors.<long[]>noCombiner(), new Function<long[], Long>() {
            public Long apply(long[] a) {
                return a[0];
            }
        });
    }

    public static <T> Collector<T, ?, Optional<T>> minBy(final Comparator<? super T> comparator) {
        return reducing(new BinaryOperator<T>() {
            public T apply(T a, T b) {
                return comparator.compare(a, b) <= 0 ? a : b;
            }
        });
    }

    public static <T> Collector<T, ?, Optional<T>> maxBy(final Comparator<? super T> comparator) {
        return reducing(new BinaryOperator<T>() {
            public T apply(T a, T b) {
                return comparator.compare(a, b) >= 0 ? a : b;
            }
        });
    }

    public static <T> Collector<T, ?, Integer> summingInt(final ToIntFunction<? super T> mapper) {
        return new CollectorImpl<T, int[], Integer>(new Supplier<int[]>() {
            public int[] get() {
                return new int[1];
            }
        }, new BiConsumer<int[], T>() {
            public void accept(int[] a, T t) {
                a[0] += mapper.applyAsInt(t);
            }
        }, Collectors.<int[]>noCombiner(), new Function<int[], Integer>() {
            public Integer apply(int[] a) {
                return a[0];
            }
        });
    }

    public static <T> Collector<T, ?, Long> summingLong(final ToLongFunction<? super T> mapper) {
        return new CollectorImpl<T, long[], Long>(new Supplier<long[]>() {
            public long[] get() {
                return new long[1];
            }
        }, new BiConsumer<long[], T>() {
            public void accept(long[] a, T t) {
                a[0] += mapper.applyAsLong(t);
            }
        }, Collectors.<long[]>noCombiner(), new Function<long[], Long>() {
            public Long apply(long[] a) {
                return a[0];
            }
        });
    }

    public static <T> Collector<T, ?, Double> summingDouble(final ToDoubleFunction<? super T> mapper) {
        return new CollectorImpl<T, double[], Double>(new Supplier<double[]>() {
            public double[] get() {
                return new double[1];
            }
        }, new BiConsumer<double[], T>() {
            public void accept(double[] a, T t) {
                a[0] += mapper.applyAsDouble(t);
            }
        }, Collectors.<double[]>noCombiner(), new Function<double[], Double>() {
            public Double apply(double[] a) {
                return a[0];
            }
        });
    }

    public static <T> Collector<T, ?, Double> averagingInt(final ToIntFunction<? super T> mapper) {
        return averagingDouble(new ToDoubleFunction<T>() {
            public double applyAsDouble(T t) {
                return mapper.applyAsInt(t);
            }
        });
    }

    public static <T> Collector<T, ?, Double> averagingLong(final ToLongFunction<? super T> mapper) {
        return averagingDouble(new ToDoubleFunction<T>() {
            public double applyAsDouble(T t) {
                return mapper.applyAsLong(t);
            }
        });
    }

    public static <T> Collector<T, ?, Double> averagingDouble(final ToDoubleFunction<? super T> mapper) {
        return new CollectorImpl<T, double[], Double>(new Supplier<double[]>() {
            public double[] get() {
                return new double[2];
            }
        }, new BiConsumer<double[], T>() {
            public void accept(double[] a, T t) {
                a[0] += mapper.applyAsDouble(t);
                a[1]++;
            }
        }, Collectors.<double[]>noCombiner(), new Function<double[], Double>() {
            public Double apply(double[] a) {
                return a[1] == 0 ? 0.0d : a[0] / a[1];
            }
        });
    }

    public static <T> Collector<T, ?, IntSummaryStatistics> summarizingInt(final ToIntFunction<? super T> mapper) {
        return new CollectorImpl<T, IntSummaryStatistics, IntSummaryStatistics>(new Supplier<IntSummaryStatistics>() {
            public IntSummaryStatistics get() {
                return new IntSummaryStatistics();
            }
        }, new BiConsumer<IntSummaryStatistics, T>() {
            public void accept(IntSummaryStatistics r, T t) {
                r.accept(mapper.applyAsInt(t));
            }
        }, Collectors.<IntSummaryStatistics>noCombiner(), Collectors.<IntSummaryStatistics, IntSummaryStatistics>castingIdentity());
    }

    public static <T> Collector<T, ?, T> reducing(final T identity, final BinaryOperator<T> op) {
        return new CollectorImpl<T, Object[], T>(new Supplier<Object[]>() {
            public Object[] get() {
                return new Object[] {identity};
            }
        }, new BiConsumer<Object[], T>() {
            @SuppressWarnings("unchecked")
            public void accept(Object[] a, T t) {
                a[0] = op.apply((T) a[0], t);
            }
        }, Collectors.<Object[]>noCombiner(), new Function<Object[], T>() {
            @SuppressWarnings("unchecked")
            public T apply(Object[] a) {
                return (T) a[0];
            }
        });
    }

    public static <T> Collector<T, ?, Optional<T>> reducing(final BinaryOperator<T> op) {
        return new CollectorImpl<T, Object[], Optional<T>>(new Supplier<Object[]>() {
            public Object[] get() {
                return new Object[] {null, Boolean.FALSE};
            }
        }, new BiConsumer<Object[], T>() {
            @SuppressWarnings("unchecked")
            public void accept(Object[] a, T t) {
                if (a[1] == Boolean.TRUE) {
                    a[0] = op.apply((T) a[0], t);
                } else {
                    a[0] = t;
                    a[1] = Boolean.TRUE;
                }
            }
        }, Collectors.<Object[]>noCombiner(), new Function<Object[], Optional<T>>() {
            @SuppressWarnings("unchecked")
            public Optional<T> apply(Object[] a) {
                return a[1] == Boolean.TRUE ? Optional.ofNullable((T) a[0]) : Optional.<T>empty();
            }
        });
    }

    public static <T, U> Collector<T, ?, U> reducing(final U identity, final Function<? super T, ? extends U> mapper,
            final BinaryOperator<U> op) {
        return mapping(mapper, reducing(identity, op));
    }

    public static <T, K> Collector<T, ?, Map<K, List<T>>> groupingBy(Function<? super T, ? extends K> classifier) {
        return groupingBy(classifier, Collectors.<T>toList());
    }

    public static <T, K, A, D> Collector<T, ?, Map<K, D>> groupingBy(Function<? super T, ? extends K> classifier,
            Collector<? super T, A, D> downstream) {
        return groupingBy(classifier, new Supplier<Map<K, D>>() {
            public Map<K, D> get() {
                return new HashMap<K, D>();
            }
        }, downstream);
    }

    @SuppressWarnings("unchecked")
    public static <T, K, D, A, M extends Map<K, D>> Collector<T, ?, M> groupingBy(
            final Function<? super T, ? extends K> classifier, final Supplier<M> mapFactory,
            final Collector<? super T, A, D> downstream) {
        final Supplier<A> ds = downstream.supplier();
        final BiConsumer<A, ? super T> dacc = downstream.accumulator();
        final Function<A, D> dfin = downstream.finisher();
        return new CollectorImpl<T, Map<K, A>, M>(new Supplier<Map<K, A>>() {
            public Map<K, A> get() {
                return (Map<K, A>) mapFactory.get();
            }
        }, new BiConsumer<Map<K, A>, T>() {
            public void accept(Map<K, A> m, T t) {
                K key = classifier.apply(t);
                if (key == null) {
                    throw new NullPointerException("element cannot be mapped to a null key");
                }
                A container = m.get(key);
                if (container == null) {
                    container = ds.get();
                    m.put(key, container);
                }
                ((BiConsumer<A, T>) dacc).accept(container, t);
            }
        }, Collectors.<Map<K, A>>noCombiner(), new Function<Map<K, A>, M>() {
            public M apply(Map<K, A> intermediate) {
                for (Map.Entry<K, A> e : intermediate.entrySet()) {
                    ((Map.Entry<K, Object>) (Map.Entry<K, ?>) e).setValue(dfin.apply(e.getValue()));
                }
                return (M) intermediate;
            }
        });
    }

    public static <T> Collector<T, ?, Map<Boolean, List<T>>> partitioningBy(Predicate<? super T> predicate) {
        return partitioningBy(predicate, Collectors.<T>toList());
    }

    public static <T, D, A> Collector<T, ?, Map<Boolean, D>> partitioningBy(final Predicate<? super T> predicate,
            Collector<? super T, A, D> downstream) {
        Collector<T, ?, Map<Boolean, D>> c = groupingBy(new Function<T, Boolean>() {
            public Boolean apply(T t) {
                return predicate.test(t);
            }
        }, new Supplier<Map<Boolean, D>>() {
            public Map<Boolean, D> get() {
                return new LinkedHashMap<Boolean, D>();
            }
        }, downstream);
        final Supplier<A> ds = downstream.supplier();
        final Function<A, D> df = downstream.finisher();
        return collectingAndThen((Collector<T, Object, Map<Boolean, D>>) (Collector<T, ?, ?>) c,
                new Function<Map<Boolean, D>, Map<Boolean, D>>() {
                    public Map<Boolean, D> apply(Map<Boolean, D> m) {
                        LinkedHashMap<Boolean, D> r = new LinkedHashMap<Boolean, D>();
                        r.put(Boolean.FALSE, m.containsKey(Boolean.FALSE) ? m.get(Boolean.FALSE) : df.apply(ds.get()));
                        r.put(Boolean.TRUE, m.containsKey(Boolean.TRUE) ? m.get(Boolean.TRUE) : df.apply(ds.get()));
                        return r;
                    }
                });
    }

    public static <T, K, U> Collector<T, ?, Map<K, U>> toMap(Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends U> valueMapper) {
        return toMap(keyMapper, valueMapper, new BinaryOperator<U>() {
            public U apply(U a, U b) {
                throw new IllegalStateException("Duplicate key (attempted merging values " + a + " and " + b + ")");
            }
        });
    }

    public static <T, K, U> Collector<T, ?, Map<K, U>> toMap(Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends U> valueMapper, BinaryOperator<U> mergeFunction) {
        return toMap(keyMapper, valueMapper, mergeFunction, new Supplier<Map<K, U>>() {
            public Map<K, U> get() {
                return new HashMap<K, U>();
            }
        });
    }

    public static <T, K, U, M extends Map<K, U>> Collector<T, ?, M> toMap(final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper, final BinaryOperator<U> mergeFunction,
            Supplier<M> mapFactory) {
        return new CollectorImpl<T, M, M>(mapFactory, new BiConsumer<M, T>() {
            public void accept(M map, T element) {
                map.merge(keyMapper.apply(element), valueMapper.apply(element), mergeFunction);
            }
        }, Collectors.<M>noCombiner(), Collectors.<M, M>castingIdentity());
    }

    public static <T, K, U> Collector<T, ?, Map<K, U>> toUnmodifiableMap(Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends U> valueMapper) {
        return collectingAndThen(Collectors.<T, K, U>toMapImpl(keyMapper, valueMapper), new Function<Map<K, U>, Map<K, U>>() {
            public Map<K, U> apply(Map<K, U> m) {
                return Collections.unmodifiableMap(m);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T, K, U> Collector<T, Object, Map<K, U>> toMapImpl(Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends U> valueMapper) {
        return (Collector<T, Object, Map<K, U>>) (Collector<T, ?, ?>) toMap(keyMapper, valueMapper);
    }
}
