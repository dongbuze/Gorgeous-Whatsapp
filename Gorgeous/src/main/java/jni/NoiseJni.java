package jni;

public class NoiseJni {
    public static native long CreateInstance();
    public static native void DestroyInstance(long instance);

    public static native int GetAction(long instance);
    public static native int StartHandshakeXX(long instance, byte[] privateKey, byte[] publicKey);
    public static native int StartHandshakeIK(long instance, byte[] privateKey, byte[] publicKey, byte[] serverPublicKey);

    public static native byte[] Decrypt(long instance, byte[] data);
    public static native byte[] Encrypt(long instance, byte[] data);

    public static native byte[] WriteMessage(long instance, byte[] payload);
    public static native byte[] ReadMessage(long instance, byte[] message);

    public static native byte[] GetServerPublicKey(long instance);

    public static native int Split(long instance);

    public static native byte[] InitData(byte[] routeInfo);
}
