package jni;

public class ProtocolNodeJni {
    public static native byte[] XmlToBytes(String xml);
    public static native String BytesToXml(byte[] data);
}
