/**
 * @file        BridgeProtocol.java
 * @brief       Implements BridgeProtocol class.
 */
package com.fbcti.sanbot.bridge.transport;

/**
 * Defines shared bridge service transport protocol constants.
 *
 * @version     1.0.002
 * @date        26 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class BridgeProtocol
{
    /**
     * @name Bridge Protocol Message Types
     *
     * The following messages are sent by clients to the bridge service
     * - @e info: request to robot to return information or status data (synchronous)
     * - @c query: request to robot to return information or status data (asynchronous)
     * - @e command: request to robot to perform action
     * - @e media: request to robot to stream media
     *
     * The following messages are sent by the bridge service to clients
     * - @e response: response to received @e info, @e command or @e media message
     * - @e event: unsolicited robot event message
     * @{ 
     */ 

    public static final String TYPE_INFO = "info";              ///< Info request type.
    public static final String TYPE_QUERY = "query";            ///< Query request type.
    public static final String TYPE_COMMAND = "command";        ///< Command request type.
    public static final String TYPE_MEDIA = "media";            ///< Short-lived media request type.
    public static final String TYPE_STREAM = "stream";          ///< Streaming media request type.
    public static final String TYPE_RESPONSE = "response";      ///< Response type.
    public static final String TYPE_EVENT = "event";            ///< Event type.

    /** @} */

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // FIELD TYPES
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String FIELD_ID = "id";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_MODULE = "module";
    public static final String FIELD_ACTION = "action";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_RESPONSE = "response";
    public static final String FIELD_EVENT = "event";
    public static final String FIELD_DATA = "data";
    public static final String FIELD_KEY = "key";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // RESPONSE TYPES
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String RESPONSE_ERROR = "error";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // EVENT MESSAGES
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String EVENT_PRESSED = "pressed";
    public static final String EVENT_RELEASED = "released";
    public static final String EVENT_DETECTED = "detected";
    public static final String EVENT_ALARM = "alarm";
    public static final String EVENT_CLEARED = "cleared";
    public static final String EVENT_SPEAK = "speak";
    public static final String EVENT_FACE = "face";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // DEFINITIONS FOR ROBOT MODULES
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String MODULE_ROBOT = "robot";
    public static final String MODULE_BRIDGE = "bridge";
    public static final String MODULE_BATTERY = "battery";
    public static final String MODULE_HEAD = "head";
    public static final String MODULE_ARMS = "arms";
    public static final String MODULE_FACE = "face";
    public static final String MODULE_LED = "led";
    public static final String MODULE_CAMERA = "camera";
    public static final String MODULE_SPEECH = "speech";
    public static final String MODULE_SENSOR = "sensor";
    public static final String MODULE_AUDIO = "audio";
    public static final String MODULE_VIDEO = "video";
    public static final String MODULE_SCREEN = "screen";
    public static final String MODULE_SCRIPT = "script";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // DEFINITIONS FOR ROBOT ACTIONS
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String ACTION_AUTHORIZE = "authorize";
    public static final String ACTION_OPENAPI = "openapi";
    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_SET = "set";
    public static final String ACTION_GET = "get";
    public static final String ACTION_RESET = "reset";
    public static final String ACTION_STATUS = "status";
    public static final String ACTION_CONFIG = "config";
    public static final String ACTION_FEATURES = "features";
    public static final String ACTION_UPLOAD = "upload";
    public static final String ACTION_REMOVE = "remove";
    public static final String ACTION_MOVE = "move";
    public static final String ACTION_LOCATION = "location";
    public static final String ACTION_WALK = "walk";
    public static final String ACTION_CHARGE = "charge";
    public static final String ACTION_CENTER = "center";
    public static final String ACTION_TURN = "turn";
    public static final String ACTION_NOD = "nod";
    public static final String ACTION_MODULAR = "modular";
    public static final String ACTION_EMOTION = "emotion";
    public static final String ACTION_WHITELIGHT = "whitelight";
    public static final String ACTION_LED = "led";
    public static final String ACTION_FACE = "face";
    public static final String ACTION_VOLUME = "volume";
    public static final String ACTION_PLAY = "play";
    public static final String ACTION_RECORD = "record";
    public static final String ACTION_SNAPSHOT = "snapshot";
    public static final String ACTION_VIDEO = "video";
    public static final String ACTION_PICTURE = "picture";
    public static final String ACTION_IMAGE = "image";
    public static final String ACTION_SAY = "say";
    public static final String ACTION_LISTEN = "listen";
    public static final String ACTION_ORIENTATION = "orientation";
    public static final String ACTION_INFRARED = "infrared";
    public static final String ACTION_LIST = "list";
    public static final String ACTION_TEST = "test";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // DEFINITIONS FOR ROBOT MOTION
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String SIDE_LEFT = "left";
    public static final String SIDE_RIGHT = "right";
    public static final String SIDE_BOTH = "both";
    public static final String DIRECTION_UP = "up";
    public static final String DIRECTION_DOWN = "down";
    public static final String DIRECTION_LEFT = "left";
    public static final String DIRECTION_RIGHT = "right";
    public static final String DIRECTION_FORWARD = "forward";
    public static final String DIRECTION_BACKWARD = "backward";
    public static final String DIRECTION_VERTICAL = "vertical";
    public static final String DIRECTION_HORIZONTAL = "horizontal";
    public static final String DIRECTION_LEFTUP = "left_up";
    public static final String DIRECTION_RIGHTUP = "right_up";
    public static final String DIRECTION_LEFTDOWN = "left_down";
    public static final String DIRECTION_RIGHTDOWN = "right_down";
    public static final String DIRECTION_LEFTFORWARD = "left_forward";
    public static final String DIRECTION_RIGHTFORWARD = "right_forward";
    public static final String DIRECTION_LEFTBACKWARD = "left_backward";
    public static final String DIRECTION_RIGHTBACKWARD = "right_backward";
    public static final String DIRECTION_LEFTTURN = "left_turn";
    public static final String DIRECTION_RIGHTTURN = "right_turn";
    public static final String DIRECTION_LEFTCIRCLE = "left_circle";
    public static final String DIRECTION_RIGHTCIRCLE = "right_circle";
    public static final String DIRECTION_STOPTURN = "stop_turn";
    public static final String DIRECTION_NONE = "none";
    public static final String DIRECTION_BOTH = "both";
    public static final String MODULAR_WANDER = "wander";
    public static final String MODULAR_FOLLOW = "follow";
    public static final String MODULAR_DUCKRUN = "duckrun";
    public static final String MODE_AUTO = "auto";
    public static final String MODE_MANUAL = "manual";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // ROBOT LEDS
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String LED_ALL = "all";
    public static final String LED_LEFT_HEAD = "left_head";
    public static final String LED_RIGHT_HEAD = "right_head";
    public static final String LED_LEFT_ARM = "left_arm";
    public static final String LED_RIGHT_ARM = "right_arm";
    public static final String LED_BASE = "base";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // LED COLORS
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String LED_OFF = "off";
    public static final String LED_WHITE = "white";
    public static final String LED_RED = "red";
    public static final String LED_GREEN = "green";
    public static final String LED_PINK = "pink";
    public static final String LED_PURPLE = "purple";
    public static final String LED_BLUE = "blue";
    public static final String LED_YELLOW = "yellow";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // BATTERY STATUSES
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String BATTERY_STATUS_NORMAL = "normal";
    public static final String BATTERY_STATUS_PILE = "charge_pile";
    public static final String BATTERY_STATUS_LINE = "charge_line";
    public static final String BATTERY_STATUS_UNKNOWN = "unknown";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // SENSOR TYPES
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String SENSOR_ALL = "all";
    public static final String SENSOR_TOUCH = "touch";
    public static final String SENSOR_ORIENTATION = "orientation";
    public static final String SENSOR_OBSTACLE = "obstacle";
    public static final String SENSOR_PASSIVEIR = "pir";
    public static final String SENSOR_ACTIVEIR = "infrared";
    public static final String SENSOR_VOICELOCATE = "voicelocate";


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // MEDIA STREAM TYPES
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String STREAM_MUSIC = "music";
    public static final String STREAM_TTS = "tts";
    public static final String STREAM_SYSTEM = "system";
    public static final String STREAM_ALARM = "alarm";
    public static final String STREAM_VOICE_CALL = "voice_call";
    public static final String STREAM_RING = "ring";
    public static final String STREAM_NOTIFICATION = "notification";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // AUDIO SOURCE TYPES
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static final int SOURCE_URL = 1;
    public static final int SOURCE_FILE = 2;

    /***********************************************************************************************
     * TOUCH SENSORS
     **********************************************************************************************/

    public static final String[] TOUCH_SENSORS = { "invalid_sensor",
        "right_chin", "left_chin", "chest_left", "chest_right", "head_back_left", "head_back_right",
        "back_left", "back_right", "hand_left", "hand_right", "head_top", "head_front_left", "head_front_right"
    };

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /** Private constructor preventing utility class instantiation. */
    private BridgeProtocol()
    {
    }
}
