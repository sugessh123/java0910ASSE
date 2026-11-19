**Name:** T.Achyuth  |  **Register Number:** 192372257  |  **Course:** CSA09 Programming in Java  |  **Assignment 2**

# Q4 – Predictions (written BEFORE running any benchmark)

## Reasoning basis
| Map | Structure | put / get | Iteration order |
|-----|-----------|-----------|-----------------|
| `HashMap` | bucket table + linked/tree bins | O(1) average | none guaranteed (depends on hash) |
| `LinkedHashMap` | `HashMap` + doubly linked list through the entries | O(1) average | **insertion order** |
| `TreeMap` | red-black tree | O(log n) (about log2 n key comparisons) | **sorted by key** |
| `Hashtable` | legacy hash table, every method `synchronized` | O(1) average, plus lock cost | none |
| sorted `Medicine[]` + `Arrays.binarySearch` | contiguous array | search O(log n); insert O(n) (shifting) | sorted by key |

Requirements: (R1) look up stock by code, hundreds of times a day; (R2) evening report in **code order**;
(R3) evening list in **insertion order**.

## Predictions

| # | Measure | Prediction |
|---|---------|------------|
| P1 | `put` cost, n = 100 000 and 1 000 000 | HashMap fastest; LinkedHashMap about 1.0–1.5x HashMap; TreeMap 3–10x HashMap. |
| P2 | 100 000 lookups | HashMap ≈ LinkedHashMap ≈ Hashtable (single thread). TreeMap 3–8x slower at 100 000+ entries and growing with log n. |
| P3 | Which map gives which report for free | R2 → TreeMap only. R3 → LinkedHashMap only. HashMap gives neither (its order is arbitrary). |
| P4 | Extra work with the "wrong" map | Code-order report from HashMap/LinkedHashMap needs a sort: O(n log n), at n = 1 000 000 at least 10x slower than TreeMap's plain iteration. Insertion-order report from HashMap/TreeMap is impossible without storing a sequence number and sorting on it. |
| P5 | Correctness of order (computed, not eyeballed) | Plain iteration order matches R2 only for TreeMap, R3 only for LinkedHashMap. |
| P6 | Operation counts | TreeMap ≈ log2(n) + small constant key comparisons per lookup (≈ 20–27 at n = 1 000 000). HashMap ≈ 1 `equals()` call per successful lookup. Binary search ≈ log2(n) probes. |
| P7 | Acceptability criterion | I define acceptable as **p99 ≤ 1 ms per lookup** (1 % of a 100 ms interactive budget). Prediction: every in-memory map passes by ≥ 3 orders of magnitude, so the choice is decided by the reports, not by lookup speed. |
| P8 | Where the remaining cost comes from | At 1 000 000 entries a HashMap lookup is dominated by **cache misses** (table → node → key → char data), not by arithmetic: repeatedly looking up one hot key should be ≥ 3x faster than random keys. |
| P9 | Sorted array + binarySearch | Lookup comparable to TreeMap or faster (contiguous memory); insert is O(n): at n = 1 000 000 at least 100x slower than TreeMap.put. Memory ≈ 4 bytes/entry (≥ 8x less than any map). |
| P10 | Hashtable vs ConcurrentHashMap, 2 reader threads | Hashtable ≥ 2x slower than HashMap and ConcurrentHashMap because every `get` takes the same lock. |
| P11 | Memory per entry (container only, exact) | HashMap ≈ 38–42 B, LinkedHashMap ≈ 8 B more, TreeMap = 40 B, Hashtable ≈ 36–44 B. |
