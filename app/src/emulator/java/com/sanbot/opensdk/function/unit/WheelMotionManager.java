package com.sanbot.opensdk.function.unit;

import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.wheelmotion.DistanceWheelMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.RelativeAngleWheelMotion;

public class WheelMotionManager
{
    public interface WheelMotionListener
    {
        void onWheelStatus(String status);
    }

    public OperationResult doNoAngleMotion(NoAngleWheelMotion motion)
    {
        return new OperationResult();
    }

    public OperationResult doDistanceMotion(DistanceWheelMotion motion)
    {
        return new OperationResult();
    }

    public OperationResult doRelativeAngleMotion(RelativeAngleWheelMotion motion)
    {
        return new OperationResult();
    }

    public void setWheelMotionListener(WheelMotionListener listener)
    {
    }
}
