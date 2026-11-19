import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Q1 Design Decision - the booking server is multithreaded and several threads append to ONE shared booking log.
 *
 *  Experiment A: shared StringBuilder (not thread-safe)  -> do we lose data / crash?
 *  Experiment B: shared StringBuffer, entry appended in ONE call vs in SEVERAL calls -> are log lines torn?
 *  Experiment C: cost of the safe options under contention.
 *
 * Writes data/q1_threads.csv
 */
public class SharedLogDemo {
    static final int THREADS = 4;

    interface Task { void run(int id) throws Exception; }

    static void runThreads(Task t) throws Exception {
        Thread[] th = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            final int id = i;
            th[i] = new Thread(() -> { try { t.run(id); } catch (Exception e) { throw new RuntimeException(e); } });
        }
        for (Thread x : th) x.start();
        for (Thread x : th) x.join();
    }

    // ---------------- A: lost updates in an unsynchronised StringBuilder ----------------
    static int[] experimentA(int trials, int perThread) throws Exception {
        int lost = 0, crashed = 0;
        for (int tr = 0; tr < trials; tr++) {
            StringBuilder log = new StringBuilder();
            AtomicInteger errors = new AtomicInteger();
            Thread[] th = new Thread[THREADS];
            for (int i = 0; i < THREADS; i++) {
                final int id = i;
                th[i] = new Thread(() -> {
                    try { for (int k = 0; k < perThread; k++) log.append("T").append(id).append(':').append(k).append('\n'); }
                    catch (RuntimeException e) { errors.incrementAndGet(); }   // e.g. ArrayIndexOutOfBounds
                });
            }
            for (Thread x : th) x.start();
            for (Thread x : th) x.join();
            long expected = 0;
            for (int id = 0; id < THREADS; id++)
                for (int k = 0; k < perThread; k++) expected += 4 + Integer.toString(k).length();   // "T" + id(1 digit) + ":" + k + "\n"
            if (errors.get() > 0) crashed++;
            else if (log.length() != expected) lost++;
        }
        return new int[]{lost, crashed};
    }

    // ---------------- B: StringBuffer is safe per call, but a 5-call entry is not atomic ----------------
    static int[] experimentB(int perThread) throws Exception {
        Pattern ok = Pattern.compile("\\[T\\d\\] booking-\\d+");
        // multi-call entry
        StringBuffer multi = new StringBuffer();
        runThreads(id -> { for (int k = 0; k < perThread; k++)
            multi.append("[T").append(id).append("] booking-").append(k).append('\n'); });
        // single-call entry (line built locally, appended once)
        StringBuffer single = new StringBuffer();
        runThreads(id -> { for (int k = 0; k < perThread; k++) {
            String line = new StringBuilder(24).append("[T").append(id).append("] booking-").append(k).append('\n').toString();
            single.append(line); } });
        int tornMulti = 0, tornSingle = 0;
        for (String l : multi.toString().split("\n")) if (!ok.matcher(l).matches()) tornMulti++;
        for (String l : single.toString().split("\n")) if (!ok.matcher(l).matches()) tornSingle++;
        return new int[]{tornMulti, tornSingle, THREADS * perThread};
    }

    // ---------------- C: cost of the thread-safe alternatives ----------------
    static long timeC(int variant, int perThread) throws Exception {
        long t0 = System.nanoTime();
        switch (variant) {
            case 0: {                                       // shared StringBuffer, one append per entry
                StringBuffer log = new StringBuffer();
                runThreads(id -> { for (int k = 0; k < perThread; k++) log.append("T" + id + ":" + k + "\n"); });
                Bench.sink += log.length();
                break; }
            case 1: {                                       // shared StringBuilder guarded by an explicit lock
                StringBuilder log = new StringBuilder();
                Object lock = new Object();
                runThreads(id -> { for (int k = 0; k < perThread; k++) { String s = "T" + id + ":" + k + "\n";
                    synchronized (lock) { log.append(s); } } });
                Bench.sink += log.length();
                break; }
            case 2: {                                       // no sharing: one StringBuilder per thread, merged at the end
                StringBuilder[] part = new StringBuilder[THREADS];
                runThreads(id -> { StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < perThread; k++) sb.append('T').append(id).append(':').append(k).append('\n');
                    part[id] = sb; });
                StringBuilder all = new StringBuilder();
                for (StringBuilder p : part) all.append(p);
                Bench.sink += all.length();
                break; }
            default: {                                      // lock-free queue of entries
                ConcurrentLinkedQueue<String> q = new ConcurrentLinkedQueue<>();
                runThreads(id -> { for (int k = 0; k < perThread; k++) q.add("T" + id + ":" + k + "\n"); });
                Bench.sink += q.size();
            }
        }
        return System.nanoTime() - t0;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Environment: " + Bench.env());
        try (Bench.Csv csv = new Bench.Csv("data/q1_threads.csv", "experiment,variant,value,unit")) {

            int trials = 40, per = 50_000;
            int[] a = experimentA(trials, per);
            System.out.printf("%n[A] Shared StringBuilder, %d threads x %d appends, %d trials:%n", THREADS, per, trials);
            System.out.printf("    trials with LOST data      : %d / %d%n", a[0], trials);
            System.out.printf("    trials that THREW exception: %d / %d%n", a[1], trials);
            System.out.printf("    trials fully correct       : %d / %d%n", trials - a[0] - a[1], trials);
            csv.row("A", "StringBuilder_lost_data_trials", a[0], "of " + trials);
            csv.row("A", "StringBuilder_exception_trials", a[1], "of " + trials);

            StringBuffer sbuf = new StringBuffer();
            int bad = 0;
            for (int tr = 0; tr < trials; tr++) {
                StringBuffer log = new StringBuffer();
                runThreads(id -> { for (int k = 0; k < per; k++) log.append("T").append(id).append(':').append(k).append('\n'); });
                long expected = 0;
                for (int id = 0; id < THREADS; id++) for (int k = 0; k < per; k++) expected += 4 + Integer.toString(k).length();   // "T" + id + ":" + k + "\n"
                if (log.length() != expected) bad++;
            }
            System.out.printf("[A] Shared StringBuffer, same test: trials with lost data = %d / %d%n", bad, trials);
            csv.row("A", "StringBuffer_lost_data_trials", bad, "of " + trials);

            int[] b = experimentB(50_000);
            System.out.printf("%n[B] StringBuffer log, %d entries written by %d threads:%n", b[2], THREADS);
            System.out.printf("    entry appended with 5 separate append() calls -> torn/garbled lines: %d%n", b[0]);
            System.out.printf("    entry built locally, appended in ONE call       -> torn/garbled lines: %d%n", b[1]);
            csv.row("B", "StringBuffer_5_calls_torn_lines", b[0], "of " + b[2]);
            csv.row("B", "StringBuffer_1_call_torn_lines", b[1], "of " + b[2]);

            String[] names = {"shared StringBuffer", "shared StringBuilder + lock", "per-thread StringBuilder + merge", "ConcurrentLinkedQueue<String>"};
            int per2 = 200_000;
            System.out.printf("%n[C] Cost with %d threads x %d entries (median of 7 runs, ms):%n", THREADS, per2);
            for (int v = 0; v < 4; v++) {
                final int vv = v;
                Bench.Stat s = Bench.time(2, 7, () -> { try { return timeC(vv, per2); } catch (Exception e) { throw new RuntimeException(e); } });
                System.out.printf("    %-36s median %8.1f ms   sd %6.1f ms%n", names[v], Bench.ms(s.median), Bench.ms(s.sd));
                csv.row("C", names[v], Bench.ms(s.median), "ms_median");
            }
        }
    }
}
