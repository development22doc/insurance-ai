import java.sql.*;
public class AddColumn {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://100.114.133.69:30432/claimassist_claims?sslMode=disable&useSSL=false";
        String user = "claimassist";
        String pass = "sLZW4D2OBI09bp4qXQOj8NX5VFgS45kI";
        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(url, user, pass);
        Statement stmt = conn.createStatement();
        stmt.execute("ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS request_id VARCHAR(255)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_outbox_request_id ON outbox_events (request_id)");
        ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'outbox_events' ORDER BY ordinal_position");
        while (rs.next()) System.out.println(rs.getString(1));
        conn.close();
    }
}