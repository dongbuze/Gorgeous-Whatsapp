package Util;

import java.util.UUID;

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

    public static String BytesToHex(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for(int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if(hex.length() < 2){
                sb.append(0);
            }
            sb.append(hex);
        }
        return sb.toString();
    }


    public static byte HexToByte(String inHex){
        return (byte)Integer.parseInt(inHex,16);
    }

    public static byte[] HexToBytes(String inHex) {
        int hexlen = inHex.length();
        byte[] result;
        if (hexlen % 2 == 1){
            //奇数
            hexlen++;
            result = new byte[(hexlen/2)];
            inHex="0"+inHex;
        }else {
            //偶数
            result = new byte[(hexlen/2)];
        }
        int j=0;
        for (int i = 0; i < hexlen; i+=2){
            result[j]= HexToByte(inHex.substring(i,i+2));
            j++;
        }
        return result;
    }


    public static String GenerateIqId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
