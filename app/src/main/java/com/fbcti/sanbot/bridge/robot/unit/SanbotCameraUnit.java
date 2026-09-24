/**
 * @file        SanbotCameraUnit.java
 * @brief       Implements SanbotCameraUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.MediaResult;
import com.fbcti.sanbot.bridge.robot.camera.SanbotCameraManager;
import com.fbcti.sanbot.bridge.robot.media.Image;
import com.fbcti.sanbot.bridge.util.JsonUtils;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.ValueUtils;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages Sanbot SDK camera operations.
 *
 * This class extends the abstract BridgeCameraUnit class to provide access to the Sanbot HD camera
 * located in the robot head through the single SanbotCameraManager instance. It supports @e MJPEG
 * video streaming, image capture, and face detection and capture. The class implements methods for
 * the initialization and shutdown of the unit as well as for handling images obtained from the
 * SAnbot face detection feature. All other functionality is implemented by the base class.
 *
 * Live video capture and still image capture are handled through separate streams so both
 * operations can be active at the same time. Live video capture uses a long-lived video stream
 * that is open while one or more clients are connected, still image capture uses a short-lived
 * video stream that is closed after the snapshot has been captured. Snapshot images and still
 * images can be returned as either binary image data or as Base-64 encoded data.
 *
 * If face detection images is enabled the camera will capture an image if a human face is detected.
 * The last captured images are stored in a buffer and can be retrieved.
 *
 * All features support a number of bridge request parameters that control how an image is captured
 * by the camera (frame capture parameters) or specify what further image processing is required
 * (image processing parameters). Frame capture parameters are passed to the Sanbot camera manager.
 * Image processing is handled by this camera unit and supports the following parameters
 *
 * If a parameter is not specified the default value will be used. It not in the allowed range the
 * value is clamped to the minimum or maximum allowed value.
 *
 * <b>Common capture parameters</b>
 *  @anchor SANBOT_CAPTURE_PARAMS
 * - @c capture: String value specifying frame capture mode. Possible values <tt>[yuv,rgb]</tt>;
 *   default values are defined by @c DEFAULT_LIVE_CAPTURE_MODE and @c DEFAULT_STILL_CAPTURE_MODE
 *   member variables.
 *
 * <b>Image processing parameters</b>
 * The following image process parameters are supported for live video capture:
 * - @c fps: Integer that specifies frame capture rate. This is not the capture rate of the
 *   camera, but the rate at which frames are retrieved from the frame buffer. Allowed range is
 *   from 1 to the value defined by @c MAX_STREAM_FPS base class member variable; default is
 *   defined by the base class @c DEFAULT_STREAM_FPS memver variable.
 * - @c quality: Integer that specified @e MJPEG encoding quality. Allowed range is specified by
 *   @c MIN_MJPEG_QUALITY to @c MAX_MJPEG_QUALITY defined in base cass member variables; default is
 *   @c defined by DEFAULT_MJPEG_QUALITY.
 *
 * The following parameters are supported for snapshot. still and face image capture:
 * - @c format: Image encoding format. One of ["JPEG", "PNG", "WEBP"], default is specified by
 *   @c DEFAULT_IMAGE_FORMAT defined in the OrbbecCameraManager class
 * - @c quality: Integer that specified @e MJPEG encoding quality. Allowed range is specified by
 *   @c MIN_MJPEG_QUALITY to @c MAX_MJPEG_QUALITY defined in base cass member variables; default is
 *   @c defined by DEFAULT_MJPEG_QUALITY.
 * - @c flip: If @c true, flip (vertically mirror) image. Default is @c false.
 * = @c width: If set, the image is resized to the new width. If no @c height is specified the
 *   image is resized keeping the original aspect ratio.
 * = @c height: If set, the image is resized to the new height. If no @c width is specified the
 *   image is resized keeping the original aspect ratio.
 *
 * @version     1.0.001
 * @date        19 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class SanbotCameraUnit extends BridgeCameraUnit
{
    /** Source label used for log messages. */
