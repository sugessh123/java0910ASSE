import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Q2 - Music playlist editor: ArrayList vs LinkedList vs ArrayDeque vs a plain array with manual shifting.
 *
 * Every operation is timed per element/operation at three playlist sizes (1 000, 10 000, 100 000).
 * Writes data/q2_operations.csv, data/q2_upnext.csv and data/q2_space.csv.
 */
public class PlaylistBenchmark {

    static final class Song {
        final int id;
        Song(int id) { this.id = id; }
    }

    static final int[] SIZES = {1_000, 10_000, 100_000};
    static final Song[] SONGS = new Song[1_200_000];          // created once; list cost excludes Song creation
    static { for (int i = 0; i < SONGS.length; i++) SONGS[i] = new Song(i); }

    // ------------------------------------------------------------------------------------------
    /** The "plain array with manual shifting" alternative asked for in the brief (no JDK collection). */
    static final class ArrayPlaylist {
        Song[] a;
        int size;
        ArrayPlaylist(int capacity) { a = new Song[capacity]; }
        void addEnd(Song s) { if (size == a.length) grow(); a[size++] = s; }
        void grow() { Song[] b = new Song[a.length * 2]; for (int i = 0; i < size; i++) b[i] = a[i]; a = b; }
        void insert(int i, Song s) {                           // manual shifting loop, O(n - i)
            if (size == a.length) grow();
            for (int j = size; j > i; j--) a[j] = a[j - 1];
            a[i] = s; size++;
        }
        Song remove(int i) {
            Song r = a[i];
            for (int j = i; j < size - 1; j++) a[j] = a[j + 1];
            a[--size] = null; return r;
        }
        Song get(int i) { return a[i]; }
    }

    static List<Song> filled(Supplier<List<Song>> make, int n) {
        List<Song> l = make.get();
        for (int i = 0; i < n; i++) l.add(SONGS[i]);
        return l;
    }
    static ArrayPlaylist filledArray(int n) {
        ArrayPlaylist p = new ArrayPlaylist(n + 5000);
        for (int i = 0; i < n; i++) p.addEnd(SONGS[i]);
        return p;
    }

    static final String[] TYPES = {"ArrayList", "LinkedList", "ArrayDeque", "PlainArray"};

    static Supplier<List<Song>> maker(int t) {
        return t == 0 ? ArrayList::new : LinkedList::new;
    }

    // ------------------------------------------------------------------------------------------
    static int opsFor(String op, int type, int n) {
        switch (op) {
            case "add_end":        return n;                                 // build the whole list
            case "insert_front":
            case "remove_front":
            case "insert_middle":
            case "remove_middle":  return (type == 1 && n >= 100_000) ? 500 : 1000;   // LinkedList middle is slow: fewer ops
            case "get_kth":        return (type == 1) ? (n >= 100_000 ? 500 : 2000) : 1_000_000;
            default:               return n;                                  // iterate: n elements
        }
    }

