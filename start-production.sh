#!/bin/bash

# Production Minecraft Server Startup Script
# Automatically downloads latest plugin release from GitHub before starting

echo "=== Production Server Startup ==="
echo "$(date): Starting production server with auto-update..."

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

# Start the server with production settings
nohup java -Xms3G -Xmx4G -jar paper-1.21.4.jar --nogui > minecraft.log 2>&1 &

# Get the process ID
SERVER_PID=$!
echo "$(date): Server started with PID: $SERVER_PID"
echo "$SERVER_PID" > server.pid

echo "=== Server startup complete ==="
echo "Monitor logs with: tail -f minecraft.log"
echo "Stop server with: kill \$(cat server.pid)"