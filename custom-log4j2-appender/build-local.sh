#!/bin/bash
# clean existing stuff
rm -rf build io
# copy correct build.gradle
cp build-shadowJar.gradle build.gradle

# Get the current directory (assuming the script is run from the custom-log4j2-appender directory)
CURRENT_DIR=$(pwd)
PROJECT_ROOT=$(dirname "$CURRENT_DIR")

echo "Current Directory: $CURRENT_DIR"
echo "Project Root: $PROJECT_ROOT"

# Clean and build the project
cd "$PROJECT_ROOT"
./gradlew clean build shadowJar 

# Navigate to the directory containing the artifacts
cd "$CURRENT_DIR/build/libs"

# List the contents of the build/libs directory for debugging
echo "Contents of build/libs:"
ls -la

echo "Local jar custom-log4j2-appender.jar is produced. You can now copy custom-log4j2-appender.jar to your applications classpath ."
