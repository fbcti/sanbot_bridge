/**
 * @file        OrbbecCameraManager.java
 * @brief       Implements OrbbecCameraManager class.
 */
package com.fbcti.sanbot.bridge.robot.camera;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Range;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.camera.interfaces.BridgeCameraManager;
import com.fbcti.sanbot.bridge.robot.media.VideoFrameBuffer;
import com.fbcti.sanbot.bridge.transport.BridgeEvent;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.CameraResolution;
import com.fbcti.sanbot.bridge.util.ColorMap;
import com.fbcti.sanbot.bridge.util.CommonUtils;
import com.fbcti.sanbot.bridge.robot.media.Image;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;

import org.openni.Device;
import org.openni.DeviceInfo;
import org.openni.OpenNI;
import org.openni.PixelFormat;
import org.openni.SensorType;
import org.openni.VideoFrameRef;
import org.openni.VideoMode;
import org.openni.VideoStream;
import org.openni.android.OpenNIHelper;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the Orbbec camera.
 *
 * This class implements the BridgeCameraManager interface to expose the functionality provided by
 * the Orbbec camera located in the robot head. It owns and manages the lifecycle of the OpenNI2
 * @c OpenNI API instance.
 *
 * The class manages a shared video/image stream that is used for live video streaming, snapshot
 * image capture, and still image capture. A snapshot image is obtained by just capturing a single
 * video frame. Because the @c OpenNI API does not provide a separate method for capturing still
 * images, a still image like a snapshot image is obtained by capturing a single frame from the
 * shared stream. The class is responsible for opening the stream, copying incoming frames into a
 * frame buffer, and closing the stream when no longer required.
 *
 * The camera provides color, depth, and infrared sensors. A stream can only be configured for a
 * single sensor at a time, so simultaneous capture using different sensors is not supported.
 *
 * A frame capture mode associated with the steam defines how frames are acquired from the camera
 * (see @ref ORBBEC_CAPTURE_PARAMS "here" for supported parameters). The capture mode to use is
 * specified by the @c capture frame capture parameter. If this parameter is not specified or not
 * valid, a default capture mode is selected. The stream also has an associated frame decode mode
 * that defines how those frames are represented in the frame buffer. For the color sensor the
 * decode mode is fully determined by the capture type (live or still capture) and the selected
 * frame capture mode. For the depth and infrared sensors it is specified by the @c decode frame
 * capture parameter. If this parameter is not specified or not valid, a default decode mode is
 * selected.
 *
 * @parblock @note @anchor YUV422_GRAY8
 * The @e GRAY8 pixel format is listed as being supported by the color sensor, but is not actually
 * producing video frames. The Orbbec camera does support two @c YUV (or @c YCrCb) pixel formats,
 * @c YUYV and @c YUV422. Because the @c YUV422 mode is just the @c YUYV mode with the luminance
 * and chroma bytes swapped (i.e. <tt>u y0 v y1</tt> instead of <tt>y0 u y1 v1</tt>) it  has no
 * added value. This allows the @c YUV422 mode to be used as a pseudo-@e GRAY8 mode - when the
 * @e GRAY8 mode is selected, the camera manager actually captures frame using the @c YUV422 pixel
 * format, and just retains the luminance byte only to produce a gray-scale image,
 * @endparblock
 *
 * @version     1.0.003
 * @date        5 Oct 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 * @since       1.0.001
 * @changelog
 * - FIX: fixed wrong image type in decodeDepthAsGray8() (1.1.003)
 * - FIX: fixed incorrect array size in decodeDepthAsRgb() (1.1.003)
 */
public final class OrbbecCameraManager implements BridgeCameraManager
{
    /** Source label used for log messages. */
    private static final String TAG = "OrbbecCameraManager";

    /** Human-readable camera name. */
    public static final String CAMERA_NAME = "orbbec";

    /** Human-readable camera alias. */
    public static final String CAMERA_ALIAS = "3d";

    /** Identifies live capture type. */
    public static final int CAPTURE_TYPE_LIVE = 1;

    /** Identifies still capture type. */
    public static final int CAPTURE_TYPE_STILL = 2;

    /** Human-readable live capture type name. */
    public static final String CAPTURE_NAME_LIVE = "live";

    /** Human-readable still capture type name. */
    public static final String CAPTURE_NAME_STILL = "still";

    /** Maximum time to wait for USB device to become available. */
    private static final int USB_OPEN_TIMEOUT_MS = 8000;

    /** Maximum number of retries while waiting for stream to become available. */
    public static final int STREAM_CHECK_MAX_RETRIES = 40;

    /** Delay in milliseconds between retries while waiting for stream to become available. */
    public static final long STREAM_CHECK_DELAY_MS = 100L;

    /** Maximum number of retries to retrieve video frame used for snapshot image. */
    public static final int NEW_FRAME_MAX_RETRIES = 30;

    /** Delay in milliseconds between attempts to retrieve video frame used for snapshot image. */
    public static final long NEW_FRAME_RETRY_DELAY_MS = 100L;

    /**
     * Minimum depth in centimeters for depth data captured with 100 micrometer resolution.
     *
     * Used if the @c depthrangemin frame capture parameter is not specified or invalid.
     */
    private static final int MIN_DEPTH_SHORT_RANGE_CM = 15;

    /**
     * Maximum depth in centimeters for depth data captured with 100 micrometer resolution.
     *
     * Used if the @c depthrangemin frame capture parameter is not specified or invalid.
     */
    private static final int MAX_DEPTH_SHORT_RANGE_CM = 150;

    /**
     * Minimum depth in centimeters for depth data captured with 1 millimeter resolution.
     *
     * Used if the @c depthrangemin frame capture parameter is not specified or invalid.
     */
    private static final int MIN_DEPTH_LONG_RANGE_CM = 30;

    /**
     * Maximum depth in centimeters for depth data captured with 1 millimeter resolution.
     *
     * Used if the @c depthrangemin frame capture parameter is not specified or invalid.
     */
    private static final int MAX_DEPTH_LONG_RANGE_CM = 500;

    /** Sensor type to use if no sensor type is specified in camera parameters. */
    private static final SensorType DEFAULT_SENSOR_TYPE = SensorType.COLOR;

    /**
     * Maximum frame width for default live capture mode.
     *
     * The frame capture mode with the largest width not exceeding this value will be selected if
     * the @c capture frame capture parameter is not specified or is invalid.
     */
    private static final int DEFAULT_LIVE_FRAME_WIDTH = 640;

    /**
     * Maximum frame width for default still capture mode.
     *
     * The frame capture mode with the largest width not exceeding this value will be selected if
     * the @c capture frame capture parameter is not specified or is invalid.
     */
    private static final int DEFAULT_STILL_FRAME_WIDTH = 1028;

    /**
     * Default decode mode for depth sensor data.
     *
     * This decode mode is selected to decode depth sensor data is the @c decode mode is not
     * specified or is invalid.
     */
    private static final int DEFAULT_DEPTH_DECODE_MODE = DecodeMode.DECODE_GRAY;

    /**
     * Default decode mode for infrared sensor data.
     *
     * This decode mode is selected to decode infrared sensor data is the @c decode mode is not
     * specified or is invalid.
     */
    private static final int DEFAULT_IR_DECODE_MODE = DecodeMode.DECODE_COLOR | ColorMap.PALETTE_TEMPERATURE << 4;

    /** Android application context. */
    private Context context = null;

    /** OpenNI2 helper providing access permissions management and device initialization. */
    private OpenNIHelper openNIHelper = null;

    /** Orbbec USB device. */
    private UsbDevice usbDevice = null;

    /** OpenNI2 @c Device instance representing Orbbec camera. */
    private Device camera = null;

    /** OpenNI2 @c DeviceInfo instance containing Orbbec camera information. */
    private DeviceInfo cameraInfo = null;

    private VideoStream videoStream = null;

    /** Shared stream used for video streaming, snapshot capture, and still image capture. */
    private final Stream sharedStream = new Stream();

    /** List of supported frame capture modes. */
    private final List<CaptureMode> captureModes = new ArrayList<>();

    /** Listener for frame events from OpenNi2 @c VideoStream API. */
    private FrameListener frameListener;

    /** Callback host to which to publish bridge unit events. */
    private final BridgeEventHost eventHost;

    /** Flag specifying if OpenNI API is initialized. */
    private boolean openNIInitialized = false;

    /** Current camera status. */
    private volatile CameraStatus cameraStatus = CameraStatus.NOT_INITIALIZED;

