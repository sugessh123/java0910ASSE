import java.io.IOException;
import java.util.StringJoiner;

/**
 * Q1 - Railway e-ticket generation: String (+) vs StringBuilder vs StringBuffer
 *      (plus the alternatives String.join, StringJoiner and String.format).
 *
 * Produces:  data/q1_workloads.csv   (three designed workloads)
 *            data/q1_scaling.csv     (one ticket, n fields from 10 to 20 000)
 *            data/q1_model.csv       (analytical operation counts)
 */
public class TicketBenchmark {

    static final String[] APPROACHES = {"plus", "StringBuilder", "StringBuffer", "String.join", "StringJoiner", "String.format"};

    // ---------- the six ways of building ONE ticket from n pre-built field strings ----------

    static String plus(String[] f) {
        String s = "";
        for (String x : f) s = s + x + "\n";          // new String every iteration (O(n^2) chars copied)
        return s;
    }
    static String builder(String[] f) {
        StringBuilder sb = new StringBuilder();
        for (String x : f) sb.append(x).append('\n');
        return sb.toString();
    }
    static String buffer(String[] f) {
        StringBuffer sb = new StringBuffer();
        for (String x : f) sb.append(x).append('\n'); // same code, synchronized methods
        return sb.toString();
    }
    static String join(String[] f) {
        return String.join("\n", f) + "\n";
    }
    static String joiner(String[] f) {
        StringJoiner j = new StringJoiner("\n", "", "\n");
        for (String x : f) j.add(x);
        return j.toString();
    }
    static String format(String[] f) {
        return String.format("%s\n".repeat(f.length), (Object[]) f);
    }

    static String build(int approach, String[] f) {
        switch (approach) {
            case 0: return plus(f);
            case 1: return builder(f);
            case 2: return buffer(f);
            case 3: return join(f);
            case 4: return joiner(f);
            default: return format(f);
        }
    }

    /** Deterministic ticket fields, roughly 20 characters each (PNR, name, coach, berth, fare ...). */
    static String[] fields(int n) {
        String[] f = new String[n];
        for (int i = 0; i < n; i++) f[i] = "Field" + i + "=Value" + ((i * 7919) % 100000);
        return f;
    }

    // ---------- analytical model: characters copied and intermediate objects ----------

    /** Total characters copied by repeated '+' = sum of the length after each iteration. */
    static long charsCopiedPlus(String[] f) {
        long total = 0, len = 0;
        for (String x : f) { len += x.length() + 1; total += len; }
        return total;
    }
    /** StringBuilder: characters appended + characters copied during array growth + final toString copy. */
    static long[] charsCopiedBuilder(String[] f) {          // returns {copied, resizes}
        long cap = 16, len = 0, copied = 0, resizes = 0;
        for (String x : f) {
            for (int part = 0; part < 2; part++) {
                int add = part == 0 ? x.length() : 1;
                long need = len + add;
                if (need > cap) {                            // grow: newCap = max(need, 2*cap+2)
                    cap = Math.max(need, cap * 2 + 2);
                    copied += len;
                    resizes++;
                }
                len += add;
                copied += add;                               // the append itself
            }
        }
        copied += len;                                       // toString() copy
        return new long[]{copied, resizes};
    }

    // ---------- one workload = `tickets` tickets of `n` fields ----------

    static long workload(int approach, String[] f, int tickets) {
        long acc = 0;
        for (int t = 0; t < tickets; t++) acc += build(approach, f).length();
        return acc;
    }

