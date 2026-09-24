package com.sanbot.opensdk.function.beans.headmotion;

public class LocateAbsoluteAngleHeadMotion
{
    public static final byte ACTION_NO_LOCK = 0;
    public static final byte ACTION_HORIZONTAL_LOCK = 1;
    public static final byte ACTION_VERTICAL_LOCK = 2;
    public static final byte ACTION_BOTH_LOCK = 3;

    private byte action;
    private int hAngle;
    private int vAngle;

    public LocateAbsoluteAngleHeadMotion(byte action, int hAngle, int vAngle)
    {
        this.action = action;
        this.hAngle = hAngle;
        this.vAngle = vAngle;
    }

    public byte getAction()
    {
        return action;
    }

    public int getHAngle()
    {
        return hAngle;
    }

    public int getVAngle()
    {
        return vAngle;
    }
}