package com.sanbot.opensdk.function.unit;

import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.LED;
import com.sanbot.opensdk.function.unit.interfaces.hardware.HardWareListener;

public class HardWareManager
{
    public void setOnHareWareListener(HardWareListener listener) {}


    public OperationResult setLED(LED led) { return new OperationResult(); }

    public OperationResult switchWhiteLight(boolean on)
    {
        return new OperationResult();
    }

    public OperationResult setWhiteLightLevel(int level)
    {
        return new OperationResult();
    }

    public OperationResult queryWhiteLightBrightness()
    {
        return new OperationResult();
    }

    public OperationResult queryGravityData() { return new OperationResult(); }

    public OperationResult queryBatteryValue()
    {
        return new OperationResult(1, "Emulator battery value", "100");
    }

    public OperationResult queryBatteryStatus()
    {
        return new OperationResult(1, "Emulator battery status", "charging");
    }

    public OperationResult queryPirStatus(int id)
    {
        return new OperationResult(1, "Emulator PIR status", "" + id);
    }
}