package ProtocolTree;

import Util.StringUtil;
import org.dom4j.*;
import org.dom4j.io.SAXReader;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
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
        for (ProtocolTreeNode child : children_) {
            if (child.GetTag().equals(name)) {
                results.add(child);
            }
        }
        return  results;
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

    static  ProtocolTreeNode InnerParseElement(Element element){
        ProtocolTreeNode node = new ProtocolTreeNode(element.getName());
        List<Attribute> attributes = element.attributes();
        for( Attribute attribute : attributes) {
            node.attributes_.add(new StanzaAttribute(attribute.getName(), attribute.getValue()));
        }

        String elementContent = element.getTextTrim();
        if (!Util.StringUtil.isEmpty(elementContent)) {
            node.SetData(Base64.getDecoder().decode(elementContent));
        }

        List<Element> childrenElements = element.elements();
        for (Element childElement : childrenElements){
            node.AddChild(InnerParseElement(childElement));
        }

        return node;
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
        String namespace = null;
        if (node.attributes_ != null) {
            for (StanzaAttribute attribute : node.attributes_) {
                if (attribute.key_.equals("xmlns")) {
                    namespace = attribute.value_;
                    break;
                }
            }
        }

        Element element = null;
        if (StringUtil.isEmpty(namespace)) {
            element = parent.addElement(node.tag_);
        } else {
            element = parent.addElement(node.tag_, namespace);
        }

        if (node.data_ != null) {
            element.setText(Base64.getEncoder().encodeToString(node.data_));
        }
        if (node.attributes_ != null) {
            for (StanzaAttribute attribute : node.attributes_) {
                element.addAttribute(attribute.key_, attribute.value_);
            }
        }

        if (node.children_ != null) {
            for (ProtocolTreeNode child : node.children_) {
                InnerToString(child, element);
            }
        }
    }

    public String toString() {
        Document document = DocumentHelper.createDocument();
        InnerToString(this, document);

        return  document.asXML();
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
