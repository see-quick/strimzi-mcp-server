#!/usr/bin/env bash
set -e

# Release script for strimzi-mcp-server
# Usage: ./bin/release.sh [patch|minor|major]
#        Default: patch

BUMP_TYPE="${1:-patch}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
POM_FILE="$PROJECT_DIR/pom.xml"
LAUNCHER_SCRIPT="$SCRIPT_DIR/strimzi-mcp"

cd "$PROJECT_DIR"

# Extract current version from pom.xml
CURRENT_VERSION=$(grep -m1 '<version>' "$POM_FILE" | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

if [[ ! "$CURRENT_VERSION" =~ -SNAPSHOT$ ]]; then
    echo "Error: Current version ($CURRENT_VERSION) is not a SNAPSHOT version"
    exit 1
fi

# Remove -SNAPSHOT for release version
RELEASE_VERSION="${CURRENT_VERSION%-SNAPSHOT}"
echo "Current version: $CURRENT_VERSION"
echo "Release version: $RELEASE_VERSION"

# Parse version components
IFS='.' read -r MAJOR MINOR PATCH <<< "$RELEASE_VERSION"

# Calculate next version based on bump type
case "$BUMP_TYPE" in
    major)
        NEXT_VERSION="$((MAJOR + 1)).0.0-SNAPSHOT"
        ;;
    minor)
        NEXT_VERSION="$MAJOR.$((MINOR + 1)).0-SNAPSHOT"
        ;;
    patch)
        NEXT_VERSION="$MAJOR.$MINOR.$((PATCH + 1))-SNAPSHOT"
        ;;
    *)
        echo "Error: Invalid bump type '$BUMP_TYPE'. Use: patch, minor, or major"
        exit 1
        ;;
esac

echo "Next dev version: $NEXT_VERSION"
echo ""

# Confirm with user
read -p "Proceed with release? [y/N] " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo "==> Running tests..."
./mvnw clean verify -q

echo ""
echo "==> Updating pom.xml to release version $RELEASE_VERSION..."
sed -i.bak "s|<version>$CURRENT_VERSION</version>|<version>$RELEASE_VERSION</version>|" "$POM_FILE"
rm -f "$POM_FILE.bak"

echo "==> Updating launcher script version..."
sed -i.bak "s|VERSION=\"\${STRIMZI_MCP_VERSION:-[^\"]*}\"|VERSION=\"\${STRIMZI_MCP_VERSION:-$RELEASE_VERSION}\"|" "$LAUNCHER_SCRIPT"
rm -f "$LAUNCHER_SCRIPT.bak"

echo "==> Building release jar..."
./mvnw package -DskipTests -q

echo ""
echo "==> Committing release version..."
git add "$POM_FILE" "$LAUNCHER_SCRIPT"
git commit -m "Release v$RELEASE_VERSION"

echo "==> Creating tag v$RELEASE_VERSION..."
git tag -a "v$RELEASE_VERSION" -m "Release v$RELEASE_VERSION"

echo ""
echo "==> Updating pom.xml to next snapshot version $NEXT_VERSION..."
sed -i.bak "s|<version>$RELEASE_VERSION</version>|<version>$NEXT_VERSION</version>|" "$POM_FILE"
rm -f "$POM_FILE.bak"

# Extract just the version number without -SNAPSHOT for launcher script
NEXT_RELEASE="${NEXT_VERSION%-SNAPSHOT}"
sed -i.bak "s|VERSION=\"\${STRIMZI_MCP_VERSION:-[^\"]*}\"|VERSION=\"\${STRIMZI_MCP_VERSION:-$NEXT_RELEASE}\"|" "$LAUNCHER_SCRIPT"
rm -f "$LAUNCHER_SCRIPT.bak"

echo "==> Committing next development version..."
git add "$POM_FILE" "$LAUNCHER_SCRIPT"
git commit -m "Prepare for next development iteration ($NEXT_VERSION)"

echo ""
echo "==> Pushing commits and tags..."
git push && git push --tags

echo ""
echo "============================================"
echo "Release v$RELEASE_VERSION complete!"
echo ""
echo "Release jar: target/strimzi-mcp-server-$RELEASE_VERSION.jar"
echo ""
echo "Next steps:"
echo "  1. Go to GitHub and create a release for tag v$RELEASE_VERSION"
echo "  2. Upload the jar file to the release"
echo "============================================"