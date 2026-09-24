/**
 * @file        HttpRequest.java
 * @brief       Implements HttpRequest class.
 */
package com.fbcti.sanbot.bridge.transport;

import com.fbcti.sanbot.bridge.util.StringUtils;

import java.util.Map;

/**
 * Represents an HTTP request.
 *
 * If a message is received on the HTTP socket a new instance of this class is created. Member
 * variables are implemented for storing the request properties including the HTTP method
 * (@c GET, @c PUT, @c POST, @c DELETE), the endpoint path, query parameters and HTTP headers.
 * All member variable are declared @c public @ final, no setter or getter are implemented. The
 * class does implement a public method that checks if the request is in fact a request to upgrade
 * to the WebSocket protocol instead of a 'normal' REST request.
 *
 * @version     1.0.001
 * @date        18 Jun 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class HttpRequest
{
    /** HTTP method. */
    public final String method;

    /** HTTP endpoint path. */
    public final String path;

    /** JSON body */
    public final String body;

    /** Query parameters */
    public final Map<String, String> queryParams;

    /** HTTP headers. */
    public final Map<String, String> httpHeaders;

    /** Optional request id. */
    public final String id;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new HttpRequest instance.
     *
     * The specified HTTP method, HTTP endpoint path, JSON body, query parameters and HTTP headers
     * are copied to member variables.
     *
     * @param   method          HTTP method
     * @param   path            HTTP endpoint path
     * @param   body            JSON body
     * @param   queryParams     query parameters
     * @param   httpHeaders     HTTP headers
     */
    public HttpRequest(String method, String path, String body, Map<String, String> queryParams, Map<String, String> httpHeaders)
    {
        // Copy method parameters to class members.
        this.method = method;
        this.path = path;
        this.body = body;
        this.queryParams = queryParams;
        this.httpHeaders = httpHeaders;
        this.id = requestId(queryParams, httpHeaders);
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Checks if the request is a request tu upgrade the connection to the WebSocket protocol.
     *
     * A request to upgrade to the WebSocket protocol is always a @c GET request, the value of the
     * value of the 'connection' header must contain 'upgrade', and the value of the 'upgrade'
     * header equals 'websocket' (both not case-sensitive).
     *
     * @return  @c true if request is upgrade request, @c false if this is not the case
     */
    public boolean isWebSocketUpgradeRequest()
    {
        // The request type must be GET.
        if ("GET".equals(method) == false) return false;

        // Check if the value of the 'connection' header contains 'upgrade', and if the value of
        // the 'upgrade' header equals 'websocket'.
        String connection = StringUtils.normalize(httpHeaders.get("connection"));
        String upgrade = httpHeaders.get("upgrade");
        return ((connection != null) && (connection.contains("upgrade")) && ("websocket".equalsIgnoreCase(StringUtils.trim(upgrade))));
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Returns the optional request id from HTTP headers or query parameters.
     *
     * If both are present, the id specified in the query parameters takes precedence over the id
     * specified by the @c x-request-id HTTP header.
     *
     * @param   queryParams     data map containing query parameters from request
     * @param   httpHeaders     data map containing HTTP headers from request
     *
     * @return  request id or @c null if no request id is present
     */
    private String requestId(Map<String, String> queryParams, Map<String, String> httpHeaders)
    {
        String id = (queryParams != null) ? queryParams.get(BridgeProtocol.FIELD_ID) : null;
        if (StringUtils.isBlank(id))
        {
            id = (httpHeaders != null) ? httpHeaders.get("x-request-id") : null;
        }
        return (StringUtils.isBlank(id) == false) ? StringUtils.trim(id) : null;
    }
}