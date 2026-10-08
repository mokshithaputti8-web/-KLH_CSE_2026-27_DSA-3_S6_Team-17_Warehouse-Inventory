# Performance (1 crore = 10,000,000 rows, ~950 MB, measured on a 1-core cloud VM)
| Operation | Time |
|---|---|
| Generate dataset | 8 s |
| Parallel aggregation (4 threads) | 5.7 s (1.7 M rows/s) |
| Streaming search, KMP | 3.1 s |
| Streaming search, Rabin-Karp | 6.3 s |
| Percentiles via quickselect | 2.6 s |
| Reservoir sample | 1.2 s |
Multi-core laptops will aggregate faster because the threads run truly in parallel.
