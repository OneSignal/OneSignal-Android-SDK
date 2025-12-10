#!/bin/bash

# Diff Coverage Check Script
# This script generates coverage reports and checks diff coverage against the base branch
# Uses a manual coverage check that reliably matches JaCoCo paths to git diff paths
# 
# Usage:
#   ./coverage/checkCoverage.sh                    # Local use (console output)
#   GENERATE_MARKDOWN=true ./coverage/checkCoverage.sh  # CI/CD use (generates markdown)

set -e  # Exit on error (but we handle Python exit codes manually)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
COVERAGE_THRESHOLD=${DIFF_COVERAGE_THRESHOLD:-80}
BASE_BRANCH=${BASE_BRANCH:-origin/main}
GENERATE_MARKDOWN=${GENERATE_MARKDOWN:-false}  # Set to 'true' for CI/CD to generate markdown report
SKIP_COVERAGE_CHECK=${SKIP_COVERAGE_CHECK:-false}  # Set to 'true' to bypass coverage check (still runs but doesn't fail)

# Get script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Paths relative to project root
COVERAGE_REPORT="$PROJECT_ROOT/build/reports/jacoco/merged/jacocoMergedReport.xml"
HTML_REPORT="$SCRIPT_DIR/diff_coverage.html"
MARKDOWN_REPORT="$PROJECT_ROOT/diff_coverage.md"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Diff Coverage Check${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Check for bypass conditions (still run coverage check, but don't fail)
BYPASS_REASON=""
if [ "$SKIP_COVERAGE_CHECK" = "true" ]; then
    BYPASS_REASON="SKIP_COVERAGE_CHECK environment variable set"
elif [ -n "$GITHUB_EVENT_NAME" ] && [ "$GITHUB_EVENT_NAME" = "pull_request" ]; then
    # Check commit messages for bypass keyword
    cd "$PROJECT_ROOT"
    COMMIT_MESSAGES=$(git log --format=%B origin/main..HEAD 2>/dev/null || git log --format=%B "$BASE_BRANCH"..HEAD 2>/dev/null || echo "")
    if echo "$COMMIT_MESSAGES" | grep -qiE "\[skip coverage\]|\[bypass coverage\]|\[no coverage\]"; then
        BYPASS_REASON="Commit message contains [skip coverage] keyword"
    fi
fi

if [ -n "$BYPASS_REASON" ]; then
    echo -e "${YELLOW}⚠ Coverage check will not fail build${NC}"
    echo -e "${YELLOW}  Reason: $BYPASS_REASON${NC}"
    echo -e "${YELLOW}  Coverage will still be checked and reported${NC}\n"
fi

# Step 1: Generate coverage reports
echo -e "${YELLOW}[1/3] Generating coverage reports...${NC}"
cd "$PROJECT_ROOT"
./gradlew jacocoTestReportAll jacocoMergedReport --console=plain

if [ ! -f "$COVERAGE_REPORT" ]; then
    echo -e "${RED}✗ Error: Coverage report not found at $COVERAGE_REPORT${NC}" >&2
    exit 1
fi
echo -e "${GREEN}✓ Coverage report generated${NC}\n"

# Step 2: Check diff coverage using manual method (reliable path matching)
echo -e "${YELLOW}[2/3] Checking diff coverage against $BASE_BRANCH...${NC}"
echo -e "${YELLOW}Threshold: ${COVERAGE_THRESHOLD}%${NC}\n"

# Get changed files (run from project root)
cd "$PROJECT_ROOT"
CHANGED_FILES=$(git diff --name-only "$BASE_BRANCH"...HEAD 2>/dev/null | grep -E '\.(kt|java)$' || true)

if [ -z "$CHANGED_FILES" ]; then
    echo -e "${BLUE}No Kotlin/Java files changed${NC}\n"
    if [ "$GENERATE_MARKDOWN" = "true" ]; then
        echo "✓ Coverage check passed (no source files changed)" > "$MARKDOWN_REPORT"
    else
        echo -e "${GREEN}✓ Coverage check passed (no source files changed)${NC}\n"
    fi
else
    echo -e "${BLUE}Changed files:${NC}"
    echo "$CHANGED_FILES" | sed 's/^/  /'
    echo ""
    
    # Manual coverage check (reliable path matching)
    export COVERAGE_THRESHOLD
    export COVERAGE_REPORT
    export GENERATE_MARKDOWN
    export MARKDOWN_REPORT
    python3 << PYEOF
import xml.etree.ElementTree as ET
import re
import sys
import os

coverage_report = os.environ.get('COVERAGE_REPORT')
threshold = int(os.environ.get('COVERAGE_THRESHOLD', '80'))
changed_files_str = """$CHANGED_FILES"""
generate_markdown = os.environ.get('GENERATE_MARKDOWN', 'false').lower() == 'true'
markdown_report = os.environ.get('MARKDOWN_REPORT', 'diff_coverage.md')

try:
    tree = ET.parse(coverage_report)
    root = tree.getroot()
except Exception as e:
    print(f"Error parsing coverage report: {e}")
    sys.exit(1)

changed_files = [f.strip() for f in changed_files_str.split('\n') if f.strip()]

total_uncovered = 0
total_lines = 0
files_below_threshold = []
files_checked = []
markdown_output = []

if generate_markdown:
    markdown_output.append("## Diff Coverage Report\n")
    markdown_output.append(f"**Threshold:** {threshold}%\n\n")
    markdown_output.append("### Changed Files Coverage\n\n")

for changed_file in changed_files:
    # Handle paths with or without OneSignalSDK/ prefix
    if 'OneSignalSDK/' in changed_file:
        path_part = changed_file.replace('OneSignalSDK/', '')
    else:
        path_part = changed_file
    
    # Extract package and filename from path
    # e.g., onesignal/core/src/main/java/com/onesignal/common/IDManager.kt
    # -> package: com/onesignal/common, filename: IDManager.kt
    match = re.search(r'src/main/(java|kotlin)/(.+)/([^/]+\.(kt|java))$', path_part)
    if not match:
        continue
    
    package_path = match.group(2)
    filename = match.group(3)
    package_name = package_path.replace('/', '/')
    
    # Find in coverage report
    found = False
    for package in root.findall(f'.//package[@name="{package_name}"]'):
        for sourcefile in package.findall(f'sourcefile[@name="{filename}"]'):
            found = True
            files_checked.append(filename)
            
            lines = sourcefile.findall('line')
            file_total = len([l for l in lines if int(l.get('mi', 0)) > 0 or int(l.get('ci', 0)) > 0])
            file_covered = len([l for l in lines if int(l.get('ci', 0)) > 0])
            file_uncovered = len([l for l in lines if l.get('ci') == '0' and int(l.get('mi', 0)) > 0])
            
            if file_total > 0:
                total_lines += file_total
                total_uncovered += file_uncovered
                coverage_pct = (file_covered / file_total * 100)
                
                if generate_markdown:
                    status = "✅" if coverage_pct >= threshold else "❌"
                    markdown_output.append(f"- {status} **{filename}**: {file_covered}/{file_total} lines ({coverage_pct:.1f}%)")
                    if coverage_pct < threshold:
                        files_below_threshold.append((filename, coverage_pct, file_uncovered))
                        markdown_output.append(f"  - ⚠️ Below threshold: {file_uncovered} uncovered lines")
                else:
                    status = "✓" if coverage_pct >= threshold else "✗"
                    color = "" if coverage_pct >= threshold else "\033[0;31m"
                    reset = "\033[0m" if color else ""
                    print(f"  {color}{status}{reset} {filename}: {file_covered}/{file_total} lines ({coverage_pct:.1f}%)")
                    if coverage_pct < threshold:
                        files_below_threshold.append((filename, coverage_pct, file_uncovered))
            break
        if found:
            break
    
    if not found:
        if generate_markdown:
            markdown_output.append(f"- ⚠️ **{filename}**: Not in coverage report (may not be compiled/tested)")
        else:
            print(f"  ⚠ {filename}: Not in coverage report (may not be compiled/tested)")

if total_lines > 0:
    overall_coverage = ((total_lines - total_uncovered) / total_lines * 100)
    
    if generate_markdown:
        markdown_output.append(f"\n### Overall Coverage\n")
        markdown_output.append(f"**{total_lines - total_uncovered}/{total_lines}** lines covered ({overall_coverage:.1f}%)\n")
        
        if files_below_threshold:
            markdown_output.append(f"\n### ❌ Coverage Check Failed\n")
            markdown_output.append(f"Files below {threshold}% threshold:\n")
            for filename, pct, uncovered in files_below_threshold:
                markdown_output.append(f"- **{filename}**: {pct:.1f}% ({uncovered} uncovered lines)\n")
        
        # Write markdown file
        with open(markdown_report, 'w') as f:
            f.write('\n'.join(markdown_output))
        
        # Print to console
        print('\n'.join(markdown_output))
        
        if files_below_threshold:
            sys.exit(1)
        else:
            sys.exit(0)
    else:
        print(f"\n  Overall: {(total_lines - total_uncovered)}/{total_lines} lines covered ({overall_coverage:.1f}%)")
        
        if files_below_threshold:
            print(f"\n  Files below {threshold}% threshold:")
            for filename, pct, uncovered in files_below_threshold:
                print(f"    • {filename}: {pct:.1f}% ({uncovered} uncovered lines)")
            sys.exit(1)
        else:
            print(f"\n  ✓ All files meet {threshold}% threshold")
            sys.exit(0)
elif files_checked:
    # Files were found but had no executable lines
    if generate_markdown:
        markdown_output.append(f"\n### ✅ Coverage Check Passed\n")
        markdown_output.append("All checked files have no executable lines (or fully covered)\n")
        with open(markdown_report, 'w') as f:
            f.write('\n'.join(markdown_output))
    else:
        print("\n  ✓ All checked files have no executable lines (or fully covered)")
    sys.exit(0)
else:
    if generate_markdown:
        markdown_output.append(f"\n### ⚠️ No Coverage Data\n")
        markdown_output.append("No coverage data found for changed files\n")
        with open(markdown_report, 'w') as f:
            f.write('\n'.join(markdown_output))
    else:
        print("\n  ⚠ No coverage data found for changed files")
        print("     This may mean files aren't being compiled or tested")
    sys.exit(0)
PYEOF
    
    CHECK_RESULT=$?
    if [ $CHECK_RESULT -eq 1 ]; then
        if [ "$GENERATE_MARKDOWN" != "true" ]; then
            if [ -n "$BYPASS_REASON" ]; then
                echo -e "\n${YELLOW}⚠ Coverage below threshold (files below ${COVERAGE_THRESHOLD}%)${NC}"
                echo -e "${YELLOW}  Build will not fail due to bypass: $BYPASS_REASON${NC}\n"
            else
                echo -e "\n${RED}✗ Coverage check failed (files below ${COVERAGE_THRESHOLD}% threshold)${NC}\n"
            fi
        else
            # In markdown mode, update the report to indicate bypass if applicable
            if [ -n "$BYPASS_REASON" ] && [ -f "$PROJECT_ROOT/diff_coverage.md" ]; then
                # Append bypass notice to existing markdown
                echo "" >> "$PROJECT_ROOT/diff_coverage.md"
                echo "---" >> "$PROJECT_ROOT/diff_coverage.md"
                echo "⚠️ **Coverage check bypassed - build will not fail**" >> "$PROJECT_ROOT/diff_coverage.md"
                echo "" >> "$PROJECT_ROOT/diff_coverage.md"
                echo "**Reason:** $BYPASS_REASON" >> "$PROJECT_ROOT/diff_coverage.md"
                echo "" >> "$PROJECT_ROOT/diff_coverage.md"
                echo "**Note:** Coverage results are shown above. Please ensure adequate test coverage is added in a follow-up PR when possible." >> "$PROJECT_ROOT/diff_coverage.md"
            fi
        fi
        # Only exit with error if not bypassed
        if [ -z "$BYPASS_REASON" ]; then
            exit 1
        else
            exit 0
        fi
    elif [ $CHECK_RESULT -eq 0 ]; then
        if [ "$GENERATE_MARKDOWN" != "true" ]; then
            echo -e "\n${GREEN}✓ Coverage check passed!${NC}\n"
        fi
        exit 0
    fi
fi

# Step 3: Generate HTML report (optional, for visual inspection)
echo -e "${YELLOW}[3/3] Generating HTML coverage report...${NC}"
# Try to generate HTML report using diff-cover if available, otherwise skip
if python3 -m diff_cover.diff_cover_tool --version &>/dev/null 2>&1; then
    # Try diff-cover for HTML report (may not work due to path issues, but worth trying)
    cd "$PROJECT_ROOT"
    python3 -m diff_cover.diff_cover_tool "build/reports/jacoco/merged/jacocoMergedReport.xml" \
        --compare-branch="$BASE_BRANCH" \
        --format html:"$HTML_REPORT" 2>&1 | grep -v "No lines with coverage" || true
    
    if [ -f "$HTML_REPORT" ]; then
        echo -e "${GREEN}✓ HTML report generated: $HTML_REPORT${NC}"
        echo -e "${BLUE}  Open it in your browser to see detailed coverage${NC}\n"
    else
        echo -e "${YELLOW}  HTML report generation had issues (non-fatal)${NC}\n"
    fi
else
    echo -e "${YELLOW}  diff-cover not available, skipping HTML report${NC}"
    echo -e "${BLUE}  Install with: pip install diff-cover${NC}\n"
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Coverage check complete!${NC}"
echo -e "${BLUE}========================================${NC}"
