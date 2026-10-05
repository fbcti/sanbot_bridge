package com.sanbot.opensdk.function.beans.wheelmotion;

public class RelativeAngleWheelMotion
{
    public static final byte TURN_LEFT = 1;
    public static final byte TURN_RIGHT = 2;
    public static final byte TURN_STOP = 3;

    private final byte action;
    private final int speed;
    private final int angle;

    public RelativeAngleWheelMotion(byte action, int speed, int angle)
    {
        this.action = action;
        this.speed = speed;
        this.angle = angle;
    }

    public byte getAction() { return action; }

    public int getSpeed() { return speed; }

    public byte getLsbAngle() { return (byte)(angle & 0xFF); }

    public byte getMsbAngle() { return (byte)((angle >> 8) & 0xFF); }
}
