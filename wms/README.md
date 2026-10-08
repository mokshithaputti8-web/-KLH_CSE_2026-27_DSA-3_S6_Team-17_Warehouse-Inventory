# Warehouse Inventory Management and Optimization System

A complete Java + browser warehouse operations system designed to run directly from VS Code.

## Run in VS Code / terminal

### Windows
1. Open this folder in VS Code.
2. Open Terminal.
3. Run:
   ```bat
   run.bat
   ```
4. Open http://localhost:8080
5. Login:
   - Admin: `admin` / `admin123`
   - Staff: `staff` / `staff123`

### Linux / macOS
```bash
chmod +x run.sh
./run.sh
```
Then open http://localhost:8080.

## Included
- Modern responsive warehouse operations dashboard
- Login and role permissions
- Live inventory search, filtering, sorting, add, edit and delete
- Restocking planner with budget control
- Warehouse stock transfer planner
- Stock alerts and priority view
- Cycle-count workflow with count recording
- Large inventory data center with generation, analysis, search and sampling
- Shared persistent CSV storage for website and terminal application
- Existing Java algorithm engine preserved in `src/Main.java`
- Data, results, reports and docs folders

## Important
The website intentionally presents warehouse/business terminology rather than exposing implementation or course-algorithm names. The underlying Java implementation remains unchanged for the project requirements.

The 10,000,000-row large dataset is generated from the Data Center when needed; it is not bundled in this ZIP because of its size.
