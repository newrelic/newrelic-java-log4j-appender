#!/bin/bash

echo "🚀 Starting build and publish process..."
echo "Using proven approach from otel-mule4-observability-agent"

# Build and deploy to staging
echo "📦 Building and deploying to Sonatype OSSRH..."
mvn clean deploy -Prelease

if [ $? -ne 0 ]; then
  echo "❌ Maven deploy failed"
  exit 1
fi

echo "✅ Maven deploy completed successfully!"

# Auto-publish using Sonatype API
echo "🔄 Auto-publishing to Maven Central..."
curl -X POST \
  'https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/com.newrelic.labs?publishing_type=user_managed' \
  -H "Authorization: Bearer $OSSRH_LABS_TOKEN" \
  -d ''

if [ $? -ne 0 ]; then
  echo "❌ Curl upload failed - but artifacts are still available in staging"
  echo "You can manually release from https://oss.sonatype.org/"
  exit 1
fi

echo "🎉 Build and publish completed successfully!"
echo "Your artifacts should be available on Maven Central shortly."