//    private static final String TAG = SanbotCameraUnit.class.getSimpleName();

    /** Color of frames in face detection images. */
    private static final int FACE_FRAME_COLOR = Color.rgb(0, 255, 0);

    /** Thickness of frames in face detection images. */
    private static final int FACE_FRAME_WIDTH = 3;

    /** Sizing factor of frames in face detection imaged. */
    private static final int FACE_FRAME_PADDING_PERCENT = 10;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new SanbotCameraUnit instance.
     *
     * The base class constructor is called to set the Android application context, copy the
     * persistent bridge configuration and callback host to member variables, and set the camera
     * name.
     *
     * @param   context         Android application context
     * @param   config          persistent bridge configuration
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public SanbotCameraUnit(Context context, BridgeConfig config, BridgeEventHost eventHost)
    {
        super(context, config, eventHost, SanbotCameraManager.CAMERA_NAME);
    }

    /**
     * @name Sanbot Camera Unit Operations
     * @{
     */

    /**
     * Returns the latest cached face image as raw binary data.
     *
     * The current face capture is retrieved from the buffer if available, and processImage() is
     * called to process the image (resize, mirror, flip, compress, etc.). The processed image is
     * wrapped in a MediaResult object with binary image data.
     *
     * @param   index           last-in, first-out index of face to face capture to retrieve
     * @param   params          optional face image process parameters
     *
     * By default frames are drawn in the image around the detected faces by calling addFrames(). If
     * the @c noframes image process parameter equals @c true, the frames are not drawn. All
     * other image processing is done by the processImage() method.
     *
     * @return  MediaResult instance containing face image data
     */
    public synchronized MediaResult getFaceImage(int index, Map<String, Object> params)
    {
        // Get fixed image if in emulator mode.
        Image image;
        if (unitStatus == UnitStatus.EMULATED) image = getEmulatorImage();
        else
        {
            image = getFaceCapture(index);
            if ((image == null) || (image.hasImage() == false)) return mediaError(getError());

            // Draw frames around faces in image if required.
            boolean noframes = getBooleanParam(params, "noframes", false);
            if (noframes == false) addFaceFrames(image);
        }

        // Post-process image and return media result.
        processImage(image,  params);
        byte[] bytes = image.getBytes();
        if (bytes == null) return mediaError(DataResult.failure(image.getError()));
        return MediaResult.success(bytes, toMimeType(image.getType().name()), image.getMetaData());
    }

    /**
     * Returns the latest cached face capture as Base-64 encoded data.
     *
     * The current face capture is retrieved from the buffer if available, and processImage() is
     * called to process the image (resize, mirror, flip, compress, etc.). The processed image is
     * wrapped in a DataResult object with a JSON object containing image meta data and Base-64
     * encoded image data.
     *
     * @param   index           last-in, first-out index of face to face capture to retrieve
     * @param   params          optional face image process parameters
     *
     * By default frames are drawn in the image around the detected faces by calling addFrames(). If
     * the @c noframes image process parameter equals @c true, the frames are not drawn. All other
     * image processing is done by the processImage() method.
     *
     * @return  DataResult instance specifying operation result
     *
     * If a face image was retrieved the @c result property in the operation result object is a
     * JSON object that contains
     * @code{.json}
     * {
     *     "timestamp": face detection time stamp,
     *     "faceCount": number of detected faces,
     *     "faceData: array containing data for each detected face,
     *     "faceImage": Base-64 encoded face image data
     * }
     * @endcode
     */
    public synchronized DataResult getFaceImageBase64(int index, Map<String, Object> params)
    {
        // Return fixed image if in emulator mode.
        if (unitStatus == UnitStatus.EMULATED)
        {
            Image image = getEmulatorImage();
            processImage(image, params);
            return DataResult.success(wrapBinaryData(image.getBytes(), MapUtils.createMap("emulated", true), "imageData", "metaData"));
        }

        // Get latest face capture from buffer.
        Image image = getFaceCapture(index);
        if ((image == null) || (image.hasImage() == false)) return getError();

        // Create JSON Object containing face data and face image.
        Map<String, Object> faceData = new LinkedHashMap<>();
        Map<String, Object> data = image.getMetaData();
        faceData.put("timeStamp", image.getTimestamp());
        faceData.put("faceData", data);

        // Draw frames around faces in image if required.
        boolean noframes = getBooleanParam(params, "noframes", false);
        if (noframes == false) addFaceFrames(image);

        // Post-process image and return as media result.
        processImage(image, params);
        return DataResult.success(wrapBinaryData(image.getBytes(), faceData, "imageData", "faceData"));
    }

    /**
     * @}
     */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * The Sanbot camera manager is copied to a member variable, and the unit status is set.
     *
     * @param   cameraManager Sanbot camera manager
     */
    public synchronized void init(SanbotCameraManager cameraManager)
    {
        logStatus();

        // Copy camera manager.
        this.cameraManager = cameraManager;

        if (BuildConfig.EMULATOR_MODE)
        {
            // In emulator mode the camera is always considered to be available.
            unitStatus = UnitStatus.EMULATED;
        }
        else if ((this.cameraManager == null) || (this.cameraManager.isCameraAvailable() == false)) unitStatus = UnitStatus.NOTINITIALIZED;
        else unitStatus = UnitStatus.STARTED;

        logStatus();
    }

    /**
     * Performs unit shutdown tasks.
     *
     * The unit status is reset.
     */
    @Override
    public synchronized void shutdown()
    {
        unitStatus = UnitStatus.SHUTDOWN;
        logStatus();
    }

    /**
     * Builds a data map containing current unit status data.
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    @Override
    public synchronized Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = super.buildStatusData();
        data.put("cameraManager", (cameraManager != null) ? cameraManager.buildStatusData() : "<not available>");
        return data;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Returns the latest cached item from face capture buffer if available,
     *
     * @param   index        last in-first out index of face capture to retrieve
     *
     * @return  Image instance, or @c null if no capture is available
     */
    @Nullable
    private synchronized Image getFaceCapture(int index)
    {
        // Return error response if camera is not available.
        if ((cameraManager == null) || (cameraManager.isCameraAvailable() == false))
        {
            setError(BridgeResult.Code.NOT_READY, "image_capture_failed", "Sanbot camera manger not available");
            return null;
        }

        // Get latest capture from buffer.
        Image image = cameraManager.getFaceImage(index);
        if (image == null) setError(DataResult.notavailable("image at position " + index));
        return image;
    }

    /**
     * Add frames around faces in a face detection image.
     *
     * The list of face detection data items contains an item for each detected face, each item
     * specifying the boundaries of the rectangle containing the face. Since the original boundary
     * is tight around the face, the actual frame is made slightly larger but obviously the
     * resulting boundaries must remain within the image.
     *
     * @param   image           image to add frames to
     */
    private void addFaceFrames(@NonNull Image image)
    {
        Rect rect = image.getRect();
        if (rect == null) return;

        float padding = FACE_FRAME_PADDING_PERCENT/100f;
        int padHorizontal = (int)(0.5*padding*rect.width());
        int padVertical = (int)(0.5*padding*rect.height());
        for (Map.Entry<String, Object> dataItem : image.getMetaData().entrySet())
        {
            if ((dataItem.getKey().startsWith("face") == false) || (dataItem.getValue() instanceof JsonObject == false)) continue;

            // Compute frame rectangle.
            JsonObject pos = (JsonObject)dataItem.getValue();
            int left = ValueUtils.toBoundInteger(JsonUtils.getInteger(pos, "left", -1), -1, 0, rect.right);
            int top = ValueUtils.toBoundInteger(JsonUtils.getInteger(pos, "top", -1), -1, 0, rect.bottom);
            int right = (ValueUtils.toBoundInteger(JsonUtils.getInteger(pos, "right", -1), -1, left, rect.right));
            int bottom = (ValueUtils.toBoundInteger(JsonUtils.getInteger(pos, "bottom", -1), -1, top, rect.bottom));
            if ((left == -1) && (top == -1) && (right == -1) && (bottom == -1)) continue;

            image.addFrame(left-padHorizontal, top-padVertical, right+padHorizontal, right+padVertical);
        }
        image.drawFrames(FACE_FRAME_COLOR, FACE_FRAME_WIDTH);
    }
}