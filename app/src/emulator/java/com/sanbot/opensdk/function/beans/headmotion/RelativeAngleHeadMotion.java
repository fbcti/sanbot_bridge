package com.sanbot.opensdk.function.beans.headmotion;

public class RelativeAngleHeadMotion
{
    public static final byte ACTION_STOP = 0;
    public static final byte ACTION_UP = 1;
    public static final byte ACTION_DOWN = 2;
    public static final byte ACTION_LEFT = 3;
    public static final byte ACTION_RIGHT = 4;
    public static final byte ACTION_LEFTUP = 5;
    public static final byte ACTION_RIGHTUP = 6;
    public static final byte ACTION_LEFTDOWN = 7;
    public static final byte ACTION_RIGHTDOWN = 8;

    private final byte action;
    private final int angle;

    public RelativeAngleHeadMotion(byte action, int angle)
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
