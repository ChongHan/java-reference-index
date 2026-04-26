package io.github.hanc.javareferenceindex.gradle;

import java.io.File;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public abstract class QueryJavaReferencesTask extends DefaultTask {
    private String sql;

    @Input
    @Optional
    public String getSql() {
        return sql;
    }

    @Option(option = "sql", description = "SQL query to run against the java_references table.")
    public void setSql(String sql) {
        this.sql = sql;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getReferenceIndexFiles();

    @TaskAction
    public void queryJavaReferences() {
        if (sql == null || sql.isBlank()) {
            throw new GradleException("Pass a SQL query with --sql \"select * from java_references\"");
        }

        List<File> csvFiles = getReferenceIndexFiles().getFiles().stream()
            .filter(File::isFile)
            .sorted()
            .toList();
        if (csvFiles.isEmpty()) {
            throw new GradleException("No Java reference index CSV files were found. Run indexJavaReferences first.");
        }

        try (var connection = DriverManager.getConnection("jdbc:duckdb:");
             var statement = connection.createStatement()) {
            statement.execute("CREATE VIEW java_references AS SELECT * FROM read_csv("
                + csvFilesArgument(csvFiles)
                + ", header = true, all_varchar = true, union_by_name = true)");

            boolean hasResultSet = statement.execute(sql);
            if (!hasResultSet) {
                getLogger().lifecycle("Query completed without a result set.");
                return;
            }

            try (ResultSet resultSet = statement.getResultSet()) {
                logResultSet(resultSet);
            }
        } catch (SQLException e) {
            throw new GradleException("Failed to query Java reference index CSV files", e);
        }
    }

    private void logResultSet(ResultSet resultSet) throws SQLException {
        var metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();

        String[] header = new String[columnCount];
        for (int column = 1; column <= columnCount; column++) {
            header[column - 1] = metadata.getColumnLabel(column);
        }
        getLogger().lifecycle(csvRow(header));

        while (resultSet.next()) {
            String[] row = new String[columnCount];
            for (int column = 1; column <= columnCount; column++) {
                row[column - 1] = resultSet.getString(column);
            }
            getLogger().lifecycle(csvRow(row));
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
