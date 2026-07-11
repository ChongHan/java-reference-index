# java-reference-index

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Build a queryable Java reference map for coding agents.

`java-reference-index` is a Gradle plugin that parses Java source with Eclipse JDT, resolves referenced types to source files or binary dependencies, writes CSV indexes, and queries those indexes with DuckDB SQL.

The CSV can also be collapsed into a directed file graph. Coding agents can run standard graph analysis on that graph to identify entrypoints, coordinators, core APIs, and other read-first files in large monorepos.

Use it to answer questions such as:

- What source files does this Java file reference?
- Who directly references this source file?
- Which external library types does this project use?
- What files should a coding agent read first?

## Quick Start

Declare the plugin version in `settings.gradle.kts`:

```kotlin
pluginManagement {
    plugins {
        id("io.github.chonghan.java-reference-index") version "0.1.9"
    }
}
```

Apply it in the root project and in every included project so the root aggregate tasks can depend on each project's index task:

```kotlin
plugins {
    id("io.github.chonghan.java-reference-index")
}
```

With Isolated Projects enabled, apply the plugin in each project's build script or through an isolated convention plugin. Do not use a root `subprojects { ... }` block.

Run repo-wide queries from the root project with the leading `:`:

```bash
./gradlew -q :javaReferenceQuery -Psql="select * from java_references limit 20"
```

The root `:javaReferenceQuery` task depends on `:javaReferenceIndexAll`, so it refreshes the needed CSV files before running SQL.

Show the live task help, schema, and examples:

```bash
./gradlew help --task javaReferenceQuery
```

Refresh CSV files without querying:

```bash
./gradlew :javaReferenceIndexAll
```

Per-project `javaReferenceIndex` tasks index only that project and write files under `build/reference-index/`.

## Query Examples

What does this file reference?

```bash
./gradlew -q :javaReferenceQuery -Psql="select target_origin, target_project, target_path, reference_symbol from java_references where source_path = 'app/src/main/java/app/App.java'"
```

Who directly references this source file?

```bash
./gradlew -q :javaReferenceQuery -Psql="select distinct source_project, source_path from java_references where target_origin = 'source' and target_path = 'lib/src/main/java/lib/LibraryType.java'"
```

Which external types does a project use?

```bash
./gradlew -q :javaReferenceQuery -Psql="select distinct target_project, reference_symbol from java_references where source_project = ':app' and target_origin = 'binary'"
```

Which files reference a symbol by name?

```bash
./gradlew -q :javaReferenceQuery -Psql="select source_project, source_path, target_origin, target_project, target_path from java_references where reference_symbol = 'lib.LibraryType'"
```

## Architecture Discovery

The CSV can be collapsed into a directed file graph:

```text
A.java -> B.java
```

where `A.java` references one or more types declared in `B.java`.

Export production source-file edges:

```bash
./gradlew -q :javaReferenceQuery -Psql="
select distinct
  source_project,
  source_path,
  target_project,
  target_path
from java_references
where target_origin = 'source'
  and target_path <> ''
  and source_path like '%/src/main/java/%'
  and target_path like '%/src/main/java/%'
" > java-reference-edges.csv
```