    /** Returns ns/op median, or NaN when the structure does not offer the operation (ArrayDeque has no index API). */
    static double[] measure(String op, int type, int n) {
        int ops = opsFor(op, type, n);
        int warm = 15, runs = 9;
        if (type == 2 && (op.equals("insert_middle") || op.equals("remove_middle") || op.equals("get_kth")))
            return new double[]{Double.NaN, Double.NaN, ops};
        // O(1)-per-element operations are repeated inside one timed run so a run lasts milliseconds, not microseconds
        final int reps = (op.equals("add_end") || op.equals("iterate")) ? Math.max(1, 1_000_000 / n) : 1;
        Random rnd = new Random(42);
        int[] idx = new int[ops];
        for (int i = 0; i < ops; i++) idx[i] = rnd.nextInt(n);

        Bench.Stat s;
        switch (op) {
            case "add_end":
                s = Bench.time(warm, runs, () -> { long a = 0; for (int rep = 0; rep < reps; rep++) {
                    if (type == 3) { ArrayPlaylist p = new ArrayPlaylist(16); for (int i = 0; i < n; i++) p.addEnd(SONGS[i]); a += p.size; }
                    else if (type == 2) { ArrayDeque<Song> d = new ArrayDeque<>(); for (int i = 0; i < n; i++) d.addLast(SONGS[i]); a += d.size(); }
                    else { List<Song> l = maker(type).get(); for (int i = 0; i < n; i++) l.add(SONGS[i]); a += l.size(); } } return a; });
                break;
            case "insert_front":
            case "insert_middle": {
                boolean front = op.equals("insert_front");
                if (type == 2) s = Bench.timeWithSetup(warm, runs, () -> { ArrayDeque<Song> d = new ArrayDeque<>(); for (int i = 0; i < n; i++) d.addLast(SONGS[i]); return d; },
                        d -> { for (int i = 0; i < ops; i++) d.addFirst(SONGS[i]); return d.size(); });
                else if (type == 3) s = Bench.timeWithSetup(warm, runs, () -> filledArray(n),
                        p -> { for (int i = 0; i < ops; i++) p.insert(front ? 0 : p.size / 2, SONGS[i]); return p.size; });
                else s = Bench.timeWithSetup(warm, runs, () -> filled(maker(type), n),
                        l -> { for (int i = 0; i < ops; i++) l.add(front ? 0 : l.size() / 2, SONGS[i]); return l.size(); });
                break; }
            case "remove_front":
            case "remove_middle": {
                boolean front = op.equals("remove_front");
                if (type == 2) s = Bench.timeWithSetup(warm, runs, () -> { ArrayDeque<Song> d = new ArrayDeque<>(); for (int i = 0; i < n; i++) d.addLast(SONGS[i]); return d; },
                        d -> { long a = 0; for (int i = 0; i < ops; i++) a += d.pollFirst().id; return a; });
                else if (type == 3) s = Bench.timeWithSetup(warm, runs, () -> filledArray(n),
                        p -> { long a = 0; for (int i = 0; i < ops; i++) a += p.remove(front ? 0 : p.size / 2).id; return a; });
                else s = Bench.timeWithSetup(warm, runs, () -> filled(maker(type), n),
                        l -> { long a = 0; for (int i = 0; i < ops; i++) a += l.remove(front ? 0 : l.size() / 2).id; return a; });
                break; }
            case "get_kth":
                if (type == 3) { ArrayPlaylist p = filledArray(n);
                    s = Bench.time(warm, runs, () -> { long a = 0; for (int i = 0; i < ops; i++) a += p.get(idx[i]).id; return a; }); }
                else { List<Song> l = filled(maker(type), n);
                    s = Bench.time(warm, runs, () -> { long a = 0; for (int i = 0; i < ops; i++) a += l.get(idx[i]).id; return a; }); }
                break;
            default: // iterate
                if (type == 2) { ArrayDeque<Song> d = new ArrayDeque<>(); for (int i = 0; i < n; i++) d.addLast(SONGS[i]);
                    s = Bench.time(warm, runs, () -> { long a = 0; for (int rep = 0; rep < reps; rep++) for (Song x : d) a += x.id; return a; }); }
                else if (type == 3) { ArrayPlaylist p = filledArray(n);
                    s = Bench.time(warm, runs, () -> { long a = 0; for (int rep = 0; rep < reps; rep++) for (int i = 0; i < p.size; i++) a += p.a[i].id; return a; }); }
                else { List<Song> l = filled(maker(type), n);
                    s = Bench.time(warm, runs, () -> { long a = 0; for (int rep = 0; rep < reps; rep++) for (Iterator<Song> it = l.iterator(); it.hasNext();) a += it.next().id; return a; }); }
        }
        return new double[]{s.median / ((double) ops * reps), s.cvPercent(), ops};
    }

    // ------------------------------------------------------------------------------------------
    /** "Up Next" queue: songs added at BOTH ends and removed from the front (changed condition). */
    static double upNext(int type, int n, int ops) {
        Random r = new Random(7);
        int[] code = new int[ops];                                 // 0 = addLast (35%), 1 = addFirst (15%), 2 = removeFirst (50%)
        for (int i = 0; i < ops; i++) { double x = r.nextDouble(); code[i] = x < 0.35 ? 0 : x < 0.50 ? 1 : 2; }
        Bench.Stat s;
        if (type == 0) s = Bench.timeWithSetup(2, 5, () -> filled(ArrayList::new, n), l -> {
            long a = 0; for (int i = 0; i < ops; i++) { if (code[i] == 0) l.add(SONGS[i]); else if (code[i] == 1) l.add(0, SONGS[i]); else if (!l.isEmpty()) a += l.remove(0).id; } return a + l.size(); });
        else if (type == 1) s = Bench.timeWithSetup(2, 5, () -> filled(LinkedList::new, n), l -> {
            long a = 0; LinkedList<Song> q = (LinkedList<Song>) l;
            for (int i = 0; i < ops; i++) { if (code[i] == 0) q.addLast(SONGS[i]); else if (code[i] == 1) q.addFirst(SONGS[i]); else if (!q.isEmpty()) a += q.removeFirst().id; } return a + q.size(); });
        else s = Bench.timeWithSetup(2, 5, () -> { ArrayDeque<Song> d = new ArrayDeque<>(); for (int i = 0; i < n; i++) d.addLast(SONGS[i]); return d; }, d -> {
            long a = 0; for (int i = 0; i < ops; i++) { if (code[i] == 0) d.addLast(SONGS[i]); else if (code[i] == 1) d.addFirst(SONGS[i]); else if (!d.isEmpty()) a += d.pollFirst().id; } return a + d.size(); });
        return s.median / ops;
    }

