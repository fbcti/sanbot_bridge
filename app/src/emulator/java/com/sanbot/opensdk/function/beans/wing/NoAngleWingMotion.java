package com.sanbot.opensdk.function.beans.wing;

public class NoAngleWingMotion
{
    public static final byte PART_BOTH = 0;
    public static final byte PART_LEFT = 1;
    public static final byte PART_RIGHT = 2;
    public static final byte ACTION_UP = 1;
    public static final byte ACTION_DOWN = 2;
    public static final byte ACTION_RESET = 1;
    public static final byte ACTION_STOP = 2;

    private final byte part;
    private final int speed;
    private final byte action;

    public NoAngleWingMotion(byte part, int speed, byte action)
    {
        this.part = part;
        this.speed = speed;
        this.action = action;
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
}
