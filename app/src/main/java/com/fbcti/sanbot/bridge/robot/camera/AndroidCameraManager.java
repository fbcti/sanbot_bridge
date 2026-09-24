/**
 * @file        AndroidCameraUnit.java
 * @brief       Implements AndroidCameraUnit class.
 */
package com.fbcti.sanbot.bridge.robot.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
import com.fbcti.sanbot.bridge.robot.media.Image;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the Android camera.
 *
 * This class implements the BridgeCameraManager interface to expose the functionality provided by
 * the Android camera located in the robot body. It owns and manages the lifecycle of the Android
 * @c Camera API instance.
 *
 * The class manages a shared video/image stream that is used for live video streaming, snapshot
 * image capture, and still image capture. A snapshot image is obtained by just capturing a single
 * video frame. The @c Camera API provides a separate method for capturing still images that may
 * have higher resolution and better quality than snapshot images. The class is responsible for
 * opening the stream, copying incoming frames into a frame buffer, and closing the stream when no
 * longer required.
 *
 * A frame capture mode associated with the steam defines how frames are acquired from the camera
 * (see @ref ANDROID_CAPTURE_PARAMS "here" for supported parameters). The capture mode to use is
 * specified by the @c capture frame capture parameter. If this parameter is not specified or not
 * valid, a default capture mode is selected. The stream also has an associated frame decode mode
 * that defines how those frames are represented in the frame buffer, For this camera the decode
 * mode is fully determined by the capture type (live or still capture) and the selected frame
 * capture mode.
 *
 * @parblock @note
 * The Android @c Camera API @c takePicture() method takes two arguments, with the second argument
 * both implementations of the @c Camera.PictureCallback interface. According to the documentation
 * the first receives raw image data, the second image data compressed as @e JPEG. However, the
 * callback for raw frame capture is never called, so apparently raw frame capture is not supported
 * by the camera.
 * @endparblock
 *
 * @version     1.0.001
 * @date        2 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
@SuppressWarnings("deprecation")
public final class AndroidCameraManager implements BridgeCameraManager
{
    /** Source label used for log messages. */
    private static final String TAG = "AndroidCameraManager";

    /** Human-readable camera name. */
    public static final String CAMERA_NAME = "android";

    /** Human-readable camera alias. */
    public static final String CAMERA_ALIAS = "body";

    /** Identifies live capture type. */
    public static final int CAPTURE_TYPE_LIVE = 1;

    /** Identifies still capture type. */
    public static final int CAPTURE_TYPE_STILL = 2;

    /** Human-readable live capture type name. */
    public static final String CAPTURE_NAME_LIVE = "live";

    /** Human-readable still capture type name. */
    public static final String CAPTURE_NAME_STILL = "still";

    /**
     * Camera warmup time in milliseconds.
     *
     * The warmup time is set to allow the camera to automatically determine the best exposure and
     * white-balance values. If too small, still images may be over- or underexposed.
     */
    private static final int CAMERA_WARMUP_TIME_MS = 500;

    /** Maximum number of retries while waiting for stream to become available. */
    public static final int STREAM_CHECK_MAX_RETRIES = 40;

    /** Delay in milliseconds between retries while waiting for stream to become available. */
    public static final long STREAM_CHECK_DELAY_MS = 100L;

    /** Maximum number of retries to retrieve video frame used for snapshot image. */
    public static final int NEW_FRAME_MAX_RETRIES = 30;

    /** Delay in milliseconds between attempts to retrieve video frame used for snapshot image. */
    public static final long NEW_FRAME_RETRY_DELAY_MS = 100L;

    /**
     * Maximum frame width used when selecting the default live frame capture mode.
     *
     * The frame capture mode with the largest width not exceeding this value will be selected if
     * the @c capture frame capture parameter is not specified or is invalid.
     */
    private static final int DEFAULT_LIVE_FRAME_WIDTH = 640;

    /**
     * Maximum frame width used when selecting the default still frame capture mode.
     *
     * The frame capture mode with the largest width not exceeding this value will be selected if
     * the @c capture frame capture parameter is not specified or is invalid.
     */
    private static final int DEFAULT_STILL_FRAME_WIDTH = 1280;

    /** Android application context. */
    private Context context = null;

    /** Android @c Camera API instance managed by this class. */
    private Camera camera = null;

    /** Selected Android camera id, or -1 if no camera is available. */
    private int cameraId = -1;

    /** Selected camera information. */
    private final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

    /** Preview surface texture used by the Android camera. */
    private final SurfaceTexture surfaceTexture = new SurfaceTexture(0);

    /** Shared stream used for video streaming, snapshot capture, and still image capture. */
    private final Stream sharedStream = new Stream("video/image");

    /** List of supported frame capture modes. */
    private final List<CaptureMode> captureModes = new ArrayList<>();

    /** Callback host to which to publish bridge unit events. */
    private final BridgeEventHost eventHost;

    /** Current camera status. */
    private volatile CameraStatus cameraStatus = CameraStatus.NOT_INITIALIZED;

