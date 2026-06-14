package io.github.chonghan.javareferenceindex.gradle;

import java.io.File;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "The task executes interactive SQL and logs query results instead of producing cached outputs.")
public abstract class QueryJavaReferencesTask extends DefaultTask {
    static final String TABLE_NAME = "java_references";
    static final String SCHEMA = "source_project, source_path, target_origin, target_project, target_path, reference_symbol";
    static final String COLUMN_MEANING =
        "source_project/source_path identify the referencing file; target_origin is source, binary, or unresolved; "
            + "target_project is the target Gradle project path or library coordinate; "
            + "target_path is the referenced source path for source references and empty for binary references; "
            + "reference_symbol is the referenced Java symbol name";
    static final String SOURCE_EXAMPLE_ROW =
        ":app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java,lib.LibraryType";
    static final String BINARY_EXAMPLE_ROW =
        ":app,app/src/main/java/app/App.java,binary,org.agrona:agrona:2.4.1,,org.agrona.DirectBuffer";
    static final String REFERENCES_QUERY =
        "select target_project, target_path, reference_symbol from java_references where source_path = 'app/src/main/java/app/App.java'";
    static final String BLAST_RADIUS_QUERY =
        "select source_project, source_path from java_references where target_path = 'lib/src/main/java/lib/LibraryType.java'";

    @Inject
    protected abstract ProviderFactory getProviders();

    static String taskDescription() {
        return """
            Query Java reference edges with DuckDB SQL.
            Table: %s
            Schema: %s
            Columns: %s.
            Source row: %s
            Binary row: %s
            Use -q for clean query output without Gradle task noise.
            Pass SQL with the Gradle property -Psql.
            Repo-wide query from root: ./gradlew -q :javaReferenceQuery -Psql="select * from java_references limit 20"
            Root query depends on :javaReferenceIndexAll, the root-only aggregate index task.
            Use the leading ':' from root; otherwise Gradle can run every javaReferenceQuery task in root and subprojects.
            What this file references: ./gradlew -q :javaReferenceQuery -Psql="%s"
            Who references this file: ./gradlew -q :javaReferenceQuery -Psql="%s"
            """.formatted(
                TABLE_NAME,
                SCHEMA,
                COLUMN_MEANING,
                SOURCE_EXAMPLE_ROW,
                BINARY_EXAMPLE_ROW,
                REFERENCES_QUERY,
                BLAST_RADIUS_QUERY
            );
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getReferenceIndexFiles();

    @TaskAction
    public void javaReferenceQuery() {
        String querySql = effectiveSql();
        if (querySql == null || querySql.isBlank()) {
            throw new GradleException("Pass a SQL query with -Psql=\"select * from java_references\"");
        }

        List<File> csvFiles = getReferenceIndexFiles().getFiles().stream()
            .filter(File::isFile)
            .sorted()
            .toList();
        loadDuckDbDriver();

        try (var connection = DriverManager.getConnection("jdbc:duckdb:");
             var statement = connection.createStatement()) {
            if (csvFiles.isEmpty()) {
                statement.execute("""
                    CREATE TABLE java_references (
                        source_project VARCHAR,
                        source_path VARCHAR,
                        target_origin VARCHAR,
                        target_project VARCHAR,
                        target_path VARCHAR,
                        reference_symbol VARCHAR
                    )
                    """);
            } else {
                statement.execute("CREATE VIEW java_references AS SELECT * FROM read_csv("
                    + csvFilesArgument(csvFiles)
                    + ", header = true, all_varchar = true, union_by_name = true)");
            }

            boolean hasResultSet = statement.execute(querySql);
            if (!hasResultSet) {
                getLogger().quiet("Query completed without a result set.");
                return;
            }

            try (ResultSet resultSet = statement.getResultSet()) {
                logResultSet(resultSet);
            }
        } catch (SQLException e) {
            throw new GradleException("DuckDB query failed: " + e.getMessage(), e);
        }
    }

    private String effectiveSql() {
        return getProviders().gradleProperty("sql").getOrNull();
    }

    private static void loadDuckDbDriver() {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new GradleException("DuckDB JDBC driver is not available on the plugin classpath", e);
        }
    }

    private void logResultSet(ResultSet resultSet) throws SQLException {
        var metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();

        String[] header = new String[columnCount];
        for (int column = 1; column <= columnCount; column++) {
            header[column - 1] = metadata.getColumnLabel(column);
        }
        getLogger().quiet(csvRow(header));

        while (resultSet.next()) {
            String[] row = new String[columnCount];
            for (int column = 1; column <= columnCount; column++) {
                row[column - 1] = resultSet.getString(column);
            }
            getLogger().quiet(csvRow(row));
        }
    }

    private static String csvFilesArgument(List<File> csvFiles) {
        return "[" + csvFiles.stream()
            .map(File::getAbsolutePath)
            .map(path -> path.replace('\\', '/'))
            .map(QueryJavaReferencesTask::sqlString)
            .reduce((left, right) -> left + ", " + right)
            .orElseThrow() + "]";
    }

    private static String sqlString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String csvRow(String... columns) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csvValue(columns[i]));
        }
        return builder.toString();
    }

    private static String csvValue(String value) {
        if (value == null) {
            return "";
        }
        boolean requiresQuoting = value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0;
        if (!requiresQuoting) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
