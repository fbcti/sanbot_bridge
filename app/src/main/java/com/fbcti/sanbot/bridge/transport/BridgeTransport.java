/**
 * @file        BridgeTransport.java
 * @brief       Implements BridgeTransport class.
 */
package com.fbcti.sanbot.bridge.transport;

import java.io.OutputStream;
import java.net.Socket;

/**
 * Abstract class implementing common bridge transport behaviour.
 *
 * This class implement some helper functions and  declares an interface that defines callbacks
 * contract for handling bridge transport events.
 *
 * @version     1.0.001
 * @date        18 Jun 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public abstract class BridgeTransport
{
    /** Callback host to which to publish bridge transport events. */
    protected final Host host;

    /***********************************************************************************************
     * CONSTRUCTOR
     **********************************************************************************************/

    /**
     * Constructs the base portion of a bridge transport.
     *
     * The base class constructor is called to copy the event listener to a member variable.
     *
     * @param   host            callback host to which to publish bridge transport events
     */
    BridgeTransport(Host host)
    {
        this.host = host;
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Returns the client IP address from the socket.
     *
     * @param   socket          network socket to retrieve client IP address from
     *
     * @return  client IP address or 'unknown' if IP address could not be retrieved
     */
    public static String clientIp(Socket socket)
    {
        if ((socket == null) || (socket.getInetAddress() == null)) return "unknown";
        return socket.getInetAddress().getHostAddress();
    }

    /***********************************************************************************************
     * INTERFACES
     **********************************************************************************************/

    /**
     * Defines bridge transport callback contract.
     *
     * Routing, authorization, media routing, and traffic logging stay with the host so transport
     * classes can remain focused on message parsing and response formatting.
     */
    public interface Host
    {
        /** Routes regular request to bridge service. */
        JsonResponse routeBridgeRequest(BridgeRequest request);

        /** Routes media request to bridge service. */
        MediaResponse routeMediaRequest(BridgeRequest request);

        void routeStreamRequest(OutputStream outputStream, Socket socket, BridgeRequest request);

        /** Checks if the specified API key is valid. */
        boolean isApiKeyValid(String apiKey);

        /** Writes a raw message to the traffic log. */
        void appendTrafficLog(String message);
    }
}