    /** Last reported error. */
    private volatile DataResult error = null;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new AndroidCameraManager instance.
     *
     * The base class constructor is called to copy the event host to a member variable.
     *
     * @param   eventHost       callback host to which to forward camera manager events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward camera manager events to be published by the BridgeService
     * instance.
     */
    public AndroidCameraManager(BridgeEventHost eventHost)
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
     * the existing Android @c Camera API instance representing the camera is released before
     * initCamera() is called to create and configure a new one. After initialization completes, the
     * shared stream state is reset to reflect the resulting camera status.
     *
     * @param   context         Android application context
     * @param   args            additional initialization parameters
     *
     * This implementation requires no additional initialization parameters.
     */
    public synchronized void init(Context context, Object... args)
    {
        BridgeLog.info(TAG, "Initialize Android camera manager");
        this.context = context;
        error = null;

        // Close shared stream if currently open.
        if (sharedStream.isOpen) closeStream(sharedStream);

        // Release camera if already initialized.
        if ((cameraStatus == CameraStatus.ERROR) || (cameraStatus == CameraStatus.AVAILABLE)) releaseCamera();

        // Initialize camera.
        cameraStatus = ((BuildConfig.EMULATOR_MODE) || (initCamera())) ? CameraStatus.AVAILABLE : CameraStatus.ERROR;
        String status = (cameraStatus == CameraStatus.AVAILABLE) ? ((BuildConfig.EMULATOR_MODE) ? "emulated_ready" : "ready") : "unavailable";

        // Reset shared stream.
        sharedStream.reset(status);
    }

