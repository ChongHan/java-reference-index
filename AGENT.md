# Agent Guide

Prefer the Java reference index over broad string search for Java dependency, reverse-reference, and blast-radius questions.

## Java References

Discover usage, schema, and examples from the task itself:

```bash
./gradlew help --task queryJavaReferences
```

For repo-wide queries from the root project, use the leading `:`:

```bash
./gradlew :queryJavaReferences --sql "select * from java_references limit 20"
```

Without `:`, Gradle can run every task named `queryJavaReferences` in root and subprojects. Use `rg` after the index narrows the search space, or when the question is not about Java references.

## Code Review

For Java code reviews, use `:queryJavaReferences` when reverse references or blast radius matter before falling back to broad `rg`.
