/**
 * @file        CommonUtils.java
 * @brief       Implements CommonUtils utility class.
 */
package com.fbcti.sanbot.bridge.util;

import android.support.annotation.NonNull;

import java.nio.charset.StandardCharsets;

/**
 * Helper class that implements a number of common static methods.
 */
public class CommonUtils
{
    /**
     * Copies the specified string to the byte array.
     *
     * @param   bytes           byte array to copy string to
     * @param   pos             position in byte array to copy string to
     * @param   value           string to copy
     */
    public static void copyStringToByteArray(@NonNull byte[] bytes, int pos, @NonNull String value)
    {
        byte[] b = value.getBytes(StandardCharsets.UTF_8);
        if (pos + b.length <= bytes.length) System.arraycopy(b, 0, bytes, pos, b.length);
    }

    /**
     * Copies the specified integer to the byte array
     *
     * @param   bytes           byte array to copy integer to
     * @param   pos             position in byte array to copy integer to
     * @param   value           integer to copy
     */
    public static void copyIntToByteArray(@NonNull byte[] bytes, int pos, int value)
    {
        bytes[pos  ] = (byte)(value & 0xFF);
        bytes[pos+1] = (byte)((value >> 8) & 0xFF);
        bytes[pos+2] = (byte)((value >> 16) & 0xFF);
        bytes[pos+3] = (byte)((value >> 24) & 0xFF);
    }

    /**
     * Copies the specified byte to the byte array
     *
     * @param   bytes           short array to copy integer to
     * @param   pos             position in byte array to copy integer to
     * @param   value           short to copy
     */
    public static void copyShortToByteArray(@NonNull byte[] bytes, int pos, int value)
    {
        bytes[pos  ] = (byte)(value & 0xFF);
        bytes[pos+1] = (byte)((value >> 8) & 0xFF);
    }
}