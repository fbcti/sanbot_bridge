/**
 * @file        WebSocketTransport.java
 * @brief       Implements WebSocketTransport class.
 */
package com.fbcti.sanbot.bridge.transport;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;
import com.fbcti.sanbot.bridge.util.BridgeLog;

import com.fbcti.sanbot.bridge.util.JsonUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles REST traffic for the bridge server.
 *
 * The handleMessage() method handle an incoming request to upgrade the connection to a WebSocket
 * connection. If successful, The receiveMessages() method handles incoming WebSocket requests
 * until the client connection is dropped. The  request can be a regular bridge request (@e info,
 * @e query or @e command) that returns a JsonResponse instance, or a short-lived media requests
 * that returns a MediaResponse instance. The method converts the request into a protocol-neutral
 * @c BridgeRequest, and delegates routing to the implementation of the BridgeTransport.AudioManagerHost
 * interface.
 *
 * The broadcastEvent() method relays events to connected WebSocket clients.
 *
 * @version     1.0.001
 * @date        21 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class WebSocketTransport extends BridgeTransport
{
    /** Source label used for log messages. */
    private static final String TAG = "WebSocketTransport";

    /** Websocket GUID. */
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** Parses JSON objects. */
    private static final Gson GSON = new Gson();

    /** Date-time format for bridge events. */
    private static final SimpleDateFormat EVENT_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault());

    /** Object to lock authentication requests. */
    private final Object authorizationLock = new Object();

    /** THe one WebSocket session with read-write authorization. */
    private WebSocketSession readWriteSession;

    /** List of active WebSocket sessions. */
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    /** Specifies number of WebSocket sessions. */
    private final AtomicLong sessionCounter = new AtomicLong(1L);

    /** Event counter. */
    private final AtomicLong eventCounter = new AtomicLong(1L);

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new WebSocketTransport instance.
     *
     * The base class constructor is called to copy the event listener to a member variable.
     *
     * @param   host            callback host to which to publish bridge transport events
     */
    public WebSocketTransport(Host host)
    {
        super(host);
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Handles WebSocket upgrade request.
     *
     * If an upgrade message is received an 'accepted' message is sent to the client and the
     * server starts receiving and handling WebSocket frames.
     *
     * @param   httpRequest     HTTP request to handle
     * @param   socket          network socket
     * @param   inputStream     input stream assigned to socket
     * @param   outputStream    output stream assigned to socket
     */
    public void handleRequest(HttpRequest httpRequest, Socket socket, InputStream inputStream, OutputStream outputStream)
    {
        boolean upgradeAccepted = false;
        String logPrefix = "WS " + clientIp(socket) + " ";

        try
        {
            // Check if message contains secure WebSocket key. If this is not the case, send an
            // error response sent to the client. informing the client the socket upgrade has
            // failed.
            String websocketKey = httpRequest.httpHeaders.get("sec-websocket-key");
            if (StringUtils.isBlank(websocketKey))
            {
                sendError(outputStream, 400, "WebSocket Invalid", "Missing sec-websocket-key header", logPrefix);
                host.appendTrafficLog(logPrefix + "OUT WebSocket Invalid 400");
                return;
            }

            // Respond to the client to inform the socket upgrade is accepted.
            sendUpgradeAccepted(outputStream, websocketKey, logPrefix);
            upgradeAccepted = true;

            // Register the new WebSocket session.
            WebSocketSession session = new WebSocketSession(socket, clientIp(socket), sessionCounter.getAndIncrement(), outputStream);
            sessions.add(session);
            host.appendTrafficLog("connect " + httpRequest.path + " Unauthorized");

            // Start receiving WebSocket frames.
            receiveMessages(socket, session, inputStream);
        }
        catch (Exception e)
        {
            // If the socket upgrade is not yet accepted it is still possible to send an HTTP error.
            if (upgradeAccepted == false) 
                sendError(outputStream, 500, "WebSocket Error", e.getClass().getSimpleName(), logPrefix);
            host.appendTrafficLog(logPrefix + "OUT WebSocket Error " + e.getClass().getSimpleName());
        }
    }

    /**
     * Sends an event to authorized clients.
     *
     * A new JSON object is created containing the specified module and event type, as well as
     * an auto-incremented event id and the current time stamp. If additional event data is
     * available it is parsed and added to the JSON object. The resulting JSON object is sent to
     * all authorized WebSocket clients.
     *
     * @param   module          robot module that raised the event
     * @param   event           event type
     * @param   data            additional event data
     */
    public void broadcastEvent(String module, String event, Object data)
    {
        // Create JSON object.
        JsonObject payload = new JsonObject();
        payload.addProperty(BridgeProtocol.FIELD_ID, "evt-" + eventCounter.getAndIncrement());
        payload.addProperty(BridgeProtocol.FIELD_TIMESTAMP, EVENT_TIME_FORMAT.format(new Date()));
        payload.addProperty(BridgeProtocol.FIELD_TYPE, BridgeProtocol.TYPE_EVENT);
        payload.addProperty(BridgeProtocol.FIELD_MODULE, module);
        payload.addProperty(BridgeProtocol.FIELD_EVENT, event);
        if (data != null) payload.add(BridgeProtocol.FIELD_DATA, GSON.toJsonTree(data));

        String json = payload.toString();
        StringBuilder recipients = new StringBuilder();
        for (WebSocketSession session : sessions)
        {
            // Skip unauthorized clients.
            if (session.authorizationLevel == AuthorizationLevel.NONE) continue;

            // Send event.
            try
            {
                sendText(session, json);
                appendRecipient(recipients, session);
            }
            catch (IOException e)
            {
                BridgeLog.warning(TAG, "Could not push WebSocket event", e);
            }
        }
        host.appendTrafficLog("WS EVT " + BridgeProtocol.TYPE_EVENT + ":" + module + ":" + event + " recipients=" + (recipients.length() > 0 ? recipients.toString() : "none"));
    }

    /**
     * Returns the number of connected WebSocket sessions.
     *
     * @return  number of WebSocket sessions.
     */
    public int getConnectedSessionCount()
    {
        return sessions.size();
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Sends a HTML response specifying the WebSocket upgrade request is accepted.
     *
     * @param   outputStream    output stream to write upgrade response to
     * @param   webSocketKey    unique WebSocket key
     * @param   logPrefix       prefix for transport log messages
     */
    private void sendUpgradeAccepted(OutputStream outputStream, String webSocketKey, String logPrefix)
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            writer.write("HTTP/1.1 101 Switching Protocols\r\n");
            writer.write("Upgrade: websocket\r\n");
            writer.write("Connection: Upgrade\r\n");
            writer.write("Sec-WebSocket-Accept: " + createAcceptKey(webSocketKey) + "\r\n");
            writer.write("\r\n");
            writer.flush();
        }
        catch (IOException e)
        {
            host.appendTrafficLog(logPrefix + "OUT WebSocket Error " + e.getClass().getSimpleName());
        }
    }

    /**
     * Sends a HTML error response.
     *
     * @param   outputStream    output stream to write error response to
     * @param   statusCode      error code
     * @param   description     error description
     * @param   message         exception message text
     * @param   logPrefix       prefix for transport log messages
     */
    private void sendError(OutputStream outputStream, int statusCode, String description, String message, String logPrefix)
    {
        try
        {
            String status = HttpStatus.toText(statusCode);
            JsonResponse response = JsonResponse.create(null, statusCode, description);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            String jsonObject = response.toString();
            writer.write("HTTP/1.1 " + statusCode + " " + status + "\r\n");
            writer.write("Content-Type: application/json; charset=utf-8\r\n");
            writer.write("Content-Length: " + jsonObject.getBytes(StandardCharsets.UTF_8).length + "\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.write(jsonObject);
            writer.flush();
        }
        catch (IOException e)
        {
            host.appendTrafficLog(logPrefix + "OUT WebSocket Error " + e.getClass().getSimpleName());
        }
    }

    /**
     * Main loop that receives and handles WebSocket messages.
     *
     * The socket keeps receiving messages until the socket is closed or a CLOSE frame is
     * received. Note that only single-frame messages are accepted. This should be okay since
     * the only short messages are send and received.
     *
     * @param   socket          network socket
     * @param   wsSession       WebSocket session
     * @param   inputStream     input stream from which to read message data
     *
     * @throws IOException      thrown if bridge response data could not be written
     */
    private void receiveMessages(@NonNull Socket socket, WebSocketSession wsSession, InputStream inputStream) throws IOException
    {
        try
        {
            while (socket.isClosed() == false)
            {
                WebSocketFrame frame = readFrame(inputStream);
                if (frame == null) break;

                // TEXT frame with FIN bit set - handleRequest the message.
                if ((frame.opcode == 0x1) && (frame.fin == true))
                {
                    handleMessage(wsSession, new String(frame.payload, StandardCharsets.UTF_8));
                    continue;
                }

                // CLOSE frame (opcode 8) - exit the while-loop to stop receiving frames.
                if (frame.opcode == 0x8)
                {
                    sendControlFrame(wsSession, 0x8, frame.payload);
                    break;
                }
                // PING frame (opcode 9) - respond by sending a PONG frame.
                if (frame.opcode == 0x9)
                {
                    sendControlFrame(wsSession, 0xA, frame.payload);
                    continue;
                }
                // PONG frame (opcode 10) - no action required,
                if (frame.opcode == 0xA) continue;

                // Invalid frame.
                JsonResponse response = JsonResponse.create(null, JsonResponse.CODE_BAD_REQUEST, "Invalid frame - only single-frame text messages are supported");
                sendText(wsSession, response.toString());
            }
        }
        catch (EOFException | SocketException ignored)
        {
            // Browser tabs can close the TCP socket before the WebSocket close handshake completes.
        }
        finally
        {
            boolean logDisconnect = (wsSession.suppressDisconnectLog == false);
            sessions.remove(wsSession);
            releaseSessionAuthorization(wsSession);
            if (logDisconnect) host.appendTrafficLog(logPrefix(wsSession) + "disconnect");
        }
    }

    /**
     * Handles a WebSocket message.
     *
     * A new BridgeRequest instance is created from the WebSocket message string.
     *
     * @param   wsSession       WebSocket session
     * @param   message         WebSocket message to handle
     */
    private void handleMessage(WebSocketSession wsSession, String message)
    {
        String logMessage = message;
        JsonObject logObject = JsonUtils.parseJsonObject(message, null);
        if (logObject != null)
        {
            JsonElement data = logObject.get(BridgeProtocol.FIELD_DATA);
            if ((data != null) && (data.isJsonObject()) && (data.getAsJsonObject().size() == 0))
                logObject.remove(BridgeProtocol.FIELD_DATA);
            logMessage = GSON.toJson(logObject);
        }
        host.appendTrafficLog(logPrefix(wsSession) + "IN " + logMessage);

        // Create bridge request.
        BridgeRequest request = parseBridgeRequest(message);

        // Validate request.
        JsonResponse validationError = validateRequest(request);
        if (validationError != null)
        {
            sendBridgeResponse(wsSession, validationError);
            return;
        }

        // Handle authorization request.
        if (isAuthorizeRequest(request))
        {
            handleAuthorizationRequest(wsSession, request);
            return;
        }

        // Handle media request.
        if (BridgeProtocol.TYPE_MEDIA.equals(request.type))
        {
            handleMediaRequest(wsSession, request);
            return;
        }

        // Handle bridge request.
        handleBridgeRequest(wsSession, request);
    }

    /**
     * Validates the request.
     *
     * A valid request has at least a request id, the request type must be one of the @c TYPE_*
     * (but not @c TYPE_STREAM) constants defined by the BridgeProtocol class, and both the module
     * and action may not be empty.
     *
     * @param   request         request to validate
     *
     * @return  @c null if request is valid, JsonResponse instance if invalid
     */
    @Nullable
    private JsonResponse validateRequest(BridgeRequest request)
    {
        // If request is null return error response.
        if (request == null)
            return JsonResponse.error(null, JsonResponse.CODE_BAD_REQUEST,"expected JSON payload");

        // If request id is not set return error response.
        if ((request.id == null) || (request.id.isEmpty()))
            return JsonResponse.error(request, JsonResponse.CODE_BAD_REQUEST, "missing id field");

        // Check if request type is supported. If not, return error response.
        if ((BridgeProtocol.TYPE_COMMAND.equals(request.type) == false)
            && (BridgeProtocol.TYPE_INFO.equals(request.type) == false)
            && (BridgeProtocol.TYPE_QUERY.equals(request.type) == false)
            && (BridgeProtocol.TYPE_MEDIA.equals(request.type) == false))
            return JsonResponse.error(request, JsonResponse.CODE_BAD_REQUEST, "expected type=command, type=info, type=query or type=media");

        // If request module is not set return error response.
        if ((request.module == null) || (request.module.isEmpty()))
            return JsonResponse.error(request, JsonResponse.CODE_BAD_REQUEST, "missing module field");

        // If request action is not set return error response.
        if (request.action == null || request.action.isEmpty())
            return JsonResponse.error(request, JsonResponse.CODE_BAD_REQUEST, "missing action field");

        return null;
    }

    /**
     * Handles an authorization request.
     *
     * If the request payload contains a valid API authorization key the authorization level is
     * set and a response is sent to the WebSocket client. If not, an error response is sent.
     *
     * @param   wsSession       WebSocket session
     * @param   request         bridge request data
     */
    private void handleAuthorizationRequest(WebSocketSession wsSession, @NonNull BridgeRequest request)
    {
        String key = JsonUtils.getString(JsonUtils.toJsonObject(request.payload, null), BridgeProtocol.FIELD_KEY);
        if (StringUtils.isBlank(key))
        {
            // No API Key in payload.
            clearSessionAuthorization(wsSession);
            sendBridgeResponse(wsSession, JsonResponse.unauthorized(request, "missing API key"));
            return;
        }
        if (host.isApiKeyValid(key) == false)
        {
            // API key is invalid.
            clearSessionAuthorization(wsSession);
            sendBridgeResponse(wsSession, JsonResponse.unauthorized(request, "invalid API key"));
            return;
        }

        // Authorization successful.
        AuthorizationLevel authorizationLevel = authorizeSession(wsSession);
        sendBridgeResponse(wsSession, JsonResponse.create(request, JsonResponse.CODE_OK, authorizationLevel.value()));
    }

    /**
     * Handles a bridge request.
     *
     * Bridge request handling is delegated to the host. The sendBridgeResponse() method is called
     * to send the returned JsonResponse instance to the WebSocket client.
     *
     * @param   wsSession       WebSocket session
     * @param   request         bridge request data
     */
    private void handleBridgeRequest(WebSocketSession wsSession, BridgeRequest request)
    {
        // Check if authorized for media request.
        JsonResponse response = validateBridgeRequestAuthorization(wsSession, request);
        if (response != null)
        {
            sendBridgeResponse(wsSession, response);
            return;
        }

        // Route bridge request. Send error response if no media response is received.
        response = host.routeBridgeRequest(request);
        if (response == null) response = JsonResponse.internalError(request, "empty bridge response");
        sendBridgeResponse(wsSession, response);
    }

    /**
     * Handles a media request.
     *
     * Media request handling is delegated to the host. If successful, the sendMediaResponse()
     * method is called to send the returned MediaResponse instance to the WebSocket client. If
     * not authorized for media request, or no media response was returned, or the request failed,
     * sendMediaErrorResponse() is called to send an error response.
     *
     * @param   wsSession       WebSocket session
     * @param   request         bridge request data
     */
    private void handleMediaRequest(WebSocketSession wsSession, BridgeRequest request)
    {
        // Check if authorized for media request.
        MediaResponse response = validateMediaRequestAuthorization(wsSession, request);
        if (response != null)
        {
            sendMediaErrorResponse(wsSession, request, response);
            return;
        }

        // Route media request. Send error response if no media response is received.
        response = host.routeMediaRequest(request);
        if (response == null)
        {
            sendMediaErrorResponse(wsSession, request, MediaResponse.internalError(request, "empty media response"));
            return;
        }

        // If response is not ok send media response with error message.
        if (response.ok() == false)
        {
            sendMediaErrorResponse(wsSession, request, response);
            return;
        }

        // Send media response.
        sendMediaResponse(wsSession, request, response);
    }

    /**
     * Sends a JsonResponse instance to the WebSocket client.
     *
     * @param   wsSession       WebSocket session
     * @param   response        JSON response data
     */
    private void sendBridgeResponse(WebSocketSession wsSession, @NonNull JsonResponse response)
    {
        try
        {
            sendText(wsSession, response.toString());
            host.appendTrafficLog(logPrefix(wsSession) + "OUT " + response.toSummary());
        }
        catch (IOException e)
        {
            BridgeLog.error(TAG, "Could not send WebSocket response", e);
        }
    }

    /**
     * Sends a MediaResponse instance for a successful media request to the WebSocket client.
     *
     * Since the request succeeded, the response is sent as one JSON metadata frame followed by
     * one binary frame containing the actual media payload.
     *
     * @param   wsSession       WebSocket session
     * @param   request         request data
     * @param   response        response data to send
     */
    private void sendMediaResponse(WebSocketSession wsSession, @NonNull BridgeRequest request, @NonNull MediaResponse response)
    {
        try
        {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("contentType", response.mimetype);
            metadata.addProperty("length", response.bytes.length);
            metadata.addProperty("binaryFrame", "next");
            sendTextAndBinary(wsSession, buildMediaResponseJson(response, metadata), response.bytes);
            host.appendTrafficLog(logPrefix(wsSession) + "OUT media " + request.module + ":" + request.action + " " + response.code + " " + response.bytes.length + " bytes");
        }
        catch (IOException e)
        {
            BridgeLog.error(TAG, "Could not send WebSocket media response", e);
        }
    }

    /**
     * Sends a MediaResponse instance for a failed media request to the WebSocket client.
     *
     * Since the request failed, only the JSON response envelope is sent. No binary frame follows.
     *
     * @param   wsSession       WebSocket session
     * @param   request         request data
     * @param   response        response data to send
     */
    private void sendMediaErrorResponse(WebSocketSession wsSession, @NonNull BridgeRequest request, MediaResponse response)
    {
        try
        {
            sendText(wsSession, buildMediaResponseJson(response, null));
            host.appendTrafficLog(logPrefix(wsSession) + "OUT media " + request.module + ":" + request.action + " " + response.code + " " + response.message);
        }
        catch (IOException e)
        {
            BridgeLog.error(TAG, "Could not send WebSocket media response", e);
        }
    }
    
    /**
     * Sends a text-only media response to the WebSocket client, 
     * 
     * @param   wsSession       WebSocket session
     * @param   message         message to write
     * 
     * @throws  IOException     thrown if WebSocket frame could not be written
     */
    private void sendText(@NonNull WebSocketSession wsSession, @NonNull String message) throws IOException
    {
        byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
        synchronized (wsSession.sendLock)
        {
            writeFrame(wsSession.outputStream, 0x1, buffer);
        }
    }

    /**
     * Sends a media response containing binary payload data to the WebSocket client, 
     *
     * @param   wsSession       WebSocket session
     * @param   message         message to write
     * @param   bytes           binary frame data
     *
     * @throws  IOException     thrown if WebSocket frame could not be written
     */
    private void sendTextAndBinary(@NonNull WebSocketSession wsSession, @NonNull String message, byte[] bytes) throws IOException
    {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] binaryBytes = (bytes != null) ? bytes : new byte[0];
        synchronized (wsSession.sendLock)
        {
            writeFrame(wsSession.outputStream, 0x1, messageBytes);
            writeFrame(wsSession.outputStream, 0x2, binaryBytes);
        }
    }

    /**
     * Writes a WebSocket control frame. 
     *
     * @param   wsSession       WebSocket session
     * @param   opcode          WebSocket opcode
     * @param   bytes           frame data
     *
     * @throws  IOException     thrown if WebSocket frame could not be written
     */
    private void sendControlFrame(@NonNull WebSocketSession wsSession, int opcode, byte[] bytes) throws IOException
    {
        synchronized (wsSession.sendLock)
        {
            writeFrame(wsSession.outputStream, opcode, (bytes != null) ? bytes : new byte[0]);
        }
    }

    /**
     * Writes a single WebSocket frame to the output stream.
     *
     * @param   outputStream    stream to write WebSocket frame to
     * @param   opcode          WebSocket opcode
     * @param   bytes           WebSocket payload
     *
     * @throws  IOException     thrown if WebSocket frame could not be written
     */
    private void writeFrame(@NonNull OutputStream outputStream, int opcode, @NonNull byte[] bytes) throws IOException
    {
        outputStream.write(0x80 | (opcode & 0x0F));
        if (bytes.length <= 125) outputStream.write(bytes.length);
        else if (bytes.length <= 0xFFFF)
        {
            outputStream.write(126);
            outputStream.write((bytes.length >> 8) & 0xFF);
            outputStream.write(bytes.length & 0xFF);
        }
        else
        {
            outputStream.write(127);
            long length = bytes.length;
            for (int shift=56; shift>=0; shift-=8)
            {
                outputStream.write((int) ((length >> shift) & 0xFF));
            }
        }
        outputStream.write(bytes);
        outputStream.flush();
    }

    /**
     * Creates a BridgeRequest instance from the specified JSON string.
     *
     * @param   text            JSON string containing bridge request data
     *
     * @return  BridgeRequest instance created from JSON string, or @c null on error
     */
    @Nullable
    private BridgeRequest parseBridgeRequest(String text)
    {
        try
        {
            // Exit if text is empty.
            if (StringUtils.isBlank(text)) return null;

            // Exit if text can not be parsed as JSON object.
            JsonObject raw = JsonUtils.parseJsonObject(text, null);
            if (raw == null) return null;

            // Create BridgeRequest instance.
            return BridgeRequest.create(
                BridgeRequest.Protocol.WEBSOCKET,
                JsonUtils.getString(raw, BridgeProtocol.FIELD_ID),
                StringUtils.normalize(JsonUtils.getString(raw, BridgeProtocol.FIELD_TYPE)),
                StringUtils.normalize(JsonUtils.getString(raw, BridgeProtocol.FIELD_MODULE)),
                StringUtils.normalize(JsonUtils.getString(raw, BridgeProtocol.FIELD_ACTION)),
                JsonUtils.getElement(raw, BridgeProtocol.FIELD_DATA));
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Reads a single WebSocket frame from the input stream.
     * 
     * @param   inputStream     input stream to which
     *
     * @return  WenSocketFrame instance
     * 
     * @throws  IOException     thrown if WebSocket frame could not be read
     */
    @Nullable
    private WebSocketFrame readFrame(@NonNull InputStream inputStream) throws IOException
    {
        int first = inputStream.read();
        if (first < 0) return null;
        
        int second = inputStream.read();
        if (second < 0) throw new EOFException("Unexpected EOF while reading WebSocket frame");

        boolean fin = (first & 0x80) != 0;
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) length = ((long) readRequiredByte(inputStream) << 8) | readRequiredByte(inputStream);
        else if (length == 127)
        {
            length = 0L;
            for (int i=0; i<8; i++)
            {
                length = (length << 8) | readRequiredByte(inputStream);
            }
        }
        if (length > Integer.MAX_VALUE) throw new IOException("WebSocket frame too large");
        
        byte[] mask = null;
        if (masked)
        {
            mask = new byte[4];
            readBytes(inputStream, mask);
        }

        byte[] payload = new byte[(int) length];
        readBytes(inputStream, payload);
        if (masked)
        {
            for (int i=0; i<payload.length; i++)
            {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }

        return new WebSocketFrame(fin, opcode, payload);
    }

    /**
     * Reads a single byte from the input stream.
     *
     * @param   inputStream     input stream from which to read bytes
     *
     * @throws  IOException     thrown if byte could not be read
     */
    private int readRequiredByte(@NonNull InputStream inputStream) throws IOException
    {
        int value = inputStream.read();
        if (value < 0)
        {
            throw new EOFException("Unexpected EOF while reading WebSocket data");
        }
        return value;
    }

    /**
     * Read all bytes from the input stream.
     * 
     * @param   inputStream     input stream from which to read bytes
     * @param   buffer          buffer in which to return bytes
     * 
     * The byte array represented by the @p buffer parameter has the exact length matching the
     * size of the frame data.
     *
     * @throws  IOException     thrown if bytes could not be read
     */
    private void readBytes(InputStream inputStream, @NonNull byte[] buffer) throws IOException
    {
        int offset = 0;
        while (offset < buffer.length)
        {
            int count = inputStream.read(buffer, offset, buffer.length - offset);
            if (count < 0)
            {
                throw new EOFException("Unexpected EOF while reading WebSocket data");
            }
            offset += count;
        }
    }

    /**
     * Checks if authorized for bridge request.
     *
     * @param   wsSession       WebSocket session
     * @param   request         request for which to check authorization
     *
     * @return  @c null if authorized, JsonResponse instance if not authorized
     */
    @Nullable
    private JsonResponse validateBridgeRequestAuthorization(WebSocketSession wsSession, @NonNull BridgeRequest request)
    {
        // For command requests read-write authorization is required.
        if (BridgeProtocol.TYPE_COMMAND.equals(request.type))
        {
            if (wsSession.authorizationLevel != AuthorizationLevel.READ_WRITE)
            {
                return JsonResponse.forbidden(request, "read/write authorization required for command requests");
            }
        }

        // For all other requests at least read requests is required.
        if (wsSession.authorizationLevel == AuthorizationLevel.NONE)
        {
            JsonResponse.unauthorized(request, "not authorized");
        }
        return null;
    }

    /**
     * Checks if authorized for bridge request.
     *
     * @param   wsSession       WebSocket session
     * @param   request         request for which to check authorization
     *
     * @return  @c null if authorized, JsonResponse instance if not authorized
     */
    @Nullable
    private MediaResponse validateMediaRequestAuthorization(@NonNull WebSocketSession wsSession, BridgeRequest request)
    {
        // For all requests at least read requests is required.
        if (wsSession.authorizationLevel == AuthorizationLevel.NONE)
        {
            return MediaResponse.create(request, MediaResponse.CODE_UNAUTHORIZED, "call bridge:authorize first");
        }
        return null;
    }

    /**
     * Set the authorization level for the specified WebSocket session.
     *
     * If the specified session is the one session with read-write authorization, or the one session
     * with read-write authorization is no longer active, the authorization level for the session
     * is set to read-write. In all other cases it is set to read-only.
     *
     * @param   wsSession       WebSocket session
     *
     * @return  authorization level assigned to WebSocket session
     */
    private AuthorizationLevel authorizeSession(WebSocketSession wsSession)
    {
        synchronized (authorizationLock)
        {
            if (readWriteSession == wsSession)
            {
                wsSession.authorizationLevel = AuthorizationLevel.READ_WRITE;
                return wsSession.authorizationLevel;
            }
            if (isActive(readWriteSession) == false)
            {
                readWriteSession = wsSession;
                wsSession.authorizationLevel = AuthorizationLevel.READ_WRITE;
                return wsSession.authorizationLevel;
            }

            wsSession.authorizationLevel = AuthorizationLevel.READ_ONLY;
            return wsSession.authorizationLevel;
        }
    }

    /**
     * Clears the authorization level for a connected WebSocket session.
     *
     * This is used when a still-connected client loses authorization, for example after sending
     * a bridge:authorize request with a missing or invalid API key. If that session was the
     * one session with read-write authorization, updateSessionAuthorization() is called to select
     * a new active read-only session to become the read-write session.
     *
     * @param   wsSession       WebSocket session
     */
    private void clearSessionAuthorization(@NonNull WebSocketSession wsSession)
    {
        synchronized (authorizationLock)
        {
            boolean update = (readWriteSession == wsSession);
            wsSession.authorizationLevel = AuthorizationLevel.NONE;
            if (update)
            {
                readWriteSession = null;
                updateSessionAuthorization();
            }
        }
    }

    /**
     * Releases the authorization slot for a closing WebSocket session.
     *
     * This is used during disconnect cleanup after the session has been removed from the active
     * session set. If the closing session held read-write authorization, updateSessionAuthorization()
     * is called to promote the oldest remaining active read-only session.
     *
     * @param   session         WebSocket session being closed
     */
    private void releaseSessionAuthorization(WebSocketSession session)
    {
        synchronized (authorizationLock)
        {
            if (readWriteSession == session)
            {
                readWriteSession = null;
                updateSessionAuthorization();
            }
            session.authorizationLevel = AuthorizationLevel.NONE;
        }
    }

    /**
     * Promotes a read-only sessions to a read-write session.
     *
     * The access level first active read-only session in the session list is promoted to
     * read-write.
     */
    private void updateSessionAuthorization()
    {
        WebSocketSession candidate = null;
        for (WebSocketSession session : sessions)
        {
            if ((session.authorizationLevel != AuthorizationLevel.READ_ONLY) || (isActive(session) == false)) continue;

            if ((candidate == null) || (session.sequence < candidate.sequence))  candidate = session;
        }
        if (candidate != null)
        {
            candidate.authorizationLevel = AuthorizationLevel.READ_WRITE;
        }
        readWriteSession = candidate;
    }

    /**
     * Close all websocket sessions.
     * 
     * @param   logDisconnects  log disconnects
     */
    public void closeAllSessions(boolean logDisconnects)
    {
        for (WebSocketSession session : sessions)
        {
            session.suppressDisconnectLog = (logDisconnects == false);
            try
            {
                session.socket.close();
            }
            catch (IOException ignored) {}
        }
        sessions.clear();
        synchronized (authorizationLock)
        {
            readWriteSession = null;
        }
    }

    /**
     * Checks if the specified request is an authorization request.
     *
     * @param   request         bridge request to check
     *
     * @return  @c true if request ia authorization request
     */
    private boolean isAuthorizeRequest(@NonNull BridgeRequest request)
    {
        if (BridgeProtocol.TYPE_COMMAND.equals(request.type) == false) return false;
        return BridgeProtocol.MODULE_BRIDGE.equals(request.module)
            && BridgeProtocol.ACTION_AUTHORIZE.equals(request.action);
    }

    /**
     * Builds the JSON text frame for a WebSocket media response.
     *
     * The optional metadata describes binary payload data sent outside this JSON object, for
     * example in the next WebSocket binary frame.
     *
     * @param   response        media response to serialize
     * @param   metadata        optional metadata for out-of-band media data
     *
     * @return  JSON text frame for the media response
     */
    @NonNull
    private String buildMediaResponseJson(@NonNull MediaResponse response, JsonElement metadata)
    {
        JsonObject jsonObject = new JsonObject();
        if (response.id != null) jsonObject.addProperty(BridgeProtocol.FIELD_ID, response.id);
        jsonObject.addProperty(BridgeProtocol.FIELD_TYPE, BridgeProtocol.TYPE_RESPONSE);
        if (response.module != null) jsonObject.addProperty(BridgeProtocol.FIELD_MODULE, response.module);
        if (response.action != null) jsonObject.addProperty(BridgeProtocol.FIELD_ACTION, response.action);
        jsonObject.addProperty("code", response.code);
        if (response.message != null) jsonObject.addProperty(BridgeProtocol.FIELD_MESSAGE, response.message);
        if ((metadata != null) && (metadata.isJsonNull() == false))
        {
            jsonObject.add(BridgeProtocol.FIELD_DATA, metadata);
        }
        return jsonObject.toString();
    }

    /**
     * Creates a WebSocket accept key.
     *
     * The client accepts the key to be present in the response to a WebSocket upgrade request to
     * acknowledge the server is indeed a WebSocket server.
     *
     * @param   clientKey       client key from which to create accept key
     *
     * @return  accept key on success, @c null on failure
     */
    private String createAcceptKey(String clientKey) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest((clientKey.trim() + WEBSOCKET_GUID).getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        }
        catch (Exception e)
        {
            throw new IOException("Could not build WebSocket accept key", e);
        }
    }

    /**
     * Adds the specified WebSocket sessions details to the recipient list.
     *
     * @param   recipients      list of WebSocket recipients
     * @param   wsSession       WebSocket session
     */
    private void appendRecipient(@NonNull StringBuilder recipients, WebSocketSession wsSession)
    {
        if (recipients.length() > 0) recipients.append(",");
        recipients.append("ws.").append(wsSession.sequence).append("@").append(wsSession.clientIp);
    }

    /**
     * Checks if the specified WebSocket session is active.
     *
     * @param   wsSession       WebSocket session to check
     *
     * @return  @c true if WebSocket session is active, @c false if it is not
     */
    private boolean isActive(WebSocketSession wsSession)
    {
        return wsSession != null && sessions.contains(wsSession) && !wsSession.socket.isClosed();
    }

    /**
     * Returns the common log prefix for a WebSocket session.
     *
     * @param   wsSession       WebSocket session for which to create log prefix
     *
     * @return  string specifying log prefix
     */
    @NonNull
    private String logPrefix(@NonNull WebSocketSession wsSession)
    {
        return wsSession.clientIp + " ws." + wsSession.sequence + " ";
    }

    /** Defines authorization level granted to WebSocket session. */
    public enum AuthorizationLevel
    {
        NONE("none"),                               ///< Unauthorized
        READ_ONLY("read_only"),                     ///< Read-only authorization
        READ_WRITE("read_write");                   ///< Read-write authorization

        // Enumerator value.
        private final String value;

        /**
         * Constructs a new AuthorizationLevel instance.
         *
         * The specified authorization level is copied to a member variable.
         *
         * @param   value           string specifying authorization level
         */
        AuthorizationLevel(String value) { this.value = value; }

        /**
         * Returns the string representation of the authorization level.
         *
         * @return  string representation of authorization level
         */
        String value() { return value; }
    }

    /** Stores a single WebSocket frame. */
    private static final class WebSocketFrame
    {
        final boolean fin;                      ///< If @c true, this is final frame in message.
        final int opcode;                       ///< Capture type.
        final byte[] payload;                   ///< Capture payload - text if opcode equals 1.

        /**
         * Constructs a new WebSocketFrame instance.
         *
         * Method parameters are copied to member variables.
         *
         * @param   fin             if @c true, this is final frame in message
         * @param   opcode          frame type
         * @param   payload         frame payload - text if opcode equals 1
         */
        WebSocketFrame(boolean fin, int opcode, byte[] payload)
        {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
        }
    }

    /**
     * Specifies WebSocket session details.
     */
    private static final class WebSocketSession
    {
        final Socket socket;                    ///< Network socket.
        final String clientIp;                  ///< Client IP address.
        final long sequence;                    ///< Message counter.
        final OutputStream outputStream;        ///< Output stream assigned to socket.

        // Lock object used to synchronize sending of messages.
        final Object sendLock = new Object();

        // Session authorization level.
        volatile AuthorizationLevel authorizationLevel = AuthorizationLevel.NONE;

        // Suppresses the final disconnect log when a bridge reset intentionally closes the socket.
        volatile boolean suppressDisconnectLog;

        /**
         * Constructs a new WebSocketFrame instance.
         *
         * Method parameters are copied to member variables.
         *
         * @param   socket          network socket
         * @param   clientIp        client IP address
         * @param   sequence        message counter
         * @param   outputStream    output stream assigned to socket
         */
        WebSocketSession(Socket socket, String clientIp, long sequence, OutputStream outputStream)
        {
            this.socket = socket;
            this.clientIp = clientIp;
            this.sequence = sequence;
            this.outputStream = outputStream;
        }
    }
}