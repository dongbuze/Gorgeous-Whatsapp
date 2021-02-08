package Handshake;

public class HandshakeConfig {
    public static final String[] s_server =  {"e1.whatsapp.net",  "e2.whatsapp.net",  "e3.whatsapp.net",
            "e4.whatsapp.net",  "e5.whatsapp.net",  "e6.whatsapp.net",
            "e7.whatsapp.net",  "e8.whatsapp.net",  "e9.whatsapp.net",
            "e10.whatsapp.net", "e11.whatsapp.net", "e12.whatsapp.net",
            "e13.whatsapp.net", "e14.whatsapp.net", "e15.whatsapp.net",
            "e16.whatsapp.net"};

    public static final byte[] PUBLIC_KEY = new byte[]{20, 35, 117, 87, 77, 10, 88, 113, 102, -86, -25, 30, -66, 81, 100, 55, -60, -94, -117, 115, -29, 105, 92, 108, -31, -9, -7, 84, 93, -88, -18, 107};
    public static int WRITE_MESSAGE = 16641;
    public static int READ_MESSAGE = 16642;
    public static int FAILED = 16643;
    public static int SPLIT = 16644;
    public static int COMPLETE = 16645;
}
