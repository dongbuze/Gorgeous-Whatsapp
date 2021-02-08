package axolotl.store;

import axolotl.AxolotlManager;
import org.whispersystems.libsignal.groups.SenderKeyName;
import org.whispersystems.libsignal.groups.state.FastRatchetSenderKeyRecord;
import org.whispersystems.libsignal.groups.state.FastRatchetSenderKeyStore;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.logging.Log;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SignalFastRatchetSenderKeyStore implements FastRatchetSenderKeyStore {
    static final String TAG = "SignalFastRatchetSenderKeyStore";
    AxolotlManager axolotlManager_;
    public SignalFastRatchetSenderKeyStore(AxolotlManager axolotlManager) {
        axolotlManager_ = axolotlManager;
    }

    @Override
    public void storeFastRatchetSenderKey(SenderKeyName senderKeyName, FastRatchetSenderKeyRecord record) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("INSERT OR REPLACE INTO fast_ratchet_sender_keys (group_id, sender_id, device_id, record, timestamp) VALUES(?,?, ?,?,?)");
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
    public FastRatchetSenderKeyRecord loadFastRatchetSenderKey(SenderKeyName senderKeyName) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("select record from fast_ratchet_sender_keys where group_id = ? AND sender_id = ? AND device_id = ?");
            preparedStatement.setString(1, senderKeyName.getGroupId());
            preparedStatement.setString(2, senderKeyName.getSender().getName());
            preparedStatement.setInt(3, senderKeyName.getSender().getDeviceId());
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()){
                return new FastRatchetSenderKeyRecord(rs.getBytes(1));
            }
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return null;
    }
}
