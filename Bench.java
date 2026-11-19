import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.LongSupplier;

/**
 * Small, dependency-free benchmarking helper shared by Q1-Q7.
 *
 * Method (applies to every timing reported in the assignment):
 *   1. WARM-UP runs are executed and discarded so the JIT compiler has compiled the hot code.
 *   2. Then RUNS measured repetitions are timed with System.nanoTime().
 *   3. We report median (robust to outliers), mean, standard deviation, min and max.
 *   4. Every measured action returns a value that is accumulated into {@link #sink}, so the JIT
 *      cannot delete the work as "dead code".
 */
public final class Bench {
    private Bench() {}

    /** Prevents dead-code elimination. Never read for meaning. */
    public static volatile long sink;

    /** Statistics of one experiment (all times in nanoseconds per run). */
    public static final class Stat {
        public final double median, mean, sd, min, max;
        public final int runs;
        Stat(double[] t) {
            double[] s = t.clone();
            Arrays.sort(s);
            runs = s.length;
            min = s[0];
            max = s[s.length - 1];
            median = (s.length % 2 == 1) ? s[s.length / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2.0;
            double m = 0;
            for (double v : s) m += v;
            mean = m / s.length;
            double q = 0;
            for (double v : s) q += (v - mean) * (v - mean);
            sd = s.length > 1 ? Math.sqrt(q / (s.length - 1)) : 0;
        }
        /** Coefficient of variation in percent: how noisy the measurement was. */
        public double cvPercent() { return mean == 0 ? 0 : 100.0 * sd / mean; }
    }

    /** Time {@code action} (which must return a value that depends on its work). */
    public static Stat time(int warmup, int runs, LongSupplier action) {
        long acc = 0;
        for (int i = 0; i < warmup; i++) acc += action.getAsLong();
        double[] t = new double[runs];
        for (int i = 0; i < runs; i++) {
            long a = System.nanoTime();
            acc += action.getAsLong();
            t[i] = System.nanoTime() - a;
        }
        sink += acc;
        return new Stat(t);
    }

    /**
     * Like {@link #time} but each run first builds fresh state with {@code setup} (NOT timed) and then times
     * only {@code action(state)}. Needed when the operation changes the structure it is measuring.
     */
    public static <T> Stat timeWithSetup(int warmup, int runs, java.util.function.Supplier<T> setup,
                                         java.util.function.ToLongFunction<T> action) {
        long acc = 0;
        for (int i = 0; i < warmup; i++) acc += action.applyAsLong(setup.get());
        double[] t = new double[runs];
        for (int i = 0; i < runs; i++) {
            T state = setup.get();
            long a = System.nanoTime();
            acc += action.applyAsLong(state);
            t[i] = System.nanoTime() - a;
        }
        sink += acc;
        return new Stat(t);
    }

    /** Bytes allocated by the current thread while running {@code action} (exact, from the JVM). */
    public static long allocatedBytes(LongSupplier action) {
        com.sun.management.ThreadMXBean mx =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(id);
        sink += action.getAsLong();
        return mx.getThreadAllocatedBytes(id) - before;
    }

    /** Heap in use right now, after asking the GC to run (approximate; used for before/after). */
    public static long usedHeap() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try { Thread.sleep(30); } catch (InterruptedException ignored) { }
        }
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    private static Object heldForRetained;

    /** Clean heap baseline: call ONCE, before any structure you want to measure exists. */
    public static long cleanBaseline() {
        heldForRetained = null;
        return usedHeap();
    }

    /**
     * Retained heap bytes of the object graph returned by {@code build}, measured against {@code cleanBaseline}.
     * Median of 5 rounds. (A per-round "before" reading is avoided on purpose: HotSpot can keep the previous round's
     * object alive through a stale interpreter-frame reference, which silently corrupts before/after differences.)
     */
    public static double retainedBytes(java.util.function.Supplier<Object> build, long cleanBaseline) {
        double[] r = new double[5];
        for (int i = 0; i < r.length; i++) {
            heldForRetained = build.get();
            long after = usedHeap();
            r[i] = after - cleanBaseline;
            sink += System.identityHashCode(heldForRetained);
            heldForRetained = null;
        }
        Arrays.sort(r);
        return r[2];
    }

    // ---------------- CSV output ----------------

    /** Writes a CSV file (creating parent directories). */
    public static final class Csv implements AutoCloseable {
        private final PrintWriter out;
        public Csv(String path, String header) throws IOException {
            Path p = Paths.get(path);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            out = new PrintWriter(Files.newBufferedWriter(p));
            out.println(header);
        }
        public void row(Object... cells) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) sb.append(',');
                Object c = cells[i];
                if (c instanceof Double) sb.append(String.format("%.3f", (Double) c));
                else sb.append(c);
            }
            out.println(sb);
        }
        @Override public void close() { out.close(); }
    }

    public static String env() {
        return "Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vm.name") + "), "
                + System.getProperty("os.name") + " " + System.getProperty("os.arch") + ", "
                + Runtime.getRuntime().availableProcessors() + " CPU(s), max heap "
                + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB";
    }

    public static double ms(double ns) { return ns / 1e6; }
}
