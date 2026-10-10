<div align="center">

#  Warehouse Inventory Management & Optimization System

**A Java-based warehouse operations platform with a web dashboard and a terminal interface, featuring intelligent search, replenishment optimization, warehouse balancing, risk analysis, cycle counting and large-scale (10 million row) inventory processing.**

![Java](https://img.shields.io/badge/Java-11+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white)
![CSS3](https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)
![Storage](https://img.shields.io/badge/Storage-CSV-2E7D32?style=for-the-badge)
![Tests](https://img.shields.io/badge/Tests-23%20passed-brightgreen?style=for-the-badge)

**KLH · CSE · 2026-27 · DSA-3 · Section 6 · Team 17**

[Overview](#-overview) · [Features](#-features) · [Architecture](#-architecture) · [Modules](#-modules-and-algorithms) · [Quick Start](#-quick-start) · [Performance](#-performance) · [Team](#-team)

</div>

---

## 📖 Overview

Managing stock across many warehouses means answering questions quickly: *What do we have? What is running out? What should we order within our budget? Which warehouse should send stock to which?*

This project answers those questions with a single Java application that can be used from a **web dashboard** or a **terminal menu**. Both read and write the **same CSV files**, so a change made in one appears in the other straight away.

| | |
|:--|:--|
| 🏭 **Warehouses** | 5 (Mumbai, Delhi, Hyderabad, Chennai, Kolkata) |
| 📦 **Products in sample data** | 2,804 stock records across 6 categories |
| 🚚 **Transfer routes** | 20 warehouse-to-warehouse routes with capacities |
| 📈 **Scale tested** | 10,000,000 rows (~950 MB) |
| ✅ **Automated tests** | 23 / 23 passing |
| 👥 **Roles** | Admin and Staff |

---

## ✨ Features

```mermaid
mindmap
  root((Warehouse<br/>Inventory System))
    Inventory
      Add / Edit / Delete
      Filter and Sort
      Typo tolerant search
    Planning
      Restocking planner
      Budget control
      Stock transfer planner
    Monitoring
      Dashboard
      Stock alerts
      Risk ranking
      Supplier coverage
    Operations
      Cycle counting
      Count adjustment
    Big Data
      10M row generator
      Parallel analysis
      Streaming search
      Sampling and percentiles
    Access
      Login
      Admin and Staff roles
```

| Area | What it does |
|---|---|
| **Dashboard** | Live summary of stock, alerts and priorities across all warehouses |
| **Inventory** | Add, edit, delete, filter and sort products, with a typo-tolerant search |
| **Restocking planner** | Picks the best set of items to reorder within a given budget |
| **Transfer planner** | Moves surplus stock from one warehouse to another where there is a shortage |
| **Risk analysis** | Scores every product from 0 to 100 and ranks products, warehouses and suppliers |
| **Cycle counting** | Selects a random sample to count, records the count and can adjust stock |
| **Data Center** | Generates and analyses a 10-million-row dataset using multiple threads |
| **Login & roles** | Admin can delete products; Staff cannot |

---

## 🏗️ Architecture

The web page and the terminal both talk to the same Java engine, which keeps everything in CSV files.

```mermaid
flowchart LR
    subgraph Clients
        W["🌐 Web Dashboard<br/>HTML · CSS · JavaScript"]
        T["⌨️ Terminal Menu<br/>12 options"]
    end

    subgraph Engine["☕ Java Engine · src/Main.java"]
        direction TB
        API["HTTP Server<br/>REST API · port 8080"]
        AUTH["Login and Roles"]
        CORE["Optimization Modules<br/>search · replenish · balance<br/>risk · cycle count · large data"]
        STORE["Store<br/>atomic writes"]
        API --> AUTH --> CORE --> STORE
    end

    subgraph Data["🗂️ data/"]
        INV[("inventory.csv")]
        RT[("routes.csv")]
        US[("users.csv")]
        LG[("large inventory<br/>generated on demand")]
    end

    W -- "JSON over HTTP" --> API
    T --> CORE
    STORE <--> INV
    STORE <--> RT
    AUTH <--> US
    CORE <--> LG
```

### How the website and terminal stay in sync

```mermaid
sequenceDiagram
    participant U as User
    participant W as Web Dashboard
    participant J as Java Engine
    participant F as inventory.csv
    participant T as Terminal

    U->>W: Edit a product
    W->>J: POST /api/products/update
    J->>F: Write to temp file, then atomic move
    T->>J: Next menu action
    J->>F: Modified time changed, so reload
    J-->>T: Shows the updated product
```

---

## 🧩 Modules and Algorithms

Each business feature is backed by a classic data-structures-and-algorithms technique.

```mermaid
flowchart TD
    A["📦 Warehouse Inventory System"] --> M1["🔍 Search"]
    A --> M3["💰 Replenishment"]
    A --> M4["🔄 Warehouse Balancing"]
    A --> M5["⚠️ Risk and Supplier Analysis"]
    A --> M6["📊 Cycle Count and Large Data"]

    M1 --> A1["Tokenise the query"]
    A1 --> A2["Aho-Corasick: all words in one scan"]
    A2 --> A3["Edit distance: fix typos"]
    A3 --> A4["KMP: exact phrase boost"]

    M3 --> B1["0/1 Knapsack DP"]
    M4 --> C1["Max Flow: Edmonds-Karp"]
    M5 --> D1["Risk score"]
    M5 --> D2["Greedy Set Cover"]
    M6 --> E1["Reservoir Sampling"]
    M6 --> E2["Quickselect and 3-way Quicksort"]
    M6 --> E3["Chunked parallel scan"]
```

| Module | Business purpose | Technique | Complexity / note |
|:--:|---|---|---|
| **1 + 2** | Find products, even with typos | Aho-Corasick, KMP, Z-algorithm, Rabin-Karp, suffix array + LCP | Multi-word search in one pass over the data |
| **3** | Spend a budget on the most valuable restock | Damerau edit distance, **0/1 Knapsack DP** | Never exceeds the budget |
| **4** | Move surplus to where it is needed | **Edmonds-Karp max flow** | O(V·E²); unmet shortage is reported |
| **5** | Rank risky products, warehouses and suppliers | Risk score + **greedy set cover** | H(d) approximation guarantee |
| **6** | Cycle counts and 10M-row analysis | Reservoir sampling, quickselect, parallel chunk scan | Streams the file, no full load in memory |

### Key definitions

| Term | Meaning |
|---|---|
| **Below minimum** | `qty < min` |
| **Surplus** | `qty > 2 × min` |
| **Order quantity** | `2 × min − qty` |
| **Risk score (0–100)** | `100 × (1 − daysOfCover / 21)`, plus 25 if below minimum; 100 if out of stock |
| **At risk** | Risk score ≥ 60 |

### Restocking decision flow

```mermaid
flowchart LR
    S(["Start"]) --> P["Find products<br/>below minimum"]
    P --> Q["Order qty = 2 x min - qty<br/>Cost = qty x price<br/>Benefit = qty x (demand + 1)"]
    Q --> K["0/1 Knapsack<br/>within budget"]
    K --> R["Show plan"]
    R --> D{"Place order?"}
    D -- Yes --> U["Receive stock<br/>update CSV"]
    D -- No --> E(["End"])
    U --> E
```

---

## 🗄️ Data Model

```mermaid
erDiagram
    PRODUCT {
        string id PK "P000001"
        string sku "PKG-0001"
        string name
        string category
        string warehouse
        string supplier
        int qty
        int min
        float price
        int demand "units per day"
    }
    ROUTE {
        string from_warehouse
        string to_warehouse
        int capacity_units
    }
    USER {
        string username PK
        string salt
        string hash "SHA-256"
        string role "admin or staff"
    }
    WAREHOUSE ||--o{ PRODUCT : stores
    WAREHOUSE ||--o{ ROUTE : "sends or receives"
```

A SKU may exist in several warehouses. The pair **SKU + warehouse** is unique.

---

## 📁 Project Structure

```text
wms/
├── src/
│   ├── Main.java            # Java engine: server, algorithms, terminal menu, tests
│   └── web/
│       └── index.html       # Web dashboard (HTML, CSS, JavaScript)
├── data/
│   ├── inventory.csv        # 2,804 product records
│   ├── routes.csv           # 20 transfer routes with capacities
│   └── users.csv            # Users with salted password hashes
├── docs/
│   └── design.md            # Design notes and definitions
├── reports/
│   ├── test_report.md       # Automated test results
│   └── performance.md       # Large dataset timings
├── results/                 # Output files
├── run.bat                  # Run on Windows
├── run.sh                   # Run on Linux / macOS
└── README.md
```

---

## 🚀 Quick Start

**Requirements:** Java JDK 11 or newer. Nothing else, there are no external libraries.

### Windows

```bat
run.bat
```

### Linux / macOS

```bash
chmod +x run.sh
./run.sh
```

Then open **http://localhost:8080** in your browser.

### Demo logins

| Role | Username | Password | Can do |
|---|---|---|---|
| Admin | `admin` | `admin123` | Everything, including deleting products |
| Staff | `staff` | `staff123` | Everything except deleting products |

### Terminal commands

After compiling (`javac -d out src/Main.java`):

| Command | What it does |
|---|---|
| `java -cp out Main` | Opens the 12-option terminal menu (asks for the same login) |
| `java -cp out Main serve 8080` | Starts the web server on port 8080 |
| `java -cp out Main gen 10000000` | Generates a 10-million-row dataset |
| `java -cp out Main test` | Runs all tests and writes `reports/test_report.md` |

### Terminal menu

| # | Option | # | Option |
|:-:|---|:-:|---|
| 1 | Search products | 7 | Risk ranking |
| 2 | Add product | 8 | Cycle count |
| 3 | Edit product | 9 | Large data center |
| 4 | Delete product (admin) | 10 | String matching lab |
| 5 | Restocking planner | 11 | Dashboard summary |
| 6 | Transfer planner | 12 | Run tests |

---

## ⚡ Performance

Measured on 10,000,000 rows (~950 MB) on a single-core cloud VM. Multi-core laptops run the parallel steps faster.

```mermaid
xychart-beta
    title "Time to process 10 million rows (seconds, lower is better)"
    x-axis ["Generate", "Aggregate (4 threads)", "Search KMP", "Search Rabin-Karp", "Percentiles", "Reservoir sample"]
    y-axis "Seconds" 0 --> 10
    bar [8, 5.7, 3.1, 6.3, 2.6, 1.2]
```

| Operation | Time |
|---|---:|
| Generate dataset | 8 s |
| Parallel aggregation (4 threads) | 5.7 s (1.7 M rows/s) |
| Streaming search, KMP | 3.1 s |
| Streaming search, Rabin-Karp | 6.3 s |
| Percentiles via quickselect | 2.6 s |
| Reservoir sample | 1.2 s |

> The 10-million-row file is not stored in this repository because of its size. Generate it from the **Data Center** page or with `java -cp out Main gen 10000000`.

---

## ✅ Testing

```mermaid
pie showData title Automated test results (23 tests)
    "Passed" : 23
    "Failed" : 0
```

The tests check each algorithm against a simple, obviously correct version: string search against naive search, knapsack against brute force, max flow against min cut, parallel against single-thread results, and the budget limit of the restocking plan. Full results are in [`reports/test_report.md`](wms/reports/test_report.md).

---

## 🔒 Data Safety

- **Atomic writes:** every save goes to a temporary file first and is then moved over the original, so a crash cannot leave a half-written file.
- **Always in sync:** every read checks the file's modified time, so the website and terminal see each other's changes.
- **Passwords:** stored as salted SHA-256 hashes, never as plain text.
- **Role checks:** deleting products is enforced as admin-only on the server, not just hidden in the page.

---

## 📚 Documentation

| Document | Description |
|---|---|
| [`docs/design.md`](wms/docs/design.md) | Storage format, definitions and how each module works |
| [`reports/test_report.md`](wms/reports/test_report.md) | Latest automated test run |
| [`reports/performance.md`](wms/reports/performance.md) | Large dataset benchmark |

---

## 👥 Team

**Team 17 · KLH · CSE · 2026-27 · DSA-3 · Section 6**

| Name | Student ID | GitHub |
|---|---|---|
| Mokshitha | 2520030396 | [@mokshithaputti8-web](https://github.com/mokshithaputti8-web) |
| Abhishiktha | 2520030185 | _@username_ |


---

<div align="center">

Built for the Data Structures and Algorithms course project, KLH.

</div>
