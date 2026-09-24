/**
 * @file        BridgeAudioManager.java
 * @brief       Declares BridgeAudioManager interface.
 */
package com.fbcti.sanbot.bridge.robot.camera.interfaces;

import android.content.Context;

import com.fbcti.sanbot.bridge.robot.DataResult;

import java.util.Map;

/**
 * Defines the common audio device management contract used by all audio device implementations.
 *
 * Implementations manage the lifecycle of a specific audio backend, expose methods for opening and
 * releasing the audio stream, and for starting and stopping audio recording.
 *
 * @version     1.0.001
 * @date        7 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public interface BridgeAudioManager
{
    /**
     * Initializes the audio manager.
     *
     * @param   context         Android application context
     * @param   args            additional initialization arguments
     */
    void init(Context context, Object... args);

    /**
     * Shuts down the audio manager.
     */
    void shutdown();

    /**
     * Registers a new audio capture client with the live stream.
     *
     * @param   recordingParams optional audio recording parameters
     *
     * @return  live stream handle on success, or -1 if stream could not be opened
     */
    long openAudioStream(Map<String, Object> recordingParams);

    /**
     * Starts recording audio.
     *
     * @param   length          number of seconds of audio to record
     *
     * @return  array of bytes containing recorded audio as in-memory @c WAV file
     */
    byte[] recordAudio(int length);

    /**
     * Stops recording audio.
     *
     */
    void stopAudioRecording();

    /**
     * Unregisters an audio capture client from the live stream.
     */
    void releaseAudioStream();

    /**
     * Builds a data map containing camera manager status data.
     *
     * @return  Java @c Map instance containing status data
     */
    public Map<String, Object> buildStatusData();

    /**
     * Builds a data map containing live audio stream status data.
     *
     * @return  Java @c Map instance containing status data
     */
    Map<String, Object> buildAudioData();

    /**
     * Returns @c true if audio recorder is available.
     *
     * @return  @c true if audio recorder is available, @c false if audio recorder is not available
     */
    boolean isRecorderAvailable();

    /**
     * Returns current audio status.
     *
     * @return  AudioStatus enumerator value specifying audio status
     */
    AudioStatus getAudioStatus();

    /**
     * Returns the last reported error.
     *
     * @return  DataResult instance containing last reported error.
     */
    DataResult getError();

    /**
     * Enumerator defining audio device status.
     */
    enum AudioStatus
    {
        NOT_INITIALIZED,            ///< Audio device is not yet initialized.
        AVAILABLE,                  ///< Audio device is available.
        PLAYING,                    ///< Audio devive is actively playing.
        RECORD_STARTING,            ///< Audio recording stream is open and worker is starting.
        RECORDING,                  ///< Audio devive is actively recording.
        RECORD_STOPPING,            ///< A request to stop recording is received.
        STOPPED,                    ///< Audio device has been shut down.
        ERROR                       ///< Audio device is in error state.
    }
}
