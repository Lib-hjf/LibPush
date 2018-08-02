package org.hjf.kaconnect.nio;

import android.text.TextUtils;

import org.hjf.kaconnect.OnConnectStatusChangedListener;
import org.hjf.kaconnect.OnReceiveDataListener;
import org.hjf.log.LogUtil;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * 长连接客户端
 */
public class NioClient {

    /**
     * 单例对象
     */
    private static NioClient client;

    /**
     * 客户端状态
     */
    private boolean isConnect = false;

    /**
     * 发起连接操作的对象，内部带有失败重试策略
     */
    private ConnectThread connectThread;

    /**
     * 接收推送消息的线程
     */
    private ReceiveThread receiveThread;

    /**
     * 发送消息线程
     */
    private SendThread sendThread;

    /**
     * 主机地址和端口
     */
    private String host;
    private int port;

    /**
     * 手机ip地址
     */
    private String localIpAddress;

    /**
     * 回调对象
     */
    private OnReceiveDataListener onReceiveDataListener;
    private OnConnectStatusChangedListener onConnectStatusChangedListenerOut = null;
    private OnNioTcpConnectStatueChangedListener onTcpConnectStatueChangedListener = new OnNioTcpConnectStatueChangedListener() {


        @Override
        public void onConnected(Selector selector, SocketChannel socketChannel) {
            isConnect = true;
            LogUtil.d("长连接成功");
            NioClient.this.stopConnectThread();
            // 获取 ip
            InetAddress localAddress = socketChannel.socket().getLocalAddress();
            localIpAddress = localAddress.toString().split("/")[1];

            // 开启接收数据线程：在和服务端连接成功之后，需要注册SelectChannel的关注事件为读事件
            try {
                socketChannel.register(selector, SelectionKey.OP_READ);
            } catch (ClosedChannelException e) {
                if (NioClient.this.onConnectStatusChangedListenerOut!= null){
                    NioClient.this.onConnectStatusChangedListenerOut.onError(e);
                }
            }
            receiveThread = new ReceiveThread(selector, NioClient.this.onReceiveDataListener);
            receiveThread.start();

            // 开启发送数据线程
            sendThread = new SendThread(socketChannel);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LogUtil.e(e.toString());
            }
            sendThread.start();

            if (NioClient.this.onConnectStatusChangedListenerOut!= null){
                NioClient.this.onConnectStatusChangedListenerOut.onConnected();
            }
        }

        @Override
        public void onDisconnected() {
            isConnect = false;
            NioClient.this.stopConnectThread();
            if (NioClient.this.onConnectStatusChangedListenerOut!= null){
                NioClient.this.onConnectStatusChangedListenerOut.onDisconnected();
            }
        }

        @Override
        public void onError(Exception e) {
            isConnect = false;
            startConnectThread();
            if (NioClient.this.onConnectStatusChangedListenerOut!= null){
                NioClient.this.onConnectStatusChangedListenerOut.onError(e);
            }
        }
    };

    /**
     * 构造肥那个发
     */
    private NioClient() {

    }

    /**
     * 设置接受到消息后的通知对象
     */
    public NioClient setOnConnectStatusChangedListener(OnConnectStatusChangedListener listener) {
        this.onConnectStatusChangedListenerOut = listener;
        return this;
    }
    /**
     * 设置接受到消息后的通知对象
     */
    public NioClient setOnReceiveDataListener(OnReceiveDataListener onReceiveDataListener) {
        this.onReceiveDataListener = onReceiveDataListener;
        return this;
    }

    /**
     * 连接
     */
    public synchronized void connect(String host, int port) {
        this.host = host;
        this.port = port;
        this.startConnectThread();
    }

    /**
     * 开启长连接线程任务
     */
    private void startConnectThread() {
        // 丢失 主机地址和端口
        if (TextUtils.isEmpty(this.host) || this.port == 0) {
            return;
        }
        // 准备线程对象
        if (connectThread == null) {
            connectThread = new ConnectThread(onTcpConnectStatueChangedListener);
        }
        connectThread.setConnectInfo(this.host, this.port);
        // 长连接线程已经运行
        if (connectThread.isRunning) {
            connectThread.resetReconnectTime();
        }
        // 开始运行
        else {
            connectThread.start();
        }
    }

    /**
     * 断开连接
     */
    public synchronized void disconnect() {
        // 销毁连接线程
        this.stopConnectThread();
        // 销毁接收线程
        // 内部: 关闭/销毁通道管理器Selector
        if (receiveThread != null) {
            receiveThread.destroy();
            receiveThread = null;
        }
        // 销毁发送线程
        // 内部: 关闭/销毁SocketChannel通道连接
        if (sendThread != null) {
            sendThread.destroy();
            sendThread = null;
        }
        // 标记未连接状态
        isConnect = false;
    }

    /**
     * 销毁连接工具线程
     */
    private void stopConnectThread() {
        if (connectThread != null) {
            connectThread.destroy();
            connectThread = null;
        }
    }

    /**
     * 推送消息给云端服务器
     */
    public void pushData(ByteBuffer buffer) {
        if (this.isConnect()) {
            sendThread.addPushData(buffer);
        }
    }

    /**
     * App内发送事件消息，可用于跨组件转送数据
     */
    public void sendMessage(ByteBuffer buffer) {
        if (this.onReceiveDataListener != null) {
            this.onReceiveDataListener.onReceiveData(buffer.array());
        }
    }


    /**
     * 是否连接
     */
    public boolean isConnect() {
        return this.isConnect && this.receiveThread != null && this.receiveThread.isRunning;
    }

    /**
     * 获取手机ip地址
     */
    public String getLocalIpAddress() {
        return localIpAddress;
    }

    /**
     * 获取单例对象
     */
    public static NioClient getInstance() {
        if (client == null) {
            synchronized (NioClient.class) {
                if (client == null) {
                    client = new NioClient();
                }
            }
        }
        return NioClient.client;
    }


}
