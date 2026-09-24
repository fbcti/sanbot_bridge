/**
 * @file        BridgeRequest.java
 * @brief       Implements BridgeRequest class.
 */
package com.fbcti.sanbot.bridge.transport;

import com.fbcti.sanbot.bridge.util.JsonUtils;
import com.google.gson.JsonElement;

/**
 * Represents a protocol-neutral bridge request.
 *
 * Depending on the protocol on which the request was received this the instance of this class is
 * created by either the HttpTransport or WebSockerTransport class upon receiving a message. The
 * request object is immutable - all members are declared @c final.
 *
 * @version     1.0.001
 * @date        18 Jun 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class BridgeRequest
{
    /** Protocol on which request was received. */
    public final Protocol protocol;

    /** Request id - optional for REST requests, mandatory for WebSocket requests. */
    public final String id;

    /** Request type - one of @c INFO_ strings defined in BridgeProtocol class. */
    public final String type;

    /** Robot module to send request to. */
    public final String module;

    /** Requested action. */
    public final String action;

    /** Request payload. */
    public final JsonElement payload;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new BridgeRequest instance.
     *
     * The request parameters are copied to member variables. The JSON payload is normalized to
     * ensure key names are all in lower case and do not have leading and trailing whitespace. The
     * constructor is declared @c private, instances of this class must be created using the static
     * factory method.
     *
     * @param   protocol        protocol on which request was received
     * @param   id              request id
     * @param   type            request type
     * @param   module          robot module to send request to
     * @param   action          requested action
     * @param   payload         request payload
     */
    private BridgeRequest(Protocol protocol, String id, String type, String module, String action, JsonElement payload)
    {
        // Copy method parameters to class members.
        this.protocol = protocol;
        this.id = id;
        this.type = type;
        this.module = module;
        this.action = action;
        this.payload = JsonUtils.normalizeKeys(payload);
    }

    /**
     * @name    Public Static Factory Methods
     * @{ 
     */ 

    /**
     * Creates a bridge request.
     *
     * If all method parameters are valid they are forwarded to the constructor unchanged. If not,
     * the method returns @c null.
     *
     * @param   protocol        protocol on which request was received
     * @param   id              request id
     * @param   type            request type
     * @param   module          robot module to send request to
     * @param   action          requested action
     * @param   payload         request payload
     */
    public static BridgeRequest create(Protocol protocol, String id, String type, String module, String action, JsonElement payload)
    {
        // The transport protocol, request type, module and action are all mandatory. */
        if (protocol == null) return null;

        return new BridgeRequest(protocol, id, type, module, action, payload);
    }

    /** @} */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Returns a string containing the @c type, @c module and @c action properties.
     *
     * @return  formatted request name
     */
    public String toString()
    {
        String s1 = ((type != null) && (type.isEmpty() == false)) ? type: "";
        String s2 = ((module != null) && (module.isEmpty() == false)) ? module: "";
        String s3 = ((action != null) && (action.isEmpty() == false)) ? action: "";
        return String.format("%s:%s:%s", s1, s2, s3);
    }

    /***********************************************************************************************
     * ENUMERATORS
     **********************************************************************************************/

    /**
     * Specifies transport protocol.
     */
    public enum Protocol
    {
        REST,                                   ///< REST protocol.
        WEBSOCKET                               ///< WebSocket protocol.
    }
}