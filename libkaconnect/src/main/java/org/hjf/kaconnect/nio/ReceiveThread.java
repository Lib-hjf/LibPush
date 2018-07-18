package org.hjf.kaconnect.nio;

import android.support.annotation.NonNull;

import org.hjf.kaconnect.OnReceiveDataListener;
import org.hjf.liblogx.LogUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

final class ReceiveThread extends Thread {

    boolean isRunning = false;
    private Selector selector;
    private OnReceiveDataListener onReceiveDataListener;
    private ByteBuffer buff = ByteBuffer.allocate(2048);

    ReceiveThread(@NonNull Selector selector, @NonNull OnReceiveDataListener onReceiveDataListener) {
        this.selector = selector;
        this.onReceiveDataListener = onReceiveDataListener;
    }

    @Override
    public void run() {
        super.run();
        while (isRunning) {

            try {
                LogUtil.d("ReceiveThread wait.");
                selector.select();
                LogUtil.d("ReceiveThread notified");
            } catch (IOException e) {
                LogUtil.e(e.getMessage());
            }

            // 遍历通道管理器中各就绪通道中的就绪事件，此处关注的是：SelectionKey.OP_READ 事件
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                // 2. 读取数据就绪，接收推送数据
                if (key.isReadable()) {
                    SocketChannel sc = (SocketChannel) key.channel();
                    buff.clear();
                    int count = 0;
                    try {
                        count = sc.read(buff);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (count < 0) {
                        continue;
                    }
                    // 接受数据并回传
                    if (onReceiveDataListener != null) {
                        onReceiveDataListener.onReceiveData(buff.array());
                    }
                }
            }
        }
    }

    @Override
    public synchronized void start() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        super.start();
    }

    @Override
    public void destroy() {
        this.isRunning = false;
        this.onReceiveDataListener = null;
        this.buff.clear();
        this.buff = null;
        // 关闭通道管理器
        if (this.selector != null) {
            try {
                this.selector.close();
            } catch (IOException e) {
                LogUtil.e(e.getMessage());
            }
            this.selector = null;
        }
    }
}
