import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Q4 - Hospital pharmacy stock: HashMap vs TreeMap vs LinkedHashMap (+ Hashtable and a sorted array with binarySearch).
 *
 * Requirements: R1 stock lookup by code; R2 evening report in CODE order; R3 evening list in INSERTION order.
 * Writes data/q4_*.csv
 */
public class PharmacyBenchmark {

    static final class Medicine {
        final String code, name; int stock; final int seq;
        Medicine(String code, String name, int stock, int seq) { this.code = code; this.name = name; this.stock = stock; this.seq = seq; }
    }

    static final String[] MAPS = {"HashMap", "TreeMap", "LinkedHashMap", "Hashtable"};

    /** n medicines arriving in RANDOM code order, so insertion order differs from code order. */
    static Medicine[] makeMeds(int n) {
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        Random r = new Random(1);
        for (int i = n - 1; i > 0; i--) { int j = r.nextInt(i + 1); int t = perm[i]; perm[i] = perm[j]; perm[j] = t; }
        Medicine[] m = new Medicine[n];
        for (int i = 0; i < n; i++) m[i] = new Medicine(String.format("MED%07d", perm[i]), "drug" + perm[i], 100 + perm[i] % 50, i);
        return m;
    }

    static Map<String, Medicine> build(int type, Medicine[] meds) {
        Map<String, Medicine> m = type == 0 ? new HashMap<>() : type == 1 ? new TreeMap<>() : type == 2 ? new LinkedHashMap<>() : new Hashtable<>();
        for (Medicine x : meds) m.put(x.code, x);
        return m;
    }

    static final Comparator<Medicine> BY_CODE = Comparator.comparing(m -> m.code);

