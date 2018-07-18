package org.hjf.kaconnect;

public interface OnConnectStatusChangedListener {

    void onConnected() ;

    void onDisconnected();

    void onError(Exception e);
}
