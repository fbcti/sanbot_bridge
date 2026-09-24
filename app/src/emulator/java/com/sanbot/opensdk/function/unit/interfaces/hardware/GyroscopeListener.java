package com.sanbot.opensdk.function.unit.interfaces.hardware;

public interface GyroscopeListener extends HardWareListener
{
    void gyroscopeCheckResult(boolean gravityOk, boolean compassOk);

    void gyroscopeData(float yaw, float pitch, float roll);
}
