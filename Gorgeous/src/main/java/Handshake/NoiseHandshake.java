package Handshake;

import Env.DeviceEnv;
import ProtocolTree.ProtocolTreeNode;
import Util.GorgeoesLooper;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.proxy.Socks5ProxyHandler;
import jni.NoiseJni;
import jni.ProtocolNodeJni;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.UntypedStateMachine;
import org.squirrelframework.foundation.fsm.UntypedStateMachineBuilder;
import org.squirrelframework.foundation.fsm.annotation.StateMachineParameters;
import org.squirrelframework.foundation.fsm.impl.AbstractUntypedStateMachine;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.KeyHelper;

import javax.crypto.BadPaddingException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class NoiseHandshake {
    public interface HandshakeNotify {
        public void OnConnected(byte[] serverPublicKey);
        public void OnDisconnected(String desc);
        public void OnPush(ProtocolTreeNode node);
    }

    public static class Proxy {
        public String server;
        public int port;
        public String userName;
        public String password;
    }

    //接收tcp 数据
    class ClientHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            OnChannelRead(ctx, msg);
        }

        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                throws Exception {
        }
    }

    void OnChannelRead(ChannelHandlerContext ctx, Object msg) {
        receiveBuf_.writeBytes((ByteBuf)msg);
        int readableBytes = receiveBuf_.readableBytes();
        if (readableBytes < 3) {
            //是否接收完头部数据
            return;
        }
        byte[] lenBuffer = new byte[3];
        receiveBuf_.getBytes(0, lenBuffer);
        int bodyLen = HandshakeUtil.BodyBytesToLen(lenBuffer);
        if (readableBytes < 3 + bodyLen) {
            //判断是否接收完body
            return;
        }
        byte[] body = new byte[bodyLen];
        receiveBuf_.skipBytes(3);
        receiveBuf_.readBytes(body);
        receiveBuf_.discardReadBytes();
        GorgeoesLooper.Instance().PostTask(() -> {
            HandleSegment(body);
        });
    }


    void HandleSegment(byte[] body) {
        if (handshakeStateMachine_.getCurrentState() == HandshakeXXState.WaitFinish) {
            HandleXXServerHello(body);
        } else if (handshakeStateMachine_.getCurrentState() == HandshakeIKState.Finish) {
            HandleIKServerHello(body);
        } else {
            HandleReceivePacket(body);
        }
    }

    //HandshakeXX StateMachine
    enum HandshakeXXEvent {
        SendClientHello,
        HandleServerHello,
        SendClientFinish,
        Notify
    }

    enum HandshakeXXState {
        Init,
        WaitFinish,
        Finish,
        ChannelReady
    }

    @StateMachineParameters(stateType= HandshakeXXState.class, eventType=HandshakeXXEvent.class, contextType=NoiseHandshake.class)
    static class HandshakeXXStateMachine extends AbstractUntypedStateMachine {
        protected void FromInitToWaitServerResponse(HandshakeXXState from, HandshakeXXState to, HandshakeXXEvent event, NoiseHandshake context) {
            System.out.println("Transition from '"+from+"' to '"+to+"' on event '"+event+
                    "' with context '"+context+"'.");
            context.SendClientHello();
        }

        protected void Finish(HandshakeXXState from, HandshakeXXState to, HandshakeXXEvent event, NoiseHandshake context) {
            System.out.println("Transition from '"+from+"' to '"+to+"' on event '"+event+
                    "' with context '"+context+"'.");
            context.HandshakeXXFinish();
        }

        protected void Notify(HandshakeXXState from, HandshakeXXState to, HandshakeXXEvent event, NoiseHandshake context) {
            System.out.println("Transition from '"+from+"' to '"+to+"' on event '"+event+
                    "' with context '"+context+"'.");
            context.NotifyConnect();
        }
    }

    void SendClientHello() {
        //1) 获取一个32 字节的公钥
        byte[] ephemeral_public_buf = NoiseJni.WriteMessage(noiseHandshakeState_, null);
        //2) 构造一个 client hello
        DeviceEnv.HandshakeMessage.Builder builder = DeviceEnv.HandshakeMessage.newBuilder();
        DeviceEnv.ClientHello.Builder clientHello = DeviceEnv.ClientHello.newBuilder();
        clientHello.setEphemeral(ByteString.copyFrom(ephemeral_public_buf));
        builder.setClientHello(clientHello);
        //3) 发送数据
        WriteSegment(builder.build().toByteArray()).addListener(future -> {
            if (future.isSuccess()) {
                //等待服务器回包，这里不需要修改状态
            } else {
                NotifyDisconnect(future.toString());
            }
        });
    }

    void HandshakeXXFinish() {
        //1) 构造发送的payload
        byte[] payload = CreateFullPayload();
        //2) 加密数据
        byte[] message = NoiseJni.WriteMessage(noiseHandshakeState_, payload) ;
        //43 构造client finish
        DeviceEnv.HandshakeMessage.Builder clientFinish = DeviceEnv.HandshakeMessage.newBuilder();
        clientFinish.getClientFinishBuilder().setStatic(ByteString.copyFrom(message, 0, 48));
        clientFinish.getClientFinishBuilder().setPayload(ByteString.copyFrom(message, 48, message.length - 48));
        //5 发送数据
        WriteSegment(clientFinish.build().toByteArray()).addListener(future -> {
            if (future.isSuccess()) {
                //获取加解密秘钥
                handshakeStateMachine_.fire(HandshakeXXEvent.Notify, this);
            } else {
                NotifyDisconnect(future.toString());
            }
        });
    }


    void HandleXXServerHello(byte[] body) {
        try {
            DeviceEnv.HandshakeMessage serverHello = DeviceEnv.HandshakeMessage.parseFrom(body);
            if (!serverHello.hasServerHello()) {
                NotifyDisconnect("hasServerHello no");
                return;
            }
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
            //流转下一次状态
            handshakeStateMachine_.fire(HandshakeXXEvent.SendClientFinish, this);
        } catch (InvalidProtocolBufferException e) {
            NotifyDisconnect(e.getLocalizedMessage());
        }
    }

    //HandshakeIK StateMachine
    enum HandshakeIKEvent {
        SendPayload,
        HandleServerHello,
        Notify
    }

    enum HandshakeIKState {
        Init,
        Finish,
        ChannelReady
    }

    @StateMachineParameters(stateType= HandshakeIKState.class, eventType=HandshakeIKEvent.class, contextType=NoiseHandshake.class)
    static class HandshakeIKStateMachine extends AbstractUntypedStateMachine {
        protected void FromInitToWaitServerResponse(HandshakeIKState from, HandshakeIKState to, HandshakeIKEvent event, NoiseHandshake context) {
            System.out.println("Transition from '"+from+"' to '"+to+"' on event '"+event+
                    "' with context '"+context+"'.");
            context.SendPayload();
        }

        protected void Notify(HandshakeIKState from, HandshakeIKState to, HandshakeIKEvent event, NoiseHandshake context) {
            System.out.println("Transition from '"+from+"' to '"+to+"' on event '"+event+
                    "' with context '"+context+"'.");
            context.NotifyConnect();
        }
    }

    void HandleIKServerHello(byte[] body) {
        try {
            DeviceEnv.HandshakeMessage serverHello = DeviceEnv.HandshakeMessage.parseFrom(body);
            if (!serverHello.hasServerHello()) {
                NotifyDisconnect("hasServerHello no");
                return;
            }
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
            //流转下一次状态
            handshakeStateMachine_.fire(HandshakeIKEvent.Notify, this);
        } catch (InvalidProtocolBufferException e) {
            NotifyDisconnect(e.getLocalizedMessage());
        }
    }

    void SendPayload() {
        //1) 构造payload
        byte[] payload = CreateFullPayload();
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
        WriteSegment(builder.build().toByteArray()).addListener(future -> {
            if (future.isSuccess()) {
                //等待服务器回包
            } else {
                NotifyDisconnect(future.toString());
            }
        });
    }

    void HandleReceivePacket(byte[] body) {
        byte[] recvBuffer = NoiseJni.Decrypt(noiseHandshakeState_, body);
        if (recvBuffer.length > 0) {
            ProtocolTreeNode node = ProtocolTreeNode.FromXml(ProtocolNodeJni.BytesToXml(recvBuffer));
            if (null != notify_) {
                notify_.OnPush(node);
            }
        }
    }

    static EventLoopGroup eventGroup_ = new NioEventLoopGroup();
    static final String TAG = NoiseHandshake.class.getSimpleName();
    ByteBuf receiveBuf_ = Unpooled.buffer();
    Channel socketChannel_;
    HandshakeNotify notify_;
    Proxy proxy_;
    long noiseHandshakeState_ = 0;
    UntypedStateMachine handshakeStateMachine_;
    Env.DeviceEnv.AndroidEnv env_;
    byte[] publicServerKey_ = null;

    public NoiseHandshake(HandshakeNotify notify, Proxy proxy) {
        notify_ = notify;
        proxy_ = proxy;
    }

    //开始进行noise 握手， 为了方便同步收发数据，这里简单在一个线程进行
    public void StartNoiseHandShake(Env.DeviceEnv.AndroidEnv env) {
        String server = HandshakeConfig.s_server[new Random().nextInt(HandshakeConfig.s_server.length)];
        Log.i(TAG, "选择服务器:" + server);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventGroup_)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        if (proxy_ != null) {
                            ch.pipeline().addFirst(new Socks5ProxyHandler(new InetSocketAddress(proxy_.server, proxy_.port), proxy_.userName, proxy_.password));
                        }
                        ch.pipeline().addLast(new ClientHandler());
                    }
                });
        ChannelFuture future = bootstrap.connect(server, 443);
        //ChannelFuture future = bootstrap.connect("127.0.0.1", 18001);
        future.addListener(connectFuture -> {
            if (connectFuture.isSuccess()) {
                env_ = env;
                //连接成功开始走handshake流程
                HandleNoiseHandshake();
                socketChannel_.closeFuture().addListener(closeFuture -> {
                    NotifyDisconnect(closeFuture.toString());
                });
            } else {
                NotifyDisconnect(connectFuture.toString());
            }
        });
        socketChannel_ = future.channel();
    }

    public void StopNoiseHandShake() {
        if (socketChannel_ != null) {
            socketChannel_.close();
        }
    }

    public String SendNode (ProtocolTreeNode node) {
        GorgeoesLooper.Instance().PostTask(()->{
            try {
                byte[] data = ProtocolNodeJni.XmlToBytes(node.toString());
                byte[] cipherText = NoiseJni.Encrypt(noiseHandshakeState_, data);
                WriteSegment(cipherText);
            }
            catch (Exception e) {
            }
        });
        return node.IqId();
    }

    void HandleNoiseHandshake() throws IOException, NoSuchAlgorithmException, ShortBufferException, BadPaddingException {
        //连接成功,发送初始化信息
        byte[] routingInfo = env_.getEdgeRoutingInfo().toByteArray();
        ChannelFuture future =  socketChannel_.writeAndFlush(Unpooled.copiedBuffer(NoiseJni.InitData(routingInfo)));
        future.addListener(sendFuture -> {
            if (sendFuture.isSuccess()) {
                if (!env_.hasServerStaticPublic() || env_.getServerStaticPublic() == null) {
                    HandshakeXX();
                } else {
                    HandshakeIK();
                }
            } else {
                NotifyDisconnect(sendFuture.toString());
            }
        });
    }



    void HandshakeXX() throws InterruptedException, IOException {
        Log.d(TAG, "start HandshakeXX");
        noiseHandshakeState_ = NoiseJni.CreateInstance();
        //开始握手
        NoiseJni.StartHandshakeXX(noiseHandshakeState_, env_.getClientStaticKeyPair().getStrPrivateKey().toByteArray(), env_.getClientStaticKeyPair().getStrPubKey().toByteArray());

        //组装状态机
        UntypedStateMachineBuilder builder = StateMachineBuilderFactory.create(HandshakeXXStateMachine.class);
        builder.externalTransition().from(HandshakeXXState.Init).to(HandshakeXXState.WaitFinish).on(HandshakeXXEvent.SendClientHello).callMethod("FromInitToWaitServerResponse");
        builder.externalTransition().from(HandshakeXXState.WaitFinish).to(HandshakeXXState.Finish).on(HandshakeXXEvent.SendClientFinish).callMethod("Finish");
        builder.externalTransition().from(HandshakeXXState.Finish).to(HandshakeXXState.ChannelReady).on(HandshakeXXEvent.Notify).callMethod("Notify");
        handshakeStateMachine_ = builder.newStateMachine(HandshakeXXState.Init);
        handshakeStateMachine_.start();
        handshakeStateMachine_.fire(HandshakeXXEvent.SendClientHello, this);
    }

    void HandshakeIK() {
        Log.d(TAG, "start HandshakeIK");
        noiseHandshakeState_ = NoiseJni.CreateInstance();
        NoiseJni.StartHandshakeIK(noiseHandshakeState_, env_.getClientStaticKeyPair().getStrPrivateKey().toByteArray(),env_.getClientStaticKeyPair().getStrPubKey().toByteArray(),env_.getServerStaticPublic().toByteArray());
        //组装状态机
        UntypedStateMachineBuilder builder = StateMachineBuilderFactory.create(HandshakeIKStateMachine.class);
        builder.externalTransition().from(HandshakeIKState.Init).to(HandshakeIKState.Finish).on(HandshakeIKEvent.SendPayload).callMethod("FromInitToWaitServerResponse");
        builder.externalTransition().from(HandshakeIKState.Finish).to(HandshakeIKState.ChannelReady).on(HandshakeIKEvent.Notify).callMethod("Notify");
        handshakeStateMachine_ = builder.newStateMachine(HandshakeIKState.Init);
        handshakeStateMachine_.start();
        handshakeStateMachine_.fire(HandshakeIKEvent.SendPayload, this);
    }

    ChannelFuture WriteSegment(byte[] data) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBytes(HandshakeUtil.GenerateDataHead(data.length));
        buffer.writeBytes(data);
        return socketChannel_.writeAndFlush(buffer);
    }

    void NotifyDisconnect(String detail) {
        GorgeoesLooper.Instance().PostTask(()->{
            if (null != notify_) {
                notify_.OnDisconnected(detail);
            }
        });
    }

    void NotifyConnect() {
        NoiseJni.Split(noiseHandshakeState_);
        GorgeoesLooper.Instance().PostTask(()->{
            if (null != notify_) {
                notify_.OnConnected(publicServerKey_);
            }
        });
    }

    boolean CheckCertificate(byte[] payload)  {
        try {
            DeviceEnv.NoiseCertificate certificate = DeviceEnv.NoiseCertificate.parseFrom(ByteBuffer.wrap(payload));
            DeviceEnv.CertificateDetails details = DeviceEnv.CertificateDetails.parseFrom(certificate.getDetails());
            assert details.getIssuer() == "WhatsAppLongTerm1";
           return Curve.verifySignature(new IdentityKey(HandshakeConfig.PUBLIC_KEY, 0).getPublicKey() , certificate.getDetails().toByteArray(), certificate.getSignature().toByteArray());
        }
        catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return false;
    }

    byte[] CreateFullPayload() {
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
