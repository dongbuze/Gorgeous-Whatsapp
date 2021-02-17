package Handshake;

import Env.DeviceEnv;
import ProtocolTree.ProtocolTreeNode;
import com.google.protobuf.ByteString;
import jni.ProtocolNodeJni;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.KeyHelper;

import jni.NoiseJni;
import javax.crypto.BadPaddingException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.LinkedTransferQueue;


public class NoiseHandshake {
    public interface HandshakeNotify {
        public void OnConnected(byte[] serverPublicKey);
        public void OnDisconnected(String desc);
        public void OnPush(ProtocolTreeNode node);
    }

    Socket socket_ = null;
    private ReadSocketStream readStream_;
    private WriteSocketStream writeStream_;

    static final String TAG = NoiseHandshake.class.getSimpleName();

    //发送和接收线程
    Thread recvThread_;
    LinkedTransferQueue<ProtocolTreeNode> recv_queue_;

    Thread sendThread_;
    LinkedTransferQueue<ProtocolTreeNode> send_queue_;

    long noiseHandshakeState_ = 0;

    HandshakeNotify notify_;
    Proxy proxy_;

    public NoiseHandshake(HandshakeNotify notify, Proxy proxy) {
        notify_ = notify;
        proxy_ = proxy;
    }

    public void Disconnect() {
        notify_ = null;
        try {
            if (null != send_queue_) {
                send_queue_.put(null);
            }
            if (null != recv_queue_) {
                recv_queue_.put(null);
            }
            if (socket_ != null) {
                socket_.close();
                socket_ = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (noiseHandshakeState_ != 0) {
                NoiseJni.DestroyInstance(noiseHandshakeState_);
                noiseHandshakeState_ = 0;
            }
        }
    }

    void NoiseHandShakeProc(Env.DeviceEnv.AndroidEnv env) throws IOException, NoSuchAlgorithmException, ShortBufferException, BadPaddingException {
        //随机选择一个域名, tcp 连接
        String server = HandshakeConfig.s_server[new Random().nextInt(HandshakeConfig.s_server.length)];
        Log.i(TAG, "选择服务器:" + server);
        if (null != proxy_){
            socket_ = new Socket(proxy_);
        } else {
            socket_ = new Socket();
        }

        socket_.connect(new InetSocketAddress(server, 443));

        InputStream read = socket_.getInputStream();
        OutputStream write = socket_.getOutputStream();

        byte[] routingInfo = env.getEdgeRoutingInfo().toByteArray();
        write.write(NoiseJni.InitData(routingInfo));

        readStream_ = new ReadSocketStream(read);
        writeStream_ = new WriteSocketStream(write);
        //开始noise 握手
        if (!env.hasServerStaticPublic() || env.getServerStaticPublic() == null) {
            HandshakeXX(env);
        } else {
            HandshakeIK(env);
        }
        if (notify_ != null){
            notify_.OnConnected(publicServerKey_);
        }
        //启动接收线程，回调
        recv_queue_ = new LinkedTransferQueue<>();
        recvThread_ = new Thread(() -> {
            while (true) {
                try {
                    ProtocolTreeNode node = recv_queue_.take();
                    if (null != notify_) {
                        notify_.OnPush(node);
                    }
                }
                catch (Exception e) {
                    Log.i(TAG, "接收线程退出:" + e.getLocalizedMessage());
                }
            }
        });
        recvThread_.start();
        //启动发送线程
        send_queue_ = new LinkedTransferQueue<>();
        sendThread_ = new Thread(() -> {
            while (true) {
                try {
                    ProtocolTreeNode node = send_queue_.take();
                    byte[] data = ProtocolNodeJni.XmlToBytes(node.toString());
                    byte[] cipherText = NoiseJni.Encrypt(noiseHandshakeState_, data);
                    writeStream_.write(cipherText);
                }
                catch (Exception e){
                    Log.i(TAG, "发送线程退出:" + e.getLocalizedMessage());
                }
            }
        });
        sendThread_.start();
        LoopRecvSegment();
    }

    Thread noiseHandShakeThread_;
    public void StartNoiseHandShake(Env.DeviceEnv.AndroidEnv env) {
        noiseHandShakeThread_ = new Thread(() -> {
            try {
                NoiseHandShakeProc(env);
            } catch (Exception e) {
                if (null != notify_) {
                    notify_.OnDisconnected(e.getLocalizedMessage());
                }
            }
        });
        noiseHandShakeThread_.start();
    }

    public String SendNode (ProtocolTreeNode node) {
        send_queue_.add(node);
        return  node.IqId();
    }

    private void HandshakeXX(Env.DeviceEnv.AndroidEnv env) throws IOException, NoSuchAlgorithmException, ShortBufferException, BadPaddingException {
        Log.d(TAG, "start HandshakeXX");
        //注册完之后第一次登陆
        noiseHandshakeState_ = NoiseJni.CreateInstance();
        //开始握手
        int state = NoiseJni.StartHandshakeXX(noiseHandshakeState_, env.getClientStaticKeyPair().getStrPrivateKey().toByteArray(), env.getClientStaticKeyPair().getStrPubKey().toByteArray());


        int currentAction = NoiseJni.GetAction(noiseHandshakeState_);
        assert currentAction == HandshakeConfig.WRITE_MESSAGE;
        {
            //发送client hello
            //1) 获取一个32 字节的公钥
            byte[] ephemeral_public_buf = NoiseJni.WriteMessage(noiseHandshakeState_, null);
            //2) 构造一个 client hello
            DeviceEnv.HandshakeMessage.Builder builder = DeviceEnv.HandshakeMessage.newBuilder();
            DeviceEnv.ClientHello.Builder clientHello = DeviceEnv.ClientHello.newBuilder();
            clientHello.setEphemeral(ByteString.copyFrom(ephemeral_public_buf));
            builder.setClientHello(clientHello);
            //3) 发送数据
            writeStream_.write(builder.build().toByteArray());
            Log.d(TAG, "send client hello");
        }
        {
            //接收服务器回包
            DeviceEnv.HandshakeMessage serverHello = ReadServerHello();
            if (!serverHello.hasServerHello()) {
                Log.e(TAG, "server hello is empty");
                throw new IOException("has no server hello");
            }
            Log.d(TAG, "recv server:" + serverHello.toString());
            currentAction = NoiseJni.GetAction(noiseHandshakeState_);;
            assert currentAction == HandshakeConfig.READ_MESSAGE;
            byte[] ephemerial = serverHello.getServerHello().getEphemeral().toByteArray();
            byte[] staticBuffer = serverHello.getServerHello().getStatic().toByteArray();
            byte[] serverPayload = serverHello.getServerHello().getPayload().toByteArray();
            byte[] message = new byte[ephemerial.length + staticBuffer.length + serverPayload.length];
            System.arraycopy(ephemerial,0, message, 0 , ephemerial.length);
            System.arraycopy(staticBuffer, 0, message, ephemerial.length, staticBuffer.length);
            System.arraycopy(serverPayload,0, message, ephemerial.length + staticBuffer.length, serverPayload.length);

            byte[] payload = NoiseJni.ReadMessage(noiseHandshakeState_, message) ;
            //校验证书，这步骤可以不做
            CheckCertificate(payload);
            publicServerKey_ = NoiseJni.GetServerPublicKey(noiseHandshakeState_);
        }
        {
            Log.d(TAG, "send client finish");
            //发送client finish
            //1) 检查一下action 状态
            currentAction = NoiseJni.GetAction(noiseHandshakeState_);;
            assert currentAction == HandshakeConfig.WRITE_MESSAGE;
            //2) 构造发送的payload
            byte[] payload = CreateFullPayload(env);
            //3) 加密数据
            byte[] message = NoiseJni.WriteMessage(noiseHandshakeState_, payload) ;
            //4 构造client finish
            DeviceEnv.HandshakeMessage.Builder clientFinish = DeviceEnv.HandshakeMessage.newBuilder();
            clientFinish.getClientFinishBuilder().setStatic(ByteString.copyFrom(message, 0, 48));
            clientFinish.getClientFinishBuilder().setPayload(ByteString.copyFrom(message, 48, message.length - 48));
            //5 发送数据
            writeStream_.write(clientFinish.build().toByteArray());
        }

        {
            currentAction = NoiseJni.GetAction(noiseHandshakeState_);;
            assert currentAction == HandshakeConfig.SPLIT;
            //获取加解密秘钥
            NoiseJni.Split(noiseHandshakeState_);
        }

        {
            //握手完成
            currentAction = NoiseJni.GetAction(noiseHandshakeState_);;
            assert currentAction == HandshakeConfig.COMPLETE ;
        }
    }

    void LoopRecvSegment() throws IOException, BadPaddingException ,ShortBufferException {
        while (true) {
            byte[] date = ReadSegment();
            byte[] recvBuffer = NoiseJni.Decrypt(noiseHandshakeState_, date);
            if (recvBuffer.length > 0) {
                ProtocolTreeNode node = ProtocolTreeNode.FromXml(ProtocolNodeJni.BytesToXml(recvBuffer));
               if (null != notify_) {
                   notify_.OnPush(node);
               }
                Log.i(TAG, node.toString());
            }
        }
    }

    byte[] publicServerKey_ = null;

    private void HandshakeIK(Env.DeviceEnv.AndroidEnv env) throws IOException, NoSuchAlgorithmException, ShortBufferException, BadPaddingException {
        Log.d(TAG, "start HandshakeIK");
        noiseHandshakeState_ =  NoiseJni.CreateInstance();
        NoiseJni.StartHandshakeIK(noiseHandshakeState_, env.getClientStaticKeyPair().getStrPrivateKey().toByteArray(),env.getClientStaticKeyPair().getStrPubKey().toByteArray(),env.getServerStaticPublic().toByteArray());
        int currentAction = NoiseJni.GetAction(noiseHandshakeState_);;
        assert currentAction == HandshakeConfig.WRITE_MESSAGE;
        {
            //发送payload
            //1) 构造payload
            byte[] payload = CreateFullPayload(env);
            //2) 加密 payload
            byte[] message = NoiseJni.WriteMessage(noiseHandshakeState_, payload);
            //3) 构造 client hello
            DeviceEnv.HandshakeMessage.Builder builder = DeviceEnv.HandshakeMessage.newBuilder();
            DeviceEnv.ClientHello.Builder clientHello = DeviceEnv.ClientHello.newBuilder();
            clientHello.setEphemeral(ByteString.copyFrom(message, 0, 32));
            clientHello.setStatic(ByteString.copyFrom(message, 32 ,48));
            clientHello.setPayload(ByteString.copyFrom(message, 32+48, message.length - 32 -48));
            builder.setClientHello(clientHello);
            //4) 发送数据
            writeStream_.write(builder.build().toByteArray());
        }
        {
            //接受server hello
            DeviceEnv.HandshakeMessage serverHello = ReadServerHello();
            if (!serverHello.hasServerHello()) {
                throw new IOException("hasServerHello no");
            }
            Log.d(TAG, "recv server:" + serverHello.toString());
            currentAction = NoiseJni.GetAction(noiseHandshakeState_);;
            assert currentAction == HandshakeConfig.READ_MESSAGE;
            byte[] ephemerial = serverHello.getServerHello().getEphemeral().toByteArray();
            byte[] staticBuffer = serverHello.getServerHello().getStatic().toByteArray();
            byte[] serverPayload = serverHello.getServerHello().getPayload().toByteArray();
            byte[] message = new byte[ephemerial.length + staticBuffer.length + serverPayload.length];
            System.arraycopy(ephemerial,0, message, 0 , ephemerial.length);
            System.arraycopy(staticBuffer, 0, message, ephemerial.length, staticBuffer.length);
            System.arraycopy(serverPayload,0, message, ephemerial.length + staticBuffer.length, serverPayload.length);
            byte[] payload = NoiseJni.ReadMessage(noiseHandshakeState_, message);
            if (payload.length == 0) {
                //需要清理serverkey
                publicServerKey_ = null;
            }
        }
        {
            currentAction = NoiseJni.GetAction(noiseHandshakeState_);;
            assert currentAction == HandshakeConfig.SPLIT;
            //获取加解密秘钥
            NoiseJni.Split(noiseHandshakeState_);
        }

        {
            //握手完成
            currentAction = NoiseJni.GetAction(noiseHandshakeState_);;
            assert currentAction == HandshakeConfig.COMPLETE ;
        }
    }

    private DeviceEnv.HandshakeMessage ReadServerHello() throws IOException {
        byte[] body = ReadSegment();
        if (null == body) {
            throw  new IOException("read data error 2");
        }
        DeviceEnv.HandshakeMessage serverHello = DeviceEnv.HandshakeMessage.parseFrom(body);
        return serverHello;
    }

    byte[] ReadSegment() throws IOException{
        byte[] lenBuffer = readStream_.ReadData(3);
        if (null == lenBuffer) {
            throw  new IOException("read data error");
        }
        int bodyLen = HandshakeUtil.BodyBytesToLen(lenBuffer);
        return readStream_.ReadData(bodyLen);
    }

    boolean CheckCertificate(byte[] payload)  {
        try {
            DeviceEnv.NoiseCertificate certificate = DeviceEnv.NoiseCertificate.parseFrom(ByteBuffer.wrap(payload));
            DeviceEnv.CertificateDetails details = DeviceEnv.CertificateDetails.parseFrom(certificate.getDetails());
            assert details.getIssuer() == "WhatsAppLongTerm1";
            boolean checkSignature = Curve.verifySignature(new IdentityKey(HandshakeConfig.PUBLIC_KEY, 0).getPublicKey() , certificate.getDetails().toByteArray(), certificate.getSignature().toByteArray());
            assert checkSignature;
        }
        catch (Exception e) {

        }
        return false;
    }

    byte[] CreateFullPayload(Env.DeviceEnv.AndroidEnv env_) {
        DeviceEnv.ClientPayload.Builder clientPayload = DeviceEnv.ClientPayload.newBuilder();
        clientPayload.setUsername(Long.valueOf(env_.getFullphone()));
        clientPayload.setPassive(env_.getPassive());
        clientPayload.setUserAgent(env_.getUserAgent());
        clientPayload.setPushName(env_.getPushname());
        clientPayload.setSessionId(KeyHelper.getRandomSequence(Integer.MAX_VALUE));
        clientPayload.setShortConnect(false);
        clientPayload.setConnectType(DeviceEnv.ConnectType.CELLULAR_UNKNOWN);
        clientPayload.setConnectReason(DeviceEnv.ConnectReason.USER_ACTIVATED);
        return clientPayload.build().toByteArray();
    }
}
