<a href="https://opensource.newrelic.com/oss-category/#new-relic-experimental"><picture><source media="(prefers-color-scheme: dark)" srcset="https://github.com/newrelic/opensource-website/raw/main/src/images/categories/dark/Experimental.png"><source media="(prefers-color-scheme: light)" srcset="https://github.com/newrelic/opensource-website/raw/main/src/images/categories/Experimental.png"><img alt="New Relic Open Source experimental project banner." src="https://github.com/newrelic/opensource-website/raw/main/src/images/categories/Experimental.png"></picture></a>


![GitHub forks](https://img.shields.io/github/forks/newrelic-experimental/newrelic-java-log4j-appender?style=social)
![GitHub stars](https://img.shields.io/github/stars/newrelic-experimental/newrelic-java-log4j-appender?style=social)
![GitHub watchers](https://img.shields.io/github/watchers/newrelic-experimental/newrelic-java-log4j-appender?style=social)

![GitHub all releases](https://img.shields.io/github/downloads/newrelic-experimental/newrelic-java-log4j-appender/total)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/newrelic-experimental/newrelic-java-log4j-appender)
![GitHub last commit](https://img.shields.io/github/last-commit/newrelic-experimental/newrelic-java-log4j-appender)
![GitHub Release Date](https://img.shields.io/github/release-date/newrelic-experimental/newrelic-java-log4j-appender)


![GitHub issues](https://img.shields.io/github/issues/newrelic-experimental/newrelic-java-log4j-appender)
![GitHub issues closed](https://img.shields.io/github/issues-closed/newrelic-experimental/newrelic-java-log4j-appender)
![GitHub pull requests](https://img.shields.io/github/issues-pr/newrelic-experimental/newrelic-java-log4j-appender)
![GitHub pull requests closed](https://img.shields.io/github/issues-pr-closed/newrelic-experimental/newrelic-java-log4j-appender)

# New Relic Log4j2 Appender

A custom Log4j2 appender that sends logs to New Relic.

## Installation

Add the library to your project using Maven Central:

```xml
<dependency>
    <groupId>io.github.newrelic-experimental</groupId>
    <artifactId>custom-log4j2-appender</artifactId>
    <version>0.0.8</version>
</dependency>
```

Or, if using a locally built JAR file:

```xml
<dependency>
    <groupId>io.github.newrelic-experimental</groupId>
    <artifactId>custom-log4j2-appender</artifactId>
    <version>0.0.8</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/src/main/resources/custom-log4j2-appender.jar</systemPath>
</dependency>
```

## Usage

### Set up New Relic Log4j2 Appender

Follow the instructions for setting up the New Relic Log4j2 Appender.

### Log4J XML Configuration

Replace `[your-api-key]` with the ingest key obtained from the New Relic platform.

#### log4j2.xml:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="DEBUG" name="cloudhub" packages="com.newrelic.labs">
    <Appenders>
        <!-- Custom New Relic Batching Appender with Pattern Layout -->
        <NewRelicBatchingAppender name="NewRelicAppender"
                                  apiKey="[your-api-key]"
                                  apiUrl="https://log-api.newrelic.com/log/v1"
                                  logType="muleLog"
                                  applicationName="your-application-name"
                                  batchSize="5000"
                                  maxMessageSize="1048576"
                                  flushInterval="120000">
            <PatternLayout pattern="[%d{MM-dd HH:mm:ss}] %-5p %c{1} [%t]: %m%n"/>
        </NewRelicBatchingAppender>
    </Appenders>

    <Loggers>
        <AsyncRoot level="INFO">
            <AppenderRef ref="NewRelicAppender"/>
        </AsyncRoot>
    </Loggers>
</Configuration>
```

### Parameters

| Parameter           | Required? | Default Value | Description                                                                 |
|---------------------|-----------|---------------|-----------------------------------------------------------------------------|
| name                | Yes       |               | Name used to register Log4j Appender                                        |
| apiKey              | Yes       |               | API key for authenticating with New Relic's logging service                 |
| apiUrl              | Yes       |               | URL for New Relic's log ingestion API                                       |
| logType             | No        | "muleLog"     | Type of log being sent                                                      |
| applicationName     | Yes       |               | Name of the application generating the logs                                 |
| batchSize           | No        | 5000          | Maximum number of log entries to batch together before sending to New Relic |
| maxMessageSize      | No        | 1048576       | Maximum size (in bytes) of the payload to be sent in a single HTTP request  |
| flushInterval       | No        | 120000        | Interval (in milliseconds) at which the log entries are flushed to New Relic|

### TLS 1.2 Requirement

New Relic only accepts connections from clients using TLS version 1.2 or greater. Ensure that your execution environment is configured to use TLS 1.2 or greater.

### Create GROK Parsing Rule at New Relic Platform

- **Name**: `NRMuleParser`
- **Field to parse**: `message`
- **Filter logs based on NRQL**: `logtype = 'muleLog'`
- **Parsing rule**:
  ```sh
  %{LOGLEVEL:log.level}  %{DATA:log.logger} \[%{DATA:log.thread}\]: %{GREEDYDATA:log.message}
  ```

## Sample log details at New Relic Platform

<img width="745" alt="image" src="https://github.com/user-attachments/assets/6adff21d-7fdf-4b39-b19e-0ed385ff6ed5">

## Building

### Building the Local JAR File

```sh
cd custom-log4j2-appender
./build-local.sh 
```

### Copying the Local JAR File to the Destination

Example:

```sh
cp build/libs/custom-log4j2-appender.jar /Users/gsidhwani/AnypointStudio/studio-workspace/samplemuleapp/src/main/resources/
```

## Scripts

### `publish-jar.sh` and `publish-shadowJar.sh`

These scripts generate a Maven publishing compatible bundle for publishing to Maven Central. This is for New Relic's use, and users are not required to use it. They should mention this dependency in their application's `pom.xml` file to pull it directly from Maven Central or build locally as per the instructions above.

## Support

New Relic has open-sourced this project. This project is provided AS-IS WITHOUT WARRANTY OR DEDICATED SUPPORT. Issues and contributions should be reported to the project here on GitHub.

### Community Support

We encourage you to bring your experiences and questions to the [Explorers Hub](https://discuss.newrelic.com) where our community members collaborate on solutions and new ideas.

## Contributing

We encourage your contributions to improve the New Relic Log4j 2 Appender! When you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project. If you have any questions, or to execute our corporate CLA (required if your contribution is on behalf of a company), please drop us an email at opensource@newrelic.com.

### A Note About Vulnerabilities

As noted in our [security policy](../../security/policy), New Relic is committed to the privacy and security of our customers and their data. We believe that coordinated disclosure by security researchers and engaging with the security community are important means to achieve our security goals.

If you believe you have found a security vulnerability in this project or any of New Relic's products or websites, we welcome and greatly appreciate you reporting it to New Relic through [HackerOne](https://hackerone.com/newrelic).

## License

The New Relic Log4j 2 Appender is licensed under the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.

