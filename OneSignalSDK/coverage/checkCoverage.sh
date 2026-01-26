#!/bin/bash

# Diff Coverage Check Script
# This script generates coverage reports and checks diff coverage against the base branch
# Only checks coverage for newly added/modified lines (not entire files)
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
# PROJECT_ROOT is the OneSignalSDK directory (where build reports are)
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# REPO_ROOT is the git repository root (parent of OneSignalSDK)
REPO_ROOT="$(cd "$PROJECT_ROOT/.." && pwd)"

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
    cd "$REPO_ROOT"
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
# Include committed changes, staged changes, and unstaged changes
cd "$REPO_ROOT"
COMMITTED_FILES=$(git diff --name-only "$BASE_BRANCH"...HEAD 2>/dev/null | grep -E '\.(kt|java)$' || true)
STAGED_FILES=$(git diff --cached --name-only 2>/dev/null | grep -E '\.(kt|java)$' || true)
UNSTAGED_FILES=$(git diff --name-only 2>/dev/null | grep -E '\.(kt|java)$' || true)
# Combine all, remove duplicates, and filter to OneSignalSDK files
CHANGED_FILES=$(echo -e "$COMMITTED_FILES\n$STAGED_FILES\n$UNSTAGED_FILES" | grep -E '^OneSignalSDK/' | sort -u || true)

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
    export BASE_BRANCH
    export REPO_ROOT
    python3 << PYEOF
import xml.etree.ElementTree as ET
import re
import sys
import os
import subprocess

coverage_report = os.environ.get('COVERAGE_REPORT')
threshold = int(os.environ.get('COVERAGE_THRESHOLD', '80'))
changed_files_str = """$CHANGED_FILES"""
generate_markdown = os.environ.get('GENERATE_MARKDOWN', 'false').lower() == 'true'
markdown_report = os.environ.get('MARKDOWN_REPORT', 'diff_coverage.md')
base_branch = os.environ.get('BASE_BRANCH', 'origin/main')
repo_root_env = os.environ.get('REPO_ROOT')

def get_changed_lines(file_path, project_root):
    """Get line numbers of added/modified lines from git diff"""
    try:
        # First try to get diff from committed changes
        result = subprocess.run(
            ['git', 'diff', '--unified=0', base_branch + '...HEAD', '--', file_path],
            capture_output=True,
            text=True,
            cwd=project_root
        )
        
        # If no committed changes, check staged changes
        if result.returncode != 0 or not result.stdout.strip():
            result = subprocess.run(
                ['git', 'diff', '--cached', '--unified=0', '--', file_path],
                capture_output=True,
                text=True,
                cwd=project_root
            )
        
        # If no staged changes, check unstaged changes
        if result.returncode != 0 or not result.stdout.strip():
            result = subprocess.run(
                ['git', 'diff', '--unified=0', '--', file_path],
                capture_output=True,
                text=True,
                cwd=project_root
            )
        
        # If still nothing, try alternative base branch format
        if result.returncode != 0 or not result.stdout.strip():
            result = subprocess.run(
                ['git', 'diff', '--unified=0', base_branch, 'HEAD', '--', file_path],
                capture_output=True,
                text=True,
                cwd=project_root
            )
        
        if result.returncode != 0 or not result.stdout.strip():
            return None
        
        changed_lines = set()
        current_new_line = None
        
        for line in result.stdout.split('\n'):
            # Parse unified diff format
            # @@ -old_start,old_count +new_start,new_count @@
            match = re.match(r'@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@', line)
            if match:
                current_new_line = int(match.group(1))
                count = int(match.group(2)) if match.group(2) else 1
                # The count tells us how many lines are in this hunk
                # We'll track them as we see + lines
            elif line.startswith('+') and not line.startswith('+++'):
                # Added/modified line (starts with +)
                if current_new_line is not None:
                    changed_lines.add(current_new_line)
                    current_new_line += 1
            elif line.startswith('-') and not line.startswith('---'):
                # Deleted line - don't add to changed_lines, don't increment current_new_line
                pass
            elif line.startswith(' '):
                # Context line (unchanged, starts with space) - increment current_new_line
                if current_new_line is not None:
                    current_new_line += 1
        
        return changed_lines if changed_lines else None
    except Exception as e:
        # Silently fail and return None - we'll fall back to checking all lines
        return None

try:
    tree = ET.parse(coverage_report)
    root = tree.getroot()
except Exception as e:
    print(f"Error parsing coverage report: {e}")
    sys.exit(1)

# Get repository root - prefer environment variable, then try to detect from coverage report path
if repo_root_env:
    project_root = repo_root_env
else:
    # Fallback: try to detect from coverage report path
    # Coverage report is in OneSignalSDK/build/..., so go up two levels to get repo root
    detected_root = os.path.dirname(os.path.dirname(coverage_report)) if '/build/' in coverage_report else os.path.dirname(coverage_report)
    # Look for OneSignalSDK in the path and go one level up
    parts = coverage_report.split('/')
    if 'OneSignalSDK' in parts:
        idx = parts.index('OneSignalSDK')
        project_root = '/'.join(parts[:idx])
    else:
        # Fallback: assume we're in repo root
        project_root = os.getcwd()

changed_files = [f.strip() for f in changed_files_str.split('\n') if f.strip()]

