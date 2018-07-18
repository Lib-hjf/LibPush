package org.hjf.kaconnect;


/**
 * 收到推送数据
 */
public interface OnReceiveDataListener {

    /**
     * 云端push下来的数据
     */
    void onReceiveData(byte[] data);
}
