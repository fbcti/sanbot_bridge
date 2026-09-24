package com.sanbot.opensdk.function.beans.handmotion;

public class CombinationHandMotion
{
    public static final byte MOTION_OPEN_ARM = 1;
    public static final byte MOTION_RAISE_HAND = 2;
    public static final byte MOTION_CHEER = 3;
    public static final byte MOTION_WAVE = 4;
    public static final byte PART_BOTH = 0;
    public static final byte PART_LEFT = 1;
    public static final byte PART_RIGHT = 2;
    public static final byte ACTION_START = 1;

    public CombinationHandMotion(byte part, byte motion, byte action)
    {
    }
}
