package org.hjf.kaconnect.nio;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * 长连接状态改变回调
 */
interface OnNioTcpConnectStatueChangedListener {

    void onConnected(Selector selector, SocketChannel socketChannel);

    void onDisconnected();

    void onError(Exception e);
}
