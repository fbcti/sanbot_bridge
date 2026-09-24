/**
 * @file        SanbotCameraManager.java
 * @brief       Implements SanbotCameraManager class.
 */
package com.fbcti.sanbot.bridge.robot.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.camera.interfaces.BridgeAudioManager;
import com.fbcti.sanbot.bridge.robot.camera.interfaces.BridgeCameraManager;
import com.fbcti.sanbot.bridge.robot.media.AudioFrameBuffer;
import com.fbcti.sanbot.bridge.robot.media.VideoFrameBuffer;
import com.fbcti.sanbot.bridge.robot.media.FaceCaptureBuffer;
import com.fbcti.sanbot.bridge.transport.BridgeEvent;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.robot.media.Image;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.fbcti.sanbot.bridge.util.ValueUtils;
import com.google.gson.JsonObject;
import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.FaceRecognizeBean;
import com.sanbot.opensdk.function.beans.StreamOption;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.interfaces.media.FaceRecognizeListener;
import com.sanbot.opensdk.function.unit.interfaces.media.MediaStreamListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the Sanbot camera.
 *
 * This class implements the BridgeCameraManager and BridgeAudioManager interfaces to export the
 * camera and audio functionality provided by the camera located in the robot head. It owns and
 * manages the lifecycle of the Sanbot @c HDCameraManager API instance.
 *
 * The API provides two video channels:
 * - @c SUB_STREAM channel: 640x480 video
 * - @c MAIN_STREAM channel: 1280x720 video
 *
 * The class manages a separate video/image stream for the sub-stream and main stream channels. The
 * low resolution sub-stream is used for live frame capture (video streaming and snapshot image
 * capture), the high-resolution main stream is used for still frame capture. A snapshot image is
 * obtained by just capturing a single video frame from the low-resolution stream. Because the
 * @c HDCameraManager API does not provide a separate method for capturing still images, a still
 * image is obtained by capturing a single frame from the high-resolution main stream. The class is
 * responsible for opening a stream, copying incoming frames into a frame buffer, and closing the
 * stream when no longer required.
 *
 * A frame capture mode associated with each steam defines how frames are acquired from the camera
 * (see @ref SANBOT_CAPTURE_PARAMS "here" for supported parameters). The capture mode specifies the
 * video mode that is applied which is one of
 * - @c SOFTWARE_DECODE_NV21: Video is captured as 12-bit YUV (YCrCb) frames.
 * - @c SOFTWARE_DECODE_RGB565: Video is captured as 16-bit RGB frames.
 * - @c HARDWARE_DECODE: Video is captured as H.264 Annex B frames with an I-frame interval of 1.5
 *   seconds (currently not supported).
 *
 * The capture mode to use is specified by the @c capture frame capture parameter. If this parameter
 * is not specified or not valid, a default capture mode is selected. Both streams also have an
 * associated frame decode mode that defines how those frames are represented in the frame buffer.
 * The decode mode defines the size and format of the frames retrieved by the camera unit. For this
 * camera the decode mode is fully determined by the capture type (live or still capture) and the
 * selected frame capture mode.
 *
 * Only the sub-stream is used for audio recording, audio frames received on the man stream are
 * ignored. Audio is always recorded as 8kHz mono 16-bit PCM audio frames.
 *
 * The @c HDCameraManager internally caches an image and broadcasts an event each time a human face
 * is detected. If enabled, the image and associated data passed in the event are stored and can be
 * retrieved by calling getFaceImage(). Face images are captured as bitmaps with size 1280x720.
 *
 * @parblock @note
 * The channel id received in the FrameListener event handler is not the same as the channel
 * type set when initializing the stream. Instead, the channel id is an index that is determined by
 * the order in which the streams were created. Events from the first opened stream have channel id
 * 0, events from the second opened stream have channel id 1. Since only two streams can be opened,
 * these are the only two values that ever exist. If for some unexpected reason an event arrives
 * with another channel id that event is ignored.
 * @endparblock
 *
 * @parblock @note @anchor FACE_CAPTURE
 * A background task continuously analyses the camera stream to detect human faces. If a face is
 * detected, an image containing the face is cached internally. The Sanbot @c HDCameraManager API
 * provides a @c getVideoImage() method that ultimately calls @c BindBaseService.getFaceImage() to
 * retrieve the cached image. The method can be called twice to retrieve the same cached image, but
 * fails on the third call. As a workaround, the face detection event listener implemented by this
 * class immediately retrieves the image and stores it in a fail-save buffer.
 * @endparblock
 *
 * @parblock @note
 * The Sanbot face recognition feature is capable of recognizing the detected face by comparing the
 * captured image with images stored in the @e Contact data. The face detection event data contains
 * fields for the name, gender, birthday etc., but since that feature uses a now defunct Sanbot web
 * service that data is always empty, so these fields are not included in the metadata copied to the
 * face capture buffer.
 * @endparblock
 *
 * @version     1.0.001
 * @date        7 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class SanbotCameraManager implements BridgeCameraManager, BridgeAudioManager
{
    /** Source label used for log messages. */
    private static final String TAG = "SanbotCameraManager";

    /** Human-readable camera name. */
    public static final String CAMERA_NAME = "sanbot";

    /** Human-readable camera alias. */
    public static final String CAMERA_ALIAS = "head";

    /** Identifies live capture type. */
    public static final int CAPTURE_TYPE_LIVE = 1;

    /** Identifies still capture type. */
    public static final int CAPTURE_TYPE_STILL = 2;

    /** Human-readable live capture type name. */
    public static final String CAPTURE_NAME_LIVE = "live";

    /** Human-readable still capture type name. */
    public static final String CAPTURE_NAME_STILL = "still";

    /** Sub-stream resolution. */
    public static final String SUB_STREAM_RESOLUTION = "640x480";

    /** Main stream resolution. */
    public static final String MAIN_STREAM_RESOLUTION = "1280x720";

    /** Maximum number of retries to retrieve video frame used for snapshot image. */
    public static final int NEW_FRAME_MAX_RETRIES = 30;

    /** Delay in milliseconds between attempts to retrieve video frame used for snapshot image. */
    public static final long NEW_FRAME_RETRY_DELAY_MS = 100L;

    /** Android application context. */
    private Context context = null;

    /**
     * Pixel format used for default live capture mode.
     *
     * The frame capture mode matching this pixel format will be selected if the @c capture frame
     * capture parameter is not specified or is invalid.
     */
    private static final PixelFormat DEFAULT_LIVE_PIXEL_FORMAT = PixelFormat.YUV;

    /**
     * Pixel format used for default still capture mode.
     *
     * The frame capture mode matching this pixel format will be selected if the @c capture frame
     * capture parameter is not specified or is invalid.
     */
    private static final PixelFormat DEFAULT_STILL_PIXEL_FORMAT = PixelFormat.YUV;

    /** Active Sanbot @c HDCameraManager instance. */
    private HDCameraManager hdCameraManager = null;

    /** Sub-stream properties. */
    private final Stream subStream = new Stream("video/audio");

    /** Main stream properties. */
    private final Stream mainStream = new Stream("image");

    /** Callback host to which to publish bridge unit events. */
    private final BridgeEventHost eventHost;

    /** List of available frame capture modes. */
    private final List<CaptureMode> captureModes = new ArrayList<>();

    /** Flag specifying if face detection is enabled. */
    private boolean faceDetection = false;

    /** Face capture buffer. */
    private final FaceCaptureBuffer faceCaptureBuffer = new FaceCaptureBuffer();

    /**
     * Counter for number of open streams.
     *
     * This counter is used to define a stream index that is equal to 0 for the first opened stream
     * and 1 for the next opened stream.
     */
    private final AtomicInteger streamCount = new AtomicInteger();

    /** Current camera device status. */
    private volatile CameraStatus cameraStatus = CameraStatus.NOT_INITIALIZED;

    /** Audio frame buffer. */
    private final AudioFrameBuffer audioFrameBuffer = new AudioFrameBuffer();

    /** Counter for number of received audio frame events. */
    private final AtomicInteger audioFrameCount = new AtomicInteger();

    /** Current audio device status. */
    private volatile AudioStatus audioStatus = AudioStatus.NOT_INITIALIZED;

    /** Last reported error. */
    private DataResult error = null;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new SanbotCameraManager instance.
     *
     * The base class constructor is called to copy the event host to a member variable.
     *
     * @param   eventHost       callback host to which to forward camera manager events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward camera manager events to be published by the BridgeService
     * instance.
     */
    public SanbotCameraManager(BridgeEventHost eventHost)
    {
        this.eventHost = eventHost;
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Initializes the camera manager.
     *
     * The sub-stream and main stream are closed if currently open. If the specified Sanbot
     * @c HDCameraManager instance is not the same as the active instance the specified instance is
     * made the active instance. After initialization completes, the streams states are reset to
     * reflect the resulting camera status, and registerListeners() is called to attach the frame
     * listeners and face detection listener to the camera manager.
     *
     * @param   context         Android application context
     * @param   args            additional initialization arguments
     *
     * This implementation requires two additional initialization arguments
     * - Sanbot @c HDCameraManager API instance.
     * - Boolean value, if @c true, face detection is enabled.
     *
     * In emulator mode @p cameraManager is equal to @c null.
     */
    public synchronized void init(Context context, @NonNull Object... args)
    {
        BridgeLog.info(TAG, "Initialize Sanbot camera manager");
        this.context = context;
        error = null;

        // Parse arguments.
        HDCameraManager hdCameraManager = null;
        faceDetection = false;
        if (args.length >= 2)
        {
            hdCameraManager = (args[0] instanceof HDCameraManager) ? (HDCameraManager)args[0] : null;
            Boolean b = ValueUtils.toBoolean(args[1]);
            faceDetection = (b != null) ? b : false;
        }

        // Close active camera streams if open. Copy specified HDCameraManager instance to active
        // instance if specified instance is not the active instance.
        if (subStream.isOpen) closeStream(subStream);
        if (mainStream.isOpen) closeStream(mainStream);
        if (this.hdCameraManager != hdCameraManager) this.hdCameraManager = hdCameraManager;

        // Initialize camera.
        if ((BuildConfig.EMULATOR_MODE) || (initCamera()))
        {
            cameraStatus = CameraStatus.AVAILABLE;
            audioStatus = AudioStatus.AVAILABLE;
        }
        else
        {
            cameraStatus = CameraStatus.ERROR;
            audioStatus = AudioStatus.ERROR;
        }

        // Initialize streams and registers event listeners.
        String status = (cameraStatus == CameraStatus.AVAILABLE) ? ((BuildConfig.EMULATOR_MODE) ? "emulated_ready" : "ready") : "unavailable";
        subStream.reset(status);
        mainStream.reset(status);
        streamCount.set(0);
        registerListeners(faceDetection);
    }

    /**
     * Shuts down the camera manager.
     *
     * The managed Sanbot @c HDCameraManager API instance is released and the sub-stream and main
     * stream are closed.
     */
    public synchronized void shutdown()
    {
        BridgeLog.info(TAG, "Shut down Sanbot camera manager");

        cameraStatus = CameraStatus.STOPPED;
        audioStatus = AudioStatus.STOPPED;
        streamCount.set(0);
        hdCameraManager = null;
        closeStream(subStream);
        closeStream(mainStream);
    }

    /**
     * Resets the camera manager.
     *
     * The managed Android @c Camera API instance is released and the shared stream is closed, and
     * the camera is re-initialized.
     */
    public synchronized DataResult reset()
    {
        Context context = this.context;
        HDCameraManager hdCameraManager =  this.hdCameraManager;
        boolean faceDetection = this.faceDetection;

        shutdown();
        init(context, hdCameraManager, faceDetection);
        return (error == null) ? DataResult.success() : error;
    }

    /**
     * Starts streaming live video.
     *
     * This method must be called when a client requests live video streaming. If the camera is
     * currently available, the requested or default frame capture mode and frame decode mode are
     * selected, and ensureStreamOpen() is called to open the sub-stream for video streaming if not
     * already open. If successful, the shared stream handle is returned.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  shared stream handle on success, or -1 if the stream could not be opened
     */
    public synchronized long openVideoStream(Map<String, Object> captureParams)
    {
        // If camera is not available return error response.
        if (isCameraAvailable() == false)
        {
            setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure", "camera is not available for video streaming");
            return -1;
        }

        // Select frame capture mode specified by frame capture parameters.
        CaptureMode captureMode = selectCaptureMode(StreamOption.SUB_STREAM, CAPTURE_TYPE_LIVE, captureParams);
        if (captureMode == null) return -1;

        // Select frame decode mode specified by frame capture parameters.
        DecodeMode decodeMode = selectDecodeMode(captureMode, captureParams);
        if (decodeMode == null) return -1;

        // Ensure the sub-stream is open.
        return ensureStreamOpen(subStream, captureParams, captureMode, decodeMode);
    }

    /**
     * Retrieves the most recently buffered frame from the video frame buffer
     *
     * The frame is retrieved from the video frame buffer associated with the sub-stream.
     *
     * @return  latest video frame as @e MJPEG image, or @c null if frame is not available
     */
    public synchronized Image getVideoFrame()
    {
        return subStream.frameBuffer.getVideoFrame();
    }

    /**
     * Stops streaming live video.
     *
     * This method must be called to stop live video streaming. It calls closeStreamIfIdle() to
     * release the sub-stream if no longer required.
     */
    public synchronized void releaseVideoStream()
    {
        closeStreamIfIdle(subStream);
    }

    /**
     * Captures a snapshot image from the sub-stream.
     *
     * If the camera is currently available, the requested or default fram capture mode and frame
     * decode mode are selected, and ensureStreamOpen() is called to open the sub-stream for video
     * streaming if not already open. If successful, getSnapshotImageFromBuffer() is called to wait
     * for a frame newer than the latest buffered frame to appear in the frame buffer.
     *
     * The sub-stream is closed if no longer required.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  Image instance representing captured snapshot image, or @c null on failure
     */
    @Nullable
    public synchronized Image getSnapshotImage(Map<String, Object> captureParams)
    {
        // If camera is not available return error response.
        if (isCameraAvailable() == false)
        {
            setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure", "camera not available for capturing snapshot image");
            return null;
        }

        // Select frame capture mode specified by frame capture parameters.
        CaptureMode captureMode = selectCaptureMode(StreamOption.SUB_STREAM, CAPTURE_TYPE_LIVE, captureParams);
        if (captureMode == null) return null;

        // Select frame decode mode specified by frame capture parameters.
        DecodeMode decodeMode = selectDecodeMode(captureMode, captureParams);
        if (decodeMode == null) return null;

        // Retrieve timestamp of last video frame stored in frame buffer.
        long timestamp = subStream.frameBuffer.getFrameTimestamp();

        // Ensure the sub-stream is open.
        if (ensureStreamOpen(subStream, captureParams, captureMode, decodeMode) < 0) return null;

        try
        {
            // Wait for a snapshot frame newer than the one in cache.
            return getSnapshotImageFromBuffer(subStream.frameBuffer, timestamp);
        }
        finally
        {
            // Close sub-stream if no longer required.
            closeStreamIfIdle(subStream);
        }
    }

    /**
     * Captures a still image as a snapshot image from the main stream.
     *
     * If the camera is currently available, the requested or default frame capture mode and frame
     * decode mode are selected, and ensureStreamOpen() is called to open the main stream for video
     * streaming if not already open. If successful, getSnapshotImageFromBuffer() is called to wait
     * for a frame newer than the latest buffered frame to appear in the frame.
     *
     * The main stream is closed if no longer required.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  Image instance representing captured snapshot image, or @c null on failure
     */
    @Nullable
    public synchronized Image getStillImage(Map<String, Object> captureParams)
    {
        // If camera is not available return error response.
        if (isCameraAvailable() == false)
        {
            setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure", "camera not available for capturing still image");
            return null;
        }

        // Select frame capture mode specified by frame capture parameters.
        CaptureMode captureMode = selectCaptureMode(StreamOption.MAIN_STREAM, CAPTURE_TYPE_STILL, captureParams);
        if (captureMode == null) return null;

        // Select frame decode mode specified by frame capture parameters.
        DecodeMode decodeMode = selectDecodeMode(captureMode, captureParams);
        if (decodeMode == null) return null;

        // Retrieve timestamp of last video frame stored in frame buffer.
        long timestamp = mainStream.frameBuffer.getFrameTimestamp();

        // Ensure the main stream is open.
        if (ensureStreamOpen(mainStream, captureParams, captureMode, decodeMode) < 0) return null;

        try
        {
            // Wait for a snapshot frame newer than the one in cache.
            return getSnapshotImageFromBuffer(mainStream.frameBuffer, timestamp);
        }
        finally
        {
            // Close main stream if no longer required.
            closeStreamIfIdle(mainStream);
        }
    }

    /**
     * Prepares the sub-stream for audio recording.
     *
     * This method must be called when a client requests recording audio. If the audio device is
     * available, the default capture mode and decode mode are selected, and ensureStreamOpen() is
     * called to open the main stream for video streaming if not already open. If successful, the
     * shared stream handle is returned.
     *
     * @param   recordingParams optional audio recording parameters
     *
     * @return  sub-stream handle on success, or -1 if stream could not be opened
     */
    public synchronized long openAudioStream(Map<String, Object> recordingParams)
    {
        // If audio device is not available for recording return error response.
        if (audioStatus != AudioStatus.AVAILABLE)
        {
            setError(BridgeResult.Code.NOT_READY, "audio_device_failed", "device not available for audio recording");
            return -1;
        }

        // Select frame capture mode specified by frame capture parameters.
        CaptureMode captureMode = selectCaptureMode(StreamOption.SUB_STREAM, CAPTURE_TYPE_LIVE, null);
        if (captureMode == null) return -1;

        // Select frame decode mode specified by frame capture parameters.
        DecodeMode decodeMode = selectDecodeMode(captureMode, null);
        if (decodeMode == null) return -1;

        // Ensure the main stream is open and reserve the recorder for the worker thread.
        long handle = ensureStreamOpen(subStream, MapUtils.createMap(), captureMode, decodeMode);
        if (handle >= 0) audioStatus = AudioStatus.RECORD_STARTING;
        return handle;
    }

    /**
     * Starts recording audio.
     *
     * The @c recordAudio method implemented by the AudioFrameBuffer class is called. The method
     * does not exit until audio recording is stopped either because the specified duration is
     * reached or because a stop request is received, so it must be executed in a separate thread
     * to make sure the main request handling thread is not blocked while audio is being recorded.
     *
     * @param   length          number of seconds of audio to record
     *
     * @return  array of bytes containing recorded audio as in-memory @c WAV file
     */
    public byte[] recordAudio(int length)
    {
        synchronized (this)
        {
            // A stop can arrive after the stream is opened but before this worker starts.
            if (audioStatus == AudioStatus.RECORD_STOPPING)
            {
                audioStatus = AudioStatus.AVAILABLE;
                return null;
            }
            if (audioStatus != AudioStatus.RECORD_STARTING) return null;

            audioFrameCount.set(0);
            audioFrameBuffer.clear();
            audioStatus = AudioStatus.RECORDING;
        }
        try
        {
            return audioFrameBuffer.recordAudio(length, new AudioFrameBuffer.AudioManagerHost()
            {
                @Override
                public AudioStatus getStatus() { return audioStatus; }
            });
        }
        finally
        {
            synchronized (this)
            {
                audioStatus = AudioStatus.AVAILABLE;
            }
        }
    }

    /**
     * Requests to stop recording audio.
     *
     * The method just sets the value of @c getAudioStatus to @c RECORD_STOPPING. The AudioFrameBuffer
     * class is responsible for checking this status and stop retrieving audio frames if a stop is
     * requested.
     */
    public synchronized void stopAudioRecording()
    {
        if ((audioStatus == AudioStatus.RECORD_STARTING) || (audioStatus == AudioStatus.RECORDING))
            audioStatus = AudioStatus.RECORD_STOPPING;
    }

    /**
     * Finalizes recording audio.
     *
     * This method must be called after audio recording has stopped. It calls closeStreamIfIdle() to
     * release the sub-stream if no longer required.
     */
    public synchronized void releaseAudioStream() { closeStreamIfIdle(subStream); }

    /**
     * Retrieves the latest face image from the image buffer.
     *
     * @param   index           last-in, first-out index of face to face capture to retrieve
     *
     * @return  lastest image from buffer, or @c null if image is not available
     */
    @Nullable
    public synchronized Image getFaceImage(int index)
    {
        return faceCaptureBuffer.getFaceCapture(index);
    }

    /**
     * Builds a data map containing camera manager status data.
     *
     * The returned map includes the camera name and status, the sub-stream and main stream status
     * data, and the audio recording status.
     *
     * @return  Java @c Map instance containing camera manager status data
     */
    @NonNull
    public Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = MapUtils.createMap("camera", CAMERA_NAME, "status", cameraStatus.name().toLowerCase());
        data.put("subStreamStatus", getStreamStatus(subStream));
        data.put("mainStreamStatus", getStreamStatus(subStream));
        data.put("audioStreamStatus", getAudioStreamStatus());
        data.put("faceCaptureBuffer", faceCaptureBuffer.buildStatusData());
        return data;
    }

    /**
     * Builds a data map containing supported camera feature data.
     *
     * The returned map contains the supported frame capture modes.
     *
     * @return  Java @c Map instance containing camera feature data
     */
    @NonNull
    public Map<String, Object> buildFeatureData()
    {
        Map<String, Object> data = MapUtils.createMap("cameraName", CAMERA_NAME, "cameraAlias", CAMERA_ALIAS);
        data.putAll(getCaptureModes());
        return data;
    }

    /**
     * Builds a data map containing audio stream data.
     *
     * @return  Java @c Map instance containing audio stream data
     */
    @NonNull
    public synchronized Map<String, Object> buildAudioData()
    {
        Map<String, Object> data = MapUtils.createMap();
        if (subStream.isOpen)
        {
            data.put("handle", subStream.handle);
            data.put("timestamp", subStream.openTime);
            data.put("channel", StringUtils.unknownIfBlank(subStream.captureMode.channelName));
            data.put("openTime", mainStream.openTime - System.currentTimeMillis() + "ms");
            data.put("capture", StringUtils.unknownIfBlank(subStream.captureMode.id));
            data.put("decode", StringUtils.unknownIfBlank(subStream.decodeMode.id));
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
     * Returns @c true if audio device is available.
     *
     * @return  @c true if audio device is available, @c false if audio device is not available
     */
    public synchronized boolean isRecorderAvailable()
    {
        return (audioStatus == AudioStatus.AVAILABLE);
    }

    /**
     * Returns current audio status.
     *
     * @return  AudioStatus enumerator value specifying audio status
     */
    public AudioStatus getAudioStatus()
    {
        return audioStatus;
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
     * Initializes the Sanbot camera.
     *
     * If the Sanbot @c HDCameraManager is available the list of supported frame capture modes is
     * initialized. No further initialization is required.
     *
     * @return  @c true on success, @c false on failure
     */
    private boolean initCamera()
    {
        BridgeLog.debug(TAG, "Initialize camera");

        // Return error response if Sanbot HDCameraManager is not available
        if (hdCameraManager == null)
        {
            setError(BridgeResult.Code.NOT_READY, "camera_init_failed", "Sanbot HDCameraManager not available");
            return false;
        }

        // Initialize list of supported frame capture modes.
        initCaptureModes();

        return true;
    }

    /**
     * Registers frame listener and face detection event listener.
     *
     * @param   faceDetection   if @c true, face recognition is enabled
     *
     * The face detection event listener is not registered if @p faceDetection equals @c false.
     */
    private synchronized void registerListeners(boolean faceDetection)
    {
        if ((BuildConfig.EMULATOR_MODE) || (cameraStatus != CameraStatus.AVAILABLE)) return;

        /** Listener for media stream events. */
        hdCameraManager.setMediaListener(new FrameListener());
        if (faceDetection)
        {
            /** Listener for face detection events. */
            hdCameraManager.setMediaListener(new FaceDetectListener());
            BridgeLog.debug(TAG, "Face detection is enabled");
        }
    }

    /**
     * Initializes the list of supported frame capture modes.
     *
     * The @c captureModes list is build from the sensor types and video modes supported by the
     * OpenNI2 @c Camera instance representing the camera. Each capture mode combines a channel
     * type, a capture type and a pixel format. The channel type and capture type are linked, all
     * four combinations of the two channel types/capture types and two pixel formats are added to
     * the list.
     */
    private void initCaptureModes()
    {
        captureModes.clear();
        addCaptureMode(StreamOption.SUB_STREAM, CAPTURE_TYPE_LIVE, PixelFormat.YUV);
        addCaptureMode(StreamOption.SUB_STREAM, CAPTURE_TYPE_LIVE, PixelFormat.RGB);
        addCaptureMode(StreamOption.MAIN_STREAM, CAPTURE_TYPE_STILL, PixelFormat.YUV);
        addCaptureMode(StreamOption.MAIN_STREAM, CAPTURE_TYPE_STILL, PixelFormat.RGB);
    }

    /**
     * Adds a new frame capture mode to the list of supported capture modes.
     *
     * @param   channelType     Sanbot @c API channel type
     * @param   captureType     capture type from which to create capture mode
     * @param   pixelFormat     pixel format from which to create capture mode
     */
    private void addCaptureMode(int channelType, int captureType, PixelFormat pixelFormat)
    {
        CaptureMode captureMode = CaptureMode.create(channelType, captureType, pixelFormat);
        if (captureMode != null) captureModes.add(captureMode);
    }

    /**
     * Selects the requested or default frame capture mode.
     *
     * The @c capture parameter specifying the id of the requested frame capture mode is retrieved
     * from the frame capture parameters. If the list of capture modes contains an item with
     * matching mode id, specified capture type and channe; type, that capture type is returned. If
     * no capture mode id is specified or the specified  capture mode is not supported,
     * defaultCaptureMode() is called to get the default capture mode for the selected capture type.
     *
     * @param   channelType     channel type for which to select frame capture mode
     * @param   captureType     frame capture type for which to select frame capture mode
     * @param   captureParams   optional frame capture parameters
     *
     * @return  selected frame capture mode, or @c null if no frame capture mode is available
     */
    @Nullable
    private CaptureMode selectCaptureMode(int channelType, int captureType, Map<String, Object> captureParams)
    {
        // Get channel type and stream name matching the stream type.
        String captureName = (captureType == CAPTURE_TYPE_LIVE) ? CAPTURE_NAME_LIVE
            : ((captureType == CAPTURE_TYPE_STILL) ? CAPTURE_NAME_STILL : null);
        if (captureName == null)
        {
            setError(BridgeResult.Code.NOT_SUPPORTED, "stream_open_failure", "unsupported capture type " + captureType);
            return null;
        }

        // Retrieve frame capture mode with specified mode id if available..
        String modeId = MapUtils.getString(captureParams, "mode", null);
        if (modeId != null)
        {
            // Find frame capture mode with matching sensor type and mode id.
            modeId = modeId.trim();
            for (CaptureMode captureMode : captureModes)
            {
                if ((captureMode.captureType == captureType) && (captureMode.channelType == channelType) && (modeId.equalsIgnoreCase(captureMode.id))) return captureMode;
            }
            setError(BridgeResult.Code.FAILURE, "stream_open_failure",
                captureName + " capture mode " + modeId + " not supported for " + channelName(channelType) + " channel - default capture mode will be used");
            return null;
        }
        else BridgeLog.warning(TAG, "Requested capture mode not specified - default capture mode will be used");

        // Return default frame capture mode.
        return defaultCaptureMode(channelType, captureType, captureName);
    }

    /**
     * Returns the default frame capture mode for the specified channel type.
     *
     * The supported frame capture mode with the configured default pixel format is returned if
     * available.
     *
     * @param   channelType     channel type for which to return default frame capture mode
     * @param   captureType     stream type for which to return default frame capture mode
     * @param   streamName      name of stream (for logging purposes only)
     *
     * @return  default frame capture mode for channel, or @c null if no capture mode is available
     */
    @Nullable
    private CaptureMode defaultCaptureMode(int channelType, int captureType, String streamName)
    {
        // Get default pixel format for stream type.
        PixelFormat pixelFormat = (captureType == CAPTURE_TYPE_LIVE) ? DEFAULT_LIVE_PIXEL_FORMAT
            : ((captureType == CAPTURE_TYPE_STILL) ? DEFAULT_STILL_PIXEL_FORMAT : null);
        if (pixelFormat == null)
        {
            setError(BridgeResult.Code.NOT_SUPPORTED, "stream_open_failed", "unsupported stream type " + captureType);
            return null;
        }

        // Returns capture mode with specified pixel format.
        for (CaptureMode captureMode : captureModes)
        {
            if ((captureMode.captureType == captureType) && (captureMode.channelType == channelType) && (captureMode.pixelFormat == pixelFormat)) return captureMode;
        }

        setError(BridgeResult.Code.NOT_AVAILABLE, "stream_open_failure",
            "no default " + streamName + " capture mode available for " + channelName(channelType) + " channel");
        return null;
    }

    /**
     * Selects requested or default frame decode mode.
     *
     * The @c decode parameter is not actually supported by this camera manager, the frame decode
     * mode is fully determined by the stream type (Live stream or still stream) and the selected
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
            "no frame decode available for " + captureMode.channelName + " channel");
        return decodeMode;
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
     * @param   stream          Stream instance representing stream to open
     * @param   captureParams   optional frame capture parameters
     * @param   captureMode     frame capture mode
     * @param   decodeMode      frame decode mode
     *
     * @return  stream handle if stream was successfully opened, or -1 on failure
     */
    private long ensureStreamOpen(@NonNull Stream stream, Map<String, Object> captureParams, CaptureMode captureMode, DecodeMode decodeMode)
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
            BridgeLog.info(TAG, "Using open stream for " + captureMode.channelName + " stream");
            handle = stream.handle;
        }
        else
        {
            // The idle stream is reconfigured for the requested capture.
            closeStream(stream);
            if ((handle = openStream(stream, captureParams, captureMode, decodeMode)) < 0) return -1;
        }

        int count = ++stream.clientCount;
        BridgeLog.info(TAG, "Shared stream client registered - number of clients is now " + count);
        return handle;
    }

    /**
     * Opens the specified stream.
     *
     * The camera parameters are set, and teh video stream is opened. If successful, the specified
     * Stream instance is initialized, and a stream open event is published for live capture.
     *
     * If the application is running in emulator there is nothing to open and a random stream handle
     * is returned.
     *
     * @param   stream          Stream instance representing stream to open
     * @param   captureParams   optional frame capture parameters
     * @param   captureMode     frame capture mode specifying frame resolution
     * @param   decodeMode      frame decode mode
     *
     * @return  stream handle (camera id) if stream was successfully opened, or -1 on failure
     */
    private long openStream(Stream stream, Map<String, Object> captureParams, CaptureMode captureMode, DecodeMode decodeMode)
    {
        long handle = -1;

        try
        {
            if (BuildConfig.EMULATOR_MODE) handle = (int)(Math.random()*Integer.MAX_VALUE);
            else
            {
                // Set camera parameters.
                StreamOption option = new StreamOption();
                option.setChannel(captureMode.channelType);
                option.setDecodType(captureMode.pixelFormat.getDecodeType());
                option.setJustIframe(false);

                // Open the stream.
                DataResult result = DataResult.fromOperationResult(hdCameraManager.openStream(option));
                handle = (result.isSuccess()) ? ValueUtils.toBoundInteger(result.getData(), -1, Integer.MIN_VALUE, Integer.MAX_VALUE) : -1;
            }
        }
        catch (Exception e)
        {
            setError(BridgeResult.Code.FAILURE, "camera_stream_failure", e.getMessage());
            closeStream(stream);
        }

        int index = (streamCount.getAndIncrement() == 0) ? 0 : 1;
        if ((handle < 0) || (stream.init(handle, index, captureMode, decodeMode, captureParams) == false))
        {
            setError(BridgeResult.Code.FAILURE, "stream_open_failure", "failed to open " + stream.name + " stream");
            return -1;
        }

        if (captureMode.captureType == CAPTURE_TYPE_LIVE)
        {
            cameraStatus = CameraStatus.LIVECAPTURE;
            publishStreamStartEvent(stream);
        }
        else cameraStatus = CameraStatus.STILLCAPTURE;
        BridgeLog.info(TAG, "Handle " + handle + " assigned to " + stream.name + " stream");
        return handle;
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
     * If the application is not running in emulator mode, the video stream is closed, and the
     * specified Stream instance is released. A stream release event is published before the stream
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

        // Close stream if not running in emulator mode.
        if (BuildConfig.EMULATOR_MODE == false)
        {
            OperationResult sdkResult = hdCameraManager.closeStream((int)stream.handle);
            DataResult result = DataResult.fromOperationResult(sdkResult);
            if (result.isFailure())
            {
                BridgeLog.error(TAG, "Failed to close " + stream.name + " stream, SDK result " + result);
                return;
            }
        }

        // Broadcast bridge event signaling the stream will close.
        if (stream.captureMode.captureType == CAPTURE_TYPE_LIVE) publishStreamStopEvent(stream);

        // Close the stream.
        BridgeLog.info(TAG, "Closed " + stream.name + " stream with handle " + stream.handle);
        stream.release("ready");
        cameraStatus = CameraStatus.AVAILABLE;
        notifyAll();
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

        for (int i=0; i < NEW_FRAME_MAX_RETRIES; i++)
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
     * Retrieves the current face image from the camera manager.
     *
     * @return  Image instance containing current face image
     */
    @Nullable
    private synchronized Bitmap getCurrentFaceImage()
    {
        if (hdCameraManager == null) return null;
        BridgeLog.apicall("SanbotSDK", "HDCameraManager", "getVideoImage");
        return hdCameraManager.getVideoImage();
    }

    /**
     * Publishes an event signaling the specified stream was opened.
     *
     * @param   stream          stream that was opened (always shared stream)
     */
    private void publishStreamStartEvent(@NonNull Stream stream)
    {
        Map<String, Object> data = MapUtils.createMap();
        data.put("handle", subStream.handle);
        data.put("timestamp", subStream.openTime);
        data.put("channel", StringUtils.unknownIfBlank(subStream.captureMode.channelName));
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
        data.put("channel", StringUtils.unknownIfBlank(stream.captureMode.channelName));
        data.put("capture", StringUtils.unknownIfBlank(stream.captureMode.id));
        data.put("decode", StringUtils.unknownIfBlank(stream.decodeMode.id));
        data.put("duration", duration + " ms");
        if (videoFrameCount> 0)
        {
            data.put("frameCount", videoFrameCount);
            data.put("frameRate", 1000*videoFrameCount/duration + "fps");
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
        Map<String, Object> data = MapUtils.createMap("isOpen", subStream.isOpen);
        if (subStream.isOpen)
        {
            data.put("status", stream.status);
            data.put("handle", stream.handle);
            data.put("capture", stream.captureMode.id);
            data.put("channel", StringUtils.unknownIfBlank(stream.captureMode.channelName));
            data.put("format", stream.captureMode.pixelFormat);
            data.put("frames", stream.frameCount);
            data.put("clients", stream.clientCount);
            data.put("openTime", stream.openTime - System.currentTimeMillis() + "ms");
            data.put("frameBuffer", stream.frameBuffer.buildStatusData());
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

        List<String> liveCaptureModeData = new ArrayList<>();
        for (CaptureMode captureMode : captureModes)
        {
            if ((StringUtils.isBlank(captureMode.id) == false) && (captureMode.captureType == CAPTURE_TYPE_LIVE))
                liveCaptureModeData.add(captureMode.id + " (" + captureMode.toString() + ")");
        }
        data.put("liveCaptureModes", liveCaptureModeData);

        List<String> stillCaptureModeData = new ArrayList<>();
        for (CaptureMode captureMode : captureModes)
        {
            if ((StringUtils.isBlank(captureMode.id) == false) && (captureMode.captureType == CAPTURE_TYPE_STILL))
                stillCaptureModeData.add(captureMode.id + " (" + captureMode.toString() + ")");
        }
        data.put("stillCaptureModes", stillCaptureModeData);

        return data;
    }

    /**
     * Returns a data map specifying the audio stream status.
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    private synchronized Map<String, Object> getAudioStreamStatus()
    {
        Map<String, Object> data = MapUtils.createMap("isOpen", subStream.isOpen);
        if (subStream.isOpen)
        {
            data.put("status", subStream.status);
            data.put("handle", subStream.handle);
            data.put("channel", StringUtils.unknownIfBlank(subStream.captureMode.channelName));
            data.put("openTime", subStream.openTime - System.currentTimeMillis() + "ms");
            data.put("frameBuffer", audioFrameBuffer.buildStatusData());
        }
        return data;
    }

    /**
     * Returns the name of the specified stream channel.
     *
     * @param   channelType     Channel type defined in Sanbot SDK @c StreamOption class
     *
     * @return  string containing channel name
     */
    @NonNull
    private static String channelName(int channelType)
    {
        if (channelType == StreamOption.MAIN_STREAM) return "main stream";
        if (channelType == StreamOption.SUB_STREAM) return "substream";
        return "";
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
     * Listener for frame events from Sanbot @c HDCameraManager API.
     *
     * This class extends the Sanbot @c MediaStreamListener class. It implements methods that are
     * called if a video frame or audio frame is received. Both these callback methods just copy the
     * received frame data to the appropriate frame buffer.
     */
    private final class FrameListener implements MediaStreamListener
    {
        /**
         * Callback method for video frame events.
         *
         * The frame is copied to the video frame buffer owned by the Stream instance corresponding
         * to the stream with the specified index.
         *
         * @param   index       index of stream on which frame is received
         * @param   bytes       byte array containing frame data
         * @param   width       image width
         * @param   height      image height
         */
        @Override
        public synchronized void getVideoStream(int index, @NonNull byte[] bytes, int width, int height)
        {
            if ((index == subStream.index) && (subStream.isOpen))
            {
                // Frame received on live stream.
                subStream.frameCount.getAndIncrement();
                subStream.frameBuffer.setData(bytes.clone(), subStream.decodeMode.imageType, width, height);
            }
            else if ((index == mainStream.index) && (mainStream.isOpen))
            {
                // Frame received on snapshot stream.
                mainStream.frameCount.getAndIncrement();
                mainStream.frameBuffer.setData(bytes.clone(), mainStream.decodeMode.imageType, width, height);
            }
        }

        /**
         * Callback method for audio frame events.
         *
         * If audio recording is active the audio frame is copied to the audio frame buffer. Only
         * frames received from the live stream are copied, audio frames from the snapshot stream
         * are ignored.
         *
         * @param   index       index of stream on which audio frame is received
         * @param   data        byte array containing audio frame data
         */
        public void getAudioStream(int index, @NonNull byte[] data)
        {
            if ((index == subStream.index) && (subStream.handle >= 0))
            {
                // Frame received on live stream.
                if (audioStatus == AudioStatus.RECORDING) audioFrameBuffer.setData(data);
            }
        }
    }

    /**
     * Listener for face detection events from Sanbot @c HDCameraManager API.
     *
     * This class extends the Sanbot SDK @c FaceRecognizeListener class. It implements the method
     * that is called if a face detection event is received.
     */
    private final class FaceDetectListener implements FaceRecognizeListener
    {
        /**
         * Callback method for face detection frame events.
         *
         * The face image (always a bitmap) is retrieved, and together with the face detection data
         * is stored in the face capture buffer. An bridge event is broadcast to signal a face has
         * been detected.
         *
         * @param   faceData    list of face detection data items
         *
         * The face data contains
         * - @c detected: Flag specifying if face is detected (may not be available).
         * - @c frameId: Capture id  (may not be available)
         * - @c left, @c top, @c right, @c bottom: Coordinates of corners of rectangle in image that
         *      contains the detected face
         *
         * Since multiple faces may be detected in the same image, the data may contain multiple
         * items.
         */
        @Override
        public void recognizeResult(@NonNull List<FaceRecognizeBean> faceData)
        {
            BridgeLog.apievent("SanbotSDK", "FaceRecognizeListener", "recognizeResult", String.format("{List<>(%d)}", faceData.size()));

            // Get face image and store image in buffer if not empty.
            Bitmap bitmap = getCurrentFaceImage();

            if (bitmap != null)
            {
                Map<String, Object> data = parseFaceData(faceData);
                faceCaptureBuffer.setData(bitmap, data);
                if (eventHost != null) eventHost.publishEvent(BridgeEvent.create(BridgeProtocol.MODULE_CAMERA, BridgeProtocol.EVENT_FACE, data));
            }
        }

        /**
         * Parses raw face detection event data.
         *
         * @param   rawData         Java @c List instance containing raw face detection event data
         *
         * @return  Java @c Map instance containing parsed face detection event data
         */
        @NonNull
        private Map<String, Object> parseFaceData(@NonNull List<FaceRecognizeBean> rawData)
        {
            Map<String, Object> parsedData = MapUtils.createMap("faceCount", rawData.size());
            if (rawData.isEmpty()) return parsedData;

            FaceRecognizeBean item0 = rawData.get(0);
            String frameId = (rawData.get(0) != null) ? item0.getFrame_id() : null; 
            if (frameId != null) parsedData.put("frameId", frameId);
            parsedData.put("faceCount", rawData.size());
            for (int i=0; i<rawData.size(); i++)
            {
                FaceRecognizeBean rawDataItem = rawData.get(i);
                String key = String.format("face-%d", i+1);
                JsonObject value = new JsonObject();
                int maxWidth = rawDataItem.getW();
                int maxHeight = rawDataItem.getH();
                value.addProperty("left", (int)(maxWidth*rawDataItem.getLeft()+0.5));
                value.addProperty("top", (int)(maxHeight*rawDataItem.getTop()+0.5));
                value.addProperty("right", (int)(maxWidth*rawDataItem.getRight()+0.5));
                value.addProperty("bottom", (int)(maxHeight*rawDataItem.getBottom()+0.5));
                parsedData.put(key, value);
            }
            return parsedData;
        }
    }

    /**
     * Helper class for storing stream properties.
     *
     * This class manages the stream properties and owns the CaptureMode, DecodeMode and
     * VideoFrameBuffer instances.
     */
    private static final class Stream
    {
        /** Human-readable stream name. */
        private final String name;

        /** Stream handle. */
        private long handle = -1;

        /** Stream index, specifies order in which streams were opened. */
        private int index = -1;

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
        private Stream(String name)
        {
            this.name = name;
        }

        /*******************************************************************************************
         * PUBLIC METHODS
         ******************************************************************************************/

        /**
         * Initializes this stream instance.
         *
         * The supplied handle, index, capture mode, decode mode, and active camera parameters are
         * stored. The frame buffer is reinitialized with metadata describing the capture settings,
         * the frame and client counters are reset, and the open timestamp and status text are
         * updated. If all mandatory properties are validated the value of the @c isOpen flag is set
         * to @c true.
         *
         * @param   handle          stream handle
         * @param   index           stream index, specifies order in which streams were opened
         * @param   captureMode     frame capture mode specifying sensor type and video mode
         * @param   decodeMode      frame decode mode (only relevant for depth and infrared data)
         * @param   cameraParams    active camera parameters
         */
        private boolean init(long handle, int index, CaptureMode captureMode, DecodeMode decodeMode, Map<String, Object> cameraParams)
       {
            // Copy specified stream properties.
            this.handle = handle;
            this.index = index;
            this.captureMode = captureMode;
            this.decodeMode = decodeMode;

            // Add camera parameters to frame buffer.
            frameBuffer.init(toMetaData(cameraParams));

            // Initialize counters.
            frameCount.set(0);
            clientCount = 0;

            // Set status info.
            this.openTime = System.currentTimeMillis();
            this.isOpen = ((handle >= 0) && (index >= 0) && (captureMode != null) && (decodeMode != null));
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
         * The stream handle, index, capture mode, and decode mode are blanked, the video frame
         * buffer is emptied, the frame and client counters are reset, and the specified status text
         * is set.
         *
         * @param   status          stream status text to set after resetting the stream
         */
        private void reset(String status)
        {
            this.handle = -1;
            this.index = -1;
            this.captureMode = null;
            this.decodeMode = null;

            // Clear frame buffer.
            frameBuffer.clear();

            // Reset counters
            frameCount.set(0);
            clientCount = 0;

            // Set status info.
            this.openTime = -1L;
            this.isOpen = false;
            this.status = status;
        }

        /**
         * Builds a metadata map containing the current stream capture settings.
         *
         * @param   ignoredCameraParams active camera parameters
         *
         * @return  Java @c Map instance containing capture metadata
         */
        @NonNull
        private Map<String, Object> toMetaData(Map<String, Object> ignoredCameraParams)
        {
            Map<String, Object> data = new LinkedHashMap<>();
            if (isOpen)
            {
                data.put("Camera", CAMERA_NAME);
                data.put("Camera-ChannelType", captureMode.channelType);
                data.put("Camera-FrameFormat", captureMode.pixelFormat);
                switch (captureMode.channelType)
                {
                    case StreamOption.SUB_STREAM:
                        data.put("Camera-FrameSize", SUB_STREAM_RESOLUTION);
                        break;
                    case StreamOption.MAIN_STREAM:
                        data.put("Camera-FrameSize", MAIN_STREAM_RESOLUTION);
                        break;
                }
            }
            return data;
        }
    }

    /**
     * Helper class for storing frame capture mode properties
     *
     * Each frame capture mode is a combination of a capture type, a channel type, and a pixel
     * format. The mode id is equal to the pixel format name. This mode id is not unique, the same
     * mode id may exist for different channel types.
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

        /** Channel type (main or sub channel) for which this mode can be used. */
        private final int channelType;

        /** Human-readable channel name. */
        private final String channelName;

        /** Pixel format. */
        private final PixelFormat pixelFormat;

        /**
         * Constructs a new CaptureMode instance.
         *
         * The channel type and video mode are copied to member variables, the channel name matching
         * the specified channel type is set, and the mode id is set to the PixelFormat enumerator
         * name.
         *
         * @param   channelType     channel type for which this mode can be applied
         * @param   captureType     stream type for which this mode can be applied
         * @param   pixelFormat     video mode enumerator value
         */
       private CaptureMode(int channelType, int captureType, @NonNull PixelFormat pixelFormat)
        {
            this.captureType = captureType;
            switch (captureType)
            {
                case CAPTURE_TYPE_LIVE:
                    this.channelType = StreamOption.SUB_STREAM;
                    break;
                case CAPTURE_TYPE_STILL:
                    this.channelType = StreamOption.MAIN_STREAM;
                    break;
                default:
                    this.channelType = -1;
            }
            this.channelName = channelName(channelType);
            this.pixelFormat = pixelFormat;
            this.id = pixelFormat.name().toLowerCase();
        }

        /**
         * Returns a new CaptureMode instance.
         *
         * A new capture mode is returned if its mode id is valid. If not, a @c null value is
         * returned.
         *
         * @param   channelType     channel type for which this mode can be applied
         * @param   captureType     capture type for which this mode can be applied
         * @param   pixelFormat     video mode enumerator value
         *
         * @return  CaptureMode instance, or @c null if frame capture mode is not valid
         */
        @Nullable
        private static CaptureMode create(int channelType, int captureType, @NonNull PixelFormat pixelFormat)
        {
            CaptureMode captureMode = new CaptureMode(channelType, captureType, pixelFormat);
            return ((StringUtils.isBlank(captureMode.id) == false) && (captureMode.channelType != -1)) ? captureMode : null;

        }

        /**
         * Checks whether the specified capture mode is compatible with the current mode.
         *
         * Capture modes are considered compatible when they have the same id.
         *
         * @param   captureMode     frame capture mode to compare with
         *
         * @return  @c true if the capture mode is compatible, or @c false otherwise
         */
        private boolean match(@NonNull CaptureMode captureMode)
        {

            return (id.equalsIgnoreCase(captureMode.id));
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
            String resolution;
            switch (channelType)
            {
                case StreamOption.MAIN_STREAM:
                    resolution = MAIN_STREAM_RESOLUTION;
                    break;
                case StreamOption.SUB_STREAM:
                    resolution = SUB_STREAM_RESOLUTION;
                    break;
                default:
                    resolution = null;
            }
            if (resolution != null) return resolution + ", " + pixelFormat.toString() + " encoding";
            else return "invalid";
        }
    }

    /**
     * Helper class for storing frame decode mode properties.
     *
     * The frame decode mode specifies the format in which frames are stored in the frame buffer.
     * Since the camera only captures color images the mode id is always @c DECODE_COLOR. The image
     * type is the image type matching the pixel format specified by the frame capture mode, so no
     * conversion is required.
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

        /** Image type. */
        private final Image.Type imageType;

        /**
         * Constructs a new DecodeMode instance.
         *
         * The mode id is set to @c DECODE_COLOR, the image type is set to match the pixel format
         * specified by the frame capture mode.
         *
         * @param   captureMode     capture mode
         * @param   ignoredCaptureParams   optional frame capture parameters (unused)
         */
        private DecodeMode(@NonNull CaptureMode captureMode, Map<String, Object> ignoredCaptureParams)
        {
            id = DECODE_COLOR;
            switch (captureMode.pixelFormat)
            {
                case YUV:
                    imageType = Image.Type.YUV_NV21;
                    break;
                case RGB:
                    imageType = Image.Type.RGB565;
                    break;
                default:
                    imageType = null;
            }
        }

        /**
         * Creates and validates a new DecodeMode instance.
         *
         * If the @c imageType member variable is @c null the decode mode is not valid.
         *
         * @param   captureMode     capture mode
         * @param   captureParams   optional frame capture parameters (unused)
         *
         * @return  new DecodeMode instance, or @c null if decode mode is invalid
         */
        @Nullable
        private static DecodeMode create(CaptureMode captureMode, Map<String, Object> captureParams)
        {
            if (captureMode == null) return null;

            DecodeMode decodeMode = new DecodeMode(captureMode, captureParams);
            return (decodeMode.imageType != null) ? decodeMode : null;
        }

        /**
         * Returns @c true if the current frame decode mode is compatible with the specified mode.
         *
         * Decode modes are compatible if the mode ids and image types are the same.
         *
         * @param   decodeMode  frame decode mode to compare
         *
         * @return  @c true if decode mode is compatible, @c false if incompatible
         */
        private boolean match(@NonNull DecodeMode decodeMode)
        {
            return ((decodeMode.id == id) && (decodeMode.imageType == imageType));
        }
    }

    /**
     * Enumerates available video modes.
     *
     * Each video mode specifies a corresponding video frame decode type. Hardware decoding is
     * defined as one of the enumerator values even though hardware decoding is not actually
     * supported.
     */
    private enum PixelFormat
    {
        YUV(StreamOption.SOFTWARE_DECODE_NV21),     ///< 12-bit YCrCb (YUV) video
        RGB(StreamOption.SOFTWARE_DECODE_RGB565),   ///< 16-bit RGB video
        HARDWARE(StreamOption.HARDWARE_DECODE);     ///< H.264 Annex B video

        /** Video frame decode type. */
        private final int decodeType;

        /**
         * Creates enumerator value corresponding to specified video frame decode type.
         *
         * The video decode type must be one of the values defined by the Sanbot SDK @c StreamOption
         * class.
         *
         * @param decodeType        video frame decode type
         */
        PixelFormat(int decodeType)
        {
            this.decodeType = decodeType;
        }

        /**
         * Returns the decode for the specified video mode.
         *
         * @return  decode type for video mode
         */
        private int getDecodeType() { return decodeType; }
    }
}
