/**
 * @file        BridgeRequestHandler.java
 * @brief       Implements BridgeRequestHandler class.
 */
package com.fbcti.sanbot.bridge.app;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.MediaResult;
import com.fbcti.sanbot.bridge.robot.mapping.SanbotMappings;
import com.fbcti.sanbot.bridge.robot.unit.BridgeCameraUnit;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.transport.BridgeRequest;
import com.fbcti.sanbot.bridge.transport.BridgeResponse;
import com.fbcti.sanbot.bridge.transport.JsonResponse;
import com.fbcti.sanbot.bridge.transport.MediaResponse;
import com.fbcti.sanbot.bridge.util.JsonUtils;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.fbcti.sanbot.bridge.util.ValueUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sanbot.opensdk.function.beans.EmotionsType;
import com.sanbot.opensdk.function.beans.headmotion.AbsoluteAngleHeadMotion;
import com.sanbot.opensdk.function.beans.headmotion.RelativeAngleHeadMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.DistanceWheelMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import com.sanbot.opensdk.function.beans.wing.AbsoluteAngleWingMotion;
import com.sanbot.opensdk.function.beans.wing.NoAngleWingMotion;
import com.sanbot.opensdk.function.beans.wing.RelativeAngleWingMotion;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dispatcher for bridge protocol requests.
 *
 * This class implements methods for dispatching protocol-neutral bridge @e info, @e query,
 * @e command and @e media requests to either the bridge service or directly to one of the bridge
 * units. Info and command request handlers all return a JsonResponse instance, media requests
 * return a MediaResponse instance.
 *
 * All requests specify the type (@c info, @c query, @c command, @c media), the robot module
 * responsible for returning the required information or executing an operation, and the actual
 * action to perform.
 *
 * Requests may have an additional JSON payload containing action parameters. Parameters may either
 * be fixed or flexible. Fixed parameters are parameters that are parsed and validated by the
 * request handlers implemented in this class and are passed to the unit that executes the request
 * as individual function parameters. Flexible parameters are parameters are passed in a Java @c
 * Map instance and must be parsed and validated by the unit itself. The parameter names in the
 * payload are assumed to be normalized, i.e. they are in lower case and do not contain leading or
 * trailing whitespace.
 *
 * @version     1.0.002
 * @date        26 sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class BridgeRequestHandler
{
    /**
     * Bridge service used to execute routed requests.
     */
    private final BridgeService service;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new BridgeRequestHandler instance.
     *
     * A reference to the specified BridgeService instance is copied to a member variable.
     *
     * @param   service         bridge service used to execute requests
     *
     * If @p service is equal to @c null, an exception is thrown.
     *
     * @throws  IllegalArgumentException thrown if @p service is @c null
     */
    public BridgeRequestHandler(BridgeService service)
    {
        if (service == null) throw new IllegalArgumentException("BridgeService instance may not be null");
        this.service = service;
    }

    /***********************************************************************************************
     * PACKAGE-PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Generic dispatcher for REST and WebSocket @e info requests.
     *
     * This dispatcher just calls the method matching the @c module and @c action request
     * parameters.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing requested information or error status
     */
    @NonNull
    JsonResponse handleInfoRequest(@NonNull BridgeRequest request)
    {
        try
        {
            // Handle 'info:bridge' requests.
            if (BridgeProtocol.MODULE_BRIDGE.equals(request.module))
            {
                if (BridgeProtocol.ACTION_OPENAPI.equals(request.action))
                    return infoBridgeOpenApi(request);
                if (BridgeProtocol.ACTION_STATUS.equals(request.action))
                    return infoBridgeStatus(request);
                if (BridgeProtocol.ACTION_CONFIG.equals(request.action))
                    return infoBridgeConfig(request);
            }

            // Handle 'info:sensor' requests.
            if (BridgeProtocol.MODULE_SENSOR.equals(request.module))
            {
                if (BridgeProtocol.ACTION_ORIENTATION.equals(request.action))
                    return infoSensorOrientation(request);
                if (BridgeProtocol.ACTION_INFRARED.equals(request.action))
                    return infoSensorInfrared(request);
            }

            // Handle 'info:camera' requests.
            if (BridgeProtocol.MODULE_CAMERA.equals(request.module))
            {
                if (BridgeProtocol.ACTION_FEATURES.equals(request.action))
                    return infoCameraFeatures(request);
            }

            // Handle 'info:audio' requests.
            if (BridgeProtocol.MODULE_AUDIO.equals(request.module))
            {
                if (BridgeProtocol.ACTION_VOLUME.equals(request.action))
                    return infoAudioVolume(request);
            }

            // Handle 'info:speech' requests.
            if (BridgeProtocol.MODULE_SPEECH.equals(request.module))
            {
                if (BridgeProtocol.ACTION_FEATURES.equals(request.action))
                    return infoSpeechFeatures(request);
                if (BridgeProtocol.ACTION_STATUS.equals(request.action))
                    return infoSpeechStatus(request);
            }

            // Handle `info:battery' requests.
            if (BridgeProtocol.MODULE_BATTERY.equals(request.module))
            {
                if (BridgeProtocol.ACTION_STATUS.equals(request.action))
                    return infoBatteryStatus(request);
            }

            // Handle `info:script' requests.
            if (BridgeProtocol.MODULE_SCRIPT.equals(request.module))
            {
                if (BridgeProtocol.ACTION_LIST.equals(request.action))
                    return infoScriptList(request);
            }

            // Request is unsupported.
            return JsonResponse.notFound(request, "unsupported request " + request.toString());
        }
        catch (RuntimeException e)
        {
            return JsonResponse.internalError(request, e.getClass().getSimpleName());
        }
    }

    /**
     * Generic dispatcher for REST and WebSocket (asynchronous) @e query requests.
     *
     * This dispatcher just calls the method matching the @c module and @c action request
     * parameters.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing requested information or error status
     *
     * Since the query request is asynchronous, the response does not contain the actual data that
     * was queried but just specifies if the query was successfully submitted. The actual data is
     * sent as an event.
     */
    @NonNull
    JsonResponse handleQueryRequest(@NonNull BridgeRequest request)
    {
        try
        {
            // Handle 'query:led' requests.
            if (BridgeProtocol.MODULE_LED.equals(request.module))
            {
                if (BridgeProtocol.ACTION_WHITELIGHT.equals(request.action))
                    return queryLedWhiteLight(request);
            }

            // Request is unsupported.
            return JsonResponse.notFound(request, "unsupported request " + request.toString());
        }
        catch (RuntimeException e)
        {
            return JsonResponse.internalError(request, e.getClass().getSimpleName());
        }
    }

    /**
     * Generic dispatcher for REST and WebSocket @e command requests.
     *
     * This dispatcher just calls the method matching the @c module and @c action request
     * parameters.
     *
     * @param   request         bridge handleCommandRequest request to handle
     *
     * @return  JsonResponse instance containing command result or error status
     */
    @NonNull
    JsonResponse handleCommandRequest(@NonNull BridgeRequest request)
    {
        try
        {
            // Handle 'command:robot' requests.
            if (BridgeProtocol.MODULE_ROBOT.equals(request.module))
            {
                if (BridgeProtocol.ACTION_MOVE.equals(request.action))
                    return commandRobotMove(request);
                if (BridgeProtocol.ACTION_WALK.equals(request.action))
                    return commandRobotWalk(request);
                if (BridgeProtocol.ACTION_TURN.equals(request.action))
                    return commandRobotTurn(request);
                if (BridgeProtocol.ACTION_MODULAR.equals(request.action))
                    return commandRobotModular(request);
                if (BridgeProtocol.ACTION_STOP.equals(request.action))
                    return commandRobotStop(request);
                if (BridgeProtocol.ACTION_RESET.equals(request.action))
                    return commandRobotReset(request);
                if (BridgeProtocol.ACTION_TEST.equals(request.action))
                    return commandRobotTest(request);
            }

            // Handle 'command:head' requests.
            if (BridgeProtocol.MODULE_HEAD.equals(request.module))
            {
                if (BridgeProtocol.ACTION_MOVE.equals(request.action))
                    return commandHeadMove(request);
                if (BridgeProtocol.ACTION_TURN.equals(request.action))
                    return commandHeadTurn(request);
                if (BridgeProtocol.ACTION_NOD.equals(request.action))
                    return commandHeadNod(request);
                if (BridgeProtocol.ACTION_LOCATION.equals(request.action))
                    return commandHeadLocation(request);
                if (BridgeProtocol.ACTION_STOP.equals(request.action))
                    return commandHeadStop(request);
                if (BridgeProtocol.ACTION_RESET.equals(request.action))
                    return commandHeadReset(request);
                if (BridgeProtocol.ACTION_CENTER.equals(request.action))
                    return commandHeadCenter(request);
                if (BridgeProtocol.ACTION_WHITELIGHT.equals(request.action))
                    return commandHeadWhiteLight(request);
                if (BridgeProtocol.ACTION_LED.equals(request.action))
                    return commandHeadLed(request);
            }

            // Handle 'command:arms' requests.
            if (BridgeProtocol.MODULE_ARMS.equals(request.module))
            {
                if (BridgeProtocol.ACTION_MOVE.equals(request.action))
                    return commandArmsMove(request);
                if (BridgeProtocol.ACTION_STOP.equals(request.action))
                    return commandArmsStop(request);
                if (BridgeProtocol.ACTION_RESET.equals(request.action))
                    return commandArmsReset(request);
                if (BridgeProtocol.ACTION_LED.equals(request.action))
                    return commandArmsLed(request);
            }

            // Handle 'command:led' requests.
            if (BridgeProtocol.MODULE_LED.equals(request.module))
            {
                if (BridgeProtocol.ACTION_SET.equals(request.action))
                    return commandLedSet(request);
            }

            // Handle 'command:face' requests.
            if (BridgeProtocol.MODULE_FACE.equals(request.module))
            {
                if (BridgeProtocol.ACTION_EMOTION.equals(request.action))
                    return commandFaceEmotion(request);
            }

            // Handle 'command:sensor' requests.
            if (BridgeProtocol.MODULE_SENSOR.equals(request.module))
            {
                if (BridgeProtocol.ACTION_CONFIG.equals(request.action))
                    return commandSensorConfig(request);
            }

            // Handle 'command:camera' requests.
            if (BridgeProtocol.MODULE_CAMERA.equals(request.module))
            {
                if (BridgeProtocol.ACTION_CONFIG.equals(request.action))
                    return commandCameraConfig(request);
                if (BridgeProtocol.ACTION_RESET.equals(request.action))
                    return commandCameraReset(request);
                if (BridgeProtocol.ACTION_SNAPSHOT.equals(request.action))
                    return commandCameraSnapshot(request);
                if (BridgeProtocol.ACTION_PICTURE.equals(request.action))
                    return commandCameraPicture(request);
                if (BridgeProtocol.ACTION_FACE.equals(request.action))
                    return commandCameraFace(request);
            }

            // Handle 'command:audio' requests.
            if (BridgeProtocol.MODULE_AUDIO.equals(request.module))
            {
                if (BridgeProtocol.ACTION_VOLUME.equals(request.action))
                    return commandAudioVolume(request);
                if (BridgeProtocol.ACTION_PLAY.equals(request.action))
                    return commandAudioPlay(request);
                if (BridgeProtocol.ACTION_RECORD.equals(request.action))
                    return commandAudioRecord(request);
                if (BridgeProtocol.ACTION_STOP.equals(request.action))
                    return commandAudioStop(request);
                if (BridgeProtocol.ACTION_LIST.equals(request.action))
                    return commandAudioList(request);
                if (BridgeProtocol.ACTION_REMOVE.equals(request.action))
                    return commandAudioRemove(request);
            }

            // Handle 'command:video' requests.
            if (BridgeProtocol.MODULE_VIDEO.equals(request.module))
            {
                if (BridgeProtocol.ACTION_RECORD.equals(request.action))
                    return commandVideoRecord(request);
                if (BridgeProtocol.ACTION_STOP.equals(request.action))
                    return commandVideoStop(request);
                if (BridgeProtocol.ACTION_LIST.equals(request.action))
                    return commandVideoList(request);
                if (BridgeProtocol.ACTION_REMOVE.equals(request.action))
                    return commandVideoRemove(request);
            }

            // Handle 'command:screen' requests.
            if (BridgeProtocol.MODULE_SCREEN.equals(request.module))
            {
                if (BridgeProtocol.ACTION_IMAGE.equals(request.action))
                    return commandScreenImage(request);
            }

            // Handle 'command:speech' requests.
            if (BridgeProtocol.MODULE_SPEECH.equals(request.module))
            {
                if (BridgeProtocol.ACTION_CONFIG.equals(request.action))
                    return commandSpeechConfig(request);
                if (BridgeProtocol.ACTION_SAY.equals(request.action))
                    return commandSpeechSay(request);
                if (BridgeProtocol.ACTION_STOP.equals(request.action))
                    return commandSpeechStop(request);
                if (BridgeProtocol.ACTION_LISTEN.equals(request.action))
                    return commandSpeechListen(request);
            }

            // Handle 'command:battery' requests.
            if (BridgeProtocol.MODULE_BATTERY.equals(request.module))
            {
                if (BridgeProtocol.ACTION_CONFIG.equals(request.action))
                    return commandBatteryConfig(request);
                if (BridgeProtocol.ACTION_CHARGE.equals(request.action))
                    return commandBatteryCharge(request);
            }

            // Handle 'command:script' requests.
            if (BridgeProtocol.MODULE_SCRIPT.equals(request.module))
            {
                if (BridgeProtocol.ACTION_UPLOAD.equals(request.action))
                    return commandScriptUpload(request);
                if (BridgeProtocol.ACTION_START.equals(request.action))
                    return commandScriptStart(request);
                if (BridgeProtocol.ACTION_STOP.equals(request.action))
                    return commandScriptStop(request);
            }

            // Request is unsupported.
            return JsonResponse.notFound(request, "unsupported request " + request.toString());
        }
        catch (RuntimeException e)
        {
            return JsonResponse.internalError(request, e.getClass().getSimpleName());
        }
    }

    /**
     * Generic dispatcher for REST and WebSocket @e media requests.
     *
     * This dispatcher just calls the method matching the @c module and @c action request
     * parameters.
     *
     * @param   request         bridge media request to handle
     *
     * @return  instance of @c MediaResponse class containing media data or error status
     */
    @NonNull
    MediaResponse handleMediaRequest(@NonNull BridgeRequest request)
    {
        try
        {
            // Handle 'media:camera' requests.
            if (BridgeProtocol.MODULE_CAMERA.equals(request.module))
            {
                if (BridgeProtocol.ACTION_PICTURE.equals(request.action))
                    return mediaCameraPicture(request);
                if (BridgeProtocol.ACTION_SNAPSHOT.equals(request.action))
                    return mediaCameraSnapshot(request);
                if (BridgeProtocol.ACTION_FACE.equals(request.action))
                    return mediaCameraFace(request);
            }

            // Handle 'media:audio' requests.
            if (BridgeProtocol.MODULE_AUDIO.equals(request.module))
            {
                if (BridgeProtocol.ACTION_GET.equals(request.action))
                    return mediaAudioGet(request);
            }

            // Handle 'media:video' requests.
            if (BridgeProtocol.MODULE_VIDEO.equals(request.module))
            {
                if (BridgeProtocol.ACTION_GET.equals(request.action))
                    return mediaVideoGet(request);
            }

            return MediaResponse.notFound(request, "unsupported request " + request.toString());
        }
        catch (RuntimeException e)
        {
            return MediaResponse.internalError(request, e.getClass().getSimpleName());
        }
    }

    /**
     * @name Info Request Handlers
     * Info requests are synchronous, the result object contains the actual response data.
     * @{
     */

    /**
     * Handles a request to return a list of available OpenAPI endpoints.
     *
     * The @e info:bridge:openapi request is forwarded to the bridge service. The payload for the
     * request is empty.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing OpenAPI information
     */
    @NonNull
    private JsonResponse infoBridgeOpenApi(@NonNull BridgeRequest request)
    {
        // Call bridge service method.
        return JsonResponse.bridgeResult(request, service.getOpenApiData());
    }

    /**
     * Handles a request to return the status for one or more bridge units.
     *
     * The @e info:bridge:status request is forwarded to the bridge service. The payload for the
     * request is
     * @code{.json}
     * {
     *      "unit": (optional) bridge unit name or array of bridge unit names
     * }
     * @endcode
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing status information
     *
     * Supported value of @c unit are
     * - @c bridge - current bridge application status
     * - @c system - current robot system status
     * - @c motion - current motion unit status
     * - @c led - current led unit status
     * - @c face - current face unit status
     * - @c sensor - current sensor unit status
     * - @c camera - current camera unit status
     * - @c audio - current audio unit status
     * - @c tts - current text-to-speech unit status
     * - @c asr - current speech recognition unit status
     *
     * If one or more units are specified only status information for those units is returned. If no
     * unit is specified, status information for all unit is returned. Unit names are normalized,
     * i.e. trimmed and converted to lower case. The list of unit names is not validated, invalid
     * entries will be ignored.
     */
    @NonNull
    private JsonResponse infoBridgeStatus(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        JsonElement jsonUnit = (payload != null) ? payload.get("unit") : null;

        // If unit property is not specified call bridge service method with empty unit list.
        if (jsonUnit == null) return JsonResponse.bridgeResult(request, service.getStatusData(new ArrayList<>()));

        // Parse unit property to a list of strings. If successful, call bridge service method.
        List<String> items = JsonUtils.toStringArray(jsonUnit, null);
        if (items != null)
        {
            List<String> normalizedItems = new ArrayList<>();
            for (String item : items)
            {
                if (StringUtils.isBlank(item) == false)
                    normalizedItems.add(StringUtils.normalize(item));
            }
            return JsonResponse.bridgeResult(request, service.getStatusData(normalizedItems));
        }

        // Parse unit property to a string. If successful, create array containing the one unit and
        // call bridge service method.
        String unit = JsonUtils.toString(jsonUnit, null);
        if (StringUtils.isBlank(unit) == false)
        {
            items = new ArrayList<>();
            items.add(StringUtils.normalize(unit));
            return JsonResponse.bridgeResult(request, service.getStatusData(items));
        }

        // Send error response.
        return JsonResponse.notAcceptable(request, "unit parameter must be a string or an array of strings");
    }

    /**
     * Handles a request to retrieve the bridge configuration data.
     *
     * The @e info:bridge:config request is forwarded to the bridge service. The payload for the
     * request is empty.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing configuration data
     */
    @NonNull
    private JsonResponse infoBridgeConfig(@NonNull BridgeRequest request)
    {
        // Call bridge service method.
        return JsonResponse.bridgeResult(request, service.getConfigData());
    }

    /**
     * Handles a request to return cached orientation data.
     *
     * The @e info:sensor:orientation request is forwarded to the sensor unit. The payload for the
     * request is empty. The returned data contains the yaw, pitch and roll that define how the
     * robot is orientated in the three-dimensional space.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing orientation data
     */
    @NonNull
    private JsonResponse infoSensorOrientation(@NonNull BridgeRequest request)
    {
        // Call sensor unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotSensorUnit().getOrientationData());
    }

    /**
     * Handles a request to return aggregated infrared sensor data.
     *
     * The @e info:sensor:infrared request is forwarded to the sensor unit. The payload for the
     * request is empty. The returned data  contains the number of values, last value, average
     * value, minimum value and maximum value for each of the seventeen passive infrared sensor that
     * fired during the configured reporting interval.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing infrared aggregate data
     */
    @NonNull
    private JsonResponse infoSensorInfrared(@NonNull BridgeRequest request)
    {
        // Call sensor unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotSensorUnit().getInfraredData());
    }

    /**
     * Handles a request to return a list of features supported by the specified camera.
     *
     * The @e info:camera:features request is forwarded to the sensor unit. The payload for the
     * request is
     *
     * @code{.json}
     * {
     *      "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec",
     *          "3d"]; see below
     * }
     * @endcode
     * The @c camera property is optional, if not specified the features for the currently selected
     * camera will be returned. The @c head, @c body and @c 3d values are aliases for the @c sanbot,
     * @c android, and @c orbbec cameras.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing infrared aggregate data
     */
    private JsonResponse infoCameraFeatures(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String camera = StringUtils.normalize(JsonUtils.getString(payload, "camera"));

        // Create Java map containing only flexible parameters.
        Map<String, Object> params = parseParams(payload, "camera");

        // Parse camera parameters and retrieve camera unit.
        String cameraName;
        if (camera == null) cameraName = service.getConfig().getCameraName();
        else
        {
            cameraName = BridgeCameraUnit.parseCameraParams(camera, params);
            if (cameraName == null) return JsonResponse.notAcceptable(request, "unsupported camera '" + camera + "'");
        }
        BridgeCameraUnit cameraUnit = service.getBridgeCameraUnit(cameraName);

        // Call camera unit method.
        return JsonResponse.bridgeResult(request, cameraUnit.getFeatures());
    }

    /**
     * Handles a request to return the volume for an Android audio stream.
     *
     * The @e info:audio:volume request is forwarded to the Android media unit. The payload for
     * request is
     * @code{.json}
     * {
     *      "stream": (optional) stream for which to retrieve volume; see below
     * }
     * @endcode
     * Supported streams are @c music, @c tts, @c system, @c alarm, @c voice_call, @c ring, and
     * @c notification. If no stream is specified the volume for a relevant subset of streams will
     * be returned. The returned data specifies the audio volumes as a percentage of the maximum
     * volume.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing speech feature information
     *
     */
    @NonNull
    private JsonResponse infoAudioVolume(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String stream = JsonUtils.getString(payload, "stream");

        // Validate stream property.
        Integer streamType = SanbotMappings.toAudioStreamType(stream);
        if ((StringUtils.isBlank(stream) == false) && (streamType == null))
            return invalidProperty(request, "stream", stream);

        // Call audio unit method.
        return JsonResponse.bridgeResult(request, service.getBridgeAudioUnit().getAudioVolume(streamType));
    }

    /**
     * Handles a request to return a list of features supported by the active speech platform(s).
     *
     * The @e info:speech:features request is forwarded to the speech unit. The payload for request
     * is empty. The returned data contains both the features provided by either the Sanbot or
     * native Android text-to-speech platform, and the features provided by the Sanbot speech
     * recognition platform.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing speech feature information
     */
    @NonNull
    private JsonResponse infoSpeechFeatures(@NonNull BridgeRequest request)
    {
        // Call service method.
        return JsonResponse.bridgeResult(request, service.buildSpeechFeatureData());
    }

    /**
     * Handles a request to return the status of the speech platform(s).
     *
     * The @e info:speech:status request is forwarded to the speech unit. The payload for request
     * is empty.
     *
     * This request is just an alias for the @e info:bridge:status request with the list of units
     * for which to retrieve the status containing both the text-to-speech and speech recognition
     * units.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing speech status information
     */
    @NonNull
    private JsonResponse infoSpeechStatus(@NonNull BridgeRequest request)
    {
        // Create list of units for which to return status.
        List<String> units = new ArrayList<>();
        units.add("tts");
        units.add("asr");

        // Call service method.
        return JsonResponse.bridgeResult(request, service.getStatusData(units));
    }

    /**
     * Handles a request to return the battery status.
     *
     * The @e info:battery:status request is forwarded to the bridge service. The payload for the
     * request is empty.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing status information
     */
    @NonNull
    private JsonResponse infoBatteryStatus(@NonNull BridgeRequest request)
    {
        // Call bridge service method.
        return JsonResponse.bridgeResult(request, service.getBatteryStatusData());
    }

    /**
     * Handles a request to return the list of available scripts.
     *
     * The @e info:script:list command is forwarded to the bridge service. The payload for the
     * request is empty.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing list of available scripts
     */
    @NonNull
    private JsonResponse infoScriptList(@NonNull BridgeRequest request)
    {
        // Call bridge service method.
        return JsonResponse.bridgeResult(request, service.getScriptList());
    }

    /** @} */

    /**
     * @name Query Request Handlers
     * Query requests are asynchronous, the result object only specifies if the query was
     * successfully submitted, not the actual response data.
     * @{
     */

    /**
     * Handles a request to query the status of the white light in the robot head.
     *
     * The @e query:led:whitelight is forwarded to the led unit. The payload for the request is
     * @code{.json}
     * {
     *      "refid": (optional) reference id for the asynchronous query result
     * }
     * @endcode
     * If @c refid is not specified the request id will be used as reference id.
     *
     * An @e event:led:whitelight event published as a result of this request will contain the
     * white light status.
     *
     * @param   request         bridge query request to handle
     *
     * @return  JsonResponse instance containing query submission result
     */
    @NonNull
    private JsonResponse queryLedWhiteLight(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String refid = JsonUtils.getString(payload, "refid");

        // If no reference id is specified use request id as reference id.
        if (StringUtils.isBlank(refid)) refid = request.id;

        // Call led unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotLedUnit().queryHeadLightBrightness(refid));
    }

    /** @} */

    /**
     * @name Command Request Handlers
     * @{
     */

    /**
     * Handles a request to move in the specified direction.
     *
     * The @e command:robot:move request is forwarded to the motion manager. The payload for the
     * request is
     * @code{.json}
     * {
     *      "direction": (mandatory) see below,
     *      "duration": (optional) duration in units of 100ms, default 0,
     *      "speed": (optional) speed in range [1..10], default 5
     * }
     * @endcode
     * Supported values of @c direction are.
     * - @c forward - moves the robot forward
     * - @c backward - move the robot backward
     * - @c left - move the robot left (without turning)
     * - @c right - move the robot right (without turning)
     * - @c left_forward - turn 45 degrees left and move forward
     * - @c right_forward - turn 45 degrees right and move forward
     * - @c left_backward - turn 135 degrees left and move backward
     * - @c right_backward - turn 135 degrees right and move backward
     * - @c left_turn - turn left (counter-clockwise)
     * - @c right_turn - turn right (clockwise)
     * - @c left_circle - circle left (counter-clockwise)
     * - @c right_circle - circle right (clockwise)
     * - @c stop_turn - stop turning
     * - @c stop - stop all movement
     * - @c reset - reset wheel motion
     *
     * The direction is converted to a Sanbot SDK wheel action. If @c duration is equal to 0
     * motion continues until explicitly stopped by a stop request.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandRobotMove(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String direction = JsonUtils.getString(payload, "direction");
        int duration = JsonUtils.getInteger(payload, "duration", 0);
        int speed = JsonUtils.getInteger(payload, "speed", 5);

        // Validate direction property and convert to Sanbot SDK wheel action.
        if (direction == null) return missingProperty(request, "direction");
        Byte action = SanbotMappings.mapNoAngleWheelMotionAction(direction);
        if (action == null) return invalidProperty(request, "direction", direction);

        // Validate speed and duration if action is not stop.
        if ((action != NoAngleWheelMotion.ACTION_STOP_TURN) && (action != NoAngleWheelMotion.ACTION_STOP))
        {
            if (duration < 0) return invalidProperty(request, "duration", duration);
            if ((speed < 1) || (speed > 10)) return invalidProperty(request, "speed", speed);
        }

        // Call motion unit method.
        DataResult result = service.getSanbotMotionUnit().moveRobotDuration(action, speed, duration);
        return JsonResponse.bridgeResult(request, result);
    }

    /**
     * Handles a request to move a specified distance forward or to stop moving forward.
     *
     * The @e command:robot:walk request is forwarded to the motion unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "distance": (mandatory) distance in centimeters,
     *      "speed": (optional) speed in range [1..10], default 5
     * }
     * @endcode
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse    instance containing operation result
     */
    @NonNull
    private JsonResponse commandRobotWalk(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        Integer distance = JsonUtils.getInteger(payload, "distance");
        int speed = JsonUtils.getInteger(payload, "speed", 5);

        // Validate distance property.
        if ((distance == null) || (distance < 0)) return invalidProperty(request, "distance", distance);

        // Validate speed property.
        if ((speed < 1) || (speed > 10)) return invalidProperty(request, "speed", speed);

        // Call motion unit method.
        DataResult result = service.getSanbotMotionUnit().moveRobotForward(DistanceWheelMotion.ACTION_FORWARD_RUN, speed, distance);
        return JsonResponse.bridgeResult(request, result);
    }

    /**
     * Handles a request to turn left or right by the specified angle.
     *
     * The @e command:robot:turn request is forwarded to the motion unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "direction": (mandatory) one of ["left", "right", "stop"],
     *      "angle": (mandatory) angle in degrees,
     *      "speed": (optional) speed in range [1..10], default 5
     * }
     * @endcode
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandRobotTurn(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String direction = JsonUtils.getString(payload, "direction");
        Integer angle = JsonUtils.getInteger(payload, "angle");
        int speed = JsonUtils.getInteger(payload, "speed", 5);

        // Validate direction property and convert to Sanbot SDK wheel action.
        if (direction == null) return missingProperty(request, "direction");
        Byte action = SanbotMappings.mapRelativeAngleWheelMotionAction(direction);
        if (action == null) return invalidProperty(request, "direction", direction);

        // Validate angle property.
        if ((angle == null) || (angle < 0) || (angle > 360)) return invalidProperty(request, "angle", angle);

        // Validate speed property.
        if ((speed < 1) || (speed > 10)) return invalidProperty(request, "speed", speed);

        // Call motion unit method.
        DataResult result = service.getSanbotMotionUnit().turnRobotByAngle(action, speed, angle);
        return JsonResponse.bridgeResult(request, result);
    }

    /**
     * Handles request to start or stop one of the modular motion modes.
     *
     * The @e command:robot:modular request is forwarded to the motion unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "mode": (mandatory) one of ["wander", "follow", "duckrun"],
     *      "action": (mandatory) one of ["start", "stop"],
     *      "info": (optional) additional modular mode info
     * }
     * @endcode
     * The role of the @c info property is unknown.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandRobotModular(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String mode = JsonUtils.getString(payload, "mode");
        String action = JsonUtils.getString(payload, "action");
        String info = JsonUtils.getString(payload, "info");

        // Validate mode parameter.
        if (mode == null) return missingProperty(request, "mode");

        // Convert action parameter to modular movement boolean.
        if (action == null) return missingProperty(request, "action");
        action = action.trim();
        boolean enable = BridgeProtocol.ACTION_START.equalsIgnoreCase(action);
        if ((enable == false) && (BridgeProtocol.ACTION_STOP.equalsIgnoreCase(action) == false)) return invalidProperty(request, "action", action);

        // Call motion unit method matching mode parameter.
        switch (mode)
        {
            case BridgeProtocol.MODULAR_WANDER:
                return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().switchRobotWanderMode(enable, info));
            case BridgeProtocol.MODULAR_FOLLOW:
                return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().switchRobotFollowMode(enable, info));
            case BridgeProtocol.MODULAR_DUCKRUN:
                return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().switchRobotDuckRunMode(enable, info));
            default:
                return invalidProperty(request, "mode", mode);
        }
    }

    /**
     * Handles a request to stop moving.
     *
     * The @e command:robot:stop request is forwarded to the motion unit. The payload for the
     * request is empty.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandRobotStop(@NonNull BridgeRequest request)
    {
        // Call motion unit method.
        DataResult result = service.getSanbotMotionUnit().stopMovingRobot();
        return JsonResponse.bridgeResult(request, result);
    }

    /**
     * Handles a request to reset the robot.
     *
     * The @e command:robot:reset request is forwarded to the bridge service. The payload for the
     * request is empty.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandRobotReset(@NonNull BridgeRequest request)
    {
        // Call bridge service method.
        return JsonResponse.bridgeResult(request, service.resetRobot());
    }

    /**
     * Handles a request to execute a test function (for development and testing only).
     *
     * The @e command:robot:test request is forwarded to the bridge service. The payload for the
     * request is
     * @code{.json}
     * {
     *      "func": (mandatory) bridge service test method name,
     *      "param1": (optional) first string parameter,
     *      ...
     *      "param9": (optional) ninth string parameter
     * }
     * @endcode
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandRobotTest(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String func = JsonUtils.getString(payload, "func");

        // Validate func parameter.
        if (func == null) return missingProperty(request, "func");

        // Create Java list containing string parameter values.
        List<String> params = new ArrayList<>();
        for (int i = 1; i <= 9; i++)
            params.add(JsonUtils.getString(payload, String.format("param%d", i)));

        // Call bridge service method.
        return JsonResponse.bridgeResult(request, service.test(func, params));
    }

    /**
     * Handles a request to move the head in the specified direction.
     *
     * The @e command:head:move request is forwarded to the motion unit. The payload for the request
     * is
     * @code{.json}
     * {
     *      "direction": (mandatory) direction in which to move; see below
     *      "angle": (mandatory for absolute movement, optional for relative movement) absolute or
     *          relative angle; see below
     * }
     * @endcode
     * Supported values of @c direction are.
     * - @c horizontal
     * - @c vertical
     * - @c left
     * - @c right
     * - @c up
     * - @c down
     * - @c left_up
     * - @c left_down
     * - @c right_up
     * - @c right_down
     *
     * If @c direction is either @c horizontal or @c vertical motion is absolute, i.e. the head is
     * moved @e to the specified angle. Absolute motion uses centered coordinates in range [-90..90]
     * for the horizontal orientation, and in range [-13..10] for the vertical orientation of the
     * head. For all other values of @c direction motion is relative, i.e. the head is moved @e by
     * the specified angle relative to the current orientation. For relative horizontal motion the
     * angle must be in range [0..180], for relative vertical and diagonal movement the angle must
     * be in range [0..25]. If @c angle is not specified for a cardinal relative direction, the head
     * moves in that direction until it reaches the extreme horizontal or vertical orientation.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     *
     */
    @NonNull
    private JsonResponse commandHeadMove(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String direction = JsonUtils.getString(payload, "direction");
        Integer angle = JsonUtils.getInteger(payload, "angle");

        // Validate direction property.
        if (direction == null) return missingProperty(request, "direction");

        // Convert the direction property to either an absolute or relative movement action. Either
        // one of the two must be not null.
        Byte actionAbsolute = SanbotMappings.mapAbsoluteAngleHeadMotionAction(direction);
        Byte actionRelative = SanbotMappings.mapRelativeAngleHeadMotionAction(direction);
        if ((actionAbsolute == null) && (actionRelative == null))
            return invalidProperty(request, "direction", direction);

        // Set a single character flags that specifies the orientation (vertical, horizontal or
        // diagonal).
        char orientation = 'd';
        if (actionRelative != null)
        {
            if ((actionRelative == RelativeAngleHeadMotion.ACTION_UP) || (actionRelative == RelativeAngleHeadMotion.ACTION_DOWN))
                orientation = 'v';
            else if ((actionRelative == RelativeAngleHeadMotion.ACTION_LEFT) || (actionRelative == RelativeAngleHeadMotion.ACTION_RIGHT))
                orientation = 'h';
        }
        else
        {
            if (actionAbsolute == AbsoluteAngleHeadMotion.ACTION_VERTICAL) orientation = 'v';
            else if (actionAbsolute == AbsoluteAngleHeadMotion.ACTION_HORIZONTAL) orientation = 'h';
        }

        // If the angle property is not set the direction must be specified as a relative movement
        // action. This is converted to an absolute movement action with the angle set to the
        // extreme value matching for that direction. Diagonal movement is not supported.
        if (angle == null)
        {
            if ((orientation == 'h') && (actionRelative != null))
            {
                angle = (actionRelative == RelativeAngleHeadMotion.ACTION_LEFT) ? 0 : 180;
                return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveHeadToAngle(AbsoluteAngleHeadMotion.ACTION_HORIZONTAL, angle));
            }
            else if ((orientation == 'v') && (actionRelative != null))
            {
                angle = (actionRelative == RelativeAngleHeadMotion.ACTION_DOWN) ? 7 : 30;
                return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveHeadToAngle(AbsoluteAngleHeadMotion.ACTION_VERTICAL, angle));
            }
            else
                return JsonResponse.notAcceptable(request, "direction '" + direction + "' not valid for no-angle movement");
        }

        // Validate the angle property. For absolute movement bridge protocol angle must be
        // converted to a Sanbot SDK angle.
        boolean absolute = (actionAbsolute != null);
        if (absolute) angle = SanbotMappings.mapHeadAngle(angle, (orientation == 'h'));
        if (validateHeadAngle(angle, absolute, (orientation == 'h')) == false) return invalidProperty(request, "angle", angle);

        // Call motion unit method.
        if (absolute)
            return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveHeadToAngle(actionAbsolute, angle));
        else
            return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveHeadByAngle(actionRelative, angle));
    }

    /**
     * Handles a request to turn the head (horizontal move).
     *
     * The @e command:head:turn request is forwarded to the motion unit. The payload for the request
     * is
     * @code{.json}
     * {
     *      "direction": (optional) one of ["left", "right"],
     *      "angle": (mandatory) absolute or relative angle; see below
     * }
     * @endcode
     * If @c direction is not specified motion is absolute, i.e. the head is moved @e to the
     * specified horizontal angle. Absolute motion uses centered coordinates in range [-90..90]. If
     * @c direction is specified, motion is relative and the head is moved @e by the specified
     * angle. Relative horizontal angles must be in range [0..180].
     *
     * @param   request bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandHeadTurn(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String direction = JsonUtils.getString(payload, "direction");
        Integer angle = JsonUtils.getInteger(payload, "angle");

        // Set flag specifying if movement is absolute or relative.
        boolean absolute = (direction == null);

        // Validate the angle property. For absolute movement the bridge protocol angle must be
        // converted to a Sanbot SDK angle.
        if (angle == null) return missingProperty(request, "angle");
        if (absolute) angle = SanbotMappings.mapHeadAngle(angle, true);
        if (validateHeadAngle(angle, absolute, true) == false) return invalidProperty(request, "angle", angle);

        // If direction property is not specified call motion unit method.
        if (absolute)
            return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveHeadToAngle(AbsoluteAngleHeadMotion.ACTION_HORIZONTAL, angle));

        // Validate direction property.
        Byte action = SanbotMappings.mapRelativeAngleHeadMotionAction(direction);
        if ((action == null) || ((action != RelativeAngleHeadMotion.ACTION_LEFT) && (action != RelativeAngleHeadMotion.ACTION_RIGHT)))
            return invalidProperty(request, "direction", direction);

        // Call motion unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveHeadByAngle(action, angle));
    }

    /**
     * Handles a request to nod the head (vertical move).
     *
     * The @e command:head:nod request is forwarded to the motion unit. The payload for the request
     * is
     * @code{.json}
     * {
     *      "direction": (optional) one of ["up", "down"],
     *      "angle": (mandatory) absolute or relative angle; see below
     * }
     * @endcode
     * If @c direction is not specified motion is absolute, i.e. the head is moved @e to the
     * specified vertical angle. Absolute motion uses centered coordinates in range [-13..10]. If
     * @c direction is specified, motion is relative and the head is moved @e by the specified
     * angle. Relative vertical angles must be in range [0..25].
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandHeadNod(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String direction = JsonUtils.getString(payload, "direction");
        Integer angle = JsonUtils.getInteger(payload, "angle");

        // Set flag specifying if movement is absolute or relative.
        boolean absolute = (direction == null);

        // Validate the angle property. For absolute movement the bridge protocol angle must be
        // converted to a Sanbot SDK angle.
        if (angle == null) return missingProperty(request, "angle");
        if (absolute) angle = SanbotMappings.mapHeadAngle(angle, false);
        if (validateHeadAngle(angle, absolute, false) == false) return invalidProperty(request, "angle", angle);

        // If direction property is not specified call motion unit method.
        if (direction == null)
            return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveHeadToAngle(AbsoluteAngleHeadMotion.ACTION_VERTICAL, angle));

        // Validate direction property.
        Byte action = SanbotMappings.mapRelativeAngleHeadMotionAction(direction);
        if ((action == null) || ((action != RelativeAngleHeadMotion.ACTION_UP) && (action != RelativeAngleHeadMotion.ACTION_DOWN)))
            return invalidProperty(request, "direction", direction);

        // Call motion unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveHeadByAngle(action, angle));
    }

    /**
     * Handles a request to move the head to the specified absolute location.
     *
     * The @e command:head:location request is forwarded to the motion unit. The payload for the request
     * is
     * @code{.json}
     * {
     *      "hangle": (mandatory) absolute horizontal angle,
     *      "vangle": (mandatory) absolute vertical angle,
     *      "lock": (optional) lock mode, one of ["none", "horizontal", "vertical", "both"],
     *          default "none"
     * }
     * @endcode
     * The location is specified by centered coordinates. Horizontal angles are in range [-90..90],
     * vertical angles in range [-13..10].
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    private JsonResponse commandHeadLocation(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        Integer hangle = JsonUtils.getInteger(payload, "hangle");
        Integer vangle = JsonUtils.getInteger(payload, "vangle");
        String lock = JsonUtils.getString(payload, "lock", "none");

        // Horizontal and verticla angles must be specified.
        if (hangle == null) return missingProperty(request, "hangle");
        if (vangle == null) return missingProperty(request, "vangle");

        // Convert horizontal angle from centered coordinates to Sanbot coordinates and validate.
        int hangleSanbot = SanbotMappings.mapHeadAngle(hangle, true);
        if (validateHeadAngle(hangleSanbot, true, true) == false)
            return JsonResponse.notAcceptable(request, "hangle must be in range [-90..90]");

        // Convert vertical angle from centered coordinates to Sanbot coordinates and validate.
        int vangleSanbot = SanbotMappings.mapHeadAngle(vangle, false);
        if (validateHeadAngle(vangleSanbot, true, false) == false)
            return JsonResponse.notAcceptable(request, "vangle hangle must be in range [-10..13]");

        // Convert the lock property to a lock action.
        Byte action = SanbotMappings.mapLocateAbsoluteAngleHeadMotionAction(lock);
        if (action == null) return invalidProperty(request, "lock", lock);

        // Call motion unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveHeadToAbslutePosition(action, hangleSanbot, vangleSanbot));
    }

    /**
     * Handles a request to stop moving the head.
     *
     * The @e command:head:stop request is forwarded to the motion unit. The payload for the request
     * is empty.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandHeadStop(@NonNull BridgeRequest request)
    {
        // Call motion unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().stopMovingHead());
    }

    /**
     * Handles a request to move head to the central position.
     *
     * The @e command:head:reset request is forwarded to the motion unit. The payload for the
     * request is empty.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandHeadReset(@NonNull BridgeRequest request)
    {
        // Call motion unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().resetHead());
    }

    /**
     * Handles a request to lock the head in the central position.
     *
     * The @e command:head:center request is forwarded to the motion unit. The payload for the
     * request is empty.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandHeadCenter(@NonNull BridgeRequest request)
    {
        // Call motion unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().lockHeadInCentralPosition());
    }

    /**
     * Handles a request to switch the white light in the head on or off or set the brightness.
     *
     * The @e command:head:whitelight request is forwarded to the led unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "on": (optional) flag specifying if light is switched on or off, one of [true, false],
     *      "brightness": (optional) 1 (least bright), 2 or 3 (most bright)
     * }
     * @endcode
     * Both @c on and @c brightness are marked as optional, but at least one of the two must be
     * specified.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandHeadWhiteLight(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());

        // Call generic method for setting white light status.
        return applyWhiteLightStatus(request, payload);
    }

    /**
     * Handles a request to control the leds in the robot head.
     *
     * The applyLedStatus() method is called to control the leds. The payload for the
     * @e command:head:led request is
     * @code{.json}
     * {
     *      "side": (mandatory) one of ["left", "right"],
     *      "color": (mandatory) either "off", or one of ["white", "red", "green", "pink", "purple",
     *          "blue", "yellow"]
     *      "flicker": (optional) flicker delay time in units of 100ms, 0 to disable flicker mode,
     *      "random": (optional) number of colors in random mode, 0 to disable random mode
     * }
     * @endcode
     * The @c side property is converted to a Sanbot SDK part parameter.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandHeadLed(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String side = JsonUtils.getString(payload, "side");

        // Convert bridge protocol side property to Sanbot SDK part.
        if (side == null) return missingProperty(request, "side");
        Byte part = SanbotMappings.mapHeadLedPart(side);
        if (part == null) return invalidProperty(request, "side", side);

        // Call generic method for setting led status.
        return applyLedStatus(request, payload, part);
    }

    /**
     * Handles a request to move one or both arms.
     *
     * The @e command:arms:move request is forwarded to the motion unit. The payload for the request
     * is
     * @code{.json}
     * {
     *      "side": (optional) one of ["left", "right", "both"],
     *      "direction": (optional) one of ["up", "down"],
     *      "angle": (optional) absolute or relative angle in range [0..270],
     *      "speed": (optional) speed in range range [1..8], default 5
     * }
     * @endcode
     * If @c side is not specified, both arms will move. If @c angle is not specified, @c direction
     * is mandatory and one or both arms are moved in that direction until the extreme position is
     * reached. If @c angle is specified but @c direction is not specified motion is absolute, i.e.
     * the arm(s) move @e to the specified angle. If both @c angle and @c direction are specified,
     * the arm(s) move @e by the specified angle relative to the current position. Angles must be in
     * the range [0..270], with 0 represents the arms pointing up, and 270 the arms pointing
     * backwards.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandArmsMove(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String side = JsonUtils.getString(payload, "side");
        String direction = JsonUtils.getString(payload, "direction");
        Integer angle = JsonUtils.getInteger(payload, "angle");
        int speed = JsonUtils.getInteger(payload, "speed", 5);

        // Validate speed property.
        if ((speed < 1) || (speed > 8)) return invalidProperty(request, "speed", speed);

        // If angle property is not set request no-angle movement.
        if (angle == null)
        {
            // Validate and convert side property to Sanbot SDK part for no-angle movement.
            Byte part = (side != null) ? SanbotMappings.mapNoAngleWingMotionSide(side) : Byte.valueOf(NoAngleWingMotion.PART_BOTH);
            if (part == null) return invalidProperty(request, "side", side);

            // Validate and convert direction property to Sanbot SDK action.
            if (direction == null)
                return JsonResponse.notAcceptable(request, "direction must be specified for for no-angle movement");
            Byte action = SanbotMappings.mapNoAngleWingMotionAction(direction);
            if (action == null)
                return JsonResponse.notAcceptable(request, "direction '" + direction + "' not valid for no-angle movement");

            // Call bridge service method to request no-angle movement.
            return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveArms(part, speed, action));
        }

        // Validate angle property.
        if ((angle < 0) || (angle > 270)) return invalidProperty(request, "angle", angle);

        if (direction == null)
        {
            // Validate and convert side property to Sanbot SDK part for absolute movement.
            Byte part = (side != null) ? SanbotMappings.mapAbsoluteAngleWingMotionPart(side) : Byte.valueOf(AbsoluteAngleWingMotion.PART_BOTH);
            if (part == null) return invalidProperty(request, "side", side);

            // Call motion unit method to request absolute movement.
            return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveArmsToAngle(part, speed, angle));
        }

        // Validate and convert side property to Sanbot SDK part for relative movement.
        Byte part = (side != null) ? SanbotMappings.mapRelativeAngleWingMotionPart(side) : Byte.valueOf(RelativeAngleWingMotion.PART_BOTH);
        if (part == null) return invalidProperty(request, "side", side);

        // Validate and convert direction property to Sanbot SDK action for relative movement.
        Byte action = SanbotMappings.mapRelativeAngleWingMotionAction(direction);
        if (action == null) return invalidProperty(request, "direction", direction);

        // Call motion unit method to request relative movement.
        return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().moveArmsByAngle(part, speed, action, angle));
    }

    /**
     * Handles a request to stop moving the arm(s).
     *
     * The @e command:arms:stop request is forwarded to the motion unit. The payload for the request
     * is
     * @code{.json}
     * {
     *      "side": (optional) one of ["left", "right", "both"]; see below
     * }
     * @endcode
     * If @c side is not specified both arms will stop moving.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandArmsStop(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String side = JsonUtils.getString(payload, "part");

        // Validate and convert part property to Sanbot SDK part.
        Byte part = (side != null) ? SanbotMappings.mapNoAngleWingMotionSide(side) : Byte.valueOf(NoAngleWingMotion.PART_BOTH);
        if (part == null) return invalidProperty(request, "side", side);

        // Call motion unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().stopMovingArms(part));
    }

    /**
     * Handles a request to reset one or both arms to the default position.
     *
     * The @e command:arms:reset request is forwarded to the motion unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "side": (optional) one of ["left", "right", "both"],
     *      "speed": (optional) speed [1..8], default 5
     * }
     * @endcode
     * If @c side is not specified both arms will be reset to the default position.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandArmsReset(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String side = JsonUtils.getString(payload, "part");
        int speed = JsonUtils.getInteger(payload, "speed", 5);

        // Validate speed property.
        if ((speed < 1) || (speed > 8)) return invalidProperty(request, "speed", speed);

        // Validate and convert 'part' property to Sanbot SDK part.
        Byte part = (side != null) ? SanbotMappings.mapNoAngleWingMotionSide(side) : Byte.valueOf(NoAngleWingMotion.PART_BOTH);
        if (part == null) return invalidProperty(request, "side", side);

        // Call motion unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotMotionUnit().resetArms(part, speed));
    }

    /**
     * Handles a request to control the leds in the robot arms.
     *
     * The applyLedStatus() method is called to control the leds. The payload for the
     * @e command:head:led request is
     * @code{.json}
     * {
     *      "side": (mandatory) one of ["left", "right"],
     *      "color": (mandatory) either "off", or one of ["white", "red", "green", "pink", "purple",
     *          "blue", "yellow"]
     *      "flicker": (optional) flicker delay time in units of 100ms, 0 to disable flicker mode,
     *      "random": (optional) number of colors in random mode, 0 to disable random mode
     * }
     * @endcode The @c side property is converted to a Sanbot SDK part parameter.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandArmsLed(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String side = JsonUtils.getString(payload, "side", BridgeProtocol.LED_ALL);

        // Convert bridge protocol side property to Sanbot SDK part.
        if (side == null) return missingProperty(request, "side");
        Byte part = SanbotMappings.mapWingLedPart(side);
        if (part == null) return invalidProperty(request, "side", side);

        // Call generic method for string led status.
        return applyLedStatus(request, payload, part);
    }

    /**
     * Handles a request to control one or more leds.
     *
     * Depending on whether the white light or the head, arms or wheel leds must be controller the
     * applyWhiteLightStatus() or applyLedStatus() method is called to control the leds. The payload
     * for the @e command:led:set request is
     * @code{.json}
     * {
     *      "part": (mandatory) one of [ "whitelight", "base", "left_head", "right_head",
     *          "left_arm", "right_arm", "all" ]
     *      "on": (optional) flag specifying if light is switched on or off, one of [true, false],
     *      "brightness": (optional) 1 (least bright), 2 or 3 (most bright)
     *      "color": (optional) either "off", or one of ["white", "red", "green", "pink", "purple",
     *          "blue", "yellow"]
     *      "flicker": (optional) flicker delay time in units of 100ms, 0 to disable flicker mode,
     *      "random": (optional) number of colors in random mode, 0 to disable random mode
     * }
     * @endcode
     * The @c on and @c brightness properties are only relevant if @c part equals @c whitelight. The
     * @c color, @c flicker and @c random properties are only relevant for the other @c part values.
     *
     * @param   request     bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    private JsonResponse commandLedSet(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String part = JsonUtils.getString(payload, "part");

        // If part is whitelight call generic method for setting white light status.
        if ((part != null) && (part.equalsIgnoreCase(BridgeProtocol.ACTION_WHITELIGHT)))
            return applyWhiteLightStatus(request, payload);

        // Validate part property.
        Byte bytePart = (part != null) ? SanbotMappings.mapLedPart(part) : null;
        if (bytePart == null) return invalidProperty(request, "part", part);

        // Call generic method for string led status.
        return applyLedStatus(request, payload, bytePart);
    }

    /**
     * Handles a request to set the face emotion.
     *
     * The @e command:face:emotion request is forwarded to the emotion unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "emotion": (optional) emotion to display,
     *      "duration": (optional) duration of emotion in seconds,
     *      "refresh": (optional) refresh timer for emotion update
     * }
     * @endcode
     * Supported values for @c emotion are
     * - @c angry
     * - @c cry
     * - @c excitement
     * - @c faint
     * - @c goodbye
     * - @c grievance
     * - @c kiss
     * - @c laughter
     * - @c normal
     * - @c picknose
     * - @c prise
     * - @c question
     * - @c shy
     * - @c sleep
     * - @c smile
     * - @c snicker
     * - @c speak
     * - @c surprise
     * - @c sweat
     * - @c whistle
     *
     * If @c emotion is not specified the default emotion is set.  If no @c duration is specified or
     * @c duration is equal to 0, the emotion will fall back to its default value after about ten
     * seconds. If @c duration is specified and larger than 0, the emotion will be refreshed every
     * @c refresh seconds to make the emotion persistent for the specified duration. The refresh
     * time must be no longer than 30 seconds, if not specified it is set to 4 seconds.
     *
     * @param   request         bridge command request to handle
     *
     * @return  instance of JsonResponse containing operation result
     */
    @NonNull
    private JsonResponse commandFaceEmotion(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String emotion = JsonUtils.getString(payload, "emotion");
        Integer duration = JsonUtils.getInteger(payload, "duration");
        int refresh = JsonUtils.getInteger(payload, "refresh", 4);

        // Validate emotion property.
        EmotionsType emotionsType = SanbotMappings.mapEmotionsType(emotion);
        if (emotionsType == null)
        {
            if (emotion != null) return invalidProperty(request, "emotion", emotion);
            else emotionsType = EmotionsType.NORMAL;
        }

        // If duration property is not specified or is zero, the emotion is non-persistent.
        int emotionDuration = (duration != null) ? duration : 0;
        if (emotionDuration > 0)
        {
            if ((refresh < 0) || (refresh > 30))
                return invalidProperty(request, "refresh", refresh);
            if (refresh >= emotionDuration)
                return JsonResponse.notAcceptable(request, "'refresh' must be smaller than 'duration'");
        }

        // Call emotion unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotFaceUnit().setEmotion(emotionsType, emotionDuration, refresh));
    }

    /**
     * Handles a request to start or stop receiving events from one or more sensors.
     *
     * The @e command:sensor:config request is forwarded to the sensor unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "reset": (optional) reset parameters to default values, one of [true, false],
     *      "reload": (optional) reload parameters from configuration file, one of [true, false],
     *      "save": (optional) save updated parameters, one of [true, false],
     *      "all": one of [true, false] or { "enable: true/false }; see below,
     *      "touch": one of [true, false] or { "enable: true/false }; see below,
     *      "orientation": enabled/disabled or { "enable: enabled/disabled, ... }; see below,
     *      "obstacle": one of [true, false] or { "enable: true/false }; see below,
     *      "pir": one of [true, false] or { "enable: true/false }; see below,
     *      "infrared": one of [true, false] or { "enable: true/false, ... }; see below,
     *      "voicelocate": one of [true, false] or { "enable: true/false }; see below
     * }
     * @endcode
     * If the @c reset or @c reload property has value @c true, all sensor settings are either reset
     * to their default values or reloaded from the configuration file, ignoring all other payload
     * properties. The @c reset property takes precedence over the @c reload property. In all other
     * cases the specified parameters are passed to the sensor unit can interpret them. If the
     * @c save property has configuration file, if @c false (or not specified), the settings will
     * remain only active value @c true, the updated values will be written to the until updated
     * again or the bridge service is restarted.
     *
     * For each sensor or group of sensors, the parameter value can be a scalar value representing
     * the enabled/disabled state, or a JSON object that contains an optional @c enable property
     * that specifies the enabled/disabled state plus additional sensor-specific parameters. The
     * scalar enabled/disabled state can be a boolean (@c true/@c false), number (1/0) or string
     * ("true"/"false", "1"/"0", "enable"/"disable" or "enabled"/"disabled"). Sensor parameters are
     * passed to the sensor unit as a Java @c Map instance that itself contains a @c Map instance
     * for each sensor for which parameters are to be set. If the request payload contains a scalar
     * enabled/disabled state the map for the sensor or group of sensors just contains the @c enable
     * parameter, with the scalar enable/disable state converted to an actual boolean. If the
     * request payload contains a JSON object value, the properties in that JSON object are copied
     * to the map for that sensor or group of sensors. If the JSON object includes the @c enable
     * property, the value is replaced by the matching boolean value.
     *
     * @param   request     bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandSensorConfig(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        boolean reset = JsonUtils.getBoolean(payload, "reset", false);
        boolean reload = JsonUtils.getBoolean(payload, "reload", false);
        boolean save = JsonUtils.getBoolean(payload, "save", false);

        // Remove fixed parameters.
        JsonUtils.remove(payload, "reset");
        JsonUtils.remove(payload, "reload");
        JsonUtils.remove(payload, "save");

        // Create Java map containing sensor parameters.
        Map<String, Object> params = new LinkedHashMap<>();
        if (payload != null)
        {
            for (Map.Entry<String, JsonElement> entry : payload.entrySet())
            {
                // If a property value is a JSON object convert to Java map and add to parameter map.
                if (entry.getValue().isJsonObject())
                    params.put(entry.getKey(), parseParams(entry.getValue().getAsJsonObject()));
                else
                {
                    // If the value is a JSON primitive it a new Java map is created containing just
                    // the enable parameter with the value of that property.
                    if (entry.getValue().isJsonPrimitive())
                    {
                        Object value = JsonUtils.parseObject(entry.getValue());
                        params.put(entry.getKey(), MapUtils.createMap("enable", ValueUtils.toBoolean(value)));
                    }
                }
            }
        }

        // Call sensor unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotSensorUnit().updateConfig(reset, reload, save, params));
    }

    /**
     * Handles a request to update the configuration parameters for the specified camera.
     *
     * The @e command:camera:config request is forwarded to one selected camera unit. The payload
     * request is
     * @code{.json}
     * {
     *      "camera": (optional) camera name, one of ["sanbot", "head", "android", "body",
     *          "orbbec", "3d"]; see below,
     *      "active": (optional) make specified camera the active camera, one of [true, false],
     *      "reset": (optional) reset parameters to default values, one of [true, false],
     *      "reload": (optional) reload parameters from configuration file, one of [true, false],
     *      "save": (optional) save updated parameters, one of [true, false],
     *      "<flex1>": (optional) flexible parameter,
     *      "<flex2>": (optional) flexible parameter,
     *      ...
     * }
     * @endcode
     * The @c camera property is mandatory and must specify a supported camera. The @c camera
     * property is optional, if not specified the default camera will be used. The @c head, @c body
     * and @c 3d values are aliases for the @c sanbot, @c android, and @c orbbec cameras. If the
     * @c active property is @c true, the specified camera is set as active camera. If either
     * @c reset or @c reload is @c true, camera parameters are either reset to their default values
     * or reloaded from the configuration file. The @c reset and @c reload properties are mutually
     * exclusive; only the first property set to @c true is applied. If both are @c false, the
     * camera unit is responsible for updating the active camera parameters with the supplied
     * flexible parameter values. If the @c save property is @c true, the reset, reloaded or updated
     * parameters are made persistent by writing them to the configuration file. If @c save is
     * @c false or not specified, the parameters will be semi-persistent and only remain active
     * until they are updated again or the bridge service is restarted.
     *
     * @param   request         bridge command request to handle
     *
     * @return  instance of @c JsonResponse containing operation result
     */
    @NonNull
    private JsonResponse commandCameraConfig(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String camera = JsonUtils.getString(payload, "camera");
        boolean active = JsonUtils.getBoolean(payload, "active", false);
        boolean reset = JsonUtils.getBoolean(payload, "reset", false);
        boolean reload = JsonUtils.getBoolean(payload, "reload", false);
        boolean save = JsonUtils.getBoolean(payload, "save", false);

        // Create Java map containing only flexible parameters.
        Map<String, Object> params = parseParams(payload, "camera", "active", "reset", "reload", "save");

        // Parse camera parameters and retrieve camera unit.
        String cameraName;
        if (camera == null) cameraName = service.getConfig().getCameraName();
        else
        {
            cameraName = BridgeCameraUnit.parseCameraParams(camera, params);
            if (cameraName == null) return invalidProperty(request, "camera", camera);
        }
        BridgeCameraUnit cameraUnit = service.getBridgeCameraUnit(cameraName);

        // Call camera unit method.
        return JsonResponse.bridgeResult(request, cameraUnit.updateSettings(active, reset, reload, save, params));
    }

    /**
     * Handles a request to reset the specified camera,
     *
     * The @e command:camera:reset request is forwarded to one selected camera unit. The payload
     * request is
     * @code{.json}
     * {
     *      "camera": (optional) camera name, one of ["sanbot", "head", "android", "body",
     *          "orbbec", "3d"]; see below
     * }
     * @endcode
     * The @c camera property is optional, if not specified the default camera will be used. The
     * @c "head", @c "body" and @c "3d" values are aliases for the @c "sanbot", @c "android", and
     * @c "orbbec" cameras.
     *
     * @param   request         bridge command request to handle
     *
     * @return  instance of @c JsonResponse containing operation result
     */
    @NonNull
    private JsonResponse commandCameraReset(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String camera = JsonUtils.getString(payload, "camera");

        // Create Java map containing only flexible parameters.
        Map<String, Object> params = parseParams(payload, "camera");

        // Parse camera parameters and retrieve camera unit.
        String cameraName;
        if (camera == null) cameraName = service.getConfig().getCameraName();
        else
        {
            cameraName = BridgeCameraUnit.parseCameraParams(camera, params);
            if (cameraName == null) return invalidProperty(request, "camera", camera);
        }
        BridgeCameraUnit cameraUnit = service.getBridgeCameraUnit(cameraName);

        // Call camera unit method.
        return JsonResponse.bridgeResult(request, cameraUnit.reset());
    }

    /**
     * Handles a request to capture a snapshot image as Base-64 encoded data.
     *
     * The @e command:camera:snapshot request is forwarded to the selected camera unit. The payload
     * for the request is
     * @code{.json}
     * {
     *      "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec",
     *          "3d"]; see below,
     *      "<flex1>": (optional) flexible parameter,
     *      "<flex2>": (optional) flexible parameter,
     *      ...
     * }
     * @endcode
     * The @c camera property is optional, if not specified the default camera will be used. The
     * @c head, @c body and @c 3d values are aliases for the @c sanbot, @c android, and @c orbbec
     * cameras. The camera name may be followed by an optional sensor name, separated from the
     * camera name by a dash. The sensor name is extracted from the camera name and added to the
     * flexible parameters if not already present. Currently the only camera supporting multiple
     * sensors is the Orbbec camera, with sensors @c color, @c ir and @c depth. For instance, if the
     * camera is specified as @c orbbec-ir the Orrbec camera will capture an infrared image.
     *
     * The camera unit is responsible for updating the active camera parameters with the supplied
     * flexible parameter values. The parameters will be semi-persistent and only remain active
     * until they are updated again or the bridge service is restarted.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing Base-64 image data
     */
    @NonNull
    private JsonResponse commandCameraSnapshot(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String camera = JsonUtils.getString(payload, "camera");

        // Create Java map containing only flexible parameters.
        Map<String, Object> params = parseParams(payload, "camera");

        // Parse camera parameters and retrieve camera unit.
        String cameraName;
        if (camera == null) cameraName = service.getConfig().getCameraName();
        else
        {
            cameraName = BridgeCameraUnit.parseCameraParams(camera, params);
            if (cameraName == null) return invalidProperty(request, "camera", camera);
        }
        BridgeCameraUnit cameraUnit = service.getBridgeCameraUnit(cameraName);

        // Call camera unit method.
        return JsonResponse.bridgeResult(request, cameraUnit.getSnapshotBase64(params));
    }

    /**
     * Handles a request to capture a still image as Base-64 encoded data.
     *
     * The @e command:camera:picture request is forwarded to the selected camera unit. The payload for
     * the request is
     * @code{.json}
     * {
     *      "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec",
     *          "3d"]
     *      "<flex1>": (optional) flexible parameter,
     *      "<flex2>": (optional) flexible parameter,
     *      ...
     * }
     * @endcode
     * The @c camera property is optional, if not specified the default camera will be used. The
     * @c head, @c body and @c 3d values are aliases for the @c sanbot, @c android, and @c orbbec
     * cameras. The camera name may be followed by an optional sensor name, separated from the
     * camera name by a dash. The sensor name is extracted from the camera name and added to the
     * flexible parameters if not already present. Currently the only camera supporting multiple
     * sensors is the Orbbec camera, with sensors @c color, @c ir and @c depth. For instance, if the
     * camera is specified as @c orbbec-ir the Orrbec camera will capture an infrared image.
     *
     * The camera unit is responsible for updating the active camera parameters with the supplied
     * flexible parameter values. The parameters will be semi-persistent and only remain active
     * until they are updated again or the bridge service is restarted.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing Base-64 image data
     */
    @NonNull
    private JsonResponse commandCameraPicture(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String camera = JsonUtils.getString(payload, "camera");

        // Create Java map containing only flexible parameters.
        Map<String, Object> params = parseParams(payload, "camera");

        // Parse camera parameters and retrieve camera unit.
        String cameraName;
        if (camera == null) cameraName = service.getConfig().getCameraName();
        else
        {
            cameraName = BridgeCameraUnit.parseCameraParams(camera, params);
            if (cameraName == null) return invalidProperty(request, "camera", camera);
        }
        BridgeCameraUnit cameraUnit = service.getBridgeCameraUnit(cameraName);

        // Call camera unit method.
        return JsonResponse.bridgeResult(request, cameraUnit.getImageBase64(params));
    }

    /**
     * Handles a request to return a cached face image as Base-64 encoded data.
     *
     * The @e command:camera:face request is forwarded to the Sanbot camera unit. The payload for
     * the request is
     * @code{.json}
     * {
     *      "index": (optional) last-in, first-out index of image to retrieve, default 0,
     *      "<flex1>": (optional) flexible camera parameter,
     *      "<flex2>": (optional) flexible camera parameter,
     *      ...
     * }
     * @endcode
     * The Sanbot camera unit is responsible for updating the active camera parameters with the
     * supplied flexible parameter values. The parameters will be semi-persistent and only remain
     * active until they are updated again or the bridge service is restarted.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing Base-64 face image data
     */
    @NonNull
    private JsonResponse commandCameraFace(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        int index = JsonUtils.getInteger(payload, "index", 0);

        // Validate index.
        if (index < 0) return invalidProperty(request, "index", index);
        JsonUtils.remove(payload, "index");

        // Call Sanbot camera unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotCameraUnit().getFaceImageBase64(index, parseParams(payload)));
    }

    /**
     * Handles a request to set the volume for a single Android audio stream.
     *
     * The @e command:audio:volume request is forwarded to the Android system unit. The payload for
     * the request is
     * @code{.json}
     * {
     *      "stream": (optional) Android output stream
     *      "volume": (mandatory) audio volume as percentage of maximum volume
     * }
     * @endcode
     * Supported streams are @c music, @c tts, @c system, @c alarm, @c voice_call, @c ring, and
     * @c notification. If no stream is specified the volume for a relevant subset of streams will
     * be set. If @c stream is not specified the volume for the default audio stream is set.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandAudioVolume(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String stream = JsonUtils.getString(payload, "stream");
        Integer volume = JsonUtils.getInteger(payload, "volume");

        // Convert stream property to integer stream type and validate. If stream property is not
        // specified, stream type will be null to indicate default stream volume must be set.
        Integer streamType;
        if (stream == null) streamType = null;
        else
        {
            streamType = SanbotMappings.toAudioStreamType(stream);
            if (streamType == null) return invalidProperty(request, "stream", stream);
        }

        // Validate volume property.
        if ((volume == null) || (volume < 0) || (volume > 100)) return invalidProperty(request, "volume", volume);

        // Call Android system unit method.
        return JsonResponse.bridgeResult(request, service.getBridgeAudioUnit().setAudioVolume(streamType, volume));
    }

    /**
     * Handles a request to start playing audio from a URL or local audio file.
     *
     * The @e command:audio:play request is forwarded to the audio unit. The payload for the request
     * is
     * @code{.json}
     * {
     *      "stream": (optional) Android output stream,
     *      "url": (optional) HTTP(S) audio URL,
     *      "filename": (optional) file name relative to SanbotBridge/audio
     * }
     * @endcode
     * Supported audio streams types are @c music, @c tts, @c system, @c alarm, @c voice_call,
     * @c ring and @c notification. At least @c url or @c filename must be specified, if both are
     * specified @c url takes precedence.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandAudioPlay(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String stream = JsonUtils.getString(payload, "stream", null);
        String url = JsonUtils.getString(payload, "url", null);
        String filename = JsonUtils.getString(payload, "filename", null);

        // Convert stream property to integer stream type and validate. If stream property is not
        // specified, stream type will be null to indicate default stream volume must be set.
        Integer streamType;
        if (stream == null) streamType = null;
        else
        {
            streamType = SanbotMappings.toAudioStreamType(stream);
            if (streamType == null) return invalidProperty(request, "stream", stream);
        }

        // Set audio source.
        String source;
        int type;
        if (StringUtils.isBlank(url) == false)
        {
            source = url;
            type = BridgeProtocol.SOURCE_URL;
        }
        else if (StringUtils.isBlank(filename) == false)
        {
            source = filename;
            type = BridgeProtocol.SOURCE_FILE;
        }
        else return missingProperty(request, "url or filename");

        // Call audio unit method.
        return JsonResponse.bridgeResult(request, service.getBridgeAudioUnit().startPlayback(source, type, streamType));
    }

    /**
     * Handles a request to start recording audio.
     *
     * The @e command:audio:record request is forwarded to the audio unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "duration": (optional) maximum recording duration in seconds,
     *      "save": (optional) one of [true, false, "filename"], default false
     * }
     * @endcode
     * If @c duration is 0 or not specified the default value defined in the audio unit is applied.
     * If @c save is @c true, the audio data will be saved to a file with a name constructed from
     * the current date and time. If @c save has a string value that value is used as the filename.
     * Specifying the file extension is optional, if not present a @e wav extension is added.
     *
     * @param   request         bridge command request to handle
     *
     * @return  instance of JsonResponse class containing operation result
     */
    @NonNull
    private JsonResponse commandAudioRecord(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        int duration = JsonUtils.getInteger(payload, "duration", 0);
        JsonElement save = (payload != null) ? payload.get("save") : null;

        // Validate duration property.
        if (duration < 0) return JsonResponse.notAcceptable(request, "duration must not be negative");

        // Retrieve file name from save property.
        String filename = null;
        if (save != null)
        {
            JsonPrimitive jsonPrimitive = save.getAsJsonPrimitive();
            if ((jsonPrimitive.isBoolean()) && (jsonPrimitive.getAsBoolean() == true))
            {
                // Set filename from current date.
                filename = new SimpleDateFormat("yyyyMMdd'_'HHmmss", Locale.getDefault()).format(new Date());
            }
            else if (jsonPrimitive.isString()) filename = jsonPrimitive.getAsString().trim();
        }

        // Create Java map containing only flexible parameters.
        Map<String, Object> params = parseParams(payload, "length", "save");

        // Call audio unit method
        return JsonResponse.bridgeResult(request, service.getBridgeAudioUnit().startRecording(filename, duration, params));
    }

    /**
     * Handles a request to stop audio playback or audio recording.
     *
     * The @e command:audio:stop request is forwarded to either the audio unit. The payload for the
     * request is empty.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandAudioStop(@NonNull BridgeRequest request)
    {
        // Call audio unit method
        return JsonResponse.bridgeResult(request, service.getBridgeAudioUnit().stopAudio());
    }

    /**
     * Handles a request to return a list of available audio recordings.
     *
     * The @e command:audio:list request is forwarded to either the audio unit. The payload for the
     * request is empty.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandAudioList(@NonNull BridgeRequest request)
    {
        // Call audio unit method
        return JsonResponse.bridgeResult(request, service.getBridgeAudioUnit().getRecordingList());
    }

    /**
     * Handles a request to remove one or more audio recordings.
     *
     * The @e command:audio:remove request is forwarded to either the audio unit. The payload for
     * the request is
     * @code{.json}
     * {
     *      "filename": (mandatory) "filename", or "all" to remove all audio recordings
     * }
     * @endcode
     * Specifying the file extension is optional, if not present a @e wav extension is added.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    private JsonResponse commandAudioRemove(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String filename = JsonUtils.getString(payload, "filename");

        // Validate filename property.
        if (StringUtils.isBlank(filename)) return missingProperty(request, "filename");
        if ("all".equalsIgnoreCase(filename)) filename = null;

        // Call audio unit method
        return JsonResponse.bridgeResult(request, service.getBridgeAudioUnit().removeRecording(filename));
    }

    /**
     * Handles a request to start recording video.
     *
     * The @e command:video:record request is forwarded to the video unit. The payload for the
     * request is:
     * @code{.json}
     * {
     *      "duration": (optional) maximum recording duration in seconds
     *      "filename": (optional) name of video file to create, default current date and time,
     * }
     * @endcode
     * If @c duration is 0 or not specified the default value defined in the video unit is applied.
     * If @c filename is @c null or an empty string, a file name is generated from the current date
     * and time. Specifying the file extension is optional, if not present a @e rec extension is
     * added.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandVideoRecord(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        int duration = JsonUtils.getInteger(payload, "duration", 0);
        String filename = JsonUtils.getString(payload, "filename");

        // Validate duration property.
        if (duration < 0) return JsonResponse.notAcceptable(request, "duration must not be negative");

        // Trim file name.
        if (filename != null) filename = filename.trim();

        // Set filename from current date if no file name is specified.
        if (StringUtils.isBlank(filename)) filename = new SimpleDateFormat("yyyyMMdd'_'HHmmss", Locale.getDefault()).format(new Date());

        // Call video unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotVideoUnit().startRecording(filename, duration));
    }

    /**
     * Handles a request to stop video recording.
     *
     * The @e command:video:stop request is forwarded to the video unit. The payload for the
     * request is empty.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandVideoStop(@NonNull BridgeRequest request)
    {
        // Call video unit method.
        return JsonResponse.bridgeResult(request, service.getSanbotVideoUnit().stopRecording());
    }


    /**
     * Handles a request to return a list of available video recordings.
     *
     * The @e command:video:list request is forwarded to either the audio unit. The payload for the
     * request is empty.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandVideoList(@NonNull BridgeRequest request)
    {
        // Call audio unit method
        return JsonResponse.bridgeResult(request, service.getSanbotVideoUnit().getRecordingList());
    }

    /**
     * Handles a request to remove one or more video recordings.
     *
     * The @e command:video:remove request is forwarded to either the video unit. The payload for
     * the request is
     * @code{.json}
     * {
     *      "filename": (mandatory) "filename", or "all" to remove all video recordings
     * }
     * @endcode
     * Specifying the file extension is optional, if not present a @e rec extension is added.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    private JsonResponse commandVideoRemove(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String filename = JsonUtils.getString(payload, "filename");

        // Validate filename property.
        if (StringUtils.isBlank(filename)) return missingProperty(request, "filename");
        if ("all".equalsIgnoreCase(filename)) filename = null;

        // Call audio unit method
        return JsonResponse.bridgeResult(request, service.getSanbotVideoUnit().removeRecording(filename));
    }

    /**
     * Handles a request to show an image on the robot screen.
     *
     * The @e command:screen:image request is forwarded to the Android media unit. The payload for
     * the request is:
     * @code{.json}
     * {
     *      "filename": (optional) image file name relative to the bridge image directory
     * }
     * @endcode
     * The file must exist in the robot's @c SanbotBridge/images directory. If @c filename is not
     * specified, the current image is removed from the screen, to display the main application
     * window.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandScreenImage(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String fileName = JsonUtils.getString(payload, "filename");

        // Call Android system unit method to show the image or main bridge window.
        return JsonResponse.bridgeResult(request, service.getAndroidSystemUnit().showScreenImage(fileName));
    }

    /**
     * Handles a request to update the speech settings.
     *
     * This method extracts the flow-control properties and forwards the remaining settings to the
     * speech unit. The payload for the @e command:speech:config is
     * @code{.json}
     * {
     *      "reset": (optional) reset parameters to default values, one of [true, false],
     *      "reload": (optional) reload parameters from configuration file, one of [true, false],
     *      "save": (optional) save updated parameters, one of [true, false],
     *      "<flex1>": (optional) flexible parameter,
     *      "<flex2>": (optional) flexible parameter,
     *      ...
     * }
     * @endcode
     * If the either the @c reset or @c reload property is @c true, text-to-speech parameters are
     * either reset to their default values or reloaded from the configuration file. The @c reset,
     * and @c reload properties are mutually exclusive; only the first property set to @c true is
     * applied. If both properties are @c false, the text-to-speech unit is responsible for updating
     * the active text-to-speech parameters with the supplied flexible parameter values. If the
     * @c save property is @c true, the reset, reloaded or updated parameters are made persistent by
     * writing them to the configuration file. If @c save is @c false or not specified, the
     * parameters will be semi-persistent and remain active until they are updated again or the
     * bridge service is restarted.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandSpeechConfig(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        boolean reset = JsonUtils.getBoolean(payload, "reset", false);
        boolean reload = JsonUtils.getBoolean(payload, "reload", false);
        boolean save = JsonUtils.getBoolean(payload, "save", false);

        // Create Java map containing only flexible parameters.
        Map<String, Object> params = parseParams(payload, "reset", "reload", "save");

        // Call text-to-speech unit method.
        return JsonResponse.bridgeResult(request, service.getBridgeTtsUnit().updateConfig(reset, reload, save, params));
    }

    /**
     * Handles a request to start speaking.
     *
     * The @e command:speech:say request is forwarded to the speech unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "text": (mandatory) phrase to say,
     *      "<flex1>": (optional) flexible parameter,
     *      "<flex2>": (optional) flexible parameter,
     *      ...
     * }
     * @endcode
     * The text-to-speech unit is responsible for updating the active text-to-speech parameters with
     * the supplied flexible parameter values. The parameters will be semi-persistent and remain
     * active until they are updated again or the bridge service is restarted.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandSpeechSay(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String text = JsonUtils.getString(payload, "text");

        // Validate text property.
        if (text == null) return missingProperty(request, "text");
        if (text.isEmpty()) return invalidProperty(request, "text", text);

        // Create Java map containing only flexible parameters.
        Map<String, Object> params = parseParams(payload, "text");

        // Call speech unit method.
        return JsonResponse.bridgeResult(request, service.getBridgeTtsUnit().startSpeaking(text, params));
    }

    /**
     * Handles a request to stop speaking.
     *
     * The @e command:speech:stop request is forwarded to the speech unit. The payload for the
     * request is empty
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandSpeechStop(@NonNull BridgeRequest request)
    {
        // Call speech unit method.
        return JsonResponse.bridgeResult(request, service.getBridgeTtsUnit().stopSpeaking());
    }

    /**
     * Handles a request to start listening.
     *
     * The @e command:speech:say request is forwarded to the speech unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "language": (optional) language code
     * }
     * @endcode
     * If the language is not specified the current configured language will be used.
     *
     * @param   request         bridge information request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandSpeechListen(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String language = JsonUtils.getString(payload, "language");

        // Convert language to Sanbot language code.
        String languageType;
        if (language != null)
        {
            languageType = SanbotMappings.mapSanbotLanguageType(language);
            if (languageType == null) invalidProperty(request, "language", language);
        }
        else languageType = null;

        // Call speech unit method.
        return JsonResponse.bridgeResult(request, service.speechDoWakeup(languageType));
    }

    /**
     * Handles request to update battery charging properties.
     *
     * The @e command:battery:charge request is forwarded to the motion unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "mode": (mandatory) one of ["auto", "manual"]
     *      "level": (optional): minimum battery level, one of [0, 10, 20, 30, 40]
     * }
     * @endcode
     * The minimum battery level is ignored for manual charging mode. If the value is not specified
     * or equal to 0 the current minimum level is left unchanged.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    private JsonResponse commandBatteryConfig(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String mode = JsonUtils.getString(payload, "mode", null);
        int level = JsonUtils.getInteger(payload, "level", 0);

        // Convert mode parameter to auto charge boolean,
        if (mode == null) return missingProperty(request, "mode");
        mode = mode.trim();
        boolean auto = BridgeProtocol.MODE_AUTO.equalsIgnoreCase(mode);
        if ((auto == false) && (BridgeProtocol.MODE_MANUAL.equalsIgnoreCase(mode) == false)) return invalidProperty(request, "mode", mode);

        // Validate level parameter.
        if ((level != 0) && (level != 10) && (level != 20) && (level != 30) && (level != 40))
            return invalidProperty(request, "level", level);

        // Call bridge service method.
        DataResult result = service.configBattery(auto, level);
        return JsonResponse.bridgeResult(request, result);
    }

    /**
     * Handles a request to move to charge pile.
     *
     * The @e command:battery:charge request is forwarded to the motion unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "cancel": (optional) one of [true, false]
     * }
     * @endcode
     * If @c cancel is not specified the robot will move to the charging pile. If @c cancel equals
     * @c true, the robot will stop moving to the charging pile, if it equals @c false no action is
     * required. Instead of boolean @c true and @c false the value may also be specified as a
     * number (1/0) or string ("true"/"false", "1"/"0").
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    private JsonResponse commandBatteryCharge(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String cancel = JsonUtils.getString(payload, "cancel");

        // Set charge state.
        boolean charge;
        if (cancel == null) charge = true;
        else
        {
            // Convert cancel parameter to boolean value.
            Boolean b = ValueUtils.toBoolean(cancel);
            if (b == null) return invalidProperty(request, "cancel", cancel);

            // If charging must not be canceled there is nothing to do.
            if (b == false) return JsonResponse.bridgeResult(request, DataResult.success());

            charge = false;
        }

        // Call motion unit method.
        DataResult result = service.chargeBattery(charge);
        return JsonResponse.bridgeResult(request, result);
    }

    /**
     * Handles a request to upload a script file.
     *
     * The @e command:script:upload request is forwarded to the bridge service. The payload for the
     * request is
     * @code{.json}
     * {
     *      "name": (mandatory) name of script to execute,
     *      "content": (mandatory) string containing script data
     * }
     * @endcode
     * The script name may only contain alpha-numerical characters and underscores.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandScriptUpload(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String name = JsonUtils.getString(payload, "name");
        String content = JsonUtils.getString(payload, "content");

        // Validate name property. The script name may only contain alphanumeric characters.
        if ((StringUtils.isBlank(name)) || (name.matches("[a-zA-Z0-9_]*") == false))
            return invalidProperty(request, "name", name);

        // Validate content property.
        if (StringUtils.isBlank(content)) return missingProperty(request, "content");

        // Call bridge service method.
        return JsonResponse.bridgeResult(request, service.writeScript(name, content));
    }

    /**
     * Handles a request to start executing a script.
     *
     * The @e command:script:start request is forwarded to the bridge service. The payload for the
     * request is
     * @code{.json}
     * {
     *      "name": (mandatory) name of script to execute
     * }
     * @endcode
     * The script name may only contain alpha-numerical characters and underscores.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandScriptStart(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String name = JsonUtils.getString(payload, "name");

        // Validate name property. The script name may only contain alphanumeric characters.
        if ((StringUtils.isBlank(name)) || (name.matches("[a-zA-Z0-9_]*") == false))
            return invalidProperty(request, "name", name);

        // Call bridge service method.
        return JsonResponse.bridgeResult(request, service.startScript(name));
    }

    /**
     * Handles a request to stop executing the current script.
     *
     * The @e command:script:stop request is forwarded to the bridge service. The payload for the
     * request is empty.
     *
     * @param   request         bridge command request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse commandScriptStop(@NonNull BridgeRequest request)
    {
        // Call service method to stop the script.
        return JsonResponse.bridgeResult(request, service.stopScript());
    }

    /* @} */

    /**
     * @name Media Request Handlers
     * @{
     */

    /**
     * Handles a request to capture a snapshot image.
     *
     * The @e media:camera:snapshot request is forwarded to the media unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec",
     *          "3d"]; see below,
     *      "<flex1>": (optional) flexible camera parameter,
     *      "<flex2>": (optional) flexible camera parameter,
     *      ...
     * }
     * @endcode
     * The @c camera property is option, if not specified the default camera will be used. The
     * @c camera property is optional, if not specified the default camera will be used. The
     * @c head, @c body and @c 3d values are aliases for the @c sanbot, @c android, and @c orbbec
     * cameras. The camera name may be followed by an optional sensor name, separated from the
     * camera name by a dash. The sensor name is extracted from the camera name and added to the
     * flexible parameters if not already present. Currently the only camera supporting multiple
     * sensors is the Orbbec camera, with sensors @c color, @c ir and @c depth. For instance, if the
     * camera is specified as @c orbbec-ir the Orrbec camera will capture an infrared image.
     *
     * The camera unit is responsible for updating the active camera parameters with the supplied
     * flexible parameter values. The parameters will be semi-persistent and only remain active
     * until they are updated again or the bridge service is restarted.
     *
     * @param   request     bridge media request to handle
     *
     * @return  instance of @c MediaResponse class containing image data
     */
    @NonNull
    private MediaResponse mediaCameraSnapshot(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String camera = StringUtils.normalize(JsonUtils.getString(payload, "camera"));

        // Create Java map containing only flexible parameters.
        Map<String, Object> params = parseParams(payload, "camera");

        // Parse camera parameters and retrieve camera unit.
        String cameraName;
        if (camera == null) cameraName = service.getConfig().getCameraName();
        else
        {
            cameraName = BridgeCameraUnit.parseCameraParams(camera, params);
            if (cameraName == null) return MediaResponse.create(request, BridgeResponse.CODE_NOT_ACCEPTABLE, "unsupported camera '" + camera + "'", null, null, null);
        }
        BridgeCameraUnit cameraUnit = service.getBridgeCameraUnit(cameraName);

        // Call camera unit method.
        MediaResult result = cameraUnit.getSnapshot(params);

        if (result == null) return MediaResponse.internalError(request, "empty media result");
        return MediaResponse.create(request, result.getStatusCode(), result.getDescription(), result.getBytes(), result.getMimeType(), result.getMetaData());
    }

    /**
     * Handles a request to capture a still image.
     *
     * The @e media:camera:picture request is forwarded to the media unit. The payload for the
     * request is
     * @code{.json}
     * {
     *      "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec",
     *          "3d"]; see below
     *      "<flex1>": (optional) flexible camera parameter,
     *      "<flex2>": (optional) flexible camera parameter,
     *      ...
     * }
     * @endcode
     * The @c camera property is option, if not specified the default camera will be used. The
     * @c camera property is optional, if not specified the default camera will be used. The
     * @c head, @c body and @c 3d values are aliases for the @c sanbot, @c android, and @c orbbec
     * cameras. The camera name may be followed by an optional sensor name, separated from the
     * camera name by a dash. The sensor name is extracted from the camera name and added to the
     * flexible parameters if not already present. Currently the only camera supporting multiple
     * sensors is the Orbbec camera, with sensors @c color, @c ir and @c depth. For instance, if the
     * camera is specified as @c orbbec-ir the Orrbec camera will capture an infrared image.
     *
     * The camera unit is responsible for updating the active camera parameters with the supplied
     * flexible parameter values. The parameters will be semi-persistent and only remain active
     * until they are updated again or the bridge service is restarted.
     *
     * @param   request     bridge media request to handle
     *
     * @return  instance of @c MediaResponse class containing image data
     */
    @NonNull
    private MediaResponse mediaCameraPicture(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String camera = StringUtils.normalize(JsonUtils.getString(payload, "camera"));

        // Create Java map containing only flexible parameters.
        Map<String, Object> params = parseParams(payload, "camera");

        // Parse camera parameters and retrieve camera unit.
        String cameraName;
        if (camera == null) cameraName = service.getConfig().getCameraName();
        else
        {
            cameraName = BridgeCameraUnit.parseCameraParams(camera, params);
            if (cameraName == null) return MediaResponse.create(request, BridgeResponse.CODE_NOT_ACCEPTABLE, "unsupported camera '" + camera + "'", null, null, null);
        }
        BridgeCameraUnit cameraUnit = service.getBridgeCameraUnit(cameraName);

        // Call camera unit method.
        MediaResult result = cameraUnit.getImage(params);

        if (result == null) return MediaResponse.internalError(request, "empty media result");
        return MediaResponse.create(request, result.getStatusCode(), result.getDescription(), result.getBytes(), result.getMimeType(), result.getMetaData());
    }

    /**
     * Handles a request to return a cached face image.
     *
     * The @e media:camera:face request is forwarded to the Sanbot camera service. The payload for
     * the request is
     * @code{.json}
     * {
     *      "index": (optional) last-in, first-out index of image to retrieve, default 0,
     *      "<flex1>": (optional) flexible camera parameter,
     *      "<flex2>": (optional) flexible camera parameter,
     *      ...
     * }
     * @endcode
     * @param   request         bridge media request to handle
     *
     * @return  instance of @c MediaResponse class containing face image data
     */
    @NonNull
    private MediaResponse mediaCameraFace(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        int index = JsonUtils.getInteger(payload, "index", 0);

        // Validate index.
        if (index < 0) return MediaResponse.internalError(request, "invalid image index");
        JsonUtils.remove(payload, "index");

        // Call Sanbot camera unit method.
        MediaResult result = service.getSanbotCameraUnit().getFaceImage(index, parseParams(payload));
        if (result == null) return MediaResponse.internalError(request, "empty media result");
        return MediaResponse.create(request, result.getStatusCode(), result.getDescription(), result.getBytes(), result.getMimeType(), result.getMetaData());
    }

    /**
     * Handles a request to retrieve an audio recording.
     *
     * The @e media:audio:get request is forwarded to the Sanbot camera service. The payload for
     * the request is
     * @code{.json}
     * {
     *      "filename": (optional) file name relative to SanbotBridge/audio
     * }
     * @endcode
     * If no file name is specified the currently cached WAV data is retrieved. Specifying the file
     * extension is optional, if not present a @e wav extension is added.
     *
     * @param   request         bridge media request to handle
     *
     * @return  instance of MediaResponse class containing audio data
     */
    @NonNull
    private MediaResponse mediaAudioGet(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String filename = JsonUtils.getString(payload, "filename", null);

        // Trim leading and trailing spaces from file name if not null.
        if (filename != null) filename = filename.trim();

        // Call audio unit method.
        MediaResult result = service.getBridgeAudioUnit().getRecording(filename);
        if (result == null) return MediaResponse.internalError(request, "empty media result");
        return MediaResponse.create(request, result.getStatusCode(), result.getDescription(), result.getBytes(), result.getMimeType(), result.getMetaData());
    }

    /**
     * Handles a request to retrieve a video recording.
     *
     * The @e media:video:get request is forwarded to the Sanbot camera service. The payload for
     * the request is
     * @code{.json}
     * {
     *      "filename": (optional) file name relative to SanbotBridge/video
     * }
     * @endcode
     * If @c filename is not specified the most recent video recording is returned.
     *
     * @param   request         bridge media request to handle
     *
     * @return  instance of MediaResponse class containing video data
     */
    @NonNull
    private MediaResponse mediaVideoGet(@NonNull BridgeRequest request)
    {
        // Get request properties from JSON payload.
        JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
        String filename = JsonUtils.getString(payload, "filename", null);

        // Validate file name. Trim leading and trailing spaces from file name if not null.
        if (filename == null) return MediaResponse.internalError(request, "filename");
        filename = filename.trim();

        // Call video unit method.
        MediaResult result = service.getSanbotVideoUnit().getRecording(filename);
        return MediaResponse.create(request, result.getStatusCode(), result.getDescription(), result.getBytes(), result.getMimeType(), result.getMetaData());
    }

    /** @} */

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Validates the head angle.
     *
     * The head angle must be specified as a Sanbot SDK angle. For absolute movement the angle must
     * be in the range [0..180] horizontally and [7..30] vertically. For relative movement the angle
     * is a movement magnitude in the range [0..180] horizontally and [0..25] vertically or
     * diagonally.
     *
     * @param   angle           angle to validate
     * @param   absolute        @c true if movement is to absolute position
     * @param   horizontal      @c true if movement orientation is horizontal (left-right)
     *
     * @return  @c true if angle is in allowed range, @c false if not
     */
    private boolean validateHeadAngle(int angle, boolean absolute, boolean horizontal)
    {
        if (absolute)
        {
            if (horizontal) return ((angle >= 0) && (angle <= 180));
            else return ((angle >= 7) && (angle <= 30));
        }

        if (horizontal) return ((angle >= 0) && (angle <= 180));
        else return ((angle >= 0) && (angle <= 25));
    }

    /**
     * Sets the color, flicker mode and random mode for one or more leds.
     *
     * The color, flicker, and random mode are retrieved from the request payload and validated. If
     * valid, the request to apply the specified settings is sent to the led unit.
     *
     * @param   request         bridge media request object
     * @param   payload         JSON payload passed in request
     * @param   part            specifies which led(s) to switch on or off
     *
     * @return  JsonResponse instance containing operation result
     */
    @NonNull
    private JsonResponse applyLedStatus(@NonNull BridgeRequest request, JsonObject payload, Byte part)
    {
        String color = JsonUtils.getString(payload, "color");
        Integer flicker = JsonUtils.getInteger(payload, "flicker");
        Integer random = JsonUtils.getInteger(payload, "random");

        // Validate 'color' property.
        Byte c = (color != null) ? SanbotMappings.mapLedColor(color) : null;
        if ((color != null) && (c == null)) return invalidProperty(request, "color", color);

        // Validate 'flicker' property.
        if ((flicker != null) && ((flicker < 0) || (flicker > 255))) return invalidProperty(request, "flicker", flicker);

        // Validate 'random' property.
        if ((random != null) && ((random < 0) || (random > 7))) return invalidProperty(request, "random", random);

        // Call led unit method.
        Byte mode = SanbotMappings.toLedMode(c, flicker, random);
        byte delay = ((flicker == null) || (flicker == 0)) ? (byte)1 : flicker.byteValue();
        byte rand = ((random == null) || (random == 0)) ? (byte)1 : random.byteValue();
        return JsonResponse.bridgeResult(request, service.getSanbotLedUnit().setLed(part, mode, delay, rand));
    }

    /**
     * Sets white light brightness.
     *
     * Either the on/off status or brightness are retrieved from the request payload and validated.
     * If valid, the request to apply the specified settings is sent to the led unit.
     *
     * @param   request         bridge media request object
     * @param   payload         JSON payload passed in request
     *
     * @return  JsonResponse instance containing operation result
     */
    private JsonResponse applyWhiteLightStatus(@NonNull BridgeRequest request, JsonObject payload)
    {
        Boolean on = JsonUtils.getBoolean(payload, "on");
        Integer brightness = JsonUtils.getInteger(payload, "brightness");

        // At least one of the two properties be specified.
        if ((on == null) && (brightness == null))
            return JsonResponse.notAcceptable(request, "either on or brightness must be specified");

        if (brightness != null)
        {
            // Validate brightness property.
            if ((brightness<1) || (brightness>3))
                return invalidProperty(request, "brightness", brightness);

            // Call led unit method to set brightness. Return error response on failure.
            DataResult result = service.getSanbotLedUnit().setHeadLightBrightness(brightness);
            if ((on == null) || (result.isFailure()))
                return JsonResponse.bridgeResult(request, result);
        }

        // Call led unit method to switch light on or off.
        return JsonResponse.bridgeResult(request, service.getSanbotLedUnit().switchHeadLight(on));
    }

   /**
     * Retrieves parameter list from payload.
     *
     * Parameters may be specified as a single @c params property with a comma-separated string of
     * key-value pairs as value, or as individual payload parameters. If the same parameter name
     * appears in the comma-separated property and as an individual parameter, the value of the
     * individual parameter takes precedence.
     *
     * @param   payload         JSON payload from which to retrieve parameters
     *
     * @return  instance of Java @c Map class containing parameters
     */
    @NonNull
    private Map<String, Object> parseParams(JsonObject payload)
    {
        return parseParams(payload, (String[])null);
    }

    /**
     * Retrieves parameter list from payload, excluding fixed parameter names.
     *
     * @param   payload         JSON payload from which to retrieve parameters
     * @param   excludedNames   optional parameter names to ignore
     *
     * @return  instance of Java @c Map class containing parameters
     */
    @NonNull
    private Map<String, Object> parseParams(JsonObject payload, String... excludedNames)
    {
        Map<String, Object> params = MapUtils.createMap();
        Set<String> excluded = new HashSet<>();

        if (excludedNames != null)
        {
            for (String name : excludedNames)
            {
                name = StringUtils.normalize(name);
                if (name != null) excluded.add(name);
            }
        }

        // If params property exist parse the value.
        String flexParams = JsonUtils.getString(payload, "params", "");
        if (StringUtils.isBlank(flexParams) == false)
        {
            String[] kvps = flexParams.split(",");
            for (String kvp : kvps)
            {
                String[] s = kvp.split("=", 2);
                if (s.length != 2) continue;

                String key = StringUtils.normalize(s[0]);
                if ((key == null) || excluded.contains(key)) continue;

                params.put(key, s[1].trim());
            }
        }

        // Copy individual payload parameters.
        for (String key : payload.keySet())
        {
            key = key.trim();
            if ((key.isEmpty()) || ("params".equals(key)) || excluded.contains(key)) continue;

            Object value = JsonUtils.parseObject(payload.get(key));
            if (value instanceof String) value = ((String)value).trim();
            if (value != null)
            {
                params.put(key, value);
            }
        }
        return params;
    }

    /**
     * Creates a response object for a request with a missing mandatory property.
     *
     * @param   request         BridgeRequest instance specifying request properties
     * @param   name            name of missing parameter
     *
     * @return  JsonResponse instance with 'missing property' description
     */
    @NonNull
    private JsonResponse missingProperty(@NonNull BridgeRequest request, String name)
    {
        String message = String.format("mandatory parameter %s not specified", name);
        return JsonResponse.notAcceptable(request, message);
    }

    /**
     * Creates a response object for a request with a missing property or invalid parameter value.
     *
     * @param   request         BridgeRequest instance specifying request properties
     * @param   name            name of missing property or property with invalid value
     * @param   value           invalid value of property, or @c null if property is missing
     *
     * @return  JsonResponse instance with 'invalid property value' description
     */
    @NonNull
    private JsonResponse invalidProperty(@NonNull BridgeRequest request, String name, Object value)
    {
        String message = (value != null) ? String.format("property %s has invalid value '%s'", name, value.toString()) : String.format("property %s may not be empty", name);
        return JsonResponse.notAcceptable(request, message);
    }
}
