/**
 * @file        MediaResult.java
 * @brief       Implements MediaResult class.
 */
package com.fbcti.sanbot.bridge.robot;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents bridge operation result containing binary media data.
 *
 * This class extends the abstract BridgeResult class. It adds a @c bytes that contains raw media
 * data and a @c mimeType member that specifies the mime type for the media data. It implements a
 * number of static methods that each creates an instance of this class from specified values of the
 * result code, description and/or media data. If no media data is available, @c bytes will be a
 * zero-length array, and @c mimeType will be @c null.
 *
 * @version     1.0.001
 * @date        25 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class MediaResult extends BridgeResult
{
    /** Raw media data. */
    private final byte[] bytes;

    /** Media MIME type. */
    private final String mimetype;
    
    /** Optional metadata. */
    private final Map<String, Object> metadata;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Creates a new MediaResult instance.
     *
     * The method parameters are copied to class member variables.
     *
     * @param   code            result code
     * @param   description     optional human-readable result description
     * @param   bytes           raw media data
     * @param   mimetype        media MIME type
     * @param   metadata        optional metadata
     *
     * If @p bytes is @c null a zero-length array is copied to the @c bytes member variable. The
     * constructor is declared @c private, instances of this class must be created using the static
     * factory methods.
     */
    private MediaResult(Code code, String description, byte[] bytes, String mimetype, Map<String, Object> metadata)
    {
        super(code, description);
        this.bytes = (bytes != null) ? bytes : new byte[0];
        this.mimetype = mimetype;
        this.metadata = (metadata != null) ? metadata : new LinkedHashMap<>();
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Creates a MediaResult instance from specified values.
     *
     * This factory method allows all result properties to be specified.
     *
     * @param   code            result code
     * @param   description     brief result description
     * @param   bytes           raw media data
     * @param   mimetype        media MIME type
     * @param   metadata        optional metadata
     *
     * @return  MediaResult instance
     */
    @NonNull
    public static MediaResult create(Code code, String description, byte[] bytes, String mimetype, Map<String, Object> metadata)
    {
        return new MediaResult(code, description, bytes, mimetype, metadata);
    }

    /**
     * Creates a MediaResult instance for a successful operation.
     *
     * This factory method allows the media data to be specified, but always sets the result code to
     * @c Code.SUCCESS, passes an empty brief result description to the constructor to set the
     * default description matching the result code, and sets empty metadata.
     *
     * @param   bytes           raw media data
     * @param   mimetype        media MIME type
     *
     * @return  MediaResult instance for successful operation, includes media data
     */
    @NonNull
    public static MediaResult success(byte[] bytes, String mimetype)
    {
        return new MediaResult(Code.SUCCESS, null, bytes, mimetype, null);
    }

    /**
     * Creates a MediaResult instance for a successful operation.
     *
     * This factory method allows the media data and metadata to be specified, but always sets the 
     * result code to @c Code.SUCCESS passes an empty brief result description to the constructor to 
     * set the default description matching the result code.
     *
     * @param   bytes           raw media data
     * @param   mimetype        media MIME type
     * @param   metadata        optional metadata
     *
     * @return  MediaResult instance for successful operation, includes media data
     */
    @NonNull
    public static MediaResult success(byte[] bytes, String mimetype, Map<String, Object> metadata)
    {
        return new MediaResult(Code.SUCCESS, null, bytes, mimetype, metadata);
    }

    /**
     * Creates a MediaResult instance for a failed operation.
     *
     * This factory method allows the result code and result description to be specified, but sets
     * empty media data and empty metadata.
     *
     * @param   code            result code
     * @param   description     brief result description
     *
     * @return  MediaResult instance for failed operation, does not include media data
     */
    @NonNull
    public static MediaResult failure(Code code, String description)
    {
        return new MediaResult(code, description, null, null, null);
    }

    /**
     * Creates a MediaResult instance for a failed operation.
     *
     * This factory method allows the result description to be specified, but always sets the result
     * result code to @c Code.FAILURE and sets empty media data and empty metadata.
     *
     * @param   description     brief result description
     *
     * @return  MediaResult instance for failed operation, does not include media data
     */
    @NonNull
    public static MediaResult failure(String description)
    {
        return new MediaResult(Code.FAILURE, description, null, null, null);
    }


    /**
     * Creates a MediaResult from a DataResult instance that does includes media data.
     *
     * This factory method copies the result code from the DataResult instance, constructs a new
     * description that combines the DataResult description and data, and copies the media data.
     *
     * @param   result          DataResult instance from which to create MediaResult
     * @param   bytes           raw media data
     * @param   mimetype        media MIME type
     *
     * @return  MediaResult instance
     */
    @NonNull
    public static MediaResult fromDataResult(@NonNull DataResult result, byte[] bytes, String mimetype)
    {
        String description = result.getDescription();
        if (StringUtils.isBlank(description)) description = Code.UNKNOWN.description();
        String resultData = result.getData();
        if (StringUtils.isBlank(resultData) == false)
            description = String.format("%s (%s)", description, resultData);

        return new MediaResult(Code.SUCCESS, description, bytes, mimetype, null);
    }

    /**
     * Creates a MediaResult from a DataResult instance that does not include media data.
     *
     * This factory method copies the result code from the DataResult instance, constructs a new
     * description that combines the DataResult description and data, and sets media data.
     *
     * @param   result          DataResult instance from which to create MediaResult
     *
     * @return  MediaResult instance
     */
    @NonNull
    public static MediaResult fromDataResult(DataResult result)
    {
        return fromDataResult(result, null, null);
    }

    /** Returns raw media data */
    public byte[] getBytes() { return bytes; }

    /** Returns media MIME type. */
    public String getMimeType() { return mimetype; }

    /** Returns metadata. */
    public Map<String, Object> getMetaData() { return metadata; }
}
