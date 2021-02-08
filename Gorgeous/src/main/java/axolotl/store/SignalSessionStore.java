package axolotl.store;

import axolotl.AxolotlManager;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SignalSessionStore implements SessionStore {
    static final String TAG = "SignalSessionStore";
    AxolotlManager axolotlManager_;


    public SignalSessionStore(AxolotlManager axolotlManager) {
        axolotlManager_ = axolotlManager;
    }

    @Override
    public SessionRecord loadSession(SignalProtocolAddress address) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("select record from sessions where recipient_id = ? AND device_id = ?");
            preparedStatement.setString(1, address.getName());
            preparedStatement.setInt(2, address.getDeviceId());
            ResultSet result = preparedStatement.executeQuery();
            if (result.next()) {
                return new SessionRecord(result.getBytes(1));
            }
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        try {
            List<Integer> subDevice = new ArrayList<>();
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("SELECT device_id from sessions WHERE recipient_id = ?");
            preparedStatement.setString(1, name);
            ResultSet result = preparedStatement.executeQuery();
            while (result.next()) {
                subDevice.add(result.getInt(1));
            }
            return subDevice;
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord record) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("INSERT OR REPLACE INTO sessions (recipient_id, device_id, record, timestamp) VALUES(?,?, ?,?)");
            preparedStatement.setString(1, address.getName());
            preparedStatement.setInt(2, address.getDeviceId());
            preparedStatement.setBytes(3, record.serialize());
            preparedStatement.setLong(4, System.currentTimeMillis() / 1000);
            preparedStatement.execute();
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("select COUNT(*) from sessions where recipient_id = ? AND device_id = ?");
            preparedStatement.setString(1, address.getName());
            preparedStatement.setInt(2, address.getDeviceId());
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
    public void deleteSession(SignalProtocolAddress address) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("delete from sessions where recipient_id = ? AND device_id = ?");
            preparedStatement.setString(1, address.getName());
            preparedStatement.setInt(2, address.getDeviceId());
            preparedStatement.execute();
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void deleteAllSessions(String name) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("delete from sessions where recipient_id = ?");
            preparedStatement.setString(1, name);
            preparedStatement.execute();
        }
        catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }
}
