import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class P7E2eDatabase {
    private static final String[] SCHEMAS = {
        "test_p7_e2e_conversation", "test_p7_e2e_orchestration", "test_p7_e2e_knowledge"
    };

    public static void main(String[] args) throws Exception {
        boolean clean = args.length > 0 && "clean".equals(args[0]);
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "123456");
             Statement statement = connection.createStatement()) {
            for (String schema : SCHEMAS) {
                if (clean) statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                else statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            }
        }
    }
}
