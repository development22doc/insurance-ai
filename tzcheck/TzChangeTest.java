import java.util.TimeZone;
import java.time.ZoneId;

public class TzChangeTest {
    public static void main(String[] args) {
        System.out.println("Before: TimeZone.getDefault() = " + TimeZone.getDefault().getID());
        System.out.println("Before: ZoneId.systemDefault() = " + ZoneId.systemDefault().getId());

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

        System.out.println("After TimeZone.setDefault(...): TimeZone.getDefault() = " + TimeZone.getDefault().getID());
        System.out.println("After TimeZone.setDefault(...): ZoneId.systemDefault() = " + ZoneId.systemDefault().getId());
    }
}
