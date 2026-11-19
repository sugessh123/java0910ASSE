**Name:** T.Achyuth  |  **Register Number:** 192372257  |  **Course:** CSA09 Programming in Java  |  **Assignment 2**

# Q2 – Predictions (written BEFORE running any benchmark)

## Reasoning basis
* `ArrayList` = one contiguous `Object[]`. `get(k)` is pointer arithmetic (O(1)). Insert/remove at index `i` shifts the
  `size - i` elements after it with a fast `System.arraycopy` (O(n) but a very small constant, cache-friendly).
* `LinkedList` = doubly linked nodes (24 bytes each on a 64-bit JVM with compressed pointers: 12-byte header + 3 references).
  Linking a node is O(1) **once you are standing on it**, but `get(k)` / `add(k, x)` must first walk `min(k, n-k)` nodes
  (O(n) pointer chasing, poor cache behaviour).
* `ArrayDeque` = circular array with head/tail indices: O(1) at both ends, **no** index-based `get(k)` / middle insert at all.

## Predictions

| # | Operation | Prediction |
|---|-----------|------------|
| P1 | Add at end | All three are O(1) amortised: tens of ns/op. LinkedList 2–5x slower than ArrayList (one node allocation per add). |
| P2 | Insert / remove at front | ArrayList grows linearly with n (≈100x from n=1 000 to n=100 000; about 5–30 µs/op at n=100 000). LinkedList and ArrayDeque stay flat (tens of ns). |
| P3 | Insert / remove in the middle | **Counter-intuitive:** LinkedList is *not* faster. Walking n/2 nodes costs more than `arraycopy`-ing n/2 references, so at n=100 000 LinkedList is ≥ 3x slower than ArrayList. |
| P4 | get(k) | ArrayList flat (a few ns). LinkedList linear in n (≥ 10 µs at n=100 000). |
| P5 | Iterate all | Both O(n); ArrayList 2–5x faster per element (contiguous memory vs pointer chasing). |
| P6 | Space per element (1 M elements, element objects excluded) | ArrayList ≈ 4–6 B, ArrayDeque ≈ 4–8 B, LinkedList ≈ 24 B (≥ 4x larger). |
| P7 | "Up Next" mixed workload (add both ends, remove front) | ArrayDeque and LinkedList flat in n; ArrayList linear in n → ≥ 100x slower than ArrayDeque at n = 100 000. ArrayDeque at least as fast as LinkedList. |
| P8 | Plain array with manual shifting | Same O(n) growth as ArrayList; constant 1–5x worse than ArrayList's `arraycopy`. |
