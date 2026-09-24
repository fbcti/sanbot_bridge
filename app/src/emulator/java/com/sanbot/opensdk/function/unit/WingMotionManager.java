package com.sanbot.opensdk.function.unit;

import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.wing.AbsoluteAngleWingMotion;
import com.sanbot.opensdk.function.beans.wing.NoAngleWingMotion;
import com.sanbot.opensdk.function.beans.wing.RelativeAngleWingMotion;

public class WingMotionManager
{
    public OperationResult doNoAngleMotion(NoAngleWingMotion motion)
    {
        return new OperationResult();
    }

    public OperationResult doRelativeAngleMotion(RelativeAngleWingMotion motion)
    {
        return new OperationResult();
    }

    public OperationResult doAbsoluteAngleMotion(AbsoluteAngleWingMotion motion)
    {
        return new OperationResult();
    }
}
