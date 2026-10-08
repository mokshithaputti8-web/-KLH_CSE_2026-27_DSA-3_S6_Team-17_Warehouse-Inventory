# Design notes
**Storage.** `data/inventory.csv` columns: id, sku, name, category, warehouse, supplier, qty, min, price, demand(units/day). A SKU can exist in several warehouses (unique per SKU + warehouse). Every write goes to a temp file and is atomically moved over the original; every read checks the file's modified time, so the website and the terminal always see each other's changes.

**Definitions.** Below minimum: qty < min. Surplus: qty > 2 x min. Risk score (0-100): 100 x (1 - daysOfCover/21), +25 if below minimum, 100 if out of stock; at-risk means score >= 60.

**Module 1+2 search.** Query is tokenised; Aho-Corasick scans the product corpus once for all words. Words with no hit are corrected to the nearest vocabulary word by Damerau edit distance (<=1 for short words, <=2 otherwise) and the search is rerun. Ranking: matched words, +4 if KMP finds the whole phrase in the name, +6 for exact SKU.

**Module 3.** Each below-minimum product gets order qty = 2 x min - qty, cost = qty x price, benefit = order qty x (demand+1) (x3 if out of stock). 0/1 knapsack DP picks the best-benefit set within budget (costs scaled up to <=20,000 DP columns for big budgets; never exceeds the budget).

**Module 4.** Per SKU: source -> warehouse (surplus) , warehouse -> warehouse (route capacity), warehouse -> sink (shortage). Edmonds-Karp (BFS augmenting paths, O(VE^2)). Unmet shortage is reported.

**Module 5.** Warehouse and product ranking by risk; suppliers chosen by greedy set cover over at-risk products (H(d) approximation guarantee, shown in the output).

**Module 6.** Reservoir sampling; randomised 3-way quicksort/quickselect; chunked parallel scan of the large file (each thread handles whole lines in its byte range) with a reduce step.
