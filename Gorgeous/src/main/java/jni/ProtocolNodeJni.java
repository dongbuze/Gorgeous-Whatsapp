package jni;

import ProtocolTree.ProtocolTreeNode;

public class ProtocolNodeJni {
    public static native ProtocolTreeNode Decode(byte[] data);
    public static native byte[] Encode(ProtocolTreeNode node);
}
