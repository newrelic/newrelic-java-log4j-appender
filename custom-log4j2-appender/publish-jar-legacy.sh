#!/bin/bash

# clean existing stuff
rm -rf build bundle
# copy correct build.gradle
cp build-jar.gradle build.gradle
# Set variables
GROUP_ID="com.newrelic.labs"
ARTIFACT_ID="custom-log4j2-appender"
VERSION="1.0.10"
KEY_ID="0ED9FD74E81E6D83FAE25F235640EA0B1C631C6F" # Replace with your actual key ID

# Get the current directory (assuming the script is run from the custom-log4j2-appender directory)
CURRENT_DIR=$(pwd)
PROJECT_ROOT=$(dirname "$CURRENT_DIR")

echo "Current Directory: $CURRENT_DIR"
echo "Project Root: $PROJECT_ROOT"

# Clean and build the project
cd "$PROJECT_ROOT"
./gradlew clean build jar javadocJar sourcesJar generatePomFileForMavenJavaPublication

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
TARGET_DIR="$CURRENT_DIR/bundle"
mkdir -p $TARGET_DIR

# Copy the built artifacts to the appropriate directory with version numbers
echo "Copying artifacts to $TARGET_DIR"
cp $JAR_FILE $TARGET_DIR/custom-log4j2-appender-$VERSION.jar
cp $JAVADOC_FILE $TARGET_DIR/custom-log4j2-appender-$VERSION-javadoc.jar
cp $SOURCES_FILE $TARGET_DIR/custom-log4j2-appender-$VERSION-sources.jar

# Navigate to the target directory
cd $TARGET_DIR

# Generate checksums for the copied files
# for legacy checkusum is not needed and hence commenting
# for file in custom-log4j2-appender-$VERSION.jar custom-log4j2-appender-$VERSION-javadoc.jar custom-log4j2-appender-$VERSION-sources.jar; do
   # echo "Generating checksums for $file"
  #  md5sum $file | awk '{ print $1 }' > $file.md5
  #  sha1sum $file | awk '{ print $1 }' > $file.sha1
# done

# Generate GPG signatures using the specific key ID
for file in custom-log4j2-appender-$VERSION.jar custom-log4j2-appender-$VERSION-javadoc.jar custom-log4j2-appender-$VERSION-sources.jar; do
    echo "Generating GPG signature for $file"
    gpg --local-user $KEY_ID --armor --detach-sign $file
done

# Generate the dependencies section for the POM file
DEPENDENCIES=$(cat <<EOF
<dependency>
  <groupId>com.squareup.okhttp3</groupId>
  <artifactId>okhttp</artifactId>
  <version>4.9.3</version>
  <scope>compile</scope>
</dependency>
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <version>2.13.1</version>
  <scope>compile</scope>
</dependency>
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-core</artifactId>
  <version>2.24.3</version>
  <scope>compile</scope>
</dependency>
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-api</artifactId>
  <version>2.24.3</version>
  <scope>compile</scope>
</dependency>
<dependency>
  <groupId>com.newrelic.telemetry</groupId>
  <artifactId>telemetry-http-okhttp</artifactId>
  <version>0.16.0</version>
  <scope>compile</scope>
</dependency>
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>slf4j-api</artifactId>
  <version>1.7.32</version>
  <scope>compile</scope>
</dependency>
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-slf4j-impl</artifactId>
  <version>2.24.3</version>
  <scope>compile</scope>
</dependency>
EOF
)

# Read the POM template, replace placeholders, and save the final POM file
POM_TEMPLATE=$(<"$CURRENT_DIR/pom-template.xml")
POM_CONTENT="${POM_TEMPLATE//\{\{GROUP_ID\}\}/$GROUP_ID}"
POM_CONTENT="${POM_CONTENT//\{\{ARTIFACT_ID\}\}/$ARTIFACT_ID}"
POM_CONTENT="${POM_CONTENT//\{\{VERSION\}\}/$VERSION}"
POM_CONTENT="${POM_CONTENT//\{\{DEPENDENCIES\}\}/$DEPENDENCIES}"

echo "$POM_CONTENT" > "$TARGET_DIR/$POM_FILE"

# Generate checksums and signatures for the POM file
#  for legacy checkusum is not needed and hence commenting
# echo "Generating checksums and GPG signature for $POM_FILE"
# md5sum $POM_FILE | awk '{ print $1 }' > $POM_FILE.md5
# sha1sum $POM_FILE | awk '{ print $1 }' > $POM_FILE.sha1
gpg --local-user $KEY_ID --armor --detach-sign $POM_FILE

# Verify checksums
#  for legacy checkusum is not needed and hence commenting
echo "Verifying checksums"
# for file in custom-log4j2-appender-$VERSION.jar custom-log4j2-appender-$VERSION-javadoc.jar custom-log4j2-appender-$VERSION-sources.jar $POM_FILE; do
    # if [ -f "$file" ]; then
    #     echo "Verifying checksum for $file"
    #     md5sum -c <(echo "$(cat $file.md5)  $file")
    #     sha1sum -c <(echo "$(cat $file.sha1)  $file")
   #  else
      #   echo "File $file does not exist in the target directory $TARGET_DIR."
      #   ls -la $TARGET_DIR
   #  fi
#  done

# Navigate back to the custom-log4j2-appender directory
cd "$CURRENT_DIR/bundle"

# Create a ZIP file containing the entire directory structure
echo "Creating jar bundle-$VERSION.jar"
jar -cvf ../bundle-$VERSION.jar *
cd ..
echo "Artifacts prepared and zipped successfully. You can now upload bundle-$VERSION.jar to Sonatype OSSRH https://oss.sonatype.org/#welcome ."
