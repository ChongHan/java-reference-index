---
name: java-reference-index
description: Use when working in a Gradle Java repository that has the io.github.chonghan.java-reference-index plugin configured. Prefer this skill for Java dependency, reverse-reference, blast-radius, impact-analysis, code-review, and "who uses this?" questions.
version: 0.1.0
license: Apache-2.0
compatibility: Any coding agent that can read Markdown instructions or agentskills.io SKILL.md-style skills. No setup is needed beyond the repository already having the Gradle plugin configured.
metadata:
  tags: java gradle references static-analysis code-review blast-radius dependency
  agentskills_spec: "1.0"
---

# Java Reference Index

Use the Java Reference Index Gradle task before broad text search when the user asks about Java references, dependencies, reverse dependencies, or blast radius.

## First check

When you need schema, examples, or confirmation that the task exists, run:

```bash
./gradlew help --task javaReferenceQuery
```

For repo-wide queries from the root project, use:

```bash
./gradlew -q :javaReferenceQuery --sql "select * from java_references limit 20"
```

## Table

`javaReferenceQuery` exposes a DuckDB table named `java_references`.

Columns:

- `source_project`: Gradle project path containing the referencing file
- `source_path`: Java source path relative to the root project
- `target_kind`: `source`, `binary`, or empty when unresolved
- `target_project`: target Gradle project path, dependency coordinates, or compiled classpath entry
- `target_path`: referenced source path for source references, or empty for binary/unresolved references
- `target_type`: referenced Java type name; distinguishes multiple source types declared in the same target file

## Recipes

### What does this file reference?

```bash
./gradlew -q :javaReferenceQuery --sql "
select distinct target_kind, target_project, target_path, target_type
from java_references
where source_path = 'path/to/File.java'
order by target_kind, target_project, target_path, target_type
"
```

### Who directly references this source file?

```bash
./gradlew -q :javaReferenceQuery --sql "
select distinct source_project, source_path
from java_references
where target_kind = 'source'
  and target_path = 'path/to/Target.java'
order by source_project, source_path
"
```

### Which external/binary types does a project use?

```bash
./gradlew -q :javaReferenceQuery --sql "
select distinct target_project, target_type
from java_references
where source_project = ':project-name'
  and target_kind = 'binary'
order by target_project, target_type
"
```

### Which source files reference a binary dependency or type?

```bash
./gradlew -q :javaReferenceQuery --sql "
select distinct source_project, source_path, target_project, target_type
from java_references
where target_kind = 'binary'
  and (target_project like 'group:artifact:%' or target_type = 'fully.qualified.TypeName')
order by source_project, source_path, target_project, target_type
"
```

### Code review / blast radius checklist

Before editing or reviewing a Java file when impact matters:

1. Query direct reverse references with `target_path = '<changed source path>'`.
2. Query forward references with `source_path = '<changed source path>'` if dependency context matters.
3. Read only the changed file and the candidate dependent files returned by the index.
4. Use `rg` only after the index narrows the search space, or when the question is not about Java references.

## Answering rules

- Report exact `source_project` and `source_path` values for reverse-reference results.
- Say whether the reference is `source`, `binary`, or unresolved when relevant.
- Do not claim transitive impact unless you explicitly queried for it or explain that the result is direct references only.
- If the Gradle task is unavailable or fails, state that and fall back to normal repository inspection.
- For non-Java-reference questions, do not force this tool; use ordinary file reads/searches.
