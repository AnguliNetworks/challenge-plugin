#!/bin/bash

# Production Minecraft Server Startup Script
# Automatically downloads latest plugin release from GitHub before starting
# Usage: ./script.sh <server-jar-file>

# Check if server file argument is provided
if [ $# -eq 0 ]; then
    echo "Error: No server file specified"
    echo "Usage: $0 <server-jar-file>"
    echo "Example: $0 paper-1.21.4.jar"
    exit 1
fi

SERVER_JAR="$1"

# Check if server file exists
if [ ! -f "$SERVER_JAR" ]; then
    echo "Error: Server file '$SERVER_JAR' not found"
    exit 1
fi

echo "=== Production Server Startup ==="
echo "$(date): Starting production server with auto-update..."
echo "$(date): Using server file: $SERVER_JAR"

# Configuration
GITHUB_REPO="AnguliNetworks/challenge-plugin"
PLUGIN_DIR="plugins"
PLUGIN_NAME="ChallengePlugin.jar"

# Create plugins directory if it doesn't exist
mkdir -p "$PLUGIN_DIR"

echo "$(date): Checking for latest plugin release..."

# Get the most recent release download URL (including prereleases)
LATEST_RELEASE_URL=$(curl -s "https://api.github.com/repos/$GITHUB_REPO/releases" | \
    grep "browser_download_url.*\.jar" | \
    head -1 | \
    cut -d '"' -f 4)

if [ -n "$LATEST_RELEASE_URL" ]; then
    # Extract version info from URL for better logging
    VERSION_TAG=$(echo "$LATEST_RELEASE_URL" | grep -o 'v[^/]*' | tail -1)
    echo "$(date): Found release: $VERSION_TAG"
    echo "$(date): Downloading from: $LATEST_RELEASE_URL"

    # Download the latest plugin JAR
    if curl -L -o "$PLUGIN_DIR/$PLUGIN_NAME" "$LATEST_RELEASE_URL"; then
        echo "$(date): Plugin updated successfully to $VERSION_TAG!"
    else
        echo "$(date): Warning: Failed to download plugin, using existing version"
    fi
else
    echo "$(date): Warning: Could not find latest release, using existing plugin version"
fi

echo "$(date): Starting Minecraft server..."

# Start the server with production settings using the provided server file
java -Xms12288M -Xmx12288M -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -jar "$SERVER_JAR" nogui
