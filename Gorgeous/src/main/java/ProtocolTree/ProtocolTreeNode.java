package ProtocolTree;

import org.dom4j.*;
import org.dom4j.io.SAXReader;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class ProtocolTreeNode {
    private String tag_;
    private byte[] data_;
    private StanzaAttribute[] attributes_;
    private ProtocolTreeNode[] children_;


    public ProtocolTreeNode(String tag, StanzaAttribute[] attributes, ProtocolTreeNode[] children, byte[] data) {
        this.tag_ = tag;
        this.attributes_ = attributes;
        this.children_ = children;
        this.data_ = data;
    }

    public String IqId() {
        for (int i=0; i< attributes_.length;i++) {
            if (attributes_[i].key_.equals("id")) {
                return attributes_[i].value_;
            }
        }
        return "";
    }

    public byte[] GetData() {
        return data_;
    }

    public String GetTag() {
        return tag_;
    }

    public ProtocolTreeNode[] GetChildren() {
        return children_;
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
        this(tag, attributes, null, data);
    }

    public ProtocolTreeNode(String tag, StanzaAttribute[] attributes, String data) {
        this(tag, attributes, null, data != null ? data.getBytes() : null);
    }


    public ProtocolTreeNode(String tag) {
        this(tag, null ,null, null);
    }


    public ProtocolTreeNode(String tag, StanzaAttribute[] attributes) {
        this(tag, attributes ,null, null);
    }


    public final StanzaAttribute GetAttribute(String key) {
        if (attributes_ == null || (attributes_.length) <= 0) {
            return null;
        }
        for (StanzaAttribute attribute : attributes_) {
            if (key.equals(attribute.key_)) {
                return attribute;
            }
        }
        return null;
    }

    public StanzaAttribute[] GetAttributes() {
       return attributes_;
    }


    static  ProtocolTreeNode InnerParseElement(Element element){
        List<Attribute> attributes = element.attributes();
        StanzaAttribute[] stanzaAttributes = new StanzaAttribute[attributes.size()];
        for (int i=0; i< attributes.size() ;i++) {
            stanzaAttributes[i] = new StanzaAttribute( attributes.get(i).getName(),attributes.get(i).getValue());
        }
        byte[] data = null;
        String elementContent = element.getTextTrim();
        if (!Util.StringUtil.isEmpty(elementContent)) {
            data = Base64.getDecoder().decode(elementContent);
        }

        List<Element> childrenElements = element.elements();
        ProtocolTreeNode[] childrenProtocolNode = new ProtocolTreeNode[childrenElements.size()];
        for (int i=0;i<childrenElements.size();i++){
            childrenProtocolNode[i] = InnerParseElement(childrenElements.get(i));
        }


        return new ProtocolTreeNode(element.getName(), stanzaAttributes, childrenProtocolNode, data);
    }

    public static ProtocolTreeNode FromXml(String xml){
        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = document.getRootElement();
            return InnerParseElement(root);
        }
        catch (Exception e){

        }
        return null;
    }

    static void  InnerToString(ProtocolTreeNode node, Branch parent) {
        Element element = parent.addElement(node.tag_);
        if (node.data_ != null) {
            element.setText(Base64.getEncoder().encodeToString(node.data_));
        }
        if (node.attributes_ != null) {
            for (int i=0; i< node.attributes_.length; i++) {
                element.addAttribute(node.attributes_[i].key_, node.attributes_[i].value_);
            }
        }

        if (node.children_ != null) {
            for (int i=0; i< node.children_.length; i++) {
                InnerToString(node.children_[i], element);
            }
        }
    }

    public String toString() {
        Document document = DocumentHelper.createDocument();
        InnerToString(this, document);
        return  document.asXML();
    }
}
