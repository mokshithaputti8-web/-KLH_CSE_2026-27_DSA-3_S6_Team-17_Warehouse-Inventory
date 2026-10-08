# Test report

Generated: 2026-10-04T10:16:38

| Test | Result | Detail |
|---|---|---|
| M2 KMP / Z / Rabin-Karp / suffix-array search equal naive search | PASS | 300 random texts |
| M2 Aho-Corasick multi-pattern counts equal naive | PASS | 200 random texts, 3 patterns each |
| M2 suffix array sorted and Kasai LCP correct | PASS | 100 random strings |
| M3 edit distance kitten -> sitting = 3 | PASS | Wagner-Fischer |
| M3 transposition counts as one edit (bolt -> blot) | PASS | Damerau / OSA |
| M3 distance from empty string | PASS | length of other string |
| M3 knapsack equals brute-force optimum | PASS | 150 random instances |
| M4 max flow on the textbook (CLRS) network = 23 | PASS | got 23 |
| M4 max-flow value equals min-cut capacity | PASS | 100 random networks |
| M5 greedy set cover covers every element | PASS | 2 sets chosen |
| M6 randomised quicksort and quickselect correct | PASS | 50 random arrays with many duplicates |
| M6 reservoir sampling is uniform | PASS | each of 10 items picked about 9000 of 30000 times |
| Storage: CSV line round trip | PASS | parse + format |
| M1 typo 'haevy' corrected to 'heavy' and products found | PASS | found 313 products |
| M1 exact SKU search ranks that product first | PASS | SKU PKG-0001 |
| M3 replenishment plan stays within budget | PASS | spent 49,919 of 50,000 on 7 items |
| M4 balancing finds transfers | PASS | legs 806, moved 30,832 |
| M5 risk ranking sorted (highest first) | PASS | top risk 100.0 |
| M6 cycle sample has requested size, no duplicates | PASS | 8 distinct products |
| M6 parallel aggregation (4 threads) equals sequential (1 thread) | PASS | 200,000 rows |
| M2 large search: KMP, Z and Rabin-Karp agree | PASS | 'bolt' matches 11042 of 200,000 rows |
| M6 percentiles via quickselect: min <= median <= max | PASS | p50 = 190 |
| M6 large reservoir sample size | PASS | 12 of 200,000 |

**Total: 23 passed, 0 failed (3208 ms)**
