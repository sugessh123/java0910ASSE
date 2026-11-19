**Name:** T.Achyuth  |  **Register Number:** 192372257  |  **Course:** CSA09 Programming in Java  |  **Assignment 2**

# Q3 – Predictions (written BEFORE running anything)

## Reasoning basis
`HashSet.add(x)` is really `HashMap.put(x, PRESENT)`. To place or find `x` Java:
1. calls `x.hashCode()` and spreads the bits → picks a **bucket**;
2. walks that bucket and, for each stored key `k`, tests `k.hash == x.hash && (k == x || x.equals(k))`.

`equals()` is therefore only ever consulted for keys that are **already in the same bucket with an identical full hash**.
`Student` in the brief overrides `equals()` but not `hashCode()`, so it inherits `Object.hashCode()` (derived from object
identity). Two different `Student` objects for the same `regNo` almost surely have different identity hashes, so they
land in different buckets / fail the hash test, and `equals()` is **never even called**.

## Predictions (each recorded in code *before* the test runs, and compared automatically with the observed size)

| Test | Scenario | Predicted result and reason |
|------|----------|-----------------------------|
| T1a | 5 scans of 5 *different* students, junior class | size = 5. Nothing to de-duplicate, so the bug is invisible. |
| T1b | 5 distinct students, but one **same object** scanned 3 extra times | size = 5. Same object ⇒ same identity hash ⇒ `k == x` succeeds. The bug is still hidden. |
| T2  | 6 scans = 4 distinct regNo, two of them scanned again with **new objects** | size = 6 (should be 4). Hash codes differ so `equals` is never reached. Over 1 000 random runs duplicates should show in 1 000/1 000. |
| T2b | Same scans, class with `hashCode()` on regNo | size = 4. |
| T3a | `regNo` mutated after insertion (hash from regNo) | size stays 1, but `contains(sameObject)` = false, `contains(new Student(oldRegNo))` = false, `remove` = false. The entry is lost in a bucket nobody will search ("ghost entry"). |
| T3b | `TreeSet` ordered by `name`, two *different* students with the same name | `TreeSet` size = 1 (second silently dropped); `HashSet` with `equals` on regNo size = 2. |
| T3c | `hashCode` uses (regNo + name) but `equals` only regNo; same regNo scanned with name "Arun" and "ARUN K" | size = 2 (duplicate), because equal objects have unequal hashes. |
| T4  | Constant `hashCode()` (always 1) | De-duplication stays **correct** (equals still decides) but cost per scan grows linearly with set size: ≥ 20x slower at n = 20 000 than a good hash code. |
| T5  | Mechanism counters | Adding a new student: `hashCode` called once, `equals` 0 times. Adding a duplicate: `hashCode` once, `equals` once. |
| Alt | HashSet vs TreeSet(comparator) vs HashMap<String,Student> | HashSet/HashMap time per scan roughly flat in n; TreeSet grows ≈ log n (about 17 comparisons at n = 100 000). TreeSet node (40 B) costs more memory per element than HashSet entry (~32–48 B). |
| Complete the Code | `Attendance` before TODO 1 | prints `Unique students: 3`; after TODO 1 prints `Unique students: 2`. |