    // ----------------------------------------------------------------------------------------------
    // reports: "native" = plain iteration, "extra" = what must be added when the map does not give the order
    static List<String> codeOrderReport(int type, Map<String, Medicine> m) {
        List<String> out;
        if (type == 1) { out = new ArrayList<>(m.size()); for (String c : m.keySet()) out.add(c); }      // TreeMap: already sorted
        else { out = new ArrayList<>(m.keySet()); Collections.sort(out); }                                   // others: sort, O(n log n)
        return out;
    }
    static List<String> codeOrderNative(Map<String, Medicine> m) {                                          // plain iteration, whatever the map
        List<String> out = new ArrayList<>(m.size()); for (String c : m.keySet()) out.add(c); return out;
    }
    static List<String> insertionReport(int type, Map<String, Medicine> m) {
        List<String> out;
        if (type == 2) { out = new ArrayList<>(m.size()); for (String c : m.keySet()) out.add(c); }        // LinkedHashMap: already in order
        else {                                                                                               // others: need the stored sequence number
            List<Medicine> l = new ArrayList<>(m.values());
            l.sort(Comparator.comparingInt(x -> x.seq));
            out = new ArrayList<>(l.size()); for (Medicine x : l) out.add(x.code);
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Environment: " + Bench.env());
        int[] sizes = {1_000, 10_000, 100_000, 1_000_000};
        final int LOOKUPS = 100_000;

        try (Bench.Csv put = new Bench.Csv("data/q4_put.csv", "n,structure,ns_per_put,cv_percent");
             Bench.Csv look = new Bench.Csv("data/q4_lookup.csv", "n,structure,ms_for_100000_lookups,ns_per_lookup,cv_percent,p50_ns,p99_ns,p999_ns,max_ns");
             Bench.Csv iter = new Bench.Csv("data/q4_iterate.csv", "n,structure,ns_per_element");
             Bench.Csv rep = new Bench.Csv("data/q4_reports.csv", "n,structure,report,approach,ms,order_matches_requirement");
             Bench.Csv ins = new Bench.Csv("data/q4_insert_new.csv", "n,structure,ns_per_insert")) {

            for (int n : sizes) {
                System.out.printf("%n################  n = %,d medicines  ################%n", n);
                Medicine[] meds = makeMeds(n);
                Random rnd = new Random(42);
                String[] keys = new String[LOOKUPS];
                Medicine[] probes = new Medicine[LOOKUPS];
                for (int i = 0; i < LOOKUPS; i++) { keys[i] = meds[rnd.nextInt(n)].code; probes[i] = new Medicine(keys[i], "", 0, 0); }
                int warmP = n >= 1_000_000 ? 2 : 6, runsP = n >= 1_000_000 ? 5 : 9;
                int warmL = n >= 1_000_000 ? 4 : 15, runsL = 9;

                // expected orders, computed independently of any map
                List<String> expectedCode = new ArrayList<>(); for (Medicine x : meds) expectedCode.add(x.code); Collections.sort(expectedCode);
                List<String> expectedInsert = new ArrayList<>(); for (Medicine x : meds) expectedInsert.add(x.code);

                // ---- put ----
                System.out.printf("%-14s %12s %12s %14s %10s %10s %10s%n", "structure", "ns/put", "ns/lookup", "100k lookups ms", "p50 ns", "p99 ns", "p99.9 ns");
                Map<String, Medicine>[] maps = new Map[4];
                for (int t = 0; t < 4; t++) {
                    final int tt = t;
                    Bench.Stat sp = Bench.time(warmP, runsP, () -> build(tt, meds).size());
                    maps[t] = build(t, meds);
                    Map<String, Medicine> mp = maps[t];
                    put.row(n, MAPS[t], sp.median / n, sp.cvPercent());

                    // ---- 100 000 lookups ----
                    Bench.Stat sl = Bench.time(warmL, runsL, () -> { long a = 0; for (String k : keys) a += mp.get(k).stock; return a; });
                    // latency distribution: time batches of 100 lookups, report ns per lookup
                    int batches = 1000; double[] lat = new double[batches];
                    for (int b = 0; b < batches; b++) {
                        int off = (b * 100) % (LOOKUPS - 100); long a = 0;
                        long t0 = System.nanoTime();
                        for (int i = 0; i < 100; i++) a += mp.get(keys[off + i]).stock;
                        lat[b] = (System.nanoTime() - t0) / 100.0; Bench.sink += a;
                    }
                    Arrays.sort(lat);
                    double p50 = lat[batches / 2], p99 = lat[(int) (batches * 0.99)], p999 = lat[(int) (batches * 0.999)], max = lat[batches - 1];
                    look.row(n, MAPS[t], Bench.ms(sl.median), sl.median / LOOKUPS, sl.cvPercent(), p50, p99, p999, max);
                    System.out.printf("%-14s %12.1f %12.1f %14.2f %10.1f %10.1f %10.1f%n", MAPS[t], sp.median / n, sl.median / LOOKUPS, Bench.ms(sl.median), p50, p99, p999);

                    // ---- iteration ----
                    Bench.Stat si = Bench.time(warmL, runsL, () -> { long a = 0; for (Medicine x : mp.values()) a += x.stock; return a; });
                    iter.row(n, MAPS[t], si.median / n);
                }

                // ---- sorted array + binarySearch ----
                Medicine[] sorted = meds.clone(); Arrays.sort(sorted, BY_CODE);
                Bench.Stat sps = Bench.time(warmP, runsP, () -> { Medicine[] c = meds.clone(); Arrays.sort(c, BY_CODE); return c.length; });
                Bench.Stat sls = Bench.time(warmL, runsL, () -> { long a = 0; for (Medicine p : probes) a += sorted[Arrays.binarySearch(sorted, p, BY_CODE)].stock; return a; });
                put.row(n, "SortedArray(build)", sps.median / n, sps.cvPercent());
                look.row(n, "SortedArray", Bench.ms(sls.median), sls.median / LOOKUPS, sls.cvPercent(), "", "", "", "");
                System.out.printf("%-14s %12.1f %12.1f %14.2f   (build = clone + sort)%n", "SortedArray", sps.median / n, sls.median / LOOKUPS, Bench.ms(sls.median));
                Bench.Stat sia = Bench.time(warmL, runsL, () -> { long a = 0; for (Medicine x : sorted) a += x.stock; return a; });
                iter.row(n, "SortedArray", sia.median / n);

                // ---- reports (time, and does the plain iteration order already satisfy the requirement?) ----
                System.out.printf("%n  Reports (ms)   %-14s %-26s %-26s%n", "structure", "code-order report", "insertion-order report");
                for (int t = 0; t < 4; t++) {
                    final int tt = t; Map<String, Medicine> mp = maps[t];
                    boolean nativeCode = codeOrderNative(mp).equals(expectedCode);
                    boolean nativeIns = codeOrderNative(mp).equals(expectedInsert);
                    Bench.Stat sc = Bench.time(warmL / 3, 5, () -> codeOrderReport(tt, mp).size());
                    Bench.Stat sn = Bench.time(warmL / 3, 5, () -> insertionReport(tt, mp).size());
                    boolean okC = codeOrderReport(t, mp).equals(expectedCode), okI = insertionReport(t, mp).equals(expectedInsert);
                    rep.row(n, MAPS[t], "code-order", t == 1 ? "plain iteration" : "copy keys + sort", Bench.ms(sc.median), okC);
                    rep.row(n, MAPS[t], "code-order", "plain iteration only", "", nativeCode);
                    rep.row(n, MAPS[t], "insertion-order", t == 2 ? "plain iteration" : "copy values + sort by seq", Bench.ms(sn.median), okI);
                    rep.row(n, MAPS[t], "insertion-order", "plain iteration only", "", nativeIns);
                    System.out.printf("                 %-14s %8.2f (plain order OK? %-5s)  %8.2f (plain order OK? %-5s)%n",
                            MAPS[t], Bench.ms(sc.median), nativeCode, Bench.ms(sn.median), nativeIns);
                    if (!okC || !okI) throw new AssertionError("report with extra work is still wrong for " + MAPS[t]);
                }

                // ---- inserting NEW medicines into an existing inventory of n ----
                int m = 1000;
                String[] newCodes = new String[m]; Medicine[] newMeds = new Medicine[m];
                Random r2 = new Random(5);
                for (int i = 0; i < m; i++) { newCodes[i] = String.format("NEW%07d", r2.nextInt(10_000_000)); newMeds[i] = new Medicine(newCodes[i], "", 0, 0); }
                Bench.Stat sh = Bench.timeWithSetup(warmP / 2 + 1, 5, () -> build(0, meds), x -> { for (Medicine q : newMeds) x.put(q.code, q); return x.size(); });
                Bench.Stat st = Bench.timeWithSetup(warmP / 2 + 1, 5, () -> build(1, meds), x -> { for (Medicine q : newMeds) x.put(q.code, q); return x.size(); });
                Bench.Stat sa = Bench.timeWithSetup(warmP / 2 + 1, 5, () -> { Medicine[] a = Arrays.copyOf(sorted, n + m); return a; }, a -> {
                    int size = n; long acc = 0;
                    for (Medicine q : newMeds) {
                        int pos = Arrays.binarySearch(a, 0, size, q, BY_CODE); if (pos < 0) pos = -pos - 1;
                        System.arraycopy(a, pos, a, pos + 1, size - pos); a[pos] = q; size++; acc += pos;
                    } return acc + size; });
                ins.row(n, "HashMap", sh.median / m); ins.row(n, "TreeMap", st.median / m); ins.row(n, "SortedArray", sa.median / m);
                System.out.printf("%n  Insert 1000 NEW medicines (ns/insert): HashMap %.0f | TreeMap %.0f | SortedArray %.0f%n", sh.median / m, st.median / m, sa.median / m);
            }
        }

        // ---------------- operation counts (primary evidence) ----------------
        System.out.println("\n=== Operation counts per successful lookup ===");
        try (Bench.Csv csv = new Bench.Csv("data/q4_opcounts.csv", "n,structure,operation,per_lookup,log2_n")) {
            for (int n : sizes) {
                Medicine[] meds = makeMeds(n); Random rnd = new Random(42);
                long[] cnt = {0};
                Comparator<String> counting = (a, b) -> { cnt[0]++; return a.compareTo(b); };
                TreeMap<String, Medicine> tm = new TreeMap<>(counting);
                for (Medicine x : meds) tm.put(x.code, x);
                cnt[0] = 0; int L = 100_000;
                for (int i = 0; i < L; i++) tm.get(meds[rnd.nextInt(n)].code);
                double tmc = cnt[0] / (double) L;
                Medicine[] sorted = meds.clone(); Arrays.sort(sorted, BY_CODE);
                cnt[0] = 0; Comparator<Medicine> cm = (a, b) -> { cnt[0]++; return a.code.compareTo(b.code); };
                for (int i = 0; i < L; i++) Arrays.binarySearch(sorted, meds[rnd.nextInt(n)], cm);
                double bsc = cnt[0] / (double) L;
                CKey.hash = CKey.eq = 0;
                HashMap<CKey, Integer> hm = new HashMap<>();
                for (Medicine x : meds) hm.put(new CKey(x.code), 1);
                CKey.hash = CKey.eq = 0;
                for (int i = 0; i < L; i++) hm.get(new CKey(meds[rnd.nextInt(n)].code));
                double eqc = CKey.eq / (double) L, hc = CKey.hash / (double) L;
                double lg = Math.log(n) / Math.log(2);
                System.out.printf("n=%9d  TreeMap compare()=%5.1f   binarySearch compare()=%5.1f   HashMap equals()=%4.2f hashCode()=%4.2f   (log2 n = %.1f)%n", n, tmc, bsc, eqc, hc, lg);
                csv.row(n, "TreeMap", "compare_calls", tmc, lg); csv.row(n, "SortedArray", "compare_calls", bsc, lg);
                csv.row(n, "HashMap", "equals_calls", eqc, lg); csv.row(n, "HashMap", "hashCode_calls", hc, lg);
            }
        }

        // ---------------- hot key vs random key (where does the remaining cost come from?) ----------------
        System.out.println("\n=== HashMap lookup: same hot key vs random keys (ns per lookup) ===");
        try (Bench.Csv csv = new Bench.Csv("data/q4_cache.csv", "n,hot_key_ns,random_keys_ns,ratio")) {
            for (int n : sizes) {
                Medicine[] meds = makeMeds(n); Map<String, Medicine> hm = build(0, meds);
                Random rnd = new Random(3); String[] rk = new String[100_000]; String[] hk = new String[100_000];
                String hot = meds[0].code; for (int i = 0; i < rk.length; i++) { rk[i] = meds[rnd.nextInt(n)].code; hk[i] = hot; }
                Bench.Stat sr = Bench.time(10, 9, () -> { long a = 0; for (String k : rk) a += hm.get(k).stock; return a; });
                Bench.Stat sh = Bench.time(10, 9, () -> { long a = 0; for (String k : hk) a += hm.get(k).stock; return a; });
                System.out.printf("n=%9d  hot key %6.1f ns   random keys %6.1f ns   ratio %.1fx%n", n, sh.median / 1e5, sr.median / 1e5, sr.median / sh.median);
                csv.row(n, sh.median / 1e5, sr.median / 1e5, sr.median / sh.median);
            }
        }

        // ---------------- Hashtable vs HashMap vs ConcurrentHashMap, 2 reader threads ----------------
        System.out.println("\n=== 2 reader threads x 2 000 000 lookups each, n = 100 000 (ms, median of 7) ===");
        try (Bench.Csv csv = new Bench.Csv("data/q4_concurrent.csv", "structure,ms_single_thread,ms_two_threads")) {
            Medicine[] meds = makeMeds(100_000);
            Map<String, Medicine> ht = build(3, meds), hm = build(0, meds), chm = new ConcurrentHashMap<>();
            for (Medicine x : meds) chm.put(x.code, x);
            String[] keys = new String[2_000_000]; Random rnd = new Random(9);
            for (int i = 0; i < keys.length; i++) keys[i] = meds[rnd.nextInt(meds.length)].code;
            String[] nm = {"Hashtable", "HashMap (read-only)", "ConcurrentHashMap"};
            Map<String, Medicine>[] ms = new Map[]{ht, hm, chm};
            for (int v = 0; v < 3; v++) {
                Map<String, Medicine> mp = ms[v];
                Bench.Stat one = Bench.time(2, 7, () -> { long a = 0; for (String k : keys) a += mp.get(k).stock; return a; });
                Bench.Stat two = Bench.time(2, 7, () -> {
                    long[] acc = new long[2];
                    Thread[] th = new Thread[2];
                    for (int i = 0; i < 2; i++) { final int id = i; th[i] = new Thread(() -> { long a = 0; for (String k : keys) a += mp.get(k).stock; acc[id] = a; }); th[i].start(); }
                    try { for (Thread t : th) t.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                    return acc[0] + acc[1]; });
                System.out.printf("%-22s 1 thread %8.1f ms    2 threads (each does the full %,d lookups) %8.1f ms%n", nm[v], Bench.ms(one.median), keys.length, Bench.ms(two.median));
                csv.row(nm[v], Bench.ms(one.median), Bench.ms(two.median));
            }
        }

        // ---------------- memory per entry (exact, pre-sized containers) ----------------
        System.out.println("\n=== Container overhead per entry (exact allocated bytes; Medicine and String objects excluded) ===");
        try (Bench.Csv csv = new Bench.Csv("data/q4_space.csv", "structure,bytes_per_entry")) {
            for (int n : new int[]{100_000, 1_000_000}) {
                Medicine[] meds = makeMeds(n);
                int cap = (int) (n / 0.75f) + 1;
                double[] b = new double[5];
                b[0] = Bench.allocatedBytes(() -> { Map<String, Medicine> m = new HashMap<>(cap); for (Medicine x : meds) m.put(x.code, x); return m.size(); }) / (double) n;
                b[1] = Bench.allocatedBytes(() -> { Map<String, Medicine> m = new LinkedHashMap<>(cap); for (Medicine x : meds) m.put(x.code, x); return m.size(); }) / (double) n;
                b[2] = Bench.allocatedBytes(() -> { Map<String, Medicine> m = new TreeMap<>(); for (Medicine x : meds) m.put(x.code, x); return m.size(); }) / (double) n;
                b[3] = Bench.allocatedBytes(() -> { Map<String, Medicine> m = new Hashtable<>(cap); for (Medicine x : meds) m.put(x.code, x); return m.size(); }) / (double) n;
                b[4] = Bench.allocatedBytes(() -> meds.clone().length) / (double) n;
                String[] nm = {"HashMap", "LinkedHashMap", "TreeMap", "Hashtable", "SortedArray"};
                for (int i = 0; i < 5; i++) { System.out.printf("n=%9d  %-14s %6.1f bytes/entry%n", n, nm[i], b[i]); csv.row(nm[i] + "@" + n, b[i]); }
            }
        }
        System.out.println("\n(sink=" + Bench.sink + ")");
    }

    /** Key wrapper that counts hashCode()/equals() calls made by HashMap. */
    static final class CKey {
        static long hash, eq; final String s;
        CKey(String s) { this.s = s; }
        @Override public int hashCode() { hash++; return s.hashCode(); }
        @Override public boolean equals(Object o) { eq++; return o instanceof CKey && s.equals(((CKey) o).s); }
    }
}
