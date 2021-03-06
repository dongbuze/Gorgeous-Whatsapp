import Env.DeviceEnv;
import Handshake.NoiseHandshake;
import Message.WhatsMessage;
import ProtocolTree.ProtocolTreeNode;
import ProtocolTree.StanzaAttribute;
import Util.GorgeousLooper;
import Util.StringUtil;
import axolotl.AxolotlManager;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.SenderKeyDistributionMessage;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GorgeousEngine implements NoiseHandshake.HandshakeNotify {
    public interface GorgeousEngineDelegate {
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


    static final String TAG = GorgeousEngine.class.getName();
    public  GorgeousEngine(String configPath, GorgeousEngineDelegate delegate, NoiseHandshake.Proxy proxy) {
        configPath_ = configPath;
        delegate_ = delegate;
        proxy_ = proxy;
    }

    boolean StartEngine() {
        axolotlManager_ = new AxolotlManager(configPath_);
        try {
            byte[] envBuffer =  axolotlManager_.GetBytesSetting("env");
            envBuilder_ = DeviceEnv.AndroidEnv.parseFrom(envBuffer).toBuilder();
            Env.DeviceEnv.AppVersion.Builder useragentBuilder = envBuilder_.getUserAgentBuilder().getAppVersionBuilder();
            useragentBuilder.setPrimary(2);
            useragentBuilder.setSecondary(21);
            useragentBuilder.setTertiary(3);
            useragentBuilder.setQuaternary(20);
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
        if (null != axolotlManager_) {
            axolotlManager_.Close();
        }
    }

    private NoiseHandshake noiseHandshake_ = null;
    private String configPath_;
    NoiseHandshake.Proxy proxy_;
    AxolotlManager axolotlManager_;
    DeviceEnv.AndroidEnv.Builder envBuilder_;
    Timer pingTimer_;

    @Override
    public void OnConnected(byte[] serverPublicKey) {
        if (serverPublicKey != null) {
            envBuilder_.setServerStaticPublic(ByteString.copyFrom(serverPublicKey));
        } else {
            envBuilder_.clearServerStaticPublic();
        }
        axolotlManager_.SetBytesSetting("env", envBuilder_.build().toByteArray());
    }

    @Override
    public void OnDisconnected(String desc) {
        if (null != delegate_) {
            delegate_.OnDisconnect(desc);
        }
        if (null != pingTimer_) {
            pingTimer_.cancel();
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

    void SendPing() {
        ProtocolTreeNode ping = new ProtocolTreeNode("iq");
        ping.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        ping.AddAttribute(new StanzaAttribute("xmlns", "w:p"));
        ping.AddAttribute(new StanzaAttribute("type", "get"));
        ping.AddAttribute(new StanzaAttribute("to", "s.whatsapp.net"));
        ping.AddChild(new ProtocolTreeNode("ping"));
        AddTask(ping);
    }

    void HandleSuccess(ProtocolTreeNode node) {
        if (null != delegate_) {
            delegate_.OnLogin(0 , node);
        }

        GetCdnInfo();
        pingTimer_ = new Timer();
        pingTimer_.schedule(new TimerTask() {
            @Override
            public void run() {
                SendPing();
            }
        }, 100, 4 * 60 *1000);
        {
            //available
            ProtocolTreeNode available = new ProtocolTreeNode("presence");
            available.AddAttribute(new StanzaAttribute("type", "available"));
            AddTask(available);
        }
    }

    void Test() {
        SendPing();
    }

    public String SendText(String jid, String content) {
            String id = GenerateIqId();
            GorgeousLooper.Instance().PostTask(() -> {
            WhatsMessage.WhatsAppMessage.Builder builder = WhatsMessage.WhatsAppMessage.newBuilder();
            builder.setConversation(content);
            SendSerialData(jid, builder.build().toByteArray(), "text", "", id);
        });
        //序列化数据
        return id;
    }

    void GetCdnInfo() {
        ProtocolTreeNode cdnNode = new ProtocolTreeNode("iq");
        cdnNode.AddAttribute(new StanzaAttribute("to", "s.whatsapp.net"));
        cdnNode.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        cdnNode.AddAttribute(new StanzaAttribute("xmlns", "w:m"));
        cdnNode.AddAttribute(new StanzaAttribute("type", "set"));


        ProtocolTreeNode media = new ProtocolTreeNode("media_conn");
        cdnNode.AddChild(media);
        AddTask(cdnNode, (srcNode, result) -> {
            String type = result.GetAttributeValue("type");
            if (!type.equals("result")) {
                Log.e(TAG, "cdn 失败:" + result.toString());
                return;
            }
            ProtocolTreeNode media_conn = result.GetChild("media_conn");
            if (null == media_conn) {
                Log.e(TAG, "media_conn 节点:" + result.toString());
                return;
            }
            cdnAuthKey_ = media_conn.GetAttributeValue("auth");
            LinkedList<ProtocolTreeNode> children = media_conn.GetChildren();
            for (ProtocolTreeNode child : children) {
                if (child.GetAttributeValue("type").equals("primary")) {
                    cdnHost_ = child.GetAttributeValue("hostname");
                    break;
                }
            }
        });
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

    byte[]  HandlePreKeyWhisperMessage(String recepid, ProtocolTreeNode encNode) throws UntrustedIdentityException, LegacyMessageException, InvalidVersionException, InvalidMessageException, DuplicateMessageException, InvalidKeyException, InvalidKeyIdException {
        byte[] result =  axolotlManager_.DecryptPKMsg(recepid, encNode.GetData());
        if (encNode.GetAttributeValue("v").equals("2")) {
            ParseAndHandleMessageProto(recepid, result);
        }
        return result;
    }

    byte[] HandleWhisperMessage(String recepid, ProtocolTreeNode encNode) throws NoSessionException, DuplicateMessageException, InvalidMessageException, UntrustedIdentityException, LegacyMessageException {
        byte[] result =  axolotlManager_.DecryptMsg(recepid, encNode.GetData());
        if (encNode.GetAttributeValue("v").equals("2")) {
            ParseAndHandleMessageProto(recepid, result);
        }
        return result;
    }

    byte[] HandleSenderKeyMessage(String recepid, ProtocolTreeNode messageNode) throws DuplicateMessageException, InvalidMessageException, LegacyMessageException {
        LinkedList<ProtocolTreeNode> encNodes = messageNode.GetChildren("enc");
        ProtocolTreeNode encNode = GetEncNode(encNodes, "skmsg");
        try {
            byte[] result =  axolotlManager_.GroupDecrypt(messageNode.GetAttributeValue("from"), recepid, encNode.GetData());
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
                axolotlManager_.GroupCreateSession(message.getSenderKeyDistributionMessage().getGroupId(), recepid, message.getSenderKeyDistributionMessage().getAxolotlSenderKeyDistributionMessage().toByteArray());
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


    byte[] CombineDecodePoint(byte[] buffer) {
        byte[] result = new byte[buffer.length + 1];
        result[0] = 5;
        System.arraycopy(buffer, 0, result, 1, buffer.length);
        return result;
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
                     ProtocolTreeNode registration = user.GetChild("registration");
                    //pre key
                    ProtocolTreeNode preKeyNode = user.GetChild("key");
                    if (null == preKeyNode) {
                        Log.e(TAG, "没有获取到 prekey:" + user.toString());
                        continue;
                    }
                    ECPublicKey preKeyPublic = Curve.decodePoint(CombineDecodePoint(preKeyNode.GetChild("value").GetData()), 0);

                    //skey
                    ProtocolTreeNode skey = user.GetChild("skey");
                    ECPublicKey signedPreKeyPub = Curve.decodePoint(CombineDecodePoint(skey.GetChild("value").GetData()), 0);

                    //identity
                    ProtocolTreeNode identity = user.GetChild("identity");
                    IdentityKey identityKey = new IdentityKey(Curve.decodePoint(CombineDecodePoint(identity.GetData()),0));

                    PreKeyBundle preKeyBundle = new PreKeyBundle(DeAdjustId(registration.GetData()),0,DeAdjustId(preKeyNode.GetChild("id").GetData()),preKeyPublic,
                            DeAdjustId(skey.GetChild("id").GetData()),signedPreKeyPub,skey.GetChild("signature").GetData(),identityKey);

                    axolotlManager_.CreateSession(recepid, preKeyBundle);
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
        iq.AddAttribute(new StanzaAttribute("id", GenerateIqId()));

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
         } else {
             count++;
         }
         retries_.put(iqid, count);
         if (count >= 3) {
             retries_.remove(iqid);
             return;
         }
         ProtocolTreeNode receipt = new ProtocolTreeNode("receipt");
         receipt.AddAttribute(new StanzaAttribute("id", iqid));
        receipt.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
        receipt.AddAttribute(new StanzaAttribute("type", "retry"));
        String participant = node.GetAttributeValue("participant");
        if (!StringUtil.isEmpty(participant)) {
            receipt.AddAttribute(new StanzaAttribute("participant", participant));
        }
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
        registration.SetData(AdjustId(axolotlManager_.getLocalRegistrationId()));

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
                plainText = HandlePreKeyWhisperMessage(recepid, pkMsgEncNode);
            } else {
                ProtocolTreeNode whisperEncNode = GetEncNode(encNodes, "msg");
                if (whisperEncNode != null) {
                    plainText = HandleWhisperMessage(recepid, whisperEncNode);
                }
            }

            ProtocolTreeNode skMsgEncNode = GetEncNode(encNodes, "skmsg");
            if (skMsgEncNode != null) {
                plainText = HandleSenderKeyMessage(recepid, node);
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
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

        if (plainText != null) {
            try {
                WhatsMessage.WhatsAppMessage msg = WhatsMessage.WhatsAppMessage.parseFrom(plainText);
                Log.d(TAG, "接收消息:" + msg.toString());
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
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
            ProtocolTreeNode child = node.GetChild("count");
            if (child != null) {
                axolotlManager_.LevelPreKeys(true);
                LinkedList<PreKeyRecord>  unsentPreKeys = axolotlManager_.LoadUnSendPreKey();
                FlushKeys(axolotlManager_.LoadLatestSignedPreKey(false),  unsentPreKeys);
            }
        }

        ProtocolTreeNode ack = new ProtocolTreeNode("ack");
        ack.AddAttribute(new StanzaAttribute("id", node.GetAttributeValue("id")));
        ack.AddAttribute(new StanzaAttribute("class", "notification"));
        ack.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
        if (!StringUtil.isEmpty(type)) {
            ack.AddAttribute(new StanzaAttribute("type", type));
        }
        String participant = node.GetAttributeValue("participant");
        if (!StringUtil.isEmpty(participant)) {
            ack.AddAttribute(new StanzaAttribute("participant", participant));
        }
        AddTask(ack);
        if (null != delegate_) {
            delegate_.OnSync(node);
        }
    }


    class HandleResult implements NodeCallback{
        String type_;
        HandleResult(String type) {
            type_ = type;
        }
        @Override
        public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
            if (null != delegate_) {
                delegate_.OnPacketResponse(type_, result);
            }
    }
    }

    public String CreateGroup(String subjectName, List<String> members) {
        ProtocolTreeNode node = new ProtocolTreeNode("iq");
        node.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        node.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));
        node.AddAttribute(new StanzaAttribute("type", "set"));
        node.AddAttribute(new StanzaAttribute("to", "g.us"));

        ProtocolTreeNode create = new ProtocolTreeNode("create");
        create.AddAttribute(new StanzaAttribute("subject", subjectName));
        for (String member : members) {
            ProtocolTreeNode participant = new ProtocolTreeNode("participant");
            participant.AddAttribute(new StanzaAttribute("jid", JidNormalize(member)));
            create.AddChild(participant);
        }
        node.AddChild(create);

        return AddTask(node, new HandleResult("creategroup"));
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
        String hex = StringUtil.BytesToHex(data);
        return new BigInteger(hex, 16).intValue();
    }


    void  FlushKeys(SignedPreKeyRecord signedPreKeyRecord, List<PreKeyRecord> unsentPreKeys) {
        StanzaAttribute[] attributes = new StanzaAttribute[4];
        attributes[0] = new StanzaAttribute("id", GenerateIqId());
        attributes[1] = new StanzaAttribute("xmlns", "encrypt");
        attributes[2] = new StanzaAttribute("type", "set");
        attributes[3] = new StanzaAttribute("to", "s.whatsapp.net");
        ProtocolTreeNode iqNode = new ProtocolTreeNode("iq", attributes);
        {
            //identify
            ProtocolTreeNode identity = new ProtocolTreeNode("identity");
            identity.SetData(axolotlManager_.GetIdentityKeyPair().getPublicKey().serialize(), 1, 32);
            iqNode.AddChild(identity);
        }

        {
            //registration
            ProtocolTreeNode registration = new ProtocolTreeNode("registration");
            registration.SetData(AdjustId(axolotlManager_.getLocalRegistrationId()));
            iqNode.AddChild(registration);
        }

        {
            //type
            ProtocolTreeNode type = new ProtocolTreeNode("type");
            type.SetData(new byte[]{5});
            iqNode.AddChild(type);
        }
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
            //signature  key skey
            ProtocolTreeNode skey = new ProtocolTreeNode("skey");
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
            iqNode.AddChild(skey);
        }
        AddTask(iqNode, (srcNode, result) -> HandleFlushKey(srcNode,result));
    }

    String AddTask(ProtocolTreeNode node, NodeCallback callback) {
        if (null != callback) {
            registerHandleMap_.put(node.IqId(), new NodeHandleInfo(callback, node));
        }
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
            axolotlManager_.SetAsSent(sentPrekeyIds);
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
        if (StringUtil.isEmpty(iqId)) {
            iqId = GenerateIqId();
        }
        jid = JidNormalize(jid);
        String recepid = jid;
        int index =recepid.indexOf("@");
        if (index != -1) {
            recepid = recepid.substring(0, index);
        }
        boolean isGroup = jid.indexOf("-") != -1 ? true : false;
        if (isGroup) {
            return SendToGroup(jid, serialData, messageType, mediaType, iqId);
        } else if (axolotlManager_.ContainsSession(recepid)) {
            return SendToContact(jid, serialData, messageType, mediaType, iqId);
        } else {
            LinkedList<String> jids = new LinkedList<>();
            jids.add(jid);
            String finalJid = jid;
            String finalIqId = iqId;
            GetKeysFor(jids, (srcNode, result) -> SendToContact(finalJid, serialData,messageType, mediaType, finalIqId));
            return iqId;
        }
    }


    class HandleGetGroupInfo implements NodeCallback{
        byte[] message_;
        String messageType_;
        String mediaType_;
        String iqId_;
        HandleGetGroupInfo(byte[] message, String messageType, String mediaType, String iqId) {
            message_ = message;
            messageType_ = messageType;
            mediaType_ = mediaType;
            iqId_ = iqId;
        }
        @Override
        public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
            ProtocolTreeNode group = result.GetChild("group");
            if (group == null) {
                Log.e(TAG, "获取群失败:" + srcNode.toString());
                return;
            }
            String selfJid = JidNormalize(envBuilder_.getFullphone());
            List<String> sessionJids = new LinkedList<>();
            LinkedList<ProtocolTreeNode>  participants = group.GetChildren("participant");
            for (ProtocolTreeNode participant : participants) {
                String jid = participant.GetAttributeValue("jid");
                if (jid.equals(selfJid)) {
                    continue;
                }
                sessionJids.add(jid);
            }
            EnsureSessionsAndSendToGroup(srcNode.GetAttributeValue("to"), sessionJids, message_, messageType_, mediaType_, iqId_);
        }
    }


    void EnsureSessionsAndSendToGroup(String groupId, List<String> sessionJids, byte[] message, String messageType, String mediaType, String iqId) {
        List<String> jidsNoSession = new LinkedList<>();
        for (String jid : sessionJids) {
            String recepid = jid;
            int index =recepid.indexOf("@");
            if (index != -1) {
                recepid = recepid.substring(0, index);
            }
            if (!axolotlManager_.ContainsSession(recepid)) {
                jidsNoSession.add(jid);
            }
        }
        if (jidsNoSession.isEmpty()) {
            try {
                SendToGroupWithSessions(groupId, sessionJids, message, messageType, mediaType, iqId);
            } catch (UntrustedIdentityException e) {
                e.printStackTrace();
            } catch (NoSessionException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }
        } else {
            GetKeysFor(jidsNoSession, (srcNode, result) -> {
                try {
                    SendToGroupWithSessions(groupId, jidsNoSession, message, messageType, mediaType, iqId);
                } catch (UntrustedIdentityException e) {
                    e.printStackTrace();
                } catch (NoSessionException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    void SendToGroupWithSessions(String groupId, List<String> sessionJids, byte[] message, String messageType, String mediaType, String iqId) throws UntrustedIdentityException, NoSessionException, InvalidKeyException {
        ProtocolTreeNode partici = new ProtocolTreeNode("participants");
        if (!sessionJids.isEmpty()) {
            SenderKeyDistributionMessage senderKeyDistributionMessage = axolotlManager_.GroupCreateSKMsg(groupId);
            ByteString senderKeySerial = ByteString.copyFrom(senderKeyDistributionMessage.serialize());
            for (String jid :  sessionJids) {
                String recepid = jid;
                int index =recepid.indexOf("@");
                if (index != -1) {
                    recepid = recepid.substring(0, index);
                }
                WhatsMessage.WhatsAppMessage.Builder sendKeyMessage = SerializeSenderKeyDistributionMessageToProtobuf(groupId, senderKeySerial);
                CiphertextMessage cipherText = axolotlManager_.Encrypt(recepid, sendKeyMessage.build().toByteArray());
                ProtocolTreeNode encNode = new ProtocolTreeNode("enc");
                encNode.AddAttribute(new StanzaAttribute("v", "2"));
                encNode.SetData(cipherText.serialize());
                if (!StringUtil.isEmpty(mediaType)) {
                    encNode.AddAttribute(new StanzaAttribute("mediatype", mediaType));
                }
                switch (cipherText.getType()) {
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
                ProtocolTreeNode participant = new ProtocolTreeNode("participant");
                participant.AddAttribute(new StanzaAttribute("jid",jid));
                participant.AddChild(encNode);
                partici.AddChild(participant);
            }
        }

        //组装消息
        ProtocolTreeNode msg = new ProtocolTreeNode("message");
        msg.AddAttribute(new StanzaAttribute("id", iqId));
        msg.AddAttribute(new StanzaAttribute("type", messageType));
        msg.AddAttribute(new StanzaAttribute("to", groupId));
        msg.AddAttribute(new StanzaAttribute("t", String.valueOf(System.currentTimeMillis() / 1000)));

        //添加group 消息
        ProtocolTreeNode encNode = new ProtocolTreeNode("enc");
        encNode.AddAttribute(new StanzaAttribute("v", "2"));
        encNode.AddAttribute(new StanzaAttribute("type", "skmsg"));
        if (!StringUtil.isEmpty(mediaType)) {
            encNode.AddAttribute(new StanzaAttribute("mediatype", mediaType));
        }
        encNode.SetData(axolotlManager_.GroupEncrypt(groupId, message));
        msg.AddChild(encNode);
        msg.AddChild(partici);
        AddTask(msg);
    }

    WhatsMessage.WhatsAppMessage.Builder SerializeSenderKeyDistributionMessageToProtobuf(String groupId, ByteString senderKeySerial) {
        WhatsMessage.WhatsAppMessage.Builder builder = WhatsMessage.WhatsAppMessage.newBuilder();
        WhatsMessage.WhatsAppSenderKeyDistributionMessage.Builder senderKeyBuilder = builder.getSenderKeyDistributionMessageBuilder();
        senderKeyBuilder.setGroupId(groupId);
        senderKeyBuilder.setAxolotlSenderKeyDistributionMessage(senderKeySerial);
        return builder;
    }

    String SendToGroup(String jid, byte[] message, String messageType, String mediaType, String iqId) {
        SenderKeyRecord senderKeyRecord = axolotlManager_.LoadSenderKey(jid);
        if (senderKeyRecord.isEmpty()) {
            //获取群信息
            InnerGetGroupInfo(jid, new HandleGetGroupInfo(message, messageType, mediaType,iqId));
        } else {
            try {
                SendToGroupWithSessions(jid, new LinkedList<>(), message, messageType, mediaType, iqId);
            } catch (UntrustedIdentityException e) {
                e.printStackTrace();
            } catch (NoSessionException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }
        }
        return iqId;
    }





    String GetGroupInfo(String jid) {
        return InnerGetGroupInfo(jid, null);
    }

    String InnerGetGroupInfo(String jid, NodeCallback callback) {
        ProtocolTreeNode iq = new ProtocolTreeNode("iq");
        iq.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        iq.AddAttribute(new StanzaAttribute("type", "get"));
        iq.AddAttribute(new StanzaAttribute("to", jid));
        iq.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));

        ProtocolTreeNode query = new ProtocolTreeNode("query");
        query.AddAttribute(new StanzaAttribute("request", "interactive"));
        iq.AddChild(query);
        return AddTask(iq, callback);
    }


    String SendToContact(String jid, byte[] message, String messageType, String mediaType, String iqId) {
        String recepid = jid;
        int index =recepid.indexOf("@");
        if (index != -1) {
            recepid = recepid.substring(0, index);
        }
        CiphertextMessage ciphertextMessage = null;
        try {
            ciphertextMessage = axolotlManager_.Encrypt(recepid, message);
            return SendEncMessage(jid ,ciphertextMessage.serialize(), ciphertextMessage.getType(),messageType, mediaType, iqId);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
        return iqId;
    }

    String SendEncMessage(String jid, byte[] cipherText, int encType, String messageType, String mediaType, String iqId) {
        ProtocolTreeNode msg = new ProtocolTreeNode("message");
        if (StringUtil.isEmpty(iqId)) {
            iqId = GenerateIqId();
        }
        msg.AddAttribute(new StanzaAttribute("id", iqId));
        msg.AddAttribute(new StanzaAttribute("type", messageType));
        msg.AddAttribute(new StanzaAttribute("to", jid));
        msg.AddAttribute(new StanzaAttribute("t", String.valueOf(System.currentTimeMillis() / 1000)));

        //enc node
        ProtocolTreeNode encNode = new ProtocolTreeNode("enc");
        encNode.AddAttribute(new StanzaAttribute("v", "2"));
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

    private GorgeousEngineDelegate delegate_ = null;
    String GenerateIqId() {
        return String.format("%s:%d", idPrex_, iqidIndex_.incrementAndGet());
    }
    String idPrex_ = UUID.randomUUID().toString().replaceAll("-", "").substring(0,28);
    AtomicInteger iqidIndex_ = new AtomicInteger(0);
    String cdnAuthKey_;
    String cdnHost_;
}
