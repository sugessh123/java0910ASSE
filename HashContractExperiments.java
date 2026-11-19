import java.io.IOException;
import java.util.*;

/**
 * Q3 - hashCode / equals contract experiments.
 *
 * Each test states its PREDICTED set size in the source before the code runs, then prints PREDICTED, OBSERVED
 * and whether they agree. T4 (constant hash) and the "Alternative" benchmark write CSV files in data/.
 */
public class HashContractExperiments {

    // ----------------------------------------------------------------------------------------------
    // The junior developer's class from the brief: equals() on regNo, hashCode() NOT overridden.
    static class JuniorStudent {
        String regNo, name;
        JuniorStudent(String regNo, String name) { this.regNo = regNo; this.name = name; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof JuniorStudent)) return false;
            return regNo.equals(((JuniorStudent) o).regNo);
        }
        @Override public String toString() { return regNo + " " + name; }
    }

    // The corrected class: immutable, equals and hashCode use the SAME single field.
    static final class FixedStudent {
        private final String regNo, name;
        FixedStudent(String regNo, String name) { this.regNo = Objects.requireNonNull(regNo); this.name = name; }
        String regNo() { return regNo; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FixedStudent)) return false;
            return regNo.equals(((FixedStudent) o).regNo);
        }
        @Override public int hashCode() { return regNo.hashCode(); }
        @Override public String toString() { return regNo + " " + name; }
    }

    // Mutable key: hashCode/equals are correct but regNo is NOT final -> dangerous inside a HashSet.
    static class MutableStudent {
        String regNo; final String name;
        MutableStudent(String regNo, String name) { this.regNo = regNo; this.name = name; }
        @Override public boolean equals(Object o) { return o instanceof MutableStudent && regNo.equals(((MutableStudent) o).regNo); }
        @Override public int hashCode() { return regNo.hashCode(); }
        @Override public String toString() { return regNo + " " + name; }
    }

    // hashCode uses a DIFFERENT (larger) set of fields than equals -> violates "equal objects, equal hashes".
    static final class InconsistentStudent {
        final String regNo, name;
        InconsistentStudent(String regNo, String name) { this.regNo = regNo; this.name = name; }
        @Override public boolean equals(Object o) { return o instanceof InconsistentStudent && regNo.equals(((InconsistentStudent) o).regNo); }
        @Override public int hashCode() { return Objects.hash(regNo, name); }
    }

    // compareTo orders by NAME only: inconsistent with equals (which uses regNo).
    static final class NameOrderedStudent implements Comparable<NameOrderedStudent> {
        final String regNo, name;
        NameOrderedStudent(String regNo, String name) { this.regNo = regNo; this.name = name; }
        @Override public int compareTo(NameOrderedStudent o) { return name.compareTo(o.name); }
        @Override public boolean equals(Object o) { return o instanceof NameOrderedStudent && regNo.equals(((NameOrderedStudent) o).regNo); }
        @Override public int hashCode() { return regNo.hashCode(); }
    }

    // Constant hash: always legal, never fast.
    static final class ConstantHashStudent {
        final String regNo;
        ConstantHashStudent(String regNo) { this.regNo = regNo; }
        @Override public boolean equals(Object o) { return o instanceof ConstantHashStudent && regNo.equals(((ConstantHashStudent) o).regNo); }
        @Override public int hashCode() { return 1; }
    }

    // Instrumented class: counts how often HashSet calls hashCode() and equals().
    static final class CountingStudent {
        static int hashCalls, equalsCalls;
        final String regNo;
        CountingStudent(String regNo) { this.regNo = regNo; }
        @Override public boolean equals(Object o) { equalsCalls++; return o instanceof CountingStudent && regNo.equals(((CountingStudent) o).regNo); }
        @Override public int hashCode() { hashCalls++; return regNo.hashCode(); }
    }

    // ----------------------------------------------------------------------------------------------
    static int passed = 0, total = 0;

    static void check(String test, String what, Object predicted, Object observed) {
        total++;
        boolean ok = predicted.equals(observed);
        if (ok) passed++;
        System.out.printf("  %-4s %-62s predicted=%-6s observed=%-6s %s%n", test, what, predicted, observed, ok ? "MATCH" : "** MISMATCH **");
    }

    static final String[] REG = {"192311001", "192311002", "192311003", "192311004", "192311005"};
    static final String[] NAME = {"Arun", "Divya", "Karthik", "Meena", "Rahul"};

    /**
     * Exact container overhead in bytes per unique student for {HashSet, TreeSet, HashMap}.
     * Method: the JVM's exact per-thread allocation counter. A TreeSet allocates exactly one node per element and no garbage.
     * HashSet/HashMap are created pre-sized to the table size that normal growth would end with (smallest power of two
     * >= n / 0.75), so nothing is allocated and thrown away by resizing: allocated bytes = retained bytes.
     * (A before/after "used heap" difference was tried first and rejected: it gave inconsistent results, see README.)
     * The Student objects are created beforehand, so only the container is counted.
     */
    static double[] spaceBytes(int n) {
        FixedStudent[] first = new FixedStudent[n];
        for (int i = 0; i < n; i++) first[i] = new FixedStudent("19" + (2_311_000 + i), "n");
        Comparator<FixedStudent> byReg = Comparator.comparing(FixedStudent::regNo);
        int cap = (int) (n / 0.75f) + 1;
        double h = Bench.allocatedBytes(() -> { Set<FixedStudent> s = new HashSet<>(cap); for (FixedStudent f : first) s.add(f); return s.size(); }) / (double) n;
        double t = Bench.allocatedBytes(() -> { Set<FixedStudent> s = new TreeSet<>(byReg); for (FixedStudent f : first) s.add(f); return s.size(); }) / (double) n;
        double m = Bench.allocatedBytes(() -> { Map<String, FixedStudent> s = new HashMap<>(cap); for (FixedStudent f : first) s.put(f.regNo(), f); return s.size(); }) / (double) n;
        return new double[]{h, t, m};
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Environment: " + Bench.env());

        System.out.println("\n=== T1: cases where the buggy HashSet APPEARS to work ===");
        Set<JuniorStudent> a = new HashSet<>();
        for (int i = 0; i < 5; i++) a.add(new JuniorStudent(REG[i], NAME[i]));
        check("T1a", "5 scans, 5 different students (junior class)", 5, a.size());
        Set<JuniorStudent> b = new HashSet<>();
        JuniorStudent[] cards = new JuniorStudent[5];
        for (int i = 0; i < 5; i++) { cards[i] = new JuniorStudent(REG[i], NAME[i]); b.add(cards[i]); }
        for (int k = 0; k < 3; k++) b.add(cards[2]);                      // the SAME object scanned again
        check("T1b", "same object re-scanned 3 times (junior class)", 5, b.size());

        System.out.println("\n=== T2: scans designed to expose duplicates ===");
        String[] scans = {REG[0], REG[1], REG[2], REG[0], REG[3], REG[1]};   // 4 distinct, 2 repeated
        Set<JuniorStudent> c = new HashSet<>();
        Set<FixedStudent> cFixed = new HashSet<>();
        for (int i = 0; i < scans.length; i++) {
            c.add(new JuniorStudent(scans[i], "s" + i));
            cFixed.add(new FixedStudent(scans[i], "s" + i));
        }
        check("T2", "6 scans, 4 distinct regNo (junior class, correct = 4)", 6, c.size());
        check("T2b", "same 6 scans with hashCode() on regNo (fixed class)", 4, cFixed.size());
        int dup = 0;
        for (int trial = 0; trial < 1000; trial++) {
            Set<JuniorStudent> s = new HashSet<>();
            for (String r : scans) s.add(new JuniorStudent(r, "x"));
            if (s.size() == 6) dup++;
        }
        check("T2c", "trials (of 1000) where ALL duplicates survived", 1000, dup);
        System.out.println("       contents of the junior set: " + c);

        System.out.println("\n=== T3: additional tests of my own ===");
        // T3a: mutate a field used by hashCode() after insertion
        Set<MutableStudent> m = new HashSet<>();
        MutableStudent s1 = new MutableStudent("192311001", "Arun");
        m.add(s1);
        s1.regNo = "192399999";                                              // student card re-issued -> field changed in place
        check("T3a", "size after mutating regNo", 1, m.size());
        check("T3a", "contains(the same object)", false, m.contains(s1));
        check("T3a", "contains(new Student with the ORIGINAL regNo)", false, m.contains(new MutableStudent("192311001", "Arun")));
        check("T3a", "remove(the same object)", false, m.remove(s1));
        System.out.println("       iterating still shows the ghost entry: " + m);
        // T3b: TreeSet whose compareTo disagrees with equals
        Set<NameOrderedStudent> ts = new TreeSet<>();
        Set<NameOrderedStudent> hs = new HashSet<>();
        NameOrderedStudent p = new NameOrderedStudent("192311010", "Arun"), q = new NameOrderedStudent("192311011", "Arun");
        ts.add(p); ts.add(q); hs.add(p); hs.add(q);
        check("T3b", "TreeSet size, two students with same name (equals: different)", 1, ts.size());
        check("T3b", "HashSet size, same two students", 2, hs.size());
        // T3c: hashCode uses more fields than equals
        Set<InconsistentStudent> inc = new HashSet<>();
        inc.add(new InconsistentStudent("192311001", "Arun"));
        inc.add(new InconsistentStudent("192311001", "ARUN K"));            // same regNo -> equals() says SAME student
        check("T3c", "same regNo, different name spelling, hash on (regNo,name)", 2, inc.size());

        System.out.println("\n=== T5: mechanism - how often does HashSet call hashCode() and equals()? ===");
        Set<CountingStudent> cs = new HashSet<>();
        CountingStudent.hashCalls = CountingStudent.equalsCalls = 0;
        cs.add(new CountingStudent("192311001"));
        check("T5", "add NEW student: hashCode() calls", 1, CountingStudent.hashCalls);
        check("T5", "add NEW student: equals() calls", 0, CountingStudent.equalsCalls);
        CountingStudent.hashCalls = CountingStudent.equalsCalls = 0;
        cs.add(new CountingStudent("192311001"));
        check("T5", "add DUPLICATE: hashCode() calls", 1, CountingStudent.hashCalls);
        check("T5", "add DUPLICATE: equals() calls", 1, CountingStudent.equalsCalls);
        check("T5", "set size after duplicate add", 1, cs.size());

        // contract verification of the corrected class (reflexive, symmetric, transitive, consistent, null, hash)
        FixedStudent x = new FixedStudent("192311001", "Arun"), y = new FixedStudent("192311001", "ARUN"), z = new FixedStudent("192311001", "A");
        boolean contract = x.equals(x) && x.equals(y) == y.equals(x) && (!(x.equals(y) && y.equals(z)) || x.equals(z))
                && !x.equals(null) && x.hashCode() == y.hashCode() && x.hashCode() == x.hashCode();
        check("T6", "corrected class satisfies equals/hashCode contract checks", true, contract);

        // T4: constant hash code: still correct, but slow
        System.out.println("\n=== T4: constant hashCode() = 1 (legal but slow): time to add n distinct students ===");
        System.out.printf("%8s %18s %18s %10s%n", "n", "good hash (ms)", "constant hash (ms)", "slow-down");
        try (Bench.Csv csv = new Bench.Csv("data/q3_constant_hash.csv", "n,good_hash_ms,constant_hash_ms,slowdown,size_good,size_constant")) {
            for (int n : new int[]{1000, 2000, 5000, 10000, 20000}) {
                ConstantHashStudent[] bad = new ConstantHashStudent[n];
                FixedStudent[] good = new FixedStudent[n];
                for (int i = 0; i < n; i++) { bad[i] = new ConstantHashStudent("R" + i); good[i] = new FixedStudent("R" + i, "n"); }
                Bench.Stat sg = Bench.time(3, 7, () -> { Set<FixedStudent> s = new HashSet<>(); for (FixedStudent f : good) s.add(f); return s.size(); });
                Bench.Stat sb = Bench.time(2, 5, () -> { Set<ConstantHashStudent> s = new HashSet<>(); for (ConstantHashStudent f : bad) s.add(f); return s.size(); });
                Set<ConstantHashStudent> chk = new HashSet<>(Arrays.asList(bad));
                System.out.printf("%8d %18.3f %18.3f %9.0fx%n", n, Bench.ms(sg.median), Bench.ms(sb.median), sb.median / sg.median);
                csv.row(n, Bench.ms(sg.median), Bench.ms(sb.median), sb.median / sg.median, n, chk.size());
            }
        }

        // ---------------- Alternative concept: HashSet vs TreeSet(comparator) vs HashMap<String,Student> -----------------
        // Space: exact allocation counters (see spaceBytes()).
        java.util.Map<Integer, double[]> space = new java.util.HashMap<>();
        for (int n : new int[]{10_000, 100_000, 1_000_000}) space.put(n, spaceBytes(n));

        System.out.println("\n=== ALTERNATIVE: de-duplication structures. Each of n unique students is scanned twice ===");
        System.out.printf("%9s | %-22s %12s %14s %16s%n", "n unique", "structure", "ns / scan", "compare/equals per scan", "bytes / unique");
        try (Bench.Csv csv = new Bench.Csv("data/q3_dedup_structures.csv", "n_unique,structure,ns_per_scan,cv_percent,ops_per_scan,bytes_per_unique,final_size")) {
            for (int n : new int[]{10_000, 100_000, 1_000_000}) {
                FixedStudent[] first = new FixedStudent[n], again = new FixedStudent[n];
                for (int i = 0; i < n; i++) { String r = "19" + (2_311_000 + i); first[i] = new FixedStudent(r, "n"); again[i] = new FixedStudent(r, "n"); }
                int totalScans = 2 * n;
                Comparator<FixedStudent> byReg = Comparator.comparing(FixedStudent::regNo);

                // counting comparator for TreeSet (counts compare() calls) and counting equals for HashSet
                long[] cmpCount = {0};
                Comparator<FixedStudent> counting = (u, v) -> { cmpCount[0]++; return u.regNo().compareTo(v.regNo()); };

                int runs = n >= 1_000_000 ? 5 : 9;
                int warm = n >= 1_000_000 ? 3 : n >= 100_000 ? 8 : 40;
                Bench.Stat sh = Bench.time(warm, runs, () -> { Set<FixedStudent> s = new HashSet<>(); for (FixedStudent f : first) s.add(f); for (FixedStudent f : again) s.add(f); return s.size(); });
                Bench.Stat st = Bench.time(warm, runs, () -> { Set<FixedStudent> s = new TreeSet<>(byReg); for (FixedStudent f : first) s.add(f); for (FixedStudent f : again) s.add(f); return s.size(); });
                Bench.Stat sm = Bench.time(warm, runs, () -> { Map<String, FixedStudent> s = new HashMap<>(); for (FixedStudent f : first) s.putIfAbsent(f.regNo(), f); for (FixedStudent f : again) s.putIfAbsent(f.regNo(), f); return s.size(); });

                Set<FixedStudent> tc = new TreeSet<>(counting);
                for (FixedStudent f : first) tc.add(f);
                for (FixedStudent f : again) tc.add(f);
                double cmpPerScan = cmpCount[0] / (double) totalScans;
                double theory = Math.log(n) / Math.log(2);

                double[] by = space.get(n);               // measured in the pre-pass
                String[] nm = {"HashSet<Student>", "TreeSet<Student>+Comparator", "HashMap<String,Student>"};
                double[] ns = {sh.median / totalScans, st.median / totalScans, sm.median / totalScans};
                double[] cv = {sh.cvPercent(), st.cvPercent(), sm.cvPercent()};
                String[] ops = {"~1 hashCode + <=1 equals", String.format("%.1f compare() (log2 n = %.1f)", cmpPerScan, theory), "~1 hashCode + <=1 equals"};
                for (int i = 0; i < 3; i++) {
                    System.out.printf("%9d | %-27s %10.1f   %-34s %8.1f%n", n, nm[i], ns[i], ops[i], by[i]);
                    csv.row(n, nm[i], ns[i], cv[i], i == 1 ? String.format("%.2f", cmpPerScan) : "1", by[i], n);
                }
            }
        }
        System.out.printf("%n%d of %d predictions matched the observation.%n", passed, total);
        System.out.println("(sink=" + Bench.sink + ")");
    }
}