    static Object held;                                            // explicit holder: cleared before every "before" reading

    static Object build(int type, int n) {
        if (type == 0) { ArrayList<Song> l = new ArrayList<>(); for (int i = 0; i < n; i++) l.add(SONGS[i]); return l; }
        if (type == 1) { LinkedList<Song> l = new LinkedList<>(); for (int i = 0; i < n; i++) l.add(SONGS[i]); return l; }
        if (type == 2) { ArrayDeque<Song> l = new ArrayDeque<>(); for (int i = 0; i < n; i++) l.add(SONGS[i]); return l; }
        Song[] a = new Song[n]; for (int i = 0; i < n; i++) a[i] = SONGS[i]; return a;
    }

    /**
     * Retained bytes per element for 1 000 000 elements (element objects are shared, so excluded).
     * Measured against ONE clean baseline taken before any structure exists: a "before" reading taken between rounds
     * can still count the previous round's structure (HotSpot keeps stale references in interpreter frames until the next call).
     */
    static double bytesPerElement(int type, long cleanBaseline) {
        int n = 1_000_000;
        double[] res = new double[7];
        for (int r = 0; r < 7; r++) {
            held = build(type, n);
            long after = Bench.usedHeap();                         // exactly one structure is reachable here
            res[r] = (after - cleanBaseline) / (double) n;
            Bench.sink += System.identityHashCode(held);
            held = null;
        }
        java.util.Arrays.sort(res);
        return res[3];
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Environment: " + Bench.env());
        String[] ops = {"add_end", "insert_front", "remove_front", "insert_middle", "remove_middle", "get_kth", "iterate"};
        try (Bench.Csv csv = new Bench.Csv("data/q2_operations.csv", "operation,structure,n,ns_per_op,cv_percent,ops_per_run")) {
            for (String op : ops) {
                System.out.printf("%n--- %s : nanoseconds per operation (median of 9 runs) ---%n", op);
                System.out.printf("%-12s", "structure");
                for (int n : SIZES) System.out.printf("%14d", n);
                System.out.println();
                for (int t = 0; t < 4; t++) {
                    System.out.printf("%-12s", TYPES[t]);
                    for (int n : SIZES) {
                        double[] m = measure(op, t, n);
                        if (Double.isNaN(m[0])) System.out.printf("%14s", "n/a");
                        else System.out.printf("%14.1f", m[0]);
                        csv.row(op, TYPES[t], n, Double.isNaN(m[0]) ? "NA" : String.format("%.3f", m[0]), Double.isNaN(m[1]) ? "NA" : String.format("%.1f", m[1]), (int) m[2]);
                    }
                    System.out.println();
                }
            }
        }

        System.out.println("\n--- UP NEXT queue: 100 000 mixed ops (35% addLast, 15% addFirst, 50% removeFirst) : ns per op ---");
        System.out.printf("%-12s%14d%14d%14d%n", "structure", SIZES[0], SIZES[1], SIZES[2]);
        try (Bench.Csv csv = new Bench.Csv("data/q2_upnext.csv", "structure,n,ns_per_op")) {
            for (int t = 0; t < 3; t++) {
                System.out.printf("%-12s", TYPES[t]);
                for (int n : SIZES) { double v = upNext(t, n, 100_000); System.out.printf("%14.1f", v); csv.row(TYPES[t], n, v); }
                System.out.println();
            }
        }

        System.out.println("\n--- Space: retained bytes per element, 1 000 000 elements (median of 7, after GC) ---");
        try (Bench.Csv csv = new Bench.Csv("data/q2_space.csv", "structure,bytes_per_element")) {
            held = null;
            long base = Bench.usedHeap();
            for (int t = 0; t < 4; t++) {
                double b = bytesPerElement(t, base);
                System.out.printf("%-12s %6.1f bytes/element%n", TYPES[t], b);
                csv.row(TYPES[t], b);
            }
        }
        System.out.println("\n(sink=" + Bench.sink + ")");
    }
}
