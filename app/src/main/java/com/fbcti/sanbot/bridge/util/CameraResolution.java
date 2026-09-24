/**
 * @file        CameraResolution.java
 * @brief       Implements CameraResolution class.
 */
package com.fbcti.sanbot.bridge.util;

import android.support.annotation.Nullable;

/**
 * Provides camera resolution names for given image dimensions.
 *
 * @version     1.0.001
 * @date        23 Aug 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public class CameraResolution
{
    /**
     * Returns the name of the specified camera resolution.
     *
     * @param   resolutionX     horizontal resolution
     * @param   resolutionY     vertical resolution
     *
     * @return  name of camera resolution, or @c null if resolution is not supported
     */
    @Nullable
    public static String get(int resolutionX, int resolutionY)
    {
        if ((resolutionX == 160) && (resolutionY == 120)) return "qqvga";
        if ((resolutionX == 176) && (resolutionY == 144)) return "qcif";
        if ((resolutionX == 320) && (resolutionY == 240)) return "qvga";
        if (resolutionX == 640)
        {
            if (resolutionY == 400) return"vga400";
            if (resolutionY == 480) return "vga";
        }
        if (resolutionX == 1280)
        {
            if (resolutionY == 720) return "hd";
            if (resolutionY == 960) return "sxgaminus";
            if (resolutionY == 1024) return "sxga";
        }
        if ((resolutionX == 1600) && (resolutionY == 1200)) return "uxga";
        if ((resolutionX == 1920) && (resolutionY == 1080)) return "fhd";
        return null;
    }
}