package axolotl.store;

import axolotl.AxolotlManager;
import org.whispersystems.libsignal.logging.Log;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

public class ConfigStore {
    AxolotlManager axolotlManager_;

    public ConfigStore(AxolotlManager axolotlManager) {
        axolotlManager_ = axolotlManager;
    }

    public void SetSetting(String key, String value) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("INSERT OR REPLACE INTO settings(key, value) VALUES(?, ?)");
            preparedStatement.setString(1, key);
            preparedStatement.setString(2, value);
            preparedStatement.execute();
        }
        catch (Exception e){
        }
    }

    public String GetSetting(String key) {
        try {
            PreparedStatement preparedStatement = axolotlManager_.GetPreparedStatement("SELECT  value from settings where key = ?");
            preparedStatement.setString(1, key);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return  rs.getString("value");
            }
        }
        catch (Exception e){
        }
        return  "";
    }

    public void SetBytes(String key, byte[] value) {
        String b64Value = Base64.getEncoder().encodeToString(value);
        SetSetting(key, b64Value);
    }

    public byte[] GetBytes(String key) {
        String value = GetSetting(key);
        if (!value.isEmpty()) {
            return  Base64.getDecoder().decode(value);
        }
        return  null;
    }
}