    /** Last reported error. */
    private DataResult error = null;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new OrbbecCameraManager instance.
     *
     * The base class constructor is called to copy the event host to a member variable.
     *
     * @param   eventHost       callback host to which to forward camera manager events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward camera manager events to be published by the BridgeService
     * instance.
     */
    public OrbbecCameraManager(BridgeEventHost eventHost)
    {
        this.eventHost = eventHost;
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Initializes the camera manager.
     *
     * The shared stream is closed if currently open. If the camera has already been initialized,
     * the existing OpenNI2 @c Device API instance representing the camera is released before
     * initCamera() is called to create and configure a new one. After initialization completes, the
     * shared stream state is reset to reflect the resulting camera status.
     *
     * @param   context         Android application context
     * @param   args            additional initialization arguments
     *
     * This implementation requires no additional initialization arguments
     */
    public synchronized void init(Context context, Object... args)
    {
        BridgeLog.info(TAG, "Initialize Orbbec camera manager");
        this.context = context;
        error = null;

        // Close active camera streams if open. Release camera if already initialized.
        if (sharedStream.isOpen) closeStream(sharedStream);
        if ((cameraStatus == CameraStatus.ERROR) || (cameraStatus == CameraStatus.AVAILABLE)) releaseCamera();

        // Initialize camera.
        cameraStatus = ((BuildConfig.EMULATOR_MODE) || (initCamera())) ? CameraStatus.AVAILABLE : CameraStatus.ERROR;

        // Initialize streams.
        String status = (cameraStatus == CameraStatus.AVAILABLE) ? ((BuildConfig.EMULATOR_MODE) ? "emulated_ready" : "ready") : "unavailable";
        sharedStream.reset(status);
    }

    /**
     * Shuts down the camera manager.
     *
     * The managed OpenNI2 @c Device API instance representing the camera is released and the shared
     * stream is closed.
     */
    public synchronized void shutdown()
    {
        BridgeLog.info(TAG, "Shut down Orbbec camera manager");

        releaseCamera();
        closeStream(sharedStream);
    }

    /**
     * Resets the camera manager.
     *
     * The managed Android @c Camera API instance is released and the shared stream is closed, and
     * the camera is re-initialized.
     */
    public synchronized DataResult reset()
    {
        shutdown();
        init(context);
        return (error == null) ? DataResult.success() : error;
    }

    /**
     * Starts streaming live video.
     *
     * This method must be called when a client requests live video streaming. If the camera is
     * currently in use for still image capture, waitForStream() is called to pause the current
     * thread until the camera becomes available or the configured timeout expires. Once the camera
     * is available, the requested or default frame capture mode and frame decode mode are selected,
     * and ensureStreamOpen() is called to open or reuse the already open shared stream. If
     * successful, the shared stream handle is returned.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  shared stream handle on success, or -1 if the stream could not be opened
     */
    public synchronized long openVideoStream(Map<String, Object> captureParams)
    {
        // If camera is not available for streaming pause thread and try again.
        if ((cameraStatus == CameraStatus.STILLCAPTURE) && (waitForStream(sharedStream) == false))
        {
            setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure", "camera is not available for video streaming");
            return -1;
        }

        // Select frame capture mode specified by frame capture parameters.
        CaptureMode captureMode = selectCaptureMode(CAPTURE_TYPE_LIVE, captureParams);
        if (captureMode == null) return -1;

        // Select frame decode mode specified by frame capture parameters.
        DecodeMode decodeMode = selectDecodeMode(captureMode, captureParams);
        if (decodeMode == null) return -1;

        // Ensure shared stream is open.
        return ensureStreamOpen(sharedStream, captureParams, captureMode, decodeMode);
    }

    /**
     * Retrieves the most recently buffered frame from the video frame buffer
     *
     * The frame is retrieved from the video frame buffer associated with the shared stream.
     *
     * @return  latest video frame as @e MJPEG image, or @c null if frame is not available
     */
    public synchronized Image getVideoFrame()
    {
        return sharedStream.frameBuffer.getVideoFrame();
    }

    /**
     * Stops streaming live video.
     *
     * This method must be called to stop live video streaming. It calls closeStreamIfIdle() to
     * release the shared stream if no longer required.
     */
    public synchronized void releaseVideoStream()
    {
        closeStreamIfIdle(sharedStream);
    }

    /**
     * Captures a snapshot image from the shared stream.
     *
     * If the camera is currently in use for still image capture, waitForStream() is called to pause
     * the current thread until the camera becomes available or the configured timeout expires. Once
     * the camera is available, the requested or default frame capture mode and frame decode mode
     * are selected, and ensureStreamOpen() is called to open the shared stream, and if successful
     * getSnapshotImageFromBuffer() is called to wait for a frame newer than the latest buffered
     * frame to appear in the frame buffer.
     *
     * The shared stream is closed if no longer required.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  Image instance representing captured snapshot image, or @c null on failure
     */
    @Nullable
    public synchronized Image getSnapshotImage(Map<String, Object> captureParams)
    {
        // If camera is not available for streaming pause thread and try again.
        if ((cameraStatus != CameraStatus.AVAILABLE) && (cameraStatus != CameraStatus.LIVECAPTURE)
            && (waitForStream(sharedStream) == false))
        {
            setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure", "camera is not available for capturing snapshot image");
            return null;
        }

        // Select frame capture mode specified by frame capture parameters.
        CaptureMode captureMode = selectCaptureMode(CAPTURE_TYPE_LIVE, captureParams);
        if (captureMode == null) return null;

        // Select frame decode mode specified by frame capture parameters.
        DecodeMode decodeMode = selectDecodeMode(captureMode, captureParams);
        if (decodeMode == null) return null;

        // Retrieve timestamp of last video frame stored in frame buffer.
        long timestamp = sharedStream.frameBuffer.getFrameTimestamp();

        // Ensure shared stream is open. Return error response on failure.
        if (ensureStreamOpen(sharedStream, captureParams, captureMode, decodeMode) < 0) return null;

        try
        {
            // Wait for a snapshot frame newer than the one in cache.
            return getSnapshotImageFromBuffer(sharedStream.frameBuffer, timestamp);
        }
        finally
        {
            // Close shared stream if no longer required.
            closeStreamIfIdle(sharedStream);
        }
    }

    /**
     * Captures a still image as a snapshot from the shared stream.
     *
     * If the camera is currently in use for live video streaming or capturing a snapshot image,
     * waitForStream() is called to pause the current thread until the camera becomes available or
     * the configured timeout expires. Once the camera is available, the requested or default frame
     * capture mode and frame decode mode are selected, and ensureStreamOpen() is called to open the
     * shared stream, and if successful, getSnapshotImageFromBuffer() is called to wait for a frame
     * newer than the latest buffered frame to appear in the frame.
     *
     * The shared stream is closed if no longer required.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  Image instance representing captured still image, or @c null on failure
     */
    @Nullable
    public synchronized Image getStillImage(Map<String, Object> captureParams)
    {
        // Wait for the current shared-stream operation to complete.
        if ((cameraStatus != CameraStatus.AVAILABLE) && (waitForStream(sharedStream) == false))
        {
            setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure", "camera is not available for capturing still image");
            return null;
        }

        // Select frame capture mode specified by frame capture parameters.
        CaptureMode captureMode = selectCaptureMode(CAPTURE_TYPE_STILL, captureParams);
        if (captureMode == null) return null;

        // Select frame decode mode specified by frame capture parameters.
        DecodeMode decodeMode = selectDecodeMode(captureMode, captureParams);
        if (decodeMode == null) return null;

        // Retrieve timestamp of last video frame stored in frame buffer.
        long timestamp = sharedStream.frameBuffer.getFrameTimestamp();

        // Ensure shared stream is open. Return error response on failure.
        if (ensureStreamOpen(sharedStream, captureParams, captureMode, decodeMode) < 0) return null;

        try
        {
            // Wait for a frame newer than the one in cache.
            return getSnapshotImageFromBuffer(sharedStream.frameBuffer, timestamp);
        }
        finally
        {
            // Close shared stream if no longer required.
            closeStreamIfIdle(sharedStream);
        }
    }

    /**
     * Retrieves the most recently captured face image.
     *
     * This camera manager does not support face image capture and always returns @c null.
     *
     * @param   lifo        last in-first out index of face capture to retrieve
     *
     * @return  latest face image from the buffer, or @c null if face capture is not available
     */
    @Nullable
    public synchronized Image getFaceImage(int lifo)
    {
        setError(BridgeResult.Code.NOT_SUPPORTED, "operation_failed", "camera does not support this operation");
        return null;
    }

    /**
     * Builds a data map containing camera manager status data.
     *
     * The returned map includes the camera name and status, the selected camera id, and the shared
     * stream status data.
     *
     * @return  Java @c Map instance containing camera manager status data
     */
    @NonNull
    public Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = MapUtils.createMap("camera", CAMERA_NAME, "status", cameraStatus.name().toLowerCase());
        if ((cameraInfo != null) && (camera != null))
        {
            Map<String, Object> infoData = MapUtils.createMap();
            infoData.put("serial", camera.getSerialNumber());
            infoData.put("firmware", camera.getFirmwareVersion());
            infoData.put("name", cameraInfo.getName());
            infoData.put("vendor", cameraInfo.getVendor());
            infoData.put("vendorId", cameraInfo.getUsbVendorId());
            infoData.put("productId", cameraInfo.getUsbProductId());
            infoData.put("uri", cameraInfo.getUri());
            data.put("cameraInfo", infoData);
        }
        data.put("streamStatus", getStreamStatus(sharedStream));
        return data;
    }

    /**
     * Builds a data map containing supported camera feature data.
     *
     * The returned map contains the supported frame capture modes and any capture parameters that
     * can be queried from the active Orbbec @c Camera instance, such as exposure range, supported
     * effects, and supported zoom values.
     *
     * @return  Java @c Map instance containing camera feature data
     */
    @NonNull
    public Map<String, Object> buildFeatureData()
    {
        Map<String, Object> data = MapUtils.createMap("cameraName", CAMERA_NAME, "cameraAlias", CAMERA_ALIAS);
        List<String> sensorTypes = new ArrayList<>();
        for (SensorType sensorType : SensorType.values())
        {
            if (camera.hasSensor(sensorType)) sensorTypes.add(sensorType.name().toLowerCase());
        }
        data.put("sensors", sensorTypes);
        data.putAll(getCaptureModes());
        data.put("mirror", Arrays.asList(true, false));
        data.put("gain", MapUtils.createMap("min", 0, "max", 50));
        data.put("exposure", MapUtils.createMap("min", 0, "max", 100));
        data.put("auto-exposure", Arrays.asList(true, false));
        data.put("auto-whitebalance", Arrays.asList(true, false));
        data.put("depthrangemin", MapUtils.createMap("min", MIN_DEPTH_SHORT_RANGE_CM));
        data.put("depthrangemax", MapUtils.createMap("max", MAX_DEPTH_SHORT_RANGE_CM));
        return data;
    }

    /**
     * Returns whether the camera is currently available for a new capture request.
     *
     * The camera is considered available if the status is either CameraStatus.AVAILABLE,
     * CameraStatus.LIVECAPTURE or CameraStatus.STILLCAPTURE.
     *
     * @return  @c true if the camera is available, or @c false otherwise
     */
    public synchronized boolean isCameraAvailable()
    {
        return ((cameraStatus == CameraStatus.AVAILABLE)
            || (cameraStatus == CameraStatus.LIVECAPTURE)
            || (cameraStatus == CameraStatus.STILLCAPTURE));
    }

    /**
     * Returns the most recently reported error.
     *
     * @return  @c DataResult instance containing error, or @c null if no error has been reported
     */
    public DataResult getError() { return error; }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Initializes the Orbbec camera.
     *
     * The OpenNI2 API is initialized if not already initialized, The list of supported USB devices
     * is retrieved, and the camera device is selected. If a camera device has already been opened
     * and that camera device is not the same as the selected camera device, the camera device is
     * closed. A new camera device is opened by calling openCameraDevice(), and the list of frame
     * capture modes supported by the camera is retrieved.
     *
     * @return  @c true on success, @c false on failure
     */
    private boolean initCamera()
    {
        BridgeLog.debug(TAG, "Initialize camera");

        // Initialize OpenNI runtime if not already open.
        if ((openNIInitialized == false) && ((openNIInitialized = initOpenNI(context)) == false)) return false;

        // Select the device. If the selected device is the current camera device it does not need
        // to be opened.
        DeviceInfo deviceInfo = getDeviceInfo(OpenNI.enumerateDevices(), usbDevice);
        if (deviceInfo == null)
        {
            setError(BridgeResult.Code.FAILURE, "camera_init_failed", "failed to retrieve USB device info");
            return false;
        }
        String deviceUri = StringUtils.safeString(deviceInfo.getUri());
        String cameraUri = (cameraInfo != null) ? StringUtils.safeString(cameraInfo.getUri()) : "";
        if ((camera != null) && (deviceUri.equals(cameraUri)))
        {
            BridgeLog.debug(TAG, "Orbbec camera already initialized");
            return true;
        }

        // Close current camera if open, and open selected camera.
        if (camera != null) camera.close();
        camera = openCameraDevice(deviceInfo);
        if (camera == null)
        {
            setError(BridgeResult.Code.FAILURE, "camera_init_failed", "failed to initialize Orbbec camera");
            return false;
        }

        BridgeLog.debug(TAG, "Orbbec camera successfully initialized");
        cameraInfo = deviceInfo;

        // Initialize list of supported frame capture modes.
        initCaptureModes(camera, cameraInfo);
        return true;
    }

    /**
     * Initialize the OpenNI2 API.
     *
     * The OpenNI2 helper is initialized, access to the USB device is requested, and the OpenNI2
     * runtime is initialized.
     *
     * @param   context         Android application context
     *
     * @return  @c true on success, @c false on failure
     */
    private boolean initOpenNI(@NonNull Context context)
    {
        BridgeLog.debug(TAG, "Initialize OpenNI2 runtime");

        // Initialize the OpenNI helper.
        openNIHelper = new OpenNIHelper(context.getApplicationContext());

        // Request access to USB device. Return error response on failure.
        if (openUsbDevice() == false) return false;

        // Initialize OpenNI runtime.
        try
        {
            OpenNI.setLogAndroidOutput(true);
            OpenNI.setLogMinSeverity((BuildConfig.DEBUG) ? 0 : 3);
            OpenNI.initialize();
            return true;
        }
        catch (Exception e)
        {
            setError(BridgeResult.Code.FAILURE, "openni2_init_failed", "failed to initialize OpenNI2 - " + e.getMessage());
            return false;
        }
    }

    /**
     * Requests access to the Orbbec USB device.
     *
     * The method submits a request to access the Orbbec USB device, and waits for the response or
     * until the time defined by @c USB_OPEN_TIMEOUT_MS is exceeded. If successful, the device is
     * copied to the @c usbDevice class member. An error response is returned if the USB device is
     * not found or is not accessible,
     *
     * @return  @c true if Orbbec device is found and accessible, @c false on failure
     */
    private boolean openUsbDevice()
    {
        UsbOpenResult usbOpenResult = new UsbOpenResult();
        CountDownLatch usbReady = new CountDownLatch(1);
        openNIHelper.requestDeviceOpen(new DeviceOpenListener(usbOpenResult, usbReady));

        try
        {
            if (usbReady.await(USB_OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS) == false)
            {
                // Timeout waiting for device.
                setError(BridgeResult.Code.FAILURE, "openni2_init_failed", "timeout waiting for USB device");
                return false;
            }
        }
        catch (InterruptedException e)
        {
            setError(BridgeResult.Code.FAILURE, "openni2_init_failed", e.getMessage());
            return false;
        }

        // Return error if device is not found or not accessible.
        if (usbOpenResult.device == null)
        {
            setError(BridgeResult.Code.FAILURE, "openni2_init_failed", usbOpenResult.error);
            return false;
        }

        // Copy device to class member.
        usbDevice = usbOpenResult.device;
        return true;
    }

