/**
 * @file        SanbotMappings.java
 * @brief       Implements SanbotMappings class.
 */
package com.fbcti.sanbot.bridge.robot.mapping;

import android.media.AudioManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.google.gson.JsonObject;
import com.sanbot.opensdk.function.beans.EmotionsType;
import com.sanbot.opensdk.function.beans.LED;
import com.sanbot.opensdk.function.beans.SpeakOption;
import com.sanbot.opensdk.function.beans.WakeUpOption;
import com.sanbot.opensdk.function.beans.headmotion.AbsoluteAngleHeadMotion;
import com.sanbot.opensdk.function.beans.headmotion.LocateAbsoluteAngleHeadMotion;
import com.sanbot.opensdk.function.beans.headmotion.RelativeAngleHeadMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.RelativeAngleWheelMotion;
import com.sanbot.opensdk.function.beans.wing.AbsoluteAngleWingMotion;
import com.sanbot.opensdk.function.beans.wing.NoAngleWingMotion;
import com.sanbot.opensdk.function.beans.wing.RelativeAngleWingMotion;

import java.util.Locale;

/**
 * Helper class providing methods for mapping bridge protocol constants to Sanbot SDK constants.
 *
 * All methods in this class are declared @c public @c static, except for the constructor which is
 * declared @c private to prevent an instance of the class to be created.
 *
 * @version     1.0.001
 * @date        22 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class SanbotMappings
{
    /** Android TTS stream id used by robot audio output on older SDKs. */
    private static final int AUDIO_STREAM_TTS = 9;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /** Private constructor preventing utility class instantiation. */
    private SanbotMappings() {}

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Maps bridge protocol direction name to Sanbot SDK @c RelativeAngleHeadMotion action value.
     *
     * @param   direction       bridge protocol head direction name
     *
     * @return  Sanbot SDK 'head action' as @c Byte value, or @c null if @p direction is invalid
     */
    @Nullable
    public static Byte mapRelativeAngleHeadMotionAction(String direction)
    {
        String normalized = StringUtils.normalize(direction);
        if (normalized == null) return null;

        switch (normalized)
        {
            case BridgeProtocol.DIRECTION_UP:
                return RelativeAngleHeadMotion.ACTION_UP;
            case BridgeProtocol.DIRECTION_DOWN:
                return RelativeAngleHeadMotion.ACTION_DOWN;
            case BridgeProtocol.DIRECTION_LEFT:
                return RelativeAngleHeadMotion.ACTION_LEFT;
            case BridgeProtocol.DIRECTION_RIGHT:
                return RelativeAngleHeadMotion.ACTION_RIGHT;
            case BridgeProtocol.DIRECTION_LEFTUP:
                return RelativeAngleHeadMotion.ACTION_LEFTUP;
            case BridgeProtocol.DIRECTION_RIGHTUP:
                return RelativeAngleHeadMotion.ACTION_RIGHTUP;
            case BridgeProtocol.DIRECTION_LEFTDOWN:
                return RelativeAngleHeadMotion.ACTION_LEFTDOWN;
            case BridgeProtocol.DIRECTION_RIGHTDOWN:
                return RelativeAngleHeadMotion.ACTION_RIGHTDOWN;
        }
        return null;
    }

    /**
     * Maps bridge protocol direction name to Sanbot SDK @c AbsoluteAngleHeadMotion action value.
     *
     * @param   direction       bridge protocol head direction name
     *
     * @return  Sanbot SDK 'head action' as @c Byte value, or @c null if @p direction is invalid
     */
    @Nullable
    public static Byte mapAbsoluteAngleHeadMotionAction(String direction)
    {
        String normalized = StringUtils.normalize(direction);
        if (normalized == null) return null;

        if (BridgeProtocol.DIRECTION_VERTICAL.equals(normalized)) return AbsoluteAngleHeadMotion.ACTION_VERTICAL;
        if (BridgeProtocol.DIRECTION_HORIZONTAL.equals(normalized)) return AbsoluteAngleHeadMotion.ACTION_HORIZONTAL;
        return null;
    }

    /**
     * Maps bridge protocol direction name to Sanbot SDK @c LocateAbsoluteAngleHeadMotion action
     * value.
     *
     * @param   direction       bridge protocol lock direction name
     *
     * @return  Sanbot SDK 'lock action' as @c Byte value, or @c null if @p direction is invalid
     */
    @Nullable
    public static Byte mapLocateAbsoluteAngleHeadMotionAction(String direction)
    {
        String normalized = StringUtils.normalize(direction);
        if (normalized == null) return null;

        switch (normalized)
        {
            case BridgeProtocol.DIRECTION_NONE:
                return LocateAbsoluteAngleHeadMotion.ACTION_NO_LOCK;
            case BridgeProtocol.DIRECTION_HORIZONTAL:
                return LocateAbsoluteAngleHeadMotion.ACTION_HORIZONTAL_LOCK;
            case BridgeProtocol.DIRECTION_VERTICAL:
                return LocateAbsoluteAngleHeadMotion.ACTION_VERTICAL_LOCK;
            case BridgeProtocol.DIRECTION_BOTH:
                return LocateAbsoluteAngleHeadMotion.ACTION_BOTH_LOCK;
        }
        return null;
    }

    /**
     * Maps bridge protocol head angle to Sanbot SDK angle.
     *
     * The bridge protocol specifies angles as centered coordinates, i.e. the center position of the
     * head is at position (0,0). The Sanbot bridge protocols uses horizontal coordinates in the
     * range [0..180] and vertical coordinates in the range [7..30], with (90,20) the center
     * positions.
     *
     * @param   angle           bridge protocol angle
     * @param   horizontal      @c true if mapping is done for horizontal angles
     *
     * @return  angle compatible with Sanbot SDK functions
     */
    public static int mapHeadAngle(int angle, boolean horizontal)
    {
        if (horizontal) return angle + 90;
        return angle + 20;
    }

    /**
     * Maps bridge protocol arms side name to Sanbot SDK @c NoAngleWingMotion part value.
     *
     * @param   side            bridge protocol arms side name
     *
     * @return  Sanbot SDK 'wing part' as @c Byte value, or @c null if @p side is invalid
     */
    @Nullable
    public static Byte mapNoAngleWingMotionSide(String side)
    {
        String normalized = StringUtils.normalize(side);
        if (normalized == null) return null;

        switch (normalized)
        {
            case BridgeProtocol.SIDE_LEFT:
                return NoAngleWingMotion.PART_LEFT;
            case BridgeProtocol.SIDE_RIGHT:
                return NoAngleWingMotion.PART_RIGHT;
            case BridgeProtocol.SIDE_BOTH:
                return NoAngleWingMotion.PART_BOTH;
        }
        return null;
    }

    /**
     * Maps bridge protocol arms direction name to Sanbot SDK @c NoAngleWingMotion action value.
     *
     * @param   direction       bridge protocol arms direction name
     *
     * @return  Sanbot SDK 'wing action' as @c Byte value, or @c null if @p direction is invalid
     */
    @Nullable
    public static Byte mapNoAngleWingMotionAction(String direction)
    {
        String normalized = StringUtils.normalize(direction);
        if (normalized == null) return null;

        if (BridgeProtocol.DIRECTION_UP.equals(normalized)) return NoAngleWingMotion.ACTION_UP;
        else if (BridgeProtocol.DIRECTION_DOWN.equals(normalized)) return NoAngleWingMotion.ACTION_DOWN;
        return null;
    }

    /**
     * Maps bridge protocol arms side name to Sanbot SDK @c AbsoluteAngleWingMotion part value.
     *
     * @param   side           bridge protocol arms side name
     *
     * @return  Sanbot SDK 'wing part' as @c Byte value, or @c null if @p direction is invalid
     */
    @Nullable
    public static Byte mapAbsoluteAngleWingMotionPart(String side)
    {
        String normalized = StringUtils.normalize(side);
        if (normalized == null) return null;

        switch (normalized)
        {
            case BridgeProtocol.SIDE_LEFT:
                return AbsoluteAngleWingMotion.PART_LEFT;
            case BridgeProtocol.SIDE_RIGHT:
                return AbsoluteAngleWingMotion.PART_RIGHT;
            case BridgeProtocol.SIDE_BOTH:
                return AbsoluteAngleWingMotion.PART_BOTH;
        }
        return null;
    }

    /**
     * Maps bridge protocol side name to Sanbot SDK @c RelativeAngleWingMotion part value.
     *
     * @param   side           bridge protocol arms side name
     *
     * @return  Sanbot SDK 'wing part' as @c Byte value, or @c null if @p direction is invalid
     */
    @Nullable
    public static Byte mapRelativeAngleWingMotionPart(String side)
    {
        String normalized = StringUtils.normalize(side);
        if (normalized == null) return null;

        switch (normalized)
        {
            case BridgeProtocol.SIDE_LEFT:
                return RelativeAngleWingMotion.PART_LEFT;
            case BridgeProtocol.SIDE_RIGHT:
                return RelativeAngleWingMotion.PART_RIGHT;
            case BridgeProtocol.SIDE_BOTH:
                return RelativeAngleWingMotion.PART_BOTH;
        }
        return null;
    }

    /**
     * Maps bridge protocol direction name to Sanbot SDK @c RelativeAngleWingMotionAction action
     * value.
     *
     * @param   direction       bridge protocol arms direction name
     *
     * @return  Sanbot SDK 'wing action' as @c Byte value, or @c null if @p direction is invalid
     */
    @Nullable
    public static Byte mapRelativeAngleWingMotionAction(String direction)
    {
        String normalized = StringUtils.normalize(direction);
        if (normalized == null) return null;

        if (BridgeProtocol.DIRECTION_UP.equals(normalized)) return RelativeAngleWingMotion.ACTION_UP;
        else if (BridgeProtocol.DIRECTION_DOWN.equals(normalized)) return RelativeAngleWingMotion.ACTION_DOWN;
        return null;
    }

    /**
     * Maps protocol bridge direction name to Sanbot SDK @c NoAngleWheelMotion action value.
     *
     * @param   direction       bridge protocol direction name
     *
     * @return  Sanbot SDK 'wheel action' as @c Byte value, or @c null if @p direction is invalid
     */
    @Nullable
    public static Byte mapNoAngleWheelMotionAction(String direction)
    {
        String normalized = StringUtils.normalize(direction);
        if (normalized == null) return null;

        switch (normalized)
        {
            case BridgeProtocol.DIRECTION_FORWARD:
                return NoAngleWheelMotion.ACTION_FORWARD;
            case BridgeProtocol.DIRECTION_BACKWARD:
                return NoAngleWheelMotion.ACTION_BACK;
            case BridgeProtocol.DIRECTION_LEFT:
                return NoAngleWheelMotion.ACTION_LEFT_TRANSLATION;
            case BridgeProtocol.DIRECTION_RIGHT:
                return NoAngleWheelMotion.ACTION_RIGHT_TRANSLATION;
            case BridgeProtocol.DIRECTION_LEFTFORWARD:
                return NoAngleWheelMotion.ACTION_LEFT_FORWARD;
            case BridgeProtocol.DIRECTION_RIGHTFORWARD:
                return NoAngleWheelMotion.ACTION_RIGHT_FORWARD;
            case BridgeProtocol.DIRECTION_LEFTBACKWARD:
                return NoAngleWheelMotion.ACTION_LEFT_BACK;
            case BridgeProtocol.DIRECTION_RIGHTBACKWARD:
                return NoAngleWheelMotion.ACTION_RIGHT_BACK;
            case BridgeProtocol.DIRECTION_LEFTCIRCLE:
                return NoAngleWheelMotion.ACTION_TURN_LEFT;
            case BridgeProtocol.DIRECTION_RIGHTCIRCLE:
                return NoAngleWheelMotion.ACTION_TURN_RIGHT;
            case BridgeProtocol.DIRECTION_LEFTTURN:
                return NoAngleWheelMotion.ACTION_LEFT;
            case BridgeProtocol.DIRECTION_RIGHTTURN:
                return NoAngleWheelMotion.ACTION_RIGHT;
            case BridgeProtocol.DIRECTION_STOPTURN:
                return NoAngleWheelMotion.ACTION_STOP_TURN;
            case BridgeProtocol.ACTION_STOP:
                return NoAngleWheelMotion.ACTION_STOP;
            case BridgeProtocol.ACTION_RESET:
                return NoAngleWheelMotion.ACTION_RESET;
        }
        return null;
    }

    /**
     * Maps protocol bridge direction name to Sanbot SDK @c RelativeAngleWheelMotion action value.
     *
     * @param   direction       bridge protocol direction name
     *
     * @return  Sanbot SDK 'wheel action' as @c Byte value, or @c null if @p direction is invalid
     */
    @Nullable
    public static Byte mapRelativeAngleWheelMotionAction(String direction)
    {
        String normalized = StringUtils.normalize(direction);
        if (normalized == null) return null;

        switch (normalized)
        {
            case BridgeProtocol.DIRECTION_LEFT:
                return RelativeAngleWheelMotion.TURN_LEFT;
            case BridgeProtocol.DIRECTION_RIGHT:
                return RelativeAngleWheelMotion.TURN_RIGHT;
            case BridgeProtocol.ACTION_STOP:
                return RelativeAngleWheelMotion.TURN_STOP;
        }
        return null;
    }

    /**
     * Maps bridge protocol robot part name to Sanbot SDK @c LED part value.
     *
     * @param   part            bridge protocol robot part name
     *
     * @return  Sanbot 'led part' as @c Byte value, or @c null if @p part is invalid
     */
    @Nullable
    public static Byte mapLedPart(String part)
    {
        String normalized = StringUtils.normalize(part);

        if ((normalized == null) || (BridgeProtocol.LED_ALL.equals(normalized))) return LED.PART_ALL;
        switch (normalized)
        {
            case BridgeProtocol.LED_LEFT_HEAD:
                return LED.PART_LEFT_HEAD;
            case BridgeProtocol.LED_RIGHT_HEAD:
                return LED.PART_RIGHT_HEAD;
            case BridgeProtocol.LED_LEFT_ARM:
                return LED.PART_LEFT_HAND;
            case BridgeProtocol.LED_RIGHT_ARM:
                return LED.PART_RIGHT_HAND;
            case BridgeProtocol.LED_BASE:
                return LED.PART_WHEEL;
        }
        return null;
    }

    /**
     * Maps bridge protocol head side name to Sanbot SDK @c LED part value.
     *
     * This is a variation of the mapLedPart() method that limits the allowed parts to be returned
     * to the left and right side of the robot head.
     *
     * @param   side            bridge protocol head side name
     *
     * @return  Sanbot 'led part' as @c Byte value, or @c null if @p side is invalid
     */
    @Nullable
    public static Byte mapHeadLedPart(String side)
    {
        String normalized = StringUtils.normalize(side);
        if (normalized == null) return null;

        if (BridgeProtocol.SIDE_LEFT.equals(normalized)) return LED.PART_LEFT_HEAD;
        if (BridgeProtocol.SIDE_RIGHT.equals(normalized)) return LED.PART_RIGHT_HEAD;
        return null;
    }

    /**
     * Maps bridge protocol arms side name to Sanbot SDK @c LED part value.
     *
     * This is a variation of the mapLedPart() method that limits the allowed parts to be returned
     * to the left and right robot arm.
     *
     * @param   side            bridge protocol arm side name
     *
     * @return  Sanbot 'led part' as @c Byte value, or @c null if @p side is invalid
     */
    @Nullable
    public static Byte mapWingLedPart(String side)
    {
        String normalized = StringUtils.normalize(side);
        if (normalized == null) return null;

        if (BridgeProtocol.SIDE_LEFT.equals(normalized)) return LED.PART_LEFT_HAND;
        else if (BridgeProtocol.SIDE_RIGHT.equals(normalized)) return LED.PART_RIGHT_HAND;
        return null;
    }

    /**
     * Maps bridge protocol color name to Sanbot SDK @c LED mode value.
     *
     * @param   color           bridge protocol color name
     *
     * @return  Sanbot 'led mode' as @c Byte value, or @c null if @p color is invalid
     */
    @Nullable
    public static Byte mapLedColor(String color)
    {
        String normalized = StringUtils.normalize(color);
        if (normalized == null) return null;

        switch (normalized)
        {
            case BridgeProtocol.LED_OFF:
                return LED.MODE_CLOSE;
            case BridgeProtocol.LED_WHITE:
                return LED.MODE_WHITE;
            case BridgeProtocol.LED_RED:
                return LED.MODE_RED;
            case BridgeProtocol.LED_GREEN:
                return LED.MODE_GREEN;
            case BridgeProtocol.LED_PINK:
                return LED.MODE_PINK;
            case BridgeProtocol.LED_PURPLE:
                return LED.MODE_PURPLE;
            case BridgeProtocol.LED_BLUE:
                return LED.MODE_BLUE;
            case BridgeProtocol.LED_YELLOW:
                return LED.MODE_YELLOW;
        }
        return null;
    }

    /**
     * Maps bridge protocol color name and flicker and random name values to Sanbot SDK @c LED mode
     * value.
     *
     * @param   color           bridge protocol color name
     * @param   flicker         flicker delay time in units of 100ms, or 0 to disable flicker mode
     * @param   random          number of colors in random mode, or 0 to disable random mode
     *
     * @return  Sanbot 'led mode' as @c Byte value, or @c LED.MODE_CLOSE if @p color is invalid
     */
    public static Byte toLedMode(Byte color, Integer flicker, Integer random)
    {
        if ((flicker != null) && (flicker > 0))
        {
            if ((random != null) && (random > 0)) return LED.MODE_FLICKER_RANDOM;

            if (color != null) return (byte)(color | 16);
        }

        return (color != null) ? color : LED.MODE_CLOSE;
    }

    /**
     * Maps a bridge protocol emotion name to a Sanbot SDK @c EmotionsType value.
     *
     * @param   emotion         bridge protocol emotion
     *
     * @return  Sanbot SDK emotion type as @c EmotionType value, or @c null if @p emotion is invalid
     */
    @Nullable
    public static EmotionsType mapEmotionsType(String emotion)
    {
        try
        {
            String normalized = (emotion != null) ? emotion.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.US) : "";
            if (normalized.isEmpty()) return null;
            return EmotionsType.valueOf(normalized);
        }
        catch (IllegalArgumentException ignored)
        {
            return null;
        }
    }

    /**
     * Maps bridge protocol language name to a Sanbot language identifier.
     *
     * @param   language        bridge protocol language name
     *
     * The following language name formats are accepted:
     * - language name, for instance @e english or @e italian
     * - ISO 639 two-letter language code, for instance @e en or @e it
     * - string starting with ISO 639 code followed by dash, for instance @e en-UK or @e it-IT
     *
     * @return  Sanbot SDK language identifiers, or @c null if @p language is invalid
     *
     * Supported language identifiers are defined as @c LAG_* constants in Sanbot @c SpeakOption
     * API.
     */
    @Nullable
    public static Integer mapSanbotLanguageId(String language)
    {
        String normalized = StringUtils.normalize(language);
        if (normalized == null) return null;

        // If language is locale code extract first two letters identify the language code.
        if ((normalized.length() >= 5) && (normalized.charAt(2) == '-')) normalized = normalized.substring(0, 2);

        switch (normalized)
        {
            case "english":
            case "en":
                return SpeakOption.LAG_ENGLISH_US;
            case "german":
            case "de":
                return SpeakOption.LAG_GERMAN;
            case "danish":
            case "da":
                return SpeakOption.LAG_DANISH;
            case "french":
            case "fr":
                return SpeakOption.LAG_FRENCH_FRANCE;
            case "italian":
            case "it":
                return SpeakOption.LAG_ITALIAN;
            case "spanish":
            case "es":
                return SpeakOption.LAG_SPANISH_SPAIN;
            case "portugese":
            case "pt":
                return SpeakOption.LAG_PORTUGUESE_PORTUGAL;
            case "polish":
            case "pl":
                return SpeakOption.LAG_POLISH;
            case "turkish":
            case "tr":
                return SpeakOption.LAG_TURKISH;
            case "chinese":
            case "zh":
                return SpeakOption.LAG_CHINESE;
            case "japanese":
            case "jp":
                return SpeakOption.LAG_JAPANESE;
            case "korean":
            case "ko":
                return SpeakOption.LAG_KOREAN;
            case "arabic":
            case "ar":
                return SpeakOption.LAG_ARABIC_INTERNATIONAL;
        }

        return null;
    }

    /**
     * Maps bridge protocol language name to a Sanbot language type.
     *
     * @param   language        bridge protocol language name
     *
     * The following language name formats are accepted:
     * - language name, for instance @e english or @e italian
     * - ISO 639 two-letter language code, for instance @e en or @e it
     * - string starting with ISO 639 code followed by dash, for instance @e en-UK or @e it-IT
     *
     * @return  Sanbot SDK language type, or @c null if @p language is invalid
     *
     * Supported language types are defined as @c LAG_* constants in Sanbot @c WakeUpOption API.
     */
    @Nullable
    public static String mapSanbotLanguageType(String language)
    {
        String normalized = StringUtils.normalize(language);
        if (normalized == null) return null;

        // If language is locale code extract first two letters identify the language code.
        if ((normalized.length() >= 5) && (normalized.charAt(2) == '-')) normalized = normalized.substring(0, 2);

        switch (normalized)
        {
            case "english":
            case "en":
                return WakeUpOption.LAG_ENGLISH_US;
            case "german":
            case "de":
                return WakeUpOption.LAG_GERMAN;
            case "danish":
            case "da":
                return WakeUpOption.LAG_DANISH;
            case "french":
            case "fr":
                return WakeUpOption.LAG_FRENCH_FRANCE;
            case "italian":
            case "it":
                return WakeUpOption.LAG_ITALIAN;
            case "spanish":
            case "es":
                return WakeUpOption.LAG_SPANISH_SPAIN;
            case "portugese":
            case "pt":
                return WakeUpOption.LAG_PORTUGUESE_PORTUGAL;
            case "polish":
            case "pl":
                return WakeUpOption.LAG_POLISH;
            case "turkish":
            case "tr":
                return WakeUpOption.LAG_TURKISH;
            case "chinese":
            case "zh":
                return WakeUpOption.LAG_CHINESE_HK;
            case "japanese":
            case "jp":
                return WakeUpOption.LAG_JAPANESE;
            case "korean":
            case "ko":
                return WakeUpOption.LAG_KOREAN;
            case "arabic":
            case "ar":
                return WakeUpOption.LAG_ARABIC_INTERNATIONAL;
        }

        return null;
    }

    /**
     * Converts Sanbot SDK alarm type code to human readable alarm type name.
     *
     * @param   type        numerical alarm type
     *
     * @return  human-readable alarm type name
     */
    @NonNull
    public static String toAlarmName(int type)
    {
        switch (type)
        {
            case 1:
                return "obstruction";
            case 2:
                return "intrusion";
            case 3:
                return "out-of-bounds";
        }
        return "unknown";
    }

    /**
     * Converts the Sanbot SDK numerical value of the battery status to a JSON string.
     *
     * The method returns a string representation of a JSON object containing the specified
     * numerical value of the battery status as well as a string representation of that value.
     *
     * @param   value           battery status
     *
     * @p value must be either 1, 2 or 3, all other values will be changed to 0 to indicate an @c
     * unknown mode.
     *
     * @return  string representations of JSON objet specifying battery status
     */
    @NonNull
    public static String toBatteryStatus(int value)
    {
        String mode;
        switch (value)
        {
            case 1:
                mode = BridgeProtocol.BATTERY_STATUS_NORMAL;
                break;
            case 2:
                mode = BridgeProtocol.BATTERY_STATUS_PILE;
                break;
            case 3:
                mode = BridgeProtocol.BATTERY_STATUS_LINE;
                break;
            default:
                value = 0;
                mode = BridgeProtocol.BATTERY_STATUS_UNKNOWN;
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("value", value);
        jsonObject.addProperty("status", mode);
        return jsonObject.toString();
    }

    /**
     * Returns an Android audio stream type name matching the numerical stream type.
     *
     * @param   streamType      Android audio stream type
     *
     * @return  human-readable stream name
     */
    public static String toAudioStream(int streamType)
    {
        switch (streamType)
        {
            case AUDIO_STREAM_TTS:
                return BridgeProtocol.STREAM_TTS;
            case AudioManager.STREAM_SYSTEM:
                return BridgeProtocol.STREAM_SYSTEM;
            case AudioManager.STREAM_ALARM:
                return BridgeProtocol.STREAM_ALARM;
            case AudioManager.STREAM_VOICE_CALL:
                return BridgeProtocol.STREAM_VOICE_CALL;
            case AudioManager.STREAM_RING:
                return BridgeProtocol.STREAM_RING;
            case AudioManager.STREAM_NOTIFICATION:
                return BridgeProtocol.STREAM_NOTIFICATION;
            case AudioManager.STREAM_MUSIC:
            default:
                return BridgeProtocol.STREAM_MUSIC;
        }
    }

    /**
     * Returns an numerical stream type matching the Android stream type name.
     *
     * @param   streamTypeName  audio stream type name
     *
     * @return  numerical stream type, or @c null if specified stream type name is invalid
     */
    @Nullable
    public static Integer toAudioStreamType(String streamTypeName)
    {
        String normalized = StringUtils.normalize(streamTypeName);
        if (normalized == null) return null;

        switch (normalized)
        {
            case "music":
                return AudioManager.STREAM_MUSIC;
            case "tts":
                return AUDIO_STREAM_TTS;
            case "system":
                return AudioManager.STREAM_SYSTEM;
            case "alarm":
                return AudioManager.STREAM_ALARM;
            case "voice_call":
                return AudioManager.STREAM_VOICE_CALL;
            case "ring":
                return AudioManager.STREAM_RING;
            case "notification":
                return AudioManager.STREAM_NOTIFICATION;
            default:
                return null;
        }
    }

    /**
     * Returns an touch sensor name matching the numerical sensor id.
     *
     * @param   sensorId        numerical sensor id
     *
     * @return  human-readable sensor name
     */
    public static String toTouchSensor(int sensorId)
    {
        if ((sensorId < 0) || (sensorId >= BridgeProtocol.TOUCH_SENSORS.length)) sensorId = 0;
        return BridgeProtocol.TOUCH_SENSORS[sensorId];
    }

    /**
     * Returns a passive infrared (PIR)  sensor name matching the numerical sensor id.
     *
     * @param   sensorId        numerical sensor id
     *
     * @return  human-readable sensor name
     */
    @NonNull
    public static String toPIRSensor(int sensorId)
    {
        return (sensorId == 1) ? "pir_front" : (sensorId == 2) ? "pir_back" : "invalid_sensor";
    }
}