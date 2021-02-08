package Util;

public class StringUtil {
    public static StringBuilder StringAppender(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append(str);
        return sb;
    }

    public static String StringAppender(String str, int i) {
        StringBuilder sb = new StringBuilder();
        sb.append(str);
        sb.append(i);
        return sb.toString();
    }

    public static boolean isEmpty(String str) {
        if ((str == null) || str.equals("")) {
            return true;
        }
        return false;
    }
}
