import Env.DeviceEnv;
import Handshake.NoiseHandshake;
import ProtocolTree.ProtocolTreeNode;
import ProtocolTree.StanzaAttribute;
import Util.StringUtil;
import axolotl.AxolotlManager;
import com.google.protobuf.ByteString;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.lang.reflect.Method;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GorgeosEngine implements NoiseHandshake.HandshakeNotify {
    public interface GorgeosEngineDelegate {
        public void OnLogin(int code, ProtocolTreeNode desc);
        public void OnDisconnect(String desc);
        public void OnSync(ProtocolTreeNode content);
        public void OnPacketResponse(String type, ProtocolTreeNode content);
    }

    public static class NodeHandleInfo {
        String funName;
        ProtocolTreeNode srcNode;
        NodeHandleInfo(String funName, ProtocolTreeNode srcNode) {
            this.funName = funName;
            this.srcNode = srcNode;
        }
    }


    static final String TAG = GorgeosEngine.class.getName();
    public GorgeosEngine(String configPath, GorgeosEngineDelegate delegate, Proxy proxy) {
        configPath_ = configPath;
        delegate_ = delegate;
        proxy_ = proxy;
    }

    boolean StartEngine() {
        axolotlManager_ = new AxolotlManager(configPath_);

        try {
            byte[] envBuffer =  axolotlManager_.GetConfigStore().GetBytes("env");
            envBuilder_ = DeviceEnv.AndroidEnv.parseFrom(envBuffer).toBuilder();
            noiseHandshake_ = new NoiseHandshake(this, null);
            noiseHandshake_.StartNoiseHandShake( envBuilder_.build());
            return true;
        }
        catch (Exception e){
            Log.e(TAG, "parse env failed:" + e.getLocalizedMessage());
            return false;
        }
    }

    void StopEngine() {
        if (noiseHandshake_ != null) {
            noiseHandshake_.Disconnect();
            noiseHandshake_ = null;
        }
    }

    private NoiseHandshake noiseHandshake_ = null;
    private String configPath_;
    Proxy proxy_;
    AxolotlManager axolotlManager_;
    DeviceEnv.AndroidEnv.Builder envBuilder_;

    @Override
    public void OnConnected(byte[] serverPublicKey) {
        if (serverPublicKey != null) {
            envBuilder_.setServerStaticPublic(ByteString.copyFrom(serverPublicKey));
        } else {
            envBuilder_.clearServerStaticPublic();
        }

        axolotlManager_.GetConfigStore().SetBytes("env", envBuilder_.build().toByteArray());
    }

    @Override
    public void OnDisconnected(String desc) {
        Log.e(TAG, "OnDisconnected:" + desc);
        if (null != delegate_) {
            delegate_.OnDisconnect(desc);
        }
    }

    @Override
    public void OnPush(ProtocolTreeNode node) {
        if (HandleRegisterNode(node)) {
            return;
        }
        switch (node.GetTag()){
            case "iq":{
                HandleIq(node);
                break;
            }
            case "call":{
                HandleCall(node);
                break;
            }
            case "stream:error":{
                HandleStreamError(node);
                break;
            }
            case "failure":{
                HandleFailure(node);
                break;
            }
            case "success":{
                HandleSuccess(node);
                break;
            }
            case "receipt":{
                HandleAeceipt(node);
                break;
            }
            case "message":{
                HandleRecvMessage(node);
                break;
            }
            case "ack":{
                HandleAck(node);
                break;
            }
            case "notification":{
                HandleNotification(node);
                break;
            }
            case "presence":{
                HandlePresence(node);
                break;
            }
            default:
            {
                break;
            }
        }
    }

    void  HandlePresence(ProtocolTreeNode node) {
        if (null != delegate_) {
            delegate_.OnSync(node);
        }
    }


    void  HandleIq(ProtocolTreeNode node) {

    }

    void HandleCall(ProtocolTreeNode node) {
        String type = node.GetAttributeValue("type");
        if (type.equals("offer")) {
            ProtocolTreeNode receipt = new ProtocolTreeNode("receipt");
            receipt.AddAttribute(new StanzaAttribute("id", node.GetAttributeValue("id")));
            receipt.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
            LinkedList<ProtocolTreeNode> offer = node.GetChildren("offer");
            if (!offer.isEmpty()) {
                receipt.AddChild(offer.get(0));
            }
            AddTask(receipt);
        } else {
            ProtocolTreeNode ack = new ProtocolTreeNode("ack");
            ack.AddAttribute(new StanzaAttribute("id", node.GetAttributeValue("id")));
            ack.AddAttribute(new StanzaAttribute("class", "call"));
            ack.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
            AddTask(ack);
        }
    }

    void HandleStreamError(ProtocolTreeNode node) {
        if (null != delegate_) {
            delegate_.OnDisconnect(node.toString());
        }
    }

    void HandleFailure(ProtocolTreeNode node) {
        if (null != delegate_) {
            delegate_.OnLogin(-1 , node);
        }
    }

    void HandleSuccess(ProtocolTreeNode node) {
        if (null != delegate_) {
            delegate_.OnLogin(0 , node);
        }
        LinkedList<PreKeyRecord>  unsentPreKeys = axolotlManager_.GetPreKeyStore().loadUnSendPreKey();
        FlushKeys(unsentPreKeys);
    }

    void HandleAeceipt(ProtocolTreeNode node) {
        StanzaAttribute type = node.GetAttribute("type");
        if (null != type) {
            if (type.value_.equals("retry")) {
                //需要發送retry
            }
        }
        {
            //发送确认
            ProtocolTreeNode ack = new ProtocolTreeNode("ack");
            ack.AddAttribute(new StanzaAttribute("id", node.GetAttributeValue("id")));
            ack.AddAttribute(new StanzaAttribute("class", "receipt"));
            ack.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
            {
                //type
                String value = node.GetAttributeValue("type");
                if (!StringUtil.isEmpty(value)) {
                    ack.AddAttribute(new StanzaAttribute("type", value));
                }
            }

            {
                //participant
                String value = node.GetAttributeValue("participant");
                if (!StringUtil.isEmpty(value)) {
                    ack.AddAttribute(new StanzaAttribute("participant", value));
                }
            }

            {
                //list
                LinkedList<ProtocolTreeNode> list =  node.GetChildren("list");
                for (ProtocolTreeNode child : list) {
                    ack.AddChild(child);
                }
            }
            AddTask(ack);
        }
        if (delegate_ != null) {
            delegate_.OnSync(node);
        }
    }

    void HandleRecvMessage(ProtocolTreeNode node) {

    }

    void HandleAck(ProtocolTreeNode node) {
        String type = node.GetAttributeValue("class");
        if (type.equals("message")) {
            if (delegate_ != null) {
                delegate_.OnPacketResponse("ack", node);
            }
        }
    }

    void HandleNotification(ProtocolTreeNode node) {
        String type = node.GetAttributeValue("type");
        if (type.equals("encrypt")) {
            List<PreKeyRecord> records = axolotlManager_.GetPreKeyStore().generatePreKey();
            FlushKeys(records);
        }
    }

    byte[] AdjustId(int id) {
        String hex = Integer.toHexString(id);
        if (hex.length() % 2 != 0) {
            hex = "0" + hex;
        }
        while (hex.length() < 6) {
            hex = "0" + hex;
        }
        byte[] baKeyword = new byte[hex.length() / 2];
        for (int i = 0; i < baKeyword.length; i++) {
            try {
                baKeyword[i] = (byte) (0xff & Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return baKeyword;
    }

    void  FlushKeys(List<PreKeyRecord> unsentPreKeys) {
        StanzaAttribute[] attributes = new StanzaAttribute[4];
        attributes[0] = new StanzaAttribute("type", "set");
        attributes[1] = new StanzaAttribute("xmlns", "encrypt");
        attributes[2] = new StanzaAttribute("to", "s.whatsapp.net");
        attributes[3] = new StanzaAttribute("id", UUID.randomUUID().toString().replaceAll("-", ""));
        ProtocolTreeNode iqNode = new ProtocolTreeNode("iq", attributes);
        {
            // prekey 节点
            ProtocolTreeNode list = new ProtocolTreeNode("list");
            LinkedList<Integer> sentPrekeyIds = new LinkedList<>();
            for (int i=0; i< unsentPreKeys.size(); i++) {
                PreKeyRecord record = unsentPreKeys.get(i);
                sentPrekeyIds.add(record.getId());
                ProtocolTreeNode key = new ProtocolTreeNode("key");
                {
                    ProtocolTreeNode idNode = new ProtocolTreeNode("id");
                    idNode.SetData(AdjustId(record.getId()));
                    key.AddChild(idNode);
                }
                {
                    ProtocolTreeNode value = new ProtocolTreeNode("value");
                    value.SetData(record.getKeyPair().getPublicKey().serialize(), 1, 32);
                    key.AddChild(value);
                }
                list.AddChild(key);
            }
            iqNode.AddChild(list);
            iqNode.SetCustomParams(sentPrekeyIds);
        }

        {
            //identify
            ProtocolTreeNode identity = new ProtocolTreeNode("identity");
            identity.SetData(axolotlManager_.GetIdentityKeyStore().getIdentityKeyPair().getPublicKey().serialize(), 1, 32);
            iqNode.AddChild(identity);
        }

        {
            //registration
            ProtocolTreeNode registration = new ProtocolTreeNode("registration");
            registration.SetData(AdjustId(axolotlManager_.GetIdentityKeyStore().getLocalRegistrationId()));
            iqNode.AddChild(registration);
        }

        {
            //type
            ProtocolTreeNode type = new ProtocolTreeNode("type");
            type.SetData("5".getBytes(StandardCharsets.UTF_8));
            iqNode.AddChild(type);
        }

        {
            //signature  key skey
            ProtocolTreeNode skey = new ProtocolTreeNode("skey");
            SignedPreKeyRecord signedPreKeyRecord =  axolotlManager_.GetSignedPreKeyStore().loadLatestSignedPreKey();
            if (null != signedPreKeyRecord) {
                {
                    ProtocolTreeNode id = new ProtocolTreeNode("id");
                    id.SetData(AdjustId(signedPreKeyRecord.getId()));
                    skey.AddChild(id);
                }
                {
                    ProtocolTreeNode value = new ProtocolTreeNode("value");
                    value.SetData(signedPreKeyRecord.getKeyPair().getPublicKey().serialize(), 1, 32);
                    skey.AddChild(value);
                }

                {
                    ProtocolTreeNode signature = new ProtocolTreeNode("signature");
                    signature.SetData(signedPreKeyRecord.getSignature());
                    skey.AddChild(signature);
                }
            }
            iqNode.AddChild(skey);
        }
        AddTask(iqNode, "HandleFlushKey");
    }

    String AddTask(ProtocolTreeNode node, String callbackFuncName) {
        registerHandleMap.put(node.IqId(), new NodeHandleInfo(callbackFuncName, node));
        return noiseHandshake_.SendNode(node);
    }

    String AddTask(ProtocolTreeNode node) {
        return AddTask(node, null);
    }

    boolean  HandleRegisterNode(ProtocolTreeNode node) {
        NodeHandleInfo  handleInfo = registerHandleMap.remove(node.IqId());
        if (handleInfo == null) {
            return  false;
        }
        if (StringUtil.isEmpty(handleInfo.funName)) {
            return false;
        }
        Method method = reflectMethods.get(handleInfo.funName);
        if (method == null) {
            try {
                method = this.getClass().getMethod(handleInfo.funName, new Class[] {ProtocolTreeNode.class, ProtocolTreeNode.class});
            }
            catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
            reflectMethods.put(handleInfo.funName, method);
        }

        if (method == null) {
            return true;
        }
        try {
            method.invoke(this, handleInfo.srcNode, node);
        }
        catch (Exception e) {

        }
        return true;
    }

    ConcurrentHashMap<String, NodeHandleInfo> registerHandleMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Method> reflectMethods = new ConcurrentHashMap<>();

    void  HandleFlushKey(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
        StanzaAttribute type = result.GetAttribute("type");
        if ((type != null) && (type.value_.equals("result"))) {
            //更新db
            LinkedList<Integer> sentPrekeyIds = (LinkedList<Integer>)srcNode.GetCustomParams();
            axolotlManager_.GetPreKeyStore().setAsSent(sentPrekeyIds);
        }
    }

    private GorgeosEngineDelegate delegate_ = null;
}
