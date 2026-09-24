package com.sanbot.opensdk.function.beans;

public class WakeUpOption
{
    public static final String LAG_CHINESE_HK = "zh-HK";
    public static final String LAG_ENGLISH_US = "en";
    public static final String LAG_ARABIC_INTERNATIONAL = "ar";
    public static final String LAG_JAPANESE = "ja";
    public static final String LAG_ITALIAN = "it";
    public static final String LAG_POLISH = "pl";
    public static final String LAG_SPANISH_SPAIN = "es";
    public static final String LAG_DANISH = "da";
    public static final String LAG_TURKISH = "tr";
    public static final String LAG_KOREAN = "ko";
    public static final String LAG_GERMAN = "de";
    public static final String LAG_FRENCH_FRANCE = "fr";
    public static final String LAG_PORTUGUESE_PORTUGAL = "pt";

    private String languageType;

    public void setLanguageType(String languageType)
    {
        this.languageType = languageType;
    }

    public String getLanguageType()
    {
        return languageType;
    }
}
