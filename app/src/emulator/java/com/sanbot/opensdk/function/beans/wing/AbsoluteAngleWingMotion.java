package com.sanbot.opensdk.function.beans.wing;

public class AbsoluteAngleWingMotion
{
    public static final byte PART_BOTH = 10;
    public static final byte PART_LEFT = 1;
    public static final byte PART_RIGHT = 2;

    private final byte part;
    private final int speed;
    private final int angle;

    public AbsoluteAngleWingMotion(byte part, int speed, int angle)
    {
        this.part = part;
        this.speed = speed;
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

    public int getAngle()
    {
        return angle;
    }
}
