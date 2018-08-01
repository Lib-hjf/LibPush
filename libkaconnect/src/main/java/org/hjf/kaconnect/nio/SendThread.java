package org.hjf.kaconnect.nio;

import android.support.annotation.NonNull;

import org.hjf.util.log.LogUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Vector;

/**
 * 发送线程
 */
final class SendThread extends Thread {

    private boolean isRunning = false;
    private SocketChannel socketChannel;
    private Vector<ByteBuffer> sendPacketQueue = new Vector<>();

    SendThread(@NonNull SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    @Override
    public void run() {
        super.run();

        while (isRunning) {

            // 没有要发送的数据包时，使线程阻塞
            synchronized (this) {
                try {
                    if (sendPacketQueue.size() == 0) {
                        LogUtil.d("SendThread wait");
                        this.wait();
                        LogUtil.d("SendThread notified");
                    }

                    // 处理发送任务
                    Iterator<ByteBuffer> iterator = sendPacketQueue.iterator();
                    while (iterator.hasNext()) {
                        ByteBuffer buffer = iterator.next();
                        iterator.remove();
                        while (buffer.hasRemaining()) {
                            try {
                                socketChannel.write(buffer);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    LogUtil.e(e.getMessage());
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
        this.sendPacketQueue.clear();
        this.sendPacketQueue = null;
        // 关闭SocketChannel通道连接
        if (this.socketChannel != null) {
            try {
                this.socketChannel.close();
            } catch (IOException e) {
                LogUtil.e(e.getMessage());
            }
            this.socketChannel = null;
        }
    }


    /**
     * 添加要发送的任务
     * 每次添加都尝试激活阻塞线程
     */
    void addPushData(ByteBuffer buffer) {
        synchronized (this) {
            LogUtil.d("SendThread add push Task.");
            if (this.sendPacketQueue != null) {
                this.sendPacketQueue.add(buffer);
            }
            this.notify();
        }
    }
}
