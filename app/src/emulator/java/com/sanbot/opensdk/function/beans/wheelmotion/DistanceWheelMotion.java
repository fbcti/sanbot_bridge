package com.sanbot.opensdk.function.beans.wheelmotion;

public class DistanceWheelMotion
{
    public static final byte ACTION_FORWARD_RUN = 1;
    public static final byte ACTION_STOP_RUN = 2;

    private final byte action;
    private final int speed;
    private final int distance;

    public DistanceWheelMotion(byte action, int speed, int distance)
    {
        this.action = action;
        this.speed = speed;
        this.distance = distance;
    }

    public byte getAction() { return action; }

    public int getSpeed() { return speed; }

    public byte getLsbDistance() { return (byte)(distance & 0xFF); }

    public byte getMsbDistance() { return (byte)((distance >> 8) & 0xFF); }
}
