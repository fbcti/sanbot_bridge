/**
 * @file        BridgeCameraManager.java
 * @brief       Declares BridgeCameraManager interface.
 */
package com.fbcti.sanbot.bridge.robot.camera.interfaces;

import android.content.Context;

import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.media.Image;

import java.util.Map;

/**
 * Defines the common camera management contract used by all bridge camera implementations.
 *
 * Implementations manage the lifecycle of a specific camera backend, expose methods for opening and
 * releasing live video capture, and provide buffer-backed access to the most recently received
 * video frame. Snapshot and still-image capture are exposed through separate methods because the
 * underlying camera APIs may implement those capture paths differently.
 *
 * The interface also standardizes access to optional face-capture data, camera status and feature
 * metadata, camera availability checks, and the last reported error so higher-level bridge code
 * can interact with different camera managers through a single API.
 *
 * @version     1.0.001
 * @date        7 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public interface BridgeCameraManager
{
    /**
     * Initializes the camera manager.
     *
     * @param   context         Android application context
     * @param   args            additional initialization arguments
     */
    void init(Context context, Object... args);

    /**
     * Shuts down the camera manager.
     */
    void shutdown();

    /**
     * Resets the camera manager.
     */
    DataResult reset();

    /**
     * Starts streaming live video.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  live stream handle on success, or -1 if stream could not be opened
     */
    long openVideoStream(Map<String, Object> captureParams);

    /**
     * Retrieves the most recently buffered frame from the video frame buffer.
     *
     * @return  latest video frame as @e MJPEG image, or @c null if frame is not available
     */
    Image getVideoFrame();

    /**
     * Unregisters a video capture client from the live stream.
     */
    void releaseVideoStream();

    /**
     * Captures a snapshot image.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  snapshot image, or @c null if no snapshot image was captured
     */
    Image getSnapshotImage(Map<String, Object> captureParams);

    /**
     * Captures a still image.
     *
     * @param   captureParams   optional frame capture parameters
     *
     * @return  @c image instance success, or @c null if no image frame was captured
     */
    Image getStillImage(Map<String, Object> captureParams);

    /**
     * Retrieves the latest face image from the image buffer.
     *
     * @param   lifo        last in-first out index of face capture to retrieve
     *
     * @return  lastest image from buffer, or @c null if image is not available
     */
    Image getFaceImage(int lifo);

    /**
     * Builds a data map containing current camera manager status data.
     *
     * @return  Java @c Map instance containing status data
     */
    Map<String, Object> buildStatusData();

    /**
     * Builds a data map containing camera feature data.
     *
     * @return  Java @c Map instance containing feature data
     */
    Map<String, Object> buildFeatureData();

    /**
     * Returns @c true if camera is available.
     *
     * @return  @c true if camera is available, @c false if camera is not available
     */
    boolean isCameraAvailable();

    /**
     * Returns the last reported error.
     *
     * @return  DataResult instance containing last reported error.
     */
    DataResult getError();

    /**
     * Enumerator defining camera device statuses.
     */
    enum CameraStatus
    {
        NOT_INITIALIZED,            ///< Camera device is not yet initialized.
        AVAILABLE,                  ///< Camera device is available.
        LIVECAPTURE,                ///< Camera is busy capturing live video.
        STILLCAPTURE,               ///< Camera is busy capturing still image.
        STOPPED,                    ///< Camera device has been shut down.
        ERROR                       ///< Camera device is in error state.
    }
}