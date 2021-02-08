package ProtocolTree;

public class StanzaAttribute {
    public byte type_;
    public String key_;
    public String value_;

    public StanzaAttribute(String key, String value) {
        key_ = key;
        value_ = value;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder ("KeyValue{key='");
        sb.append(key_).append('\'').append(", value='");
        sb.append(value_).append('\'').append(", type='");
        sb.append((int) type_);
        sb.append('\'');
        sb.append('}');
        return sb.toString();
    }

}
