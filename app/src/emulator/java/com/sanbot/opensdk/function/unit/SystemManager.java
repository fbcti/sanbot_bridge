package com.sanbot.opensdk.function.unit;

import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.EmotionsType;
import com.sanbot.opensdk.function.unit.interfaces.IDarlingListener;
import com.sanbot.opensdk.function.unit.interfaces.system.KeyStatusListener;
import com.sanbot.opensdk.function.unit.interfaces.system.ObstacleStatusListener;
import com.sanbot.opensdk.function.unit.interfaces.system.WheelObstacleStatusListener;

public class SystemManager
{
    public String getDeviceId() { return null; };

    public OperationResult showEmotion(EmotionsType emotion)
    {
        return new OperationResult();
    }

    public int getBatteryValue() { return 0; };

    public int getBatteryStatus() { return 0; };

    public String getMainServiceVersion() { return null; }

    public void setOnIDarlingListener(IDarlingListener listener)
    {
    }

    public void setOnObstacleStatusListener(ObstacleStatusListener listener)
    {

    }
    public void setOnWheelObstacleStatusListener(WheelObstacleStatusListener listener)
    {

    }

    public void setKeyStatusListener(KeyStatusListener listener)
    {

    }
}
