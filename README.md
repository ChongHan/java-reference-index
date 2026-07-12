# java-reference-index

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Build a queryable Java reference map for coding agents.

This Gradle plugin parses Java with Eclipse JDT, resolves type references, writes CSV indexes, and queries them with DuckDB SQL. It can answer questions such as:

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

Treat source references as a directed file graph: `A.java -> B.java` means that `A.java` references a type in `B.java`.

- **Out-degree** finds coordinators and entrypoint candidates.
- **In-degree** finds foundational and high-blast-radius files, but often favors utilities.
- **Directed betweenness** finds bridges and dependency choke points.

Export distinct production edges:

```bash
./gradlew -q :javaReferenceQuery -Psql="
select distinct source_project, source_path, target_project, target_path
from java_references
where target_origin = 'source'
  and (source_path like 'src/main/java/%' or source_path like '%/src/main/java/%')
  and (target_path like 'src/main/java/%' or target_path like '%/src/main/java/%')
order by source_project, source_path, target_project, target_path
" > java-reference-edges.csv
```

Install [NetworkX](https://networkx.org/) and save the following as `analyze-reference-graph.py`:

```bash
python3 -m pip install networkx
```

```python
#!/usr/bin/env python3
import argparse
import csv
import networkx as nx

parser = argparse.ArgumentParser()
parser.add_argument("csv_file")
parser.add_argument("--project", help="include only internal edges of this Gradle project")
parser.add_argument("--limit", type=int, default=20)
args = parser.parse_args()

graph = nx.DiGraph()
with open(args.csv_file, newline="") as input_file:
    for row in csv.DictReader(input_file):
        if args.project and not (
            row["source_project"] == args.project == row["target_project"]
        ):
            continue
        graph.add_edge(
            (row["source_project"], row["source_path"]),
            (row["target_project"], row["target_path"]),
        )


def show(name, scores):
    ranked = sorted(scores.items(), key=lambda item: (-item[1], item[0]))
    print(f"\n{name}")
    for (project, path), score in ranked[:args.limit]:
        print(f"{score:.6g}\t{project}\t{path}")


show("out-degree", dict(graph.out_degree()))
show("in-degree", dict(graph.in_degree()))
show("directed betweenness", nx.betweenness_centrality(graph))
```

Analyze one project, or omit `--project` for the whole repository:

```bash
python3 analyze-reference-graph.py java-reference-edges.csv --project :aeron-driver --limit 10
```

NetworkX uses Brandes' algorithm for unweighted betweenness. Exact calculation costs `O(VE)`; degree rankings are cheaper for large graphs. Keep the graph directed—adding reverse edges changes the metric and tends to promote shared utilities.

Results from the pinned fixtures:

| Project and metric | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| Agrona out-degree | `CountersManager` | `ManyToOneRingBuffer` | `OneToOneRingBuffer` | `AbstractMutableDirectBuffer` | `Object2IntHashMap` |
| Agrona in-degree | `BitUtil` | `DirectBuffer` | `MutableDirectBuffer` | `AtomicBuffer` | `UnsafeApi` |
| Agrona betweenness | `DirectBuffer` | `SystemUtil` | `AtomicCounter` | `CountersManager` | `UnsafeBuffer` |
| Aeron Driver out-degree | `DriverConductor` | `MediaDriver` | `PublicationImage` | `NetworkPublication` | `Configuration` |
| Aeron Driver in-degree | `UdpChannel` | `MediaDriver` | `ReceiveChannelEndpoint` | `Configuration` | `FlowControl` |
| Aeron Driver betweenness | `MediaDriver` | `DriverConductor` | `Configuration` | `FlowControl` | `UdpChannel` |
| Disruptor out-degree | `Disruptor` | `RingBuffer` | `BatchEventProcessor` | `EventHandlerGroup` | `AbstractSequencer` |
| Disruptor in-degree | `Sequence` | `SequenceBarrier` | `WaitStrategy` | `AlertException` | `EventProcessor` |
| Disruptor betweenness | `RingBuffer` | `Sequencer` | `BatchEventProcessor` | `AbstractSequencer` | `SequenceBarrier` |

Use out-degree as a read-first view, in-degree as a change-carefully view, and betweenness to find architectural bridges. Exclude samples, benchmarks, generated code, and test-support projects when they are not relevant.

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
