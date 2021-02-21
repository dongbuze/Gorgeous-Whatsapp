package axolotl;

import Env.DeviceEnv;
import Util.GorgeousLooper;
import axolotl.store.*;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.groups.GroupCipher;
import org.whispersystems.libsignal.groups.GroupSessionBuilder;
import org.whispersystems.libsignal.groups.SenderKeyName;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SenderKeyDistributionMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

import java.io.File;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class AxolotlManager {
    private java.sql.Connection axolotlManager_ = null;
    SignalIdentityKeyStore identityKeyStore_ = null;
    SignalPreKeyStore preKeyStore_ = null;
    SignalSessionStore sessionStore_ = null;
    SignalSignedPreKeyStore signedPreKeyStore_ = null;
    SignalSenderKeyStore senderKeyStore_ = null;
    SignalFastRatchetSenderKeyStore fastRatchetSenderKeyStore_ = null;
    ConfigStore configStore_ = null;
    HashMap<String, SessionCipher> sessionCipherHashMap = new HashMap<>();
    HashMap<SenderKeyName, GroupCipher> groupCipherHashMap = new HashMap<>();
    GroupSessionBuilder groupSessionBuilder_;
    String userName_;


    public AxolotlManager(String dbPath) {
        try{
            boolean dbExist = new File(dbPath).exists();
            //连接数据库
            String connectionPath = "jdbc:sqlite:" + dbPath;
            axolotlManager_ = DriverManager.getConnection(connectionPath);
            if (!dbExist) {
                //如果第一次安装创建数据表
                CrateTables();
                //初始化数据
                InitInstallData();
            }
            identityKeyStore_ = new SignalIdentityKeyStore(this);
            preKeyStore_ = new SignalPreKeyStore(this);
            sessionStore_ = new SignalSessionStore(this);
            signedPreKeyStore_ = new SignalSignedPreKeyStore(this);
            senderKeyStore_ = new SignalSenderKeyStore(this);
            fastRatchetSenderKeyStore_ = new SignalFastRatchetSenderKeyStore(this);
            configStore_ = new ConfigStore(this);
            groupSessionBuilder_ = new GroupSessionBuilder(senderKeyStore_);

            byte[] envBuffer =  GetBytesSetting("env");
            DeviceEnv.AndroidEnv.Builder builder = DeviceEnv.AndroidEnv.parseFrom(envBuffer).toBuilder();
            userName_ = builder.getFullphone();
        }
        catch (Exception e){
            System.err.println(e.getMessage());
        }
    }

    public void SetAutoCommit(boolean commit) {
        try {
            axolotlManager_.setAutoCommit(commit);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public void Commit() {
        try {
            axolotlManager_.commit();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public void Close(){
        try
        {
            if(axolotlManager_ != null) {
                axolotlManager_.close();
                axolotlManager_ = null;
            }
        }
        catch (Exception e){
        }
    }
    void CrateTables() {
        try {
            Statement statement = axolotlManager_.createStatement();
            //创建数据表
            statement.execute("CREATE TABLE IF NOT EXISTS identities (_id INTEGER PRIMARY KEY AUTOINCREMENT, recipient_id INTEGER, device_id INTEGER, registration_id INTEGER, public_key BLOB, private_key BLOB, next_prekey_id INTEGER, timestamp INTEGER)");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS identities_idx ON identities(recipient_id, device_id)");
            statement.execute("CREATE TABLE IF NOT EXISTS prekeys (_id INTEGER PRIMARY KEY AUTOINCREMENT, prekey_id INTEGER UNIQUE, sent_to_server BOOLEAN, record BLOB, direct_distribution BOOLEAN, upload_timestamp INTEGER)");
            statement.execute("CREATE TABLE IF NOT EXISTS prekey_uploads (_id INTEGER PRIMARY KEY AUTOINCREMENT, upload_timestamp INTEGER)");
            statement.execute("CREATE TABLE IF NOT EXISTS sessions (_id INTEGER PRIMARY KEY AUTOINCREMENT, recipient_id INTEGER, device_id INTEGER, record BLOB, timestamp INTEGER)");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS sessions_idx ON sessions(recipient_id, device_id)");
            statement.execute("CREATE TABLE IF NOT EXISTS signed_prekeys (_id INTEGER PRIMARY KEY AUTOINCREMENT, prekey_id INTEGER UNIQUE, timestamp INTEGER, record BLOB)");
            statement.execute("CREATE TABLE IF NOT EXISTS message_base_key (_id INTEGER PRIMARY KEY AUTOINCREMENT, msg_key_remote_jid TEXT NOT NULL, msg_key_from_me BOOLEAN NOT NULL, msg_key_id TEXT NOT NULL, recipient_id INTEGER, device_id INTEGER NOT NULL DEFAULT 0, last_alice_base_key BLOB NOT NULL, timestamp INTEGER)");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS message_base_key_idx ON message_base_key (msg_key_remote_jid, msg_key_from_me, msg_key_id, recipient_id, device_id)");
            statement.execute("CREATE TABLE IF NOT EXISTS sender_keys (_id INTEGER PRIMARY KEY AUTOINCREMENT, group_id TEXT NOT NULL, sender_id INTEGER NOT NULL, device_id INTEGER NOT NULL DEFAULT 0, record BLOB NOT NULL, timestamp INTEGER)");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS sender_keys_idx ON sender_keys (group_id, sender_id, device_id)");
            statement.execute("CREATE TABLE IF NOT EXISTS fast_ratchet_sender_keys (_id INTEGER PRIMARY KEY AUTOINCREMENT, group_id TEXT NOT NULL, sender_id INTEGER NOT NULL, device_id INTEGER NOT NULL DEFAULT 0, record BLOB NOT NULL)");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS fast_ratchet_sender_keys_idx ON fast_ratchet_sender_keys (group_id, sender_id, device_id)");
            //创建环境表
            statement.execute("CREATE TABLE IF NOT EXISTS settings(key text PRIMARY KEY,value text)");
        }
        catch (Exception e){

        }
    }

    public  Statement GetStatement() {
        try {
            return axolotlManager_.createStatement();
        }
        catch (Exception e){
            System.err.println(e.getMessage());
        }
        return null;
    }

    public PreparedStatement GetPreparedStatement(String preSql) {
        try {
            return axolotlManager_.prepareStatement(preSql);
        }
        catch (Exception e){
            System.err.println(e.getMessage());
        }
        return null;
    }

    void InitInstallData() {
        //初始化第一次安装的数据
        IdentityKeyPair identityKeyPair = InitIdentityData();
        if (null == identityKeyPair){
            System.err.println("create identityKeyPair failed");
            return;
        }
        InitSignedPreKeyData(identityKeyPair);
    }

    //初始化 用户标识数据表
    IdentityKeyPair InitIdentityData() {
        try {
            IdentityKeyPair identityKeyPair = KeyHelper.generateIdentityKeyPair();
            int registrationId  = KeyHelper.generateRegistrationId(true);
            //保存identity key pair
            PreparedStatement identityPreStatement = axolotlManager_.prepareStatement("INSERT OR REPLACE INTO identities(recipient_id, device_id, registration_id, public_key, private_key, next_prekey_id, timestamp) values(-1,0,?,?,?,?,?)");
            identityPreStatement.setInt(1, registrationId);
            identityPreStatement.setBytes(2, identityKeyPair.getPublicKey().serialize());
            identityPreStatement.setBytes(3, identityKeyPair.getPrivateKey().serialize());
            identityPreStatement.setInt(4, KeyHelper.getRandomSequence(16777214));
            identityPreStatement.setLong(5, System.currentTimeMillis() / 1000);
            identityPreStatement.execute();
            return identityKeyPair;
        }
        catch (Exception e){

        }
        return null;
    }

    //初始化signed pre key
    void  InitSignedPreKeyData(IdentityKeyPair identityKeyPair) {
        try {
            SignedPreKeyRecord signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, 0);
            PreparedStatement preStatement = axolotlManager_.prepareStatement("INSERT INTO signed_prekeys (prekey_id, timestamp, record) VALUES(?,?,?)");
            preStatement.setInt(1, signedPreKey.getId());
            preStatement.setLong(2, System.currentTimeMillis() / 1000);
            preStatement.setBytes(3, signedPreKey.serialize());
            preStatement.execute();
        }
        catch (Exception e){

        }
    }

    public List<PreKeyRecord> LevelPreKeys(boolean force) {
        int count =  preKeyStore_.getPendingPreKeysCount();
        if (force || count < 10) {
            int maxId = preKeyStore_.getMaxPreKeyId();
            return preKeyStore_.generatePreKeyAndStore(maxId, 812);
        }
        return null;
    }

    public void SetBytesSetting(String key, byte[] value) {
        configStore_.SetBytes(key ,value);
    }

    public byte[] GetBytesSetting(String key) {
        return configStore_.GetBytes(key);
    }

    public LinkedList<PreKeyRecord> LoadUnSendPreKey() {
        return preKeyStore_.LoadUnSendPreKey();
    }

    public void SetAsSent(LinkedList<Integer> sentIds) {
        preKeyStore_.setAsSent(sentIds);
    }

    public SignedPreKeyRecord LoadLatestSignedPreKey(boolean generate) {
        List<SignedPreKeyRecord> records = signedPreKeyStore_.loadSignedPreKeys();
        if ((null == records) || (records.isEmpty())) {
            if (generate) {
                return GenerateSignedPrekey();
            }
            return null;
        }
        return records.get(records.size() -1 );
    }


    public SignedPreKeyRecord GenerateSignedPrekey() {
        SignedPreKeyRecord preKeyRecord = LoadLatestSignedPreKey(false);
        int newSignedPrekeyId = 0;
        if (null != preKeyRecord) {
            newSignedPrekeyId = preKeyRecord.getId() + 1;
        }
        SignedPreKeyRecord result = null;
        try {
            result = KeyHelper.generateSignedPreKey(identityKeyStore_.getIdentityKeyPair(), newSignedPrekeyId);
            signedPreKeyStore_.storeSignedPreKey(result.getId(), result);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return result;
    }

    public SessionCipher GetSessionCipher(String receiptId) {
        GorgeousLooper.Instance().CheckThread();
        SessionCipher sessionCipher = sessionCipherHashMap.get(receiptId);
        if (sessionCipher == null) {
            SignalProtocolAddress address = new SignalProtocolAddress(receiptId, 0);
            sessionCipher = new SessionCipher(sessionStore_, preKeyStore_,signedPreKeyStore_, identityKeyStore_, address);
            sessionCipherHashMap.put(receiptId, sessionCipher);
        }
        return sessionCipher;
    }

    public GroupCipher GetGroupCipher(String groupId, String receiptId) {
        GorgeousLooper.Instance().CheckThread();
        SignalProtocolAddress address = new SignalProtocolAddress(receiptId, 0);
        SenderKeyName senderKeyName = new SenderKeyName(groupId, address);
        GroupCipher groupCipher = groupCipherHashMap.get(senderKeyName);
        if (null == groupCipher) {
            groupCipher = new GroupCipher(senderKeyStore_, senderKeyName);
            groupCipherHashMap.put(senderKeyName, groupCipher);
        }
        return groupCipher;
    }


    public CiphertextMessage Encrypt(String receiptId, byte[] message) throws UntrustedIdentityException {
        GorgeousLooper.Instance().CheckThread();
        SessionCipher cipher = GetSessionCipher(receiptId);
        //固定加一个padding
        byte[] paddingData = new byte[message.length + 1];
        System.arraycopy(message, 0, paddingData, 0, message.length);
        paddingData[paddingData.length - 1] = 1;
        return cipher.encrypt(paddingData);
    }

    public byte[] DecryptPKMsg(String senderId, byte[] data) throws InvalidVersionException, InvalidMessageException, InvalidKeyException, DuplicateMessageException, InvalidKeyIdException, UntrustedIdentityException, LegacyMessageException {
        GorgeousLooper.Instance().CheckThread();
        PreKeySignalMessage message = new PreKeySignalMessage(data);
        byte[] plaintext = GetSessionCipher(senderId).decrypt(message);
        int padding = plaintext[plaintext.length-1];

        byte[] result = new byte[plaintext.length - padding];
        System.arraycopy(plaintext, 0, result, 0, result.length);
        return result;
    }

    public byte[] DecryptMsg(String senderId, byte[] data) throws LegacyMessageException, InvalidMessageException, DuplicateMessageException, NoSessionException, UntrustedIdentityException {
        GorgeousLooper.Instance().CheckThread();
        SignalMessage message = new SignalMessage(data);
        byte[] plaintext = GetSessionCipher(senderId).decrypt(message);
        int padding = plaintext[plaintext.length-1];

        byte[] result = new byte[plaintext.length - padding];
        System.arraycopy(plaintext, 0, result, 0, result.length);
        return result;
    }

    public byte[] GroupEncrypt(String groupId, byte[] message) throws NoSessionException, InvalidKeyException {
        GorgeousLooper.Instance().CheckThread();
        GroupCipher cipher = GetGroupCipher(groupId, userName_);
        //固定加一个padding
        byte[] paddingData = new byte[message.length + 1];
        System.arraycopy(message, 0, paddingData, 0, message.length);
        paddingData[paddingData.length - 1] = 1;
        return cipher.encrypt(paddingData);
    }

    public byte[] GroupDecrypt(String groupId, String participantId, byte[] data) throws NoSessionException, DuplicateMessageException, InvalidMessageException, LegacyMessageException {
        GorgeousLooper.Instance().CheckThread();
        GroupCipher cipher = GetGroupCipher(groupId, participantId);
        byte[] plaintext = cipher.decrypt(data);
        int padding = plaintext[plaintext.length-1];

        byte[] result = new byte[plaintext.length - padding];
        System.arraycopy(plaintext, 0, result, 0, result.length);
        return result;
    }

    public SenderKeyDistributionMessage GroupCreateSKMsg(String groupId) {
        GorgeousLooper.Instance().CheckThread();
        SignalProtocolAddress address = new SignalProtocolAddress(userName_, 0);
        SenderKeyName senderKeyName = new SenderKeyName(groupId, address);
        return groupSessionBuilder_.create(senderKeyName);
    }

    public void GroupCreateSession(String groupId, String participantId, byte[] data) throws InvalidMessageException, LegacyMessageException {
        GorgeousLooper.Instance().CheckThread();
        SignalProtocolAddress address = new SignalProtocolAddress(participantId, 0);
        SenderKeyName senderKeyName = new SenderKeyName(groupId, address);
        groupSessionBuilder_.process(senderKeyName, new SenderKeyDistributionMessage(data));
    }

    public void CreateSession(String receiptId, PreKeyBundle bundle) throws UntrustedIdentityException, InvalidKeyException {
        SignalProtocolAddress address = new SignalProtocolAddress(receiptId, 0);
        SessionBuilder sessionBuilder = new SessionBuilder(sessionStore_,preKeyStore_, signedPreKeyStore_, identityKeyStore_ ,address);
        sessionBuilder.process(bundle);
    }

    public SenderKeyRecord LoadSenderKey(String groupId) {
        SignalProtocolAddress address = new SignalProtocolAddress(userName_, 0);
        SenderKeyName senderKeyName = new SenderKeyName(groupId, address);
        return senderKeyStore_.loadSenderKey(senderKeyName);
    }

    public int getLocalRegistrationId() {
        return identityKeyStore_.getLocalRegistrationId();
    }

    public IdentityKeyPair GetIdentityKeyPair() {
        return identityKeyStore_.getIdentityKeyPair();
    }

    public boolean ContainsSession(String receiptId) {
        return sessionStore_.containsSession(receiptId);
    }
}
