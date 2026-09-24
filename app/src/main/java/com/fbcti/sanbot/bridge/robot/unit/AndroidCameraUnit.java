/**
 * @file        AndroidCameraUnit.java
 * @brief       Implements AndroidCameraUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.content.Context;
import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.camera.AndroidCameraManager;

import java.util.Map;

/**
 * Manages Android camera operations.
 *
 * This class extends the abstract BridgeCameraUnit class to provide access to the Android camera
 * located in the robot body through the AndroidCameraManager instance. It supports both @e MJPEG
 * video streaming and image capture. The class only implements methods for the initialization and
 * shutdown of the unit, all other functionality is implemented by the base class.
 *
 * Live video capture and still image capture are handled through the same streams so these
 * operations can not be active at the same time. Since a snapshot image is just a single live video
 * frame, snapshot images can be captured while streaming live video. Snapshot images and still
 * images can be returned as either binary image data or as Base-64 encoded data.
 *
 * All features support a number of bridge request parameters that control how an image is captured
 * by the camera (frame capture parameters) or specify what further image processing is required
 * (image processing parameters). Frame capture parameters are passed to the Sanbot camera manager.
 * Image processing is handled by this camera unit and supports the following parameters
 *
 * If a parameter is not specified the default value will be used. It not in the allowed range the
 * value is clamped to the minimum or maximum allowed value.
 *
 * <b>Frame capture parameters</b>
 * @anchor ANDROID_CAPTURE_PARAMS
 * The following common frame capture parameters are supported:
 * - @c capture: String that specifies frame capture mode to use, see below.
 * - @c exposure: Integer value that specifies exposure correction. Retrieve camera features for
 *   supported values; default is 0 (no correction).
 * - @c exposure-lock: Boolean value that specifies if automatic exposure correction is disabled.
 *   This prevents the exposure to change between captures; default is @c false.
 * - @c effect: String value that specifies color effect to apply. Supported values are
 *   <tt>[none,mono]</tt>; default is @c none.
 *
 * The following additional parameters are supported for snapshot still image capture only:
 * - @c zoom: Zoom factor as percentage of default image size. Retrieve camera features for
 *   supported values; default is 100 (no zoom).
 *
 * Available capture mode are dynamically obtained from camera properties and can be retrieved by
 * sending a camera feature info request.
 *
 * <b>Image processing parameters</b>
 * The following image process parameters are supported for live video capture:
 * - @c fps: Integer that specifies frame capture rate. This is not the capture rate of the
 *   camera, but the rate at which frames are retrieved from the frame buffer. Allowed range is
 *   from 1 to the value defined by @c MAX_STREAM_FPS base class member variable; default is
 *   defined by the base class @c DEFAULT_STREAM_FPS member variable.
 * - @c quality: Integer that specified @e MJPEG encoding quality. Allowed range is specified by
 *   @c MIN_MJPEG_QUALITY to @c MAX_MJPEG_QUALITY defined in base class member variables; default is
 *   @c defined by DEFAULT_MJPEG_QUALITY.
 *
 * The following parameters are supported for snapshot and still image capture:
 * - @c format: Image encoding format. One of ["JPEG", "PNG", "WEBP"], default is specified by
 *   @c DEFAULT_IMAGE_FORMAT defined in the OrbbecCameraManager class
 * - @c quality: Integer that specified @e MJPEG encoding quality. Allowed range is specified by
 *   @c MIN_MJPEG_QUALITY to @c MAX_MJPEG_QUALITY defined in base class member variables; default is
 *   @c defined by DEFAULT_MJPEG_QUALITY.
 * - @c mirror: If @c true, mirror image. Default is @c false.
 * - @c flip: If @c true, flip (vertically mirror) image. Default is @c false.
 * = @c width: If set, the image is resized to the new width. If no @c height is specified the
 *   image is resized keeping the original aspect ratio.
 * = @c height: If set, the image is resized to the new height. If no @c width is specified the
 *   image is resized keeping the original aspect ratio.
 *
 * The Android camera returns still images as @e JPEG images. If the @c format parameters specifies
 * another image type the image must be converted which will have impact on performance. Since
 * the default format is @e JPEG, do not set the @c format parameter unless there is a good reason
 * to do so.
 *
 * @version     1.0.001
 * @date        4 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class AndroidCameraUnit extends BridgeCameraUnit
{
    /** Source label used for log messages. */
//    private static final String TAG = "AndroidCameraUnit";

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new AndroidCameraUnit instance.
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
    public AndroidCameraUnit(Context context, BridgeConfig config, BridgeEventHost eventHost)
    {
        super(context, config, eventHost, AndroidCameraManager.CAMERA_NAME);
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * The Android camera manager is copied to a member variable, and the unit status is set.
     *
     * @param   cameraManager Android camera manager
     */
    public synchronized void init(AndroidCameraManager cameraManager)
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
     * @return  Java Map instance containing status data
     */
    @NonNull
    @Override
    public synchronized Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = super.buildStatusData();
        data.put("cameraManager", (cameraManager != null) ? cameraManager.buildStatusData() : "<not available>");
        return data;
    }
}