    /**
     * Shuts down the camera manager.
     *
     * The managed Android @c Camera API instance is released and the shared stream is closed.
     */
    public synchronized void shutdown()
    {
        BridgeLog.info(TAG, "Shut down Android camera manager");

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
        error = null;
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
     * Retrieves the most recently buffered frame from the video frame buffer.
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
            setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure", "camera not available for capturing snapshot image");
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
     * Captures a still image using the Android @c Camera API @c takePicture() path.
     *
     * If the camera is currently in use for live video streaming or capturing a snapshot image,
     * waitForStream() is called to pause the current thread until the camera becomes available or
     * the configured timeout expires. Once the camera is available, the requested or default frame
     * capture mode and frame decode mode are selected, ensureStreamOpen() is called to open the
     * shared stream, and the Android @c Camera API @c takePicture() method is called to capture a
     * still image. Because this operation is asynchronous, a countdown latch is used to wait until
     * the still frame is received. Once the latch is released, getStillImageFromBuffer() is called
     * to retrieve the captured image.
     *
     * The shared stream is closed if no longer required.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  Image instance containing the captured still image, or @c null on failure
     */
    @Nullable
    public synchronized Image getStillImage(Map<String, Object> captureParams)
    {
        // If camera is not available for streaming pause thread and try again.
        if ((cameraStatus != CameraStatus.AVAILABLE) && (waitForStream(sharedStream) == false))
        {
            setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure", "camera not available for capturing still image");
            return null;
        }

        // Select frame capture mode specified by frame capture parameters.
        CaptureMode captureMode = selectCaptureMode(CAPTURE_TYPE_STILL, captureParams);
        if (captureMode == null) return null;

        // Select frame decode mode specified by frame capture parameters.
        DecodeMode decodeMode = selectDecodeMode(captureMode, captureParams);
        if (decodeMode == null) return null;

        // Ensure shared stream is open. Return error response on failure.
        if (ensureStreamOpen(sharedStream, captureParams, captureMode, decodeMode) < 0) return null;

        try
        {
            long timestamp = System.currentTimeMillis();
            AtomicReference<byte[]> frameBuffer = new AtomicReference<>();
            CountDownLatch frameReady = new CountDownLatch(1);

            // Take picture.
            long wait = NEW_FRAME_MAX_RETRIES*NEW_FRAME_RETRY_DELAY_MS;
            camera.takePicture(null, null, new StillFrameListener(frameBuffer, frameReady));
            if (frameReady.await(wait, TimeUnit.MILLISECONDS) == false)
            {
                setError(BridgeResult.Code.FAILURE, "image_capture_failed", "failed to capture still image");
                return null;
            }

            // Retrieve image from frame buffer.
            return getStillImageFromBuffer(frameBuffer, sharedStream.decodeMode, timestamp, sharedStream.toMetaData(camera.getParameters()));
        }
        catch (Exception e)
        {
            setError(BridgeResult.Code.FAILURE, "still_capture_failed", "failed to capture still image - " + e.getMessage());
            return null;
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
     * Builds a data map containing the current camera manager status data.
     *
     * The returned map includes the camera name and status, the selected camera id, the currently
     * active camera parameters, and shared stream status data.
     *
     * @return  Java @c Map instance containing camera manager status data
     */
    @NonNull
    public Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = MapUtils.createMap("camera", CAMERA_NAME, "status", cameraStatus.name().toLowerCase());
        Map<String, Object> infoData = MapUtils.createMap("id", cameraId);
        infoData.put("type", (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) ? "back facing" : "front facing");
        data.put("cameraInfo", infoData);
        data.put("streamStatus", getStreamStatus(sharedStream));
        return data;
    }

    /**
     * Builds a data map containing supported camera feature data.
     *
     * The returned map contains the supported frame-capture modes and any capture parameters that
     * can be queried from the active Android @c Camera instance, such as exposure range, supported
     * effects, and supported zoom values.
     *
     * @return  Java @c Map instance containing camera feature data
     */
    @NonNull
    public Map<String, Object> buildFeatureData()
    {
        Map<String, Object> data = MapUtils.createMap("cameraName", CAMERA_NAME, "cameraAlias", CAMERA_ALIAS);
        data.putAll(getCaptureModes());
        if (camera != null)
        {
            Camera.Parameters cameraParams = camera.getParameters();
            List<Integer> zoomValues = cameraParams.getZoomRatios();
            data.put("exposure", MapUtils.createMap("min", cameraParams.getMinExposureCompensation(),
                "max", cameraParams.getMaxExposureCompensation()));
            if (cameraParams.isAutoExposureLockSupported()) data.put("exposure-lock", Arrays.asList(true, false));
            data.put("effect", cameraParams.getSupportedColorEffects());
            data.put("zoom", MapUtils.createMap("min", zoomValues.get(0), "max", zoomValues.get(zoomValues.size()-1),
                "step", zoomValues.get(1)-zoomValues.get(0)));
        }
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
     * Initializes the Android camera.
     *
     * The Android camera is selected from the list of available cameras, the camera is opened, and
     * the preview surface texture is added. This is necessary even though the preview surface is
     * not actually used for normal OpenGL rendering. The list of frame capture modes supported by
     * the camera is retrieved.
     *
     * @return  @c true on success, @c false on failure
     */
    private boolean initCamera()
    {
        BridgeLog.debug(TAG, "Initialize camera");

        // Select camera to use.
        cameraId = selectCamera();
        if (cameraId < 0) return false;

        try
        {
            // Open camera and retrieve camera info.
            camera = Camera.open(cameraId);
            Camera.getCameraInfo(cameraId, cameraInfo);

            // Set preview surface texture. The 0 parameter value indicated the the surface is
            // unbound, i.e. it isn't intended to be used for normal rendering.
            camera.setPreviewTexture(surfaceTexture);
        }
        catch (Exception e)
        {
            setError(BridgeResult.Code.FAILURE, "camera_init_failed", "failed to initialize Android camera - " + e.getMessage());
            return false;
        }

        // Initialize list of supported frame capture modes.
        initCaptureModes(camera);
        BridgeLog.debug(TAG, "Android camera successfully initialized");
        return true;
    }

    /**
     * Selects the preferred camera.
     *
     * If multiple cameras are available the first front-facing camera is selected if available. If
     * no front facing camera is available, the camera with id 0 is selected.
     *
     * @return  preferred camera id, or -1 if no camera is available
     */
    private int selectCamera()
    {
        int cameraCount = Camera.getNumberOfCameras();
        if (cameraCount == 0) return -1;

        for (int cameraId=0; cameraId<cameraCount; cameraId++)
        {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return cameraId;
        }
        return 0;
    }

    /**
     * Initializes the list of supported frame-capture modes.
     *
     * The @c captureModes list is build from the capabilities supported by the Android @c Camera 
     * instance. Each capture mode combines a capture type (live or still capture), a preview size, 
     * and a picture size. Live capture modes are created from the supported preview sizes. Still 
     * capture modes are created from the supported picture sizes. Because for still image capture a 
     * preview is required even though its resolution is not relevant the smallest available preview
     * size is chosen.
     *
     * @param   camera      Android @c Camera API instance whose capabilities are queried
     */
    private void initCaptureModes(Camera camera)
    {
        if (camera == null) return;

        // Clear list of frame capture modes.
        captureModes.clear();

        // Set live capture modes specifying preview size only.
        Camera.Size minPreviewSize = null;
        for (Camera.Size size : camera.getParameters().getSupportedPreviewSizes())
        {
            CaptureMode captureMode = CaptureMode.create(CAPTURE_TYPE_LIVE, size, size);
            if (captureMode != null) captureModes.add(captureMode);
            if ((minPreviewSize == null) || (size.width < minPreviewSize.width)) minPreviewSize = size;
        }
        if (minPreviewSize == null) return;

        // Set still capture modes specifying both preview size and picture size.
        for (Camera.Size size : camera.getParameters().getSupportedPictureSizes())
        {
            CaptureMode captureMode = CaptureMode.create(CAPTURE_TYPE_STILL, minPreviewSize, size);
            if (captureMode != null) captureModes.add(captureMode);
        }
    }

    /**
     * Selects the requested or default frame capture mode.
     *
     * The @c capture parameter that specifies the requested frame capture mode is retrieved from
     * the frame capture parameters. If the list of capture modes contains an item with matching
     * mode id and the specified capture type that capture mode is returned. If no mode id is
     * specified or the specified capture mode is not supported, defaultCaptureMode() is called to
     * get the default capture mode for the selected capture type.
     *
     * @param   captureType     frame capture type for which to select frame capture mode
     * @param   captureParams   optional frame capture parameters
     *
     * @return  selected frame capture mode, or @c null if no frame capture mode is available
     */
    @Nullable
    private CaptureMode selectCaptureMode(int captureType, Map<String, Object> captureParams)
    {
        // Get frame capture name matching frame capture type.
        String captureName = (captureType == CAPTURE_TYPE_LIVE) ? CAPTURE_NAME_LIVE
            : ((captureType == CAPTURE_TYPE_STILL) ? CAPTURE_NAME_STILL : null);
        if (captureName == null)
        {
            setError(BridgeResult.Code.NOT_SUPPORTED, "stream_open_failure", "unsupported capture type " + captureType);
            return null;
        }

        // Retrieve mode id from parameters.
        String modeId = MapUtils.getString(captureParams, "capture", null);
        if (modeId != null)
        {
            // Find frame capture mode with matching mode id and capture type.
            modeId = modeId.trim();
            for (CaptureMode captureMode : captureModes)
            {
                if ((captureMode.captureType == captureType) && (modeId.equalsIgnoreCase(captureMode.id))) return captureMode;
            }
            setError(BridgeResult.Code.FAILURE, "stream_open_failure",
                captureName + " capture mode " + modeId + " not supported - default capture mode will be used");
            return null;
        }
        else BridgeLog.warning(TAG, "Requested capture mode not specified - default capture mode will be used");

        // Return default frame capture mode.
        return defaultCaptureMode(captureType, captureName);
    }

    /**
     * Selects the default frame capture mode for the specified frame capture type.
     *
     * The supported frame capture mode for the specified fram capture type with the largest frame 
     * width not exceeding the configured maximum value is returned.
     *
     * @param   captureType      frame capture type for which to return default frame capture mode
     * @param   captureName      name of frame capture type (for logging purposes only)
     *
     * @return  default frame capture mode, or @c null if no capture mode is available
     */
    @Nullable
    private CaptureMode defaultCaptureMode(int captureType, String captureName)
    {
        // Determine maximum allowed frame width.
        Integer maxWidth = (captureType == CAPTURE_TYPE_LIVE) ? (Integer)DEFAULT_LIVE_FRAME_WIDTH
            : ((captureType == CAPTURE_TYPE_STILL) ? (Integer)DEFAULT_STILL_FRAME_WIDTH : null);
        if (maxWidth == null)
        {
            setError(BridgeResult.Code.NOT_SUPPORTED, "stream_open_failure", "unsupported capture type " + captureType);
            return null;
        }

        // Return compatible capture mode with highest width not exceeding maximum value.
        CaptureMode selectedCaptureMode = null;
        for (CaptureMode captureMode : captureModes)
        {
            int width = captureMode.pictureSize.width;
            int highWidth = (selectedCaptureMode != null) ? selectedCaptureMode.pictureSize.width : 0;
            if ((captureMode.captureType == captureType) && ((width <= maxWidth) && (width > highWidth)))
                selectedCaptureMode = captureMode;
        }

        if (selectedCaptureMode == null) setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure",
            "no default " + captureName +  " capture mode available");
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
            "no frame decode available");
        return decodeMode;
    }

    /**
     * Waits until the camera becomes available.
     *
     * If the camera is not currently available, the current thread waits for the specified delay.
     * After each wait interval the camera status is checked again. The wait is repeated until the
     * camera becomes available or the maximum number of retries is exceeded.
     *
     * @param   stream          stream for which to check availability (always shared stream)
     *
     * @return  @c true if the stream is available, or @c false otherwise
     */
    private boolean waitForStream(Stream stream)
    {
        // If stream is not open it is closed.
        if ((cameraStatus == CameraStatus.AVAILABLE) && (stream.isOpen == false)) return true;

        // Repeatedly wait for the stream to release. wait() releases the manager monitor so other
        // synchronized methods can continue and release the stream.
        for (int i=0; i<STREAM_CHECK_MAX_RETRIES; i++)
        {
            try { wait(STREAM_CHECK_DELAY_MS); }
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
     * @param   stream          stream to open (always shared stream)
     * @param   captureParams   optional frame capture parameters
     * @param   captureMode     frame capture mode
     * @param   decodeMode      frame decode mode
     *
     * @return  stream handle if the stream is open with compatible settings, or -1 on failure
     */
    private long ensureStreamOpen(Stream stream, Map<String, Object> captureParams, CaptureMode captureMode, DecodeMode decodeMode)
    {
        // Return error result if context or camera is not available.
        if ((isCameraAvailable() == false))
        {
            setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failed", "camera not available");
            return -1;
        }

        long handle;
        if (stream.isOpen == false)
        {
            // The stream is not open so it is opened.
            if ((handle = openStream(stream, captureParams, captureMode, decodeMode)) < 0) return -1;
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
            BridgeLog.info(TAG, "Using open " + stream.name + " stream");
            handle = stream.handle;
        }
        else
        {
            // The idle stream is reconfigured for the requested capture.
            closeStream(stream);
            if ((handle = openStream(stream, captureParams, captureMode, decodeMode)) < 0) return handle;
        }

        int count = ++sharedStream.clientCount;
        BridgeLog.info(TAG, "Shared stream client registered - number of clients is now " + count);
        return handle;
    }

    /**
     * Opens the specified stream.
     *
     * The requested frame capture parameters are applied to the Android @c Camera instance. The
     * resulting preview size and pixel format are then used to calculate the callback buffer size.
     * Three callback buffers are registered to support continuous preview delivery. The callback
     * that receives incoming frames is assigned to the camera, preview capture is started, and the
     * camera is given a brief warm-up period so exposure and white-balance settings can settle
     * before frames are consumed. If successful, the specified Stream instance is initialized, and
     * a stream open event is published for live capture.
     *
     * If the application is running in emulator there is nothing to open and a random stream handle
     * is returned.
     *
     * @param   stream          stream to open (always @c sharedStream)
     * @param   captureParams   optional frame capture parameters
     * @param   captureMode     frame capture mode
     * @param   decodeMode      frame decode mode
     *
     * @return  stream handle (camera id) if the stream was successfully opened, or -1 on failure
     */
    private long openStream(Stream stream, Map<String, Object> captureParams, CaptureMode captureMode, DecodeMode decodeMode)
    {
        int handle;
        Camera.Parameters cameraParams = null;

        try
        {
            if (BuildConfig.EMULATOR_MODE) handle = (int)(Math.random()*Integer.MAX_VALUE);
            else
            {
                // Apply camera parameters.
                cameraParams = applyCaptureParams(captureParams, captureMode);
                Camera.Size previewSize = cameraParams.getPreviewSize();
                if (previewSize == null)
                {
                    setError(BridgeResult.Code.FAILURE, "stream_open_failed", "stream preview size not available");
                    return -1;
                }

                // Retrieve actual preview dimensions and calculate buffer size.
                int width = previewSize.width;
                int height = previewSize.height;
                int pixelFormat = cameraParams.getPreviewFormat();
                int bitsPerPixel = ImageFormat.getBitsPerPixel(pixelFormat);
                int bufferSize = (width*height*bitsPerPixel)/8;

                // Set listener for frame events and start video stream.
                camera.addCallbackBuffer(new byte[bufferSize]);
                camera.addCallbackBuffer(new byte[bufferSize]);
                camera.addCallbackBuffer(new byte[bufferSize]);
                camera.setPreviewCallbackWithBuffer(new LiveFrameListener());

                // Start preview.
                BridgeLog.debug(TAG, "Start video preview");
                camera.startPreview();

                // Give camera some time to warm up.
                try {if (CAMERA_WARMUP_TIME_MS > 0) Thread.sleep(CAMERA_WARMUP_TIME_MS);}
                catch (InterruptedException e) {Thread.currentThread().interrupt();}

                // All done... copy camera id to handle.
                handle = cameraId;
            }
        }
        catch (Exception e)
        {
            setError(BridgeResult.Code.FAILURE, "camera_stream_failure", e.getMessage());
            closeStream(stream);
            cameraParams = null;
            handle = -1;
        }

        // Initialize stream.
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
     * Applies the requested frame capture parameters to the Android @c Camera instance.
     *
     * The current camera parameters are retrieved, updated with the selected preview size, picture
     * size, and supported capture settings, and then pushed back to the camera. The resulting
     * parameter set is returned.
     *
     * @param   captureParams   optional frame capture parameters
     * @param   captureMode     selected frame capture mode
     *
     * @return  Android @c Camera.Parameters instance containing active camera parameters
     */
    private Camera.Parameters applyCaptureParams(Map<String, Object> captureParams, CaptureMode captureMode)
    {
        BridgeLog.debug(TAG, "Apply capture parameters to camera");

        try
        {
            // Get current parameters,
            Camera.Parameters cameraParams = camera.getParameters();

            // Set preview dimensions,
            cameraParams.setPreviewSize(captureMode.previewSize.width, captureMode.previewSize.height);
            cameraParams.setPictureSize(captureMode.pictureSize.width, captureMode.pictureSize.height);

            // Set exposure settings.
            setExposure(captureParams, cameraParams);

            // Set color effect.
            setColorEffect(captureParams, cameraParams);

            // Set zoom.
            setZoom(captureParams, cameraParams);

            // Push parameters to camera.
            camera.setParameters(cameraParams);

            // If a warmup time is specified wait until it is expired.
            if (CAMERA_WARMUP_TIME_MS > 0) Thread.sleep(CAMERA_WARMUP_TIME_MS);
        }
        catch (Exception e)
        {
            BridgeLog.warning(TAG, "Failed to update camera settings", e);
        }
        return camera.getParameters();
    }

    /**
     * Applies exposure setting.
     *
     * The exposure compensation is specified by the @c exposure parameter. If the value is not
     * within the allowed range it is adjusted before it is copied to the camera parameters. If
     * the auto exposure lock feature is supported, the @c exposure-lock parameter is retrieved and
     * copied to the camera parameters.
     *
     * @param captureParams     optional frame capture parameters
     * @param cameraParams      camera parameters
     */
    private void setExposure(Map<String, Object> captureParams, @NonNull Camera.Parameters cameraParams)
    {
        // Retrieve exposure parameter from frame capture parameters.
        int exposure = MapUtils.getInt(captureParams, "exposure", 0);

        // Clamp exposure to allowed values.
        int minExposure = cameraParams.getMinExposureCompensation();
        int maxExposure = cameraParams.getMaxExposureCompensation();
        if (exposure < minExposure) exposure = minExposure;
        if (exposure > maxExposure) exposure = maxExposure;
        cameraParams.setExposureCompensation(exposure);

        // Set auto exposure lock if supported.
        if (cameraParams.isAutoExposureLockSupported())
            cameraParams.setAutoExposureLock(MapUtils.getBoolean(captureParams, "exposure-lock", false));
    }

    /**
     * Applies color effect setting.
     *
     * The color effect is specified by the @c effect parameter. If the value is not one of the
     * supported values it is not set.
     *
     * @param captureParams     optional frame capture parameters
     * @param cameraParams      camera parameters
     */
    public void setColorEffect(Map<String, Object> captureParams, @NonNull Camera.Parameters cameraParams)
    {
        // Retrieve white balance parameter from frame capture parameters.
        String colorEffect = StringUtils.normalize(MapUtils.getString(captureParams, "effect", "none"));

        // Select color effect.
        if (colorEffect != null)
        {
            String requestedColorEffect = null;
            for (String colorEffectListItem : cameraParams.getSupportedColorEffects())
            {
                if (colorEffectListItem.equalsIgnoreCase(colorEffect))
                    requestedColorEffect = colorEffectListItem;
            }
            if (requestedColorEffect != null) cameraParams.setColorEffect(requestedColorEffect);
        }
    }

    /**
     * Applies zoom setting.
     *
     * The zoom percentage is specified by the @c zoom parameter. The zoom value in the camera
     * parameters is not the actual zoom percentage, but the index of the element in the list of
     * supported zoom values. The index of the values closest to the specified percentage is
     * selected.
     *
     * @param captureParams     optional frame capture parameters
     * @param cameraParams      camera parameters
     */
    public void setZoom(Map<String, Object> captureParams, @NonNull Camera.Parameters cameraParams)
    {
        if (cameraParams.isZoomSupported())
        {
            // Retrieve exposure parameter from frame capture parameters.
            Object zoom = MapUtils.get(captureParams, "zoom");
            int zoomIndex = -1;
            List<Integer> zoomRatios = cameraParams.getZoomRatios();
            if (zoom != null)
            {
                int requestedZoom = ((Number)zoom).intValue();
                for (int i=0; i<zoomRatios.size(); i++)
                {
                    if (requestedZoom <= zoomRatios.get(i))
                    {
                        zoomIndex = i;
                        break;
                    }
                }
                if (zoomIndex == -1) zoomIndex = zoomRatios.size() - 1;
            }
            else zoomIndex = 0;
            cameraParams.setZoom(zoomIndex);
        }
        else BridgeLog.warning(TAG, "Camera does not supported changing zoom value");
    }

    /**
     * Unregisters client and closes stream if all clients have diconnected.
     *
     * The active video-client count is decremented. When there are no more clients closeStream() is
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
     * If the application is not running in emulator mode the frame preview is stopped, and the
     * specified Stream instance is released. A stream release event is published before the stream
     * is closed since after closing the stream the stream details will no longer be available.
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

        // Stop preview.
        if (BuildConfig.EMULATOR_MODE == false)
        {
            try
            {
                BridgeLog.debug(TAG, "Stop video preview");
                camera.stopPreview();
                camera.setPreviewCallbackWithBuffer(null);
            }
            catch (RuntimeException e)
            {
                BridgeLog.error(TAG, "Failed to release " + stream.name + " stream", e);
                return;
            }
        }

        // Close the stream.
        BridgeLog.info(TAG, "Closed " + stream.name + " stream with handle " + stream.handle);
        stream.release("ready");
        cameraStatus = CameraStatus.AVAILABLE;
        notifyAll();
    }

    /**
     * Releases camera resources.
     *
     * The camera is released and the camera status is reset.
     */
    private synchronized void releaseCamera()
    {
        BridgeLog.info(TAG, "Shut down Android camera manager");

        try
        {
            if (camera != null)
            {
                camera.setPreviewCallback(null);
                camera.release();
            }
        }
        catch (Exception ignored) {}
        finally
        {
            cameraStatus = CameraStatus.STOPPED;
            cameraId = -1;
            camera = null;
        }
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
     * Retrieves a still image frame from the frame buffer.
     *
     * If the referenced byte array is not empty a new Image instance is returned from the data in
     * the byte array and the specified meta data..
     *
     * @param   frameBuffer     frame buffer from which to retrieve image data
     * @param   decodeMode      frame decode mode
     * @param   timestamp       image timestamp
     * @param   metadata        optional image meta data
     *
     * @return  Image instance on success, @c null on failure
     */
    @Nullable
    private Image getStillImageFromBuffer(@NonNull AtomicReference<byte[]> frameBuffer, DecodeMode decodeMode, long timestamp, Map<String, Object> metadata)
    {
        // Get bytes from frame buffer.
        byte[] bytes = frameBuffer.get();
        if ((bytes == null) || (bytes.length == 0))
        {
            setError(BridgeResult.Code.FAILURE, "image_capture_failed", "no data in frame buffer");
            return null;
        }

        // Create image from byte array.
        Image.Type imageType = decodeMode.imageType;
        int width = decodeMode.width;
        int height = decodeMode.height;
        return Image.create(bytes, imageType, width, height, timestamp, metadata);
    }

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
     * Retrieves a data map containing the active camera parameters.
     *
     * @return  Java @c Map instance containing active camera parameters
     */
    @NonNull
    private Map<String, Object> getActiveCameraParams()
    {
        Map<String, Object> data = MapUtils.createMap();
        Camera.Parameters params = (camera != null) ? camera.getParameters() : null;
        data.put("warmupTime", CAMERA_WARMUP_TIME_MS);
        if (params != null)
        {
            data.put("exposure", StringUtils.unknownIfBlank(params.getExposureCompensation()));
            data.put("exposure-lock", StringUtils.unknownIfBlank(params.getAutoExposureLock()));
            data.put("whitebalance", StringUtils.unknownIfBlank(params.getWhiteBalance()));
            data.put("whitebalance-lock", StringUtils.unknownIfBlank(params.getAutoWhiteBalanceLock()));
            data.put("effect", StringUtils.unknownIfBlank(params.getColorEffect()));
            data.put("zoom", StringUtils.unknownIfBlank(params.getZoom()));
        }
        return data;
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
        if (stream.isOpen)
        {
            data.put("status", stream.status);
            data.put("handle", stream.handle);
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

        int liveCaptureModeCount = 0;
        List<String> liveCaptureModeData = new ArrayList<>();
        for (CaptureMode captureMode : captureModes)
        {
            if (captureMode.captureType == CAPTURE_TYPE_LIVE)
            {
                String modeId = (captureMode.id != null) ? captureMode.id : "capture" + liveCaptureModeCount++;
                liveCaptureModeData.add(modeId + " (" + captureMode.toString() + ")");
            }
        }
        data.put("liveCaptureModes", liveCaptureModeData);

        int stillCaptureModeCount = 0;
        List<String> stillCaptureModeData = new ArrayList<>();
        for (CaptureMode captureMode : captureModes)
        {
            if (captureMode.captureType == CAPTURE_TYPE_STILL)
            {
                String modeId = (captureMode.id != null) ? captureMode.id : "capture" + stillCaptureModeCount++;
                stillCaptureModeData.add(modeId + " (" + captureMode.toString() + ")");
            }
        }
        data.put("stillCapturesMode", stillCaptureModeData);

        return data;
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
     * Listener for live frame events from Android @c Camera API.
     *
     * This class extends the Android SDK @c Camera.PreviewCallback class. It implements the
     * callback method that handles an incoming live frame.
     */
    private class LiveFrameListener implements Camera.PreviewCallback
    {
        /**
         * Callback method handling incoming live frames.
         *
         * The received frame data is copied to the video frame buffer owned by the Stream instance
         * corresponding to the shared stream. The callback buffer is reassigned to the camera.
         *
         * @param   bytes       byte array containing frame data
         * @param   camera      Android @c Camera API instance
         */
        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera)
        {
            try
            {
                DecodeMode decodeMode = sharedStream.decodeMode;
                if ((sharedStream.isOpen) && (decodeMode != null))
                {
                    sharedStream.frameCount.getAndIncrement();

                    if ((bytes != null) && (bytes.length != 0))
                    {
                        sharedStream.frameBuffer.setData(bytes.clone(), decodeMode.imageType,
                        decodeMode.width, decodeMode.height);
                    }

                }
            }
            finally
            {
                camera.addCallbackBuffer(bytes);
            }
        }
    }

    /**
     * Listener for still frame events from Android @c Camera API.
     *
     * This class extends the Android SDK @c Camera.PreviewCallback class. It implements the
     * callback method that handles an incoming still frame.
     */
    private static class StillFrameListener implements Camera.PictureCallback
    {
        /** Buffer for returning frame data. */
        private final AtomicReference<byte[]> frameBuffer;

        /** Latch to be released if frame is received. */
        private final CountDownLatch frameReady;

        /**
         * Constructs a new StillFrameListener instance.
         *
         * The supplied frame buffer is used to return the captured image bytes, and the supplied
         * latch is released when the picture callback is invoked.
         *
         * @param   frameBuffer     buffer used to return captured image data
         * @param   frameReady      latch released when image data has been received
         */
        private StillFrameListener(AtomicReference<byte[]> frameBuffer, CountDownLatch frameReady)
        {
            this.frameBuffer = frameBuffer;
            this.frameReady = frameReady;
        }

        /**
         * Callback method handling received still image frame.
         *
         * The received image bytes are copied to the return buffer and the waiting thread is
         * released by counting down the latch.
         *
         * @param   bytes       byte array containing captured still-image data
         * @param   camera      Android @c Camera API instance
         */
        @Override
        public void onPictureTaken (byte[] bytes, Camera camera)
        {
            frameBuffer.set((bytes != null) ? bytes.clone() : null);
            frameReady.countDown();
        }
    }

    /**
     * Helper class for storing stream properties.
     *
     * This class manages the stream properties and owns the CaptureMode, DecodeMode and
     * VideoFrameBuffer instances.
     */
    private static class Stream
    {
        /** Human-readable stream name. */
        private final String name;

        /**
         * Stream handle.
         *
         * The Android camera does not actually have a device handle, the handle specified by this
         * member variable is just the index of the camera in the list of available cameras. With
         * only one available camera this means a valid handle will always have value 0.
         */
        private int handle = -1;

        /** Frame capture mode. */
        CaptureMode captureMode = null;

        /** Frame decode mode. */
        DecodeMode decodeMode = null;

        /** Video frame buffer. */
        private final VideoFrameBuffer frameBuffer = new VideoFrameBuffer();

        /** Counter for number of received frames. */
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
         *
         * @param   name        human-readable stream name
         */
        private Stream(String name)
        {
            this.name = name;
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
         * @param   captureMode     frame capture mode
         * @param   decodeMode      frame decode mode
         * @param   cameraParams    active camera parameters
         *
         * @return  @c true if the stream is considered open, or @c false otherwise
         */
        boolean init(int handle, CaptureMode captureMode, DecodeMode decodeMode, Camera.Parameters cameraParams)
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
            handle = -1;
            captureMode = null;
            decodeMode = null;

            // Clear frame buffer.
            frameBuffer.clear();

            // Reset counters.
            frameCount.set(0);
            clientCount = 0;

            // Set status info.
            openTime = 0L;
            isOpen = false;
            this.status = status;
        }

        /**
         * Builds a metadata map containing the current stream capture settings.
         *
         * When the stream is open, the returned map includes the selected capture mode, stored
         * frame size, and when available selected camera parameters such as focal length, exposure,
         * zoom, white balance, color effect, and scene mode.
         *
         * @param cameraParams      active camera parameters
         *
         * @return  Java @c Map instance containing capture metadata
         */
        @NonNull
        private Map<String, Object> toMetaData(Camera.Parameters cameraParams)
        {
            Map<String, Object> data = MapUtils.createMap("camera", CAMERA_NAME);
            if (isOpen)
            {
                data.put("Camera-CaptureMode", StringUtils.unknownIfBlank(captureMode.id));
                data.put("Camera-FrameSize", decodeMode.width + "x" + decodeMode.height);
                if (cameraParams != null)
                {
                    data.put("Camera-FocalLength", cameraParams.getFocalLength());
                    data.put("Camera-Exposure", cameraParams.getExposureCompensation());
                    data.put("Camera-WhiteBalance", cameraParams.getWhiteBalance());
                    data.put("Camera-ColorEffect", cameraParams.getColorEffect());
                    data.put("Camera-SceneMode", cameraParams.getSceneMode());
                    data.put("Camera-Effect", cameraParams.getColorEffect());
                    data.put("Camera-HorizontalAngle", cameraParams.getHorizontalViewAngle());
                    data.put("Camera-VerticalAngle", cameraParams.getVerticalViewAngle());
                    data.put("Camera-Zoom", cameraParams.getZoom());
                }
            }
            return data;
        }
    }

    /**
     * Helper class for storing frame capture mode properties.
     *
     * Each frame capture mode is a combination of a frame capture type (live or still capture), an
     * image frame size that specifies the dimensions of the actual streamed video image, captured
     * snapshot image, or captured still image, and a preview frame size. For live capture modes
     * the image size is always the same as the preview size. The name of the video resolution is
     * set as the mode id. This mode id is not unique, the same mode id may exist for different
     * capture types.
     *
     * Instances of this class should always be constructed using the create() factory method, since
     * that method does not only create an instance but also validates it and returns @c null if
     * invalid.
     */
    private static class CaptureMode
    {
        /** Frame capture mode id. */
        private final String id;

        /** Frame capture type (live or still capture) for which this mode can be used. */
        private final int captureType;

        /** Preview frame size. */
        private final Camera.Size previewSize;

        /**
         * Picture frame size.
         *
         * For live capture modes the picture frame size is equal to the preview frame size.
         */
        private final Camera.Size pictureSize;

        /**
         * Constructs a new CaptureMode instance.
         *
         * The mode id is derived from the picture frame resolution. The capture type, preview size,
         * and picture size are copied to member variables.
         *
         * @param   captureType     frame capture type (live or still capture)
         * @param   previewSize     preview frame size
         * @param   pictureSize     picture frame size
         */
        private CaptureMode(int captureType, @NonNull Camera.Size previewSize, @NonNull Camera.Size pictureSize)
        {
            this.id = CameraResolution.get(pictureSize.width, pictureSize.height);
            this.captureType = captureType;
            this.previewSize = previewSize;
            this.pictureSize = pictureSize;
        }

        /**
         * Creates and validates a new frame capture mode.
         *
         * A new @c CaptureMode instance is returned only if a valid mode id can be derived from the
         * supplied picture size.
         *
         * @param   captureType         frame capture type for which this mode can be used
         * @param   previewSize         preview frame size
         * @param   pictureSize         picture frame size
         *
         * @return  CaptureMode instance, or @c null if the frame capture mode is not valid
         */
        @Nullable
        private static CaptureMode create(int captureType, @NonNull Camera.Size previewSize, @NonNull Camera.Size pictureSize)
        {
            CaptureMode captureMode = new CaptureMode(captureType, previewSize, pictureSize);
            return (StringUtils.isBlank(captureMode.id) == false) ? captureMode : null;
        }

        /**
         * Checks whether the specified capture mode is compatible with the current mode.
         *
         * Capture modes are considered compatible when they have the same capture type and mode
         * id.
         *
         * @param   captureMode     frame capture mode to compare with
         *
         * @return  @c true if the capture mode is compatible, or @c false otherwise
         */
        private boolean match(@NonNull CaptureMode captureMode)
        {
            return ((captureType == captureMode.captureType)
                && (id.equalsIgnoreCase(captureMode.id)));
        }

        /**
         * Returns a human-readable description of the capture mode.
         *
         * The description includes the picture resolution and the effective encoding...
         *
         * @return  string representation of the frame capture mode
         */
        @NonNull
        public String toString()
        {
            String resolution = String.format("%dx%d", pictureSize.width, pictureSize.height);
            switch (captureType)
            {
                case CAPTURE_TYPE_LIVE:
                    return resolution + ", YUV encoding";
                case CAPTURE_TYPE_STILL:
                    return resolution + ", JPG encoding";
                default:
                    return resolution;
            }
        }
    }

    /**
     * Helper class for storing frame decode mode properties.
     *
     * The frame decode mode specifies the size and format in which frames are stored in the frame
     * buffer. Since the Sanbot camera only captures color images the mode id is always
     * @c DECODE_COLOR. The image type is always @c YUV_NV21.
     *
     * Instances of this class should always be constructed using the create() factory method, since
     * that method does not only create an instance but also validates it and returns @c null if
     * invalid.
     */
    private static class DecodeMode
    {
        /** Color image decode mode. */
        private static final int DECODE_COLOR = 3;

        /** Decode mode id. */
        private final int id;

        /** Width of picture frame. */
        private final int width;

        /** Height of picture frame. */
        private final int height;

        /** Image type. */
        private final Image.Type imageType;

        /**
         * Constructs a new DecodeMode instance.
         *
         * The decode mode is derived from the selected capture mode. For live capture the stored
         * image type is @c YUV_NV21; for still capture it is @c JPEG. The stored width and height
         * match the selected picture size.
         *
         * @param   captureMode            selected capture mode
         * @param   ignoredParams          optional frame capture parameters (unused)
         */
        private DecodeMode(@NonNull CaptureMode captureMode, Map<String, Object> ignoredParams)
        {
            id = DECODE_COLOR;
            width = captureMode.pictureSize.width;
            height = captureMode.pictureSize.height;
            imageType = (captureMode.captureType == CAPTURE_TYPE_LIVE) ? Image.Type.YUV_NV21 : Image.Type.JPEG;
        }

        /**
         * Creates and validates a new decode mode.
         *
         * A decode mode is considered valid only when a non-null image type can be derived from the
         * supplied capture mode.
         *
         * @param   captureMode     capture mode
         * @param   captureParams   optional frame capture parameters (unused)
         *
         * @return  new @c DecodeMode instance, or @c null if the decode mode is invalid
         */
        @Nullable
        private static DecodeMode create(CaptureMode captureMode, Map<String, Object> captureParams)
        {
            if (captureMode == null) return null;

            DecodeMode decodeMode = new DecodeMode(captureMode, captureParams);
            return (decodeMode.imageType != null) ? decodeMode : null;
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
    }
}
