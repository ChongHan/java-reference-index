# java-reference-index

`java-reference-index` is a Gradle plugin that builds a queryable reference index for Java source code.

It parses Java sources with Eclipse JDT, resolves type references against project sources and binary dependencies, writes one CSV per source set, and lets you query the result with DuckDB SQL.

## Use Cases

- Find what a Java file depends on.
- Find which files reference a source file or binary type.
- Estimate the blast radius of changing a Java file.
- Give coding agents a structured codebase map instead of relying only on text search.

## Apply The Plugin

Apply the plugin in the root `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.chonghan.java-reference-index") version "0.1.0"
}
```

The plugin configures every subproject that applies the Gradle `java` plugin.

## Index References

Run from the root project:

```bash
./gradlew indexJavaReferences
```

Each Java source set writes its own CSV file under that subproject:

```text
build/reference-index/
  main-references.csv
  test-references.csv
```

Example rows:

```csv
source_project,source_path,target_kind,target_project,target
:app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java
:app,app/src/main/java/app/App.java,binary,org.agrona:agrona:2.4.1,org.agrona.collections.IntArrayList
```

## Query References

Run SQL against all generated CSV files:

```bash
./gradlew queryJavaReferences --sql "SELECT * FROM java_references LIMIT 20"
```

The task exposes a DuckDB view named `java_references`.

| Column | Description |
|---|---|
| `source_project` | Gradle project path containing the source file, such as `:app` |
| `source_path` | Source file path relative to the root project |
| `target_kind` | `source`, `binary`, or empty when unresolved |
| `target_project` | Source target project path, or Maven coordinates for binary targets |
| `target` | Source target path, binary fully-qualified type name, or empty when unresolved |

Find what one file references:

```bash
./gradlew queryJavaReferences --sql "SELECT target_kind, target_project, target FROM java_references WHERE source_path = 'app/src/main/java/app/App.java'"
```

Find files affected by changing a source file:

```bash
./gradlew queryJavaReferences --sql "SELECT source_project, source_path FROM java_references WHERE target = 'lib/src/main/java/lib/LibraryType.java'"
```

Find external types used by a project:

```bash
./gradlew queryJavaReferences --sql "SELECT DISTINCT target_project, target FROM java_references WHERE source_project = ':app' AND target_kind = 'binary'"
```

## How It Works

1. The plugin gathers Java source files, source roots, compiler settings, and resolved classpath entries for each source set.
2. Eclipse JDT parses each source file and resolves type bindings.
3. The core indexer classifies each resolved type as a source reference or binary reference.
4. The CSV module writes the in-memory index as reference rows.
5. `queryJavaReferences` loads all CSV files into DuckDB and executes the supplied SQL.

Source references are preferred over binary references. If a type is available as source in the build, it is recorded as a source file even if compiled classes for the same type also exist on the classpath.

## Project Layout

| Subproject | Purpose |
|---|---|
| `reference-index-core` | JDT-based parser, resolver, and in-memory model |
| `reference-index-csv` | CSV serialization for the core index output |
| `reference-index-gradle-plugin` | Gradle tasks, source set wiring, artifact resolution, and DuckDB query support |

## Development

Requirements:

- Java 21
- Gradle wrapper from this repository

Run the main test suite:

```bash
./gradlew :reference-index-core:test :reference-index-csv:test :reference-index-gradle-plugin:test
```

Validate Plugin Portal metadata without uploading:

```bash
./gradlew :reference-index-gradle-plugin:publishPlugins --validate-only -PreleaseVersion=0.1.0
```

## Publishing

The plugin is configured for the Gradle Plugin Portal with a shaded plugin artifact, so consumers only need the plugin id.

Set Plugin Portal credentials through environment variables:

```bash
GRADLE_PUBLISH_KEY=... GRADLE_PUBLISH_SECRET=... \
./gradlew :reference-index-gradle-plugin:publishPlugins -PreleaseVersion=0.1.0
```

Local builds default to `0.1.0-SNAPSHOT`. Release builds should pass `-PreleaseVersion=<version>`.
