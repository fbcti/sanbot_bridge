package com.sanbot.opensdk.function.beans.headmotion;

public class AbsoluteAngleHeadMotion
{
    public static final byte ACTION_VERTICAL = 1;
    public static final byte ACTION_HORIZONTAL = 2;

    private final byte action;
    private final int angle;

    public AbsoluteAngleHeadMotion(byte action, int angle)
    {
        this.action = action;
        this.angle = angle;
    }

    public byte getAction()
    {
        return action;
    }

    public int getAngle()
    {
        return angle;
    }
}
