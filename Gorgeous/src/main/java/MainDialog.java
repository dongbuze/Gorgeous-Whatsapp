import Env.DeviceEnv;
import Handshake.NoiseHandshake;
import ProtocolTree.*;
import axolotl.AxolotlManager;
import com.google.protobuf.ByteString;
import jni.NoiseJni;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.logging.SignalProtocolLogger;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

import javax.swing.*;
import java.awt.event.*;

public class MainDialog extends JDialog implements NoiseHandshake.HandshakeNotify, SignalProtocolLogger {
    private static final String TAG = MainDialog.class.getSimpleName();
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JButton signal_test;
    private JButton logout;

    NoiseHandshake noiseHandshake_;
    DeviceEnv.AndroidEnv.Builder envBuilder_;
    AxolotlManager axolotlManager_;


    public MainDialog() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
        }

        setLocationRelativeTo(null);
        SignalProtocolLoggerProvider.setProvider(this);
        System.loadLibrary("libNoiseJni");

        long instance = NoiseJni.CreateInstance();
        NoiseJni.DestroyInstance(instance);
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);


        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        signal_test.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                axolotlManager_ = new AxolotlManager(System.getProperty("user.dir") + "\\out\\axolotl.db");
                try{
                    byte[] envBuffer =  axolotlManager_.GetConfigStore().GetBytes("env");
                    envBuilder_ = DeviceEnv.AndroidEnv.parseFrom(envBuffer).toBuilder();
                    noiseHandshake_ = new NoiseHandshake(MainDialog.this, null);
                    noiseHandshake_.StartNoiseHandShake( envBuilder_.build());
                }
                catch (Exception e){
                    Log.w("noise" , e.getMessage());
                }
            }
        });
        logout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (null != noiseHandshake_) {
                    noiseHandshake_.Disconnect();
                }
            }
        });
    }

    private void onOK() {
        // add your code here
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public static void main(String[] args) {
        MainDialog dialog = new MainDialog();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }

    @Override
    public void OnConnected(byte[] serverPublicKey) {
        if (serverPublicKey != null) {
            envBuilder_.setServerStaticPublic(ByteString.copyFrom(serverPublicKey));
        } else {
            envBuilder_.clearServerStaticPublic();
        }

        axolotlManager_.GetConfigStore().SetBytes("env", envBuilder_.build().toByteArray());
        Log.i(TAG, "OnConnected:");
    }

    @Override
    public void OnPush(ProtocolTreeNode node) {

    }

    @Override
    public void OnDisconnected(String desc) {
        Log.e(TAG, "OnDisconnected:" + desc);
    }

    @Override
    public void log(int priority, String tag, String message) {
        System.out.println(tag);
        System.out.println(message);
    }
}
