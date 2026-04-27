# Changelog

## 0.1.0

Initial public release of Java Reference Index.

### Features

- Gradle plugin: `io.github.chonghan.java-reference-index`
- Indexes Java source references using Eclipse JDT.
- Resolves references to project source files and binary dependency types.
- Writes per-source-set CSV files under `build/reference-index`.
- Adds `queryJavaReferences` for DuckDB SQL queries over reference data.
- Supports multi-project Gradle builds.
- Publishes a shaded plugin artifact to the Gradle Plugin Portal and Maven Central.
