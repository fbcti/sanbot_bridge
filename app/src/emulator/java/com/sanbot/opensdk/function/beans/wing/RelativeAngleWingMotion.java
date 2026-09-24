package com.sanbot.opensdk.function.beans.wing;

public class RelativeAngleWingMotion
{
    public static final byte PART_BOTH = 0;
    public static final byte PART_LEFT = 1;
    public static final byte PART_RIGHT = 2;
    public static final byte ACTION_UP = 1;
    public static final byte ACTION_DOWN = 2;

    private final byte part;
    private final int speed;
    private final byte action;
    private final int angle;

    public RelativeAngleWingMotion(byte part, int speed, byte action, int angle)
    {
        this.part = part;
        this.speed = speed;
        this.action = action;
        this.angle = angle;
    }

    public byte getPart()
    {
        return part;
    }

    public int getSpeed()
    {
        return speed;
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
