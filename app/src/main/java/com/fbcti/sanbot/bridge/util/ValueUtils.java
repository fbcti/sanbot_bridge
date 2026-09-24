/**
 * @file        ValueUtils.java
 * @brief       Implements ValueUtils utility class.
 */
package com.fbcti.sanbot.bridge.util;

import android.support.annotation.Nullable;

/**
 * Provides null-tolerant generic value conversion helpers.
 *
 * @version     1.0.001
 * @date        21 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public class ValueUtils
{
    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /** Private constructor preventing utility class instantiation. */
    private ValueUtils() {}

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Parses a boolean value.
     *
     * The boolean value may be specified as a boolean object, a numeric object, or a string such
     * as true, false, enable, disable, 1, or 0.
     *
     * @param   value           boolean-like value
     *
     * @return  instance of Java @c Boolean class, or @c null if parsing fails
     */
    @Nullable
    public static Boolean toBoolean(Object value)
    {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean)value;
        if (value instanceof Number) return (((Number)value).intValue() != 0) ? Boolean.TRUE : Boolean.FALSE;

        String text = StringUtils.normalize(String.valueOf(value));
        if (text == null) return null;
        if (("true".equals(text)) || ("enable".equals(text)) || ("enabled".equals(text)) || ("1".equals(text))) return Boolean.TRUE;
        if (("false".equals(text)) || ("disable".equals(text)) || ("disabled".equals(text)) || ("0".equals(text))) return Boolean.FALSE;
        return null;
    }

    /**
     * Parses an integer value.
     *
     * The integer value may be specified as a numeric object or as a string representation of an
     * integer.
     *
     * @param   value           integer or string representation of integer
     *
     * @return  instance of Java @c Integer class, or @c null if parsing fails
     */
    @Nullable
    public static Integer toInteger(Object value)
    {
        if (value == null) return null;
        if (value instanceof Number) return ((Number)value).intValue();

        String text = StringUtils.trim(String.valueOf(value));
        if (StringUtils.isBlank(text)) return null;
        try
        {
            return Integer.parseInt(text);
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    /**
     * Parses and validates an integer value.
     *
     * The integer value may be specified as a numeric object or as a string representation of an
     * integer. If the parsed value falls outside the specified bounds the method returns @c null.
     *
     * @param   value           integer or string representation of integer
     * @param   minValue        minimal allowed value
     * @param   maxValue        maximal allowed value
     *
     * @return  instance of Java @c Integer class, or @c null if parsing fails
     */
    @Nullable
    public static Integer toBoundInteger(Object value, int minValue, int maxValue)
    {
        Integer intValue = toInteger(value);
        return ((intValue != null) && (intValue >= minValue) && (intValue <= maxValue)) ? intValue : null;
    }

    /**
     * Parses and validates an integer value or returns the supplied default.
     *
     * @param   value           integer or string representation of integer
     * @param   defaultValue    fallback value
     * @param   minValue        minimum accepted value
     * @param   maxValue        maximum accepted value
     *
     * @return  validated integer value
     */
    public static int toBoundInteger(Object value, int defaultValue, int minValue, int maxValue)
    {
        Integer parsedValue = toBoundInteger(value, minValue, maxValue);
        return (parsedValue != null) ? parsedValue : defaultValue;
    }
}