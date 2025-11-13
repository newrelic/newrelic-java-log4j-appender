#!/bin/bash

# Maven-based publishing script for custom-log4j2-appender
# Based on successful otel-mule4-observability-agent approach

set -e

# Get the current directory
CURRENT_DIR=$(pwd)
PROJECT_DIR="$CURRENT_DIR"

echo "Current Directory: $CURRENT_DIR"
echo "Starting Maven build and deploy process..."

# Clean and build the project with Maven
echo "Running Maven clean install..."
mvn clean install

# Deploy to Sonatype OSSRH
echo "Deploying to Sonatype OSSRH..."
mvn deploy -DskipTests -Prelease

echo ""
echo "✅ Maven build and deploy completed successfully!"
echo ""
echo "The artifacts have been deployed to Sonatype OSSRH staging repository."
echo "Please log in to https://oss.sonatype.org/ to:"
echo "1. Close the staging repository"
echo "2. Release to Maven Central"
echo ""
echo "Artifacts deployed:"
echo "  - custom-log4j2-appender-1.1.4.jar"
echo "  - custom-log4j2-appender-1.1.4-sources.jar"
echo "  - custom-log4j2-appender-1.1.4-javadoc.jar"
echo "  - custom-log4j2-appender-1.1.4.pom"
echo "  - All artifacts signed with GPG"