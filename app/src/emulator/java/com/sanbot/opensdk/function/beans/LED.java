package com.sanbot.opensdk.function.beans;

public final class LED
{
    public static final byte MODE_CLOSE = 1;
    public static final byte MODE_WHITE = 2;
    public static final byte MODE_RED = 3;
    public static final byte MODE_GREEN = 4;
    public static final byte MODE_PINK = 5;
    public static final byte MODE_PURPLE = 6;
    public static final byte MODE_BLUE = 7;
    public static final byte MODE_YELLOW = 8;
    public static final byte MODE_FLICKER_WHITE = 18;
    public static final byte MODE_FLICKER_RED = 19;
    public static final byte MODE_FLICKER_GREEN = 20;
    public static final byte MODE_FLICKER_PINK = 21;
    public static final byte MODE_FLICKER_PURPLE = 22;
    public static final byte MODE_FLICKER_BLUE = 23;
    public static final byte MODE_FLICKER_YELLOW = 24;
    public static final byte MODE_FLICKER_RANDOM = 25;

    public static final byte PART_ALL = 0;
    public static final byte PART_WHEEL = 1;
    public static final byte PART_LEFT_HAND = 2;
    public static final byte PART_RIGHT_HAND = 3;
    public static final byte PART_LEFT_HEAD = 4;
    public static final byte PART_RIGHT_HEAD = 5;

    private byte part;
    private byte mode;
    private byte delayTime;
    private byte randomCount;


    public LED(byte part, byte mode, byte delayTime, byte randomCount)
    {
        this.part = part;
        this.mode = mode;
        this.delayTime = delayTime;
        this.randomCount = randomCount;
    }

    public byte getPart() { return part; }

    public byte getMode() { return mode; }

    public byte getDelayTime() { return delayTime; }

    public byte getRandomCount() { return randomCount; }
}
