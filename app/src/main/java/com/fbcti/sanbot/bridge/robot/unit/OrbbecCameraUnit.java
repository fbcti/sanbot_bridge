/**
 * @file        OrbbecCameraUnit.java
 * @brief       Implements OrbbecCameraUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.content.Context;
import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.camera.OrbbecCameraManager;

import java.util.Map;

/**
 * Manages Orbbec camera operations.
 *
 * This class extends the abstract BridgeCameraUnit class to provide access to the Orbbec color,
 * depth and infrared camera sensors located in the robot head through the OrbbecCameraManager
 * instance. It supports both @e MJPEG video streaming and image capture. The class only implements
 * methods for the initialization and shutdown of the unit, all other functionality is implemented by
 * the base class.
 *
 * Live video capture and still image capture are handled through the same streams so these
 * operations can not be active at the same time. Since a snapshot image is just a single live video
 * frame, snapshot images can be captured while streaming live video. Snapshot images and still
 * images can be returned as either binary image data or as Base-64 encoded data.
 *
 * All features support a number of bridge request parameters that control how an image is captured
 * by the camera (frame capture parameters) or specify what further image processing is required
 * (image processing parameters). Frame capture parameters are passed to the Sanbot camera manager.
 * Image processing is handled by this camera unit and supports the following parameters.
 *
 * If a parameter is not specified the default value will be used. It not in the allowed range the
 * value is clamped to the minimum or maximum allowed value.
 *
 * <b>Frame capture parameters</b>
 * @anchor ORBBEC_CAPTURE_PARAMS
 * The following frame capture paramaters are supported by all sensor types:
 * - @c sensor: String value that specifies sensor type to use. One of <tt>[color, depth, ir]</tt>;
 *   default is specified by @c DEFAULT_SENSOR_TYPE member variable defined in the
 *   OrbbecCameraManager class.
 * - @c capture: String specifying frame capture mode to use, see below.
 * - @c mirror: Boolean value that specified if frames must be mirrored horizontally; default is
 *   @c false.
 *
 * The following parameters are supported by the color sensor:
 * - @c gain: Integer value that specifies factor used to correct overall brightness of image.
 * - @c exposure: Integer value that specifies exposure correction; default is 0 (no correction).
 * - @c auto-exposure: Boolean value that specifies if exposure is automatically adjusted; default
 *   is @c true. Ignored if @c exposure parameter is specified.
 * - @c auto-whitebalance: Boolean value that specifies if white balance is automatically adjeused;
 *   default is @c true.
 *
 * The following parameters are supported by the depth sensor:
 * - @c decode: String value that specifies decode mode to use, see below.
 * - @c depthrangemin: Integer value that specifies Lower bound of depth range in centimeters.
 *   Minimum value is specified by @c MIN_DEPTH_SHORT_RANGE_CM or @c MIN_DEPTH_LONG_RANGE_CM member
 *   variable defined in the OrbbecCameraManager class, depending on the frame capture mode; default
 *   is minimum value. Ignored if raw decode mode is applied.
 * - @c depthrangemax: Integer value that specifies upper bound of depth range in centimeters.
 *   Maximum value is defined by @c MIN_DEPTH_SHORT_RANGE_CM or @c MAX_DEPTH_LONG_RANGE_CM member
 *   variable defined in the OrbbecCameraManager class, depending on the frame capture mode; default
 *   is maximum value. Ignored if raw decode mode is applied.
 *
 * The following parameters are supported by the infrared sensor:
 * - @c decode: String value that specifies decode mode to use; see below.
 *
 * The @c capture parameter specifies the frame capture mode. Available capture mode are dynamically
 * obtained from camera properties and can be retrieved by sending a camera feature info request.
 * For the color sensor the frame decode mode is fully determined by the capture type (live or still
 * capture) and the selected frame capture mode. The table below lists the supported combination of
 * sensor type, pixel format (as specified by capture mode) and decode mode.
 *
 * Sensor Type | Pixel Format | Decode Modes                  |
 * ----------- | ------------ | ----------------------------- |
 * COLOR       | RGB888       | not applicable                |
 * ^           | YUYV         | not applicable                |
 * ^           | YUV422       | not applicable, see below     |
 * DEPTH       | all formats  | raw, gray, color, @<palette@> |
 * IR          | GRAY8        | raw, gray, color, @<palette@> |
 * ^           | GRAY16       | raw, gray, color, @<palette@> |
 * ^           | RGB888       | color                         |
 *
 * The decode modes are
 * - @c raw: No transformation, values are left unchanged.
 * - @c gray: Values are represented by single byte gray values.
 * - @c color: Values are represented by three-byte @c RGB color image using default palette.
 * - @c @<pallete@>: Values are represented by three-byte @c RGB color image using specified
 *   palette (@c rainbow, @c turbo, @c viridis, @c inferno, @c temp); default is @c gray.
 *
 * If only a single decode mode is supported the @c decode camera parameter is ignored.
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
 * The following parameters are supported for snapshot and still image capture:
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
 * @date        4 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class OrbbecCameraUnit extends BridgeCameraUnit
{
    /** Source label used for log messages. */
//    private static final String TAG = "OrbbecCameraUnit";

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new OrbbecCameraUnit instance.
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
    public OrbbecCameraUnit(Context context, BridgeConfig config, BridgeEventHost eventHost)
    {
        super(context, config, eventHost, OrbbecCameraManager.CAMERA_NAME);
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * The Orbbec camera manager is copied to a member variable, and the unit status is set.
     *
     * @param   cameraManager Orbbec camera manager
     */
    public synchronized void init(OrbbecCameraManager cameraManager)
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