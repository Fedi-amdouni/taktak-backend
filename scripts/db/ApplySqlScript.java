import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal PostgreSQL migration runner for machines where psql is unavailable.
 * Usage: java ApplySqlScript <dotenv-file> <sql-file>
 */
public final class ApplySqlScript {
    private ApplySqlScript() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ApplySqlScript <dotenv-file> <sql-file>");
        }

        Map<String, String> environment = readDotenv(Path.of(args[0]));
        String jdbcUrl = require(environment, "SPRING_DATASOURCE_URL");
        String username = require(environment, "SPRING_DATASOURCE_USERNAME");
        String password = require(environment, "SPRING_DATASOURCE_PASSWORD");
        String sql = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);

        Class.forName("org.postgresql.Driver");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);

            try (ResultSet result = statement.executeQuery("""
                    SELECT count(*)
                    FROM products product
                    JOIN cafes cafe ON cafe.id = product.cafe_id
                    WHERE cafe.slug = 'monastir-lounge'
                    """)) {
                result.next();
                System.out.println("Migration appliquée sur " + safeDatabaseTarget(jdbcUrl)
                        + " ; produits Monastir Lounge=" + result.getLong(1));
            }
        }
    }

    private static Map<String, String> readDotenv(Path path) throws Exception {
        Map<String, String> values = new HashMap<>();
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
            }
            values.put(key, value);
        }
        return values;
    }

    private static String require(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variable absente: " + key);
        }
        return value;
    }

    private static String safeDatabaseTarget(String jdbcUrl) {
        try {
            URI uri = URI.create(jdbcUrl.replaceFirst("^jdbc:", ""));
            return uri.getHost() + uri.getPath();
        } catch (Exception ignored) {
            return "PostgreSQL";
        }
    }
}
