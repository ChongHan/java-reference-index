---
name: java-reference-index
description: Use in Gradle Java repositories with the io.github.chonghan.java-reference-index plugin. Prefer it for finding referenced source file locations, reverse references, direct blast radius, external type usage, and Java dependency context before broad text search.
version: 0.1.8
license: Apache-2.0
compatibility: Any coding agent that can run Gradle commands and read Markdown instructions.
metadata:
  tags: java gradle references source-location reverse-reference blast-radius static-analysis
  agentskills_spec: "1.0"
---

# Java Reference Index

Confirm the task and live schema when needed:

```bash
./gradlew help --task javaReferenceQuery
```

Run repo-wide SQL from the root project with the leading `:`. Pass SQL only through the Gradle property `-Psql`:

```bash
./gradlew -q :javaReferenceQuery -Psql="select * from java_references limit 20"
```

The root `:javaReferenceQuery` task builds required indexes automatically through `:javaReferenceIndexAll`. Do not ask the user to run indexing separately unless you are debugging index generation.

Use `java_references` columns:

- `source_project`, `source_path`: referencing project and file
- `target_origin`: `source`, `binary`, or `unresolved`
- `target_project`: target Gradle project path or binary dependency coordinates
- `target_path`: referenced source path for `source` rows; empty otherwise
- `target_type`: referenced Java type name

Use these queries first.

Find source files referenced by a file:

```sql
select target_project, target_path, target_type
from java_references
where source_path = 'path/to/File.java'
  and target_origin = 'source'
order by target_project, target_path, target_type
```

Find files that directly reference a source file:

```sql
select distinct source_project, source_path
from java_references
where target_origin = 'source'
  and target_path = 'path/to/Target.java'
order by source_project, source_path
```

Find binary dependencies or external types used by a file:

```sql
select distinct target_project, target_type
from java_references
where source_path = 'path/to/File.java'
  and target_origin = 'binary'
order by target_project, target_type
```

Find references to a known type:

```sql
select source_project, source_path, target_origin, target_project, target_path
from java_references
where target_type = 'com.example.TypeName'
order by source_project, source_path
```

Rules:

- Use `:javaReferenceQuery` before broad `rg` searches for Java source-reference questions.
- Report exact `source_project`, `source_path`, `target_project`, `target_path`, and `target_type` values that answer the question.
- Treat results as direct references only. Do not claim transitive impact unless you run additional queries.
- Same-file source references are omitted from CSV output; read the file for inner classes and local declarations.
- If the Gradle task is unavailable or fails, say so briefly and fall back to normal repository inspection.
- Do not use this skill for non-Java-reference questions.
