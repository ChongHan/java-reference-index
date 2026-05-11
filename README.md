# java-reference-index

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Build a queryable map of Java source references for coding agents.

`java-reference-index` is a Gradle plugin that parses Java with Eclipse JDT, resolves references to source files and binary dependencies, writes CSV indexes per source set, and queries them with DuckDB SQL. It helps an agent answer "what does this file depend on?" and "who will be affected if this file changes?" before reading half the repository.

## Quick Start

Apply the plugin in the root `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.chonghan.java-reference-index") version "0.1.1"
}

subprojects {
    apply(plugin = "io.github.chonghan.java-reference-index")
}
```

Apply the plugin to the root project for repo-wide query tasks, and to each Java subproject that should produce an index.

Query from the root project:

```bash
./gradlew -q :javaReferenceQuery --sql "select * from java_references limit 20"
```

`javaReferenceQuery` is the normal entry point. When run from the root project, it automatically depends on `:javaReferenceIndexAll` and builds the needed per-project CSV indexes before running SQL. You do not need to run an index task by hand before querying.

Ask Gradle for the live schema and examples:

```bash
./gradlew help --task javaReferenceQuery
```

If you only want to build or refresh CSV files without running SQL, use the root-only aggregate task:

```bash
./gradlew :javaReferenceIndexAll
```

Per-project `javaReferenceIndex` tasks index only that project and are mostly useful for focused debugging.

## What Agents Can Ask

What does this file reference?

```bash
./gradlew -q :javaReferenceQuery --sql "select target_kind, target_project, target_path, target_type from java_references where source_path = 'app/src/main/java/app/App.java'"
```

Who references this source file?

```bash
./gradlew -q :javaReferenceQuery --sql "select distinct source_project, source_path from java_references where target_path = 'lib/src/main/java/lib/LibraryType.java'"
```

Which external types does a project use?

```bash
./gradlew -q :javaReferenceQuery --sql "select distinct target_project, target_type from java_references where source_project = ':app' and target_kind = 'binary'"
```

## Table Shape

`javaReferenceQuery` exposes a DuckDB table named `java_references`.

| Column | Description |
|---|---|
| `source_project` | Gradle project path containing the referencing file |
| `source_path` | Java source path relative to the root project |
| `target_kind` | `source`, `binary`, or empty when unresolved |
| `target_project` | Target Gradle project path, or dependency coordinates for binaries |
| `target_path` | Referenced source path for source references, or empty for binary/unresolved references |
| `target_type` | Referenced Java type name; distinguishes multiple source types declared in the same target file |

Example rows:

```csv
source_project,source_path,target_kind,target_project,target_path,target_type
:app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java,lib.LibraryType
:app,app/src/main/java/app/App.java,binary,org.agrona:agrona:2.4.1,,org.agrona.collections.IntArrayList
```

## How It Works

1. The plugin gathers Java source files, source roots, compiler settings, and resolved classpath entries for each source set.
2. Eclipse JDT parses each source file and resolves type bindings.
3. The core indexer records source references when the target type exists as source in the build.
4. Otherwise it records binary references to dependency coordinates or compiled classpath entries.
5. CSV files are written under each project's `build/reference-index/`, then loaded into DuckDB by `javaReferenceQuery`.

Source references are preferred over binary references. If a type is available as source, the index points to the source file even if compiled classes for the same type are also on the classpath.

## Project Layout

| Subproject | Purpose |
|---|---|
| `reference-index-core` | JDT parser, resolver, and in-memory model |
| `reference-index-csv` | CSV serialization |
| `reference-index-gradle-plugin` | Gradle task wiring, artifact resolution, and DuckDB query support |

## Development

Requirements:

- Java 21
- Gradle wrapper from this repository

Run the build:

```bash
./gradlew build
```

Show per-source-set indexing timings:

```bash
./gradlew :javaReferenceIndex --info
```

Maintainer release steps are documented in [docs/releasing.md](docs/releasing.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
