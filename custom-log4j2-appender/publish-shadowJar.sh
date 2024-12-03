#!/bin/bash
# clean existing stuff
rm -rf build io
# copy correcr build.gradle
cp build-shadowJar.gradle build.gradle
# Set variables
GROUP_ID="io.github.newrelic-experimental"
ARTIFACT_ID="custom-log4j2-appender"
VERSION="1.0.4"
KEY_ID="0ED9FD74E81E6D83FAE25F235640EA0B1C631C6F" # Replace with your actual key ID

# Get the current directory (assuming the script is run from the custom-log4j2-appender directory)
CURRENT_DIR=$(pwd)
PROJECT_ROOT=$(dirname "$CURRENT_DIR")

echo "Current Directory: $CURRENT_DIR"
echo "Project Root: $PROJECT_ROOT"

# Clean and build the project
cd "$PROJECT_ROOT"
./gradlew clean build shadowJar javadocJar sourcesJar generatePomFileForMavenJavaPublication

# Navigate to the directory containing the artifacts
cd "$CURRENT_DIR/build/libs"

# List the contents of the build/libs directory for debugging
echo "Contents of build/libs:"
ls -la

# Find the files without version numbers
JAR_FILE="custom-log4j2-appender.jar"
JAVADOC_FILE="custom-log4j2-appender-javadoc.jar"
SOURCES_FILE="custom-log4j2-appender-sources.jar"
POM_FILE="custom-log4j2-appender-$VERSION.pom"

echo "JAR File: $JAR_FILE"
echo "Javadoc File: $JAVADOC_FILE"
echo "Sources File: $SOURCES_FILE"
echo "POM File: $POM_FILE"

# Check if the files exist before proceeding
if [ ! -f "$JAR_FILE" ] || [ ! -f "$JAVADOC_FILE" ] || [ ! -f "$SOURCES_FILE" ]; then
    echo "One or more expected files are missing in build/libs"
    ls -la
    exit 1
fi

# Prepare the directory structure
TARGET_DIR="$CURRENT_DIR/io/github/newrelic-experimental/custom-log4j2-appender/$VERSION"
mkdir -p $TARGET_DIR

# Copy the built artifacts to the appropriate directory with version numbers
echo "Copying artifacts to $TARGET_DIR"
cp $JAR_FILE $TARGET_DIR/custom-log4j2-appender-$VERSION.jar
cp $JAVADOC_FILE $TARGET_DIR/custom-log4j2-appender-$VERSION-javadoc.jar
cp $SOURCES_FILE $TARGET_DIR/custom-log4j2-appender-$VERSION-sources.jar

# Navigate to the target directory
cd $TARGET_DIR

# Generate checksums for the copied files
for file in custom-log4j2-appender-$VERSION.jar custom-log4j2-appender-$VERSION-javadoc.jar custom-log4j2-appender-$VERSION-sources.jar; do
    echo "Generating checksums for $file"
    md5sum $file | awk '{ print $1 }' > $file.md5
    sha1sum $file | awk '{ print $1 }' > $file.sha1
done

# Generate GPG signatures using the specific key ID
for file in custom-log4j2-appender-$VERSION.jar custom-log4j2-appender-$VERSION-javadoc.jar custom-log4j2-appender-$VERSION-sources.jar; do
    echo "Generating GPG signature for $file"
    gpg --local-user $KEY_ID --armor --detach-sign $file
done

# Generate and copy the POM file
cd "$PROJECT_ROOT"
./gradlew generatePomFileForMavenJavaPublication
cp custom-log4j2-appender/build/publications/mavenJava/pom-default.xml $TARGET_DIR/$POM_FILE

# Navigate back to the target directory
cd $TARGET_DIR

# Generate checksums and signatures for the POM file
echo "Generating checksums and GPG signature for $POM_FILE"
md5sum $POM_FILE | awk '{ print $1 }' > $POM_FILE.md5
sha1sum $POM_FILE | awk '{ print $1 }' > $POM_FILE.sha1
gpg --local-user $KEY_ID --armor --detach-sign $POM_FILE

# Verify checksums
echo "Verifying checksums"
for file in custom-log4j2-appender-$VERSION.jar custom-log4j2-appender-$VERSION-javadoc.jar custom-log4j2-appender-$VERSION-sources.jar $POM_FILE; do
    if [ -f "$file" ]; then
        echo "Verifying checksum for $file"
        md5sum -c <(echo "$(cat $file.md5)  $file")
        sha1sum -c <(echo "$(cat $file.sha1)  $file")
    else
        echo "File $file does not exist in the target directory $TARGET_DIR."
        ls -la $TARGET_DIR
    fi
done

# Navigate back to the custom-log4j2-appender directory
cd "$CURRENT_DIR"

# Create a ZIP file containing the entire directory structure
echo "Creating ZIP file custom-log4j2-appender-$VERSION.zip"
zip -r custom-log4j2-appender-$VERSION.zip io

echo "Artifacts prepared and zipped successfully. You can now upload custom-log4j2-appender-$VERSION.zip to Sonatype OSSRH."
