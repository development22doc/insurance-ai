import java.util.TimeZone;
import java.time.ZoneId;

public class TzCheck {
    public static void main(String[] args) {
        System.out.println("TZ default: " + TimeZone.getDefault().getID());
        System.out.println("Resolved Asia/Kolkata -> " + TimeZone.getTimeZone("Asia/Kolkata").getID());
        System.out.println("ZoneId.systemDefault: " + ZoneId.systemDefault().getId());
        System.out.println("ZoneId.of(Asia/Kolkata): " + ZoneId.of("Asia/Kolkata").getId());
        TimeZone tz = TimeZone.getTimeZone("Asia/Kolkata");
        System.out.println("TZ raw toString: " + tz);
    }
}
