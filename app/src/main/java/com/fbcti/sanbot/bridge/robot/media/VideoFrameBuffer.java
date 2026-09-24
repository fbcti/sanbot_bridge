/**
 * @file        VideoFrameBuffer.java
 * @brief       Implements VideoFrameBuffer class.
 */
package com.fbcti.sanbot.bridge.robot.media;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;

import java.util.Date;
import java.util.Map;

/**
 * Buffers a single video frame received from one of the supported camera.
 *
 * This class buffers a single video frame received from one of the camera manager classes that
 * implement the BridgeCameraManager interface. The frame is stored as received, i.e. as a byte
 * array containing raw image data and specified values for the image type, width and height. If
 * either getVideoFrame() or getSnapshot() )is called to retrieve the frame for video streaming, an
 * Image instance constructed from the frame data is returned. Metadata is added by getSnapshot(),
 * but not by getVideoFrame() since returning video frames is time-critical.
 *
 * @version     1.0.001
 * @date        8 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class VideoFrameBuffer
{
    /** Byte array for storing frame data. */
    private byte[] bytes = null;

    /** Image type of stored frame. */
    Image.Type type = Image.Type.NONE;

    /** Width of stored frame. */
    private int width = 0;

    /** Height of stored frame. */
    private int height = 0;

    /** Timestamp of of stored frame. */
    private long timestamp = 0L;

    /** Image metadata. */
    private Map<String, Object> metadata = null;

    /** Number of received frames. */
    private long numReceived = 0L;

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Initializes the frame buffer.
     *
     * The data map containing camera parameters are copied to a member variable.
     *
     * @param   metaData    Java @c Map instance containing metadata to be added to video snaphots
     */
    public void init(Map<String, Object> metaData)
    {
        this.metadata = metaData;
    }

    /**
     * Copies the raw image data and image type, width and height to member variables.
     *
     * @param   bytes           bytes containing video image data
     * @param   type            video image type
     * @param   width           width of video image
     * @param   height          height of video image
     */
    public void setData(byte[] bytes, Image.Type type, int width, int height)
    {
        if ((bytes == null) || (bytes.length == 0) || (width <= 0) || (height <= 0)) return;

        long newTimestamp = System.currentTimeMillis();
        synchronized (this)
        {
            this.bytes = bytes;
            this.type = type;
            this.width = width;
            this.height = height;
            this.timestamp = newTimestamp;
            numReceived++;
        }
    }

    /**
     * Clears the buffered frame data.
     */
    public synchronized void clear()
    {
        bytes = null;
        width = 0;
        height = 0;
        timestamp = 0L;
        numReceived= 0L;
    }

    /**
     * Returns buffered frame if available.
     *
     * A new Image instance is created from the stored bitmap and the timestamp, but without further
     * metadata. This method should be called if the frame is retrieved for video streaming.
     *
     * @return  Image instance containing buffered data, or @c null if capture is not available
     */
    @Nullable
    public synchronized Image getVideoFrame()
    {
        if ((bytes == null) || (bytes.length == 0) || (width <= 0) || (height <= 0)) return null;
        return Image.create(bytes, type, width, height, timestamp, null);
    }

    @Nullable
    /**
     * Returns buffered frame if available.
     *
     * A new Image instance is created from the stored bitmap, timestamp, and metadata.
     *
     * @return  Image instance containing buffered frame, or @c null if capture is not available
     */
    public synchronized Image getImage()
    {
        if ((bytes == null) || (bytes.length == 0) || (width <= 0) || (height <= 0)) return null;
        return Image.create(bytes, type, width, height, timestamp, metadata);
    }

    /**
     * Get timestamp of buffered frame.
     *
     * @return  timestamp of buffered frame as @c long
     */
    public synchronized long getFrameTimestamp()
    {
        return timestamp;
    }

    /**
     * Builds a data map containing current buffer status data.
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    public synchronized Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = MapUtils.createMap();
        if (bytes == null) data.put("imageAvailable", false);
        else
        {
            data.put("imageAvailable", true);
            data.put("imageTimestamp", StringUtils.fromDate(new Date(timestamp)));
        }
        data.put("framesReceived", numReceived);
        return data;
    }
}