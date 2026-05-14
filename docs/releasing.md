# Releasing

Maintainers publish the Gradle plugin to the Gradle Plugin Portal and Maven Central.

## Preflight

Choose a final release version, for example `0.1.7`. The Gradle Plugin Portal rejects `SNAPSHOT` versions, and published plugin versions cannot be overwritten.

Run the build and Plugin Portal validation:

```bash
./gradlew build -PreleaseVersion=0.1.7
./gradlew :reference-index-gradle-plugin:publishPlugins --validate-only -PreleaseVersion=0.1.7
```

Before publishing, check:

- `README.md` documents the current Gradle tasks, query schema, and plugin application pattern.
- `SKILL.md` gives agents concise instructions for using `:javaReferenceQuery`.
- `reference-index-gradle-plugin/build.gradle.kts` has the correct Plugin Portal metadata: id, display name, description, tags, website, VCS URL, and feature compatibility.
- The release version is not already published.

## Tag

Tag the exact commit that will be published:

```bash
git tag -a v0.1.7 -m "Release 0.1.7"
git push origin main
git push origin v0.1.7
```

## Publish

Publish to the Gradle Plugin Portal. Use Gradle Portal credentials from `$HOME/.gradle/gradle.properties` or the `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` environment variables.

```bash
./gradlew :reference-index-gradle-plugin:publishPlugins -PreleaseVersion=0.1.7
```

Publish to Maven Central. Use Maven Central credentials from Gradle properties or `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`.

```bash
./gradlew publishAggregationToCentralPortal -PreleaseVersion=0.1.7
```

Release the Maven Central deployment from `https://central.sonatype.com/`.

## Verify

Check Maven Central after the deployment is released:

```bash
curl -I https://repo.maven.apache.org/maven2/io/github/chonghan/reference-index-gradle-plugin/0.1.7/reference-index-gradle-plugin-0.1.7.pom
curl -I https://repo.maven.apache.org/maven2/io/github/chonghan/java-reference-index/io.github.chonghan.java-reference-index.gradle.plugin/0.1.7/io.github.chonghan.java-reference-index.gradle.plugin-0.1.7.pom
```

Check the Plugin Portal page after publishing:

```text
https://plugins.gradle.org/plugin/io.github.chonghan.java-reference-index/0.1.7
```

Create a GitHub release from the pushed tag with concise release notes.

## Credentials

Plugin Portal and Maven Central credentials live in the maintainer's Gradle properties or environment. Signing uses the local `gpg` command and local GPG keyring. Do not store private signing keys in this repository.
