import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Warehouse Inventory System - ONE source file.
 * Part A  Algorithm engine  (Modules 1-6, hand-built, NO java.util inside the engine classes)
 * Part B  Persistent storage (CSV, shared by website and terminal)
 * Part C  Module features    (search, replenishment, balancing, risk, cycle count, large data)
 * Part D  Web server (localhost) and REST API
 * Part E  Terminal application and self-tests
 */
public class Main {

    // ======================================================================
    // PART A - ALGORITHM ENGINE (no java.util.* used by these classes)
    // ======================================================================

    /** Hand-built growable int array. */
    static final class IL {
        int[] a = new int[16]; int n;
        void add(int v) { if (n == a.length) { int[] b = new int[n * 2]; System.arraycopy(a, 0, b, 0, n); a = b; } a[n++] = v; }
        int[] arr() { int[] r = new int[n]; System.arraycopy(a, 0, r, 0, n); return r; }
    }

    /** Module 6: xorshift pseudo random generator (own implementation). */
    static final class Rng {
        long s;
        Rng(long seed) { s = seed * 0x9E3779B97F4A7C15L + 0x1234567L; if (s == 0) s = 88172645463325252L; for (int i = 0; i < 5; i++) next(); }
        long next() { s ^= s << 13; s ^= s >>> 7; s ^= s << 17; return s; }
        int nextInt(int n) { return (int) ((next() >>> 1) % n); }
        long nextLong(long n) { return (next() >>> 1) % n; }
    }

    interface Hit { void hit(int end, int pat); }

    // ---------------- Module 2: String algorithms ----------------
    static final class Str {
        static int[] lps(String p) {
            int m = p.length(); int[] f = new int[m];
            for (int i = 1, k = 0; i < m; i++) {
                while (k > 0 && p.charAt(i) != p.charAt(k)) k = f[k - 1];
                if (p.charAt(i) == p.charAt(k)) k++;
                f[i] = k;
            }
            return f;
        }
        /** KMP: all start positions of p in t, linear time. */
        static int[] kmp(String t, String p) {
            IL r = new IL(); int m = p.length(); if (m == 0) return new int[0];
            int[] f = lps(p);
            for (int i = 0, k = 0; i < t.length(); i++) {
                while (k > 0 && t.charAt(i) != p.charAt(k)) k = f[k - 1];
                if (t.charAt(i) == p.charAt(k)) k++;
                if (k == m) { r.add(i - m + 1); k = f[k - 1]; }
            }
            return r.arr();
        }
        static boolean kmpHas(String t, String p, int[] f) {
            int m = p.length(); if (m == 0) return true;
            for (int i = 0, k = 0; i < t.length(); i++) {
                while (k > 0 && t.charAt(i) != p.charAt(k)) k = f[k - 1];
                if (t.charAt(i) == p.charAt(k)) k++;
                if (k == m) return true;
            }
            return false;
        }
        static int[] zfunc(String s) {
            int n = s.length(); int[] z = new int[n];
            for (int i = 1, l = 0, r = 0; i < n; i++) {
                if (i < r) z[i] = Math.min(r - i, z[i - l]);
                while (i + z[i] < n && s.charAt(z[i]) == s.charAt(i + z[i])) z[i]++;
                if (i + z[i] > r) { l = i; r = i + z[i]; }
            }
            return z;
        }
        /** Z-algorithm search on p + sep + t. */
        static int[] z(String t, String p) {
            IL r = new IL(); int m = p.length(); if (m == 0) return new int[0];
            int[] z = zfunc(p + '\u0001' + t);
            for (int i = m + 1; i < z.length; i++) if (z[i] >= m) r.add(i - m - 1);
            return r.arr();
        }
        /** Rabin-Karp with double polynomial rolling hash (two moduli). */
        static int[] rk(String t, String p) {
            IL r = new IL(); int n = t.length(), m = p.length(); if (m == 0 || m > n) return new int[0];
            final long M1 = 1_000_000_007L, M2 = 998_244_353L, B = 257;
            long h1 = 0, h2 = 0, t1 = 0, t2 = 0, pw1 = 1, pw2 = 1;
            for (int i = 0; i < m; i++) {
                h1 = (h1 * B + p.charAt(i)) % M1; h2 = (h2 * B + p.charAt(i)) % M2;
                t1 = (t1 * B + t.charAt(i)) % M1; t2 = (t2 * B + t.charAt(i)) % M2;
                if (i < m - 1) { pw1 = pw1 * B % M1; pw2 = pw2 * B % M2; }
            }
            for (int i = 0; i + m <= n; i++) {
                if (t1 == h1 && t2 == h2 && t.regionMatches(i, p, 0, m)) r.add(i);
                if (i + m < n) {
                    t1 = ((t1 - t.charAt(i) * pw1) % M1 + M1) % M1; t1 = (t1 * B + t.charAt(i + m)) % M1;
                    t2 = ((t2 - t.charAt(i) * pw2) % M2 + M2) % M2; t2 = (t2 * B + t.charAt(i + m)) % M2;
                }
            }
            return r.arr();
        }
    }

    /** Aho-Corasick multi-pattern automaton (trie + failure links). */
    static final class AC {
        int[][] go; int[] fail, dict, head, nxt; int states = 1;
        AC(String[] pats) {
            int total = 1; for (String p : pats) total += p.length();
            go = new int[total][128]; for (int[] row : go) for (int j = 0; j < 128; j++) row[j] = -1;
            fail = new int[total]; dict = new int[total]; head = new int[total]; nxt = new int[pats.length];
            for (int i = 0; i < total; i++) head[i] = -1;
            for (int i = 0; i < pats.length; i++) {
                int s = 0;
                for (int k = 0; k < pats[i].length(); k++) {
                    int c = pats[i].charAt(k); if (c >= 128) c = 127;
                    if (go[s][c] < 0) go[s][c] = states++;
                    s = go[s][c];
                }
                nxt[i] = head[s]; head[s] = i;
            }
            int[] q = new int[states]; int qh = 0, qt = 0;
            for (int c = 0; c < 128; c++) { if (go[0][c] < 0) go[0][c] = 0; else { fail[go[0][c]] = 0; q[qt++] = go[0][c]; } }
            while (qh < qt) {
                int u = q[qh++];
                int f = fail[u]; dict[u] = head[f] >= 0 ? f : dict[f];
                for (int c = 0; c < 128; c++) {
                    int v = go[u][c];
                    if (v < 0) go[u][c] = go[f][c];
                    else { fail[v] = go[f][c]; q[qt++] = v; }
                }
            }
        }
        void scan(String text, Hit h) {
            int s = 0;
            for (int i = 0; i < text.length(); i++) {
                int c = text.charAt(i); if (c >= 128) c = 127;
                s = go[s][c];
                for (int u = head[s] >= 0 ? s : dict[s]; u > 0; u = dict[u])
                    for (int nd = head[u]; nd >= 0; nd = nxt[nd]) h.hit(i, nd);
            }
        }
    }

    /** Suffix array (prefix doubling + counting sort) with Kasai LCP. */
    static final class SA {
        final String s; final int[] sa, rank, lcp; final int n;
        SA(String str) {
            s = str; n = str.length(); sa = new int[n]; rank = new int[n]; lcp = new int[n];
            if (n == 0) return;
            int[] x = new int[n], y = new int[n]; int m = 256; int[] c = new int[Math.max(m, n) + 2];
            for (int i = 0; i < n; i++) c[x[i] = s.charAt(i) & 255]++;
            for (int i = 1; i < m; i++) c[i] += c[i - 1];
            for (int i = n - 1; i >= 0; i--) sa[--c[x[i]]] = i;
            for (int k = 1; k <= n; k <<= 1) {
                int p = 0;
                for (int i = n - k; i < n; i++) y[p++] = i;
                for (int i = 0; i < n; i++) if (sa[i] >= k) y[p++] = sa[i] - k;
                for (int i = 0; i < m; i++) c[i] = 0;
                for (int i = 0; i < n; i++) c[x[y[i]]]++;
                for (int i = 1; i < m; i++) c[i] += c[i - 1];
                for (int i = n - 1; i >= 0; i--) sa[--c[x[y[i]]]] = y[i];
                int[] t = x; x = y; y = t;
                p = 1; x[sa[0]] = 0;
                for (int i = 1; i < n; i++) {
                    int a = sa[i - 1], b = sa[i];
                    boolean same = y[a] == y[b] && (a + k < n ? y[a + k] : -1) == (b + k < n ? y[b + k] : -1);
                    x[b] = same ? p - 1 : p++;
                }
                if (p >= n) break;
                m = p;
            }
            for (int i = 0; i < n; i++) rank[sa[i]] = i;
            // Kasai: LCP in linear time. lcp[i] = LCP(sa[i-1], sa[i])
            for (int i = 0, h = 0; i < n; i++) {
                if (rank[i] > 0) {
                    int j = sa[rank[i] - 1];
                    while (i + h < n && j + h < n && s.charAt(i + h) == s.charAt(j + h)) h++;
                    lcp[rank[i]] = h; if (h > 0) h--;
                } else h = 0;
            }
        }
        int cmp(int pos, String p) {
            int m = p.length();
            for (int k = 0; k < m; k++) {
                if (pos + k >= n) return -1;
                int d = s.charAt(pos + k) - p.charAt(k); if (d != 0) return d;
            }
            return 0;
        }
        int lower(String p) { int lo = 0, hi = n; while (lo < hi) { int mid = (lo + hi) >>> 1; if (cmp(sa[mid], p) < 0) lo = mid + 1; else hi = mid; } return lo; }
        int upper(String p) { int lo = 0, hi = n; while (lo < hi) { int mid = (lo + hi) >>> 1; if (cmp(sa[mid], p) <= 0) lo = mid + 1; else hi = mid; } return lo; }
        int count(String p) { return p.length() == 0 ? 0 : upper(p) - lower(p); }
        long distinctSubstrings() { long t = (long) n * (n + 1) / 2; for (int i = 1; i < n; i++) t -= lcp[i]; return t; }
    }

