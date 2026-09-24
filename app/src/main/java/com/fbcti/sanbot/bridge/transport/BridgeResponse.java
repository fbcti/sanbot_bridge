/**
 * @file        BridgeResponse.java
 * @brief       Declares abstract BridgeResponse class.
 */
package com.fbcti.sanbot.bridge.transport;

/**
 * Abstract base class for bridge responses that share request envelope and HTTP-like status data.
 *
 * @version     1.0.001
 * @date        18 Jun 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public abstract class BridgeResponse
{
    /** HTTP-like status code for a request that was handled successfully. */
    public static final int CODE_OK = 200;

    /** HTTP-like status code for malformed request data. */
    public static final int CODE_BAD_REQUEST = 400;

    /** HTTP-like status code for an authentication failure. */
    public static final int CODE_UNAUTHORIZED = 401;

    /** HTTP-like status code for an authorization failure. */
    public static final int CODE_FORBIDDEN = 403;

    /** HTTP-like status code for an unsupported route or command. */
    public static final int CODE_NOT_FOUND = 404;

    /** HTTP-like status code for an unsupported method or operation. */
    public static final int CODE_NOT_ALLOWED = 405;

    /** HTTP-like status code for invalid command data. */
    public static final int CODE_NOT_ACCEPTABLE = 406;

    /** HTTP-like status code for internal bridge or SDK failures. */
    public static final int CODE_INTERNAL_SERVER_ERROR = 500;

    /** HTTP-like status code for temporarily unavailable media/service data. */
    public static final int CODE_SERVICE_UNAVAILABLE = 503;

    /** Result message for a request that was handled successfully. */
    public static final String MESSAGE_SDK_SUCCESS  = "robot request succeeded";

    /** Result message for a request that failed. */
    public static final String MESSAGE_SDK_ERROR  = "robot request failed";

    /** Request id - mandatory for WebSocket requests. */
    public final String id;

    /** Request type - copied from request object if available. */
    public final String type;

    /** Request module - copied from request object if available. */
    public final String module;

    /** Request action - copied from request object if available. */
    public final String action;

    /** HTTP-like response status code. */
    public final int code;

    /** Human-readable response message. */
    public final String message;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs the base portion of a bridge response from individual bridge request properties.
     *
     * @param   id              request id
     * @param   type            request type
     * @param   module          request module
     * @param   action          request action
     * @param   code            HTTP-like response status code
     * @param   message         human-readable response message
     */
    protected BridgeResponse(String id, String type, String module, String action, int code, String message)
    {
        this.id = id;
        this.type = type;
        this.module = module;
        this.action = action;
        this.code = code;
        this.message = message;
    }

    /**
     * Constructs the base portion of a bridge response from specified @c BridgeRequest object.
     *
     * @param   request         bridge request to copy envelope fields from
     * @param   code            HTTP-like response status code
     * @param   message         human-readable response message
     */
    protected BridgeResponse(BridgeRequest request, int code, String message)
    {
        this((request != null) ? request.id : null,
            (request != null) ? request.type : null,
            (request != null) ? request.module : null,
            (request != null) ? request.action : null,
            code,
            message);
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Returns @c true if bridge request was successful.
     *
     * @return  @c true if status code is in 2xx range, @c false in all other cases
     */
    public boolean ok()
    {
        return ((code >= 200) && (code < 300));
    }

    /**
     * Returns a summarized payload for logging purposes.
     *
     * @return  summarized payload
     */
    public String toSummary()
    {
        StringBuilder summary = new StringBuilder();
        if (ok() == false) summary.append(BridgeProtocol.RESPONSE_ERROR).append(" ");
        if (message != null) summary.append(message);
        return summary.toString();
    }
}