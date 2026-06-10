# java-reference-index

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Build a queryable Java reference map for coding agents.

`java-reference-index` is a Gradle plugin that parses Java source with Eclipse JDT, resolves referenced types to source files or binary dependencies, writes per-source-set CSV indexes, and queries those indexes with DuckDB SQL.

The main use case is agent navigation:

- "What source files does this Java file reference?"
- "Who directly references this source file?"
- "Which external library types does this file or project use?"
- "What should I read before changing this file?"

## Quick Start

Apply the plugin to the root project and to each Java subproject that should produce an index:

```kotlin
plugins {
    id("io.github.chonghan.java-reference-index") version "0.1.7"
}

subprojects {
    apply(plugin = "io.github.chonghan.java-reference-index")
}
```

Run repo-wide queries from the root project with the leading `:`:

```bash
./gradlew -q :javaReferenceQuery --sql "select * from java_references limit 20"
```

For repeated ad-hoc queries with Gradle's configuration cache, pass SQL as a Gradle property so changing only the SQL text can reuse the cached task graph:

```bash
./gradlew -q :javaReferenceQuery -Psql="select * from java_references limit 20"
```

`javaReferenceQuery` is the normal entry point. The root task depends on `:javaReferenceIndexAll`, so it builds the needed per-project CSV indexes before running SQL. You do not need to run an index task by hand before querying.

Ask Gradle for the live schema and examples:

```bash
./gradlew help --task javaReferenceQuery
```

If you only want to refresh CSV files without running SQL, use the root-only aggregate task:

```bash
./gradlew :javaReferenceIndexAll
```

Per-project `javaReferenceIndex` tasks index only that project. They are mostly useful for focused debugging or for inspecting a single project's generated CSV files under `build/reference-index/`.

## Query Examples

What does this file reference?

```bash
./gradlew -q :javaReferenceQuery --sql "select target_kind, target_project, target_path, target_type from java_references where source_path = 'app/src/main/java/app/App.java'"
```

Who directly references this source file?

```bash
./gradlew -q :javaReferenceQuery --sql "select distinct source_project, source_path from java_references where target_kind = 'source' and target_path = 'lib/src/main/java/lib/LibraryType.java'"
```

Which external types does a project use?

```bash
./gradlew -q :javaReferenceQuery --sql "select distinct target_project, target_type from java_references where source_project = ':app' and target_kind = 'binary'"
```

Which files reference a type by name?

```bash
./gradlew -q :javaReferenceQuery --sql "select source_project, source_path, target_kind, target_project, target_path from java_references where target_type = 'lib.LibraryType'"
```

## Table Shape

`javaReferenceQuery` exposes a DuckDB table named `java_references`.

| Column | Description |
|---|---|
| `source_project` | Gradle project path containing the referencing file |
| `source_path` | Java source path relative to the root project |
| `target_kind` | `source`, `binary`, or empty when unresolved |
| `target_project` | Target Gradle project path, or dependency coordinates for binary references |
| `target_path` | Referenced source path for source references; empty for binary and unresolved references |
| `target_type` | Referenced Java type name |

Example rows:

```csv
source_project,source_path,target_kind,target_project,target_path,target_type
:app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java,lib.LibraryType
:app,app/src/main/java/app/App.java,binary,org.agrona:agrona:2.4.1,,org.agrona.collections.IntArrayList
```

Rows are file-level reference edges. Source references to types declared in the same source file are omitted from CSV output because the caller already has that context when reading the file.

## Gradle Tasks

| Task | Where | Purpose |
|---|---|---|
| `javaReferenceQuery` | Root and applied subprojects | Query CSV indexes with DuckDB SQL. The root `:javaReferenceQuery` is the repo-wide entry point. |
| `javaReferenceIndexAll` | Root project only | Aggregate task that builds reference indexes for all projects with the plugin applied. |
| `javaReferenceIndex` | Each project with the plugin applied | Builds CSV indexes for that project only. |

Use `:javaReferenceQuery` from the root. Without the leading `:`, Gradle can run every matching `javaReferenceQuery` task in the root and subprojects.

## How It Works

1. The Gradle plugin gathers Java source files, source roots, compiler settings, and resolved compile classpath entries for each source set.
2. The core indexer batch-parses each source set with Eclipse JDT and resolves type bindings.
3. Source references are recorded when the target type is available as source in the current project or a project dependency.
4. Binary references are recorded when a type resolves to an external dependency or compiled classpath entry.
5. CSV files are written under each project's `build/reference-index/`, then loaded into DuckDB by `javaReferenceQuery`.

Source references are preferred over binary references. If a type is available as source, the index points to the source file even if compiled classes for the same type are also on the classpath.

## Behavior Notes

- The index is direct, not transitive. Query reverse references again if you need to walk multiple hops.
- Project dependencies are resolved back to source roots when Gradle exposes the dependency source set or artifact output on the compile classpath.
- External dependency rows use `group:module:version` coordinates when Gradle provides them.
- Unresolved references are represented with an empty `target_kind`, `target_project`, `target_path`, and `target_type`.
- JDK and common internal platform references are intentionally filtered when they are not useful source navigation targets.
- Annotation types are indexed like other binary or source references. Generated implementation types that do not exist in source may be unresolved.
- Configure-on-demand builds are supported for the root query and aggregate tasks, but every project that should contribute rows must apply the plugin.

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
