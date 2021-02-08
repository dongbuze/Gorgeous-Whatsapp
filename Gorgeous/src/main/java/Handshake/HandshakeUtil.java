package Handshake;

public class HandshakeUtil {
    public static byte[] GenerateDataHead(int i) {
        byte[] bArr = new byte[3];
        bArr[2] = (byte) i;
        bArr[1] = (byte) (i >> 8);
        bArr[0] = (byte) (i >> 16);
        return bArr;
    }
    public static int BodyBytesToLen(byte[] bArr) {
        return (bArr[2] & 255) | ((bArr[0] & 255) << 16) | ((bArr[1] & 255) << 8);
    }
}
