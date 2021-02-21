import Env.DeviceEnv;
import Handshake.NoiseHandshake;
import Message.WhatsMessage;
import ProtocolTree.ProtocolTreeNode;
import ProtocolTree.StanzaAttribute;
import Util.StringUtil;
import axolotl.AxolotlManager;
import com.google.protobuf.ByteString;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.groups.GroupCipher;
import org.whispersystems.libsignal.groups.GroupSessionBuilder;
import org.whispersystems.libsignal.groups.SenderKeyName;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SenderKeyDistributionMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GorgeosEngine implements NoiseHandshake.HandshakeNotify {
    public interface GorgeosEngineDelegate {
        public void OnLogin(int code, ProtocolTreeNode desc);
        public void OnDisconnect(String desc);
        public void OnSync(ProtocolTreeNode content);
        public void OnPacketResponse(String type, ProtocolTreeNode content);
    }

    interface NodeCallback {
        void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result);
    }


    public static class NodeHandleInfo {
        NodeCallback callbackRunnable;
        ProtocolTreeNode srcNode;
        NodeHandleInfo(NodeCallback callback, ProtocolTreeNode srcNode) {
            this.callbackRunnable = callback;
            this.srcNode = srcNode;
        }
    }


    static final String TAG = GorgeosEngine.class.getName();
    public  GorgeosEngine(String configPath, GorgeosEngineDelegate delegate, NoiseHandshake.Proxy proxy) {
        configPath_ = configPath;
        delegate_ = delegate;
        proxy_ = proxy;
    }

    boolean StartEngine() {
        axolotlManager_ = new AxolotlManager(configPath_);
        /*ProtocolTreeNode node =  ProtocolTreeNode.FromXml(ProtocolNodeJni.BytesToXml(Base64.getDecoder().decode("APgQFgb6/wuEcFUGBYoWEEZhMxAFHwT7EPZOfsmS/AY7A77JjxX8dbkI+v+HhhhohnYILwMengn/BRYQiWcGGPwMQmFnbWFuQE5pY2t5+AH4CBITDQVURBX9AAEXMwghEiEFHYy7P6D/zgREgDTlxWjFW+TUrt8jBrDjJ3G7YApcTHsaIQWOFDn9pPxhQH9NfwzR7O5xrEPK7C+zbBupCUIpMAhaACLDATMKIQWR05/bW5qolURMSEy8pJZYSJUpp4OKUciL3dshGs3YNxAUGAAikAF7xWswjMVC+Gsy9uCHZDJ9A6MUfklE+V+IDzRvyEcnW15xUP8Za/nBbszYz3T8XSDPpgGZrlGaxDyFeYcu8QzU3djRYF5V3w91N5xveQfk2amuRmTpUIPyCIkQYEKvRU3yqI1fPfhYWtiG7APTWc+uf16Y0QIKL3tkos3GsYAMW9ebHWFsvSFMYDvT/gnNc2H2MmVJO9CybCiy2raJBTAA")));
        HandleRecvMessage(node);*/
        try {
            byte[] envBuffer =  axolotlManager_.GetConfigStore().GetBytes("env");
            envBuilder_ = DeviceEnv.AndroidEnv.parseFrom(envBuffer).toBuilder();
            noiseHandshake_ = new NoiseHandshake(this, proxy_);
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
            noiseHandshake_.StopNoiseHandShake();
            noiseHandshake_ = null;
        }
    }

    private NoiseHandshake noiseHandshake_ = null;
    private String configPath_;
    NoiseHandshake.Proxy proxy_;
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

    byte[]  HandlePreKeyWhisperMessage(String recepid, ProtocolTreeNode encNode) throws InvalidVersionException, InvalidMessageException, InvalidKeyException, DuplicateMessageException, InvalidKeyIdException, UntrustedIdentityException, LegacyMessageException {
        SignalProtocolAddress address = new SignalProtocolAddress(recepid, 0);
        SessionCipher sessionCipher = new SessionCipher(axolotlManager_.GetSessionStore(), axolotlManager_.GetPreKeyStore(),axolotlManager_.GetSignedPreKeyStore(), axolotlManager_.GetIdentityKeyStore(), address);
        PreKeySignalMessage message = new PreKeySignalMessage(encNode.GetData());
        byte[] result = sessionCipher.decrypt(message);
        if (encNode.GetAttributeValue("v").equals("2")) {
            ParseAndHandleMessageProto(recepid, result);
        }
        return result;
    }

    byte[] HandleWhisperMessage(String recepid, ProtocolTreeNode encNode) throws NoSessionException, DuplicateMessageException, InvalidMessageException, UntrustedIdentityException, LegacyMessageException {
        SignalProtocolAddress address = new SignalProtocolAddress(recepid, 0);
        SessionCipher sessionCipher = new SessionCipher(axolotlManager_.GetSessionStore(), axolotlManager_.GetPreKeyStore(),axolotlManager_.GetSignedPreKeyStore(), axolotlManager_.GetIdentityKeyStore(), address);
        SignalMessage message = new SignalMessage(encNode.GetData());
        byte[] result = sessionCipher.decrypt(message);
        if (encNode.GetAttributeValue("v").equals("2")) {
            ParseAndHandleMessageProto(recepid, result);
        }
        return result;
    }

    byte[] HandleSenderKeyMessage(String recepid, ProtocolTreeNode messageNode) throws DuplicateMessageException, InvalidMessageException, LegacyMessageException {
        LinkedList<ProtocolTreeNode> encNodes = messageNode.GetChildren("enc");
        ProtocolTreeNode encNode = GetEncNode(encNodes, "skmsg");
        SignalProtocolAddress address = new SignalProtocolAddress(recepid, 0);
        SenderKeyName senderKeyName = new SenderKeyName(messageNode.GetAttributeValue("from"), address);
        GroupCipher groupCipher = new GroupCipher(axolotlManager_.GetSenderKeyStore(), senderKeyName);
        try {
            byte[] result = groupCipher.decrypt(encNode.GetData());
            ParseAndHandleMessageProto(recepid, result);
            return result;
        } catch (NoSessionException e) {
            SendRetry(messageNode);
        }
        return null;
    }

    void ParseAndHandleMessageProto(String recepid, byte[] serialData) {
        try {
            WhatsMessage.WhatsAppMessage message = WhatsMessage.WhatsAppMessage.parseFrom(serialData);
            if (message.hasSenderKeyDistributionMessage()) {
                GroupSessionBuilder sessionBuilder = new GroupSessionBuilder(axolotlManager_.GetSenderKeyStore());
                SignalProtocolAddress address = new SignalProtocolAddress(recepid, 0);
                SenderKeyName senderKeyName = new SenderKeyName(message.getSenderKeyDistributionMessage().getGroupId(), address);
                sessionBuilder.process(senderKeyName, new SenderKeyDistributionMessage(message.getSenderKeyDistributionMessage().getAxolotlSenderKeyDistributionMessage().toByteArray()));
            }
        }
        catch (Exception e) {

        }
    }

    ProtocolTreeNode GetEncNode(LinkedList<ProtocolTreeNode> encNodes, String encType) {
        for (ProtocolTreeNode encNode : encNodes) {
            if (encNode.GetAttributeValue("type").equals(encType)) {
                return encNode;
            }
        }
        return null;
    }

    class HandleGetKeysFor implements  NodeCallback{
        NodeCallback resultCallback_;
        HandleGetKeysFor(NodeCallback resultCallback) {
            resultCallback_ = resultCallback;
        }
        @Override
        public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
            LinkedList<ProtocolTreeNode> listNode = result.GetChildren("list");
            if (listNode.isEmpty()) {
                Log.w(TAG, "GetKeysFor list empty");
                return;
            }

            LinkedList<ProtocolTreeNode> users = listNode.get(0).GetChildren("user");
            for (ProtocolTreeNode user : users) {
                try {
                    //保存会话
                    String jid = user.GetAttributeValue("jid");
                    String recepid = jid;
                    int index =recepid.indexOf("@");
                    if (index != -1) {
                        recepid = recepid.substring(0, index);
                    }
                    SignalProtocolAddress address = new SignalProtocolAddress(recepid, 0);
                    SessionBuilder sessionBuilder = new SessionBuilder(axolotlManager_.GetSessionStore(),axolotlManager_.GetPreKeyStore(),axolotlManager_.GetSignedPreKeyStore(),axolotlManager_.GetIdentityKeyStore(),address);
                    ProtocolTreeNode registration = user.GetChild("registration");
                    //pre key
                    ProtocolTreeNode preKeyNode = user.GetChild("key");
                    ECPublicKey preKeyPublic = Curve.decodePoint(preKeyNode.GetChild("value").GetData(), 0);

                    //skey
                    ProtocolTreeNode skey = user.GetChild("skey");
                    ECPublicKey signedPreKeyPub = Curve.decodePoint(skey.GetChild("value").GetData(), 0);

                    //identity
                    ProtocolTreeNode identity = user.GetChild("identity");
                    IdentityKey identityKey = new IdentityKey(Curve.decodePoint(identity.GetData(),0));

                    PreKeyBundle preKeyBundle = new PreKeyBundle(DeAdjustId(registration.GetData()),0,DeAdjustId(preKeyNode.GetChild("id").GetData()),preKeyPublic,
                            DeAdjustId(skey.GetChild("id").GetData()),signedPreKeyPub,skey.GetChild("signature").GetData(),identityKey);
                    sessionBuilder.process(preKeyBundle);
                }
                catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
            resultCallback_.Run(srcNode, result);
        }
    }


    void GetKeysFor(List<String> jids, NodeCallback callback) {
        ProtocolTreeNode iq = new ProtocolTreeNode("iq");
        iq.AddAttribute(new StanzaAttribute("xmlns", "encrypt"));
        iq.AddAttribute(new StanzaAttribute("type", "get"));
        iq.AddAttribute(new StanzaAttribute("to", "s.whatsapp.net"));
        iq.AddAttribute(new StanzaAttribute("id", StringUtil.GenerateIqId()));

        ProtocolTreeNode key = new ProtocolTreeNode("key");
        for (String jid: jids) {
            ProtocolTreeNode user = new ProtocolTreeNode("user");
            user.AddAttribute(new StanzaAttribute("jid", jid));
            key.AddChild(user);
        }
        iq.AddChild(key);
        AddTask(iq, new HandleGetKeysFor(callback));
    }

    void SendRetry(ProtocolTreeNode node) {
        String iqid = node.IqId();
         Integer count = retries_.get(iqid);
         if (count == null) {
             count = new Integer(1);
             retries_.put(iqid, count);
         } else {
             count++;
         }
         ProtocolTreeNode receipt = new ProtocolTreeNode("receipt");
         receipt.AddAttribute(new StanzaAttribute("id", iqid));
        receipt.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
        receipt.AddAttribute(new StanzaAttribute("type", "retry"));
        receipt.AddAttribute(new StanzaAttribute("participant", node.GetAttributeValue("participant")));
        receipt.AddAttribute(new StanzaAttribute("t", node.GetAttributeValue("t")));

        //retry
        ProtocolTreeNode retry = new ProtocolTreeNode("retry");
        retry.AddAttribute(new StanzaAttribute("count", String.valueOf(count)));
        retry.AddAttribute(new StanzaAttribute("v", "1"));
        retry.AddAttribute(new StanzaAttribute("id", iqid));
        retry.AddAttribute(new StanzaAttribute("t", String.valueOf(System.currentTimeMillis() / 1000)));
        receipt.AddChild(retry);

        //register
        ProtocolTreeNode registration = new ProtocolTreeNode("registration");
        registration.SetData(AdjustId(axolotlManager_.GetIdentityKeyStore().getLocalRegistrationId()));

        receipt.AddChild(registration);
        AddTask(receipt);
    }


    class HandlePendingMessage implements NodeCallback {
        ProtocolTreeNode pendingMessage_;
        HandlePendingMessage(ProtocolTreeNode pendingMessage) {
            pendingMessage_ = pendingMessage;
        }
        @Override
        public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
            HandleRecvMessage(pendingMessage_);
        }
    }

    void HandleRecvMessage(ProtocolTreeNode node) {
        LinkedList<ProtocolTreeNode> encNodes = node.GetChildren("enc");
        if (encNodes.isEmpty()) {
            return;
        }
        String participant = node.GetAttributeValue("participant");
        boolean isGroup = !StringUtil.isEmpty(participant);
        String senderJid = isGroup? participant :  node.GetAttributeValue("from");
        String recepid = senderJid;
        byte[] plainText = null;
        int index =recepid.indexOf("@");
        if (index != -1) {
            recepid = recepid.substring(0, index);
        }
        try {
            ProtocolTreeNode pkMsgEncNode = GetEncNode(encNodes, "pkmsg");
            if (pkMsgEncNode != null) {
                HandlePreKeyWhisperMessage(recepid, pkMsgEncNode);
            } else {
                ProtocolTreeNode whisperEncNode = GetEncNode(encNodes, "msg");
                if (whisperEncNode != null) {
                    HandleWhisperMessage(recepid, whisperEncNode);
                }
            }

            ProtocolTreeNode skMsgEncNode = GetEncNode(encNodes, "skmsg");
            if (skMsgEncNode != null) {
                HandleSenderKeyMessage(recepid, node);
            }

            retries_.remove(node.IqId());
        }
        catch (InvalidKeyIdException e) {
            Log.e(TAG, e.getLocalizedMessage());
            SendRetry(node);
        } catch (LegacyMessageException e) {
            e.printStackTrace();
        } catch (InvalidMessageException e) {
            SendRetry(node);
            Log.e(TAG, e.getLocalizedMessage());
        } catch (UntrustedIdentityException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (InvalidKeyException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (DuplicateMessageException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (InvalidVersionException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (NoSessionException e) {
            LinkedList<String> jidList = new LinkedList<>();
            jidList.add(senderJid);
            GetKeysFor(jidList, new HandlePendingMessage(node));
            e.printStackTrace();
        }
        if (plainText != null) {

        }

        {
            //发送一个receipt
            ProtocolTreeNode receipt = new ProtocolTreeNode("receipt");
            receipt.AddAttribute(new StanzaAttribute("id", node.IqId()));
            receipt.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
            if (!participant.isEmpty()) {
                receipt.AddAttribute(new StanzaAttribute("participant", participant));
            }
            AddTask(receipt);
        }
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

    int DeAdjustId(byte[] data) {
        //转成16进制
        String hex = StringUtil.bytesToHex(data);
        return new BigInteger(hex, 16).intValue();
    }


    void  FlushKeys(List<PreKeyRecord> unsentPreKeys) {
        StanzaAttribute[] attributes = new StanzaAttribute[4];
        attributes[0] = new StanzaAttribute("type", "set");
        attributes[1] = new StanzaAttribute("xmlns", "encrypt");
        attributes[2] = new StanzaAttribute("to", "s.whatsapp.net");
        attributes[3] = new StanzaAttribute("id", StringUtil.GenerateIqId());
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
        AddTask(iqNode, new NodeCallback() {
            @Override
            public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
                HandleFlushKey(srcNode,result);
            }
        });
    }

    String AddTask(ProtocolTreeNode node, NodeCallback callback) {
        registerHandleMap_.put(node.IqId(), new NodeHandleInfo(callback, node));
        return noiseHandshake_.SendNode(node);
    }

    String AddTask(ProtocolTreeNode node) {
        return AddTask(node, null);
    }

    boolean  HandleRegisterNode(ProtocolTreeNode node) {
        NodeHandleInfo  handleInfo = registerHandleMap_.remove(node.IqId());
        if (handleInfo == null) {
            return  false;
        }
        if (handleInfo.callbackRunnable == null) {
            return false;
        }
        handleInfo.callbackRunnable.Run(handleInfo.srcNode, node);
        return true;
    }

    ConcurrentHashMap<String, NodeHandleInfo> registerHandleMap_ = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Integer> retries_ = new ConcurrentHashMap<>();


    void HandleFlushKey(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
        StanzaAttribute type = result.GetAttribute("type");
        if ((type != null) && (type.value_.equals("result"))) {
            //更新db
            LinkedList<Integer> sentPrekeyIds = (LinkedList<Integer>)srcNode.GetCustomParams();
            axolotlManager_.GetPreKeyStore().setAsSent(sentPrekeyIds);
        }
    }

    String JidNormalize(String jid) {
        int pos = jid.indexOf("@");
        if (pos != -1) {
            return jid;
        }
        pos = jid.indexOf("-");
        if (pos != -1) {
            return jid + "@g.us";
        }
        return jid + "@s.whatsapp.net";
    }

    String SendSerialData(String jid, byte[] serialData, String messageType, String mediaType, String iqId) {
        jid = JidNormalize(jid);
        String recepid = jid;
        int index =recepid.indexOf("@");
        if (index != -1) {
            recepid = recepid.substring(0, index);
        }
        boolean isGroup = jid.indexOf("-") != -1 ? true : false;
        byte[] paddingData = new byte[serialData.length + 1];
        paddingData[0] = 1;
        System.arraycopy(serialData, 0, paddingData, 1, serialData.length);
        if (isGroup) {
            return SendToGroup(jid, paddingData, messageType, mediaType, iqId);
        } else if (axolotlManager_.GetSessionStore().containsSession(recepid)) {
            return SendToContact(jid, paddingData, messageType, mediaType, iqId);
        } else {
            if (StringUtil.isEmpty(iqId)) {
                iqId = StringUtil.GenerateIqId();
            }
            LinkedList<String> jids = new LinkedList<>();
            jids.add(jid);
            String finalJid = jid;
            String finalIqId = iqId;
            GetKeysFor(jids, new NodeCallback() {
                @Override
                public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
                    SendToContact(finalJid, paddingData,messageType, mediaType, finalIqId);
                }
            });
            return iqId;
        }
    }

    String SendToGroup(String jid, byte[] paddingData, String messageType, String mediaType, String iqId) {
        SignalProtocolAddress address = new SignalProtocolAddress(envBuilder_.getFullphone(), 0);
        SenderKeyName senderKeyName = new SenderKeyName(jid, address);
        SenderKeyRecord senderKeyRecord = axolotlManager_.GetSenderKeyStore().loadSenderKey(senderKeyName);
        if (null == senderKeyRecord) {
            //获取群信息

        } else {

        }
        return "";
    }

    String SendToContact(String jid, byte[] paddingData, String messageType, String mediaType, String iqId) {
        String recepid = jid;
        int index =recepid.indexOf("@");
        if (index != -1) {
            recepid = recepid.substring(0, index);
        }
        SignalProtocolAddress address = new SignalProtocolAddress(recepid, 0);
        SessionCipher sessionCipher = new SessionCipher(axolotlManager_.GetSessionStore(), axolotlManager_.GetPreKeyStore(),axolotlManager_.GetSignedPreKeyStore(), axolotlManager_.GetIdentityKeyStore(), address);
        CiphertextMessage ciphertextMessage = null;
        try {
            ciphertextMessage = sessionCipher.encrypt(paddingData);
            return SendEncMessage(jid ,ciphertextMessage.serialize(), ciphertextMessage.getType(),messageType, mediaType, iqId);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
        return "";
    }

    String SendEncMessage(String jid, byte[] cipherText, int encType, String messageType, String mediaType, String iqId) {
        ProtocolTreeNode msg = new ProtocolTreeNode("message");
        if (StringUtil.isEmpty(iqId)) {
            iqId = StringUtil.GenerateIqId();
        }
        msg.AddAttribute(new StanzaAttribute("id", iqId));
        msg.AddAttribute(new StanzaAttribute("type", messageType));
        msg.AddAttribute(new StanzaAttribute("to", jid));
        msg.AddAttribute(new StanzaAttribute("t", String.valueOf(System.currentTimeMillis() / 1000)));

        //enc node
        ProtocolTreeNode encNode = new ProtocolTreeNode("enc");
        encNode.SetData(cipherText);
        if (!StringUtil.isEmpty(mediaType)) {
            encNode.AddAttribute(new StanzaAttribute("mediatype", mediaType));
        }
        switch (encType) {
            case CiphertextMessage.PREKEY_TYPE:
            {
                encNode.AddAttribute(new StanzaAttribute("type", "pkmsg"));
            }
            break;
            case CiphertextMessage.SENDERKEY_TYPE:
            {
                encNode.AddAttribute(new StanzaAttribute("type", "skmsg"));
            }
            break;
            default:
            {
                encNode.AddAttribute(new StanzaAttribute("type", "msg"));
            }
        }
        msg.AddChild(encNode);
        return AddTask(msg);
    }

    private GorgeosEngineDelegate delegate_ = null;
}
