/**
 * @file        MediaResponse.java
 * @brief       Implements MediaResponse class.
 */
package com.fbcti.sanbot.bridge.transport;

import android.support.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a protocol-neutral bridge response containing optional create payload data.
 *
 * Instances of this class are sent to clients to report the outcome of @c media bridge requests.
 * The response object is immutable - all member variables are declared @c final.
 *
 * @version     1.0.001
 * @date        24 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class MediaResponse extends BridgeResponse
{
    /** Raw media data. */
    public final byte[] bytes;

    /** Media MIME type. */
    public final String mimetype;

    /** Optional metadata. */
    public final Map<String, Object> metadata;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructor for the MediaResponse class from BridgeRequest instance.
     *
     * Response properties are copied to member variables. The constructor is declared @c private,
     * instances of this class must be created using the static factory methods.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   code            HTTP-like response status code
     * @param   message         human-readable response message
     * @param   bytes           raw media data
     * @param   mimetype        media MIME type
     * @param   metadata        optional metadata
     */
    private MediaResponse(BridgeRequest request, int code, String message, byte[] bytes, String mimetype, Map<String, Object> metadata)
    {
        super(request, code, message);
        this.bytes = (bytes != null) ? bytes : new byte[0];
        this.mimetype = mimetype;
        this.metadata = (metadata != null) ? metadata : new LinkedHashMap<String, Object>();
    }

    /**
     * @name    Public Static Factory Methods
     * @{ 
     */ 

    /**
     * Creates a response object with explicitly supplied binary data.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   code            HTTP-like response status code
     * @param   message         human-readable result message
     * @param   bytes           raw media data
     * @param   mimetype        media MIME type
     * @param   metadata        optional metadata
     *
     * @return  instance of MediaResponse class
     */
    @NonNull
    public static MediaResponse create(BridgeRequest request, int code, String message, byte[] bytes, String mimetype, Map<String, Object> metadata)
    {
        return new MediaResponse(request, code, message, bytes, mimetype, metadata);
    }

    /**
     * Creates a response object without binary data.
     *
     * @param   request         bridge request to copy envelope fields from
     * @param   code            HTTP-like response status code
     * @param   message         human-readable result message
     *
     * @return  instance of MediaResponse class
     */
    @NonNull
    public static MediaResponse create(BridgeRequest request, int code, String message)
    {
        return create(request, code, message, null, null, null);
    }

    /**
     * Creates a bridge response for an unsupported request.
     *
     * The response code is fixed, the response message is specified by method parameters.
     * Structured response data is not available.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   message         human-readable response message
     *
     * @return  instance of @c JsonResponse class
     */
    @NonNull
    public static MediaResponse notFound(BridgeRequest request, String message)
    {
        return new MediaResponse(request, CODE_NOT_FOUND, message, null, null, null);
    }

    /**
     * Creates a bridge response for a request that caused an internal server error.
     *
     * The response code is fixed, the response message is specified by function parameters.
     * Structured response data is not available.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   message         human-readable response message
     *
     * @return  instance of @c JsonResponse class
     */
    @NonNull
    public static MediaResponse internalError(BridgeRequest request, String message)
    {
        return new MediaResponse(request, CODE_INTERNAL_SERVER_ERROR, message, null, null, null);
    }

    /** @} */
}