package axolotl.store;

import axolotl.AxolotlManager;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;


public class SignalIdentityKeyStore implements IdentityKeyStore {
    AxolotlManager axolotlManager_;
    int localRegistrationId_ = -1;
    IdentityKeyPair identityKeyPair_;
    static final String TAG = "SignalIdentityKeyStore";
    public SignalIdentityKeyStore(AxolotlManager axolotlManager) {
        axolotlManager_ = axolotlManager;
        {
            try {
                ResultSet rs = axolotlManager_.GetStatement().executeQuery("SELECT registration_id, public_key, private_key FROM identities WHERE recipient_id = -1");
                if (rs.next()){
                    localRegistrationId_ =  rs.getInt("registration_id");
                    byte[] pubKey = rs.getBytes("public_key");
                    byte[] priKey = rs.getBytes("private_key");
                    identityKeyPair_ = new IdentityKeyPair(new IdentityKey(pubKey, 0), KeyHelper.decodePrivateKey(priKey));
                }
            }
            catch (Exception e){
                Log.e(TAG, e.getMessage());
            }
        }
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyPair_;
    }

    @Override
    public int getLocalRegistrationId() {
        return localRegistrationId_;
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        DeleteIdentity(address);
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("INSERT INTO identities (recipient_id, device_id, public_key, timestamp) VALUES(?, ?, ?, ?)");
            preparedStatement.setString(1, address.getName());
            preparedStatement.setInt(2, address.getDeviceId());
            preparedStatement.setBytes(3, identityKey.serialize());
            preparedStatement.setLong(4,System.currentTimeMillis() /1000);
            preparedStatement.execute();
            return true;
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return  false;
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        IdentityKey storeIdentityKey = getIdentity(address);
        if (null == storeIdentityKey){
            return  true;
        }
        return  storeIdentityKey.equals(identityKey);
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("SELECT public_key from identities where recipient_id =? and device_id=?");
            preparedStatement.setString(1, address.getName());
            preparedStatement.setInt(2, address.getDeviceId());
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()){
                return new IdentityKey(rs.getBytes("public_key"), 0);
            }
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    void  DeleteIdentity(SignalProtocolAddress address) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("delete from identities where recipient_id =? and device_id=?");
            preparedStatement.setString(1, address.getName());
            preparedStatement.setInt(2, address.getDeviceId());
            preparedStatement.execute();
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }
}
