package com.sanbot.opensdk.function.unit;

import android.support.annotation.NonNull;

import com.sanbot.opensdk.beans.OperationResult;

public class ZigbeeManager
{
    public void setZigbeeListener(ZigbeeListener zigbeeListener) {}

    public OperationResult addWhiteList(String s) { return new OperationResult(); }

    public OperationResult clearWhiteList() { return new OperationResult(); }

    public OperationResult deleteDevice(String s) { return new OperationResult(); }

    public OperationResult deleteWhiteList(String s) { return new OperationResult(); }

    public OperationResult getNotifyReadyStatus() { return new OperationResult(); }

    public OperationResult getWhiteList() { return new OperationResult(); }

    public OperationResult getZigbeeList() { return new OperationResult(); }

    public OperationResult sendByteCommand(byte[] b) { return new OperationResult(); }

    public OperationResult sendCommand(String s) { return new OperationResult(); }

    public OperationResult setAllowJoinTime(int i) { return new OperationResult(); }

    public OperationResult switchWhtieList(boolean b) { return new OperationResult(); }

    public static class ZigbeeListener
    {
        public void notifyWhiteList(@NonNull String s) {}

        public void notifyStatusChange(@NonNull String s) {}

        public void notifyInfo(@NonNull String s) {}
    }
}