For monorepos, run graph algorithms on the whole exported edge list, then report results for the selected subproject. A standard choice is [HITS](https://en.wikipedia.org/wiki/HITS_algorithm):

- **Hubs**: files that reference important files; useful entrypoint/coordinator candidates.
- **Authorities**: files referenced by important files; useful core API or shared concept candidates.

Example from Aeron, using the whole repo graph and reporting only `:aeron-driver`.

HITS hubs:

```text
1   aeron-driver/src/main/java/io/aeron/driver/DriverConductor.java
2   aeron-driver/src/main/java/io/aeron/driver/Configuration.java
3   aeron-driver/src/main/java/io/aeron/driver/MediaDriver.java
4   aeron-driver/src/main/java/io/aeron/driver/NetworkPublication.java
5   aeron-driver/src/main/java/io/aeron/driver/media/SendChannelEndpoint.java
6   aeron-driver/src/main/java/io/aeron/driver/PublicationParams.java
7   aeron-driver/src/main/java/io/aeron/driver/SubscriptionParams.java
8   aeron-driver/src/main/java/io/aeron/driver/media/ReceiveChannelEndpoint.java
9   aeron-driver/src/main/java/io/aeron/driver/IpcPublication.java
10  aeron-driver/src/main/java/io/aeron/driver/media/UdpChannel.java
```

HITS authorities:

```text
1   aeron-driver/src/main/java/io/aeron/driver/MediaDriver.java
2   aeron-driver/src/main/java/io/aeron/driver/Configuration.java
3   aeron-driver/src/main/java/io/aeron/driver/ThreadingMode.java
4   aeron-driver/src/main/java/io/aeron/driver/media/UdpChannel.java
5   aeron-driver/src/main/java/io/aeron/driver/DutyCycleTracker.java
6   aeron-driver/src/main/java/io/aeron/driver/status/SystemCounterDescriptor.java
7   aeron-driver/src/main/java/io/aeron/driver/status/SystemCounters.java
8   aeron-driver/src/main/java/io/aeron/driver/media/ReceiveChannelEndpoint.java
9   aeron-driver/src/main/java/io/aeron/driver/ReceiveChannelEndpointSupplier.java
10  aeron-driver/src/main/java/io/aeron/driver/SendChannelEndpointSupplier.java
```

## Table Shape

`javaReferenceQuery` exposes a DuckDB table named `java_references`.

| Column | Description |
|---|---|
| `source_project` | Gradle project path containing the referencing file |
| `source_path` | Java source path relative to the root project |
| `target_origin` | `source`, `binary`, or `unresolved` |
| `target_project` | Target Gradle project path, dependency coordinates/classpath label, or empty |
| `target_path` | Referenced source path for source references; empty for binary and unresolved references |
| `reference_symbol` | Referenced Java symbol name; empty for unresolved references |

Example rows:

```csv
source_project,source_path,target_origin,target_project,target_path,reference_symbol
:app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java,lib.LibraryType
:app,app/src/main/java/app/App.java,binary,org.agrona:agrona:2.4.1,,org.agrona.collections.IntArrayList
```

Rows are source-file to target-type reference rows. Source references to types declared in the same source file are omitted. To build a file graph, collapse rows with `select distinct source_project, source_path, target_project, target_path ...`.

## Gradle Tasks

| Task | Where | Purpose |
|---|---|---|
| `javaReferenceQuery` | Root and applied subprojects | Query CSV indexes with DuckDB SQL. Use root `:javaReferenceQuery` for repo-wide queries. |
| `javaReferenceIndexAll` | Root project only | Build reference indexes for every included project; each must apply the plugin. |
| `javaReferenceIndex` | Each project with the plugin applied | Build CSV indexes for that project only. |

Use `:javaReferenceQuery` from the root. Without the leading `:`, Gradle can run every matching task in the root and subprojects.

## How It Works

1. The Gradle plugin collects Java source roots and resolved compile classpath entries for each source set.
2. The core indexer batch-parses Java source with Eclipse JDT and resolves type bindings.
3. Source references are recorded when the target type is available as source in the current project or a project dependency.
4. Binary references are recorded when the target type resolves to an external dependency or compiled classpath entry.
5. CSV files are written under each project's `build/reference-index/` and loaded into DuckDB by `javaReferenceQuery`.

Source references are preferred over binary references when both are available.

## Behavior Notes

- The index is direct, not transitive.
- Project dependencies are resolved back to source roots when Gradle exposes dependency source sets or artifact outputs on the compile classpath.
- External dependency rows use `group:module:version` coordinates when Gradle provides them; otherwise they use a classpath label.
- Unresolved references have `target_origin` set to `unresolved` and empty `target_project`, `target_path`, and `reference_symbol`.
- `java.*`, `javax.*`, `jdk.*`, `sun.*`, and `com.sun.*` references are ignored.
- Annotation types are indexed like other binary or source references. Generated implementation types that do not exist in source may be unresolved.
- Configure-on-demand builds are supported for root query and aggregate tasks, but every project that should contribute rows must apply the plugin.

## Project Layout

| Subproject | Purpose |
|---|---|
| `reference-index-core` | JDT parser, resolver, and model |
| `reference-index-csv` | CSV serialization |
| `reference-index-gradle-plugin` | Gradle tasks, artifact resolution, and DuckDB query support |

## Development

Requirements:

- JDK 25 (compiled with `--release 21`)
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
