# java-reference-index

A Gradle plugin that builds queryable reference indices for Java source code. It analyzes your project's source files using the Eclipse JDT compiler, extracts all type references, and writes them to CSV files that can be queried with SQL (via DuckDB).

## What it does

For each source set in your project, the plugin generates a CSV file that maps every source file to the types it references — whether those types live in another source file in the same build, or in a binary dependency pulled from Maven.

```csv
source_project,source_path,target_kind,target_project,target
:app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java
:app,app/src/main/java/app/App.java,binary,org.agrona:agrona:2.4.1,org.agrona.collections.IntArrayList
```

You can then run SQL queries against the generated data to answer questions like:

- What does this file depend on?
- Who references this type? (blast radius)
- Which external libraries does this module pull in?

## Setup

Apply the plugin in your root `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.chonghan.java-reference-index") version "0.1.0-SNAPSHOT"
}
```

The plugin auto-configures itself for any subproject that has the Java plugin applied. No additional configuration is needed.

## Tasks

### `indexJavaReferences`

Parses all Java source files and writes reference data to CSV.

```
./gradlew indexJavaReferences
```

Output files are written to `build/reference-index/` within each subproject:

```
build/reference-index/
  main-references.csv
  test-references.csv
```

Running the task from the root project indexes all subprojects.

### `queryJavaReferences`

Runs a SQL query against the generated CSV files using DuckDB. The CSV data is exposed as a view named `java_references` with columns:

| Column | Description |
|---|---|
| `source_project` | Gradle project path (e.g. `:app`) |
| `source_path` | Path to the source file, relative to the project root |
| `target_kind` | `source`, `binary`, or empty (unresolved) |
| `target_project` | For source refs: Gradle project path. For binary: Maven coordinates (`group:artifact:version`) |
| `target` | For source refs: path to the target file. For binary: fully-qualified class name |

Pass a SQL query with `--sql`:

```
./gradlew queryJavaReferences --sql "SELECT target_project, target FROM java_references WHERE source_path = 'app/src/main/java/app/App.java'"
```

**Find everything that references a type** (blast radius):

```
./gradlew queryJavaReferences --sql "SELECT source_project, source_path FROM java_references WHERE target = 'lib/src/main/java/lib/LibraryType.java'"
```

**Find all external dependencies used by a module:**

```
./gradlew queryJavaReferences --sql "SELECT DISTINCT target_project, target FROM java_references WHERE source_project = ':app' AND target_kind = 'binary'"
```

## How it works

1. The plugin collects source files, classpath entries (with Maven coordinates), and compiler settings for each source set.
2. [Eclipse JDT](https://eclipse.dev/jdt/) parses the source files and resolves type bindings.
3. Each resolved reference is classified as a source reference (points to a `.java` file in the build) or a binary reference (points to a type in a JAR on the classpath).
4. Results are written to CSV, one file per source set.
5. `queryJavaReferences` loads all CSV files into a DuckDB in-memory database and runs your SQL query.

Source references are preferred over binary references: if a type is available as source in the build, it is recorded as a source reference even if the same type also appears on the binary classpath.

## Subprojects

| Subproject | Description |
|---|---|
| `reference-index-core` | JDT-based indexing engine and data model |
| `reference-index-csv` | CSV serialization for index output |
| `reference-index-gradle-plugin` | Gradle plugin, tasks, and DuckDB query integration |

## Requirements

- Java 21
- Gradle (any recent version with the Kotlin DSL)