    /**
     * Returns USB device information.
     *
     * The information on the device with the product id matching the product id of the specified
     * USB device is returned. If the device list is empty a @c null value is returned. If no
     * device with a matching product id is found information on the last device in the list is
     * returned.
     *
     * @param   deviceInfoList  list containing information on available devices
     * @param   usbDevice       USB device
     *
     * @return  OpenNI2 @c DeviceInfo instance, or @c null if no devices are available
     */
    private DeviceInfo getDeviceInfo(List<DeviceInfo> deviceInfoList, UsbDevice usbDevice)
    {
        if ((deviceInfoList == null) || deviceInfoList.isEmpty()) return null;
        if (usbDevice != null)
        {
            for (DeviceInfo deviceInfo : deviceInfoList)
            {
                if ((deviceInfo != null) && (deviceInfo.getUsbProductId() == usbDevice.getProductId())) return deviceInfo;
            }
        }
        return deviceInfoList.get(deviceInfoList.size()-1);
    }

    /**
     * Opens the camera device.
     *
     * The static @c init() method implemented by the OpenNI2 @c Device instance representing the
     * camera is first called without an argument to open the first available back-facing camera. If
     * that fails, the camera specified in tn the @c DeviceInfo instance is opened instead.
     *
     * @param   cameraInfo      OpenNI2 @c DeviceInfo instance containing camera information
     *
     * @return  OpenNI2 @c Device instance representing camera, or @c null on failure
     */
    @Nullable
    private Device openCameraDevice(DeviceInfo cameraInfo)
    {
        try
        {
            return Device.open();
        }
        catch (Exception ignored) {}
        try
        {
            String uri = (cameraInfo != null) ? cameraInfo.getUri() : null;
            if (StringUtils.isBlank(uri)) return null;
            return Device.open(uri);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Initializes the list of supported frame capture modes.
     *
     * The @c captureModes list is build from the sensor types and video modes supported by the
     * OpenNI2 @c Camera instance representing the camera. Each capture mode combines a sensor type,
     * a capture type (live or still capture), and a video format that specifies the frame size,
     * pixel format and frame rate.
     *
     * Since still images are just snapshot images captured from a second stream, the still capture
     * modes are identical to the live capture mode, so each mode is just added twice to the list,
     * once with the @c CAPTURE_TYPE_LIVE and once with @c CAPTURE_TYPE_STILL capture type.
     *
     * Video modes using he @e GRAY8 pixel format are not included in the list of color sensor
     * capture modes, see @ref YUV422_GRAY8 "here".
     *
     * @param   camera          OpenNI2 device representing Orbbec camera
     * @param   cameraInfo      Orbbec camera information
     */
    private void initCaptureModes(Device camera, DeviceInfo cameraInfo)
    {
        if ((cameraInfo == null) || (camera == null)) return;

        captureModes.clear();
        for (SensorType sensorType : SensorType.values())
        {
            if (camera.hasSensor(sensorType) == false) continue;

            for (VideoMode videoMode : camera.getSensorInfo(sensorType).getSupportedVideoModes())
            {
                // Exclude GRAY8 pixel format for COLOR sensor.
                if ((sensorType == SensorType.COLOR) && (videoMode.getPixelFormat() == PixelFormat.GRAY8)) continue;

                // Exclude the unsupported 640x400 resolution for the IR sensor.
                if ((sensorType == SensorType.IR) && (videoMode.getResolutionX() == 640)
                    && (videoMode.getResolutionY() == 400)) continue;

                // Add same capture mode for both capture type.
                CaptureMode liveCaptureMode = CaptureMode.create(sensorType, CAPTURE_TYPE_LIVE, videoMode);
                if (liveCaptureMode != null) captureModes.add(liveCaptureMode);
                CaptureMode stillCaptureMode = CaptureMode.create(sensorType, CAPTURE_TYPE_STILL, videoMode);
                if (stillCaptureMode != null) captureModes.add(stillCaptureMode);
            }
        }
    }

    /**
     * Selects the requested or default frame capture mode.
     *
     * The sensor type is selected. If successful, the @c capture parameter that specifies the
     * requested frame capture mode is retrieved from the frame capture parameters. If the list of
     * capture modes contains an item with matching mode id, selected sensor type and specified
     * capture type that capture mode is returned. If no mode id is specified or the specified
     * capture mode is not supported, defaultCaptureMode() is called to get the default capture mode
     * for the selected sensor type and capture type.
     *
     * @param   captureType     frame capture type for which to select frame capture mode
     * @param   captureParams   optional frame capture parameters
     *
     * @return  selected frame capture mode, or @c null if no frame capture mode is available
     */
    @Nullable
    private CaptureMode selectCaptureMode(int captureType, Map<String, Object> captureParams)
    {
        // Select sensor specified in parameters.
        SensorType sensorType = selectSensor(captureParams);
        String sensorName = (sensorType != null) ? sensorType.name().toLowerCase() : null;
        if (sensorName == null) return null;

        // Get frame capture name matching frame capture type.
        String captureName = (captureType == CAPTURE_TYPE_LIVE) ? CAPTURE_NAME_LIVE
            : ((captureType == CAPTURE_TYPE_STILL) ? CAPTURE_NAME_STILL : null);
        if (captureName == null)
        {
            setError(BridgeResult.Code.NOT_SUPPORTED, "stream_open_failure", "unsupported capture type " + captureType);
            return null;
        }

        // Retrieve mode id from frame capture parameters.
        String modeId = MapUtils.getString(captureParams, "capture", null);
        if (modeId != null)
        {
            // Find frame capture mode with matching mode id sensor type, and capture type.
            modeId = modeId.trim();
            for (CaptureMode captureMode : captureModes)
            {
                if ((captureMode.captureType == captureType) && (captureMode.sensorType == sensorType) && (modeId.equalsIgnoreCase(captureMode.id))) return captureMode;
            }
            setError(BridgeResult.Code.FAILURE, "stream_open_failure",
                captureName + " capture mode " + modeId + " not supported for " + sensorName + " sensor - default capture mode will be used");
            return null;
        }
        else BridgeLog.warning(TAG, "Requested capture mode not specified - default capture mode will be used");

        // Return default frame capture mode.
        return defaultCaptureMode(sensorType, captureType, captureName);
    }

    /**
     * Selects the requested or default sensor type.
     *
     * The requested sensor type is specified by the @c sensor capture parameter. If valid, and the
     * the matching sensor type is supported by the camera, the @c SensorType enumerator value is
     * returned. If the sensor type is not specified or not valid the default sensor is returned.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  OpenNI2 @c SensorType enumerator value, or @c null if sensor type is not supported
     */
    @Nullable
    private SensorType selectSensor(Map<String, Object> captureParams)
    {
        SensorType sensorType;
        String sensorName = StringUtils.normalize(MapUtils.getString(captureParams, "sensor", null));
        if (sensorName == null) sensorName = "<blank>";
        switch (sensorName)
        {
            case "color":
                sensorType = SensorType.COLOR;
                break;
            case "ir":
                sensorType = SensorType.IR;
                break;
            case "depth":
                sensorType = SensorType.DEPTH;
                break;
            case "<blank>":
                sensorType = DEFAULT_SENSOR_TYPE;
                BridgeLog.info(TAG, "Requested sensor type not specified - " + sensorType.name().toLowerCase() + " sensor will be used");
                break;
            default:
                sensorType = DEFAULT_SENSOR_TYPE;
                BridgeLog.warning(TAG, "Invalid sensor type " + sensorName + " - " + sensorType.name().toLowerCase() + " sensor will be used");
        }

        // Return sensor type if supported.
        if (camera.hasSensor(sensorType)) return sensorType;

        // Sensor type not supported.
        setError(BridgeResult.Code.NOT_SUPPORTED, "stream_open_failed",
            "sensor " + sensorType.name().toLowerCase() + " not supported by " + cameraInfo.getName() + " camera");
        return null;
    }

    /**
     * Selects the default frame capture mode for the specified sensor type and frame capture type.
     *
     * The highest-scoring frame capture mode with a frame width not exceeding the configured
     * maximum value that is supported is returned.
     *
     * @param   sensorType      sensor type for which to return default frame capture mode
     * @param   captureType     frame capture type for which to return default frame capture mode
     * @param   captureName     name of frame capture type (for logging purposes only)
     *
     * @return  default frame capture mode, or @c null if no capture mode is available
     */
    @Nullable
    private CaptureMode defaultCaptureMode(SensorType sensorType, int captureType, String captureName)
    {
        // Determine maximum allowed frame width.
        Integer maxWidth = (captureType == CAPTURE_TYPE_LIVE) ? (Integer)DEFAULT_LIVE_FRAME_WIDTH
            : ((captureType == CAPTURE_TYPE_STILL) ? (Integer)DEFAULT_STILL_FRAME_WIDTH : null);
        if (maxWidth == null)
        {
            setError(BridgeResult.Code.NOT_SUPPORTED, "stream_open_failure", "unsupported capture type " + captureType);
            return null;
        }

        // Returns highest-scoring compatible capture mode.
        CaptureMode selectedCaptureMode = null;
        for (CaptureMode captureMode : captureModes)
        {
            int width = captureMode.videoMode.getResolutionX();
            int score = (selectedCaptureMode != null) ? selectedCaptureMode.score : 0;
            if ((captureMode.sensorType == sensorType) && (width <= maxWidth) && (captureMode.score > score))
                selectedCaptureMode = captureMode;
        }

        if (selectedCaptureMode == null) setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure",
            "no default " + captureName + " capture mode available for " + sensorType.name().toLowerCase() + " sensor");
        return selectedCaptureMode;
    }

    /**
     * Selects requested or default frame decode mode.
     *
     * The @c decode parameter is not actually supported by this camera manager, the frame decode
     * mode is fully determined by the frame capture type (live or still capture) and the selected
     * frame capture mode.
     *
     * @param   captureMode     selected frame capture mode
     * @param   captureParams   optional frame capture parameters
     *
     * @return  selected frame decode mode, or @c null if no decode mode is available
     */
    @Nullable
    private DecodeMode selectDecodeMode(CaptureMode captureMode, Map<String, Object> captureParams)
    {
        DecodeMode decodeMode = DecodeMode.create(captureMode, captureParams);
        if (decodeMode == null) setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failed",
            "no decode available for " + captureMode.sensorType.name().toLowerCase() + " sensor");
        return decodeMode;
    }

