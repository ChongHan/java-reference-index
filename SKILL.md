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

## Querying

`javaReferenceQuery` exposes a DuckDB table named `java_references`. Treat `./gradlew help --task javaReferenceQuery` as the source of truth for the live schema and examples.

Use these patterns after checking the live schema:

- Forward references: filter by `source_path = 'path/to/File.java'`.
- Direct reverse source references: filter by `target_kind = 'source'` and the source target path column from help.
- Binary references: filter by `target_kind = 'binary'`, then use the dependency/owner column and Java type column from help.

## Code review / blast radius checklist

Before editing or reviewing a Java file when impact matters:

1. Query direct reverse references for the changed source path.
2. Query forward references for the changed source path if dependency context matters.
3. Read only the changed file and the candidate dependent files returned by the index.
4. Use `rg` only after the index narrows the search space, or when the question is not about Java references.

## Answering rules

- Report exact `source_project` and `source_path` values for reverse-reference results.
- Say whether the reference is `source`, `binary`, or unresolved when relevant.
- Do not claim transitive impact unless you explicitly queried for it or explain that the result is direct references only.
- If the Gradle task is unavailable or fails, state that and fall back to normal repository inspection.
- For non-Java-reference questions, do not force this tool; use ordinary file reads/searches.
