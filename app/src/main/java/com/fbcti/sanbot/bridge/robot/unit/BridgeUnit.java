package com.fbcti.sanbot.bridge.robot.unit;

/**
 * @file        BridgeUnit.java
 * @brief       Declares abstract BridgeUnit class.
 */

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.transport.BridgeEvent;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.transport.HttpStatus;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Abstract base class for bridge unit classes that manage robot operations.
 *
 * This class implements behviour common to all bridge unit classes.
 *
 * @version     1.0.001
 * @date        1 Aug 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public abstract class BridgeUnit
{
    /** Instance of class for serializing/deserializing Json objects. */
    protected static final Gson gson = new Gson();

    /** Overall unit status. */
    protected UnitStatus unitStatus = UnitStatus.INITIALIZING;

    /** Standard error message to be used for 'invalid parameters' error. */
    protected String ERROR_INVALID_PARAMS = "invalid_parameters";

    /** Source label used for log messages. */
    protected final String source;

    /** Callback host to which to publish bridge unit events. */
    private volatile BridgeEventHost eventHost;

    /** Last reported error. */
    protected DataResult error = null;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs the base part of a bridge unit.
     *
     * The callback host is copied to member variables, and the source label for log messages is
     * initialized.
     *
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public BridgeUnit(BridgeEventHost eventHost)
    {
        this.eventHost = eventHost;
        this.source = getClass().getSimpleName();
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit shutdown tasks.
     */
    public abstract void shutdown();

    /**
     * Builds a data map containing current bridge unit status data.
     *
     * This method just returns an empty data map. Bridge unit classes that extend this abstract
     * class may override this method to retrieve unit-specific status data.
     *
     * @return  instance of Java @c Map class
     */
    public Map<String, Object> buildStatusData()
    {
        return MapUtils.createMap("status", unitStatus.toString());
    }

    /**
     * Unregisters the event listener.
     */
    public void unregisterEventListener() { this.eventHost = null; }

    /**
     * Returns the last reported error.
     *
     * @return  DataResult instance containing last reported error.
     */
    public DataResult getError() { return error; }

    /***********************************************************************************************
     * PROTECTED METHODS
     **********************************************************************************************/

    /**
     * Publishes a bridge unit event.
     *
     * @param   bridgeEvent     instance of BridgeEvent class specifying event data
     */
    protected void publishEvent(BridgeEvent bridgeEvent)
    {
        if ((eventHost != null) && (bridgeEvent != null)) eventHost.publishEvent(bridgeEvent);
    }

    /**
     * Relays bridge unit event to event subscribers.
     *
     * The module on behalf of which the event is raised and the event name are specified by the
     * method parameters. A new BridgeEvent instance is created and relaued by calling the
     * publishBridgeEvent(BridgeEvent) method.
     *
     * @param   module          robot module that raised the event
     * @param   event           event name
     */
    protected void publishEvent(String module, String event)
    {
        publishEvent(BridgeEvent.create(module, event));
    }

    /**
     * Relays bridge unit event to event subscribers.
     *
     * The module on behalf of which the event is raised, the event name and addtonal event data are
     * specified by the method parameters. A new BridgeEvent instance is created and relaued by
     * calling the publishBridgeEvent(BridgeEvent) method.
     *
     * @param   module          robot module that raised the event
     * @param   event           event name
     * @param   data            optional event data
     */
    protected void publishEvent(String module, String event, Object data)
    {
        publishEvent(BridgeEvent.create(module, event, data));
    }

    /**
     * Creates a data map to which query response data can be added.
     *
     * This class just creates the data map and adds the query reference id if avaiable. The
     * consumer of this method is responsible for adding the actual result data to the data map.
     *
     * @param   refid           optional query reference id
     *
     * @return  instance of Java @c Map class containing reference, or empty
     */
    protected Map<String, Object> queryResultData(String refid)
    {
        Map<String, Object> data = MapUtils.createMap();
        if (StringUtils.isBlank(refid) == false) data.put("refid", refid);
        return data;
    }

    /**
     * Writes unit status to the bridge log.
     */
    protected void logStatus()
    {
        BridgeLog.info(getClass().getSimpleName(), "Unit status is " + unitStatus.toString().toUpperCase());
    }

    /**
     * Returns MIME type matching specified binary data type.
     *
     * @param   dataType        binary data type for which to return MIME type
     *
     * @return  MIME type matching binary data type, or @c null if matching MIME type is not found
     */
    @Nullable
    protected String toMimeType(String dataType)
    {
        String normalized = StringUtils.normalize(dataType);
        if (normalized == null) return null;

        switch (normalized)
        {
            case "jpeg":
            case "jpg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "webp":
                return "image/webp";
            case "wav":
                return "audio/wav";
            case "raw":
                return "application/octet-stream";
            default:
                return null;
        }
    }

    /**
     * Writes an error message matching speciifed DataResult to the specified output stream.
     *
     * @param   outputStream    output stream to write error message to
     * @param   error           DataResult instance specifying error
     *
     * @throws  IOException     thrown if data can not be written
     */
    protected void errorHtml(OutputStream outputStream, @NonNull DataResult error) throws IOException
    {
        writeBinaryJsonError(outputStream, error.getStatusCode(), error.getData(), null);
    }

    /**
     * Writes an error message with specified error code and text to the specified output stream.
     *
     * @param   outputStream    output stream to write error message to
     * @param   code            error code
     * @param   text            error text
     *
     * @throws  IOException     thrown if data can not be written
     */
    protected void errorHtml(OutputStream outputStream, @NonNull BridgeResult.Code code, String text) throws IOException
    {
        writeBinaryJsonError(outputStream, code.statusCode(), text, null);
    }

    /**
     * Writes a JSON HTTP error response to the output stream.
     *
     * @param   outputStream    output stream to write MJPEG response headers to
     * @param   statusCode      HTTP status code
     * @param   message         additional error message
     * @param   data            additional error data
     *
     * @throws  IOException     thrown if data can not be written
     */
    protected void writeBinaryJsonError(OutputStream outputStream, int statusCode, String message, Map<String, Object> data) throws IOException
    {
        BridgeLog.error(getClass().getSimpleName(), StringUtils.capitalize(message));

        String statusText = HttpStatus.toText(statusCode);

        Map<String, Object> response = MapUtils.createMap();
        response.put(BridgeProtocol.FIELD_TYPE, BridgeProtocol.TYPE_RESPONSE);
        response.put(BridgeProtocol.FIELD_RESPONSE, BridgeProtocol.RESPONSE_ERROR);
        response.put("description", message);
        Map<String, Object> error = MapUtils.createMap();
        error.put("message", message);
        if (data != null) error.putAll(data);
        response.put("error", error);
        byte[] body = gson.toJson(response).getBytes(StandardCharsets.UTF_8);

        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(statusCode).append(' ').append(statusText).append("\r\n");
        headers.append("Content-Type: application/json; charset=utf-8\r\n");
        headers.append("Content-Length: ").append(body.length).append("\r\n");
        headers.append("Access-Control-Allow-Origin: *\r\n");
        headers.append("Access-Control-Allow-Headers: Content-Type, X-API-Key, X-Request-Id\r\nAccess-Control-Allow-Private-Network: true\r\n");
        headers.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
        headers.append("Connection: close\r\n\r\n");

        outputStream.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.write(body);
        outputStream.flush();
    }

    /**
     * Sets the last reported error.
     *
     * @param   code            error code
     * @param   description     brief error description
     * @param   error           error text
     *
     * @return  DataResult insance specifying last reported error
     */
    @NonNull
    protected DataResult setError(BridgeResult.Code code, String description, String error)
    {
        this.error = DataResult.create(code, description, error);
        BridgeLog.error(getClass().getSimpleName(), StringUtils.capitalize(error));
        return this.error;
    }

     /**
     * Sets the last reported error.
     *
     * @param   error           DataResult instance specifying error
     *
     * @return  DataResult insance specifying last reported error
     */
    protected DataResult setError(DataResult error)
    {
        if (error == null) return null;

        this.error = error;
        String o = error.getData();
        if (o != null) BridgeLog.error(getClass().getSimpleName(), StringUtils.capitalize(o));
        else BridgeLog.error(getClass().getSimpleName(), "Unknown error");
        return error;
    }

   /***********************************************************************************************
     * ENUMERATORS
     **********************************************************************************************/

    /**
     * Unit status identifiers.
     */
    public enum UnitStatus
    {
        DISABLED("disabled"),

        SHUTDOWN("shutdown"),

        EMULATED("emulated"),

        INITIALIZING("initializing"),

        NOTINITIALIZED("initialization failed"),

        STARTED("started");

        /** Unit status. */
        private final String status;

        /**
         * Constructs a new UnitStatus instance.
         *
         * The unit status is copied to the @c status member variable.
         *
         * @param   status          unit status to copy
         */
        UnitStatus(String status) { this.status = status; }

        /**
         * Returns current unit status.
         *
         * @return  string specifying unit status
         */
        @Override
        public String toString() { return status; }
    }
}