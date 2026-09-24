/**
 * @file        BridgeCameraUnit.java
 * @brief       Declares abstract BridgeCameraUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;

import com.fbcti.sanbot.bridge.R;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.MediaResult;
import com.fbcti.sanbot.bridge.robot.camera.AndroidCameraManager;
import com.fbcti.sanbot.bridge.robot.camera.OrbbecCameraManager;
import com.fbcti.sanbot.bridge.robot.camera.SanbotCameraManager;
import com.fbcti.sanbot.bridge.robot.camera.interfaces.BridgeCameraManager;
import com.fbcti.sanbot.bridge.robot.media.Image;
import com.fbcti.sanbot.bridge.util.JsonUtils;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.fbcti.sanbot.bridge.util.ValueUtils;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstract base class for classes that manage media operations.
 *
 * This base class implements the shared camera features exposed by the specific camera units,
 * including live video streaming wrappers, snapshot image capture wrappers, still image capture
 * wrappers, and common helpers for camera stream responses. It owns the camera manager class that
 * implements the BridgeCameraManager interface.
 *
 * @version     1.0.001
 * @date        11 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public abstract class BridgeCameraUnit extends BridgeUnit
{
    /** Source label used for log messages. */
//    private static final String TAG = BridgeUnit.class.getSimpleName();

    /** List of supported camera names and aliases. */
    public static final String[][] CAMERA_NAMES  = new String[][] {
        { AndroidCameraManager.CAMERA_NAME, AndroidCameraManager.CAMERA_ALIAS },
        { OrbbecCameraManager.CAMERA_NAME, OrbbecCameraManager.CAMERA_ALIAS },
        { SanbotCameraManager.CAMERA_NAME, SanbotCameraManager.CAMERA_ALIAS }
    };

    /** Name of default camera. */
    public static final String DEFAULT_CAMERA_NAME = SanbotCameraManager.CAMERA_NAME;

    /** Maximum stream frame rate. */
    protected static final int MAX_STREAM_FPS = 30;

    /** Default stream frame rate. */
    protected static final int DEFAULT_STREAM_FPS = 25;

    /** Default image type. */
    protected static final Image.Type DEFAULT_IMAGE_FORMAT = Image.Type.JPEG;

    /** Minimum image compression quality. */
    protected static final int MIN_MJPEG_QUALITY = Image.MIN_JPEG_QUALITY;

    /** Maximum image compression quality. */
    protected static final int MAX_MJPEG_QUALITY = Image.MAX_JPEG_QUALITY;

    /** Default compression quality used for JPEG images. */
    protected static final int DEFAULT_JPEG_QUALITY = Image.DEFAULT_JPEG_QUALITY;

    /** Default compression quality used for MJPEG video frames. */
    protected static final int DEFAULT_MJPEG_QUALITY = 40;

    /** Boundary token separating JPEG frames in multipart MJPEG streams. */
    protected static final String MJPEG_BOUNDARY = "frame";

    /** Android application context. */
    protected final Context context;

    /** Camera manager. */
    protected BridgeCameraManager cameraManager = null;

    /** Persistent bridge configuration object used for saving settings. */
    private final BridgeConfig config;

    /** Name of the camera managed by the camera unit. */
    private final String cameraName;

    /** Active camera settings updated through the camera config request. */
    private final Map<String, Object> cameraSettings = new LinkedHashMap<>();

    /** Pre-defined image used if capturing image is not supported or failed. */
    public final Image predefinedImage;

    /** Lock protecting short-lived camera state transitions. */
    private final Object stateLock = new Object();

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs the base portion of a camera unit.
     *
     * The base class constructor is called to copy the callback host to a member variable, The
     * application context is validated and copied to a member variable, the persistent bridge
     * bridge configuration, camera name, and specific camera settings are copied to member
     * variables. A predefined image used when the application is running in similator mode is
     * loaded
     *
     * @param   context         Android application context
     * @param   config          persistent bridge configuration containing camera settings
     * @param   eventHost       callback host to which to forward bridge unit events
     * @param   cameraName      name of camera managed by the camera unit
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public BridgeCameraUnit(Context context, @NonNull BridgeConfig config, BridgeEventHost eventHost, String cameraName)
    {
        super(eventHost);

        // Set Android application context.
        if (context == null) throw new IllegalArgumentException("context may not be null");
        Context applicationContext = context.getApplicationContext();
        this.context = (applicationContext != null) ? applicationContext : context;

        // Copy camera name and persisten bridge configuration,
        this.config = config;
        this.cameraName = cameraName;

        // Copy camera settings.
        MapUtils.copyData(config.getCameraParams(cameraName), cameraSettings);

        // Load predefined image.
        predefinedImage = new Image(context.getResources(), R.drawable.camera_capture);
    }

    /**
     * @name Common Camera Unit Operations
     *
     * @{
     */

    /**
     * Updates camera settings.
     *
     * @param   active          make current camera the active camera
     * @param   reset           resets settings for current camera to defaults
     * @param   reload          reloads settings for current camera from configuration file
     * @param   save            save updated camera settings in configuration file
     * @param   params          data map containing camera parameters
     *
     * If @p active is @c true, the  camera is set as active camera. If @p reset or @p reload is
     * @c true, the parameters for the camera are reset to their default values or reload from the
     * configuration file, ignoring all other parameters in the @p params map. In all other cases
     * the parameters in the @p params map are applied. The @p reset and @p reload flags are
     * mutually exclusive; only first flag set to @c true is applied.
     *
     * If @p save is @c true, the reset, reloaded or updated settings are made persistent by writing
     * them to the configuration file. If @p save is @c false or not specified, the settings will be
     * semi-persistent and only remain active until they are updated again or the bridge service is
     * restarted.
     *
     * @return  DataResult instance class containing operation result
     */
    public DataResult updateSettings(boolean active, boolean reset, boolean reload, boolean save, Map<String, Object> params)
    {
        boolean updated = false;
        String sensorName = MapUtils.getString(params, "sensor", null);
        Map<String, Object> storedParams = new LinkedHashMap<>();
        if (params != null)
        {
            storedParams.putAll(params);
            storedParams.remove("sensor");
        }

        if (active)
        {
            // Set the current camera as active camera in persistent configuration.
            config.setCameraName(cameraName);
            updated = true;
        }

        if (reset)
        {
            // Reset current camera or camera-sensor settings to defaults in persistent
            // configuration, copy camera-level settings to the active settings, and save if
            // required.
            if (sensorName != null) config.resetCameraSensorParams(cameraName, sensorName);
            else config.resetCameraParams(cameraName);
            MapUtils.copyData(config.getCameraParams(cameraName), cameraSettings);
            if (save && (config.save() == false))
                return DataResult.failure("camera settings reset but save failed");
            return DataResult.success("camera settings reset", buildConfigData());
        }

        if (reload)
        {
            // Reload current camera or camera-sensor settings from persistent configuration, copy
            // camera-level settings to the active settings, and save if required.
            if (sensorName != null) config.reloadCameraSensorParams(cameraName, sensorName);
            else config.reloadCameraParams(cameraName);
            MapUtils.copyData(config.getCameraParams(cameraName), cameraSettings);
            if (save && (config.save() == false))
                return DataResult.failure("camera settings reloaded but save failed");
            return DataResult.success("camera settings reloaded", buildConfigData());
        }

        if (storedParams.isEmpty() == false)
        {
            // Update camera or camera-sensor parameters in persistent configuration and copy
            // camera-level settings to the active settings.
            boolean changed = (sensorName != null)
                ? config.setCameraSensorParams(cameraName, sensorName, storedParams)
                : config.setCameraParams(cameraName, storedParams);
            if (changed)
            {
                MapUtils.copyData(config.getCameraParams(cameraName), cameraSettings);
                updated = true;
            }
        }

        // Save settings if required.
        if (updated == false) return DataResult.success("settings were not changed");
        if (save && (config.save() == false))
            return DataResult.failure("camera settings updated but save failed", buildConfigData());
        return DataResult.success("camera settings updated", buildConfigData());
    }

    /**
     * Resets ths camera.
     *
     * The camera manager is reset, terminating all active client sessions.
     *
     * @return  DataResult instance class containing operation result
     */
    public synchronized DataResult reset()
    {
        return cameraManager.reset();
    }

    /**
     * Writes MJPEG video data to the output stream.
     *
     * If the camera manager is available, the @c openVideoStream() method implemented by the camera
     * manager class is called to start video streaming. While the output stream and client socket
     * are open, the method continuously retrieves the latest video frame from the camera manager
     * and writes encoded MJPEG frames to the HTTP output stream. If the socket closes, i.e. the
     * client disconnects from the stream, the client is unregistered. The camera manager is
     * responsible for closing the stream if no more clients are connected.
     *
     * @param   outputStream    output stream to write MJPEG data to
     * @param   socket          active client socket
     * @param   params          optional camera and process parameters
     *
     * The number of frames per seconds that are sent to the client is limited by the @c fps
     * parameter. If more frames are received (i.e. the camera frame rate has a higher value) excess
     * frames are dropped.
     *
     * @throws  IOException     thrown when writing the stream fails
     */
    public void writeVideoStream(OutputStream outputStream, Socket socket, Map<String, Object> params) throws IOException
    {
        // Create a copy of the camera manager. Since this is a long-running function that can not
        // be fully synchronized, there is a small risk the actual camera manager is nulled by the
        // shutdown() method while this method still tries to use it.
        BridgeCameraManager cameraManager = this.cameraManager;
        Map<String, Object> effectiveParams = getEffectiveParams(params);
        synchronized (stateLock)
        {
            // Return error response if camera manager is not available.
            if (cameraManager == null)
            {
                errorHtml(outputStream, BridgeResult.Code.NOT_READY, "Orbbec camera not available");
                return;
            }

            // Open video stream if not already open. Return error response on failure.
            if (cameraManager.openVideoStream(effectiveParams) < 0)
            {
                errorHtml(outputStream, cameraManager.getError());
                return;
            }
        }

        // Get video frame process parameters. The frame rate is converted to a frame interval.
        int fps = getIntegerParam(effectiveParams, "fps", 1, MAX_STREAM_FPS, DEFAULT_STREAM_FPS);
        long frameInterval = fpsToInterval(fps);
        int quality = getIntegerParam(effectiveParams, "quality", MIN_MJPEG_QUALITY, MAX_MJPEG_QUALITY, DEFAULT_MJPEG_QUALITY);

        try
        {
            // Initialize timestamp of last sent frame.
            long[] lastSentFrameTimestamp = new long[] { -1L };

            // Start streaming.
            writeMjpegStream(outputStream, socket, new FrameBuffer() {
                @Override
                public byte[] getNextFrame()
                {
                    // Get latest video frame.
                    Image image = getLatestVideoFrame(frameInterval, lastSentFrameTimestamp);
                    if ((image == null) || (image.hasImage() == false)) return null;
                    return image.getBytes(Image.Type.JPEG, quality);
                }
            });
        }
        finally
        {
            // Release video stream and check if camera is still available.
            synchronized (stateLock)
            {
                cameraManager.releaseVideoStream();
            }
        }
    }

    /**
     * Captures a snapshot image and returns image as binary image data.
     *
     * A snapshot image is captured by calling the getSnapshotImage() method implemented by the
     * concrete subclass of this base class, and processImage() is called to process the captured
     * image (resize, mirror, flip, compress. The processed image is wrapped in a MediaResult object
     * containing binary image data.
     *
     * @param   params          optional snapshot image capture and process parameters
     *
     * @return  MediaResult instance containing snapshot image data
     */
    public synchronized MediaResult getSnapshot(Map<String, Object> params)
    {
        Image image = (unitStatus == UnitStatus.EMULATED) ? getEmulatorImage() : getSnapshotImage(params);
        if (image == null) return mediaError(DataResult.failure("video snapshot not available"));

        // Process image and return media result.
        if (image.getType() != Image.Type.RAW) processImage(image, params);
        byte[] bytes = image.getBytes();
        if (bytes == null) return mediaError(DataResult.failure(image.getError()));
        return MediaResult.success(image.getBytes(), toMimeType(image.getType().name()), image.getMetaData());
    }

    /**
     * Captures still image and returns image as Base-64 encoded data.
     *
     * A snapshot image is captured by calling the getSnapshotImage() method implemented by the
     * concrete subclass of this base class, and processImage() is called to process the captured
     * image (resize, mirror, flip, compress, etc.). The processed image is wrapped in a DataResult
     * object containing Base-64 encoded image data.
     *
     * @param   params          optional snapshot image capture and process parameters
     *
     * @return  DataResult instance specifying operation result
     *
     * If a snapshot image was retrieved the @c result property in the operation result object
     * is a JSON object that contains
     * @code{.json}
     * {
     *      "medaData": image meta data,
     *      "imageData": Base-64 encoded image data
     * }
     * @endcode
     */
    public synchronized DataResult getSnapshotBase64(Map<String, Object> params)
    {
        // Return fixed image if in emulator mode.
        if (unitStatus == UnitStatus.EMULATED)
        {
            Image image = getEmulatorImage();
            processImage(image, params);
            return DataResult.success(wrapBinaryData(image.getBytes(Image.Type.JPEG, DEFAULT_JPEG_QUALITY), MapUtils.createMap("emulated", true), "imageData", "metaData"));
        }

        // Get latest snapshot image from buffer.
        Image image = getSnapshotImage(params);
        if (image == null) return DataResult.failure("video snapshot not available");

        // Process image and return media result.
        if (image.getType() != Image.Type.RAW) processImage(image, params);
        byte[] bytes = image.getBytes();
        if (bytes == null) return DataResult.failure(image.getError());
        return DataResult.success(wrapBinaryData(bytes, image.getMetaData(), "imageData", "metaData"));
    }

    /**
     * Captures a still image and returns image as binary image data.
     *
     * A still image is captured by calling the getStillImage()method implemented by the concrete
     * subclass of this base class, and processImage() is called to process the captured image
     * (resize, mirror, flip, compress, etc.). The processed image is wrapped in a MediaResult
     * object containing binary image data.
     *
     * @param   params          optional still image capture and process parameters
     *
     * @return  MediaResult instance containing still image data
     */
    public synchronized MediaResult getImage(Map<String, Object> params)
    {
        Image image = (unitStatus == UnitStatus.EMULATED) ? getEmulatorImage() : getStillImage(params);
        if ((image == null) || (image.hasImage() == false)) return mediaError(getError());

        // Process image and return as media result.
        if (image.getType() != Image.Type.RAW) processImage(image, params);
        byte[] bytes = image.getBytes();
        if (bytes == null) return mediaError(DataResult.failure(image.getError()));
        return MediaResult.success(image.getBytes(), toMimeType(image.getType().name()), image.getMetaData());
    }

    /**
     * Captures a still image and returns image as Base-64 encoded data.
     *
     * A still image is captured by calling the getStillImage()method implemented by the concrete
     * subclass of this base class, and processImage() is called to process the captured image
     * (resize, mirror, flip, compress, etc.). The processed image is wrapped in a DataResult object
     * object containing Base-64 encoded image data.
     *
     * @param   params          optional still image capture and process parameters
     *
     * @return  DataResult instance specifying operation result
     */
    public synchronized DataResult getImageBase64(Map<String, Object> params)
    {
        // Return fixed image if in emulator mode.
        if (unitStatus == UnitStatus.EMULATED)
        {
            Image image = getEmulatorImage();
            processImage(image, params);
            return DataResult.success(wrapBinaryData(image.getBytes(Image.Type.JPEG, DEFAULT_JPEG_QUALITY), MapUtils.createMap("emulated", true), "imageData", "metaData"));
        }

        // Get image.
        Image image = getStillImage(params);
        if ((image == null) || (image.hasImage() == false)) return getError();

        // Process image and return media result.
        if (image.getType() != Image.Type.RAW) processImage(image, params);
        byte[] bytes = image.getBytes();
        if (bytes == null) return DataResult.failure(image.getError());
        return DataResult.success(wrapBinaryData(bytes, image.getMetaData(), "imageData", "metaData"));

    }

    /**
     * Returns a data map containing supported camera feature data.
     *
     * @return  DataResult instance containing operation result
     */
    public synchronized DataResult getFeatures()
    {
        return DataResult.success(cameraManager.buildFeatureData());
    }

    /**
     * Retrieves camera unit status data.
     *
     * This method calls the @c %buildStatusData() method implemented by the concrete subclass of
     * this class to build a data map cntaining the unit status data.
     *
     * @return  DataResult instance containing operation result
     */
    public synchronized DataResult getStatus()
    {
        return DataResult.success(buildStatusData());
    }

    /** @} */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Retrieves the camera name and sensor from the camera alias.
     *
     * The camera is specified either as @c @<name@>-@<sensor@> or just @c @<name@>. If the camera
     * name is valid and the specified request parameters do not contain a @c sensor parameter, a
     * @c sensor parameter is added to the parameters. If a @c sensor pameter is already spoecified,
     * the sensor specified in the camera name is ignored.
     *
     * @param   camera          string from which to retrieve camera name and optional mode
     * @param   params          flexible request parameters
     *
     * @return  extracted camera name, or @c null if camera name is invalid
     */
    @Nullable
    public static String parseCameraParams(String camera, Map<String, Object> params)
    {
        if (StringUtils.isBlank(camera)) return null;

        // Split camera alias into name and sensor.
        String[] s = camera.split("-");
        for (int i=0; i<s.length; i++) s[i] = s[i].trim();

        // Check if first part of split string is valid camera name.
        String name = validateCameraName(s[0]);
        if (name == null) return null;

        // Copy second part of split string to sensor if it exist.
        String sensor = ((s.length>1) && (StringUtils.isBlank(s[1]) == false)) ? s[1] : null;

        // Add sensor to camera parameters if available and not already present.
        if ((sensor != null) && (params != null) && (params.containsKey("sensor") == false))
            params.put("sensor", sensor);

        return name;
    }

    /**
     * Validates the specified camera name.
     *
     * The name must match either one of the actual camera names or one of the camera aliases
     * defined by @c CAMERA_NAMES.
     *
     * @param   cameraName      camera name to validate
     *
     * @return  validated camera name, or @c null if camera name is invalid
     */
    @Nullable
    public static String validateCameraName(String cameraName)
    {
        for (String[] cameraItem : CAMERA_NAMES)
        {
            if ((cameraItem[0].equalsIgnoreCase(cameraName)) || (cameraItem[1].equalsIgnoreCase(cameraName)))
                return cameraItem[0];
        }
        return null;
    }

    /***********************************************************************************************
     * PROTECTED METHODS
     **********************************************************************************************/

    /**
     * Captures a snapshot image.
     *
     * If the Sanbot camera manager is available, the @c %getSnapshotImage() method implemented by
     * the SanbotCameraManager instance is called.
     *
     * @param   params          optional snapshot image capture parameters
     *
     * @return  Image instance on success, or @c null on failure
     */
    @Nullable
    protected synchronized Image getSnapshotImage(Map<String, Object> params)
    {
        // Return error response if camera manager is not ready.
        if (cameraManager == null)
        {
            setError(BridgeResult.Code.NOT_READY, "snapshot_capture_failed", "Camera manager not ready");
            return null;
        }

        // Get snapshot image from camera manager.
        Image image = cameraManager.getSnapshotImage(getEffectiveParams(params));
        if (image == null) error = cameraManager.getError();
        return image;
    }

    /**
     * Captures a still image.
     *
     * If the Sanbot camera manager is available, the @c %getStillImage() method implemented by the
     * OrbbecCameraManager instance is called.
     *
     * @param   params          optional still image capture parameters
     *
     * @return  Image instance on success, or @c null on failure
     */
    @Nullable
    protected synchronized Image getStillImage(Map<String, Object> params)
    {
        // Return error response if camera manager is not ready.
        if (cameraManager == null)
        {
            setError(BridgeResult.Code.NOT_READY, "image_capture_failed", "Camera manager not ready");
            return null;
        }

        // Get still image from camera manager.
        Image image = cameraManager.getStillImage(getEffectiveParams(params));
        if (image == null) error = cameraManager.getError();
        return image;
    }

    /**
     * Returns latest cached frame from video frame buffer if available.
     *
     * The thread is paused for the specified time to limit the number of frames sent to the client.
     * After this pause the latest cached frame is retrieved from the video frame buffer. If the
     * frame is newer than the last sent frame the frame is returned.
     *
     * @param   interval        interval between video frames in milliseconds
     * @param   timestamp       timestamp of the last sent frame
     *
     * @return  Image instance, or @c null if newer frame is not available
     */
    @Nullable
    protected Image getLatestVideoFrame(long interval, long[] timestamp)
    {
        sleep(interval);
        Image image = (cameraManager != null) ? cameraManager.getVideoFrame() : null;

        // Return null if frame is not available or frame is not newer than last sent frame.
        if ((image == null) || (image.hasImage() == false) || (image.getTimestamp() <= timestamp[0])) return null;

        // Update time of last sent frame and return frame.
        timestamp[0] = image.getTimestamp();
        return image;
    }

    /**
     * Writes an MJPEG video stream response.
     *
     * While the output stream and socket are open, the method continuously retrieves the latest
     * video frame data from the camera stream buffer and writes the frame to the output stream.
     * Since the frame buffer only stores the latest video frame, all but the first frame received
     * in the interval are ignored.
     *
     * @param   outputStream    output stream assigned to socket
     * @param   socket          active client socket
     * @param   frameBuffer     host object providing video frames
     *
     * @throws  IOException thrown when writing to the output stream fails
     */
    protected void writeMjpegStream(OutputStream outputStream, Socket socket, FrameBuffer frameBuffer) throws IOException
    {
        writeMjpegHeaders(outputStream);
        while ((unitStatus == UnitStatus.STARTED) && (socket.isClosed() == false) && (Thread.currentThread().isInterrupted() == false))
        {
            byte[] frame = frameBuffer.getNextFrame();
            if (Thread.currentThread().isInterrupted()) break;
            if ((frame == null) || (frame.length == 0)) continue;
            writeMjpegFrame(outputStream, frame);
        }
    }

    /**
     * Processes a captured image.
     *
     * The required image processing methods are called. The Image.convert() method is called to
     * convert the image from the original format to one of the supported encoding formats.
     *
     * @param   image           Image instance containing image data to be processed
     * @param   params          optional image process parameters
     *
     * Supported image process parameters are:
     * - @c format: Image encoding format, one of ["JPEG", "PNG", "WEBP". "RAW"].
     * - @c quality: Image compression quality in range.
     * - @c mirror: If @c true, mirror image.
     * - @c flip: If @c true, flip (mirror vertically) image.
     * - @c width: Width to which to resize image.
     * - @c height: Height to which to resize image.
     *
     * All parameters are optional. Default values for @c format and @c quality is declared as
     * constants, any @c true/false switch that is not specified takes @c false as default value. If
     * @p width (@p height) equals 0, the image will be resized to be specified height (width)
     * without changing the aspect ratio. If both are 0 the image will not be resized.
     */
    protected void processImage(Image image, Map<String, Object> params)
    {
        // Get output format and check if decode mode is allowed for that format.
        Image.Type format = Image.Type.fromName(getStringParam(params, "format", DEFAULT_IMAGE_FORMAT.name()));
        if (format == null) format = DEFAULT_IMAGE_FORMAT;
        if (format == Image.Type.RAW) return;

        int quality = getIntegerParam(params, "quality", MIN_MJPEG_QUALITY, MAX_MJPEG_QUALITY, DEFAULT_JPEG_QUALITY);
        boolean mirror = getBooleanParam(params, "mirror", false);
        boolean flip = getBooleanParam(params, "flip", false);
        int width = MapUtils.getInt(params, "width", 0);
        int height = MapUtils.getInt(params, "height", 0);

        // Process image.
        if (mirror) image.mirrorHorizontal();
        if (flip) image.mirrorVertical();
        if ((width != 0) || (height != 0)) image.resize(width, height);
        image.convert(format, quality);
    }

    /**
     * Creates a JSON object containing Base-64 encoded binary data.
     *
     * @param   binaryData      binary data to be included in JSON object
     * @param   metaData        optional meta data to be added to JSON object
     * @param   binaryDataKey   key name for binary data element in JSON object
     * @param   metaDataKey     key name for meta data element in JSON object
     *
     * @return  JSON object containing Base-64 encoded data and optional meta data
     */
    protected JsonObject wrapBinaryData(byte[] binaryData, Map<String, Object> metaData, String binaryDataKey, String metaDataKey)
    {
        if (binaryDataKey == null) binaryDataKey = "binaryData";
        if (metaDataKey == null) metaDataKey = "metaData";

        JsonObject jsonObject = new JsonObject();
        if (metaData != null) jsonObject.add(metaDataKey, JsonUtils.toJsonObject(metaData));
        String base64 = (binaryData != null) ? Base64.encodeToString(binaryData, Base64.NO_WRAP) : null;
        jsonObject.addProperty(binaryDataKey, base64);
        return jsonObject;
    }

    /**
     * Returns a fixed image indicating the application is in emulator mode.
     *
     * The pre-defined image is copied and text is added to inform the application is in emulator
     * mode and no camera is available. Since this is an instance of the Image class just like an
     * image captured by a real camera, all processing actions can be performed and tested.
     *
     * @return  Image instance containing emulator image
     */
    protected Image getEmulatorImage()
    {
        Image image = predefinedImage.copy();

        String text = "I don't have a " + cameraName + " camera, I am just pretending";
        String date = StringUtils.fromDate(new Date());
        image.addText("EMULATED IMAGE...", 32, 96, "Helvetica", Typeface.BOLD, 32, Color.rgb(0, 0, 0));
        image.addText(text, 32, -64, "Helvetica", Typeface.NORMAL, 12, Color.rgb(0, 0, 0));
        image.addText(date, 32, -32, "Helvetica", Typeface.NORMAL, 12, Color.rgb(0, 0, 0));
        image.drawText();
        return image;
    }

    /**
     * Creates a MediaResult instance for a failed operation.
     *
     * The pre-defined image is copied and human-friendly text matching the specified error is
     * added. The image is wrapped in a MediaResult object with binary image data.
     *
     * @param   error           DataResult instance containing error information
     *
     * @return  MediaResult instance containing error image
     */
    protected MediaResult mediaError(DataResult error)
    {
        Image image = predefinedImage.copy();

        if (error == null) error = DataResult.failure();

        String text = (StringUtils.isBlank(error.getData()) == false) ? error.getData() : null;
        if (text == null)
        {
            switch (error.getCode())
            {
                case NOT_READY:
                    text = "The camera is not ready to take a picture";
                    break;
                case NOT_AVAILABLE:
                    text = "A required resource if not available";
                    break;
                case NOT_SUPPORTED:
                    text = "The requested operation is not supported by the camera";
                    break;
                default:
                    text = "Something went wrong...";
                    break;
            }
        }
        String date = StringUtils.fromDate(new Date());
        image.addText("OOPS...", 32, 96, "Helvetica", Typeface.BOLD, 48, Color.rgb(0, 0, 0));
        image.addText(text, 32, -64, "Helvetica", Typeface.NORMAL, 12, Color.rgb(0, 0, 0));
        image.addText(date, 32, -32, "Helvetica", Typeface.NORMAL, 12, Color.rgb(0, 0, 0));
        image.drawText();

        byte[] bytes = image.getBytes(Image.Type.JPEG, DEFAULT_JPEG_QUALITY);
        if (bytes == null) return mediaError(DataResult.failure(image.getError()));
        return MediaResult.success(image.getBytes(Image.Type.JPEG, DEFAULT_JPEG_QUALITY), toMimeType(Image.Type.JPEG.name()));
    }

    /**
     * Returns active camera settings merged with request-specific overrides.
     *
     * @param   params          request-specific camera parameters
     *
     * @return  Java @c Map instance containing merged camera parameters
     */
    protected Map<String, Object> getEffectiveParams(Map<String, Object> params)
    {
        Map<String, Object> effectiveParams = new LinkedHashMap<>(cameraSettings);
        String sensorName = MapUtils.getString(params, "sensor", null);
        if (sensorName != null) effectiveParams.putAll(config.getCameraSensorParams(cameraName, sensorName));
        if (params != null) effectiveParams.putAll(params);
        return effectiveParams;
    }

    /**
     * Returns value of parameter with specified name.
     *
     * If the parameter with the specified name exists in the request-specific camera parameters it
     * is returned. If not, but it does exist in the active camera settings, that value is returned
     * instead. If not specified in the camera settings either, the method returns @c null.
     *
     * @param   params          request-specific camera parameters
     * @param   name            name of parameter to return
     *
     * @return  parameter value as Java @c Object, or @c null if not specified
     */
    protected Object getParam(Map<String, Object> params, String name)
    {
        Object value = MapUtils.get(params, name);
        if (value != null) return value;

        String sensorName = MapUtils.getString(params, "sensor", null);
        if (sensorName != null)
        {
            Map<String, Object> sensorParams = config.getCameraSensorParams(cameraName, sensorName);
            if (sensorParams.containsKey(name)) return sensorParams.get(name);
        }
        return (cameraSettings.containsKey(name)) ? cameraSettings.get(name) : null;
    }

    /**
     * Returns value of string parameter with specified name.
     *
     * The getParam() method is called to ensure the parameter is retrieved from the active camera
     * settings if not specified in the request-specific camera parameters.
     *
     * @param   params          request-specific camera parameters
     * @param   name            name of parameter to return
     * @param   defaultValue    default value to return if parameter is unspecified or invalid
     *
     * @return  string value, or @c null if unspecified or invalid
     */
    protected String getStringParam(Map<String, Object> params, String name, String defaultValue)
    {
        Object o = getParam(params, name);
        if (o == null) return defaultValue;
        String s = o.toString().trim();
        return (s.isEmpty() == false) ? s : defaultValue;
    }

    /**
     * Returns value of bounded integer parameter with specified name.
     *
     * The getParam() method is called to ensure the parameter is retrieved from the active camera
     * settings if not specified in the request-specific camera parameters.
     *
     * @param   params          request-specific camera parameters
     * @param   name            name of parameter to return
     * @param   minValue        minimum accepted value
     * @param   maxValue        maximum accepted value
     * @param   defaultValue    default value to return if parameter is unspecified or invalid
     *
     * @return  validated integer value, or default value if parameter is unspecified or invalid
     */
    protected int getIntegerParam(Map<String, Object> params, String name, int minValue, int maxValue, int defaultValue)
    {
        return ValueUtils.toBoundInteger(getParam(params, name), defaultValue, minValue, maxValue);
    }

    /**
     * Returns value of boolean parameter with specified name.
     *
     * The getParam() method is called to ensure the parameter is retrieved from the active camera
     * settings if not specified in the request-specific camera parameters.
     *
     * @param   params          request-specific camera parameters
     * @param   name            name of parameter to return
     * @param   defaultValue    default value to return if unspecified or invalid
     *
     * @return  boolean value, or default value if parameter is unspecified or invalid
     */
    protected boolean getBooleanParam(Map<String, Object> params, String name, boolean defaultValue)
    {
        Boolean b = ValueUtils.toBoolean(getParam(params, name));
        return (b != null) ? b : defaultValue;
    }

    /**
     * Pauses the thread for the specified time.
     *
     * @param   time time to pause in milliseconds
     */
    protected void sleep(long time)
    {
        try { Thread.sleep(time); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Converts frame rate to frame interval in milliseconds.
     *
     * @param   fps             frame rate in frames per second
     *
     * @return  frame interval in milliseconds
     */
    protected long fpsToInterval(int fps)
    {
        return Math.max(1L, Math.round(1000.0/fps));
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Writes MJPEG response headers to the output stream.
     *
     * @param   outputStream    output stream to write MJPEG response headers to
     *
     * @throws  IOException thrown if MJPEG response headers can not be written
     */
    private void writeMjpegHeaders(@NonNull OutputStream outputStream) throws IOException
    {
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 200 OK\r\n");
        headers.append("Content-Type: multipart/x-mixed-replace; boundary=").append(MJPEG_BOUNDARY).append("\r\n");
        headers.append("Cache-Control: no-cache\r\n");
        headers.append("Pragma: no-cache\r\n");
        headers.append("Connection: close\r\n");
        headers.append("Access-Control-Allow-Origin: *\r\n");
        headers.append("Access-Control-Allow-Headers: Content-Type, X-API-Key, X-Request-Id\r\nAccess-Control-Allow-Private-Network: true\r\n");
        headers.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n\r\n");
        outputStream.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    /**
     * Writes a single MJPEG frame to the output stream.
     *
     * @param   outputStream    output stream to write MJPEG response headers to
     * @param   frame           binary frame data
     *
     * @throws  IOException thrown if frame can not be written
     */
    private void writeMjpegFrame(@NonNull OutputStream outputStream, @NonNull byte[] frame) throws IOException
    {
        StringBuilder partHeaders = new StringBuilder();
        partHeaders.append("--").append(MJPEG_BOUNDARY).append("\r\n");
        partHeaders.append("Content-Type: image/jpeg\r\n");
        partHeaders.append("Content-Length: ").append(frame.length).append("\r\n\r\n");
        outputStream.write(partHeaders.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.write(frame);
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    /**
     * Returns a data map containing camera settings.
     *
     * @return  @c Java map instance containing settings
     */
    @NonNull
    private Map<String, Object> buildConfigData()
    {
        Map<String, Object> data = MapUtils.createMap("camera", cameraName);
        data.put("isActive", cameraName.equals(config.getCameraName()));
        data.put("settings", config.getCameraParams(cameraName));
        return data;
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Defines video stream frame buffer callback contract.
     */
    protected interface FrameBuffer
    {
        /**
         * Returns the next video frame.
         *
         * @return  next video frame as array of bytes
         */
        byte[] getNextFrame();
    }
}