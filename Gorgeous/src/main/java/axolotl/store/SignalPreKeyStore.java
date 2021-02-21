package axolotl.store;

import axolotl.AxolotlManager;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.util.KeyHelper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedList;
import java.util.List;

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

    public void setAsSent(LinkedList<Integer> sentIds) {
        axolotlManager_.SetAutoCommit(false);
        for (int i=0; i< sentIds.size(); i++) {
            try {
                PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("UPDATE prekeys SET sent_to_server = ?, upload_timestamp=? WHERE prekey_id = ?");
                preparedStatement.setInt(1, 1);
                preparedStatement.setLong(2, System.currentTimeMillis() / 1000);
                preparedStatement.setInt(3, sentIds.get(i));
                preparedStatement.execute();
            }
            catch (Exception e){
                Log.e(TAG, e.getMessage());
            }
        }

        axolotlManager_.Commit();
    }

    public LinkedList<PreKeyRecord> LoadUnSendPreKey() {
        LinkedList<PreKeyRecord> results = new LinkedList<>();
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("SELECT record FROM prekeys WHERE sent_to_server is NULL or sent_to_server = 0");
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()) {
                results.add(new PreKeyRecord(rs.getBytes(1)));
            }
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return  results;
    }

    public int getPendingPreKeysCount() {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("SELECT count(*) FROM prekeys");
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return  0;
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

    public int getMaxPreKeyId() {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("SELECT max(prekey_id) FROM prekeys");
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()) {
                return rs.getInt(1);
            }
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return 1;
    }

    public List<PreKeyRecord> generatePreKeyAndStore(int startId, int count) {
        //生成812个
        List<PreKeyRecord> preKeyRecords = KeyHelper.generatePreKeys(startId + 1, count);
        //保存到数据库
        axolotlManager_.SetAutoCommit(false);

        for (PreKeyRecord record : preKeyRecords) {
           storePreKey(record.getId(), record);
        }

        axolotlManager_.Commit();
        return preKeyRecords;
    }
}
