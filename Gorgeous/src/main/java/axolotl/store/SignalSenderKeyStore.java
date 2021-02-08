package axolotl.store;

import axolotl.AxolotlManager;
import org.whispersystems.libsignal.groups.SenderKeyName;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.groups.state.SenderKeyStore;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.SessionRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SignalSenderKeyStore implements SenderKeyStore {
    static final String TAG = "SignalSenderKeyStore";
    AxolotlManager axolotlManager_;

    public  SignalSenderKeyStore(AxolotlManager axolotlManager) {
        axolotlManager_ = axolotlManager;
    }

    @Override
    public void storeSenderKey(SenderKeyName senderKeyName, SenderKeyRecord record) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("INSERT OR REPLACE INTO sender_keys (group_id, sender_id, device_id, record, timestamp) VALUES(?,?, ?,?,?)");
            preparedStatement.setString(1, senderKeyName.getGroupId());
            preparedStatement.setString(2, senderKeyName.getSender().getName());
            preparedStatement.setInt(3, senderKeyName.getSender().getDeviceId());
            preparedStatement.setBytes(4, record.serialize());
            preparedStatement.setLong(5, System.currentTimeMillis() / 1000);
            preparedStatement.execute();
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public SenderKeyRecord loadSenderKey(SenderKeyName senderKeyName) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("select record from sender_keys where group_id = ? AND sender_id = ? AND device_id = ?");
            preparedStatement.setString(1, senderKeyName.getGroupId());
            preparedStatement.setString(2, senderKeyName.getSender().getName());
            preparedStatement.setInt(3, senderKeyName.getSender().getDeviceId());
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()){
                return new SenderKeyRecord(rs.getBytes(1));
            }
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return null;
    }
}
