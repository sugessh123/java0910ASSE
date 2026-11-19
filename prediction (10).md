**Name:** T.Achyuth  |  **Register Number:** 192372257  |  **Course:** CSA09 Programming in Java  |  **Assignment 2**

# Q1 – Predictions (written BEFORE running any benchmark)

## Reasoning basis
`String` is immutable. `s = s + field + "\n"` inside a loop cannot modify `s`; every iteration allocates a
brand-new String and copies **all characters accumulated so far** into it. If one ticket has `n` lines of
about `L` characters each, iteration `i` copies about `i·L` characters, so the total copied is

    L · (1 + 2 + ... + n) = L · n(n+1)/2   →  O(n²) characters, and n garbage Strings.

`StringBuilder` keeps one growable `byte[]` and appends in place. When it is full it doubles, so every character is
copied only a constant number of times on average (amortised) → O(n) characters, and about log2(n) resizes.
`StringBuffer` is the same algorithm but every method is `synchronized`.

## Predictions

| # | Workload | Prediction |
|---|----------|------------|
| P1 | W1: short ticket, 8 fields, 100 000 tickets | All approaches are within about 3x of each other (tens to a few hundred ns per ticket). `+` is *not* dramatically worse because n = 8 and the strings are tiny. `String.format` is the slowest (≥ 5x StringBuilder) because it parses a format string on every call. |
| P2 | W2: 10 000 tickets x 50 fields | `+` is about 4–10x slower than `StringBuilder` (character-copy ratio is about 25 500 : 3 000 ≈ 8x). |
| P3 | W3 (own): 100 tickets x 2 000 fields ("group / season-pass manifest") | `+` is at least 100x slower than `StringBuilder`; the gap is now the dominant effect and bytes allocated by `+` are hundreds of times larger. |
| P4 | Scaling sweep, one ticket, n = 10 … 20 000 | On a log-log plot `+` has slope ≈ 2 and `StringBuilder` slope ≈ 1. |
| P5 | `StringBuilder` vs `StringBuffer`, single thread | Within about ±30 %. The lock is uncontended so the cost is small, but not zero. |
| P6 | `String.join` / `StringJoiner` | Same order as `StringBuilder` (both O(n)); `String.join` may be slightly faster because it computes the exact final size once. |
| P7 | Multithreaded shared log | An unsynchronised `StringBuilder` shared by 4 threads **loses appends and/or throws** `ArrayIndexOutOfBoundsException` in a visible fraction of trials; `StringBuffer` never loses appended entries. |
