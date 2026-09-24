package com.sanbot.opensdk.function.beans;

public class SpeakOption
{
    public static final int LAG_CHINESE = 0;
    public static final int LAG_ENGLISH_US = 1;
    public static final int LAG_SPANISH_SPAIN = 2;
    public static final int LAG_FRENCH_FRANCE = 3;
    public static final int LAG_PORTUGUESE_PORTUGAL = 4;
    public static final int LAG_ARABIC_INTERNATIONAL = 5;
    public static final int LAG_JAPANESE = 6;
    public static final int LAG_ITALIAN = 7;
    public static final int LAG_POLISH = 8;
    public static final int LAG_TURKISH = 9;
    public static final int LAG_DANISH = 10;
    public static final int LAG_KOREAN = 11;
    public static final int LAG_GERMAN = 12;

    int languageType;

    int speed;

    int intonation;

    public void setLanguageType(int languageType) { this.languageType = languageType; }

    public void setSpeed(int speed) { this.speed = speed; }

    public void setIntonation(int intonation) { this.intonation = intonation; }

    public int getLanguageType() { return languageType; }

    public int getSpeed() { return speed; }

    public int getIntonation() { return intonation; }
}
