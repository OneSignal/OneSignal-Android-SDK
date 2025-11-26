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
DIFF_COVERAGE_THRESHOLD=80 ./coverage/checkCoverage.sh

# Compare against a different branch (default: origin/main)
BASE_BRANCH=origin/main ./coverage/checkCoverage.sh

# Generate markdown report (for CI/CD compatibility)
GENERATE_MARKDOWN=true ./coverage/checkCoverage.sh

# Bypass coverage check (local use only)
SKIP_COVERAGE_CHECK=true ./coverage/checkCoverage.sh
```

### Bypassing Coverage Check

In some cases, you may need to merge a PR without adequate test coverage (e.g., emergency fixes, refactoring, or intentionally untested code). 

**Important:** When bypassed, the coverage check **still runs** and shows results in the PR comment. The build simply won't fail if coverage is below the threshold. This ensures visibility into coverage even when bypassing the requirement.

There are three ways to bypass the coverage check:

#### 1. PR Label (Recommended for CI/CD)
Add the `skip-coverage-check` label to your PR. This is the recommended method as it's:
- ✅ Visible and auditable
- ✅ Easy to add/remove
- ✅ Works automatically in CI/CD
- ✅ Still shows coverage results in PR comment

**How it works:**
1. Add the `skip-coverage-check` label to your PR
2. CI/CD will run the coverage check as normal
3. Coverage results will be posted in the PR comment
4. If coverage is below threshold, a bypass notice will be added
5. The build will **not fail** even if coverage is low

**Example PR Comment (when bypassed):**
```
## Diff Coverage Report

**Threshold:** 80%

### Changed Files Coverage

- ❌ IDManager.kt: 2/11 lines (18.2%)
  - ⚠️ Below threshold: 9 uncovered lines

### Overall Coverage

**2/11** lines covered (18.2%)

### ❌ Coverage Check Failed

Files below 80% threshold:
- **IDManager.kt**: 18.2% (9 uncovered lines)

---
⚠️ **Coverage check bypassed - build will not fail**

**Reason:** PR has 'skip-coverage-check' label

**Note:** Coverage results are shown above. Please ensure adequate test coverage is added in a follow-up PR when possible.
```

#### 2. Commit Message Keyword
Include one of these keywords in your commit message:
- `[skip coverage]`
- `[bypass coverage]`
- `[no coverage]`

Example:
```bash
git commit -m "Emergency fix for critical bug [skip coverage]"
```

**How it works:**
- The script checks commit messages for these keywords
- If found, coverage check runs but build won't fail
- Coverage results are still shown in PR comment

#### 3. Environment Variable (Local Testing Only)
For local testing, you can bypass the check:
```bash
SKIP_COVERAGE_CHECK=true ./coverage/checkCoverage.sh
```

**Note:** The environment variable method only works locally. In CI/CD, use the PR label or commit message method.

### When to Use Bypass

Use the bypass mechanism for:
- 🚨 **Emergency fixes** that need to be deployed immediately
- 🔄 **Refactoring** where code is moved but not changed
- 📝 **Documentation-only** changes
- 🧪 **Test infrastructure** changes
- ⚠️ **Intentionally untested code** (with follow-up plan)

**Best Practice:** Always add a follow-up issue or comment explaining why coverage was bypassed and when it will be addressed.

### Example Output

#### Normal Run (Coverage Passes)
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

  ✓ IDManager.kt: 10/11 lines (90.9%)

  Overall: 10/11 lines covered (90.9%)

  ✓ All files meet 80% threshold

✓ Coverage check passed!
```

#### Normal Run (Coverage Fails)
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

#### Bypassed Run (Coverage Check Still Runs)
```
========================================
Diff Coverage Check
========================================

⚠ Coverage check will not fail build
  Reason: SKIP_COVERAGE_CHECK environment variable set
  Coverage will still be checked and reported

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

⚠ Coverage below threshold (files below 80%)
  Build will not fail due to bypass: SKIP_COVERAGE_CHECK environment variable set

========================================
Coverage check complete!
========================================
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

### Quick Test - Single Module (Core)

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
3. Fail the build if coverage is below the threshold (80%), unless bypassed (see [Bypassing Coverage Check](#bypassing-coverage-check) above)

This ensures that code coverage is checked consistently whether you're testing locally or in CI/CD.

