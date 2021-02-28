package ProtocolTree;
import java.io.UnsupportedEncodingException;
import java.util.*;

public class ProtocolTreeNode {
    private String tag_;
    private byte[] data_;
    private LinkedList<StanzaAttribute> attributes_;
    private LinkedList<ProtocolTreeNode> children_;
    private Object customParams_;

    public String IqId() {
        for (StanzaAttribute attribute :  attributes_) {
            if (attribute.key_.equals("id")) {
                return attribute.value_;
            }
        }
        return "";
    }

    public Object GetCustomParams() {
        return customParams_;
    }

    public  void SetCustomParams(Object customParams) {
        customParams_ = customParams;
    }

    public byte[] GetData() {
        return data_;
    }

    public String GetTag() {
        return tag_;
    }

    public LinkedList<ProtocolTreeNode> GetChildren() {
        return children_;
    }

    public LinkedList<ProtocolTreeNode> GetChildren(String name) {
        LinkedList<ProtocolTreeNode> results = new LinkedList<>();
        if (null == children_) {
            return  results;
        }
        for (ProtocolTreeNode child : children_) {
            if (child.GetTag().equals(name)) {
                results.add(child);
            }
        }
        return  results;
    }

    //@keep
    public int GetAttributesCount() {
        if (attributes_ != null) {
            return attributes_.size();
        }
        return 0;
    }
    //@keep
    public StanzaAttribute GetAttribute(int pos) {
        return attributes_.get(pos);
    }
    //@keep
    public int GetChildrenCount() {
        if (children_ != null) {
            return children_.size();
        }
        return 0;
    }
    //@keep
    public ProtocolTreeNode GetChild(int pos) {
        return children_.get(pos);
    }

    public ProtocolTreeNode GetChild(String name) {
        for (ProtocolTreeNode child : children_) {
            if (child.GetTag().equals(name)) {
                return child;
            }
        }
        return  null;
    }

    public static String BytesToString(byte[] bArr) {
        if (bArr == null) {
            return null;
        }
        try {
            return new String(bArr, "UTF-8");
        } catch (UnsupportedEncodingException unused) {
            return null;
        }
    }

    public ProtocolTreeNode(String tag, StanzaAttribute[] attributes, byte[] data) {
        this.tag_ = tag;
        this.attributes_ = new LinkedList<>();
        if (null != attributes) {
            for (int i=0; i< attributes.length;i++) {
                this.attributes_.add(attributes[i]);
            }
        }
        this.data_ = data;
    }


    public ProtocolTreeNode(String tag, StanzaAttribute[] attributes) {
        this(tag, attributes, null);
    }

    public ProtocolTreeNode(String tag) {
        this(tag,null);
    }

    public final StanzaAttribute GetAttribute(String key) {
        if (attributes_ == null || attributes_.isEmpty()) {
            return null;
        }
        for (StanzaAttribute attribute : attributes_) {
            if (key.equals(attribute.key_)) {
                return attribute;
            }
        }
        return null;
    }

    public final String GetAttributeValue(String key) {
        StanzaAttribute attribute = GetAttribute(key);
        if (null == attribute) {
            return "";
        }
        return attribute.value_;
    }

    public LinkedList<StanzaAttribute> GetAttributes() {
       return attributes_;
    }

    public void SetData(byte[] data) {
        data_ = data;
    }

    public void SetData(byte[] data, int offset, int len) {
        data_ = new byte[len];
        System.arraycopy(data, offset, data_, 0, len);
    }

    public String toString() {
            StringBuilder sb = new StringBuilder("<");
            sb.append(tag_);
            if (attributes_ != null) {
                for (StanzaAttribute attribute: attributes_) {
                    sb.append(" ");
                    sb.append(attribute.key_);
                    sb.append("='");
                    sb.append(attribute.value_);
                    sb.append("'");
                }
            }
            if (data_ == null && children_ == null) {
                sb.append("/>");
            } else {
                sb.append(">");
               if (children_ != null) {
                   for (ProtocolTreeNode child : children_)
                        sb.append(child.toString());
                }
                if (data_ != null) {
                    sb.append(Base64.getEncoder().encodeToString(data_));
                }
                sb.append("</");
                sb.append(tag_);
                sb.append(">");
            }
            return sb.toString();

        }

    public void AddChild(ProtocolTreeNode child) {
        if (null == children_) {
            children_ = new LinkedList<>();
        }
        children_.add(child);
    }

    public void AddAttribute(StanzaAttribute attribute) {
        if (attributes_ == null) {
            attributes_ = new LinkedList<>();
        }
        attributes_.add(attribute);
    }
}
