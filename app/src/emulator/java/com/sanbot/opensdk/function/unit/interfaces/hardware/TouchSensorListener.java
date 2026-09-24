package com.sanbot.opensdk.function.unit.interfaces.hardware;

public interface TouchSensorListener extends HardWareListener
{
    void onTouch(int sensorId);

    void onTouch(int sensorId, boolean touched);
}
