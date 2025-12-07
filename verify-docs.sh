#!/bin/bash
# Documentation Verification Script
# Smooth Layered Terrain System v3.2

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Counters
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
WARNINGS=0

# Functions
print_header() {
    echo -e "${BLUE}=================================================${NC}"
    echo -e "${BLUE}  Documentation Verification${NC}"
    echo -e "${BLUE}  Smooth Layered Terrain System v3.2${NC}"
    echo -e "${BLUE}=================================================${NC}"
    echo ""
}

print_section() {
    echo ""
    echo -e "${BLUE}━━━ $1 ━━━${NC}"
}

check_pass() {
    ((TOTAL_CHECKS++))
    ((PASSED_CHECKS++))
    echo -e "${GREEN}✓${NC} $1"
}

check_fail() {
    ((TOTAL_CHECKS++))
    ((FAILED_CHECKS++))
    echo -e "${RED}✗${NC} $1"
}

check_warn() {
    ((WARNINGS++))
    echo -e "${YELLOW}⚠${NC} $1"
}

# Start verification
print_header

# ============================================================================
# 1. Check File Structure
# ============================================================================
print_section "Checking File Structure"

# Required files
REQUIRED_FILES=(
    "docs/README.md"
    "docs/AGENTS.md"
    "docs/ARCHITECTURE.md"
    "docs/API.md"
    "docs/TROUBLESHOOTING.md"
    "ROADMAP.md"
    "TODO.md"
    "CHANGELOG.md"
    "README.md"
)

for file in "${REQUIRED_FILES[@]}"; do
    if [ -f "$file" ]; then
        check_pass "Found $file"
    else
        check_fail "Missing $file"
    fi
done

# Check for old files that should be removed
OLD_FILES=(
    "INSTRUKSI.md"
    "PROJECT.md"
)

for file in "${OLD_FILES[@]}"; do
    if [ -f "$file" ]; then
        check_fail "$file should be removed/moved"
    else
        check_pass "$file properly removed"
    fi
done

# ============================================================================
# 2. Check File Sizes
# ============================================================================
print_section "Checking File Sizes"

check_file_size() {
    local file=$1
    local max_size=$2
    local description=$3
    
    if [ ! -f "$file" ]; then
        return
    fi
    
    local size=$(wc -c < "$file")
    local size_kb=$((size / 1024))
    
    if [ $size_kb -lt $max_size ]; then
        check_pass "$description: ${size_kb}KB (max: ${max_size}KB)"
    else
        check_warn "$description: ${size_kb}KB exceeds recommended ${max_size}KB"
    fi
}

check_file_size "docs/AGENTS.md" 60 "AGENTS.md"
check_file_size "docs/ARCHITECTURE.md" 15 "ARCHITECTURE.md"
check_file_size "docs/API.md" 10 "API.md"
check_file_size "docs/TROUBLESHOOTING.md" 10 "TROUBLESHOOTING.md"
check_file_size "README.md" 20 "README.md"

# ============================================================================
# 3. Check Cross-References
# ============================================================================
print_section "Checking Cross-References"

check_link() {
    local file=$1
    local link=$2
    local target=$3
    
    if [ ! -f "$file" ]; then
        return
    fi
    
    if grep -q "$link" "$file"; then
        # Check if target exists
        if [ -f "$target" ]; then
            check_pass "$file links to $target"
        else
            check_fail "$file links to non-existent $target"
        fi
    else
        check_warn "$file should link to $target"
    fi
}

# Check AGENTS.md links
check_link "docs/AGENTS.md" "ARCHITECTURE.md" "docs/ARCHITECTURE.md"
check_link "docs/AGENTS.md" "TROUBLESHOOTING.md" "docs/TROUBLESHOOTING.md"

# Check README.md links
check_link "README.md" "docs/ARCHITECTURE.md" "docs/ARCHITECTURE.md"
check_link "README.md" "docs/API.md" "docs/API.md"
check_link "README.md" "docs/TROUBLESHOOTING.md" "docs/TROUBLESHOOTING.md"

# Check ROADMAP.md links
check_link "ROADMAP.md" "AGENTS.md" "docs/AGENTS.md"

# Check TODO.md links
check_link "TODO.md" "AGENTS.md" "docs/AGENTS.md"

# ============================================================================
# 4. Check Documentation Quality
# ============================================================================
print_section "Checking Documentation Quality"

check_has_content() {
    local file=$1
    local pattern=$2
    local description=$3
    
    if [ ! -f "$file" ]; then
        return
    fi
    
    if grep -q "$pattern" "$file"; then
        check_pass "$file has $description"
    else
        check_warn "$file missing $description"
    fi
}