    // ---------------- Module 3: Dynamic programming ----------------
    static final class DP {
        /** Wagner-Fischer edit distance with adjacent transpositions (Damerau, optimal string alignment). */
        static int edit(String a, String b) {
            int n = a.length(), m = b.length(); int[][] d = new int[n + 1][m + 1];
            for (int i = 0; i <= n; i++) d[i][0] = i;
            for (int j = 0; j <= m; j++) d[0][j] = j;
            for (int i = 1; i <= n; i++) for (int j = 1; j <= m; j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + c);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) v = Math.min(v, d[i - 2][j - 2] + 1);
                d[i][j] = v;
            }
            return d[n][m];
        }
        /** 0/1 knapsack, returns indices of chosen items (reconstructed from the take table). */
        static int[] knapsack(int[] w, long[] v, int W) {
            int n = w.length; boolean[][] take = new boolean[n][W + 1]; long[] dp = new long[W + 1];
            for (int i = 0; i < n; i++)
                for (int c = W; c >= w[i]; c--)
                    if (dp[c - w[i]] + v[i] > dp[c]) { dp[c] = dp[c - w[i]] + v[i]; take[i][c] = true; }
            IL sel = new IL(); int c = W;
            for (int i = n - 1; i >= 0; i--) if (take[i][c]) { sel.add(i); c -= w[i]; }
            return sel.arr();
        }
    }

    // ---------------- Module 4: Network flow (Edmonds-Karp) ----------------
    static final class Flow {
        /** Max flow on an adjacency-matrix network. res (if not null) receives the residual capacities. */
        static int maxflow(int[][] cap, int s, int t, int[][] res) {
            int n = cap.length; int[][] r = new int[n][n];
            for (int i = 0; i < n; i++) System.arraycopy(cap[i], 0, r[i], 0, n);
            int total = 0; int[] par = new int[n]; int[] q = new int[n];
            while (true) {
                for (int i = 0; i < n; i++) par[i] = -1;
                par[s] = s; int qh = 0, qt = 0; q[qt++] = s;
                while (qh < qt && par[t] < 0) { int u = q[qh++]; for (int v = 0; v < n; v++) if (par[v] < 0 && r[u][v] > 0) { par[v] = u; q[qt++] = v; } }
                if (par[t] < 0) break;
                int b = Integer.MAX_VALUE;
                for (int v = t; v != s; v = par[v]) b = Math.min(b, r[par[v]][v]);
                for (int v = t; v != s; v = par[v]) { r[par[v]][v] -= b; r[v][par[v]] += b; }
                total += b;
            }
            if (res != null) for (int i = 0; i < n; i++) System.arraycopy(r[i], 0, res[i], 0, n);
            return total;
        }
    }

    // ---------------- Module 5: Approximation algorithms ----------------
    static final class Approx {
        /** Greedy set cover (ln n approximation). sets[i] = element ids. Returns chosen set order; gain gets new coverage per step. */
        static int[] setCover(int[][] sets, int universe, IL gain) {
            boolean[] cov = new boolean[universe]; IL pick = new IL(); boolean[] used = new boolean[sets.length];
            while (true) {
                int best = -1, bg = 0;
                for (int i = 0; i < sets.length; i++) if (!used[i]) {
                    int g = 0; for (int e : sets[i]) if (!cov[e]) g++;
                    if (g > bg) { bg = g; best = i; }
                }
                if (best < 0) break;
                used[best] = true; for (int e : sets[best]) cov[e] = true; pick.add(best); gain.add(bg);
            }
            return pick.arr();
        }
    }

    // ---------------- Module 6: Randomised algorithms ----------------
    static final class Rand {
        static void swap(int[] a, int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }
        /** Randomised quicksort (3-way) of index array by double keys, ascending. */
        static void qsort(int[] a, double[] key, int lo, int hi, Rng r) {
            while (lo < hi) {
                double p = key[a[lo + r.nextInt(hi - lo + 1)]]; int lt = lo, gt = hi, i = lo;
                while (i <= gt) { double k = key[a[i]]; if (k < p) swap(a, lt++, i++); else if (k > p) swap(a, i, gt--); else i++; }
                if (lt - lo < hi - gt) { qsort(a, key, lo, lt - 1, r); lo = gt + 1; } else { qsort(a, key, gt + 1, hi, r); hi = lt - 1; }
            }
        }
        static void qsortS(int[] a, String[] key, int lo, int hi, Rng r) {
            while (lo < hi) {
                String p = key[a[lo + r.nextInt(hi - lo + 1)]]; int lt = lo, gt = hi, i = lo;
                while (i <= gt) { int c = key[a[i]].compareToIgnoreCase(p); if (c < 0) swap(a, lt++, i++); else if (c > 0) swap(a, i, gt--); else i++; }
                if (lt - lo < hi - gt) { qsortS(a, key, lo, lt - 1, r); lo = gt + 1; } else { qsortS(a, key, gt + 1, hi, r); hi = lt - 1; }
            }
        }
        /** Randomised quickselect: k-th smallest (0-based) in expected O(n). Reorders a. */
        static int qselect(int[] a, int k, Rng r) {
            int lo = 0, hi = a.length - 1;
            while (lo < hi) {
                int p = a[lo + r.nextInt(hi - lo + 1)]; int lt = lo, gt = hi, i = lo;
                while (i <= gt) { if (a[i] < p) swap(a, lt++, i++); else if (a[i] > p) swap(a, i, gt--); else i++; }
                if (k < lt) hi = lt - 1; else if (k > gt) lo = gt + 1; else return p;
            }
            return a[lo];
        }
        /** Reservoir sampling (Algorithm R): uniform k-sample from a stream of unknown length. */
        static final class Reservoir {
            final Object[] a; long seen; final Rng r;
            Reservoir(int k, Rng r) { a = new Object[k]; this.r = r; }
            void offer(Object x) {
                if (seen < a.length) a[(int) seen] = x;
                else { long j = r.nextLong(seen + 1); if (j < a.length) a[(int) j] = x; }
                seen++;
            }
            int size() { return (int) Math.min(seen, a.length); }
        }
    }

    // ======================================================================
    // PART B - PERSISTENT STORAGE (CSV shared by website and terminal)
    // ======================================================================

    static Path ROOT, DATA, RESULTS, REPORTS, WEB;
    static boolean QUIET = false;   // true while self-tests run (do not overwrite real result files)
    static final String[] WH = {"Hyderabad", "Mumbai", "Delhi", "Chennai", "Kolkata"};
    static final String[] CATS = {"Fasteners", "Electrical", "Plumbing", "Tools", "Safety", "Packaging"};
    static final String[] CP = {"FAS", "ELE", "PLU", "TOL", "SAF", "PKG"};
    static final String[][] NOUNS = {
        {"Hex Bolt", "Wood Screw", "Hex Nut", "Flat Washer", "Rivet", "Anchor Bolt"},
        {"Copper Wire", "Circuit Breaker", "LED Bulb", "Switch Socket", "Cable Tie", "Glass Fuse"},
        {"PVC Pipe", "Ball Valve", "Elbow Joint", "Pipe Clamp", "Water Tap", "Teflon Tape"},
        {"Claw Hammer", "Screwdriver Set", "Adjustable Spanner", "Drill Bit", "Hacksaw Blade", "Measuring Tape"},
        {"Safety Helmet", "Work Gloves", "Safety Goggles", "Ear Plug", "Reflective Vest", "Dust Mask"},
        {"Cardboard Box", "Stretch Film", "Packing Tape", "Bubble Wrap", "Pallet Strap", "Label Roll"}};
    static final String[] MODS = {"Steel", "Brass", "Zinc", "Heavy Duty", "Industrial", "Premium", "Compact", "Galvanized", "Stainless", "Rubber"};
    static final String[] SIZES = {"M6", "M8", "M10", "12mm", "25mm", "50mm", "2.5mm", "1 inch"};
    static final String[] SUPS = {"Tata Steel Traders", "Reliance Industrial", "Bharat Hardware", "Anand Electricals", "Sri Lakshmi Supplies",
        "Kaveri Packaging", "Mahindra Tools", "Godrej Safety", "Larsen Fittings", "Havells Direct", "Jindal Pipes", "Apex Industrial"};
    static final String HEADER = "id,sku,name,category,warehouse,supplier,qty,min,price,demand";

    static final class P {
        String id, sku, name, cat, wh, sup; int qty, min, dem; double price;
        String csv() { return id + "," + sku + "," + name + "," + cat + "," + wh + "," + sup + "," + qty + "," + min + "," + String.format(Locale.ROOT, "%.2f", price) + "," + dem; }
        static P parse(String line) {
            String[] f = line.split(",", -1); if (f.length < 10) return null;
            try {
                P p = new P(); p.id = f[0]; p.sku = f[1]; p.name = f[2]; p.cat = f[3]; p.wh = f[4]; p.sup = f[5];
                p.qty = Integer.parseInt(f[6].trim()); p.min = Integer.parseInt(f[7].trim()); p.price = Double.parseDouble(f[8].trim()); p.dem = Integer.parseInt(f[9].trim());
                return p;
            } catch (NumberFormatException e) { return null; }
        }
        double risk() {
            if (qty <= 0) return 100;
            double days = qty / Math.max(1.0, dem); double r = 100 * Math.max(0, 1 - days / 21.0);
            if (qty < min) r = Math.min(100, r + 25);
            return r;
        }
        double days() { return qty / Math.max(1.0, dem); }
        String status() { return qty <= 0 ? "OUT" : qty < min ? "LOW" : qty > 2 * min ? "SURPLUS" : "OK"; }
    }

    static final class Store {
        static final Object LOCK = new Object();
        static ArrayList<P> items = new ArrayList<>(); static long mtime = -1, ver = 0;
        static Path file() { return DATA.resolve("inventory.csv"); }
        static void ensure() {
            synchronized (LOCK) {
                try {
                    if (!Files.exists(file())) { items = seedItems(); save(); return; }
                    long m = Files.getLastModifiedTime(file()).toMillis() * 31 + Files.size(file());
                    if (m != mtime) {
                        ArrayList<P> l = new ArrayList<>();
                        for (String ln : Files.readAllLines(file(), StandardCharsets.UTF_8)) { if (ln.startsWith("id,") || ln.trim().isEmpty()) continue; P p = P.parse(ln); if (p != null) l.add(p); }
                        items = l; mtime = m; ver++;
                    }
                } catch (IOException e) { throw new RuntimeException("storage error: " + e.getMessage()); }
            }
        }
        static void save() {
            synchronized (LOCK) {
                try {
                    StringBuilder b = new StringBuilder(HEADER).append('\n');
                    for (P p : items) b.append(p.csv()).append('\n');
                    Path tmp = DATA.resolve("inventory.csv.tmp");
                    Files.write(tmp, b.toString().getBytes(StandardCharsets.UTF_8));
                    try { Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                    catch (IOException e) { Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING); }
                    mtime = Files.getLastModifiedTime(file()).toMillis() * 31 + Files.size(file()); ver++;
                } catch (IOException e) { throw new RuntimeException("cannot save: " + e.getMessage()); }
            }
        }
        static String nextId() { int mx = 0; for (P p : items) try { mx = Math.max(mx, Integer.parseInt(p.id.substring(1))); } catch (Exception e) { } return String.format("P%06d", mx + 1); }
        static P byId(String id) { for (P p : items) if (p.id.equals(id)) return p; return null; }
        static P bySkuWh(String sku, String wh) { for (P p : items) if (p.sku.equalsIgnoreCase(sku) && p.wh.equalsIgnoreCase(wh)) return p; return null; }
    }

    static ArrayList<P> seedItems() {
        Rng r = new Rng(2026); ArrayList<P> l = new ArrayList<>(); HashMap<String, Boolean> used = new HashMap<>(); int id = 1, skuN = 0;
        while (skuN < 700) {
            int c = r.nextInt(6); String name = MODS[r.nextInt(MODS.length)] + " " + NOUNS[c][r.nextInt(6)] + " " + SIZES[r.nextInt(SIZES.length)];
            if (used.put(name, true) != null) continue;
            skuN++; String sku = CP[c] + "-" + String.format("%04d", skuN); String sup = SUPS[(c * 2 + r.nextInt(2)) % SUPS.length];
            double price = (c == 1 ? 3 : c == 3 ? 5 : 1) * (10 + r.nextInt(400)) + r.nextInt(100) / 100.0;
            int[] perm = {0, 1, 2, 3, 4}; for (int i = 4; i > 0; i--) { int j = r.nextInt(i + 1); int t = perm[i]; perm[i] = perm[j]; perm[j] = t; }
            int nw = 3 + r.nextInt(3);
            for (int k = 0; k < nw; k++) {
                P p = new P(); p.id = String.format("P%06d", id++); p.sku = sku; p.name = name; p.cat = CATS[c]; p.wh = WH[perm[k]]; p.sup = sup; p.price = Math.round(price * 100) / 100.0;
                p.dem = 1 + r.nextInt(40); p.min = p.dem * (3 + r.nextInt(8)); int x = r.nextInt(100);
                p.qty = x < 5 ? 0 : x < 22 ? r.nextInt(p.min) : p.min + r.nextInt(p.min * 3 + 1);
                l.add(p);
            }
        }
        return l;
    }

    // ---------------- users / login ----------------
    static String sha(String s) {
        try { byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format("%02x", x)); return b.toString(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    static String[] login(String user, String pass) {
        try {
            Path f = DATA.resolve("users.csv");
            if (!Files.exists(f)) Files.write(f, ("username,salt,hash,role\nadmin,s1," + sha("s1admin123") + ",admin\nstaff,s2," + sha("s2staff123") + ",staff\n").getBytes(StandardCharsets.UTF_8));
            for (String ln : Files.readAllLines(f)) { String[] c = ln.split(","); if (c.length >= 4 && c[0].equals(user) && c[2].equals(sha(c[1] + pass))) return new String[]{c[0], c[3]}; }
        } catch (IOException e) { throw new RuntimeException(e); }
        return null;
    }

    // ======================================================================
    // PART C - MODULE FEATURES (shared by website and terminal)
    // ======================================================================

    /** Generic result table used by both GUI and terminal. */
    static final class Tbl {
        String title; String[] head; ArrayList<String[]> rows = new ArrayList<>(); ArrayList<String> notes = new ArrayList<>(); ArrayList<String[]> stats = new ArrayList<>(); ArrayList<Tbl> subs = new ArrayList<>();
        Tbl(String t, String... h) { title = t; head = h; }
        Tbl row(Object... c) { String[] s = new String[c.length]; for (int i = 0; i < c.length; i++) s[i] = String.valueOf(c[i]); rows.add(s); return this; }
        Tbl note(String n) { notes.add(n); return this; }
        Tbl stat(String k, Object v) { stats.add(new String[]{k, String.valueOf(v)}); return this; }
    }

    static String f2(double d) { return String.format(Locale.ROOT, "%.2f", d); }
    static String f1(double d) { return String.format(Locale.ROOT, "%.1f", d); }
    static String num(long d) { return String.format(Locale.ROOT, "%,d", d); }
    static void writeResult(String name, Tbl t) {
        if (QUIET) return;
        try {
            StringBuilder b = new StringBuilder(); b.append(String.join(",", t.head)).append('\n');
            for (String[] r : t.rows) { for (int i = 0; i < r.length; i++) { if (i > 0) b.append(','); b.append(r[i].replace(',', ';')); } b.append('\n'); }
            Files.write(RESULTS.resolve(name), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    // ---------------- Modules 1 + 2: intelligent search ----------------
    static final class Idx { long ver; String corpus; int[] off; String[] vocab; int[] freq; String[] low; }
    static Idx idx;

    static String[] tokenize(String s) {
        String low = s.toLowerCase(Locale.ROOT); ArrayList<String> out = new ArrayList<>(); int i = 0;
        while (i < low.length()) {
            while (i < low.length() && !Character.isLetterOrDigit(low.charAt(i))) i++;
            int j = i; while (j < low.length() && Character.isLetterOrDigit(low.charAt(j))) j++;
            if (j > i) out.add(low.substring(i, j)); i = j;
        }
        return out.toArray(new String[0]);
    }

    static Idx index() {
        synchronized (Store.LOCK) {
            Store.ensure();
            if (idx != null && idx.ver == Store.ver) return idx;
            Idx x = new Idx(); x.ver = Store.ver; int n = Store.items.size(); x.off = new int[n]; x.low = new String[n];
            StringBuilder b = new StringBuilder(); HashMap<String, Integer> cnt = new HashMap<>();
            for (int i = 0; i < n; i++) {
                P p = Store.items.get(i); x.off[i] = b.length();
                String ln = (p.name + " " + p.cat + " " + p.sup + " " + p.sku + " " + p.wh).toLowerCase(Locale.ROOT);
                x.low[i] = p.name.toLowerCase(Locale.ROOT); b.append(ln).append('\n');
                for (String t : tokenize(ln)) cnt.merge(t, 1, Integer::sum);
            }
            x.corpus = b.toString(); x.vocab = new String[cnt.size()]; x.freq = new int[cnt.size()]; int k = 0;
            for (String key : cnt.keySet()) { x.vocab[k] = key; x.freq[k++] = cnt.get(key); }
            idx = x; return x;
        }
    }

    static int recOf(Idx x, int pos) { int lo = 0, hi = x.off.length - 1; while (lo < hi) { int mid = (lo + hi + 1) >>> 1; if (x.off[mid] <= pos) lo = mid; else hi = mid - 1; } return lo; }

    static final class SR { ArrayList<P> list = new ArrayList<>(); String corrected; boolean fuzzy, partial; }

    static SR search(String q, String wh, String cat) {
        synchronized (Store.LOCK) {
            Idx x = index(); SR res = new SR(); int n = Store.items.size();
            String[] tok = tokenize(q == null ? "" : q); if (tok.length > 8) { String[] t2 = new String[8]; System.arraycopy(tok, 0, t2, 0, 8); tok = t2; }
            double[] score = new double[n]; boolean[] ok = new boolean[n];
            if (tok.length == 0) { for (int i = 0; i < n; i++) ok[i] = true; }
            else {
                int T = tok.length; String[] pats = new String[2 * T]; String[] corr = new String[T];
                for (int i = 0; i < T; i++) { pats[2 * i] = tok[i]; pats[2 * i + 1] = "\u0001x"; }
                int[] m = masks(x, pats);
                int[] hits = new int[T]; for (int r = 0; r < n; r++) for (int i = 0; i < T; i++) if ((m[r] & (1 << (2 * i))) != 0) hits[i]++;
                boolean any = false;
                for (int i = 0; i < T; i++) if (hits[i] == 0 && tok[i].length() >= 3) {      // Module 1: typo correction via edit distance over vocabulary
                    int maxd = tok[i].length() <= 3 ? 1 : 2, best = -1, bd = 99;
                    for (int v = 0; v < x.vocab.length; v++) {
                        if (Math.abs(x.vocab[v].length() - tok[i].length()) > maxd) continue;
                        int d = DP.edit(tok[i], x.vocab[v]);
                        if (d <= maxd && (d < bd || (d == bd && best >= 0 && x.freq[v] > x.freq[best]))) { bd = d; best = v; }
                    }
                    if (best >= 0) { corr[i] = x.vocab[best]; pats[2 * i + 1] = corr[i]; any = true; }
                }
                if (any) { m = masks(x, pats); res.fuzzy = true; StringBuilder b = new StringBuilder(); for (int i = 0; i < T; i++) b.append(i > 0 ? " " : "").append(corr[i] != null ? corr[i] : tok[i]); res.corrected = b.toString(); }
                String qn = String.join(" ", tok); int[] cntm = new int[n];
                for (int r = 0; r < n; r++) {
                    double sc = 0; int got = 0;
                    for (int i = 0; i < T; i++) { if ((m[r] & (1 << (2 * i))) != 0) { sc += 3; got++; } else if ((m[r] & (1 << (2 * i + 1))) != 0) { sc += 2; got++; } }
                    cntm[r] = got; score[r] = sc;
                    if (got == T) { ok[r] = true; if (T > 1 && Str.kmp(x.low[r], qn).length > 0) score[r] += 4; if (Store.items.get(r).sku.toLowerCase(Locale.ROOT).equals(qn)) score[r] += 6; }
                }
                boolean none = true; for (int r = 0; r < n; r++) if (ok[r]) { none = false; break; }
                if (none && T > 1) { res.partial = true; for (int r = 0; r < n; r++) if (cntm[r] > 0) ok[r] = true; }
            }
            IL sel = new IL();
            for (int r = 0; r < n; r++) { P p = Store.items.get(r); if (ok[r] && (wh == null || wh.isEmpty() || p.wh.equalsIgnoreCase(wh)) && (cat == null || cat.isEmpty() || p.cat.equalsIgnoreCase(cat))) sel.add(r); }
            int[] a = sel.arr(); double[] key = new double[n]; for (int r : a) key[r] = tok.length == 0 ? r : -score[r] + r * 1e-9;
            Rand.qsort(a, key, 0, a.length - 1, new Rng(7));
            for (int r : a) res.list.add(Store.items.get(r));
            return res;
        }
    }

    static int[] masks(Idx x, String[] pats) {
        int[] m = new int[x.off.length]; AC ac = new AC(pats);
        ac.scan(x.corpus, (end, pat) -> m[recOf(x, end)] |= 1 << pat);
        return m;
    }

    static Tbl tSearch(String q, String wh, String cat, int limit) {
        SR r = search(q, wh, cat); Tbl t = new Tbl("Search results (Modules 1+2: Aho-Corasick + KMP + edit-distance correction)", "ID", "SKU", "Product", "Warehouse", "Supplier", "Qty", "Min", "Status");
        if (r.fuzzy) t.note("Did you mean: " + r.corrected + " ?  (typo corrected with Damerau edit distance)");
        if (r.partial) t.note("No product matched every word; showing partial matches.");
        t.stat("matches", r.list.size());
        for (int i = 0; i < Math.min(limit, r.list.size()); i++) { P p = r.list.get(i); t.row(p.id, p.sku, p.name, p.wh, p.sup, p.qty, p.min, p.status()); }
        return t;
    }

    // ---------------- Module 3: replenishment (knapsack DP) ----------------
    static Tbl tReplenish(long budget, String wh, boolean apply) {
        synchronized (Store.LOCK) {
            Store.ensure(); IL c = new IL();
            for (int i = 0; i < Store.items.size(); i++) { P p = Store.items.get(i); if (p.qty < p.min && (wh == null || wh.isEmpty() || p.wh.equalsIgnoreCase(wh))) c.add(i); }
            int[] cand = c.arr(); int nc = cand.length; int[] order = new int[nc]; long[] cost = new long[nc], val = new long[nc]; double[] dens = new double[nc];
            for (int k = 0; k < nc; k++) {
                P p = Store.items.get(cand[k]); order[k] = 2 * p.min - p.qty; cost[k] = (long) Math.ceil(order[k] * p.price); if (cost[k] < 1) cost[k] = 1;
                val[k] = (long) order[k] * (p.dem + 1) * (p.qty == 0 ? 3 : 1); dens[k] = -(double) val[k] / cost[k];
            }
            int[] ord = new int[nc]; for (int i = 0; i < nc; i++) ord[i] = i; Rand.qsort(ord, dens, 0, nc - 1, new Rng(3));
            int keep = Math.min(nc, 400); int[] w = new int[keep]; long[] v = new long[keep]; long unit = Math.max(1, (long) Math.ceil(budget / 20000.0));
            for (int i = 0; i < keep; i++) { w[i] = (int) Math.max(1, (cost[ord[i]] + unit - 1) / unit); v[i] = val[ord[i]]; }
            int W = (int) (budget / unit); int[] sel = DP.knapsack(w, v, W);
            long gCost = 0, gVal = 0; for (int i = 0; i < keep; i++) if (gCost + cost[ord[i]] <= budget) { gCost += cost[ord[i]]; gVal += val[ord[i]]; }
            Tbl t = new Tbl("Replenishment plan (Module 3: 0/1 knapsack dynamic programming)", "ID", "SKU", "Product", "Warehouse", "Supplier", "Qty", "Min", "Order qty", "Cost", "Benefit");
            long spent = 0, tv = 0, units = 0;
            double[] sk = new double[keep]; for (int i = 0; i < keep; i++) sk[i] = -val[ord[i]] / (double) cost[ord[i]]; Rand.qsort(sel, sk, 0, sel.length - 1, new Rng(5));
            for (int s : sel) { int k = ord[s]; P p = Store.items.get(cand[k]); t.row(p.id, p.sku, p.name, p.wh, p.sup, p.qty, p.min, order[k], num(cost[k]), num(val[k])); spent += cost[k]; tv += val[k]; units += order[k]; }
            t.stat("budget", num(budget)).stat("spent", num(spent)).stat("left", num(budget - spent)).stat("items to order", sel.length).stat("units", num(units)).stat("products below minimum", nc);
            t.note("Benefit = order quantity x (daily demand + 1), tripled for out-of-stock items. DP total benefit " + num(tv) + " versus greedy value-density " + num(gVal) + ".");
            if (unit > 1) t.note("Costs were rounded up to units of " + unit + " rupees so the DP table stays small; the plan never exceeds the budget.");
            if (nc > keep) t.note("Considered the " + keep + " best benefit-per-rupee candidates out of " + nc + ".");
            if (apply) { for (int s : sel) { int k = ord[s]; Store.items.get(cand[k]).qty += order[k]; } if (sel.length > 0) Store.save(); t.note("ORDER PLACED: stock for " + sel.length + " products increased and saved to inventory.csv."); }
            writeResult("replenishment_plan.csv", t); return t;
        }
    }

    // ---------------- Module 4: warehouse balancing (max flow) ----------------
    static int[][] routes() {
        int[][] r = new int[5][5];
        try {
            Path f = DATA.resolve("routes.csv");
            if (!Files.exists(f)) { StringBuilder b = new StringBuilder("from,to,capacity_units\n"); for (int i = 0; i < 5; i++) for (int j = 0; j < 5; j++) if (i != j) b.append(WH[i]).append(',').append(WH[j]).append(',').append(40 + ((i * 7 + j * 3) % 5) * 20).append('\n'); Files.write(f, b.toString().getBytes()); }
            for (String ln : Files.readAllLines(f)) { String[] c = ln.split(","); if (c.length < 3 || c[0].equals("from")) continue; int a = wi(c[0]), b = wi(c[1]); if (a >= 0 && b >= 0) r[a][b] = Integer.parseInt(c[2].trim()); }
        } catch (IOException e) { throw new RuntimeException(e); }
        return r;
    }
    static int wi(String w) { for (int i = 0; i < WH.length; i++) if (WH[i].equalsIgnoreCase(w)) return i; return -1; }

    static Tbl tBalance(boolean apply) {
        synchronized (Store.LOCK) {
            Store.ensure(); int[][] route = routes(); HashMap<String, ArrayList<P>> g = new HashMap<>();
            for (P p : Store.items) g.computeIfAbsent(p.sku, k -> new ArrayList<>()).add(p);
            ArrayList<String> skus = new ArrayList<>(g.keySet()); String[] sk = skus.toArray(new String[0]); int[] ord = new int[sk.length]; for (int i = 0; i < ord.length; i++) ord[i] = i; Rand.qsortS(ord, sk, 0, ord.length - 1, new Rng(1));
            Tbl t = new Tbl("Warehouse transfer plan (Module 4: Edmonds-Karp maximum flow)", "SKU", "Product", "From", "To", "Units", "Note");
            long totalShort = 0, moved = 0, unmet = 0; int skusHandled = 0, legs = 0;
            for (int oi : ord) {
                ArrayList<P> rows = g.get(sk[oi]); int[][] cap = new int[7][7]; boolean[] has = new boolean[5]; P[] at = new P[5]; int sh = 0, su = 0;
                for (P p : rows) { int w = wi(p.wh); if (w < 0) continue; has[w] = true; at[w] = p; int s = Math.max(0, p.qty - 2 * p.min), d = Math.max(0, p.min - p.qty); cap[0][w + 1] = s; cap[w + 1][6] = d; su += s; sh += d; }
                if (sh == 0) continue; totalShort += sh;
                for (int a = 0; a < 5; a++) for (int b = 0; b < 5; b++) if (a != b && has[a] && has[b]) cap[a + 1][b + 1] = route[a][b];
                int[][] res = new int[7][7]; int fl = su == 0 ? 0 : Flow.maxflow(cap, 0, 6, res); if (su == 0) for (int a = 0; a < 7; a++) System.arraycopy(cap[a], 0, res[a], 0, 7);
                moved += fl; unmet += sh - fl; skusHandled++;
                for (int a = 1; a <= 5; a++) for (int b = 1; b <= 5; b++) { int net = cap[a][b] - res[a][b]; if (net > 0) { t.row(sk[oi], rows.get(0).name, WH[a - 1], WH[b - 1], net, "route capacity " + cap[a][b]); legs++; } }
                for (int w = 1; w <= 5; w++) if (has[w - 1]) { int need = cap[w][6], got = cap[w][6] - res[w][6]; if (need - got > 0) t.row(sk[oi], rows.get(0).name, "(none)", WH[w - 1], need - got, "UNMET: no spare stock or route"); }
                if (apply) for (int w = 1; w <= 5; w++) if (has[w - 1]) at[w - 1].qty += (cap[w][6] - res[w][6]) - (cap[0][w] - res[0][w]);
            }
            t.stat("SKUs short somewhere", skusHandled).stat("units short", num(totalShort)).stat("units movable", num(moved)).stat("unmet units", num(unmet)).stat("transfer legs", legs);
            t.note("Network per SKU: source -> warehouses with surplus (stock above 2 x minimum), warehouse -> warehouse routes with capacities from data/routes.csv, warehouses below minimum -> sink.");
            if (apply) { if (moved > 0) Store.save(); t.note("TRANSFERS APPLIED: " + num(moved) + " units moved and saved to inventory.csv."); }
            writeResult("balancing_plan.csv", t); return t;
        }
    }

    // ---------------- Module 5: risk & priority (approximation) ----------------
    static Tbl tRisk(int top) {
        synchronized (Store.LOCK) {
            Store.ensure(); int n = Store.items.size(); double[] key = new double[n]; int[] a = new int[n];
            for (int i = 0; i < n; i++) { a[i] = i; key[i] = -Store.items.get(i).risk() + i * 1e-9; }
            Rand.qsort(a, key, 0, n - 1, new Rng(11));
            Tbl t = new Tbl("Priority list (Module 5: risk score ranking, ordered by randomised quicksort)", "Rank", "ID", "SKU", "Product", "Warehouse", "Qty", "Min", "Demand/day", "Days cover", "Risk");
            for (int i = 0; i < Math.min(top, n); i++) { P p = Store.items.get(a[i]); t.row(i + 1, p.id, p.sku, p.name, p.wh, p.qty, p.min, p.dem, f1(p.days()), f1(p.risk())); }
            double[] wr = new double[5]; int[] wc = new int[5], wl = new int[5]; int atRisk = 0;
            for (P p : Store.items) { int w = wi(p.wh); if (w < 0) continue; if (p.risk() >= 60) { wr[w] += p.risk(); wc[w]++; atRisk++; } if (p.qty < p.min) wl[w]++; }
            Tbl wt = new Tbl("Warehouse attention order", "Warehouse", "At-risk products", "Below minimum", "Total risk"); int[] wo = {0, 1, 2, 3, 4}; double[] wk = new double[5]; for (int i = 0; i < 5; i++) wk[i] = -wr[i] + i * 1e-9; Rand.qsort(wo, wk, 0, 4, new Rng(2));
            for (int w : wo) wt.row(WH[w], wc[w], wl[w], f1(wr[w])); t.subs.add(wt);
            int[] eid = new int[n]; for (int i = 0; i < n; i++) eid[i] = -1; int U = 0;
            for (int i = 0; i < n; i++) if (Store.items.get(i).risk() >= 60) eid[i] = U++;
            ArrayList<IL> sets = new ArrayList<>(); ArrayList<String> sn = new ArrayList<>();
            for (int i = 0; i < n; i++) if (eid[i] >= 0) { String s = Store.items.get(i).sup; int k = sn.indexOf(s); if (k < 0) { sn.add(s); sets.add(new IL()); k = sn.size() - 1; } sets.get(k).add(eid[i]); }
            int[][] ss = new int[sets.size()][]; int maxs = 0; for (int i = 0; i < ss.length; i++) { ss[i] = sets.get(i).arr(); maxs = Math.max(maxs, ss[i].length); }
            IL gain = new IL(); int[] pick = Approx.setCover(ss, U, gain);
            Tbl st = new Tbl("Which suppliers to contact first (greedy set cover, ln n approximation)", "Step", "Supplier", "Newly covered products", "Covered so far", "% of at-risk");
            int cum = 0; for (int i = 0; i < pick.length; i++) { cum += gain.a[i]; st.row(i + 1, sn.get(pick[i]), gain.a[i], cum, f1(100.0 * cum / Math.max(1, U))); }
            double H = 0; for (int i = 1; i <= Math.max(1, maxs); i++) H += 1.0 / i;
            st.note("Minimum set cover is NP-hard. Greedy never uses more than H(d) x optimum suppliers; here d=" + maxs + " so H(d)=" + f2(H) + ", and at least " + (int) Math.ceil(U / (double) Math.max(1, maxs)) + " supplier(s) are needed.");
            t.subs.add(st); t.stat("products at risk (score >= 60)", atRisk).stat("suppliers to contact", pick.length).stat("total products", n);
            writeResult("risk_priority.csv", t); return t;
        }
    }

    // ---------------- Module 6: cycle count (reservoir sampling) ----------------
    static Tbl tCycle(int n, String wh, long seed) {
        synchronized (Store.LOCK) {
            Store.ensure(); Rand.Reservoir rs = new Rand.Reservoir(Math.max(1, n), new Rng(seed));
            for (P p : Store.items) if (wh == null || wh.isEmpty() || p.wh.equalsIgnoreCase(wh)) rs.offer(p);
            Tbl t = new Tbl("Cycle count sample (Module 6: reservoir sampling, Algorithm R)", "ID", "SKU", "Product", "Warehouse", "System qty");
            for (int i = 0; i < rs.size(); i++) { P p = (P) rs.a[i]; t.row(p.id, p.sku, p.name, p.wh, p.qty); }
            t.stat("population", num(rs.seen)).stat("sample size", rs.size()).stat("seed", seed);
            t.note("Every product has exactly the same chance (k/N) of being picked, in one pass and O(k) memory.");
            writeResult("cycle_count_sample.csv", t); return t;
        }
    }
    static String recordCount(String id, int counted, boolean adjust) {
        synchronized (Store.LOCK) {
            Store.ensure(); P p = Store.byId(id); if (p == null) throw new ApiEx(404, "unknown product " + id); if (counted < 0) throw new ApiEx(400, "counted quantity must be 0 or more");
            try {
                Path f = RESULTS.resolve("cycle_counts.csv"); String ln = java.time.LocalDateTime.now().withNano(0) + "," + p.id + "," + p.sku + "," + p.wh + "," + p.qty + "," + counted + "," + (counted - p.qty) + "," + (adjust ? "adjusted" : "logged") + "\n";
                if (!Files.exists(f)) Files.write(f, "time,id,sku,warehouse,system_qty,counted_qty,variance,action\n".getBytes());
                Files.write(f, ln.getBytes(), StandardOpenOption.APPEND);
            } catch (IOException e) { throw new RuntimeException(e); }
            int var = counted - p.qty; if (adjust && var != 0) { p.qty = counted; Store.save(); }
            return "Variance " + var + (adjust ? (var != 0 ? " - stock adjusted to " + counted : " - no change needed") : " - logged only");
        }
    }

    // ---------------- Module 2 lab ----------------
    static Tbl tStrings(String pattern, String algo) {
        Idx x = index(); String text = x.corpus; Tbl t = new Tbl("String algorithms on the product corpus (Module 2)", "Algorithm", "Matches", "Time (microseconds)", "Check");
        String p = pattern == null ? "" : pattern.toLowerCase(Locale.ROOT); if (p.trim().isEmpty()) throw new ApiEx(400, "enter a pattern");
        t.stat("corpus characters", num(text.length())).stat("products", x.off.length);
        if (p.contains(",")) {
            ArrayList<String> l = new ArrayList<>(); for (String s : p.split(",")) if (!s.trim().isEmpty()) l.add(s.trim()); String[] pa = l.toArray(new String[0]); int[] c = new int[pa.length]; long t0 = System.nanoTime();
            new AC(pa).scan(text, (e, k) -> c[k]++); long us = (System.nanoTime() - t0) / 1000;
            for (int i = 0; i < pa.length; i++) t.row("Aho-Corasick: \"" + pa[i] + "\"", c[i], us, "all patterns found in one pass"); return t;
        }
        int ref = -1; boolean all = algo == null || algo.isEmpty() || algo.equals("all");
        for (String a : new String[]{"kmp", "z", "rk", "sa", "ac"}) {
            if (!all && !a.equals(algo)) continue; long t0 = System.nanoTime(); int cnt;
            if (a.equals("kmp")) cnt = Str.kmp(text, p).length; else if (a.equals("z")) cnt = Str.z(text, p).length; else if (a.equals("rk")) cnt = Str.rk(text, p).length;
            else if (a.equals("sa")) { SA sa = new SA(text); cnt = sa.count(p); } else { int[] c = new int[1]; new AC(new String[]{p}).scan(text, (e, k) -> c[0]++); cnt = c[0]; }
            long us = (System.nanoTime() - t0) / 1000; if (ref < 0) ref = cnt;
            String nm = a.equals("kmp") ? "Knuth-Morris-Pratt" : a.equals("z") ? "Z-function" : a.equals("rk") ? "Rabin-Karp (double hash)" : a.equals("sa") ? "Suffix array (build + binary search)" : "Aho-Corasick";
            t.row(nm, cnt, us, cnt == ref ? "agrees" : "MISMATCH");
        }
        if (all) { SA sa = new SA(text); t.note("Distinct substrings in the corpus (suffix array + Kasai LCP): " + num(sa.distinctSubstrings())); }
        return t;
    }

    // ---------------- Module 6 (+2,1): large inventory processing ----------------
    static Path largeFile() { return DATA.resolve("inventory_large.csv"); }
    static volatile long genDone, genTotal; static volatile boolean genRunning; static volatile String genMsg = "";

    static void generateLarge(Path f, long rows) throws IOException {
        genRunning = true; genDone = 0; genTotal = rows; Rng r = new Rng(20260704);
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f.toFile()), StandardCharsets.ISO_8859_1), 1 << 20)) {
            StringBuilder b = new StringBuilder();
            for (long i = 1; i <= rows; i++) {
                int c = r.nextInt(6); int dem = 1 + r.nextInt(40); int min = dem * (3 + r.nextInt(8)); int x = r.nextInt(100); int qty = x < 5 ? 0 : x < 22 ? r.nextInt(min) : min + r.nextInt(min * 3 + 1);
                long cents = (c == 1 ? 3 : c == 3 ? 5 : 1) * (1000 + r.nextInt(40000)) + r.nextInt(100);
                b.setLength(0); b.append('L'); String is = Long.toString(i); for (int z = is.length(); z < 8; z++) b.append('0'); b.append(is).append(',').append(CP[c]).append('-').append(r.nextInt(100000)).append(',')
                    .append(MODS[r.nextInt(MODS.length)]).append(' ').append(NOUNS[c][r.nextInt(6)]).append(' ').append(SIZES[r.nextInt(SIZES.length)]).append(',')
                    .append(CATS[c]).append(',').append(WH[r.nextInt(5)]).append(',').append(SUPS[(c * 2 + r.nextInt(2)) % SUPS.length]).append(',')
                    .append(qty).append(',').append(min).append(',').append(cents / 100).append('.').append(cents % 100 < 10 ? "0" : "").append(cents % 100).append(',').append(dem).append('\n');
                w.write(b.toString()); if ((i & 0xFFFF) == 0) genDone = i;
            }
        } finally { genRunning = false; }
        genDone = rows; Files.write(Paths.get(f.toString() + ".meta"), String.valueOf(rows).getBytes());
    }
    static long largeRows(Path f) { try { Path m = Paths.get(f.toString() + ".meta"); return Files.exists(m) ? Long.parseLong(new String(Files.readAllBytes(m)).trim()) : -1; } catch (Exception e) { return -1; } }

    static final class Agg { long[] rows = new long[5], units = new long[5], cents = new long[5], low = new long[5], zero = new long[5], crows = new long[6], cunits = new long[6]; long bad; }

    static Tbl tLargeAgg(Path f, int threads) throws Exception {
        if (!Files.exists(f)) throw new ApiEx(400, "Large dataset not generated yet - generate it first");
        long len = Files.size(f); Agg[] ag = new Agg[threads]; Thread[] th = new Thread[threads]; Exception[] err = new Exception[1]; long t0 = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            final int id = i; final long st = len / threads * i, en = id == threads - 1 ? len : len / threads * (i + 1); ag[i] = new Agg();
            th[i] = new Thread(() -> { try { scanRange(f, st, en, ag[id]); } catch (Exception e) { err[0] = e; } }); th[i].start();
        }
        for (Thread t : th) t.join(); if (err[0] != null) throw err[0];
        Agg a = ag[0]; for (int i = 1; i < threads; i++) { for (int k = 0; k < 5; k++) { a.rows[k] += ag[i].rows[k]; a.units[k] += ag[i].units[k]; a.cents[k] += ag[i].cents[k]; a.low[k] += ag[i].low[k]; a.zero[k] += ag[i].zero[k]; } for (int k = 0; k < 6; k++) { a.crows[k] += ag[i].crows[k]; a.cunits[k] += ag[i].cunits[k]; } a.bad += ag[i].bad; }
        double sec = (System.nanoTime() - t0) / 1e9; long tot = 0, tc = 0, tl = 0; for (int k = 0; k < 5; k++) { tot += a.rows[k]; tc += a.cents[k]; tl += a.low[k]; }
        Tbl t = new Tbl("Warehouse totals over the large dataset (Module 6: parallel aggregation, map + reduce)", "Warehouse", "Rows", "Units", "Stock value", "Below minimum", "Out of stock");
        for (int k = 0; k < 5; k++) t.row(WH[k], num(a.rows[k]), num(a.units[k]), num(a.cents[k] / 100), num(a.low[k]), num(a.zero[k]));
        Tbl ct = new Tbl("Category totals", "Category", "Rows", "Units"); for (int k = 0; k < 6; k++) ct.row(CATS[k], num(a.crows[k]), num(a.cunits[k])); t.subs.add(ct);
        t.stat("rows", num(tot)).stat("threads", threads).stat("seconds", f2(sec)).stat("rows/second", num((long) (tot / Math.max(sec, 1e-9)))).stat("total value", num(tc / 100)).stat("below minimum", num(tl)).stat("bad rows", a.bad);
        t.note("Work T1 is one pass over the file; with p threads the span is about T1/p plus the cheap merge of per-thread counters (work/span reasoning).");
        writeResult("large_aggregate.csv", t); return t;
    }

    static void scanRange(Path f, long start, long end, Agg a) throws IOException {
        long pos = start;
        if (start > 0) { try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "r")) { pos = start - 1; raf.seek(pos); int b; while ((b = raf.read()) != -1) { pos++; if (b == '\n') break; } } }
        try (FileInputStream fis = new FileInputStream(f.toFile())) {
            fis.getChannel().position(pos); BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.ISO_8859_1), 1 << 20); String ln; int[] cm = new int[10];
            while (pos < end && (ln = br.readLine()) != null) {
                pos += ln.length() + 1; int k = 0;
                for (int i = 0; i < ln.length() && k < 9; i++) if (ln.charAt(i) == ',') cm[k++] = i;
                if (k < 9) { a.bad++; continue; }
                String cat = ln.substring(cm[2] + 1, cm[3]), wh = ln.substring(cm[3] + 1, cm[4]); int ci = -1, wi = -1;
                for (int i = 0; i < 6; i++) if (CATS[i].equals(cat)) ci = i;
                for (int i = 0; i < 5; i++) if (WH[i].equals(wh)) wi = i;
                if (ci < 0 || wi < 0) { a.bad++; continue; }
                int qty = pint(ln, cm[5] + 1, cm[6]), min = pint(ln, cm[6] + 1, cm[7]); long cents = 0; for (int i = cm[7] + 1; i < cm[8]; i++) { char ch = ln.charAt(i); if (ch != '.') cents = cents * 10 + (ch - '0'); }
                a.rows[wi]++; a.units[wi] += qty; a.cents[wi] += cents * qty; if (qty < min) a.low[wi]++; if (qty == 0) a.zero[wi]++; a.crows[ci]++; a.cunits[ci] += qty;
            }
        }
    }
    static int pint(String s, int a, int b) { int v = 0; for (int i = a; i < b; i++) v = v * 10 + (s.charAt(i) - '0'); return v; }
    static String field(String ln, int idx) { int s = 0; for (int k = 0; k < idx; k++) { s = ln.indexOf(',', s) + 1; if (s == 0) return ""; } int e = ln.indexOf(',', s); return e < 0 ? ln.substring(s) : ln.substring(s, e); }

    static Tbl tLargeSearch(Path f, String q, String algo, int limit) throws IOException {
        if (!Files.exists(f)) throw new ApiEx(400, "Large dataset not generated yet - generate it first"); String p = q.toLowerCase(Locale.ROOT).trim(); if (p.isEmpty()) throw new ApiEx(400, "enter a search text");
        int[] lps = Str.lps(p); long cnt = 0, rows = 0; long t0 = System.nanoTime(); Tbl t = new Tbl("Streaming search over the large dataset (Module 2 on every row)", "ID", "SKU", "Product", "Warehouse", "Qty");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f.toFile()), StandardCharsets.ISO_8859_1), 1 << 20)) {
            String ln; while ((ln = br.readLine()) != null) {
                rows++; String name = field(ln, 2).toLowerCase(Locale.ROOT); boolean m;
                if (algo.equals("z")) m = Str.z(name, p).length > 0; else if (algo.equals("rk")) m = Str.rk(name, p).length > 0; else m = Str.kmpHas(name, p, lps);
                if (m) { cnt++; if (t.rows.size() < limit) t.row(field(ln, 0), field(ln, 1), field(ln, 2), field(ln, 4), field(ln, 6)); }
            }
        }
        t.stat("rows scanned", num(rows)).stat("matching rows", num(cnt)).stat("algorithm", algo.toUpperCase()).stat("seconds", f2((System.nanoTime() - t0) / 1e9)); return t;
    }

    static Tbl tLargeSample(Path f, int n, long seed) throws IOException {
        if (!Files.exists(f)) throw new ApiEx(400, "Large dataset not generated yet - generate it first"); Rand.Reservoir rs = new Rand.Reservoir(n, new Rng(seed)); long t0 = System.nanoTime();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f.toFile()), StandardCharsets.ISO_8859_1), 1 << 20)) { String ln; while ((ln = br.readLine()) != null) rs.offer(ln); }
        Tbl t = new Tbl("Cycle-count sample drawn from the large dataset (reservoir sampling, one pass)", "ID", "SKU", "Product", "Warehouse", "System qty");
        for (int i = 0; i < rs.size(); i++) { String ln = (String) rs.a[i]; t.row(field(ln, 0), field(ln, 1), field(ln, 2), field(ln, 4), field(ln, 6)); }
        t.stat("rows streamed", num(rs.seen)).stat("sample", rs.size()).stat("seconds", f2((System.nanoTime() - t0) / 1e9)); writeResult("large_cycle_sample.csv", t); return t;
    }

    static Tbl tLargePct(Path f) throws IOException {
        if (!Files.exists(f)) throw new ApiEx(400, "Large dataset not generated yet - generate it first"); IL q = new IL(); long t0 = System.nanoTime();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f.toFile()), StandardCharsets.ISO_8859_1), 1 << 20)) { String ln; while ((ln = br.readLine()) != null) { String s = field(ln, 6); if (!s.isEmpty()) q.add(Integer.parseInt(s)); } }
        int[] a = q.arr(); Rng r = new Rng(9); Tbl t = new Tbl("Stock quantity percentiles (Module 6: randomised quickselect, expected O(n))", "Percentile", "Quantity");
        double[] ps = {0, 25, 50, 75, 90, 99, 100}; for (double pc : ps) t.row("p" + (int) pc, Rand.qselect(a, (int) Math.min(a.length - 1, Math.floor(pc / 100.0 * (a.length - 1))), r));
        t.stat("rows", num(a.length)).stat("seconds", f2((System.nanoTime() - t0) / 1e9)); writeResult("large_percentiles.csv", t); return t;
    }

    // ======================================================================
    // PART D - WEB SERVER (localhost) AND REST API
    // ======================================================================

    static final ConcurrentHashMap<String, String[]> sessions = new ConcurrentHashMap<>();
    static final class ApiEx extends RuntimeException { final int code; ApiEx(int c, String m) { super(m); code = c; } }

    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) { if (c == '"') b.append("\\\""); else if (c == '\\') b.append("\\\\"); else if (c == '\n') b.append("\\n"); else if (c < 32) b.append(' '); else b.append(c); }
        return b.append('"').toString();
    }
    static String jt(Tbl t) {
        StringBuilder b = new StringBuilder("{\"title\":" + q(t.title) + ",\"notes\":[");
        for (int i = 0; i < t.notes.size(); i++) b.append(i > 0 ? "," : "").append(q(t.notes.get(i)));
        b.append("],\"stats\":["); for (int i = 0; i < t.stats.size(); i++) b.append(i > 0 ? "," : "").append("[" + q(t.stats.get(i)[0]) + "," + q(t.stats.get(i)[1]) + "]");
        b.append("],\"head\":["); for (int i = 0; i < t.head.length; i++) b.append(i > 0 ? "," : "").append(q(t.head[i]));
        b.append("],\"rows\":["); for (int i = 0; i < t.rows.size(); i++) { b.append(i > 0 ? "," : "").append('['); String[] r = t.rows.get(i); for (int k = 0; k < r.length; k++) b.append(k > 0 ? "," : "").append(q(r[k])); b.append(']'); }
        b.append("],\"subs\":["); for (int i = 0; i < t.subs.size(); i++) b.append(i > 0 ? "," : "").append(jt(t.subs.get(i))); return b.append("]}").toString();
    }
    static String pj(P p) {
        return "{\"id\":" + q(p.id) + ",\"sku\":" + q(p.sku) + ",\"name\":" + q(p.name) + ",\"category\":" + q(p.cat) + ",\"warehouse\":" + q(p.wh) + ",\"supplier\":" + q(p.sup)
            + ",\"qty\":" + p.qty + ",\"min\":" + p.min + ",\"price\":" + f2(p.price) + ",\"demand\":" + p.dem + ",\"status\":" + q(p.status()) + ",\"risk\":" + f1(p.risk()) + "}";
    }
    static String clean(String s) { return s == null ? "" : s.replace(',', ' ').replace('\n', ' ').replace('\r', ' ').replace('"', ' ').trim(); }
    static int pi(HashMap<String, String> m, String k, int def) { try { return Integer.parseInt(m.get(k).trim()); } catch (Exception e) { return def; } }
    static long pl(HashMap<String, String> m, String k, long def) { try { return Long.parseLong(m.get(k).trim()); } catch (Exception e) { return def; } }
    static String ps(HashMap<String, String> m, String k) { String v = m.get(k); return v == null ? "" : v; }

    static HashMap<String, String> parseForm(String s) {
        HashMap<String, String> m = new HashMap<>(); if (s == null || s.isEmpty()) return m;
        for (String kv : s.split("&")) { int i = kv.indexOf('='); try { if (i < 0) m.put(URLDecoder.decode(kv, "UTF-8"), ""); else m.put(URLDecoder.decode(kv.substring(0, i), "UTF-8"), URLDecoder.decode(kv.substring(i + 1), "UTF-8")); } catch (Exception e) { } }
        return m;
    }

    static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type); ex.getResponseHeaders().set("Cache-Control", "no-store"); ex.sendResponseHeaders(code, body.length); try (OutputStream o = ex.getResponseBody()) { o.write(body); }
    }

    static void route(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!path.startsWith("/api/")) {
            Path f = WEB.resolve("index.html");
            if (path.equals("/") || path.equals("/index.html")) { if (!Files.exists(f)) send(ex, 404, "text/plain", "src/web/index.html missing".getBytes()); else send(ex, 200, "text/html; charset=utf-8", Files.readAllBytes(f)); }
            else send(ex, 404, "text/plain", "not found".getBytes());
            return;
        }
        try {
            HashMap<String, String> m = parseForm(ex.getRequestURI().getRawQuery());
            if (ex.getRequestMethod().equals("POST")) m.putAll(parseForm(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            String body = api(path, m, ex.getRequestHeaders().getFirst("X-Token"));
            send(ex, 200, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
        } catch (ApiEx e) { send(ex, e.code, "application/json", ("{\"error\":" + q(e.getMessage()) + "}").getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { e.printStackTrace(); send(ex, 500, "application/json", ("{\"error\":" + q(String.valueOf(e.getMessage())) + "}").getBytes(StandardCharsets.UTF_8)); }
    }

    static P fill(P p, HashMap<String, String> m) {
        String sku = clean(ps(m, "sku")), name = clean(ps(m, "name")), cat = clean(ps(m, "category")), wh = clean(ps(m, "warehouse")), sup = clean(ps(m, "supplier"));
        if (sku.isEmpty() || name.isEmpty()) throw new ApiEx(400, "SKU and name are required");
        if (wi(wh) < 0) throw new ApiEx(400, "Warehouse must be one of " + String.join(", ", WH));
        if (cat.isEmpty() || sup.isEmpty()) throw new ApiEx(400, "Category and supplier are required");
        int qty = pi(m, "qty", -1), min = pi(m, "min", -1), dem = pi(m, "demand", -1); double price; try { price = Double.parseDouble(ps(m, "price")); } catch (Exception e) { price = -1; }
        if (qty < 0 || min < 0 || dem < 0 || price < 0) throw new ApiEx(400, "Quantity, minimum, demand and price must be non-negative numbers");
        P dup = Store.bySkuWh(sku, wh); if (dup != null && dup != p) throw new ApiEx(409, "SKU " + sku + " already exists in " + WH[wi(wh)]);
        p.sku = sku; p.name = name; p.cat = cat; p.wh = WH[wi(wh)]; p.sup = sup; p.qty = qty; p.min = min; p.dem = dem; p.price = price; return p;
    }

    static String api(String path, HashMap<String, String> m, String token) throws Exception {
        if (path.equals("/api/login")) {
            String[] u = login(ps(m, "user"), ps(m, "pass")); if (u == null) throw new ApiEx(401, "Wrong username or password");
            byte[] r = new byte[16]; new SecureRandom().nextBytes(r); StringBuilder b = new StringBuilder(); for (byte x : r) b.append(String.format("%02x", x)); sessions.put(b.toString(), u);
            return "{\"token\":" + q(b.toString()) + ",\"user\":" + q(u[0]) + ",\"role\":" + q(u[1]) + "}";
        }
        String[] who = token == null ? null : sessions.get(token); if (who == null) throw new ApiEx(401, "Please log in");
        switch (path) {
            case "/api/logout": sessions.remove(token); return "{\"ok\":true}";
            case "/api/meta": { StringBuilder b = new StringBuilder("{\"warehouses\":["); for (int i = 0; i < WH.length; i++) b.append(i > 0 ? "," : "").append(q(WH[i])); b.append("],\"categories\":["); for (int i = 0; i < CATS.length; i++) b.append(i > 0 ? "," : "").append(q(CATS[i])); b.append("],\"suppliers\":["); for (int i = 0; i < SUPS.length; i++) b.append(i > 0 ? "," : "").append(q(SUPS[i])); return b.append("],\"role\":" + q(who[1]) + ",\"user\":" + q(who[0]) + "}").toString(); }
            case "/api/dashboard": return dashboard();
            case "/api/products": {
                SR r = search(ps(m, "q"), ps(m, "wh"), ps(m, "cat"));
                ArrayList<P> l = r.list; String sort = ps(m, "sort"); boolean desc = ps(m, "dir").equals("desc");
                if (!sort.isEmpty() && l.size() > 1) {
                    int n = l.size(); int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i;
                    if (sort.equals("name") || sort.equals("sku") || sort.equals("wh") || sort.equals("status")) { String[] k = new String[n]; for (int i = 0; i < n; i++) { P p = l.get(i); k[i] = sort.equals("name") ? p.name : sort.equals("sku") ? p.sku : sort.equals("wh") ? p.wh : p.status(); } Rand.qsortS(a, k, 0, n - 1, new Rng(4)); }
                    else { double[] k = new double[n]; for (int i = 0; i < n; i++) { P p = l.get(i); k[i] = (sort.equals("qty") ? p.qty : sort.equals("price") ? p.price : sort.equals("risk") ? p.risk() : p.min) + i * 1e-9; } Rand.qsort(a, k, 0, n - 1, new Rng(4)); }
                    ArrayList<P> s = new ArrayList<>(); for (int i = 0; i < n; i++) s.add(l.get(desc ? a[n - 1 - i] : a[i])); l = s;
                }
                int size = Math.max(1, Math.min(200, pi(m, "size", 25))), pages = Math.max(1, (l.size() + size - 1) / size), page = Math.max(1, Math.min(pages, pi(m, "page", 1)));
                StringBuilder b = new StringBuilder("{\"total\":" + l.size() + ",\"page\":" + page + ",\"pages\":" + pages + ",\"corrected\":" + (r.fuzzy ? q(r.corrected) : "null") + ",\"partial\":" + r.partial + ",\"items\":[");
                for (int i = (page - 1) * size; i < Math.min(l.size(), page * size); i++) b.append(i > (page - 1) * size ? "," : "").append(pj(l.get(i)));
                return b.append("]}").toString();
            }
            case "/api/products/add": { synchronized (Store.LOCK) { Store.ensure(); P p = fill(new P(), m); p.id = Store.nextId(); Store.items.add(p); Store.save(); return pj(p); } }
            case "/api/products/update": { synchronized (Store.LOCK) { Store.ensure(); P p = Store.byId(ps(m, "id")); if (p == null) throw new ApiEx(404, "Product not found (it may have been deleted elsewhere)"); fill(p, m); Store.save(); return pj(p); } }
            case "/api/products/delete": { if (!who[1].equals("admin")) throw new ApiEx(403, "Only admin can delete products"); synchronized (Store.LOCK) { Store.ensure(); P p = Store.byId(ps(m, "id")); if (p == null) throw new ApiEx(404, "Product not found"); Store.items.remove(p); Store.save(); return "{\"ok\":true}"; } }
            case "/api/replenish": return jt(tReplenish(Math.max(1, pl(m, "budget", 100000)), ps(m, "wh"), false));
            case "/api/replenish/apply": return jt(tReplenish(Math.max(1, pl(m, "budget", 100000)), ps(m, "wh"), true));
            case "/api/balance": return jt(tBalance(false));
            case "/api/balance/apply": return jt(tBalance(true));
            case "/api/risk": return jt(tRisk(Math.max(1, pi(m, "top", 20))));
            case "/api/cycle": return jt(tCycle(Math.max(1, Math.min(500, pi(m, "n", 10))), ps(m, "wh"), pl(m, "seed", System.nanoTime() & 0xFFFFFF)));
            case "/api/cycle/record": return "{\"message\":" + q(recordCount(ps(m, "id"), pi(m, "counted", -1), ps(m, "adjust").equals("1"))) + "}";
            case "/api/strings": return jt(tStrings(ps(m, "pattern"), ps(m, "algo")));
            case "/api/large/status": { Path f = largeFile(); return "{\"exists\":" + Files.exists(f) + ",\"bytes\":" + (Files.exists(f) ? Files.size(f) : 0) + ",\"rows\":" + largeRows(f) + ",\"running\":" + genRunning + ",\"done\":" + genDone + ",\"total\":" + genTotal + ",\"msg\":" + q(genMsg) + "}"; }
            case "/api/large/generate": {
                if (genRunning) throw new ApiEx(409, "Generation already running"); final long rows = Math.max(1000, Math.min(10_000_000L, pl(m, "rows", 10_000_000L))); genRunning = true; genMsg = "";
                Thread t = new Thread(() -> { try { generateLarge(largeFile(), rows); genMsg = "done"; } catch (Exception e) { genMsg = "failed: " + e.getMessage(); genRunning = false; } }); t.start(); return "{\"started\":true}";
            }
            case "/api/large/aggregate": return jt(tLargeAgg(largeFile(), Math.max(1, Math.min(32, pi(m, "threads", Math.max(2, Runtime.getRuntime().availableProcessors()))))));
            case "/api/large/search": return jt(tLargeSearch(largeFile(), ps(m, "q"), ps(m, "algo").isEmpty() ? "kmp" : ps(m, "algo"), 25));
            case "/api/large/sample": return jt(tLargeSample(largeFile(), Math.max(1, Math.min(200, pi(m, "n", 10))), pi(m, "seed", 1)));
            case "/api/large/percentiles": return jt(tLargePct(largeFile()));
            default: throw new ApiEx(404, "Unknown API " + path);
        }
    }

    static String dashboard() {
        synchronized (Store.LOCK) {
            Store.ensure(); int n = Store.items.size(), low = 0, out = 0, sur = 0; long units = 0; double val = 0; int[] wc = new int[5], wl = new int[5]; long[] wu = new long[5]; double[] wv = new double[5]; int[] cc = new int[6], cl = new int[6];
            for (P p : Store.items) {
                units += p.qty; val += p.qty * p.price; if (p.qty == 0) out++; if (p.qty < p.min) low++; if (p.status().equals("SURPLUS")) sur++;
                int w = wi(p.wh); if (w >= 0) { wc[w]++; wu[w] += p.qty; wv[w] += p.qty * p.price; if (p.qty < p.min) wl[w]++; } for (int c = 0; c < 6; c++) if (CATS[c].equalsIgnoreCase(p.cat)) { cc[c]++; if (p.qty < p.min) cl[c]++; }
            }
            StringBuilder b = new StringBuilder("{\"version\":" + q(Store.mtime + ":" + n) + ",\"products\":" + n + ",\"units\":" + units + ",\"value\":" + f2(val) + ",\"low\":" + low + ",\"out\":" + out + ",\"surplus\":" + sur + ",\"warehouses\":[");
            for (int i = 0; i < 5; i++) b.append(i > 0 ? "," : "").append("{\"name\":" + q(WH[i]) + ",\"products\":" + wc[i] + ",\"units\":" + wu[i] + ",\"value\":" + f2(wv[i]) + ",\"low\":" + wl[i] + "}");
            b.append("],\"categories\":["); for (int i = 0; i < 6; i++) b.append(i > 0 ? "," : "").append("{\"name\":" + q(CATS[i]) + ",\"products\":" + cc[i] + ",\"low\":" + cl[i] + "}");
            int[] a = new int[n]; double[] k = new double[n]; for (int i = 0; i < n; i++) { a[i] = i; k[i] = -Store.items.get(i).risk() + i * 1e-9; } Rand.qsort(a, k, 0, n - 1, new Rng(5));
            b.append("],\"attention\":["); for (int i = 0; i < Math.min(8, n); i++) b.append(i > 0 ? "," : "").append(pj(Store.items.get(a[i])));
            return b.append("]}").toString();
        }
    }

    static void serve(int port) throws IOException {
        Store.ensure(); HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        s.createContext("/", ex -> { try { route(ex); } catch (Throwable t) { t.printStackTrace(); } finally { ex.close(); } });
        s.setExecutor(Executors.newFixedThreadPool(8)); s.start();
        System.out.println("Warehouse system running.  Open  http://localhost:" + port + "  in your browser.   Logins: admin/admin123 (full), staff/staff123 (no delete)");
        System.out.println("Data folder: " + DATA + "   (press Ctrl+C to stop)");
    }

    // ======================================================================
    // PART E - TERMINAL APPLICATION AND SELF-TESTS
    // ======================================================================

    static void print(Tbl t) { print(t, ""); }
    static void print(Tbl t, String ind) {
        System.out.println("\n" + ind + "== " + t.title + " ==");
        for (String[] s : t.stats) System.out.println(ind + "  " + s[0] + ": " + s[1]);
        for (String n : t.notes) System.out.println(ind + "  * " + n);
        int[] w = new int[t.head.length]; for (int i = 0; i < w.length; i++) w[i] = t.head[i].length();
        for (String[] r : t.rows) for (int i = 0; i < w.length && i < r.length; i++) w[i] = Math.min(34, Math.max(w[i], r[i].length()));
        StringBuilder b = new StringBuilder(ind); for (int i = 0; i < w.length; i++) b.append(String.format("%-" + (w[i] + 2) + "s", t.head[i])); System.out.println(b); System.out.println(ind + "-".repeat(b.length() - ind.length()));
        for (String[] r : t.rows) { b = new StringBuilder(ind); for (int i = 0; i < w.length && i < r.length; i++) b.append(String.format("%-" + (w[i] + 2) + "s", r[i].length() > 34 ? r[i].substring(0, 33) + "~" : r[i])); System.out.println(b); }
        for (Tbl s : t.subs) print(s, ind);
    }

    static Scanner in;
    static String ask(String p) { System.out.print(p); return in.hasNextLine() ? in.nextLine().trim() : ""; }
    static int askInt(String p, int def) { try { return Integer.parseInt(ask(p + " [" + def + "]: ")); } catch (Exception e) { return def; } }

    static void menu() throws Exception {
        in = new Scanner(System.in); Store.ensure();
        System.out.println("=== WAREHOUSE INVENTORY SYSTEM - terminal ===");
        String[] u = null; for (int tries = 0; tries < 3 && u == null; tries++) { u = login(ask("Username: "), ask("Password: ")); if (u == null) System.out.println("Wrong login."); }
        if (u == null) return; System.out.println("Welcome " + u[0] + " (" + u[1] + ")   data: " + Store.file());
        while (true) {
            System.out.println("\n 1 Search products (M1+M2)    2 Add product      3 Update product   4 Delete product\n 5 Replenishment (M3)         6 Balancing (M4)   7 Risk & priority (M5)   8 Cycle count (M6)\n 9 Large dataset (M6)        10 String lab (M2) 11 Dashboard       12 Run self-tests   0 Exit");
            String c = ask("Choose: ");
            try {
                switch (c) {
                    case "1": print(tSearch(ask("Search text (typos allowed): "), ask("Warehouse filter (blank = all): "), "", 25)); break;
                    case "2": { HashMap<String, String> m = new HashMap<>(); for (String k : new String[]{"sku", "name", "category", "warehouse", "supplier", "qty", "min", "price", "demand"}) m.put(k, ask(k + ": ")); synchronized (Store.LOCK) { Store.ensure(); P p = fill(new P(), m); p.id = Store.nextId(); Store.items.add(p); Store.save(); System.out.println("Added " + p.id); } break; }
                    case "3": { synchronized (Store.LOCK) { Store.ensure(); P p = Store.byId(ask("Product id (e.g. P000001): ")); if (p == null) { System.out.println("Not found"); break; } HashMap<String, String> m = new HashMap<>(); String[] k = {"sku", "name", "category", "warehouse", "supplier", "qty", "min", "price", "demand"}; String[] cur = {p.sku, p.name, p.cat, p.wh, p.sup, "" + p.qty, "" + p.min, f2(p.price), "" + p.dem};
                        for (int i = 0; i < k.length; i++) { String v = ask(k[i] + " [" + cur[i] + "]: "); m.put(k[i], v.isEmpty() ? cur[i] : v); } fill(p, m); Store.save(); System.out.println("Updated " + p.id); } break; }
                    case "4": { if (!u[1].equals("admin")) { System.out.println("Only admin can delete."); break; } synchronized (Store.LOCK) { Store.ensure(); P p = Store.byId(ask("Product id: ")); if (p == null) System.out.println("Not found"); else { Store.items.remove(p); Store.save(); System.out.println("Deleted"); } } break; }
                    case "5": { long bud = askInt("Budget in rupees", 200000); String w = ask("Warehouse (blank = all): "); Tbl t = tReplenish(bud, w, false); print(t); if (!t.rows.isEmpty() && ask("Place this order and receive the stock? (y/n): ").equalsIgnoreCase("y")) { Tbl ap = tReplenish(bud, w, true); System.out.println(ap.notes.get(ap.notes.size() - 1)); } break; }
                    case "6": { Tbl t = tBalance(false); print(t); if (!t.rows.isEmpty() && ask("Apply these transfers? (y/n): ").equalsIgnoreCase("y")) { Tbl ap = tBalance(true); System.out.println(ap.notes.get(ap.notes.size() - 1)); } break; }
                    case "7": print(tRisk(askInt("Top how many", 15))); break;
                    case "8": { Tbl t = tCycle(askInt("Sample size", 10), ask("Warehouse (blank = all): "), askInt("Seed", (int) (System.nanoTime() & 0xFFFF))); print(t); for (String[] r : t.rows) { String v = ask("Counted qty for " + r[0] + " " + r[2] + " (system " + r[4] + ", blank = skip): "); if (!v.isEmpty()) System.out.println(recordCount(r[0], Integer.parseInt(v), ask("  adjust stock to the count? (y/n): ").equalsIgnoreCase("y"))); } break; }
                    case "9": { System.out.println(" a Generate   b Aggregate (parallel)   c Search   d Sample   e Percentiles"); String s = ask("Choose: "); Path f = largeFile();
                        if (s.equals("a")) { long rows = askInt("Rows", 10000000); long t0 = System.currentTimeMillis(); Thread th = new Thread(() -> { try { generateLarge(f, rows); } catch (Exception e) { System.out.println(e); } }); th.start(); while (th.isAlive()) { Thread.sleep(1000); System.out.print("\r  " + num(genDone) + " / " + num(genTotal) + " rows"); } System.out.println("\nDone in " + (System.currentTimeMillis() - t0) / 1000 + " s -> " + f); }
                        else if (s.equals("b")) print(tLargeAgg(f, askInt("Threads", Math.max(2, Runtime.getRuntime().availableProcessors()))));
                        else if (s.equals("c")) { String txt = ask("Text: "); String al = ask("Algorithm kmp / z / rk [kmp]: "); print(tLargeSearch(f, txt, al.isEmpty() ? "kmp" : al, 20)); }
                        else if (s.equals("d")) print(tLargeSample(f, askInt("Sample size", 10), askInt("Seed", 1))); else if (s.equals("e")) print(tLargePct(f)); break; }
                    case "10": print(tStrings(ask("Pattern (or comma separated list for Aho-Corasick): "), "all")); break;
                    case "11": { String d = dashboard(); System.out.println(d.substring(0, Math.min(d.length(), 300)) + " ..."); break; }
                    case "12": System.out.println(runTests(true)); break;
                    case "0": return;
                    default: System.out.println("Unknown option");
                }
            } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        }
    }

    // ---------------- self tests ----------------
    static int pass, fail; static StringBuilder rep;
    static void check(String name, boolean ok, String detail) { if (ok) pass++; else fail++; rep.append("| ").append(name).append(" | ").append(ok ? "PASS" : "FAIL").append(" | ").append(detail).append(" |\n"); System.out.println((ok ? "  PASS  " : "  FAIL  ") + name + "  (" + detail + ")"); }
    static String rs(Rng r, int len, int alpha) { StringBuilder b = new StringBuilder(); for (int i = 0; i < len; i++) b.append((char) ('a' + r.nextInt(alpha))); return b.toString(); }
    static int[] naive(String t, String p) { IL l = new IL(); for (int i = 0; i + p.length() <= t.length(); i++) if (t.startsWith(p, i)) l.add(i); return l.arr(); }
    static boolean same(int[] a, int[] b) { if (a.length != b.length) return false; for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false; return true; }
    static boolean distinct(Tbl t) { for (int i = 0; i < t.rows.size(); i++) for (int j = i + 1; j < t.rows.size(); j++) if (t.rows.get(i)[0].equals(t.rows.get(j)[0])) return false; return true; }

    static String runTests(boolean writeReport) throws Exception {
        pass = 0; fail = 0; rep = new StringBuilder("| Test | Result | Detail |\n|---|---|---|\n"); Rng r = new Rng(42); long t0 = System.currentTimeMillis(); QUIET = true;
        try {
            System.out.println("Running self-tests...");
            boolean ok = true; for (int i = 0; i < 300; i++) { String t = rs(r, 1 + r.nextInt(80), 3), p = rs(r, 1 + r.nextInt(4), 3); int[] ref = naive(t, p); if (!same(ref, Str.kmp(t, p)) || !same(ref, Str.z(t, p)) || !same(ref, Str.rk(t, p)) || new SA(t).count(p) != ref.length) { ok = false; break; } }
            check("M2 KMP / Z / Rabin-Karp / suffix-array search equal naive search", ok, "300 random texts");
            ok = true; for (int i = 0; i < 200 && ok; i++) { String t = rs(r, 60, 3); String[] ps = {rs(r, 2, 3), rs(r, 3, 3), rs(r, 1, 3)}; int[] c = new int[3]; new AC(ps).scan(t, (e, k) -> c[k]++); for (int k = 0; k < 3; k++) if (c[k] != naive(t, ps[k]).length) ok = false; }
            check("M2 Aho-Corasick multi-pattern counts equal naive", ok, "200 random texts, 3 patterns each");
            ok = true; for (int i = 0; i < 100 && ok; i++) { String s = rs(r, 2 + r.nextInt(60), 3); SA sa = new SA(s); for (int k = 1; k < s.length(); k++) { String a = s.substring(sa.sa[k - 1]), b = s.substring(sa.sa[k]); if (a.compareTo(b) >= 0) ok = false; int l = 0; while (l < a.length() && l < b.length() && a.charAt(l) == b.charAt(l)) l++; if (l != sa.lcp[k]) ok = false; } }
            check("M2 suffix array sorted and Kasai LCP correct", ok, "100 random strings");
            check("M3 edit distance kitten -> sitting = 3", DP.edit("kitten", "sitting") == 3, "Wagner-Fischer"); check("M3 transposition counts as one edit (bolt -> blot)", DP.edit("bolt", "blot") == 1, "Damerau / OSA"); check("M3 distance from empty string", DP.edit("", "abc") == 3, "length of other string");
            ok = true; for (int it = 0; it < 150 && ok; it++) { int n = 1 + r.nextInt(10); int[] w = new int[n]; long[] v = new long[n]; for (int i = 0; i < n; i++) { w[i] = 1 + r.nextInt(12); v[i] = 1 + r.nextInt(50); } int W = 5 + r.nextInt(30); long best = 0; for (int mask = 0; mask < (1 << n); mask++) { int cw = 0; long cv = 0; for (int i = 0; i < n; i++) if ((mask >> i & 1) != 0) { cw += w[i]; cv += v[i]; } if (cw <= W) best = Math.max(best, cv); }
                long got = 0; int gw = 0; for (int i : DP.knapsack(w, v, W)) { got += v[i]; gw += w[i]; } if (got != best || gw > W) ok = false; }
            check("M3 knapsack equals brute-force optimum", ok, "150 random instances");
            int[][] cap = new int[6][6]; cap[0][1] = 16; cap[0][2] = 13; cap[1][2] = 10; cap[2][1] = 4; cap[1][3] = 12; cap[3][2] = 9; cap[2][4] = 14; cap[4][3] = 7; cap[3][5] = 20; cap[4][5] = 4;
            int mf = Flow.maxflow(cap, 0, 5, null); check("M4 max flow on the textbook (CLRS) network = 23", mf == 23, "got " + mf);
            ok = true; for (int it = 0; it < 100 && ok; it++) { int n = 6; int[][] c = new int[n][n]; for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (i != j && r.nextInt(3) == 0) c[i][j] = 1 + r.nextInt(9); int[][] rr = new int[n][n]; int f = Flow.maxflow(c, 0, 5, rr);
                boolean[] seen = new boolean[n]; int[] st = new int[n * n]; int sp = 0; st[sp++] = 0; seen[0] = true; while (sp > 0) { int u = st[--sp]; for (int v = 0; v < n; v++) if (!seen[v] && rr[u][v] > 0) { seen[v] = true; st[sp++] = v; } } int cut = 0; for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (seen[i] && !seen[j]) cut += c[i][j];
                if (seen[5] || cut != f) ok = false; }
            check("M4 max-flow value equals min-cut capacity", ok, "100 random networks");
            IL gain = new IL(); int[][] sets = {{0, 1, 2}, {2, 3}, {3, 4, 5}, {0, 5}}; int[] pk = Approx.setCover(sets, 6, gain); boolean[] cv = new boolean[6]; for (int s : pk) for (int e : sets[s]) cv[e] = true; boolean all = true; for (boolean b : cv) all &= b; check("M5 greedy set cover covers every element", all && pk.length <= 3, pk.length + " sets chosen");
            ok = true; for (int it = 0; it < 50 && ok; it++) { int n = 1 + r.nextInt(200); int[] a = new int[n]; double[] k = new double[n]; int[] vals = new int[n]; for (int i = 0; i < n; i++) { a[i] = i; vals[i] = r.nextInt(30); k[i] = vals[i]; } Rand.qsort(a, k, 0, n - 1, r); for (int i = 1; i < n; i++) if (k[a[i - 1]] > k[a[i]]) ok = false;
                int kk = r.nextInt(n); int[] cp = new int[n]; System.arraycopy(vals, 0, cp, 0, n); int sel = Rand.qselect(cp, kk, r); int[] so = new int[n]; System.arraycopy(vals, 0, so, 0, n); for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) if (so[j] < so[i]) { int t = so[i]; so[i] = so[j]; so[j] = t; } if (so[kk] != sel) ok = false; }
            check("M6 randomised quicksort and quickselect correct", ok, "50 random arrays with many duplicates");
            int[] fr = new int[10]; for (int it = 0; it < 30000; it++) { Rand.Reservoir rv = new Rand.Reservoir(3, r); for (int i = 0; i < 10; i++) rv.offer(i); for (int i = 0; i < 3; i++) fr[(Integer) rv.a[i]]++; }
            ok = true; for (int f : fr) if (Math.abs(f - 9000) > 600) ok = false; check("M6 reservoir sampling is uniform", ok, "each of 10 items picked about 9000 of 30000 times");
            String line = "P000001,FAS-0001,Steel Hex Bolt M8,Fasteners,Mumbai,Bharat Hardware,12,30,45.50,5"; P pp = P.parse(line); check("Storage: CSV line round trip", pp != null && pp.csv().equals(line), "parse + format");
            Store.ensure(); String typo = null, w0 = null;
            for (int i = 0; i < Store.items.size() && typo == null; i++) for (String w : tokenize(Store.items.get(i).name)) if (w.length() >= 5 && w.charAt(1) != w.charAt(2)) { w0 = w; typo = w.charAt(0) + "" + w.charAt(2) + w.charAt(1) + w.substring(3); break; }
            SR sr = search(typo, "", ""); check("M1 typo '" + typo + "' corrected to '" + w0 + "' and products found", sr.fuzzy && sr.corrected.equals(w0) && !sr.list.isEmpty(), "found " + sr.list.size() + " products");
            P first = Store.items.get(0); SR ex = search(first.sku, "", ""); check("M1 exact SKU search ranks that product first", !ex.list.isEmpty() && ex.list.get(0).sku.equals(first.sku), "SKU " + first.sku);
            Tbl rp = tReplenish(50000, "", false); long spent = 0; for (String[] row : rp.rows) spent += Long.parseLong(row[8].replace(",", "")); check("M3 replenishment plan stays within budget", spent <= 50000 && !rp.rows.isEmpty(), "spent " + num(spent) + " of 50,000 on " + rp.rows.size() + " items");
            long before = 0; for (P p : Store.items) before += p.qty; Tbl bal = tBalance(false); check("M4 balancing finds transfers", bal.stats.size() == 5, "legs " + bal.stats.get(4)[1] + ", moved " + bal.stats.get(2)[1]);
            Tbl rk = tRisk(5); check("M5 risk ranking sorted (highest first)", rk.rows.size() == 5 && Double.parseDouble(rk.rows.get(0)[9]) >= Double.parseDouble(rk.rows.get(4)[9]), "top risk " + rk.rows.get(0)[9]);
            Tbl cy = tCycle(8, "", 5); check("M6 cycle sample has requested size, no duplicates", cy.rows.size() == 8 && distinct(cy), "8 distinct products");
            Path tmp = Files.createTempFile("large", ".csv"); generateLarge(tmp, 200000);
            Tbl a1 = tLargeAgg(tmp, 1), a4 = tLargeAgg(tmp, 4); boolean eq = true; for (int i = 0; i < a1.rows.size(); i++) for (int k = 0; k < a1.rows.get(i).length; k++) if (!a1.rows.get(i)[k].equals(a4.rows.get(i)[k])) eq = false;
            check("M6 parallel aggregation (4 threads) equals sequential (1 thread)", eq && a1.stats.get(0)[1].equals("200,000"), "200,000 rows");
            long ck = Long.parseLong(tLargeSearch(tmp, "bolt", "kmp", 5).stats.get(1)[1].replace(",", "")), cz = Long.parseLong(tLargeSearch(tmp, "bolt", "z", 5).stats.get(1)[1].replace(",", "")), cr = Long.parseLong(tLargeSearch(tmp, "bolt", "rk", 5).stats.get(1)[1].replace(",", ""));
            check("M2 large search: KMP, Z and Rabin-Karp agree", ck == cz && cz == cr && ck > 0, "'bolt' matches " + ck + " of 200,000 rows");
            Tbl pc = tLargePct(tmp); int pmin = Integer.parseInt(pc.rows.get(0)[1]), pmed = Integer.parseInt(pc.rows.get(2)[1]), pmax = Integer.parseInt(pc.rows.get(6)[1]); check("M6 percentiles via quickselect: min <= median <= max", pmin <= pmed && pmed <= pmax, "p50 = " + pmed);
            check("M6 large reservoir sample size", tLargeSample(tmp, 12, 3).rows.size() == 12, "12 of 200,000"); Files.deleteIfExists(tmp); Files.deleteIfExists(Paths.get(tmp + ".meta"));
        } finally { QUIET = false; genDone = 0; genTotal = 0; }
        String summary = "Total: " + pass + " passed, " + fail + " failed (" + (System.currentTimeMillis() - t0) + " ms)";
        if (writeReport) Files.write(REPORTS.resolve("test_report.md"), ("# Test report\n\nGenerated: " + java.time.LocalDateTime.now().withNano(0) + "\n\n" + rep + "\n**" + summary + "**\n").getBytes(StandardCharsets.UTF_8));
        return summary;
    }

    // ---------------- entry point ----------------
    public static void main(String[] args) throws Exception {
        String root = System.getProperty("wms.root"); ROOT = Paths.get(root != null ? root : System.getProperty("user.dir")).toAbsolutePath();
        if (root == null && !Files.exists(ROOT.resolve("src")) && ROOT.getParent() != null && Files.exists(ROOT.getParent().resolve("src"))) ROOT = ROOT.getParent();
        DATA = ROOT.resolve("data"); RESULTS = ROOT.resolve("results"); REPORTS = ROOT.resolve("reports"); WEB = ROOT.resolve("src").resolve("web");
        Files.createDirectories(DATA); Files.createDirectories(RESULTS); Files.createDirectories(REPORTS);
        String cmd = args.length > 0 ? args[0] : "menu";
        switch (cmd) {
            case "serve": serve(args.length > 1 ? Integer.parseInt(args[1]) : 8080); break;
            case "gen": { long rows = args.length > 1 ? Long.parseLong(args[1]) : 10_000_000L; long t0 = System.currentTimeMillis(); Thread th = new Thread(() -> { try { generateLarge(largeFile(), rows); } catch (Exception e) { System.out.println(e); } }); th.start(); while (th.isAlive()) { Thread.sleep(1000); System.out.print("\r" + num(genDone) + " / " + num(genTotal) + " rows"); } System.out.println("\nGenerated " + num(rows) + " rows in " + (System.currentTimeMillis() - t0) / 1000 + " s -> " + largeFile()); break; }
            case "test": { String s = runTests(true); System.out.println(s); System.out.println("Report written to " + REPORTS.resolve("test_report.md")); System.exit(fail == 0 ? 0 : 1); break; }
            default: menu();
        }
    }
}
