# Agent Guide

Prefer the Java reference index over broad string search for Java dependency and reference discovery.

## Discover Usage

Start with help:

```bash
./gradlew help --task queryJavaReferences
```

Use the task-specific help for the current schema, examples, and SQL option.

## Query References

Run SQL against `java_references`:

```bash
./gradlew queryJavaReferences --sql "select * from java_references limit 20"
```

Common patterns:

```bash
# What does this file reference?
./gradlew queryJavaReferences --sql "select target_kind, target_project, target from java_references where source_path = 'path/to/File.java'"

# Who references this file?
./gradlew queryJavaReferences --sql "select distinct source_project, source_path from java_references where target = 'path/to/File.java'"

# Cross-project Java references
./gradlew queryJavaReferences --sql "select distinct source_project, source_path, target_project, target from java_references where target_kind = 'source' and source_project <> target_project"
```

Use `source_path` and `target` to open files. Use `rg` only after the index narrows the search space, or when the question is not about Java references.