# Check AGENTS.md content
check_has_content "docs/AGENTS.md" "Quick Start" "Quick Start section"
check_has_content "docs/AGENTS.md" "Implementation" "Implementation guide"
check_has_content "docs/AGENTS.md" "Best Practices" "Best Practices"
check_has_content "docs/AGENTS.md" "Testing" "Testing strategy"

# Check ARCHITECTURE.md content
check_has_content "docs/ARCHITECTURE.md" "Component Hierarchy" "Component Hierarchy"
check_has_content "docs/ARCHITECTURE.md" "Data Flow" "Data Flow"
check_has_content "docs/ARCHITECTURE.md" "Performance" "Performance section"

# Check README.md content
check_has_content "README.md" "Features" "Features section"
check_has_content "README.md" "Installation" "Installation guide"
check_has_content "README.md" "Commands" "Commands list"

# ============================================================================
# 5. Check for Duplication
# ============================================================================
print_section "Checking for Content Duplication"

# Check if INSTRUKSI content is in AGENTS.md
if [ -f "docs/AGENTS.md" ]; then
    if grep -q "Prosedur Setiap Sesi\|Standard Session Workflow" "docs/AGENTS.md"; then
        check_pass "INSTRUKSI content merged into AGENTS.md"
    else
        check_warn "INSTRUKSI content may not be fully merged"
    fi
fi

# Check if AGENTS and ARCHITECTURE don't have excessive overlap
if [ -f "docs/AGENTS.md" ] && [ -f "docs/ARCHITECTURE.md" ]; then
    # Simple check: Architecture shouldn't have implementation details
    if grep -q "Step-by-step\|TODO\|Implementation Checklist" "docs/ARCHITECTURE.md"; then
        check_warn "ARCHITECTURE.md may have implementation details (should be in AGENTS.md)"
    else
        check_pass "ARCHITECTURE.md properly focused on design"
    fi
fi

# ============================================================================
# 6. Check Markdown Syntax
# ============================================================================
print_section "Checking Markdown Syntax"

check_markdown() {
    local file=$1
    
    if [ ! -f "$file" ]; then
        return
    fi
    
    # Check for common markdown issues
    local issues=0
    
    # Check for broken links [text]()
    if grep -q "\[.*\]()" "$file"; then
        check_warn "$file has empty links"
        ((issues++))
    fi
    
    # Check for unclosed code blocks
    local backticks=$(grep -o '```' "$file" | wc -l)
    if [ $((backticks % 2)) -ne 0 ]; then
        check_fail "$file has unclosed code blocks"
        ((issues++))
    fi
    
    if [ $issues -eq 0 ]; then
        check_pass "$file syntax looks good"
    fi
}

check_markdown "docs/AGENTS.md"
check_markdown "docs/ARCHITECTURE.md"
check_markdown "docs/API.md"
check_markdown "docs/TROUBLESHOOTING.md"
check_markdown "README.md"

# ============================================================================
# 7. Check Version Consistency
# ============================================================================
print_section "Checking Version Consistency"

VERSION="3.2.0"

check_version() {
    local file=$1
    
    if [ ! -f "$file" ]; then
        return
    fi
    
    if grep -q "$VERSION\|v3.2" "$file"; then
        check_pass "$file has version $VERSION"
    else
        check_warn "$file may need version update"
    fi
}

check_version "README.md"
check_version "docs/AGENTS.md"
check_version "docs/ARCHITECTURE.md"

# ============================================================================
# 8. Summary
# ============================================================================
print_section "Verification Summary"

echo ""
echo "Total Checks: $TOTAL_CHECKS"
echo -e "Passed: ${GREEN}$PASSED_CHECKS${NC}"
echo -e "Failed: ${RED}$FAILED_CHECKS${NC}"
echo -e "Warnings: ${YELLOW}$WARNINGS${NC}"
echo ""

# Calculate score
if [ $TOTAL_CHECKS -gt 0 ]; then
    SCORE=$(( (PASSED_CHECKS * 100) / TOTAL_CHECKS ))
    
    echo -e "Score: ${BLUE}${SCORE}%${NC}"
    echo ""
    
    if [ $SCORE -ge 90 ]; then
        echo -e "${GREEN}✓ Documentation is in excellent shape!${NC}"
        exit 0
    elif [ $SCORE -ge 70 ]; then
        echo -e "${YELLOW}⚠ Documentation needs some improvements${NC}"
        exit 0
    else
        echo -e "${RED}✗ Documentation needs significant work${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ No checks performed${NC}"
    exit 1
fi