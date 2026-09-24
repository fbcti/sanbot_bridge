package com.sanbot.opensdk.setting;

import android.support.annotation.Nullable;

import org.jetbrains.annotations.Contract;

public class Setting
{
    public static void putString(android.content.ContentResolver resolver, String key, String value) {}

    @Nullable
    @Contract(pure = true)
    public static String getString(android.content.ContentResolver resolver, String key)
    {
        return null;
    }

}