    static void verifyAllApproachesAgree() {
        for (int n : new int[]{1, 8, 50, 333}) {
            String[] f = fields(n);
            String ref = plus(f);
            for (int a = 1; a < APPROACHES.length; a++)
                if (!ref.equals(build(a, f))) throw new AssertionError(APPROACHES[a] + " differs for n=" + n);
        }
        System.out.println("[check] all 6 approaches produce byte-identical tickets for n = 1, 8, 50, 333");
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Environment: " + Bench.env());
        verifyAllApproachesAgree();

        // ---- the three designed workloads -------------------------------------------------
        // W1 short ticket (very many), W2 the "typical busy day" from the brief, W3 my own: long manifests.
        int[][] workloads = { {8, 100_000}, {50, 10_000}, {2_000, 100} };
        String[] wname = { "W1 short ticket (8 fields x 100000 tickets)",
                           "W2 busy day (50 fields x 10000 tickets)",
                           "W3 own: group manifest (2000 fields x 100 tickets)" };

        try (Bench.Csv csv = new Bench.Csv("data/q1_workloads.csv",
                "workload,fields,tickets,approach,median_ms,mean_ms,sd_ms,cv_percent,us_per_ticket,alloc_MB,intermediate_strings,chars_copied_per_ticket,heap_before_MB,heap_after_nogc_MB,runs")) {
            for (int w = 0; w < workloads.length; w++) {
                int n = workloads[w][0], tickets = workloads[w][1];
                String[] f = fields(n);
                System.out.println("\n=== " + wname[w] + " ===");
                System.out.printf("%-14s %10s %10s %8s %10s %12s %14s%n",
                        "approach", "median ms", "sd ms", "CV %", "us/ticket", "alloc MB", "chars copied/tkt");
                for (int a = 0; a < APPROACHES.length; a++) {
                    final int ap = a;
                    int warm = (w == 2) ? 2 : 4;
                    int runs = (w == 2) ? 7 : 10;
                    Bench.Stat s = Bench.time(warm, runs, () -> workload(ap, f, tickets));
                    long alloc = Bench.allocatedBytes(() -> workload(ap, f, tickets));
                    long heapBefore = Bench.usedHeap();
                    Bench.sink += workload(ap, f, tickets);
                    Runtime r = Runtime.getRuntime();
                    long heapAfter = r.totalMemory() - r.freeMemory();     // garbage not collected yet
                    long intermediate = (a == 0) ? (long) n * tickets : 0;  // '+' leaves n-1 garbage Strings per ticket
                    long copied = (a == 0) ? charsCopiedPlus(f) : (a == 1 || a == 2) ? charsCopiedBuilder(f)[0] : -1;
                    System.out.printf("%-14s %10.3f %10.3f %8.1f %10.3f %12.1f %14s%n",
                            APPROACHES[a], Bench.ms(s.median), Bench.ms(s.sd), s.cvPercent(),
                            s.median / 1e3 / tickets, alloc / 1048576.0, copied < 0 ? "n/a" : String.valueOf(copied));
                    csv.row(wname[w].substring(0, 2), n, tickets, APPROACHES[a], Bench.ms(s.median), Bench.ms(s.mean),
                            Bench.ms(s.sd), s.cvPercent(), s.median / 1e3 / tickets, alloc / 1048576.0, intermediate,
                            copied, heapBefore / 1048576.0, heapAfter / 1048576.0, s.runs);
                }
            }
        }

        // ---- scaling sweep: one ticket, growing n -------------------------------------------
        int[] sizes = {10, 50, 100, 500, 1000, 2000, 5000, 10000, 20000};
        System.out.println("\n=== Scaling sweep: microseconds to build ONE ticket ===");
        System.out.printf("%8s", "n");
        for (String a : APPROACHES) System.out.printf("%15s", a);
        System.out.println();
        try (Bench.Csv csv = new Bench.Csv("data/q1_scaling.csv", "n,approach,median_us,mean_us,sd_us,cv_percent,runs")) {
            for (int n : sizes) {
                String[] f = fields(n);
                System.out.printf("%8d", n);
                for (int a = 0; a < APPROACHES.length; a++) {
                    final int ap = a;
                    int reps = Math.max(1, 200_000 / n / 10);             // keep each run comparable in length
                    int runs = n >= 10000 ? 7 : 10;
                    Bench.Stat s = Bench.time(3, runs, () -> workload(ap, f, reps));
                    double us = s.median / 1e3 / reps;
                    System.out.printf("%15.2f", us);
                    csv.row(n, APPROACHES[a], us, s.mean / 1e3 / reps, s.sd / 1e3 / reps, s.cvPercent(), s.runs);
                }
                System.out.println();
            }
        }

        // ---- analytical model (operation counts are the primary evidence) -------------------
        try (Bench.Csv csv = new Bench.Csv("data/q1_model.csv",
                "n,chars_copied_plus,chars_copied_builder,ratio,builder_resizes,intermediate_strings_plus")) {
            System.out.println("\n=== Analytical model: characters copied per ticket ===");
            System.out.printf("%8s %18s %18s %10s %10s%n", "n", "plus (n^2)", "StringBuilder", "ratio", "resizes");
            for (int n : new int[]{8, 50, 100, 500, 1000, 2000, 5000, 10000, 20000}) {
                String[] f = fields(n);
                long p = charsCopiedPlus(f);
                long[] b = charsCopiedBuilder(f);
                System.out.printf("%8d %18d %18d %10.1f %10d%n", n, p, b[0], (double) p / b[0], b[1]);
                csv.row(n, p, b[0], (double) p / b[0], b[1], n);
            }
        }
        System.out.println("\n(sink=" + Bench.sink + ")");
    }
}
