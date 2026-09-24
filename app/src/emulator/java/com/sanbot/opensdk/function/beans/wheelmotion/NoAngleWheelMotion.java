package com.sanbot.opensdk.function.beans.wheelmotion;

public class NoAngleWheelMotion
{
    public static final byte ACTION_FORWARD = 1;
    public static final byte ACTION_BACK = 2;
    public static final byte ACTION_LEFT = 3;
    public static final byte ACTION_RIGHT = 4;
    public static final byte ACTION_TURN_LEFT = 5;
    public static final byte ACTION_TURN_RIGHT = 6;
    public static final byte ACTION_STOP = 7;
    public static final byte ACTION_STOP_TURN = 8;
    public static final byte ACTION_LEFT_TRANSLATION = 9;
    public static final byte ACTION_RIGHT_TRANSLATION = 10;
    public static final byte ACTION_LEFT_FORWARD = 11;
    public static final byte ACTION_RIGHT_FORWARD = 12;
    public static final byte ACTION_LEFT_BACK = 13;
    public static final byte ACTION_RIGHT_BACK = 14;
    public static final byte ACTION_RESET = 15;

    private final byte action;
    private final int speed;
    private final int duration;

    public NoAngleWheelMotion(byte action, int speed)
    {
        this(action, speed, 0);
    }

    public NoAngleWheelMotion(byte action, int speed, int duration)
    {
        this.action = action;
        this.speed = speed;
        this.duration = duration;
    }

    public byte getAction()
    {
        return action;
    }

    public int getSpeed()
    {
        return speed;
    }

    public int getDuration()
    {
        return duration;
    }
}
