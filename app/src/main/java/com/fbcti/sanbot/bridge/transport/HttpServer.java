/**
 * @file        HttpServer.java
 * @brief       Implements HttpServer class.
 */
package com.fbcti.sanbot.bridge.transport;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.util.StringUtils;
import com.fbcti.sanbot.bridge.util.ValueUtils;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements the HTTP listener and routes requests to REST or WebSocket transport methods.
 *
 * @version     1.0.001
 * @date        21 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class HttpServer
{
    /** Maximum number of client threads. */
    private static final int MAX_CLIENT_THREADS = 16;

    /** Maximum number of pending clients. */
    private static final int MAX_PENDING_CLIENTS = 32;

    /** Timeout for stopping server thread. */
    private static final int STOP_JOIN_TIMEOUT_MS = 1500;

    /** Timeout for clients to connect to socket. */
    private static final int ACCEPT_TIMEOUT_MS = 1000;

    /** Timeout for reading HTTP request. */
    private static final int REQUEST_READ_TIMEOUT_MS = 10000;

    /** Maximum number of bytes in HTTP message header. */
    private static final int MAX_HEADER_BYTES = 64 * 1024;

    /** Raw TCP discovery probe text. */
    private static final String DISCOVERY_PROBE = "hello";

    /** Friendly robot name. */
    private final String robotName;

    /** IP server port listening for client connections. */
    private final int serverPort;

    /** IP server socket. */
    private ServerSocket serverSocket;

    /** WebSocket path. */
    private final String webSocketPath;

    /** Instance of class accepting REST transport messages. */
    private final RestTransport restTransport;

    /** Instance of class accepting WebSocket transport messages. */
    private final WebSocketTransport webSocketTransport;

    /**  Callback host to which to publish lifecycle/diagnostic events. */
    private final Host host;

    /** Server thread. */
    private Thread serverThread;

    /** Client thread executor. */
    private ExecutorService clientExecutor;

    /** Thread-safe flag specifying if server is running. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new HttpServer instance.
     *
     * Method parameters are copied to member variables.
     *
     * @param   robotName           friendly robot name
     * @param   serverPort          IP server port listening for client connections
     * @param   restTransport       instance of class accepting REST transport messages
     * @param   webSocketTransport  instance of class accepting WebSocket transport messages
     * @param   webSocketPath       websocket path
     * @param   host                callback host to which to publish lifecycle/diagnostic events
     *
     * The @p host parameter represents an implementation of the AudioManagerHost interface that allows this
     * class to publish lifecycle and diagnostics events to the owner of this HttpServer instance.
     */
    public HttpServer(String robotName, int serverPort, RestTransport restTransport, WebSocketTransport webSocketTransport, String webSocketPath, Host host)
    {
        this.robotName = robotName;
        this.serverPort = serverPort;
        this.webSocketPath = webSocketPath;
        this.restTransport = restTransport;
        this.webSocketTransport = webSocketTransport;
        this.host = host;
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Starts the HTTP server if it is not already running.
     *
     * A thread pool for client threads is created, and a server thread is created that executes the
     * runServer() method.
     *
     */
    public synchronized void start()
    {
        // If the HTTP server is already running there is nothing to do.
        if ((serverThread != null) && (serverThread.isAlive())) return;

        // Set server status.
        running.set(true);

        // Start pool for client threads.
        if ((clientExecutor == null) || (clientExecutor.isShutdown()) || (clientExecutor.isTerminated()))
        {
            clientExecutor = new ThreadPoolExecutor(MAX_CLIENT_THREADS, MAX_CLIENT_THREADS,
                0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(MAX_PENDING_CLIENTS));
        }

        // Start server thread.
        host.onServerStarting();
        final ExecutorService executor = clientExecutor;
        serverThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                runServer(executor);
            }
        }, "sanbot-http-bridge");
        serverThread.start();
    }

    /**
     * Stops the HTTP server if it is not already stopped.
     *
     * The socket is closed, the client thread pool is shut down, and the server thread is stopped.
     */
    public void stop()
    {
        Thread threadToJoin;
        synchronized (this)
        {
            // Set server status.
            running.set(false);

            // Close the socket.server
            closeServerSocket(serverSocket);
            serverSocket = null;

            // Stop the client thread pool.
            if (clientExecutor != null)
            {
                clientExecutor.shutdownNow();
                clientExecutor = null;
            }
            threadToJoin = serverThread;
        }

        // Wait until server thread is stopped.
        if ((threadToJoin != null) && (threadToJoin != Thread.currentThread()))
        {
            try
            {
                threadToJoin.join(STOP_JOIN_TIMEOUT_MS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Returns the IP server port listening for client connections.
     *
     * @return  current value of @c serverPort member variable
     */
    public synchronized int getServerPort()
    {
        return serverPort;
    }

    /**
     * Reports whether the server socket is currently listening.
     *
     * The socket is listening if it exists, is not closed, and the server thread is alive.
     *
     * @return  @c true if server socket is listening, @c false if not
     */
    public synchronized boolean isListening()
    {
        return (serverSocket != null)
            && (serverSocket.isClosed() == false)
            && (serverThread != null)
            && (serverThread.isAlive());
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Runs the HTTP server.
     *
     * The server socket is opened, and bound to the configured IP port. While running, the server
     * keeps accepting incoming connections and run the handleRequest() method to handle either the
     * REST request or update to a WebSocket connection in a separate thread. If all client threads
     * are in use, the connection is refused. If a non-recoverable exception is thrown, the socket
     * is closed and the server thread is terminated.
     *
     * @param   executor        client thread executor
     */
    private void runServer(ExecutorService executor)
    {
        int listenerPort = getServerPort();
        ServerSocket socket = null;
        try
        {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(listenerPort));
            socket.setSoTimeout(ACCEPT_TIMEOUT_MS);
            synchronized (this)
            {
                serverSocket = socket;
            }
            host.onServerListening(listenerPort, webSocketPath);

            while (running.get())
            {
                try
                {
                    // Accepts connection.
                    final Socket clientSocket = socket.accept();
                    try
                    {
                        executor.execute(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                handleRequest(clientSocket);
                            }
                        });
                    }
                    catch (RejectedExecutionException e)
                    {
                        // No more client threads available.
                        host.appendTrafficLog("IN  rejected - HTTP worker pool is full");
                        closeClientSocket(clientSocket);
                    }
                }
                catch (SocketTimeoutException ignored)
                {
                    // Keep looping so shutdown is observed quickly.
                }
                catch (IOException e)
                {
                    if (isInterruptedSystemCall(e)) continue;
                    if (running.get()) throw e;
                }
            }
        }
        catch (Throwable t)
        {
            if (running.get()) host.onServerFailed(t);
        }
        finally
        {
            // Cose server socket and stop server thread.
            closeServerSocket(socket);
            synchronized (this)
            {
                if (serverSocket == socket) serverSocket = null;
                if (Thread.currentThread() == serverThread) serverThread = null;
            }
        }
    }

    /**
     * Closes the server socket.
     *
     * This method just closes the server socket and ignores any @c IOException that is thrown.
     *
     * @param   serverSocket    socket to close
     */
    private void closeServerSocket(ServerSocket serverSocket)
    {
        try
        {
            if (serverSocket == null) return;
            serverSocket.close();
        }
        catch (IOException ignored) {}
    }

    /**
     * Closes the client socket.
     *
     * This method just closes the client socket and ignores any @c IOException that is thrown.
     *
     * @param   clientSocket    socket to close
     */
    private void closeClientSocket(Socket clientSocket)
    {
        try
        {
            if (clientSocket == null) return;
            clientSocket.close();
        }
        catch (IOException ignored) {}
    }

    /**
     * Handles a message received on the listener port.
     *
     * The input and output streams associated with the socket are retrieved and the readRequest()
     * method is called to read the received data from the input stream and parse the data to create
     * an instance of the HttpRequest class. If the data could not be parsed, a status code 400 is
     * sent back to the client.
     *
     * If the data was successfully parsed, the HttpRequest.isWebSocketUpgradeRequest() method is
     * called to check if the request is a request to upgrade to the WebSocket protocol. If this is
     * the case, the handleWebSocketUpgrade(). If the request is not an upgrade request, the REST
     * transport protocol request handler is called to handle the message as a REST request.
     *
     * @param   socket          socket on which message is received
     */
    private void handleRequest(Socket socket)
    {
        try
        {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(REQUEST_READ_TIMEOUT_MS);
            BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream());
            OutputStream outputStream = socket.getOutputStream();
            if (handleDiscoveryProbe(inputStream, outputStream, socket)) return;

            HttpRequest request = readRequest(inputStream);
            if (request == null)
            {
                return;
            }

            // Check if the request is a request to upgrade to the WebSocket protocol.
            if (request.isWebSocketUpgradeRequest())
            {
                handleWebSocketUpgradeRequest(socket, inputStream, outputStream, request);
                return;
            }

            // Call REST transport protocol request handler.
            restTransport.handleRequest(request, socket, outputStream);
        }
        catch (SocketTimeoutException e)
        {
            // Browser preconnect sockets can remain idle and never send headers.
        }
        catch (Exception e)
        {
            host.appendTrafficLog("OUT internal error " + e.getClass().getSimpleName());
        }
        finally
        {
            closeClientSocket(socket);
        }
    }

    /**
     * Handles a raw TCP discovery probe before normal HTTP parsing.
     *
     * The bytes read on the input stream are compared to the discovery probe text. If equal, the
     * discovery response containing the robot name and IP address is sent, if not the input stream
     * is reset so normal HTTP parsing can proceed.
     *
     * @param   inputStream     marked input stream from which to read probe bytes
     * @param   outputStream    output stream to which to write discovery response
     * @param   socket          socket on which probe was received
     *
     * @return  @c true if a discovery probe was handled, @c false otherwise
     *
     * @throws  IOException     thrown if data could not be read or written
     */
    private boolean handleDiscoveryProbe(@NonNull BufferedInputStream inputStream, OutputStream outputStream, Socket socket) throws IOException
    {
        inputStream.mark(DISCOVERY_PROBE.length());
        for (int i = 0; i < DISCOVERY_PROBE.length(); i++)
        {
            int value = inputStream.read();
            if ((value >= 'A') && (value <= 'Z')) value += ('a' - 'A');
            if ((value < 0) || (value != DISCOVERY_PROBE.charAt(i)))
            {
                inputStream.reset();
                return false;
            }
        }

        String ipAddress = (socket.getLocalAddress() != null) ? socket.getLocalAddress().getHostAddress() : "0.0.0.0";
        String response = robotName + '@' + ipAddress;
        outputStream.write(response.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        host.appendTrafficLog("IN  discovery probe");
        host.appendTrafficLog("OUT " + response);
        return true;
    }

    /**
     * Reads an HTTP message from input stream and convert to HTTP request object.
     *
     * The readHeaderBytes() method is called to retrieve the raw HTTP message header data, which
     * is converted to an array of strings representing message lines. The first line is the start
     * line that contains the HTTP method and full path. The next lines contain the actual HTTP
     * headers, which include the length of the message body. The body bytes are read from the
     * input stream by calling the readBodyBytes() method. An HttpRequest instance if created from
     * the HTTP message elements.
     *
     * @param   inputStream     input stream from which to read HTTP message
     *
     * @return  HttpRequest instance containing HTTP request data
     *
     * @throws  IOException thrown if data could not be read from input stream
     */
    @Nullable
    private HttpRequest readRequest(InputStream inputStream) throws IOException
    {
        // Read HTTP message data.
        byte[] headerBytes = readHeaderBytes(inputStream);
        if (headerBytes == null) return null;

        // Convert message data to array of strings.
        String headerText = new String(headerBytes, StandardCharsets.UTF_8);
        String[] lines = headerText.split("\\r?\\n");
        if ((lines.length == 0) || (StringUtils.isBlank(lines[0]))) return null;

        // Split start line to obtain the HTTP method and full request path.
        String[] parts = StringUtils.trim(lines[0]).split("\\s+");
        if (parts.length < 2) return null;

        // Retrieve HTTP method.
        String method = parts[0].trim().toUpperCase(Locale.US);
        if (("GET".equals(method) == false)
            && ("POST".equals(method) == false)
            && ("PUT".equals(method) == false)
            && ("DELETE".equals(method) == false)
            && ("OPTIONS".equals(method) == false))
            return null;

        // Parse request path to retrieve query parameters.
        String fullPath = parts[1].trim();
        if (fullPath.startsWith("/") == false) return null;
        int queryIndex = fullPath.indexOf('?');
        String path = (queryIndex >= 0) ? fullPath.substring(0, queryIndex) : fullPath;
        Map<String, String> queryParams = (queryIndex >= 0)
            ? parseQueryString(fullPath.substring(queryIndex + 1)) : Collections.emptyMap();

        // Next lines contain HTTP headers.
        Map<String, String> httpHeaders = new HashMap<>();
        int contentLength = 0;
        for (int i=1; i<lines.length; i++)
        {
            String header = lines[i];
            if (StringUtils.isBlank(header)) break;

            int index = header.indexOf(':');
            if (index <= 0) continue;

            String key = StringUtils.normalize(header.substring(0, index).trim());
            String value = header.substring(index+1).trim();
            if (key != null) httpHeaders.put(key, value);
            if ("content-length".equals(key))
            {
                Integer parsedContentLength = ValueUtils.toInteger(value);
                contentLength = (parsedContentLength != null) ? Math.max(0, parsedContentLength) : 0;
            }
        }

        // Read message body.
        String body = "";
        if (contentLength > 0)
        {
            byte[] bodyBytes = new byte[contentLength];
            readBodyBytes(inputStream, bodyBytes);
            body = new String(bodyBytes, StandardCharsets.UTF_8);
        }

        // Create HTTP request.
        return new HttpRequest(method, path, body, queryParams, httpHeaders);
    }

    /**
     * Reads raw HTTP header data from input stream.
     *
     * Since HTTP header data is separated from the HTTP body data by an empty line, the method
     * reads until it encounters either a @e 0D0A0D0A sequence (i.e. <em>\\r\\n\\r\\n</em>) or just
     * <em>\\n\\n</em>. The data is returned as an array of bytes.
     *
     * @param   inputStream     input stream from which to read HTTP header data
     *
     * @return  array of bytes containing HTTP header data
     *
     * @throws  IOException     thrown if data could not be read from input stream
     */
    @Nullable
    private byte[] readHeaderBytes(@NonNull InputStream inputStream) throws IOException
    {
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        int previous = -1;
        int recentFour = 0;
        boolean complete = false;
        int value;
        while ((value = inputStream.read()) != -1)
        {
            headerBuffer.write(value);
            recentFour = (recentFour << 8) | (value & 0xFF);
            if (recentFour == 0x0D0A0D0A || (previous == '\n' && value == '\n'))
            {
                complete = true;
                break;
            }
            if (headerBuffer.size() > MAX_HEADER_BYTES) throw new IOException("HTTP headers too large");
            previous = value;
        }

        if ((complete == false) || (headerBuffer.size() == 0)) return null;

        return headerBuffer.toByteArray();
    }

    /**
     * Reads the HTTP body data from the input stream.
     *
     * @param   inputStream     input stream from which to read HTTP body data
     * @param   buffer          buffer in which to return data
     *
     * The byte array represented by the @p buffer parameter has the exact length matching the
     * size of the body data.
     *
     * @throws  IOException     thrown if data could not be read from input stream
     */
    private void readBodyBytes(InputStream inputStream, @NonNull byte[] buffer) throws IOException
    {
        int offset = 0;
        while (offset < buffer.length)
        {
            int count = inputStream.read(buffer, offset, buffer.length - offset);
            if (count < 0)
            {
                throw new IOException("Unexpected EOF while reading request body");
            }
            offset += count;
        }
    }

    /**
     * Retrieves query parameters from query string.
     *
     * @param   query           query string from which to retrieve query parameters
     *
     * @return  instance of Java @c Map class containing query parameters
     */
    private Map<String, String> parseQueryString(String query)
    {
        // If the query line is blank there are no query parameters.
        if (StringUtils.isBlank(query)) return Collections.emptyMap();

        Map<String, String> params = new HashMap<>();
        String[] parts = query.split("&");
        for (String part : parts)
        {
            if (StringUtils.isBlank(part)) continue;

            int separator = part.indexOf('=');
            if (separator < 0) params.put(part, "");
            else params.put(part.substring(0, separator), part.substring(separator + 1));

        }
        return params;
    }

    /**
     * Writes an error response to the output stream.
     *
     * @param   outputStream    output stream to which to write response data
     * @param   statusCode      HTTP response status code
     * @param   message         HTTP response message text
     *
     * @throws  IOException     thrown if data could not be written to output stream
     */
    private void writeJsonError(OutputStream outputStream, int statusCode, String message) throws IOException
    {
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        String body = JsonResponse.create(null, statusCode, message).toString();
        writer.write("HTTP/1.1 " + statusCode + " " + HttpStatus.toText(statusCode) + "\r\n");
        writer.write("Content-Type: application/json; charset=utf-8\r\n");
        writer.write("Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n");
        writer.write("Access-Control-Allow-Origin: *\r\n");
        writer.write("Access-Control-Allow-Headers: Content-Type, X-API-Key, X-Request-Id\r\nAccess-Control-Allow-Private-Network: true\r\n");
        writer.write("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
        writer.write("Connection: close\r\n");
        writer.write("\r\n");
        writer.write(body);
        writer.flush();
    }

    /**
     * Handles a WebSocket upgrade request.
     *
     * @param   socket          socket on which message is received
     * @param   inputStream     input stream from which to read request data
     * @param   outputStream    output stream to which to write response data
     * @param   request         HTTP WebSocket upgrade request

     * @throws IOException      thrown if reading from input stream/writing to output stream fails
     */
    private void handleWebSocketUpgradeRequest(Socket socket, InputStream inputStream, OutputStream outputStream, HttpRequest request) throws IOException
    {
        host.appendTrafficLog("IN  " + summarizeRequest(request));
        if (webSocketPath.equals(request.path) == false)
        {
            writeJsonError(outputStream, JsonResponse.CODE_NOT_FOUND, "unknown WebSocket endpoint");
            host.appendTrafficLog("OUT " + request.method + " " + request.path + " 404");
            return;
        }

        socket.setSoTimeout(0);
        webSocketTransport.handleRequest(request, socket, inputStream, outputStream);
    }

    /**
     * Formats the HTTP request for logging purposes.
     *
     * @param   request         HTTP request to format
     *
     * @return  string containing formatted HTTP request
     */
    @NonNull
    private String summarizeRequest(@NonNull HttpRequest request)
    {
        StringBuilder s = new StringBuilder();
        s.append(request.method).append(" ").append(request.path);
        if (StringUtils.isBlank(request.body) == false)
        {
            String compactBody = request.body.replace('\n', ' ').replace('\r', ' ').trim();
            if (compactBody.length() > 140) compactBody = compactBody.substring(0, 140) + "...";
            s.append(" ").append(compactBody);
        }
        return s.toString();
    }

    /**
     * Checks if an @c IOException is caused by an interrupted system call.
     *
     * If an exception is caused by an interrupted system call the message text contains either
     * @c EINTR or <tt>INTERRUPTED SYSTEM CALL</tt>.
     *
     * @param   exception       exception to check
     *
     * @return  @c true if exception is caused by an interrupted system call
     */
    private boolean isInterruptedSystemCall(IOException exception)
    {
        Throwable cause = exception;
        while (cause != null)
        {
            String message = cause.getMessage();
            if (message != null)
            {
                String text = message.toUpperCase(Locale.US);
                if ((text.contains("EINTR")) || (text.contains("INTERRUPTED SYSTEM CALL")))
                {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Defines HTTP server lifecycle and diagnostic callback contract.
     */
    public interface Host
    {
        /** Called if the HTTP server is starting. */
        void onServerStarting();

        /** Called when HTTP server finished starting and is now listening for events. */
        void onServerListening(int port, String webSocketPath);

        /** Called when HTTP server fails. */
        void onServerFailed(Throwable throwable);

        /** Called to add a message to the traffic log. */
        void appendTrafficLog(String message);
    }
}