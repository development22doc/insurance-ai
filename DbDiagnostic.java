import java.sql.*;

public class DbDiagnostic {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://100.114.133.69:30432/claimassist_claims?serverTimezone=UTC";
        String user = "claimassist";
        String password = "sLZW4D2OBI09bp4qXQOj8NX5VFgS45kI";

        Class.forName("org.postgresql.Driver");
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("TimeZone", "UTC");
        Connection conn = DriverManager.getConnection(url, props);

        System.out.println("=== QUERY 1: Current Database Identity ===");
        executeQuery(conn, "SELECT current_database(), current_user, current_schema();");

        System.out.println("\n=== QUERY 2: Flyway Schema History (ALL) ===");
        executeQuery(conn, "SELECT installed_rank, version, description, type, script, checksum, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;");

        System.out.println("\n=== QUERY 3: Flyway V13 Details ===");
        executeQuery(conn, "SELECT installed_rank, version, description, type, script, checksum, installed_on, success FROM flyway_schema_history WHERE version = '13' OR version LIKE '%13%';");

        System.out.println("\n=== QUERY 4: Check outbox_events Columns ===");
        executeQuery(conn, "SELECT table_schema, table_name, column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'outbox_events' ORDER BY ordinal_position;");

        System.out.println("\n=== QUERY 5: Check request_id Column Specifically ===");
        executeQuery(conn, "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'outbox_events' AND column_name = 'request_id';");

        System.out.println("\n=== QUERY 6: Check if multiple outbox_events tables ===");
        executeQuery(conn, "SELECT table_schema, table_name FROM information_schema.tables WHERE table_name = 'outbox_events';");

        System.out.println("\n=== QUERY 7: Check outbox_events indexes ===");
        executeQuery(conn, "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'outbox_events' ORDER BY indexname;");

        System.out.println("\n=== QUERY 8: List all tables in public schema ===");
        executeQuery(conn, "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;");

        System.out.println("\n=== QUERY 9: List all schemas ===");
        executeQuery(conn, "SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT LIKE 'pg_%' ORDER BY schema_name;");

        System.out.println("\n=== QUERY 10: Search for 'outbox' in all schemas ===");
        executeQuery(conn, "SELECT table_schema, table_name FROM information_schema.tables WHERE table_name LIKE '%outbox%' OR table_name LIKE '%flyway%' ORDER BY table_schema, table_name;");
    }

    static void executeQuery(Connection conn, String sql) throws Exception {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            for (int i = 1; i <= cols; i++) {
                System.out.print(meta.getColumnName(i));
                if (i < cols) System.out.print(" | ");
            }
            System.out.println();

            for (int i = 1; i <= cols; i++) {
                System.out.print("-".repeat(Math.min(20, meta.getColumnName(i).length())));
                if (i < cols) System.out.print("-+-");
            }
            System.out.println();

            while (rs.next()) {
                for (int i = 1; i <= cols; i++) {
                    Object val = rs.getObject(i);
                    System.out.print(val != null ? val.toString() : "NULL");
                    if (i < cols) System.out.print(" | ");
                }
                System.out.println();
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }
}

