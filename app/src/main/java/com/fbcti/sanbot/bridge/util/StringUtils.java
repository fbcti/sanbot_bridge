/**
 * @file        StringUtils.java
 * @brief       Implements StringUtils utility class.
 */
package com.fbcti.sanbot.bridge.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Provides null-tolerant string helper functions.
 *
 * @version     1.0.001
 * @date        21 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public class StringUtils
{
    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /** Private constructor preventing utility class instantiation. */
    private StringUtils() {}

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Trims leading and trailing white space.
     *
     * @param   value           string to trim
     *
     * @return  trimmed string, or @c null if @p value is @c null
     */
    @Nullable
    public static String trim(String value)
    {
        if (value == null) return null;
        else return value.trim();
    }

    /**
     * Removes leading/trailing white space and converts string to lower case.
     *
     * @param   value           string to normalize
     *
     * @return  normalized string, or @c null if @p value is @c null or blank
     */
    @Nullable
    public static String normalize(String value)
    {
        if (value == null) return null;

        value = value.trim().toLowerCase(Locale.US);
        return (value.isEmpty() == false) ? value : null;
    }

    /**
     * Changes the first character in the string to upper case.
     *
     * @param   value           string to capitalize
     *
     * @return  capitalized string
     */
    @NonNull
    public static String capitalize(String value)
    {
        value = value.trim();
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    /**
     * Converts the specified string as a safe string, i.e. a string that is never @c null.
     *
     * If the string is not @c null it is returned as-is, if @c null an empty string is returned.
     *
     * @param   value           string to return as safe string
     *
     * @return  safe string
     */
    @NonNull
    public static String safeString(String value)
    {
        return (value != null) ? value : "";
    }

    /**
     * Checks if a string is @c null, empty, or contains only white space.
     *
     * @param   value           string to check
     *
     * @return  @c true if string is blank, @c false if not
     */
    public static boolean isBlank(String value)
    {
        return ((value == null) || (value.trim().isEmpty()));
    }

    /**
     * Compares two strings.
     *
     * The function returns @c true if the two strings are equal.
     *
     * @param   left            string to compare
     * @param   right           string to compare
     *
     * @return  @c true if strings are equal, @c false if not
     */
    public static boolean compare(String left, String right)
    {
        if (left == null) return right == null;
        return left.equals(right);
    }

    /**
     * Returns string from specified set that is equal to the specified string.
     *
     * @param   set             array of strings
     * @param   value           string value to check
     * @param   ignoreCase      if @c true, case is ignored
     *
     * @return  matching string from set, or @c null if no matching string is found
     */
    @Nullable
    public static String fromArray(String[] set, String value, boolean ignoreCase)
    {
        if (set == null) return null;

        for (String s : set)
        {
            if (ignoreCase)
            {
                if ((s == null) ? (value == null) : (s.equalsIgnoreCase(value))) return s;
            }
            else if (compare(s, value)) return s;
        }
        return null;
    }

    /**
     * Returns a formatted date/time string.
     *
     * @param   date            date to format
     *
     * @return  formatted date/time string
     */
    @NonNull
    public static String fromDate(Date date)
    {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date);
    }

    /**
     * Returns string representation of object or a fixed string if the specified object is blank.
     *
     * @param   obj             object for which to return string representation
     *
     * @return  specified string or a fixed string if the specified string is blank
     */
    @NonNull
    public static String unknownIfBlank(Object obj)
    {
        if (obj == null) return "<unknown>";
        return obj.toString();
    }
}