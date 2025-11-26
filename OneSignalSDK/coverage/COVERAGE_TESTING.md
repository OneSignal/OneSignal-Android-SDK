# Testing Coverage Locally

You can test code coverage locally without pushing to CI/CD. Here are the commands:

## Quick Start: Diff Coverage Check (Recommended)

The easiest way to check coverage for your changed files is using the `checkCoverage.sh` script:

```bash
# Run from the project root (OneSignalSDK/)
./coverage/checkCoverage.sh
```

This script will:
1. ✅ Generate coverage reports for all modules
2. ✅ Check coverage for files changed in your branch (compared to `origin/main`)
3. ✅ Show which files are below the 80% threshold
4. ✅ Generate an HTML report for detailed inspection

### Configuration

You can customize the script behavior with environment variables:

```bash
# Set custom coverage threshold (default: 80%)
DIFF_COVERAGE_THRESHOLD=90 ./coverage/checkCoverage.sh

# Compare against a different branch (default: origin/main)
BASE_BRANCH=origin/develop ./coverage/checkCoverage.sh

# Generate markdown report (for CI/CD compatibility)
GENERATE_MARKDOWN=true ./coverage/checkCoverage.sh
```

### Example Output

```
========================================
Diff Coverage Check
========================================

[1/3] Generating coverage reports...
✓ Coverage report generated

[2/3] Checking diff coverage against origin/main...
Threshold: 80%

Changed files:
  OneSignalSDK/onesignal/core/src/main/java/com/onesignal/common/IDManager.kt

  ✗ IDManager.kt: 2/11 lines (18.2%)

  Overall: 2/11 lines covered (18.2%)

  Files below 80% threshold:
    • IDManager.kt: 18.2% (9 uncovered lines)

✗ Coverage check failed (files below 80% threshold)
```

### What the Script Does

The script uses a **manual coverage check** that reliably matches JaCoCo coverage data to git diff paths. This avoids the path matching issues that can occur with tools like `diff-cover`.

- ✅ **Reliable**: Works consistently both locally and in CI/CD
- ✅ **Fast**: Only checks files that actually changed
- ✅ **Clear**: Shows exactly which files need more tests
- ✅ **Consistent**: Same logic used in CI/CD pipeline

### Troubleshooting

**"No Kotlin/Java files changed"**
- This means there are no `.kt` or `.java` files in your diff. The check passes automatically.

**"Not in coverage report (may not be compiled/tested)"**
- The file exists in your diff but isn't in the coverage report. This usually means:
  - The file wasn't compiled (check your build)
  - The file isn't being tested (add tests)
  - The file path doesn't match expected patterns

**Script fails with "Coverage check failed"**
- One or more changed files have coverage below the threshold (default 80%)
- Check the output to see which files need more tests
- Add tests for the uncovered lines to increase coverage

## Manual Coverage Testing

If you want to manually test coverage for specific modules or view detailed reports:

To test coverage for just the core module where we added the new `IDManager` methods:

```bash
# 1. Run tests with coverage
./gradlew :onesignal:core:testDebugUnitTest

# 2. Generate coverage report
./gradlew :onesignal:core:jacocoTestReport

# 3. View the HTML report (open in browser)
open onesignal/core/build/reports/jacoco/jacocoTestReport/html/index.html
```

## Full Coverage - All Modules

To test coverage for all modules:

```bash
# 1. Run all tests with coverage
./gradlew testDebugUnitTest

# 2. Generate coverage reports for all modules
./gradlew jacocoTestReportAll

# 3. Generate merged report (for CI/CD compatibility)
./gradlew jacocoMergedReport

# 4. Print coverage summary to console
./gradlew jacocoTestReportSummary
```

## View Coverage Reports

### HTML Reports (Visual)
Each module generates an HTML report you can view in your browser:

```bash
# Core module
open onesignal/core/build/reports/jacoco/jacocoTestReport/html/index.html

# Notifications module
open onesignal/notifications/build/reports/jacoco/jacocoTestReport/html/index.html

# In-app messages module
open onesignal/in-app-messages/build/reports/jacoco/jacocoTestReport/html/index.html

# Location module
open onesignal/location/build/reports/jacoco/jacocoTestReport/html/index.html
```

### XML Reports (For CI/CD tools)
XML reports are generated at:
- Individual modules: `onesignal/{module}/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`
- Merged report: `build/reports/jacoco/merged/jacocoMergedReport.xml`

## What to Look For

### In the Console (Summary)

When you run `./gradlew jacocoTestReportSummary`, you'll see overall coverage percentages in the console:

```
Module: core
  Instructions: 17611/40425 (43.56%)
  Branches:     884/2642 (33.46%)
  Lines:        3013/6881 (43.79%)
```

This shows **overall** coverage for the module, but **not** specific method details.

### In the HTML Report (Detailed View)

To see the specific uncovered methods, you need to open the HTML report:

1. Run: `./gradlew :onesignal:core:jacocoTestReport`
2. Open: `onesignal/core/build/reports/jacoco/jacocoTestReport/html/index.html`
3. Navigate to: `com.onesignal.common` → `IDManager` class

In the HTML report, you'll see:
- **Green lines** (`fc` = fully covered): `createLocalId()` and `isLocalId()` 
- **Red lines** (`nc` = not covered): The 4 new methods we added:
  1. `isValidId()` - Lines 38-41 (red, all branches missed)
  2. `extractUuid()` - Lines 52-55 (red, all branches missed)
  3. `isUuidFormat()` - Lines 66-67 (red)
  4. `createShortId()` - Line 77 (red)

**Note:** The console summary shows overall percentages. To see which specific methods are uncovered, you need to view the HTML report.

## Verify Coverage Detection

The coverage tool should detect:
- **Covered**: `createLocalId()` and `isLocalId()` (existing methods that are used in tests)
- **Uncovered**: The 4 new methods we added (they have no tests)

This confirms your coverage setup is working correctly!

## One-Liner to Test Everything

```bash
# Quick diff coverage check (recommended)
./coverage/checkCoverage.sh

# Or manually test a single module
./gradlew :onesignal:core:testDebugUnitTest :onesignal:core:jacocoTestReport && open onesignal/core/build/reports/jacoco/jacocoTestReport/html/index.html
```

## CI/CD Integration

The same `checkCoverage.sh` script is used in CI/CD to ensure consistency:

- **Local**: Run `./coverage/checkCoverage.sh` to test before pushing
- **CI/CD**: Automatically runs on every pull request
- **Same Logic**: Both use identical coverage checking code

The CI/CD pipeline will:
1. Run the script with `GENERATE_MARKDOWN=true`
2. Post a coverage summary as a PR comment
3. Fail the build if coverage is below the threshold (80%)

This ensures that code coverage is checked consistently whether you're testing locally or in CI/CD.