total_uncovered = 0
total_lines = 0
files_below_threshold = []
files_checked = []
markdown_output = []

if generate_markdown:
    markdown_output.append("## Diff Coverage Report (Changed Lines Only)\n")
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
    
    # Get changed line numbers for this file
    changed_lines = get_changed_lines(changed_file, project_root)
    
    # Find in coverage report
    found = False
    for package in root.findall(f'.//package[@name="{package_name}"]'):
        for sourcefile in package.findall(f'sourcefile[@name="{filename}"]'):
            found = True
            files_checked.append(filename)
            
            lines = sourcefile.findall('line')
            
            # Filter to only changed lines if we have that info
            if changed_lines is not None and len(changed_lines) > 0:
                # Only check lines that were added/modified
                relevant_lines = [l for l in lines if int(l.get('nr', 0)) in changed_lines]
            else:
                # Fallback: check all lines if we can't get changed lines
                relevant_lines = lines
            
            # Count only executable lines (mi > 0 means instructions exist)
            file_total = len([l for l in relevant_lines if int(l.get('mi', 0)) > 0 or int(l.get('ci', 0)) > 0])
            file_covered = len([l for l in relevant_lines if int(l.get('ci', 0)) > 0])
            file_uncovered = len([l for l in relevant_lines if l.get('ci') == '0' and int(l.get('mi', 0)) > 0])
            
            if file_total > 0:
                total_lines += file_total
                total_uncovered += file_uncovered
                coverage_pct = (file_covered / file_total * 100) if file_total > 0 else 100
                
                if generate_markdown:
                    status = "✅" if coverage_pct >= threshold else "❌"
                    changed_info = f" ({len(changed_lines)} changed lines)" if changed_lines else " (all lines - could not determine changed lines)"
                    markdown_output.append(f"- {status} **{filename}**: {file_covered}/{file_total} changed lines ({coverage_pct:.1f}%){changed_info}")
                    if coverage_pct < threshold:
                        files_below_threshold.append((filename, coverage_pct, file_uncovered))
                        markdown_output.append(f"  - ⚠️ Below threshold: {file_uncovered} uncovered changed lines")
                else:
                    status = "✓" if coverage_pct >= threshold else "✗"
                    color = "" if coverage_pct >= threshold else "\033[0;31m"
                    reset = "\033[0m" if color else ""
                    changed_info = f" ({len(changed_lines)} changed lines)" if changed_lines else " (all lines - could not determine changed lines)"
                    print(f"  {color}{status}{reset} {filename}: {file_covered}/{file_total} changed lines ({coverage_pct:.1f}%){changed_info}")
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
        markdown_output.append(f"\n### Overall Coverage (Changed Lines Only)\n")
        markdown_output.append(f"**{total_lines - total_uncovered}/{total_lines}** changed lines covered ({overall_coverage:.1f}%)\n")
        
        if files_below_threshold:
            markdown_output.append(f"\n### ❌ Coverage Check Failed\n")
            markdown_output.append(f"Files below {threshold}% threshold:\n")
            for filename, pct, uncovered in files_below_threshold:
                markdown_output.append(f"- **{filename}**: {pct:.1f}% ({uncovered} uncovered changed lines)\n")
        
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
        print(f"\n  Overall: {(total_lines - total_uncovered)}/{total_lines} changed lines covered ({overall_coverage:.1f}%)")
        
        if files_below_threshold:
            print(f"\n  Files below {threshold}% threshold:")
            for filename, pct, uncovered in files_below_threshold:
                print(f"    • {filename}: {pct:.1f}% ({uncovered} uncovered changed lines)")
            sys.exit(1)
        else:
            print(f"\n  ✓ All files meet {threshold}% threshold for changed lines")
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
    cd "$REPO_ROOT"
    # Check if there are uncommitted changes - if so, we need to handle them differently
    STAGED_COUNT=$(git diff --cached --name-only 2>/dev/null | grep -E '\.(kt|java)$' | wc -l | tr -d ' ')
    UNSTAGED_COUNT=$(git diff --name-only 2>/dev/null | grep -E '\.(kt|java)$' | wc -l | tr -d ' ')
    
    if [ "$STAGED_COUNT" -gt 0 ] || [ "$UNSTAGED_COUNT" -gt 0 ]; then
        # There are uncommitted changes - diff-cover won't see them with --compare-branch
        # So we'll note this in the output
        echo -e "${YELLOW}  Note: HTML report shows committed changes only${NC}"
        echo -e "${YELLOW}  Uncommitted changes are checked in the console output above${NC}"
    fi
    
    python3 -m diff_cover.diff_cover_tool "$PROJECT_ROOT/build/reports/jacoco/merged/jacocoMergedReport.xml" \
        --compare-branch="$BASE_BRANCH" \
        --format html:"$HTML_REPORT" 2>&1 | grep -v "No lines with coverage" || true
    
    if [ -f "$HTML_REPORT" ]; then
        echo -e "${GREEN}✓ HTML report generated: $HTML_REPORT${NC}"
        if [ "$STAGED_COUNT" -gt 0 ] || [ "$UNSTAGED_COUNT" -gt 0 ]; then
            echo -e "${YELLOW}  Note: Report shows committed changes only (uncommitted changes shown in console)${NC}"
        fi
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
