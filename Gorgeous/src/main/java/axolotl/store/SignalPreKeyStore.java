package axolotl.store;

import axolotl.AxolotlManager;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SignalPreKeyStore implements PreKeyStore {
    static final String TAG = "SignalPreKeyStore";
    AxolotlManager axolotlManager_;

    public SignalPreKeyStore(AxolotlManager axolotlManager) {
        axolotlManager_ = axolotlManager;
    }

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("select record  from prekeys where prekey_id =?");
            preparedStatement.setInt(1, preKeyId);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return new PreKeyRecord(rs.getBytes(1));
            }
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("INSERT INTO prekeys (prekey_id, sent_to_server, record, direct_distribution) VALUES(?, ?, ?, ?)");
            preparedStatement.setInt(1, preKeyId);
            preparedStatement.setBoolean(2, false);
            preparedStatement.setBytes(3, record.serialize());
            preparedStatement.setLong(4,0);
            preparedStatement.execute();
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("select COUNT(*)  from prekeys where prekey_id =?");
            preparedStatement.setInt(1, preKeyId);
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
    public void removePreKey(int preKeyId) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("delete from prekeys where prekey_id =?");
            preparedStatement.setInt(1, preKeyId);
            preparedStatement.execute();
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }
}
