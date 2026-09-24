/**
 * @file        RestTransport.java
 * @brief       Implements RestTransport class.
 */
package com.fbcti.sanbot.bridge.transport;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.util.JsonUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles REST traffic for the bridge server.
 *
 * The handleMessage() method handle an incoming REST request. The request can be a regular bridge
 * request (@e info, @e query or @e command) that returns a JsonResponse instance, a short-lived
 * media requests that returns a MediaResponse instance, or a stream media request that does not
 * return any data but opens a video or audio stream. The handleMessage() method converts the 
 * request into a protocol-neutral @c BridgeRequest, and delegates routing to the implementation of 
 * the BridgeTransport.AudioManagerHost interface.
 *
 * @version     1.0.001
 * @date        21 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class RestTransport extends BridgeTransport
{
    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new RestTransport instance.
     *
     * The base class constructor is called to copy the callback host to a member variable.
     *
     * @param   host            callback host to which to publish bridge transport events
     */
    public RestTransport(Host host)
    {
        super(host);
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Handles HTTP request on the REST interface.
     *
     * The transport handles a few HTTP-specific cases before dispatching a normal bridge request:
     * empty requests return a bad-request response, CORS preflight requests return HTTP 204,
     * unauthorized protected requests return HTTP 401. All other valid endpoints are dispatched
     * to the bridge service.
     *
     * All requests except public @e info @e GET requests require an API authorization key to be
     * specified as either an @e api_key query parameter or as an @e x-api-key request header. The
     * @e info:bridge:config request is protected because its response contains sensitive settings.
     *
     * @param   httpRequest     HTTP request to handleRequest
     * @param   socket          network socket
     * @param   outputStream    output stream assigned to write response data to
     */
    public void handleRequest(HttpRequest httpRequest, Socket socket, OutputStream outputStream)
    {
        RestPath restPath;
        String logPrefix = clientIp(socket) + " ";
        BridgeRequest bridgeRequest = null;

        try
        {
            // Handle an unreadable or empty request.
            if (httpRequest == null)
            {
                handleEmptyRequest(outputStream, logPrefix);
                return;
            }

            // Handle CORS preflight requests without entering bridge routing.
            if ("OPTIONS".equals(httpRequest.method))
            {
                handlePreflightRequest(outputStream, httpRequest, logPrefix);
                return;
            }

            // Parse the request path into version, type, module, and action fields.
            restPath = parsePath(httpRequest);

            // Handle unsupported API version.
            if ("v1".equals(restPath.version) == false)
            {
                handleInvalidRequest(outputStream, httpRequest, restPath, logPrefix);
                return;
            }

            // Handle invalid path request.
            if ((restPath.type == null) || (restPath.module == null) || (restPath.action == null))
            {
                handleInvalidRequest(outputStream, httpRequest, restPath, logPrefix);
                return;
            }

            // Validate the API key unless this is a public information request.
            String apiKey = httpRequest.queryParams.get("api_key");
            if (apiKey == null) apiKey = httpRequest.httpHeaders.get("x-api-key");
            boolean publicInfoRequest = ("GET".equals(httpRequest.method))
                && (BridgeProtocol.TYPE_INFO.equals(restPath.type))
                && ((BridgeProtocol.MODULE_BRIDGE.equals(restPath.module) == false)
                    || (BridgeProtocol.ACTION_CONFIG.equals(restPath.action) == false));
            if (publicInfoRequest == false)
            {
                if (host.isApiKeyValid(apiKey) == false)
                {
                    handleUnauthorizedRequest(outputStream, httpRequest, restPath, logPrefix);
                    return;
                }
            }

            // Create bridge request and dispatch to bridge service.
            if (BridgeProtocol.TYPE_STREAM.equals(restPath.type))
            {
                bridgeRequest = BridgeRequest.create(BridgeRequest.Protocol.REST, httpRequest.id, restPath.type, restPath.module, restPath.action, parseMediaPayload(httpRequest));
                handleStreamRequest(outputStream, socket, httpRequest, bridgeRequest, logPrefix);
            }
            else
            {
                bridgeRequest = BridgeRequest.create(BridgeRequest.Protocol.REST, httpRequest.id, restPath.type, restPath.module, restPath.action, parseJsonPayload(httpRequest));
                if (BridgeProtocol.TYPE_MEDIA.equals(restPath.type)) handleMediaRequest(outputStream, httpRequest, bridgeRequest, logPrefix);
                else handleBridgeRequest(outputStream, httpRequest, bridgeRequest, logPrefix);
            }
        }
        catch (IOException e)
        {
            handleInternalError(outputStream, bridgeRequest, e, logPrefix);
        }
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Handles a request containing no request data or malformed request data.
     *
     * A JsonResponse instance specifying the error is created and writeResponse() is called to
     * write the response to the output stream.
     *
     * @param   outputStream    output stream assigned to write response data to
     * @param   logPrefix       prefix for transport log messages
     *
     * @throws  IOException     thrown if bridge response data could not be written
     */
    private void handleEmptyRequest(OutputStream outputStream, String logPrefix) throws IOException
    {
        host.appendTrafficLog(logPrefix + "IN  <no data>");
        JsonResponse jsonResponse = JsonResponse.badRequest(null, "could not read request data");
        writeResponse(outputStream, jsonResponse);
        host.appendTrafficLog(logPrefix + "OUT " + jsonResponse.code);
    }

    /**
     * Handles a CORS preflight request
     *
     * WriteOptionsResponse() is called to write the response to the output stream.
     *
     * @param   outputStream    output stream assigned to write response data to
     * @param   httpRequest     HTTP request data
     * @param   logPrefix       prefix for transport log messages
     *
     * @throws  IOException     thrown if bridge response data could not be written
     */
    private void handlePreflightRequest(OutputStream outputStream, @NonNull HttpRequest httpRequest, String logPrefix) throws IOException
    {
        host.appendTrafficLog(logPrefix + "IN " + httpRequest.method);
        writeOptionsResponse(outputStream);
        host.appendTrafficLog(logPrefix + "OUT " + httpRequest.method + " " + httpRequest.path + " HTTP/1.1 " + HttpStatus.NO_CONTENT.toString());
    }

    /**
     * Handles a request with an invalid API path.
     *
     * A JsonResponse instance specifying the error is created and writeResponse() is called to
     * write the response to the output stream.
     *
     * @param   outputStream    output stream assigned to write response data to
     * @param   httpRequest     HTTP request data
     * @param   restPath        HTTP request path data
     * @param   logPrefix       prefix for transport log messages
     *
     * @throws  IOException     thrown if bridge response data could not be written
     */
    private void handleInvalidRequest(OutputStream outputStream, HttpRequest httpRequest, RestPath restPath, String logPrefix) throws IOException
    {
        host.appendTrafficLog(logPrefix + "IN  " + summarizeRequest(httpRequest));
        JsonResponse jsonResponse = JsonResponse.create(httpRequest.id,
            (restPath != null) ? restPath.type : null,
            (restPath != null) ? restPath.module : null,
            (restPath != null) ? restPath.action : null, JsonResponse.CODE_NOT_FOUND, "unsupported API version");
        writeResponse(outputStream, jsonResponse);
        host.appendTrafficLog(logPrefix + "OUT " + jsonResponse.code);
    }

    /**
     * Handles a request that failed REST authorization.
     *
     * A JsonResponse instance specifying the error is created and writeResponse() is called to
     * write the response to the output stream.
     *
     * @param   outputStream    output stream assigned to write response data to
     * @param   httpRequest     HTTP request data
     * @param   restPath        HTTP request path data
     * @param   logPrefix       prefix for transport log messages
     *
     * @throws  IOException     thrown if bridge response data could not be written
     */
    private void handleUnauthorizedRequest(OutputStream outputStream, HttpRequest httpRequest, @NonNull RestPath restPath, String logPrefix) throws IOException
    {
        host.appendTrafficLog(logPrefix + "IN  " + summarizeRequest(httpRequest));
        JsonResponse jsonResponse = JsonResponse.create(httpRequest.id,
            restPath.type, restPath.module, restPath.action, BridgeResponse.CODE_UNAUTHORIZED, "Missing or invalid X-API-Key header", null);
        writeResponse(outputStream, jsonResponse);
        host.appendTrafficLog(logPrefix + "OUT " + summarizeResponse(httpRequest, jsonResponse));
    }

    /**
     * Handles a stream media request.
     *
     * A stream event is published to the listener implemented by the bridge service to handle the
     * event.
     *
     * @param   outputStream    output stream assigned to write response data to
     * @param   socket          network socket
     * @param   httpRequest     HTTP request data
     * @param   bridgeRequest   bridge request to route
     * @param   logPrefix       prefix for transport log messages
     */
    private void handleStreamRequest(OutputStream outputStream, Socket socket, HttpRequest httpRequest, BridgeRequest bridgeRequest, String logPrefix)
    {
        host.appendTrafficLog(logPrefix + "IN  " + summarizeRequestNoPayload(httpRequest));
        host.routeStreamRequest(outputStream, socket, bridgeRequest);
        host.appendTrafficLog(logPrefix + "OUT " + httpRequest.method + " " + httpRequest.path + " stream");
    }

    /**
     * Handles a short-lived media request.
     *
     * Media request handling is delegated to the host. If sucessfull, the MediaResponse is sent.
     * Errors are converted to the bridge JSON error envelope before being sent with the HTTP status
     * code provided by the media response.
     *
     * @param   outputStream    output stream assigned to write response data to
     * @param   httpRequest     HTTP request data
     * @param   bridgeRequest   bridge request data
     * @param   logPrefix       prefix for transport log messages
     *
     * @throws  IOException     thrown if media response data could not be written
     */
    private void handleMediaRequest(OutputStream outputStream, HttpRequest httpRequest, BridgeRequest bridgeRequest, String logPrefix) throws IOException
    {
        try
        {
            host.appendTrafficLog(logPrefix + "IN  " + summarizeRequest(httpRequest));
            MediaResponse mediaResponse = host.routeMediaRequest(bridgeRequest);

            // If no media response was received create an error response.
            if (mediaResponse == null)
                mediaResponse = MediaResponse.create(bridgeRequest, HttpStatus.INTERNAL_SERVER_ERROR.code, "empty media response");

            // If media response is ok write data.
            if ((mediaResponse.ok()))
            {
                String contentType = (mediaResponse.mimetype != null) ? mediaResponse.mimetype : "application/octet-stream";
                writeBinaryResponse(outputStream, mediaResponse.code, contentType, mediaResponse.bytes, mediaResponse.metadata);
                return;
            }

            // Write error response
            JsonResponse jsonResponse = JsonResponse.create(bridgeRequest, mediaResponse.code, mediaResponse.message);
            writeBinaryResponse(outputStream, mediaResponse.code, "application/json; charset=utf-8", jsonResponse.toString().getBytes(StandardCharsets.UTF_8), null);
            host.appendTrafficLog(logPrefix + "OUT " + summarizeMediaResponse(httpRequest, mediaResponse));
        }
        catch (IOException ignored) {}
    }

    /**
     * Handles a routed REST bridge request.
     *
     * Bridge request handling is delegated to the host, and the response is written to the output
     * stream.
     *
     * @param   outputStream    output stream assigned to write response data to
     * @param   httpRequest     HTTP request data
     * @param   bridgeRequest   bridge request data
     * @param   logPrefix       prefix for transport log messages
     */
    private void handleBridgeRequest(OutputStream outputStream, HttpRequest httpRequest, BridgeRequest bridgeRequest, String logPrefix) throws IOException
    {
        host.appendTrafficLog(logPrefix + "IN  " + summarizeRequest(httpRequest));
        JsonResponse response = host.routeBridgeRequest(bridgeRequest);
        writeResponse(outputStream, response);
        host.appendTrafficLog(logPrefix + "OUT " + summarizeResponse(httpRequest, response));
    }

    /**
     * Sends a structured internal error response for a failed REST request when possible.
     *
     * @param   outputStream    output stream assigned to write response data to
     * @param   bridgeRequest   bridge request data, or @c null if request data is not available
     * @param   exception       exception that caused the failure
     * @param   logPrefix       prefix for transport log messages
     */
    private void handleInternalError(OutputStream outputStream, BridgeRequest bridgeRequest, Exception exception, String logPrefix)
    {
        try
        {
            if (outputStream == null) host.appendTrafficLog(logPrefix + "OUT Internal server error " + exception.getClass().getSimpleName());
            else
            {
                JsonResponse response = JsonResponse.create(bridgeRequest, JsonResponse.CODE_INTERNAL_SERVER_ERROR, exception.getClass().getSimpleName());
                writeResponse(outputStream, response);
            }
        }
        catch (IOException ignored)
        {
        }
        host.appendTrafficLog(logPrefix + "OUT IO error " + exception.getClass().getSimpleName());
    }

    /**
     * Writes a bridge response object to the output stream.
     *
     * The bridge response is serialized as JSON and wrapped in an HTTP response with a status code,
     * CORS headers, content length, and a close-connection marker.
     *
     * @param   outputStream    output stream assigned to write response data to
     * @param   jsonResponse    bridge response object to be sent
     *
     * @throws  IOException if data could not be written
     */
    private void writeResponse(OutputStream outputStream, @NonNull JsonResponse jsonResponse) throws IOException
    {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        String response = jsonResponse.toString();
        writer.write("HTTP/1.1 " + jsonResponse.code + " " + HttpStatus.toText(jsonResponse.code) + "\r\n");
        for (String s : getCorsHeaders()) writer.write(s + "\r\n");
        writer.write("Content-Type: application/json; charset=utf-8\r\n");
        writer.write("Content-Length: " + response.getBytes(StandardCharsets.UTF_8).length + "\r\n");
        writer.write("Connection: close\r\n");
        writer.write("\r\n");
        writer.write(response);
        writer.flush();
    }

    /**
     * Writes the response to a CORS preflight request to the output stream.
     *
     * The response is an empty HTTP 204 message with the CORS headers used by the browser-based
     * clients.
     *
     * @param   outputStream    output stream assigned to write response data to
     *
     * @throws  IOException     thrown if data could not be written
     */
    private void writeOptionsResponse(OutputStream outputStream) throws IOException
    {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.write("HTTP/1.1 " + HttpStatus.NO_CONTENT.toString() + "\r\n");
        for (String s : getCorsHeaders()) writer.write(s + "\r\n");
        writer.write("Content-Length: 0\r\n");
        writer.write("Connection: close\r\n");
        writer.write("\r\n");
        writer.flush();
    }

    /**
     * Writes a raw HTTP response body to the output stream.
     *
     * @param   outputStream    output stream assigned to socket
     * @param   statusCode      HTTP status code
     * @param   contentType     response MIME type
     * @param   body            response body
     * @param   xHeaders        optional X-headers to add to response
     *
     * @throws  IOException     thrown if data could not be written
     */
    private void writeBinaryResponse(OutputStream outputStream, int statusCode, String contentType, byte[] body, Map<String, Object> xHeaders) throws IOException
    {
        byte[] responseBody = (body != null) ? body : new byte[0];
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(statusCode).append(' ').append(HttpStatus.toText(statusCode)).append("\r\n");
        headers.append("Content-Type: ").append(contentType).append("\r\n");
        headers.append("Content-Length: ").append(responseBody.length).append("\r\n");
        for (String corsHeader : getCorsHeaders()) headers.append(corsHeader).append("\r\n");
        if (xHeaders != null)
        {
            for (Map.Entry<String, Object> xHeader : xHeaders.entrySet())
            {
                Object key = xHeader.getKey();
                if (key != null) headers.append(String.format("X-%s: ", key)).append(xHeader.getValue().toString()).append("\r\n");
            }
        }
        headers.append("Connection: close\r\n\r\n");
        outputStream.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.write(responseBody);
        outputStream.flush();
    }

    /**
     * Creates an instance of the RestPath class from the path in the HTTTP request.
     *
     * Regular REST endpoints use @c /{version}/{type}/{module}/{action}. The public helper paths
     * @c /status and @c /openapi are accepted as aliases for their bridge info requests before the
     * generic path parser runs.
     *
     * @param   httpRequest     HTTP request from which to create path object
     *
     * @return  instance of RestPath containing REST path data
     */
    @NonNull
    private RestPath parsePath(@NonNull HttpRequest httpRequest)
    {
        String path = normalizePath(httpRequest.path);

        // Treat /status as an alias for info:bridge:status.
        if (("GET".equals(httpRequest.method)) && ("/status".equals(path)))
            return new RestPath(path, "v1", BridgeProtocol.TYPE_INFO, BridgeProtocol.MODULE_BRIDGE, BridgeProtocol.ACTION_SET);

        // Treat /openapi as an alias for info:bridge:openapi.
        if (("GET".equals(httpRequest.method)) && ("/openapi".equals(path)))
            return new RestPath(path, "v1", BridgeProtocol.TYPE_INFO, BridgeProtocol.MODULE_BRIDGE, BridgeProtocol.ACTION_OPENAPI);

        String[] parts = path.split("/");
        if (parts.length == 4)
            return new RestPath(path, null, parts[1], parts[2], parts[3]);
        if (parts.length == 5)
            return new RestPath(path, parts[1], parts[2], parts[3], parts[4]);

        return new RestPath(path, null, null, null, null);
    }

    /**
     * Parses the REST request payload.
     *
     * GET requests use query parameters as payload because REST GET requests should not depend on a
     * request body. Other REST callers normally send only the payload object in the HTTP body. For
     * compatibility with older clients, a wrapper object with a @c data field is still unwrapped.
     * Request identifiers are intentionally not read from the REST body.
     *
     * @param   request         HTTP request
     *
     * @return  payload data as JSON element
     */
    private JsonElement parseJsonPayload(HttpRequest request)
    {
        if ((request != null) && ("GET".equals(request.method)))
        {
            JsonObject payload = new JsonObject();
            if (request.queryParams == null) return payload;

            for (Map.Entry<String, String> entry : request.queryParams.entrySet())
            {
                payload.add(entry.getKey(), parseQueryParamValue(entry.getValue()));
            }
            return payload;
        }

        if ((request == null) || StringUtils.isBlank(request.body)) return new JsonObject();

        try
        {
            JsonElement json = JsonUtils.parseJsonElement(request.body, new JsonObject());
            if ((json != null) && (json.isJsonObject()))
            {
                JsonObject raw = json.getAsJsonObject();
                JsonElement data = JsonUtils.getElement(raw, BridgeProtocol.FIELD_DATA);
                if (data != null)
                {
                    return data;
                }
            }
            return json;
        }
        catch (IllegalStateException ignored)
        {
        }
        return new JsonObject();
    }

    /**
     * Parses the media request payload from REST query parameters.
     *
     * REST media endpoints use query parameters, while WebSocket media requests use a JSON payload.
     * Copying the query parameters into a JSON object lets the host share the same media request
     * logic for both transports.
     *
     * @param   request         HTTP media request
     *
     * @return  JSON payload containing query parameters
     */
    @NonNull
    private JsonObject parseMediaPayload(HttpRequest request)
    {
        JsonObject payload = new JsonObject();
        if ((request == null) || (request.queryParams == null)) return payload;

        for (Map.Entry<String, String> entry : request.queryParams.entrySet())
        {
            payload.add(entry.getKey(), parseQueryParamValue(entry.getValue()));
        }
        return payload;
    }
    /**
     * Converts one REST query-parameter value to a JSON value.
     *
     * Comma-separated values are normalized to JSON arrays. Individual scalar items are parsed as
     * JSON literals when possible so `true` becomes a boolean and `5` becomes a number.
     *
     * @param   value           query-parameter value
     *
     * @return  JSON value representing the query-parameter value
     */
    @NonNull
    private JsonElement parseQueryParamValue(String value)
    {
        String text = StringUtils.trim(value);
        if (text == null) return new JsonPrimitive("");

        if (text.contains(","))
        {
            JsonArray array = new JsonArray();
            for (String part : text.split(","))
            {
                String item = part.trim();
                if (item.isEmpty()) continue;
                JsonElement parsedItem = JsonUtils.parseJsonElement(item, null);
                array.add((parsedItem != null) ? parsedItem : new JsonPrimitive(item));
            }
            return array;
        }

        JsonElement parsed = JsonUtils.parseJsonElement(text, null);
        return (parsed != null) ? parsed : new JsonPrimitive(text);
    }

    /**
     * Returns the CORS headers used by the REST and browser explorer clients.
     */
    @NonNull
    private List<String> getCorsHeaders()
    {
        List<String> headers = new ArrayList<>();
        headers.add("Access-Control-Allow-Origin: *");
        headers.add("Access-Control-Allow-Headers: Content-Type, X-API-Key, X-Request-Id");
        headers.add("Access-Control-Allow-Private-Network: true");
        headers.add("Access-Control-Allow-Methods: GET, POST, OPTIONS");
        return headers;
    }

    /**
     * Summarizes the HTTP request for logging purposes.
     *
     * The HTTP request is summarized as:
     * @verbatim
     *      {method} {path} {payload}
     * @endverbatim
     * The @c normalizePayload() method is called to clean up the payload string and limit its
     * length.
     *
     * @param   httpRequest     HTTP request object
     *
     * @return  string containing summarized HTTP request
     */
    @NonNull
    private String summarizeRequest(@NonNull HttpRequest httpRequest)
    {
        StringBuilder summary = new StringBuilder();
        summary.append(httpRequest.method).append(" ").append(httpRequest.path);

        if (StringUtils.isBlank(httpRequest.body) == false)
            summary.append(" ").append(httpRequest.body);

        return summary.toString();
    }

    /**
     * Summarizes the HTTP stream media request for logging purposes.
     *
     * The HTTP request is summarized as:
     * @verbatim
     *      {method} {path}
     * @endverbatim
     *
     * @param   httpRequest     HTTP request object
     *
     * @return  string containing summarized HTTP request
     */
    @NonNull
    private String summarizeRequestNoPayload(@NonNull HttpRequest httpRequest)
    {
        StringBuilder summary = new StringBuilder();
        summary.append(httpRequest.method).append(" ").append(httpRequest.path);

        return summary.toString();
    }

    /**
     * Summarizes the bridge response for logging purposes.
     *
     * The bridge response is summarized as:
     * @verbatim
     *      {method} {path} {statuscode} {payload}
     * @endverbatim
     * The @c normalizePayload() method is called to clean up the payload string and limit its
     * length.
     *
     * @param   httpRequest     HTTP request object
     * @param   jsonResponse    bridge response object
     *
     * @return  string containing summarized bridge response
     */
    @NonNull
    private String summarizeResponse(@NonNull HttpRequest httpRequest, @NonNull JsonResponse jsonResponse)
    {
        StringBuilder summary = new StringBuilder();
        summary.append(httpRequest.method).append(" ").append(httpRequest.path).append(" ");
        summary.append(jsonResponse.code).append(" ").append(HttpStatus.toText(jsonResponse.code));
        summary.append(" ").append(jsonResponse.toSummary());
        return summary.toString();
    }

    /**
     * Summarizes a media response for traffic logging.
     *
     * @param   httpRequest     HTTP request object
     * @param   binaryResponse  media response object
     *
     * @return  string containing summarized media response
     */
    @NonNull
    private String summarizeMediaResponse(@NonNull HttpRequest httpRequest, MediaResponse binaryResponse)
    {
        StringBuilder summary = new StringBuilder();
        summary.append(httpRequest.method).append(" ").append(httpRequest.path).append(" media ");
        if (binaryResponse == null) summary.append("500 empty media response");
        else if (binaryResponse.ok()) summary.append(binaryResponse.code).append(" ").append(binaryResponse.bytes.length).append(" bytes");
        else summary.append(binaryResponse.code).append(" ").append(binaryResponse.message);
        return summary.toString();
    }

    /**
     * Normalizes the REST request path.
     *
     * Leading and trailing whitespace is removed, query parameters are stripped, a leading slash is
     * inserted when missing, and a trailing slash is removed for non-root paths.
     *
     * @param   path            path to normalize
     *
     * @return  normalized path
     */
    @NonNull
    private String normalizePath(String path)
    {
        if (path == null) return "";

        // Remove leading and trailing white space.
        String normalized = path.trim();

        // Remove query parameters.
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) normalized = normalized.substring(0, queryIndex);

        // Insert slash if path does not start with one.
        if (normalized.startsWith("/") == false) normalized = "/" + normalized;

        // Remove the trailing slash from non-root paths.
        if ((normalized.endsWith("/")) && (normalized.length() > 1)) normalized = normalized.substring(0, normalized.length() - 1);

        return normalized.toLowerCase(Locale.US);
    }

    /***********************************************************************************************
     * CLASSES
     **********************************************************************************************/

    /** Parsed representation of a normalized REST endpoint path */
    private static final class RestPath
    {
        /* Normalized REST API path. */
        final String path;

        /** REST API version. */
        final String version;

        /** Request type extracted from path. */
        final String type;

        /** Request module extracted from path. */
        final String module;

        /** Request action extracted from path. */
        final String action;

        /**
         * Constructs a new RestPath instance.
         *
         * The method parameters are copied to class member variables.
         *
         * @param   path        normalized REST API path
         * @param   version     REST API version
         * @param   type        request type extracted from path
         * @param   module      request module extracted from path
         * @param   action      request action extracted from path
         */
        RestPath(String path, String version, String type, String module, String action)
        {
            this.path = path;
            this.version = version;
            this.type = type;
            this.module = module;
            this.action = action;
        }
    }
}
