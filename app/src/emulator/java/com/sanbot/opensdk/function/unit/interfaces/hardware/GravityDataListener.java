package com.sanbot.opensdk.function.unit.interfaces.hardware;

public interface GravityDataListener extends HardWareListener
{
    void onGravityDataResult(float acceleration);
}
