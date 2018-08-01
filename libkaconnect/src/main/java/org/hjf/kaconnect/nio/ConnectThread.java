package org.hjf.kaconnect.nio;

import android.support.annotation.NonNull;


import org.hjf.util.log.LogUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * 常连接重新连接线程，自带重试机制
 */
final class ConnectThread extends Thread {

    /**
     * 主机地址和端口
     */
    private String host;
    private int port;
    /**
     * 线程运行状态
     */
    boolean isRunning = false;
    private boolean needDestroy = false;
    private boolean isPreWorkOk = false;
    private int tryNum = 1;

    /**
     * 线程阻塞工具
     */
    private OnNioTcpConnectStatueChangedListener onTcpConnectStatueChangedListener;

    ConnectThread(@NonNull OnNioTcpConnectStatueChangedListener onTcpConnectStatueChangedListener) {
        this.onTcpConnectStatueChangedListener = onTcpConnectStatueChangedListener;
    }

    @Override
    @Deprecated
    public void run() {
        super.run();
        isRunning = true;

        do {
            Selector selector = null; // 通道管理器
            SocketChannel socketChannel = null;   // Socket通道
            try {
                selector = Selector.open(); // 通道管理器
                socketChannel = SocketChannel.open();   // Socket通道
                socketChannel.configureBlocking(false); // 设置非阻塞
                // 将通道channel注册到管理器selector上，并告诉selector关注该chanel的连接事件
                // Channel、Selector一起使用时，Channel必须是非阻塞模式
                socketChannel.register(selector, SelectionKey.OP_CONNECT);

                // 客户端发起连接
                // 该方法执行后并没有实现连接，需要调用channel.finishConnect()才能完成连接
                socketChannel.connect(new InetSocketAddress(host, port));

                // 阻塞线程，直到至少有一个Channel在注册事件上准备就绪
                LogUtil.d("ConnectThread wait.");
                selector.select(getThreadSleepTimeMilliseconds(tryNum++));
                LogUtil.d("ConnectThread notified.");

                // 遍历通道管理器中各就绪通道中的就绪事件
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    // 建立连接就绪，完成连接建立
                    if (key.isConnectable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        // 如果Socket通道正在连接，则完成连接操作
                        if (sc.isConnectionPending()) {
                            sc.finishConnect();
                            // 连接建立成功
                            isPreWorkOk = true;
                            onTcpConnectStatueChangedListener.onConnected(selector, socketChannel);
                        }
                    }

                    // 删除正在处理的Key
                    keys.remove(key);
                }
            }
            // 发生异常错误
            catch (IOException e) {
                LogUtil.e(e.getMessage());
                onTcpConnectStatueChangedListener.onError(e);
                // 出现异常的情况下，对通道和通道管理器进行关闭操作
                if (selector != null) {
                    try {
                        selector.close();
                    } catch (IOException e1) {
                        LogUtil.e(e.getMessage());
                    }
                }
                if (socketChannel != null) {
                    try {
                        socketChannel.close();
                    } catch (IOException e1) {
                        LogUtil.e(e.getMessage());
                    }
                }
            }
        } while (!needDestroy && !isPreWorkOk);

    }

    /**
     * 开启任务
     */
    @Override
    public synchronized void start() {
        if (isRunning) {
            return;
        }
        super.start();
    }


    @Override
    public void destroy() {
        this.needDestroy = true;
        this.onTcpConnectStatueChangedListener = null;
    }

    /**
     * 重置重新连接尝试时间间隔
     */
    void resetReconnectTime() {
        needDestroy = false;
        tryNum = 1;
    }

    /**
     * 获取线程睡眠时间
     *
     * @param tryNum 尝试次数
     * @return 线程睡眠时间
     */
    private int getThreadSleepTimeMilliseconds(int tryNum) {
        if (tryNum < 5) {
            return 10 * 1000;
        } else if (tryNum < 10) {
            return 20 * 1000;
        } else if (tryNum < 20) {
            return 30 * 1000;
        }
        return 60 * 1000;
    }

    /**
     * 设置连接信息
     *
     * @param host 服务器主机地址
     * @param port 端口
     */
    public void setConnectInfo(String host, int port) {
        this.host = host;
        this.port = port;
    }
}
