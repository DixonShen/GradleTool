/**
 * Created by z003r98d on 6/15/2017.
 */
public class StringUtil {

    public static boolean isNotBlank(String s) {
        return !s.trim().equals("");
    }

    public static boolean isBlank(String s) {
        return s.trim().equals("");
    }
}
