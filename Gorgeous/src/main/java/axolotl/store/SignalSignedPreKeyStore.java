package axolotl.store;

import axolotl.AxolotlManager;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SignalSignedPreKeyStore implements SignedPreKeyStore {
    static final String TAG = "SignalSignedPreKeyStore";
    AxolotlManager axolotlManager_;

    public SignalSignedPreKeyStore(AxolotlManager axolotlManager) {
        axolotlManager_ = axolotlManager;
    }


    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("select record  from signed_prekeys where prekey_id =?");
            preparedStatement.setInt(1, signedPreKeyId);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return new SignedPreKeyRecord(rs.getBytes(1));
            }
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        try {
            List<SignedPreKeyRecord> results = new ArrayList<>();
            Statement statement = axolotlManager_.GetStatement();
            ResultSet rs = statement.executeQuery("select record  from signed_prekeys");
            while (rs.next()) {
                results.add(new SignedPreKeyRecord(rs.getBytes(1)));
            }
            return  results;
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    public SignedPreKeyRecord loadLatestSignedPreKey() {
        List<SignedPreKeyRecord> records = loadSignedPreKeys();
        if ((null == records) || (records.isEmpty())) {
            return null;
        }
        return records.get(records.size() -1 );
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("INSERT INTO signed_prekeys (prekey_id,  timestamp, record) VALUES(?,?,?)");
            preparedStatement.setInt(1, signedPreKeyId);
            preparedStatement.setLong(2, System.currentTimeMillis() / 1000);
            preparedStatement.setBytes(3, record.serialize());
            preparedStatement.execute();
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("select COUNT(*) from signed_prekeys where prekey_id =?");
            preparedStatement.setInt(1, signedPreKeyId);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) != 0;
            }
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return false;
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("delete from signed_prekeys where prekey_id = ?");
            preparedStatement.setInt(1, signedPreKeyId);
            preparedStatement.execute();
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }
}
