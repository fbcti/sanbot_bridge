/**
 * @file        JsonUtils.java
 * @brief       Implements JsonUtils utility class.
 */
package com.fbcti.sanbot.bridge.util;

import android.renderscript.RSInvalidStateException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provides null-tolerant helpers for reading @c Gson JSON values.
 *
 * Conversion methods return caller-supplied fallback values when the input is missing, @c null, the
 * wrong JSON shape, or cannot be parsed as the requested type.
 *
 * @version     1.0.001
 * @date        21 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public class JsonUtils
{
    /** Instance of class for serializing/deserializing Json objects. */
    private static final Gson gson = new Gson();

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /** Private constructor preventing utility class instantiation. */
    private JsonUtils() {}

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Parses a string representing a JSON element into Java object type.
     *
     * The method converts the string to a @c Gson @c JsonPrimitive, and tries to parse that
     * object into into either a @c Boolean, @c Integer, @c JsonObject, and @c JsonArray (in that
     * order). If none of those conversions match, the original string is returned unchanged.
     *
     * @param   str             string to parse
     *
     * @return  parsed string as instance of Java @c Object class
     */
    @Nullable
    public static Object parseObject(String str)
    {
        String trimmed = StringUtils.trim(str);
        if (trimmed == null) return null;

        JsonElement jsonElement = new JsonPrimitive(trimmed);

        Object object = parseObject(jsonElement);
        if (object != null) return object;

        return trimmed;
    }

    /**
     * Parses a JSON element into Java object type.
     *
     * The method tries to parse the object into into either a @c Boolean, @c Integer,
     * @c JsonObject, and @c JsonArray (in that order). If none of those conversions match, the
     * method returns @c null.
     *
     * @param   jsonElement     JSON element to parse
     *
     * @return  parsed str as instance of Java @c Object class
     */
    @Nullable
    public static Object parseObject(JsonElement jsonElement)
    {
        Boolean booleanValue = toBoolean(jsonElement, null);
        if (booleanValue != null) return booleanValue;

        Integer integerValue = toInteger(jsonElement, null);
        if (integerValue != null) return integerValue;

        JsonObject objectValue = toJsonObject(jsonElement, null);
        if (objectValue != null) return objectValue;

        JsonArray arrayValue = toJsonArray(jsonElement, null);
        if (arrayValue != null) return arrayValue;

        return toString(jsonElement, null);
    }

    /**
     * Converts a @c Gson @c JsonElement to a string value if possible.
     *
     * @param   jsonElement     JSON element to read
     * @param   value           fallback value returned when conversion fails
     *
     * @return  element value as @c String, or trimmed fallback value
     */
    @Nullable
    public static String toString(JsonElement jsonElement, String value)
    {
        try
        {
            if ((jsonElement == null) || (jsonElement.isJsonNull()) || (jsonElement.isJsonPrimitive() == false))
            {
                return (value != null) ? value.trim() : null;
            }
            return jsonElement.getAsString().trim();
        }
        catch (ClassCastException | IllegalStateException | NullPointerException e)
        {
            return (value != null) ? value.trim() : null;
        }
    }

    /**
     * Converts a @c Gson @c JsonElement to a boolean value if possible.
     *
     * The JSON element is converted to a string. If the string represents a boolean value (either
     * @c "true" or @c "false", case insensitive), the boolean value is returned. If not, the
     * specified default value is returned.
     *
     * @param   jsonElement     JSON element to read
     * @param   value           fallback value returned when conversion fails
     *
     * @return  element value as @c Boolean, or fallback value
     */
    @Nullable
    public static Boolean toBoolean(JsonElement jsonElement, Boolean value)
    {
        String text = toString(jsonElement, null);
        if (text == null) return value;
        return ValueUtils.toBoolean(text);
    }

    /**
     * Converts a @c Gson @c JsonElement to a byte value if possible.
     *
     * The JSON element is converted to a string. If the string represents a byte value the byte
     * value is returned. If not, the specified default value is returned.

     * @param   jsonElement     JSON element to read
     * @param   value           fallback value returned when conversion fails
     *
     * @return  element value as @c Byte, or fallback value
     */
    @Nullable
    public static Byte toByte(JsonElement jsonElement, Byte value)
    {
        try
        {
            String text = toString(jsonElement, null);
            return text != null ? Byte.valueOf(text) : value;
        }
        catch (NumberFormatException e)
        {
            return value;
        }
    }

    /**
     * Converts a @c Gson @c JsonElement to an integer value if possible.
     *
     * The JSON element is converted to a string. If the string represents a integer value the byte
     * value is returned. If not, the specified default value is returned.
     *
     * @param   jsonElement     JSON element to read
     * @param   value           fallback value returned when conversion fails
     *
     * @return  element value as @c Integer, or fallback value
     */
    @Nullable
    public static Integer toInteger(JsonElement jsonElement, Integer value)
    {
        try
        {
            String text = toString(jsonElement, null);
            return text != null ? Integer.valueOf(text) : value;
        }
        catch (NumberFormatException e)
        {
            return value;
        }
    }

    /**
     * Converts a JSON array to a list of primitive string values.
     *
     * @param   jsonElement     JSON element to read
     * @param   value           fallback value returned when conversion fails
     *
     * @return  list of string values, or fallback value
     */
    @Nullable
    public static List<String> toStringArray(JsonElement jsonElement, List<String> value)
    {
        JsonArray jsonArray = toJsonArray(jsonElement, null);
        if (jsonArray != null)
        {
            List<String> array = new ArrayList<>();
            for (JsonElement item : jsonArray)
            {
                if (item.isJsonPrimitive()) array.add(item.getAsString().trim());
            }
            return array;
        }

        return value;
    }

    /**
     * Converts a JSON element to a JSON object if possible.
     *
     * Existing objects are returned as-is. Primitive string values are parsed as JSON and accepted
     * only when the parsed value is an object.
     *
     * @param   jsonElement     JSON element to read
     * @param   value           fallback value returned when conversion fails
     *
     * @return  element value as @c JsonObject, or fallback value
     */
    @Nullable
    public static JsonObject toJsonObject(JsonElement jsonElement, JsonObject value)
    {
        try
        {
            if ((jsonElement == null) || (jsonElement.isJsonNull())) return value;

            if (jsonElement.isJsonObject()) return jsonElement.getAsJsonObject();

            if (jsonElement.isJsonPrimitive())
            {
                JsonElement parsed = parseJson(jsonElement.getAsString().trim());
                if ((parsed != null) && (parsed.isJsonObject())) return parsed.getAsJsonObject();
            }
        }
        catch (ClassCastException | IllegalStateException | NullPointerException ignored)
        {
        }
        return value;
    }

    /**
     * Creates a JSON object from a Java @c Map instance
     *
     * @param       map         Java @c Map instance from which to create JSON object
     *
     * @return      JSON object
     *
     * The JSON object will be empty if the map could not be converted
     */
    @NonNull
    public static JsonObject toJsonObject(Map<String, Object> map)
    {
        try
        {
            return gson.toJsonTree(map).getAsJsonObject();
        }
        catch (RSInvalidStateException e)
        {
            return new JsonObject();
        }
    }

    /**
     * Converts a JSON element to a JSON array if possible.
     *
     * Existing arrays are returned as-is. Primitive string values are parsed as JSON and accepted
     * only when the parsed value is an array.
     *
     * @param   jsonElement     JSON element to read
     * @param   value           fallback value returned when conversion fails
     *
     * @return  element value as @c JsonArray, or fallback value
     */
    @Nullable
    public static JsonArray toJsonArray(JsonElement jsonElement, JsonArray value)
    {
        try
        {
            if ((jsonElement == null) || (jsonElement.isJsonNull())) return value;

            if (jsonElement.isJsonArray()) return jsonElement.getAsJsonArray();

            if (jsonElement.isJsonPrimitive())
            {
                JsonElement parsed = parseJson(jsonElement.getAsString().trim());
                if ((parsed != null) && (parsed.isJsonArray())) return parsed.getAsJsonArray();
            }
        }
        catch (ClassCastException | IllegalStateException | NullPointerException ignored)
        {
        }
        return value;
    }

    /**
     * Retrieves a nullable @c Gson @c JsonElement from an object.
     *
     * @param   jsonObject      JSON object to read
     * @param   key             element name
     *
     * @return  JSON element, or @c null if it is missing or null
     */
    @Nullable
    public static JsonElement getElement(JsonObject jsonObject, String key)
    {
        if ((jsonObject == null) || (key == null) || (jsonObject.has(key) == false))
        {
            return null;
        }
        JsonElement jsonElement = jsonObject.get(key);
        return ((jsonElement != null) && (jsonElement.isJsonNull() == false)) ? jsonElement : null;
    }

    /**
     * Retrieves a nullable string value from a @c Gson @c JsonElement.
     *
     * @param   jsonObject      JSON object to read
     * @param   key             element name
     *
     * @return  trimmed string value, or @c null if it is missing or not a primitive value
     */
    @Nullable
    public static String getString(JsonObject jsonObject, String key)
    {
        return toString(getElement(jsonObject, key), null);
    }

    /**
     * Retrieves a string value from a @c Gson @c JsonElement with a fallback value.
     *
     * @param   jsonObject      JSON object to read
     * @param   key             element name
     * @param   value           fallback value returned when conversion fails
     *
     * @return  trimmed string value, or trimmed fallback value
     */
    @Nullable
    public static String getString(JsonObject jsonObject, String key, String value)
    {
        return toString(getElement(jsonObject, key), value);
    }

    /**
     * Retrieves a nullable integer value from a @c Gson @c JsonElement.
     *
     * @param   jsonObject      JSON object to read
     * @param   key             element name
     *
     * @return  integer value, or @c null if it is missing or not an integer value
     */
    @Nullable
    public static Integer getInteger(JsonObject jsonObject, String key)
    {
        return toInteger(getElement(jsonObject, key), null);
    }

    /**
     * Retrieves an integer value from a @c Gson @c JsonElement with a fallback value.
     *
     * @param   jsonObject      JSON object to read
     * @param   key             element name
     * @param   value           fallback value returned when conversion fails
     *
     * @return  integer value, or fallback value
     */
    @Nullable
    public static int getInteger(JsonObject jsonObject, String key, int value)
    {
        Integer i = toInteger(getElement(jsonObject, key), null);
        return (i != null) ? i : value;
    }

    /**
     * Retrieves a nullable boolean value from a @c Gson @c JsonElement.
     *
     * @param   jsonObject      JSON object to read
     * @param   key             element name
     *
     * @return  boolean value, or @c null if it is missing or not a boolean value
     */
    @Nullable
    public static Boolean getBoolean(JsonObject jsonObject, String key)
    {
        return toBoolean(getElement(jsonObject, key), null);
    }

    /**
     * Retrieves a boolean value from a @c Gson @c JsonElement with a fallback value.
     *
     * @param   jsonObject      JSON object to read
     * @param   key             element name
     * @param   value           fallback value returned when conversion fails
     *
     * @return  boolean value, or fallback value
     */
    @Nullable
    public static boolean getBoolean(JsonObject jsonObject, String key, boolean value)
    {
        Boolean b = toBoolean(getElement(jsonObject, key), null);
        return (b != null) ? b : value;
    }

    /**
     * Removes the element with the specified name from the JSON object.
     *
     * @param   jsonObject      JSON objet from which to remove element
     * @param   key             key of element to remove
     */
    public static void remove(JsonObject jsonObject, String key)
    {
        if (jsonObject != null) jsonObject.remove(key);
    }

    /**
     * Parses a string as a JSON element.
     *
     * @param   str             string value to parse
     * @param   value           fallback value returned when parsing fails
     *
     * @return  @c Gson @c JsonElement, or fallback value
     */
    @Nullable
    public static JsonElement parseJsonElement(String str, JsonElement value)
    {
        JsonElement parsed = parseJson(str);
        return (parsed != null) ? parsed : value;
    }

    /**
     * Parses a string as a JSON object.
     *
     * @param   str             string value to parse
     * @param   value           fallback value returned when parsing fails or value is not an object
     *
     * @return  @c Gson @c JsonObject
     */
    @Nullable
    public static JsonObject parseJsonObject(String str, JsonObject value)
    {
        JsonElement parsed = parseJson(str);
        return ((parsed != null) && (parsed.isJsonObject())) ? parsed.getAsJsonObject() : value;
    }

    /**
     * Normalize key name in JSON element.
     *
     * All key names are converted to lower case, and leading and trailing whitespace is trimmed.
     *
     * @param   jsonElement     JSON element for which to normalize keys
     *
     * @return  JSON element with all key names normalized
     */
    @Nullable
    public static JsonElement normalizeKeys(JsonElement jsonElement)
    {
        // If JSON element is empty there is nothing to do.
        if ((jsonElement == null) || (jsonElement.isJsonNull())) return jsonElement;

        // If the JSON element is a JSON object recursively normalize keys.
        if (jsonElement.isJsonObject())
        {
            JsonObject jsonOriginal = jsonElement.getAsJsonObject();
            JsonObject jsonNormalized = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonOriginal.entrySet()) {
                String normalizedKey = StringUtils.normalize(entry.getKey());
                if (StringUtils.isBlank(normalizedKey) == false) jsonNormalized.add(normalizedKey, normalizeKeys(entry.getValue()));
            }
            return jsonNormalized;
        }

        // If the JSON element is a JSON array normalize all element names.
        if (jsonElement.isJsonArray())
        {
            JsonArray jsonArray = new JsonArray();
            for (JsonElement jsonArrayItem : jsonElement.getAsJsonArray())
            {
                jsonArray.add(normalizeKeys(jsonArrayItem));
            }
            return jsonArray;
        }

        // Primitive.
        return jsonElement;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Parses a string as any JSON value.
     *
     * @param   str             string value to parse
     *
     * @return  @c Gson @c JsonObject, or @c null if parsing fails
     */
    @Nullable
    private static JsonElement parseJson(String str)
    {
        if (str == null) return null;

        try
        {
            return gson.fromJson(str.trim(), JsonElement.class);
        }
        catch (JsonSyntaxException | IllegalStateException e)
        {
            return null;
        }
    }
}