    /**
     * Waits until the camera becomes available.
     *
     * If the camera is not currently available, the current thread waits for the specified delay.
     * After each wait interval the camera status is checked again. The wait is repeated until the
     * camera becomes available or the maximum number of retries is exceeded.
     *
     * @param   stream stream for which to check availability (always shared stream)
     *
     * @return @return  @c true if the stream is available, or @c false otherwise
     */
    private boolean waitForStream(Stream stream)
    {
        // The camera is ready when no capture operation owns the closed shared stream.
        if ((cameraStatus == CameraStatus.AVAILABLE) && (stream.isOpen == false)) return true;

        // Repeatedly wait for the stream to release. wait() releases the manager monitor so other
        // synchronized methods can continue and release the stream.
        for (int i = 0; i< STREAM_CHECK_MAX_RETRIES; i++)
        {
            try { wait(OrbbecCameraManager.STREAM_CHECK_DELAY_MS); }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return false;
            }
            if ((cameraStatus == CameraStatus.AVAILABLE) && (stream.isOpen == false)) return true;
        }
        return false;
    }

    /**
     * Ensures the specified stream is open with compatible capture settings.
     *
     * The requested or default capture mode and decode mode are selected first. If the stream is
     * closed, it is opened immediately. If it is already open and actively in use, the existing
     * stream can only be reused when both the capture mode and decode mode are compatible with the
     * requested settings. If the stream is open but not currently in use, it is closed and reopened
     * with the requested settings. If successful, the active video client count is incremented and
     * the stream handle is returned.
     *
     * @param   stream          Stream instance representing the shared stream
     * @param   captureParams   optional frame capture parameters
     * @param   captureMode     frame capture mode
     * @param   decodeMode      frame decode mode
     *
     * @return  stream handle if stream was successfully opened, or -1 on failure
     */
    private synchronized long ensureStreamOpen(Stream stream, Map<String, Object> captureParams, CaptureMode captureMode, DecodeMode decodeMode)
    {
        // Return error result if context or camera is not available.
        if (isCameraAvailable() == false)
        {
            setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failed", "camera not available");
            return -1;
        }

        long handle;
        if (stream.isOpen == false)
        {
            // The stream is not open so it is opened.
            if ((handle = openStream(stream, camera, captureParams, captureMode, decodeMode)) < 0) return -1;
        }
        else if (stream.clientCount > 0)
        {
            // The stream is in use. Check if the requested frame capture parameters are compatible
            // with current capture parameters. If not, return an error result.
            if ((stream.captureMode.match(captureMode) == false) || (stream.decodeMode.match(decodeMode) == false))
            {
                setError(BridgeResult.Code.NOT_READY, "stream_open_failed", "stream is already in use");
                return -1;
            }
            BridgeLog.info(TAG, "Using open stream for " + captureMode.sensorType.name() + " sensor");
            handle = stream.handle;
        }
        else
        {
            // The idle stream is reconfigured for the requested capture.
            closeStream(stream);
            if ((handle = openStream(stream, camera, captureParams, captureMode, decodeMode)) < 0) return -1;
        }

        int count = ++stream.clientCount;
        BridgeLog.info(TAG, "Shared stream client registered - number of clients is now " + count);
        return handle;
    }

    /**
     * Opens the specified stream.
     *
     * An new instance of the OpenNI2 @c VideoStream instance is created, and the requested frame
     * capture parameters are applied to the OpenNI2 @c Device instance representing the camera. The
     * video stream is opened, and the callback that receives incoming frames is assigned to the
     * video stream. If successful, the specified Stream instance is initialized, and a stream open
     * event is published for live capture.
     *
     * If the application is running in emulator there is nothing to open and a random stream handle
     * is returned.
     *
     * @param   stream          Stream instance representing stream to open
     * @param   camera          OpenNI2 @c Device instance representing camera device
     * @param   captureParams   optional frame capture parameters
     * @param   captureMode     frame capture mode specifying frame resolution
     * @param   decodeMode      frame decode mode
     *
     * @return  stream handle (camera id) if stream was successfully opened, or -1 on failure
     */
    private long openStream(Stream stream, Device camera, Map<String, Object> captureParams, CaptureMode captureMode, DecodeMode decodeMode)
    {
        long handle = -1;
        VideoStream.CameraSettings cameraParams = null;

        try
        {
            if (BuildConfig.EMULATOR_MODE) handle = (int)(Math.random()*Integer.MAX_VALUE);
            else
            {
                // Create the stream.
                videoStream = VideoStream.create(camera, captureMode.sensorType);
                videoStream.setVideoMode(captureMode.videoMode);

                // Set listener for frame events and start video stream.
                if (videoStream != null)
                {
                    BridgeLog.debug(TAG, "Start video stream");
                    frameListener = new FrameListener();
                    videoStream.addNewFrameListener(frameListener);
                    videoStream.start();
                }

                // Apply camera parameters,
                cameraParams = applyCaptureParams(videoStream, captureMode.sensorType, captureParams);

                // Get camera handle.
                handle = videoStream.getHandle();
            }
        }
        catch (Exception e)
        {
            setError(BridgeResult.Code.FAILURE, "camera_stream_failure", e.getMessage());
            closeStream(stream);
        }

        if ((handle < 0) || (stream.init(handle, captureMode, decodeMode, cameraParams) == false))
        {
            setError(BridgeResult.Code.FAILURE, "stream_open_failure", "failed to open stream");
            return -1;
        }

        if (captureMode.captureType == CAPTURE_TYPE_LIVE)
        {
            cameraStatus = CameraStatus.LIVECAPTURE;
            publishStreamStartEvent(stream);
        }
        else cameraStatus = CameraStatus.STILLCAPTURE;
        BridgeLog.info(TAG, "Handle " + handle + " assigned to stream");
        return handle;
    }

    /**
     * Applies the requested frame capture parameters to the specified stream.
     *
     * If the value of the @c mirror capture parameter is @c true, the parameter is removed from the
     * capture parameters to ensure the frame is not mirrored again when the camera unit processes
     * the final image.
     *
     * Gain, exposure and white balance settings are only available for the color sensor.
     * Auto-exposure and auto-whitebalance are enabled by default, auto-exposure is disabled if the
     * @c exposure camera parameter has a value not equal to zero. A new data map containing the
     * capture parameters that were successfully set is returned.
     *
     * @param   stream          stream for which to apply camera parameters
     * @param   captureType     frame capture type (live or still capture)
     * @param   captureParams   optional frame capture parameters
     *
     * @return  Java @c Map instance containing active camera parameters

     */
    private VideoStream.CameraSettings applyCaptureParams(VideoStream stream, SensorType captureType, Map<String, Object> captureParams)
    {
        // Return null if stream is not yet defined.
        if (stream == null) return null;

        BridgeLog.debug(TAG, "Apply capture parameters to camera");

        // Get capture parameters.
        boolean mirror = MapUtils.getBoolean(captureParams, "mirror", false);
        int gain = MapUtils.getInt(captureParams, "gain", -1);
        int exposure = MapUtils.getInt(captureParams, "exposure", -1);
        boolean autoExposure = (exposure > 0) && MapUtils.getBoolean(captureParams, "auto-exposure", true);
        boolean autoWhiteBalance = MapUtils.getBoolean(captureParams, "auto-whitebalance", true);

        try
        {
            // Mirror the image if required.
            stream.setMirroringEnabled(mirror);
            MapUtils.deleteFromMap(captureParams, "mirror");
        }
        catch (Exception e)
        {
            BridgeLog.error(TAG, "Failed to disable mirroring for Orbbec camera", e);
            return null;
        }

        // Return current parameters if sensor is not the color sensor.
        if (captureType != SensorType.COLOR) return stream.getCameraSettings();

        // Get camera settings.
        VideoStream.CameraSettings cameraParams;
        try
        {
            cameraParams = stream.getCameraSettings();
            if (cameraParams == null) return null;
        }
        catch (Exception e)
        {
            BridgeLog.error(TAG, "Failed to retrieve current camera settings", e);
            return null;
        }

        try
        {
            // Apply camera settings.
            if (gain != -1) cameraParams.setGain(gain);
            cameraParams.setAutoExposureEnabled(autoExposure);
            if (exposure != -1) cameraParams.setExposure(exposure);
            cameraParams.setAutoWhiteBalanceEnabled(autoWhiteBalance);
        }
        catch (Exception e)
        {
            BridgeLog.warning(TAG, "Failed to update one or more camera settings", e);
        }

        return cameraParams;
    }

    /**
     * Copies the active camera parameters to a new data map.
     *
     * @return  Java @c Map instance containing active camera parameters
     */
    @NonNull
    private Map<String, Object> getActiveCameraParams()
    {
        Map<String, Object> data = MapUtils.createMap();
        VideoStream.CameraSettings params = (videoStream != null) ? videoStream.getCameraSettings() : null;
        if (params != null)
        {
            data.put("gain", params.getGain());
            data.put("exposure", params.getExposure());
            data.put("auto-exposure", params.getAutoExposureEnabled());
            data.put("auto-whitebalance", params.getAutoWhiteBalanceEnabled());
        }
        return data;
    }

    /**
     * Unregisters client and closes stream if all clients have diconnected.
     *
     * The active video client count is decremented. When there are no more clients closeStream() is
     * called to tear down the shared stream.
     */
    private synchronized void closeStreamIfIdle(@NonNull Stream stream)
    {
        int count = --stream.clientCount;
        BridgeLog.info(TAG, "Client unregistered from " + stream.name + " stream - number of clients is now " + count);

        // Close stream if all clients are unregistered.
        if (count <= 0) closeStream(stream);
    }

    /**
     * Closes the specified stream.
     *
     * If the application is not running in emulator mode the video stream is stopped, and the
     * specified Stream instance is released. A stream release event is published before the stream is
     * closed since after closing the stream the stream details will no longer be available.
     *
     * @param   stream          stream to release (always shared stream)
     */
    private void closeStream(@NonNull Stream stream)
    {
        // Return if stream is not open.
        if (stream.isOpen == false)
        {
            BridgeLog.info(TAG, "The " +  stream.name + " stream is already closed");
            return;
        }

        // Publish bridge event signaling the stream will release.
        if (stream.captureMode.captureType == CAPTURE_TYPE_LIVE) publishStreamStopEvent(stream);

        // Stop video stream.
        if (BuildConfig.EMULATOR_MODE == false)

            if (videoStream != null)
            {
                BridgeLog.debug(TAG, "Stop video stream");
                if (frameListener != null) videoStream.removeNewFrameListener(frameListener);
                frameListener = null;
                videoStream.stop();
                videoStream.destroy();
                videoStream = null;
            }


        // Close the stream.
        BridgeLog.info(TAG, "Closed " + stream.name + " stream with handle " + stream.handle);
        stream.release(openNIInitialized ? "ready" : "unavailable");
        cameraStatus = CameraStatus.AVAILABLE;
        notifyAll();
    }

    /**
     * Releases camera resources.
     *
     * The camera is released, the OpenNI2 API is shut down, and the camera status is reset.
     */
    private synchronized void releaseCamera()
    {
        try
        {
            if (camera != null) camera.close();
        }
        catch (Exception ignored) {}
        try
        {
            if (openNIHelper != null) openNIHelper.shutdown();
        }
        catch (Exception ignored) {}
        try
        {
            if (openNIInitialized) OpenNI.shutdown();
        }
        catch (Exception ignored) {}

        cameraStatus = CameraStatus.STOPPED;
        cameraInfo = null;
        camera = null;
        usbDevice = null;
        openNIHelper = null;
        openNIInitialized = false;
    }

    /**
     * Retrieves a snapshot frame newer than the specified timestamp from the frame buffer.
     *
     * The current thread is paused for a short time to allow a new video frame to be received. If
     * available, the frame is returned. if a new video frame is still not available after this
     * pause, this is repeated until either a new frame is received or the maximum number of
     * retries is exceeded.
     *
     * @param   frameBuffer     frame buffer from which to retrieve image data
     * @param   timestamp       specifies time after which frame is considered a a new frame
     *
     * @return  Image instance on success, @c null on failure
     */
    @Nullable
    private Image getSnapshotImageFromBuffer(VideoFrameBuffer frameBuffer, long timestamp)
    {
        for (int i=0; i<NEW_FRAME_MAX_RETRIES; i++)
        {
            try { Thread.sleep(NEW_FRAME_RETRY_DELAY_MS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            Image image = frameBuffer.getImage();
            if ((image != null) && (image.getTimestamp() > timestamp)) return image;
        }
        setError(BridgeResult.Code.FAILURE, "snapshot_capture_failed", "timeout while waiting for video frame");
        return null;
    }

    /**
     * Handles a received video frame.
     *
     * This method is called by the FrameListener instance to handle an incoming frame. YUV422
     * frames captured by the color sensor are converted to gray-scale images (as described
     * @c ref YUV422-GRAY8 "here"), all other color sensor frames are directly copied to the frame
     * buffer. For depth and infrared data the method matching the decode mode specified in the
     * stream properties is called.
     *
     * @param   bytes           byte array containing frame data
     * @param   stream          stream on which frame was received (always shared stream)
     */
    private void handleFrame(@NonNull byte[] bytes, @NonNull Stream stream)
    {
        stream.frameCount.getAndIncrement();
        VideoMode videoMode = stream.captureMode.videoMode;
        DecodeMode decodeMode = stream.decodeMode;
        PixelFormat pixelFormat = videoMode.getPixelFormat();
        if (pixelFormat == null) return;
        int decodeModeId = stream.decodeMode.id & 0x0F;

        switch (stream.captureMode.sensorType)
        {
            case COLOR:
                if (pixelFormat == PixelFormat.YUV422) decodeYuv422AsGray8(bytes, stream);
                else stream.frameBuffer.setData(bytes, decodeMode.imageType, videoMode.getResolutionX(), videoMode.getResolutionY());
                break;
            case DEPTH:
                if (decodeModeId == DecodeMode.DECODE_RAW) decodeAsRaw(bytes, stream);
                else if (decodeModeId == DecodeMode.DECODE_COLOR) decodeDepthAsRgb(bytes, stream);
                else if (decodeModeId == DecodeMode.DECODE_GRAY) decodeDepthAsGray8(bytes, stream);
                break;
            case IR:
                if (decodeModeId == DecodeMode.DECODE_RAW) decodeAsRaw(bytes, stream);
                else if (decodeModeId == DecodeMode.DECODE_COLOR)
                {
                    if (pixelFormat == PixelFormat.GRAY8) decodeIRByteAsRgb(bytes, stream);
                    else if (pixelFormat == PixelFormat.GRAY16) decodeIRShortAsRgb(bytes, stream);
                    else if (pixelFormat == PixelFormat.RGB888) decodeIRAsRgb(bytes, stream);
                }
                else
                {
                    if (pixelFormat == PixelFormat.GRAY8) decodeIRByteAsGray8(bytes, stream);
                    else if (pixelFormat == PixelFormat.GRAY16) decodeIRShortAsGray8(bytes, stream);
                    else if (pixelFormat == PixelFormat.RGB888) decodeIRAsRgb(bytes, stream);
                }
                break;
        }
    }

    /**
     * @name Image Format Conversion
     *
     * @{
     */

    /**
     * Decodes @e YUV422 image data as @e GRAY8 image.
     *
     * The @e YUV422 video mode is used to provide @e GRAY8 images as discussed @ref YUV422_GRAY8
     * "here". Each quartet of bytes in the @e YUV422 data represents a pair of pixels that have
     * different luminance (@e Y) values @c y0 and @c y1 but have the same chroma values @c u
     * (@c Cb) and @c v (@e Cr), encoded as <tt>u y0 v y1</tt>. From each pair of bytes the first
     * (chroma) byte is simply ignored, and the second (luminance) byte is copied as a single byte
     * @e GRAY8 value to the array containing decoded data. The decoded data is copied to the frame
     * buffer.
     *
     * @param   bytes           byte array containing @e YUV422 frame data
     * @param   stream          stream on which data was received
     */
    private void decodeYuv422AsGray8(@NonNull byte[] bytes, @NonNull Stream stream)
    {
        int width = stream.decodeMode.width;
        int height = stream.decodeMode.height;
        int byteCount = Math.min(width*height*2, bytes.length);
        byte[] decoded = new byte[byteCount/2];

        int byteIndex = 0;
        int decodedIndex = 0;
        while (byteIndex < byteCount-1)
        {
            byteIndex++;
            decoded[decodedIndex++] = (byte)(bytes[byteIndex++] & 0xFF);
        }
        stream.frameBuffer.setData(decoded, Image.Type.RGB888, width, height);
    }

    /**
     * Copies raw depth or infrared data.
     *
     * The raw data is copied to the frame buffer preceded by a 32-byte header:
     * - offset 0: @e RAW,
     * - offset 4: sensor type (capitalized, i.e. @e DEPTH or @e IR),
     * - offset 20: integer value specifying image width,
     * - offset 24: integer value specifying image height,
     * - offset 28: integer value specifying bytes per pixel.
     *
     * @param   bytes           bytea array containing depth or infrared frame data
     * @param   stream          stream on which data was received
     */
    private void decodeAsRaw(@NonNull byte[] bytes, @NonNull Stream stream)
    {
        VideoMode videoMode = stream.captureMode.videoMode;
        int width = stream.decodeMode.width;
        int height = stream.decodeMode.height;
        int bytesPerPixel;
        switch (videoMode.getPixelFormat())
        {
            case RGB888:
                bytesPerPixel = 3;
                break;
            case GRAY16:
            case DEPTH_1_MM:
            case DEPTH_100_UM:
                bytesPerPixel = 2;
                break;
            case GRAY8:
                bytesPerPixel = 1;
                break;
            default:
                return;
        }
        byte[] decoded = new byte[bytes.length+32];
        CommonUtils.copyStringToByteArray(decoded, 0, "RAW");
        CommonUtils.copyStringToByteArray(decoded, 4, stream.captureMode.sensorType.name().toUpperCase());
        copyIntToByteArray(decoded, 20, width);
        copyIntToByteArray(decoded, 24, height);
        copyIntToByteArray(decoded, 28, bytesPerPixel);
        System.arraycopy(bytes, 0, decoded, 32, bytes.length);
        stream.frameBuffer.setData(decoded, Image.Type.RAW, width, height);
    }

    /**
     * Decodes two-byte depth data as @e RGB888 image.
     *
     * Each depth value is a little-endian two-byte short value. In theory each value can specify a
     * distance of up to 65536 units of either one millimeter or hundred micrometer, depending on
     * the video mode. Since the full two-byte depth range is in practice never useful, the range of
     * values (i.e. <tt>[depthRangeMin..depthRangeMax]</tt> with the upper and lower bound values
     * specified in the DecodeMode instance stored in the stream properties) is scaled such that 255
     * represents the minimum depth value and 0 represents the maximum depth value. Values outside
     * the range are always converted to 0. The color values matching the scaled depth values are
     * retrieved from the selected color map and rhe resulting three-byre @e RGB888 values are
     * copied to the frame buffer,
     *
     * @param   bytes           byte array containing depth frame data
     * @param   stream          stream on which data was received
     */
    private void decodeDepthAsRgb(@NonNull byte[] bytes, @NonNull Stream stream)
    {
        int width = stream.decodeMode.width;
        int height = stream.decodeMode.height;
        ColorMap.Color[] colors = stream.decodeMode.colors;
        int rangeMin = stream.decodeMode.depthRangeLower;
        int rangeMax = stream.decodeMode.depthRangeUpper;
        int range = rangeMax - rangeMin;
        int byteCount = Math.min(width*height*2, bytes.length);
        byte[] decoded = new byte[3*byteCount/2];

        // Transform byte values to RGB values.
        int byteIndex = 0;
        int decodedIndex = 0;
        while (byteIndex < byteCount-1)
        {
            int depth = (bytes[byteIndex++] & 0xFF) | ((bytes[byteIndex++] & 0xFF) << 8);
            int b = scaleToByteReversed(depth, rangeMin, rangeMax, range);
            decoded[decodedIndex++] = colors[b].R;
            decoded[decodedIndex++] = colors[b].G;
            decoded[decodedIndex++] = colors[b].B;
        }
        stream.frameBuffer.setData(decoded, Image.Type.RGB888, width, height);
    }

    /**
     * Decodes two-byte depth data as @e GRAY8 image.
     *
     * Each depth value is a little-endian two-byte short value. In theory each value can specify a
     * distance of up to 65536 units of either one millimeter or hundred micrometer, depending on
     * the video mode. Since the full two-byte depth range is in practice never useful, the range of
     * values (i.e. <tt>[depthRangeMin..depthRangeMax]</tt> with the upper and lower bound values
     * specified in the DecodeMode instance stored in the stream properties) is scaled such that 0
     * represents the minimum depth value and 255 represents the maximum depth value. Values outside
     * the range are always converted to 0. The resulting single-byte @e GRAY8 values are copied to
     * the frame buffer.
     *
     * @param   bytes           byte array containing depth frame data
     * @param   stream          stream on which data was received
     */
    private void decodeDepthAsGray8(@NonNull byte[] bytes, @NonNull Stream stream)
    {
        int width = stream.decodeMode.width;
        int height = stream.decodeMode.height;
        int rangeMin = stream.decodeMode.depthRangeLower;
        int rangeMax = stream.decodeMode.depthRangeUpper;
        int range = rangeMax - rangeMin;
        int byteCount = Math.min(width*height*2, bytes.length);
        byte[] decoded = new byte[byteCount/2];

        // Transform byte values to gray scale values.
        int byteIndex = 0;
        int decodedIndex = 0;
        while (byteIndex < byteCount-1)
        {
            int depth = (bytes[byteIndex++] & 0xFF) | ((bytes[byteIndex++] & 0xFF) << 8);
            decoded[decodedIndex++] = (byte)(scaleToByteReversed(depth, rangeMin, rangeMax, range) & 0xFF);
        }
        stream.frameBuffer.setData(decoded, Image.Type.GRAY8, width, height);
    }

    /**
     * Decodes single-byte infrared data as @e RGB888 image.
     *
     * Each infrared value is a single-byte value. The minimum and maximum infrared values are
     * determined, and the range of values is scaled such that 0 represents the minimum infrared
     * value and 255 represents the maximum value. The color values matching the scaled depth values
     * are retrieved from the selected color map and rhe resulting three-byre @e RGB888 values are
     * copied to the frame buffer,
     *
     * @param   bytes           byte array containing infrared frame data
     * @param   stream          stream on which data was retrieved
     */
    private void decodeIRByteAsRgb(@NonNull byte[] bytes, @NonNull Stream stream)
    {
        int width = stream.decodeMode.width;
        int height = stream.decodeMode.height;
        ColorMap.Color[] colors = stream.decodeMode.colors;
        int byteCount = Math.min(width*height, bytes.length);

        // Determine minimum and maximum infrared values.
        int[] minmax = new int[2];
        getByteRange(bytes, minmax);
        int range = minmax[1] - minmax[0];
        if (range <= 0) return;

        // Transform byte values to RGB values.
        byte[] decoded = new byte[3*byteCount];
        int byteIndex = 0;
        int decodedIndex = 0;
        while (byteIndex < byteCount)
        {
            int ir = bytes[byteIndex++] & 0xFF;
            int b = scaleToByteReversed(ir, minmax[0], minmax[1], range);
            decoded[decodedIndex++] = colors[b].R;
            decoded[decodedIndex++] = colors[b].G;
            decoded[decodedIndex++] = colors[b].B;
        }
        stream.frameBuffer.setData(decoded, Image.Type.RGB888, width, height);
    }

    /**
     * Decodes single-byte infrared data as @e GRAY8 image.
     *
     * Each infrared value is a single-byte value. The minimum and maximum infrared values are
     * determined, and the range of values is scaled such that 0 represents the minimum infrared
     * value and 255 represents the maximum value. The resulting single-byte @e GRAY8 values are
     * copied to the frame buffer.
     *
     * @param   bytes           byte array containing infrared frame data
     * @param   stream          stream on which data was retrieved
     */
    private void decodeIRByteAsGray8(@NonNull byte[] bytes, @NonNull Stream stream)
    {
        int width = stream.decodeMode.width;
        int height = stream.decodeMode.height;
        int byteCount = Math.min(2*width*height, bytes.length);

        // Determine minimum and maximum infrared values.
        int[] minmax = new int[2];
        getByteRange(bytes, minmax);
        int range = minmax[1] - minmax[0];
        if (range <= 0) return;

        // Transform byte values to gray scale values.
        byte[] decoded = new byte[byteCount];
        int byteIndex = 0;
        int decodedIndex = 0;
        while (byteIndex < byteCount)
        {
            int ir = bytes[byteIndex++] & 0xFF;
            decoded[decodedIndex++] = (byte)(scaleToByte(ir, minmax[0], minmax[1], range) & 0xFF);
        }
        stream.frameBuffer.setData(decoded, Image.Type.GRAY8, width, height);
    }

    /**
     * Decodes two-byte infrared data as @e RGB888 image.
     *
     * Each infrared value is a little-endian two-byte short value. The minimum and maximum infrared
     * values are determined, and the range of values is scaled such that 0 represents the minimum
     * infrared value and 255 represents the maximum value. The resulting single-byte @e GRAY8
     * RGB values selected from the configured color map are copied to the frame buffer.
     *
     * @param   bytes           byte array containing infrared frame data
     * @param   stream          stream on which data was retrieved
     */
    private void decodeIRShortAsRgb(@NonNull byte[] bytes, @NonNull Stream stream)
    {
        int width = stream.decodeMode.width;
        int height = stream.decodeMode.height;

        // Converts byte array to short infrared values and determine minimum and maximum values.
        int[] minmax = new int[2];
        short[] shorts = getShortValues(bytes, minmax);
        int shortCount = shorts.length;
        int range = minmax[1] - minmax[0];
        if (range <= 0) return;

        // Transform short values to RGB values.
        byte[] decoded = new byte[3*shortCount];
        int shortIndex = 0;
        int decodedIndex = 0;
        ColorMap.Color[] colors = stream.decodeMode.colors;
        while (shortIndex < shortCount)
        {
            int b = scaleToByte(shorts[shortIndex++], minmax[0], minmax[1], range);
            decoded[decodedIndex++] = colors[b].R;
            decoded[decodedIndex++] = colors[b].G;
            decoded[decodedIndex++] = colors[b].B;
        }
        stream.frameBuffer.setData(decoded, Image.Type.RGB888, width, height);
    }

    /**
     * Decodes two-byte infrared data as @e GRAY8 image.
     *
     * Each infrared value is a little-endian two-byte short value. The minimum and maximum infrared
     * values are determined, and the range of values is scaled such that 0 represents the minimum
     * infrared value and 255 represents the maximum value. The resulting single-byte @e GRAY8
     * values are copied to the frame buffer.
     *
     * @param   bytes           byte array containing infrared frame data
     * @param   stream          stream on which data was retrieved
     */
    private void decodeIRShortAsGray8(@NonNull byte[] bytes, @NonNull Stream stream)
    {
        int width = stream.decodeMode.width;
        int height = stream.decodeMode.height;

        // Converts byte array to short infrared values and determine minimum and maximum values.
        int[] minmax = new int[2];
        short[] shorts = getShortValues(bytes, minmax);
        int shortCount = shorts.length;
        int range = minmax[1] - minmax[0];
        if (range <= 0) return;

        // Transform short values to gray scale values.
        byte[] decoded = new byte[shortCount];
        int shortIndex = 0;
        int decodedIndex = 0;
        while (shortIndex < shortCount)
        {
            decoded[decodedIndex++] = (byte)(scaleToByte(shorts[shortIndex++], minmax[0], minmax[1], range) &0xFF);
        }
        stream.frameBuffer.setData(decoded, Image.Type.GRAY8, width, height);
    }

    /**
     * Decodes three-byte infrared data as @e RGB888 image.
     *
     * Each infrared value is a three-byte value that is copied to the frame buffer unchanged.
     *
     * @param   bytes           byte array containing infrared frame data
     * @param   stream          stream on which data was received
     */
    private void decodeIRAsRgb(@NonNull byte[] bytes, @NonNull Stream stream)
    {
        int width = stream.decodeMode.width;
        int height = stream.decodeMode.height;
        stream.frameBuffer.setData(bytes, Image.Type.RGB888, width, height);
    }

    /**
     * Copies the specified integer to the byte array
     *
     * @param   bytes           byte array to copy integer to
     * @param   pos             position in byte array to copy integer to
     * @param   value           integer to copy
     */
    private void copyIntToByteArray(@NonNull byte[] bytes, int value, int pos)
    {
        if (pos + 3 >= bytes.length) return;

        bytes[pos  ] = (byte)(value >>> 24);
        bytes[pos+1] = (byte)(value >>> 16);
        bytes[pos+2] = (byte)(value >>> 8);
        bytes[pos+3] = (byte)value;
    }

    /**
     * Get the minimum and maximum values in the byte array.
     *
     * @param   bytes           byte array containing single byte values
     * @param   minmax          integer array in which to return minimum and maximum values
     */
    private void getByteRange(@NonNull byte[] bytes, @NonNull int[] minmax)
    {
        minmax[0] = Integer.MAX_VALUE;
        minmax[1] = 0;
        int byteCount = bytes.length;
        for (int byteIndex=0; byteIndex<byteCount-1; byteIndex++)
        {
            int s = bytes[byteIndex] & 0xFF;
            if (s == 0) continue;
            if (s < minmax[0]) minmax[0] = s;
            if (s > minmax[1]) minmax[1] = s;
        }
    }

    /**
     * Transforms the byte array to short values and determine the minimum and maximum values.
     *
     * @param   bytes           byte array containing two-byte values
     * @param   minmax          integer array in which to return minimum and maximum values
     *
     * @return  array of short values retrieved from two-byte values
     */
    @NonNull
    private short[] getShortValues(@NonNull byte[] bytes, @NonNull int[] minmax)
    {
        minmax[0] = Integer.MAX_VALUE;
        minmax[1] = 0;
        int byteCount = bytes.length;
        int valueIndex = 0;
        short[] values = new short[byteCount/2];
        for (int byteIndex=0; byteIndex<byteCount-1;)
        {
            short s = (short)((bytes[byteIndex++] & 0xFF) | ((bytes[byteIndex++] & 0xFF) << 8));
            values[valueIndex++] = s;
            if (s <= 0) continue;
            if (s < minmax[0]) minmax[0] = s;
            if (s > minmax[1]) minmax[1] = s;
        }
        return values;
    }

    /**
     * Scales an integer value in the specified range.
     *
     * If the specified value is smaller than the lower bound or exceeds the upper bound of the
     * range the method returns 0. If the value is within the specified range it is scaled so the
     * minimum value results in a scaled value of 0 and the maximum value in a scaled value of 255.
     *
     * @param   value           value to scale
     * @param   min             lower bound of range
     * @param   max             upper bound of range
     * @param   range           size of range, i.e. upper bound minus lower bound
     *
     * @return  scaled value as integer in range [0..255]
     */
    private int scaleToByte(int value, int min, int max, int range)
    {
        if ((value < min) || (value > max)) return 0;
        else return ((value - min)*255 + (range/2))/range;
    }

    /**
     * Scales an integer value in the specified range (reversed values).
     *
     * If the specified value is smaller than the lower bound or exceeds the upper bound of the
     * range the method returns 0. If the value is within the specified range it is scaled so the
     * minimum value results in a scaled value of 255 and the maximum value in a scaled value of 0.
     *
     * @param   value           value to scale
     * @param   min             lower bound of range
     * @param   max             upper bound of range
     * @param   range           size of range, i.e. upper bound minus lower bound
     *
     * @return  scaled value as integer in range [0..255]
     */
    private int scaleToByteReversed(int value, int min, int max, int range)
    {
        if ((value < min) || (value > max)) return 0;
        else return ((max - value)*255 + (range/2))/range;
    }

    /**
     * @}
     */

    /**
     * Publishes an event signaling the specified stream was opened.
     *
     * @param   stream          stream that was opened (always shared stream)
     */
    private void publishStreamStartEvent(@NonNull Stream stream)
    {
        Map<String, Object> data = MapUtils.createMap();
        data.put("handle", stream.handle);
        data.put("timestamp", stream.openTime);
        data.put("sensor", stream.captureMode.sensorType.name());
        data.put("capture", StringUtils.unknownIfBlank(stream.captureMode.id));
        data.put("decode", StringUtils.unknownIfBlank(stream.decodeMode.id));
        eventHost.publishEvent(BridgeEvent.create(BridgeProtocol.MODULE_CAMERA, "stream_opened", data));
    }

    /**
     * Publishes an event signaling the specified stream will release.
     *
     * @param   stream          stream that will release (always shared stream)
     */
    private void publishStreamStopEvent(@NonNull Stream stream)
    {
        long closeTime = System.currentTimeMillis();
        long duration = closeTime - stream.openTime;
        long videoFrameCount = stream.frameCount.get();

        Map<String, Object> data = MapUtils.createMap();
        data.put("handle", stream.handle);
        data.put("timestamp", closeTime);
        data.put("sensor", stream.captureMode.sensorType.name());
        data.put("capture", StringUtils.unknownIfBlank(stream.captureMode.id));
        data.put("decode", StringUtils.unknownIfBlank(stream.decodeMode.id));
        data.put("duration", duration + " ms");
        if (videoFrameCount > 0)
        {
            data.put("frameCount", videoFrameCount);
            data.put("averageRate", 1000*videoFrameCount/duration + "fps");
        }
        eventHost.publishEvent(BridgeEvent.create(BridgeProtocol.MODULE_CAMERA, "stream_closed", data));
    }

    /**
     * Returns a data map specifying the live video stream status.
     *
     * @param stream    stream for which to return status data
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    private synchronized Map<String, Object> getStreamStatus(@NonNull Stream stream)
    {
        Map<String, Object> data = MapUtils.createMap("isOpen", stream.isOpen);
        if ((stream.isOpen) && (stream.captureMode != null))
        {
            data.put("status", stream.status);
            data.put("handle", stream.handle);
            data.put("sensor", stream.captureMode.sensorType.name().toLowerCase());
            data.put("capture", StringUtils.unknownIfBlank(stream.captureMode.id));
            data.put("frames", stream.frameCount);
            data.put("clients", stream.clientCount);
            data.put("openTime", stream.openTime - System.currentTimeMillis() + "ms");
            data.put("frameBuffer", stream.frameBuffer.buildStatusData());
            data.put("cameraParams", getActiveCameraParams());
        }
        return data;
    }

    /**
     * Builds a list of supported frame capture modes for the shared streams.
     *
     * The lists of supported live capture modes and snapshot capture modes are added to the data
     * map.
     *
     * @return  Java @c Map instance containing supported frame capture modes
     */
    @NonNull
    private Map<String, Object> getCaptureModes()
    {
        Map<String, Object> data = MapUtils.createMap();
        if (captureModes.isEmpty()) return data;

        for (SensorType sensorType : SensorType.values())
        {
            if (camera.hasSensor(sensorType) == false) continue;

            List<String> captureModeData = new ArrayList<>();
            for (CaptureMode captureMode : captureModes)
            {
                if (captureMode.sensorType == sensorType) captureModeData.add(captureMode.id + " (" +  captureMode.toString() + ")");
            }
            data.put(sensorType.name().toLowerCase() + "CaptureModes", captureModeData);
        }
        return data;
    }

    /**
     * Determines the image type matching the pixel format for the specified video mode.
     *
     * @param   videoMode       OpenNI2 video mode
     *
     * @return  image type matching pixel format, or @c null if no matching pixel format is found
     */
    @Nullable
    private static Image.Type imageType(VideoMode videoMode)
    {
        PixelFormat pixelFormat = (videoMode != null) ? videoMode.getPixelFormat() : null;
        if (pixelFormat == null) return null;

        switch (pixelFormat)
        {
            case RGB888:
                return Image.Type.RGB888;
            case GRAY8:
            case YUV422:
                return Image.Type.GRAY8;
            case YUYV:
                return Image.Type.YUV_YUY2;
            default:
                return null;
        }
    }

    /**
     * Sets the last reported error.
     *
     * @param   code            error code
     * @param   description     brief error description
     * @param   error           error text
     */
    private void setError(BridgeResult.Code code, String description, String error)
    {
        this.error = DataResult.create(code, description, error);
        BridgeLog.error(TAG, StringUtils.capitalize(error));
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Listener for USB device status events from OpenNI2 @c OpenNIHelper API.
     *
     * This class extends the OpenNI2 @c OpenNIHelper.DeviceOpenListener class. It implements
     * methods that are called if the status of a USB device changes. Each of these callback methods
     * just sets the USB device open result. A countdown latch is used to allow the owner of this
     * class to stop waiting for events if one of the event handlers has been called or a specified
     * timeout expires.
     */
    private static final class DeviceOpenListener implements OpenNIHelper.DeviceOpenListener
    {
        /** UsbOpenResult instance in which to set result. */
        private final UsbOpenResult usbOpenResult;

        /** Countdown latch. */
        private final CountDownLatch usbReady;

        /**
         * Constructs a new DeviceOpenListener instance.
         *
         * The UsbOpenResult instance and countdown latch are copied to member variables.
         *
         * @param   usbOpenResult   OpenNI2 @c UsbOpenResult instance in which to set result
         * @param   usbReady        countdown latch
         */
        private DeviceOpenListener(UsbOpenResult usbOpenResult, CountDownLatch usbReady)
        {
            this.usbOpenResult = usbOpenResult;
            this.usbReady = usbReady;
        }

        /**
         * Callback method handling successful opening of USB device.
         *
         * The opened device is set in the UsbOpenResult instance, and the countdown latch is
         * updated.
         *
         * @param   usbDevice       USB device that was opened
         */
        @Override
        public void onDeviceOpened(UsbDevice usbDevice)
        {
            usbOpenResult.device = usbDevice;
            usbReady.countDown();
        }

        /**
         * Callback method handling USB device open failure.
         *
         * The error message is set in the UsbOpenResult instance, and the countdown latch is
         * updated.
         *
         * @param   error           error message
         */
        @Override
        public void onDeviceOpenFailed(String error)
        {
            usbOpenResult.error = StringUtils.isBlank(error) ? "failed to open USB device" : error;
            usbReady.countDown();
        }

        /**
         * Callback method called if no USB device was found.
         *
         * An error message is set in the UsbOpenResult instance, and the countdown latch is
         * updated.
         */
        @Override
        public void onDeviceNotFound()
        {
            usbOpenResult.error = "no USB device found";
            usbReady.countDown();
        }
    }

    /**
     * Listener for frame events from OpenNi2 @c VideoStream API.
     *
     * This class extends the OpenNI2 SDK @c VideoStream.NewFrameListener class. It implements the
     * callback method that handles an incoming frame.
     */
    private final class FrameListener implements VideoStream.NewFrameListener
    {
        /**
         * Callback method handling received frame.
         *
         * A reference to the captured frame is read from the video stream. The binary frame data is
         * retrieved, and the frame is released before handleFrame() is called to do further
         * handling of the frame.
         *
         * @param   videoStream     video stream on which frame is received
         */
        @Override
        public synchronized void onFrameReady(@NonNull VideoStream videoStream)
        {
            // Get stream on which frame is received.
            long handle = videoStream.getHandle();
            Stream stream = (handle == sharedStream.handle) ? sharedStream : null;
            if ((stream == null) || (stream.isOpen == false)) return;

            VideoFrameRef frameRef = null;
            byte[] bytes = null;
            try
            {
                frameRef = videoStream.readFrame();
                if (frameRef != null)
                {
                    ByteBuffer sourceBuffer = frameRef.getData();
                    if (sourceBuffer != null)
                    {
                        sourceBuffer.clear();
                        bytes = new byte[sourceBuffer.remaining()];
                        sourceBuffer.get(bytes);
                    }
                }
            }
            catch (Exception ignored) {}
            finally
            {
                if (frameRef != null)
                {
                    frameRef.release();
                    if (bytes != null) handleFrame(bytes, stream);
                }
            }
        }
    }

    /**
     * Helper class storing USB access request result.
     */
    private static final class UsbOpenResult
    {
        /** USB device. */
        private UsbDevice device;

        /** Error message. */
        private String error;
    }

    /**
     * Helper class for storing stream properties.
     *
     * This class manages the stream properties and owns the CaptureMode, DecodeMode and
     * VideoFrameBuffer instances.
     */
    private final class Stream
    {
         /** Human-readable stream name. */
        private final String name;

        /** Stream handle. */
        private long handle = -1;

        /** Frame capture mode. */
        private CaptureMode captureMode = null;

        /** Frame decode mode. */
        private DecodeMode decodeMode = null;

        /** Video frame buffer. */
        private final VideoFrameBuffer frameBuffer = new VideoFrameBuffer();

        /** Counter for number of received frame. */
        private final AtomicInteger frameCount = new AtomicInteger();

        /** Number of active video clients. */
        private int clientCount = 0;

        /** Stream open timestamp. */
        private long openTime = 0L;

        /** Flag specifying if stream is open. */
        private boolean isOpen = false;

        /** Human-readable stream status. */
        private String status = "not initialized";

        /*******************************************************************************************
         * CONSTRUCTORS
         ******************************************************************************************/

        /**
         * Constructs a new Stream instance.
         *
         * Only the stream name is set,all remaining member variables are initialized when the
         * stream is opened or reset.
         */
        private Stream()
        {
            this.name = "shared";
        }

        /*******************************************************************************************
         * PRIVATE METHODS
         ******************************************************************************************/

        /**
         * Initializes this stream instance.
         *
         * The supplied handle, capture mode, decode mode, and active camera parameters are stored.
         * The frame buffer is reinitialized with metadata describing the capture settings, the
         * frame and client counters are reset, and the open timestamp and status text are
         * updated. If all mandatory properties are validated the value of the @c isOpen flag is set
         * to @c true.
         *
         * @param   handle          stream handle
         * @param   captureMode     frame capture mode specifying sensor type and video mode
         * @param   decodeMode      frame decode mode (only relevant for depth and infrared data)
         * @param   cameraParams    active camera parameters
         *
         * @return @c true if the stream is considered open, or @c false otherwise
         */
        private boolean init(long handle, @NonNull CaptureMode captureMode, DecodeMode decodeMode, VideoStream.CameraSettings cameraParams)
        {
            // Copy specified stream properties.
            this.handle = handle;
            this.captureMode = captureMode;
            this.decodeMode = decodeMode;

            // Add capture parameters to frame buffer.
            this.frameBuffer.init(toMetaData(cameraParams));

            // Initialize counters.
            this.frameCount.set(0);
            this.clientCount = 0;

            // Set status info.
            this.openTime = System.currentTimeMillis();
            this.isOpen = (handle >= 0);
            this.status = (isOpen) ? "streaming" : "error";

            return isOpen;
        }

        /**
         * Releases this Stream instance.
         *
         * This method calls reset() to clear all stream properties and set the specified status
         * text.
         *
         * @param   status          stream status text to set after closing the stream
         */
        private void release(String status) { reset(status); }

        /**
         * Resets the Stream instance to its default state.
         *
         * The stream handle, capture mode, and decode mode are blanked, the video frame buffer is
         * emptied, the frame and client counters are reset, and the specified status text is set.
         *
         * @param   status          stream status text to set after resetting the stream
         */
        private void reset(String status)
        {
            this.handle = -1;
            this.captureMode = null;
            this.decodeMode = null;

            // Clear frame buffer.
            this.frameBuffer.clear();

            // Reset counters
            this.frameCount.set(0);
            this.clientCount = 0;

            // Set status info.
            this.openTime = -1L;
            this.isOpen = false;
            this.status = status;
        }

        /**
         * Builds a metadata map containing the current stream capture settings.
         *
         * @param cameraParams      active camera parameters
         *
         * @return  Java @c Map instance containing capture metadata
         */
        @NonNull
        private Map<String, Object> toMetaData(VideoStream.CameraSettings cameraParams)
        {
            Map<String, Object> data = MapUtils.createMap("camera", CAMERA_NAME);
            if (isOpen)
            {
                data.put("Camera-SensorType", captureMode.sensorType.name());
                data.put("Camera-CaptureMode", StringUtils.unknownIfBlank(captureMode.id));
                data.put("Camera-FrameFormat", captureMode.videoMode.getPixelFormat().name());
                data.put("Camera-FrameRate", captureMode.videoMode.getFps() + "fps");
                data.put("Camera-DecodeMode", decodeMode.toString());
                data.put("Camera-FrameSize", decodeMode.width + "x" + decodeMode.height);
                data.put("Camera-Gain", cameraParams.getGain());
                data.put("Camera-Exposure", cameraParams.getExposure());
                data.put("Camera-AutoExposure", cameraParams.getAutoExposureEnabled());
                data.put("Camera-AutoWhiteBalance", cameraParams.getAutoWhiteBalanceEnabled());
                if (videoStream != null)
                {
                    if (captureMode.sensorType != SensorType.IR)
                    {
                        data.put("Camera-HorizontalFieldOfView", Math.round(100*Math.toDegrees(videoStream.getHorizontalFieldOfView()))/100f);
                        data.put("Camera-VerticalFieldOfView", Math.round(100*Math.toDegrees(videoStream.getVerticalFieldOfView()))/100f);
                    }
                }
                if (captureMode.sensorType == SensorType.DEPTH)
                {
                    data.put("Camera-DepthRangeMin", decodeMode.depthRangeLower);
                    data.put("Camera-DepthRangeMax", decodeMode.depthRangeUpper);
                }
            }
            return data;
        }
    }

    /**
     * Helper class for storing frame capture mode properties
     *
     * Each frame capture mode is a combination of a sensor type and a video format that specifies
     * the frame size, pixel format and frame rate. A mode id is constructed from the video mode
     * properties. This mode id is not unique, the same mode id may exist for different sensor
     * types. Each mode is assigned a score that is used to select the highest scoring compatible
     * mode if no frame capture mode is explicitly requested.
     *
     * Instances of this class should always be constructed using the create() factory method, since
     * that method does not only create an instance but also validates it and returns @c null if
     * invalid.
     */
    private static class CaptureMode
    {
        /** Frame capture mode id. */
        private final String id;

        /** Sensor type (color, depth or infrared sensor) for which this mode can be used. */
        private final SensorType sensorType;

        /** Frame capture type (live or still capture) for which this mode can be used. */
        private final int captureType;

        /** Video mode specifying frame size, pixel format and frame rate. */
        private final VideoMode videoMode;

        /** Score assigned to capture mode. */
        private final int score;

        /**
         * Constructs a new CaptureMode instance.
         *
         * The mode id is derived from the video mode. The capture type, sensor type and video mode
         * are copied to member variables, and a score is assigned to the mode
         *
         * @param   sensorType      sensor type for which this mode can be applied
         * @param   captureType     frame capture type (live or still capture)
         * @param   videoMode       video mode specifying frame size, pixel format and frame rate
         */
        private CaptureMode(SensorType sensorType, int captureType, VideoMode videoMode)
        {
            this.id = modeId(videoMode);
            this.sensorType = sensorType;
            this.captureType = captureType;
            this.videoMode = videoMode;
            this.score = score(videoMode);
        }

        /**
         * Creates and validates a new frame capture mode.
         *
         * A new @c CaptureMode instance is returned only if a valid mode id can be derived from the
         * supplied video mode.
         *
         * @param   sensorType      sensor type for which this mode can be applied
         * @param   captureType     frame capture type (live or still capture)
         * @param   videoMode       video mode specifying frame size, pixel format and frame rate
         *
         * @return  CaptureMode instance, or @c null if the frame capture mode is not valid
         */
        @Nullable
        private static CaptureMode create(SensorType sensorType, int captureType, VideoMode videoMode)
        {
            CaptureMode captureMode = new CaptureMode(sensorType, captureType, videoMode);
            return (StringUtils.isBlank(captureMode.id) == false) ? captureMode : null;
        }

            /**
         * Checks whether the specified capture mode is compatible with the current mode.
         *
         * Capture modes are considered compatible when they have the same capture type, sensor type
         * and and mode id.
         *
         * @param   captureMode     frame capture mode to compare with
         *
         * @return  @c true if the capture mode is compatible, or @c false otherwise
         */
        private boolean match(@NonNull CaptureMode captureMode)
        {
            return ((captureMode.videoMode.equals(videoMode))
                && (captureMode.sensorType == sensorType)
                && (id.equalsIgnoreCase(captureMode.id)));
        }

        /**
         * Creates a mode id from the specified video mode.
         *
         * The mode id is constructed by concatenating a string specifying the resolution, a string
         * specifying the pixel format, and if not equal to the default value the numerical frame
         * rate. If either the resolution or pixel format is invalid a @c null value is returned,
         * making this mode invalid.
         *
         * @param   videoMode       video mode from which to create mode id
         *
         * @return  mode id, or @c empty string if resolution or video mode is not supported
         */
        @NonNull
        private String modeId(@NonNull VideoMode videoMode)
        {
            // Get resolution id.
            String resolutionId = CameraResolution.get(videoMode.getResolutionX(), videoMode.getResolutionY());
            if (resolutionId == null) return "";

            // Get pixel format id.
            PixelFormat pixelFormat = videoMode.getPixelFormat();
            String pixelFormatId = (pixelFormat != PixelFormat.YUV422) ? (pixelFormat(pixelFormat)) : "gray8";
            if (pixelFormatId == null) return "";

            // Format label.
            int fps = videoMode.getFps();
            if ((fps == 30) || (pixelFormat == PixelFormat.DEPTH_1_MM) || (pixelFormat == PixelFormat.DEPTH_100_UM))
                return resolutionId + "-" + pixelFormatId;
            else return resolutionId + "-" + pixelFormatId + "-" + fps;
        }

        /**
         * Returns the name of the specified pixel format.
         *
         * @param   pixelFormat     pixel format to convert
         *
         * @return  pixel format name, or @c null if pixel format is invalid
         */
        private String pixelFormat(PixelFormat pixelFormat)
        {
            if (pixelFormat == null) return null;

            switch (pixelFormat)
            {
                case RGB888:
                    return "rgb";
                case YUV422:
                    return "yuv";
                case YUYV:
                    return "yuyv";
                case DEPTH_1_MM:
                    return "coarse";
                case DEPTH_100_UM:
                    return "fine";
                default:
                    return pixelFormat.name().toLowerCase();
            }
        }

        /**
         * Scores this frame capture mode.
         *
         * Frame capture modes are scored to allow the best available capture mode type to be
         * selected if no capture mode is explicitly requested. The score is determined by scoring
         * the four video mode properties. The final score is an integer value that only the lower
         * two bytes, with a four-bit  nibble specifying the score for each property. From high to
         * low the nibbles represent:
         * - frame width: Larger width is preferred over smaller width.
         * - frame encoding: The @c YUYV encoding format is preferred for color images and IR data,
         *   1 millimeter depth samples are preferred for depth data.
         * - frame aspect ratio: The 16x9 aspect ratio is preferred.
         * - frame rate: The 30 fps frame rate is preferred.
         *
         * This means the mode with the largest frame width will always have the highest score, and
         * the frame rate score will only be relevant of all other properties are the same.
         *
         * @param   videoMode       video mode for which to compute score
         *
         * @return  score assigned to frame capture mode
         */
        private int score(@NonNull VideoMode videoMode)
        {
            // Get width and height.
            int width = videoMode.getResolutionX();
            int height = videoMode.getResolutionY();

            // Score pixel format.
            int pixelFormatScore = 0;
            switch (videoMode.getPixelFormat())
            {
                case YUYV:
                case DEPTH_1_MM:
                    pixelFormatScore = 3;
                    break;
                case RGB888:
                case DEPTH_100_UM:
                    pixelFormatScore = 2;
                    break;
                case GRAY8:
                case YUV422:
                    pixelFormatScore = 1;
                    break;
            }

            // Score aspect ratio.
            int aspectRationScore = 0;
            if (width*9 == height*16) aspectRationScore = 2;
            else if (width*3 != height*4) aspectRationScore = 1;

            // Score frame rate.
            int frameRateScore = (videoMode.getFps() == 30) ? 1 : 0;

            return ((width >> 4) << 16) | (pixelFormatScore << 12) | (aspectRationScore << 8) | (frameRateScore);
        }

        /**
         * Returns a human-readable description of the capture mode.
         *
         * The description includes the frame resolution.
         *
         * @return  string representation of the frame capture mode
         */
        @NonNull
        public String toString()
        {
            String resolution = String.format("%dx%d", videoMode.getResolutionX(), videoMode.getResolutionY());
            String format;
            switch (videoMode.getPixelFormat())
            {
                case DEPTH_100_UM:
                    format = "0.1 mm resolution";
                    break;
                case DEPTH_1_MM:
                    format = "1 mm resolution";
                    break;
                default:
                    format = videoMode.getPixelFormat().name() + " encoding";
            }
            return  resolution + ", " + format + ", " + videoMode.getFps() + "fps";
        }
    }

    /**
     * Helper class for storing frame decode mode properties.
     *
     * The frame decode mode specifies the size and format in which frames are stored in the frame
     * buffer. Frames captured by the color sensor can be decoded as color or grayscale images.
     * Depth and infrared sensor data can be decoded to produce a grayscale image, a color image
     * created using different color palettes, or may be stored as raw data.
     *
     * The @c decode frame capture parameter specifies the preferred frame decode mode. If not
     * specified, or the preferred mode is not supported by the combination of stream type, sensor
     * type and capture mode, a default mode is returned instead.
     *
     * The @c depthrangemin and @c depthrangemax frame capture parameters may be supplied to
     * override the default range for decoding depth data.
     *
     * Instances of this class should always be constructed using the create() factory method, since
     * that method does not only create an instance but also validates it and returns @c null if
     * invalid.
     */
    private static class DecodeMode 
    {
        /** Mode specifying data must not be decoded . */
        private static final int DECODE_RAW = 1;

        /** Mode specifying data must be decoded as gray-scale image. */
        private static final int DECODE_GRAY = 2;

        /** Mode specifying data must be decoded as color image. */
        private static final int DECODE_COLOR = 3;

        /** Decode mode id. */
        private final int id;

        /** Frame width in pixels. */
        private final int width;

        /** Frame height in pixels. */
        private final int height;

        /** Image type. */
        protected final Image.Type imageType;

        /** Lower bound of range used to decode depth data. */
        private final int depthRangeLower;

        /** Upper bound of range used to decode depth data. */
        private final int depthRangeUpper;

        /** Colors used to decode depth and infrared data. */
        private final ColorMap.Color[] colors;

        /**
         * Constructs a new DecodeMode instance.
         *
         * The id of the requested or default decode mode, the frame width and height and the image
         * type matching the video mode specified by the capture mode are set. If the sensor type
         * specifies the depth sensor, the depth data range is set. For both the depth sensor and
         * infrared sensor the color palette is set if the decode mode id is not @c raw.
         *
         * @param   captureMode     frame capture specifying sensor type and video mode
         * @param   captureParams   optional frame capture parameters
         */
        private DecodeMode(@NonNull CaptureMode captureMode, Map<String, Object> captureParams)
        {
            // Set mode id.
            id = getId(captureMode.captureType, captureMode.sensorType, captureMode.videoMode, captureParams);

            // Set frame width and height and image type.
            if (captureMode.videoMode != null)
            {
                width = captureMode.videoMode.getResolutionX();
                height = captureMode.videoMode.getResolutionY();
                if (captureMode.sensorType == SensorType.COLOR) imageType = imageType(captureMode.videoMode);
                else switch (id & 0x0F)
                {
                    case DECODE_RAW:
                        imageType = Image.Type.RAW;
                        break;
                    case DECODE_GRAY:
                        imageType = Image.Type.GRAY8;
                        break;
                    case DECODE_COLOR:
                        imageType = Image.Type.RGB888;
                        break;
                    default:
                        imageType = null;
                }
            }
            else
            {
                width = 0;
                height = 0;
                imageType = null;
            }

            // Set depth data range (depth sensor only).
            Range<Integer> range = (captureMode.sensorType == SensorType.DEPTH)
                ? getDepthRange(captureMode.videoMode, captureParams) : new Range<>(0, 0);
            depthRangeLower = range.getLower();
            depthRangeUpper = range.getUpper();

            // Set color palette (depth sensor and infrared sensor).
            colors = ((captureMode.sensorType != SensorType.COLOR) && ((id != DECODE_RAW))) ? ColorMap.getPalette(id >> 4) : null;
        }

        /**
         * Creates and validates a new DecodeMode instance.
         *
         * @param   captureMode     frame capture specifying sensor type and video mode
         * @param   captureParams   optional frame capture parameters
         *
         * @return  new DecodeMode instance, or @c null if decode mode is invalid
         */
        @Nullable
        private static DecodeMode create(CaptureMode captureMode, Map<String, Object> captureParams)
        {
            if (captureMode == null) return null;

            DecodeMode decodeMode =  new DecodeMode(captureMode, captureParams);
            return ((decodeMode.id != -1) && (decodeMode.width != 0) && (decodeMode.height != 0) && (decodeMode.imageType != null))
                ? decodeMode : null;
        }

        /**
         * Checks whether the specified decode mode is compatible with the current mode.
         *
         * Decode modes are compatible when both the decode mode id and the stored image type match.
         *
         * @param   decodeMode  frame decode mode to compare
         *
         * @return  @c true if the decode mode is compatible, or @c false otherwise
         */
        private boolean match(@NonNull DecodeMode decodeMode)
        {
            return ((decodeMode.id == id) && (decodeMode.imageType == imageType));
        }

        /**
         * Retrieves the frame decode mode id.
         *
         * For data captured by the color sensor the frame decode mode is either @c color or @c gray
         * depending on the video mode. For color data captured by the infrared sensor the mode is
         * always @c color. For all other combinations of sensor type and video mode the mode is
         * determined by the @c decode frame capture parameter. If the @c raw mode is selected for
         * the live stream type the id of the default mode for the sensor is returned instead.
         *
         * @param   captureType     frame capture type (live or still capture)
         * @param   sensorType      OpenNI2 sensor type
         * @param   videoMode       OpenNI2 video mode
         * @param   captureParams   optional frame capture parameters
         *
         * @result  integer frame decode mode id, or -1 if sensor type is invalid
         */
        private int getId(int captureType, @NonNull SensorType sensorType, VideoMode videoMode, Map<String, Object> captureParams)
        {
            int defaultModeId;
            switch (sensorType)
            {
                case COLOR:
                    defaultModeId = DECODE_COLOR;
                    break;
                case DEPTH:
                    defaultModeId = DEFAULT_DEPTH_DECODE_MODE;
                    break;
                case IR:
                    defaultModeId = DEFAULT_IR_DECODE_MODE;
                    break;
                default:
                    defaultModeId = -1;
            }

            // Return default mode id if pixel format is not specified.
            PixelFormat pixelFormat = (videoMode != null) ? videoMode.getPixelFormat() : null;
            if (pixelFormat == null) return defaultModeId;

            // Color sensor data only supports color and gray decode modes.
            if (sensorType == SensorType.COLOR) return (pixelFormat == PixelFormat.GRAY8) ? DECODE_GRAY : DECODE_COLOR;

            // Infrared color data only supports color decode mode.
            if ((sensorType == SensorType.IR) && ((pixelFormat == PixelFormat.RGB888) || (pixelFormat == PixelFormat.YUYV) || (pixelFormat == PixelFormat.YUV422)))
            {
                return DECODE_COLOR;
            }

            // Return default mode id if decode parameter is not specified.
            String decodeMode = StringUtils.normalize(MapUtils.getString(captureParams, "decode", "null"));
            if (decodeMode == null) return defaultModeId;

            // Return decode mode matching decode parameter.
            switch (decodeMode)
            {
                case "raw":
                    if (captureType == CAPTURE_TYPE_STILL) return DECODE_RAW;
                    else return defaultModeId;
                case "gray":
                case "grey":
                    return DECODE_GRAY;
                default:
                    int paletteId = ColorMap.getPaletteId(decodeMode) << 4;
                    return DECODE_COLOR | paletteId;
            }
        }

        /**
         * Sets range for depth data.
         *
         * The lower and upper bound values are retrieved from the frame capture parameters if
         * specified. If not, the default values defined s member variables are used instead. The
         * lower and upper bounds specified by the capture parameters or member variables are in
         * centimeters. The returned range is specified in units matching the video format.
         *
         * @param   videoMode       OpenNI2 video mode
         * @param   captureParams   optional frame capture parameters
         *
         * @return  Android @c Range instance specifying depth data range
         */
        private Range<Integer> getDepthRange(VideoMode videoMode, Map<String, Object> captureParams)
        {
            int scale;
            Range<Integer> defaultRange;
            int defaultLowerCm;
            int defaultUpperCm;

            // Set factor to convert from centimeters to pixel format units and set default range.
            PixelFormat pixelFormat = (videoMode != null) ? videoMode.getPixelFormat() : null;
            if (pixelFormat == PixelFormat.DEPTH_100_UM)
            {
                scale = 100;
                defaultLowerCm = MIN_DEPTH_SHORT_RANGE_CM;
                defaultUpperCm = MAX_DEPTH_SHORT_RANGE_CM;
                defaultRange = toScaledRange(MIN_DEPTH_SHORT_RANGE_CM, MAX_DEPTH_SHORT_RANGE_CM, scale);
            }
            else
            {
                scale = 10;
                defaultLowerCm = MIN_DEPTH_LONG_RANGE_CM;
                defaultUpperCm = MAX_DEPTH_LONG_RANGE_CM;
                defaultRange = toScaledRange(MIN_DEPTH_LONG_RANGE_CM, MAX_DEPTH_LONG_RANGE_CM, scale);
            }

            // If capture parameters are not available return default range.
            if (captureParams == null) return defaultRange;

            // Get lower and upper bound values from camera parameters. Swap lower and upper values
            // if necessary.
            int lower = MapUtils.getInt(captureParams, "depthrangemin", defaultLowerCm);
            int upper = MapUtils.getInt(captureParams, "depthrangemax", defaultUpperCm);
            Range<Integer> customRange = toScaledRange(lower, upper, scale);

            // Return intersection of two ranges.
            return (customRange.getLower() < customRange.getUpper()) ? customRange.intersect(defaultRange) : defaultRange;
        }

        /**
         * Creates a @c Range instance from the specified lower and upper bound values.
         *
         * The lower and upper bounds are scaled from centimeters to the units matching the pixel
         * format.
         *
         * @param   lower       lower bound of depth range in centimeters
         * @param   upper       upper bound of depth range in centimeters
         * @param   scale       scale to convert from centimeters to units matching pixel format
         */
        @NonNull
        private Range<Integer> toScaledRange(int lower, int upper, int scale)
        {
            int rangeLower = lower*scale;
            int rangeUpper = upper*scale;
            return (rangeLower < rangeUpper) ? new Range<>(rangeLower, rangeUpper) : new Range<>(rangeUpper, rangeLower);
        }
    }
}
