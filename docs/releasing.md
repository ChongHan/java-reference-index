# Releasing

Maintainers publish the Gradle plugin to the Gradle Plugin Portal and Maven Central.

## Preflight

Choose the release version, for example `0.1.1`, then run:

```bash
./gradlew build
./gradlew :reference-index-gradle-plugin:publishPlugins --validate-only -PreleaseVersion=0.1.1
```

## Tag

Tag the exact commit that will be published:

```bash
git tag -a v0.1.1 -m "Release 0.1.1"
git push origin main
git push origin v0.1.1
```

## Publish

Publish to the Gradle Plugin Portal:

```bash
./gradlew :reference-index-gradle-plugin:publishPlugins -PreleaseVersion=0.1.1
```

Publish to Maven Central:

```bash
./gradlew publishAggregationToCentralPortal -PreleaseVersion=0.1.1
```

Release the Maven Central deployment from `https://central.sonatype.com/`.

## Verify

Check Maven Central after the deployment is released:

```bash
curl -I https://repo.maven.apache.org/maven2/io/github/chonghan/reference-index-gradle-plugin/0.1.1/reference-index-gradle-plugin-0.1.1.pom
curl -I https://repo.maven.apache.org/maven2/io/github/chonghan/java-reference-index/io.github.chonghan.java-reference-index.gradle.plugin/0.1.1/io.github.chonghan.java-reference-index.gradle.plugin-0.1.1.pom
```

Create a GitHub release from the pushed tag with concise release notes.

## Credentials

Plugin Portal and Maven Central credentials live in the maintainer's Gradle properties or environment. Signing uses the local `gpg` command and local GPG keyring. Do not store private signing keys in this repository.
