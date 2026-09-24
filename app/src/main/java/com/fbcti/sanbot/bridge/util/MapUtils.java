/**
 * @file        MapUtils.java
 * @brief       Implements MapUtils utility class.
 */
package com.fbcti.sanbot.bridge.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides data map helper functions.
 *
 * All data maps must have a string as item key, the value can be any Java object.
 *
 * @version     1.0.001
 * @date        16 Aug 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public class MapUtils
{
    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /** Private constructor preventing utility class instantiation. */
    private MapUtils() {}

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Returns data map containing specified data items.
     *
     * A new data map is created, and the key-value pairs specified by the function parameters are
     * added.
     *
     * @param   args            keys and values of key-value pairs
     *
     * The key of each value pair must be a string, the value can be any Java object. If the key is
     * not a string the key-value pair is ignored.
     *
     * @return  Java @c Map instance containing specified data
     */
    @NonNull
    public static Map<String, Object> createMap(Object... args)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        addToMap(data, args);
        return data;
    }

    /**
     * Creates a data map from a JSON object.
     *
     * @param   jsonObject      JSON object from which to create data map
     *
     * @return  Java @c map instance containing data from JSON object
     */
    @NonNull
    public static Map<String, Object> fromJsonObject(JsonObject jsonObject)
    {
        Map<String, Object> data = MapUtils.createMap();
        if ((jsonObject == null) || (jsonObject.isJsonNull())) return data;

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet())
        {
            Object value = JsonUtils.parseObject(entry.getValue());
            if (value instanceof String) value = ((String)value).trim();
            if (value != null) data.put(entry.getKey(), value);
        }
        return data;
    }

    /**
     * Adds a variable number of items to the data map.
     *
     * @param   data            instance of Java @c Map class to which to add items
     * @param   args            keys and values of items to add
     *
     * The key of each item must be a string, if not it is ignored. The value can be any Java
     * object.
     */
    public static void addToMap(Map<String, Object> data, @NonNull Object... args)
    {
        for (int i=0; i<args.length-1; i+=2)
        {
            if (args[i] instanceof String)
            {
                String key = ((String)args[i]).trim();
                data.put(key, args[i + 1]);
            }
        }
    }

    /**
     * Deletes a variable number of items from the data map.
     *
     * @param   data            instance of Java @c Map class fom which to delete items
     * @param   args            keys of items to remove
     *
     * The key of each item must be a string, if not it is ignored.
     */
    public static void deleteFromMap(Map<String, Object> data, @NonNull Object... args)
    {
        for (Object arg : args)
        {
            if (arg instanceof String)
            {
                String key = ((String)arg).trim();
                data.remove(key);
            }
        }
    }

    /**
     * Creates a deep copy of the specified data map.
     *
     * For data items that are itself a data map the method is called recursively. Note that lists
     * items are copied as-is, so if a list item is itself a data map the resulting data map will
     * contain s shallow copy of that item.
     *
     * @param   data            data map to copy
     *
     * @return  Java @c Map instance containing copied data
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepCopy(Map<String, Object> data)
    {
        if (data == null) return null;

        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet())
        {
            Object value = entry.getValue();
            if (value instanceof Map) value = deepCopy((Map<String, Object>) value);
            if (value != null) copy.put(entry.getKey(), value);
        }
        return copy;
    }

    /**
     * Copies data from one data map into another data map.
     *
     * @param   srcData         data map to copy
     * @param   destData        data map to which to copy
     *
     * @return  Java @c Map instance containing copied data
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static Map<String, Object> copyData(Map<String, Object> srcData, Map<String, Object> destData)
    {
        if ((srcData == null) || (destData == null)) return null;

        destData.clear();
        for (Map.Entry<String, Object> entry : srcData.entrySet())
        {
            Object value = entry.getValue();
            if (value instanceof Map) value = deepCopy((Map<String, Object>)value);
            if (value != null) destData.put(entry.getKey(), value);
        }
        return destData;
    }

    /**
     * Convert a Java object to a map with string keys.
     *
     * If the object can not be converted to a map an empty map is returned. If an entry key is not
     * a string it is ignored.
     *
     * @param   obj             object to convert
     *
     * @return  Java @c Map instance with string key values
     */
    @NonNull
    public static Map<String, Object> castObject(Object obj)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (obj instanceof Map<?, ?>)
        {
            Map<?, ?> map = (Map<?, ?>)obj;
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                if (entry.getKey() instanceof String)
                    result.put((String)entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * Returns the item with the specified name from the data map
     *
     * @param   data            Java @c Map instance from which to retrieve item
     * @param   name            name of item to retrieve
     *
     * @return  parameter value as Java @c Object, or @c null if not specified
     */
    public static Object get(Map<String, Object> data, String name)
    {
        if ((name == null) || (data == null) || (data.containsKey(name) == false)) return null;
        return data.get(name);
    }

    /**
     * Returns the string value of the item with the specified key name from the map.
     *
     * Leading and trailing spaces are trimmed from the value.
     *
     * @param   data            Java @c Map instance from which to retrieve item
     * @param   name            name of item to retrieve
     * @param   value           default value to return if item does nor exist
     *
     * @return  string value of item, or default value if item does not exist
     */
    public static String getString(Map<String, Object> data, String name, String value)
    {
        Object obj = get(data, name);
        if (obj == null) return value;
        String s = obj.toString().trim();
        return (s.isEmpty() == false) ? s : value;
    }

    /**
     * Returns the integer value of the item with the specified key name from the data map.
     *
     * @param   data            data map from which to return string item
     * @param   key             key name of data item
     * @param   value           value to return if data map does not contain the item
     *
     * @return  integer item value, or default value if item does not exist in data map
     */
    public static int getInt(Map<String, Object> data, String key, int value)
    {
        Object obj = get(data, key);
        return (obj instanceof Number) ? ((Number)obj).intValue() : value;
    }

    /**
     * Returns the boolean value of the item with the specified key name from the data map.
     *
     * @param   data            data map from which to return string item
     * @param   key             key name of data item
     * @param   value           value to return if data map does not contain the item
     *
     * @return  boolean item value, or default value if item does not exist in data map
     */
    public static boolean getBoolean(Map<String, Object> data, String key, boolean value)
    {
        Object obj = data.get(key);
        if (obj instanceof Boolean) return (boolean)obj;
        if (obj instanceof Number) return ((Number)obj).intValue() != 0;
        if (obj instanceof String)
        {
            String normalized = ((String)obj).trim().toLowerCase();
            switch (normalized)
            {
                case "":
                    return value;
                case "1":
                case "true":
                case "yes":
                case "on":
                    return true;
                case "0":
                case "false":
                case "no":
                case "off":
                    return false;
            }
        }
        return value;
    }
}