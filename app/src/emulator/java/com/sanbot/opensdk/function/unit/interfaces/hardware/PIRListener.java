package com.sanbot.opensdk.function.unit.interfaces.hardware;

public interface PIRListener extends HardWareListener
{
    void onPIRCheckResult(boolean detected, int sensorId);
}
