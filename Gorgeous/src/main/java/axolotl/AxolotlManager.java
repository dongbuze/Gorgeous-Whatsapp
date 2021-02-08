package axolotl;

import axolotl.store.*;
import com.sun.org.apache.bcel.internal.generic.RET;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

import java.io.File;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class AxolotlManager {
    private java.sql.Connection axolotlManager_ = null;
    SignalIdentityKeyStore identityKeyStore_ = null;
    SignalPreKeyStore preKeyStore_ = null;
    SignalSessionStore sessionStore_ = null;
    SignalSignedPreKeyStore signedPreKeyStore_ = null;
    SignalSenderKeyStore senderKeyStore_ = null;
    SignalFastRatchetSenderKeyStore fastRatchetSenderKeyStore_ = null;
    ConfigStore configStore_ = null;


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
        }
        catch (Exception e){
            System.err.println(e.getMessage());
        }
    }

    public SignalIdentityKeyStore GetIdentityKeyStore() {
        return identityKeyStore_;
    }

    public SignalPreKeyStore GetPreKeyStore() {
        return preKeyStore_;
    }

    public SignalSessionStore GetSessionStore() {
        return sessionStore_;
    }

    public SignalSignedPreKeyStore GetSignedPreKeyStore() {
        return signedPreKeyStore_;
    }

    public  SignalSenderKeyStore GetSenderKeyStore() {
        return senderKeyStore_;
    }

    public SignalFastRatchetSenderKeyStore GetFastRatchetSenderKeyStore() {
        return fastRatchetSenderKeyStore_;
    }

    public ConfigStore GetConfigStore() {
        return configStore_;
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
}
