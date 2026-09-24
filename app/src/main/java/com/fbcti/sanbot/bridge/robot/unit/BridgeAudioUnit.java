/**
 * @file        BridgeAudioUnit.java
 * @brief       Implements BridgeAudioUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.MediaResult;
import com.fbcti.sanbot.bridge.robot.camera.interfaces.BridgeAudioManager;
import com.fbcti.sanbot.bridge.robot.mapping.SanbotMappings;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.FileUtils;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages Sanbot SDK camera operations.
 *
 * This class extends the abstract BridgeUnit class to provide access to the audio playback feature
 * of the Android audio player through the Android AudioManager API, and the audio recording feature
 * of the Sanbot HD camera through the SanbotCameraManager instance that owns the Sanbot
 * @c HDCameraManager API instance.
 *
 * @version     1.0.001
 * @date        11 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 *
 * @parblock @note @note
 * Audio volumes in this class are always specified by a percentage of the maximum volume, not the
 * actual Android volume.
 * @endparblock
 *
 * @parblock @note @note
 * The Sanbot @c HDCameraManagaer API can is not able to capture audio frames without also receiving
 * video frames. Since software video frame decoding is a CPU-intensive process decoding large video
 * frames may result in the system not being able to deliver audio frames in real-time as well. The
 * @c HDCameraManager @c SUB_STREAM channel produces the smallest video frames, so to minimize the
 * impact on the recorded audio quality the @c SUB_STREAM channel is used for audio recording.
 * @endparblock
 *
 * @todo    10/09/2026 - Check if new audio play request cancels current playback.
 */
public final class BridgeAudioUnit extends BridgeUnit
{
    /** Source label used for log messages. */
    private static final String TAG = "BridgeAudioUnit";

    /** Android TTS stream id used by the robot speaker route. */
    private static final int AUDIO_STREAM_TTS = 9;

    /** List of Android stream types supported by this class. */
    private static final int[] AUDIO_STREAM_TYPES = { AudioManager.STREAM_MUSIC, AUDIO_STREAM_TTS, AudioManager.STREAM_SYSTEM };

    /** External storage directory containing audio files. */
    private static final String AUDIO_DIRECTORY = FileUtils.BRIDGE_DATA_DIRECTORY + "/audio";

    /** Maximum number of seconds of audio to record. */
    public static final int AUDIO_RECORDING_MAX_DURATION = 300;

    /** Size of WAV header (which consequently is minimum size of audio data). */
    private static final int AUDIO_WAV_PCM_HEADER_SIZE = 44;

    /** Default Android output stream for robot speaker playback. */
    private static final int DEFAULT_AUDIO_STREAM = AudioManager.STREAM_MUSIC;

    /** Native Android audio manager providing play audio capabilities, */
    private AudioManager androidAudioManager = null;

    /** Android media player used for audio playback. */
    private MediaPlayer mediaPlayer;

    /** Bridge audio manager providing record audio capabilities, */
    private BridgeAudioManager bridgeAudioManager = null;

    /** Active background audio recording thread, or @c null if no recording is running. */
    private Thread recordingThread = null;

    /** Latest completed recording data, or @c null if no recording has completed yet. */
    private byte[] audioData = null;

    /** Current audio playback state data. */
    private final AudioPlayback audioPlayback = new AudioPlayback();

    /** Flag indicating that shutdown is in progress. */
    private boolean shuttingDown = false;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new SanbotAudioUnit instance.
     *
     * The base class constructor is called to copy the callback host to a member variable.
     *
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public BridgeAudioUnit(BridgeEventHost eventHost)
    {
        super(eventHost);
    }

    /**
     * @name Audio Unit Operations
     * @{
     */

    /**
     * Sets the volume for the specified audio stream.
     *
     * @param   streamType      audio stream for which to set volume
     * @param   volume          volume as percentage of maximum valume
     *
     * @return  DataResult instance containing operation result
     *
     * If the volume could not be set, the @c result property in the operation result object
     * contains the actual volumes for each audio stream.
     */
    @NonNull
    public synchronized DataResult setAudioVolume(Integer streamType, int volume)
    {
        if (androidAudioManager == null) return DataResult.notavailable("audio_manager");

        // Convert percenage volume to actual volume.
        int maxVolume = androidAudioManager.getStreamMaxVolume(streamType);
        int targetVolume = (maxVolume <= 0) ? 0 : Math.round((volume/100.0f) * maxVolume);
        if ((volume > 0) && (targetVolume == 0)) targetVolume = 1;

        // Set volume
        androidAudioManager.setStreamVolume(streamType, targetVolume, 0);

        // Get volume to check if volume was succesfully set.
        Integer newVolume = getVolume(streamType);
        if ((newVolume != null) && (newVolume == volume)) return DataResult.success();
        if (newVolume == null) return DataResult.failure("failed to get current volume");

        Map<String, Integer> data = new LinkedHashMap<>();
        data.put(SanbotMappings.toAudioStream(streamType) + "Volume", newVolume);
        return DataResult.failure("failed to set requested volume", data);
    }

    /**
     * Retrieves the volume for a single audio stream or all audio streams.
     *
     * @param   streamType      audio stream for which to retrieve volume
     *
     * If @p streamType equals @c null the volume for all supported audio streams is returned.
     *
     * @return  DataResult instance containing operation result
     *
     * The @c result property in the operation result object contains the audio stream volume(s).
     */
    @NonNull
    public synchronized DataResult getAudioVolume(Integer streamType)
    {
        int[] types = (streamType == null) ? AUDIO_STREAM_TYPES : new int[] { streamType };

        Map<String, Object> data = MapUtils.createMap();
        for (int type : types)
        {
            String stream = SanbotMappings.toAudioStream(type);
            Integer volume = getVolume(type);
            if (types.length > 1) data.put(stream + "Volume", (volume != null) ? volume : "not available");
        }
        return DataResult.success(data);
    }

    /**
     * Starts playing audio using the Andorid media player
     *
     * The Android media player is released if currently in use, and a new media player is created.
     * If the audio source is an URL, it is directly assigned to the media player. If the source is
     * a local audio file, the absolute path of the file is retrieved before assigning the file to
     * the media player. Preparing the media player is done in a separate thread to make sure the
     * main thred is not blocked. When preparing is complete an event is thrown, the handler for
     * this event requests the media player to start playing.
     *
     * @param   source          audio source
     * @param   sourceType      auudo source type (local file or URL)
     * @param   streamType      audio stream on which to play audio, or @c null to use default
     *
     * @return  DataResult instance containing operation result
     */
    public synchronized DataResult startAudioPlayback(String source, int sourceType, Integer streamType)
    {
        if (shuttingDown) return DataResult.notavailable("audio unit is shutting down");

        // If audio is being recorded playing audio is not allowed.
        if ((bridgeAudioManager == null) || (bridgeAudioManager.isRecorderAvailable() == false))
            return DataResult.notavailable("audio device is busy recording audio");

        // Set default stream type if stream type is not specified.
        if (streamType == null) streamType = DEFAULT_AUDIO_STREAM;

        releaseMediaPlayer(mediaPlayer);
        try
        {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(streamType);
            if (sourceType == BridgeProtocol.SOURCE_URL) mediaPlayer.setDataSource(source);
            else
            {
                File requestedFile = new File(source);
                if (requestedFile.isAbsolute()) return DataResult.failure("file path must be relative", source);
                File audioDirectory = new File(Environment.getExternalStorageDirectory(), AUDIO_DIRECTORY);
                File audioFile = new File(audioDirectory, source);
                String audioDirectoryPath = audioDirectory.getCanonicalPath();
                String audioFilePath = audioFile.getCanonicalPath();
                if ((audioFilePath.equals(audioDirectoryPath) == false) && (audioFilePath.startsWith(audioDirectoryPath + File.separator) == false))
                    return DataResult.failure("file path must stay inside bridge audio directory", source);
                if (audioFile.isFile() == false) return DataResult.failure("audio file not found", audioFile.getAbsolutePath());

                source = audioFile.getAbsolutePath();
                mediaPlayer.setDataSource(source);
            }

            String playbackId = UUID.randomUUID().toString();
            String playbackType = (sourceType == BridgeProtocol.SOURCE_URL) ? "url" : "file";
            String playbackStream = SanbotMappings.toAudioStream(streamType);

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
            {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer)
                {
                    handlePlaybackPrepared(mediaPlayer);
                }
            });

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
            {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer)
                {
                    handlePlaybackFinished(mediaPlayer, AudioPlayback.Status.COMPLETED);
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener()
            {
                @Override
                public boolean onError(MediaPlayer mediaPlayer, int type, int data)
                {
                    handlePlaybackFinished(mediaPlayer, AudioPlayback.Status.ERROR);
                    return true;
                }
            });

            audioPlayback.set(playbackId, source, playbackType, playbackStream, AudioPlayback.Status.PRPEPARING);
            mediaPlayer.prepareAsync();
            return DataResult.success(audioPlayback.buildStatusData());
        }
        catch (Exception e)
        {
            releaseMediaPlayer(mediaPlayer);
            BridgeLog.warning(TAG, "Audio playback failed", e);
            return DataResult.failure("audio playback failed", e.getClass().getSimpleName());
        }
    }

    /**
     * Starts recording audio using the Sanbot audio recorder.
     *
     * Audio recording is a asynchronous operation, so this method starts audio recording in a
     * worker thread and exits immediately. Recording ends if either the maximum audio length has
     * been reached or a request to stop recording is received.
     *
     * @param   length          number of seconds of audio to record
     * @param   filename        name of audio file, or @c null if file should not be saved
     * @param   params          optional audio recording parameters
     *
     * @return  DataResult instance specifying operation result
     */
    public synchronized DataResult startAudioRecording(int length, String filename, Map<String, Object> params)
    {
        if (shuttingDown) return DataResult.notavailable("audio unit is shutting down");

        // If audio is being recorded playing audio is not allowed.
        if (audioPlayback.isAvailable() == false) return DataResult.notavailable("audio device is busy playing audio");

        // Do not rely solely on the audio manager status: the worker may not have started yet.
        if (recordingThread != null) return DataResult.notavailable("audio device is busy recording audio");

        // Return if in emulator mode.
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();

        // Return error response if camera manager is not available.
        if ((bridgeAudioManager == null) || (bridgeAudioManager.isRecorderAvailable() == false))
            return setError(BridgeResult.Code.NOT_READY, "audio_record_failed", "Sanbot audio recording not available");

        // Open the dedicated audio recording stream if not already open. Return error response on failure.
        if (bridgeAudioManager.openAudioStream(params) < 0) return setError(bridgeAudioManager.getError());

        // Start recording in new worker thread.
        audioData = null;
        Thread worker = new Thread(new Runnable()
        {
            @Override
            public void run() {
                startWorkerThread(Thread.currentThread(), length, filename);
            }
        }, "AudioRecording");
        recordingThread = worker;
        worker.start();

        return DataResult.success();
    }

    /**
     * Stops all audio operations.
     *
     * Both audio playback and audio recording are stopped.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public DataResult stopAudio()
    {
        boolean playbackStopped = stopPlayback();
        boolean recordingStopped = stopRecording();
        synchronized (this)
        {
            if ((playbackStopped == false) || (recordingStopped == false)) return error;
            return DataResult.success();
        }
    }

    /**
     * Retrieves audio data.
     *
     * If a file name is specified the audio file with the specified name is returned if available.
     * If no file name is specified the cached audio data is returned if available,
     *
     * @param   filename        name of audio file to return, or @c null to return cached data
     *
     * @return  MediaResult instance containing audio data or error details
     */
    public MediaResult getRecordedAudio(String filename)
    {
        if (filename == null) return getAudioFromCache();
        else return getAudioFromFile(filename);
    }

    /** @} */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * The AndroidAudioManager and BridgeAudioManager instances are copied to member variables, and
     * the unit status is set.
     *
     * @param   androidAudioManager Android audio manager providing audio playback capabilities
     * @param   bridgeAudioManager  bridge audio manager providing audio recording capabilities
     */
    public synchronized void init(AudioManager androidAudioManager, BridgeAudioManager bridgeAudioManager)
    {
        logStatus();

        shuttingDown = false;

        // Copy audio managers.
        this.androidAudioManager = androidAudioManager;
        this.bridgeAudioManager = bridgeAudioManager;

        if (BuildConfig.EMULATOR_MODE) unitStatus = UnitStatus.EMULATED;
        else if ((this.androidAudioManager == null) || (bridgeAudioManager == null) || (bridgeAudioManager.isRecorderAvailable() == false))
        {
            // Set audio managers to null.
            this.androidAudioManager = null;
            this.bridgeAudioManager = null;
            unitStatus = UnitStatus.NOTINITIALIZED;
        }
        audioData = null;
        recordingThread = null;
        unitStatus = UnitStatus.STARTED;

        logStatus();
    }

    /**
     * Performs unit shutdown tasks.
     *
     * All audio operations are stopped and the unit status is reset.
     */
    @Override
    public void shutdown()
    {
        synchronized (this)
        {
            shuttingDown = true;
            unitStatus = UnitStatus.SHUTDOWN;
        }
        stopAudio();
        synchronized (this)
        {
            audioData = null;
            recordingThread = null;
        }
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
        data.put("audioVolume", buildAudioVolumeData());
        data.put("audioPlayback", audioPlayback.buildStatusData());
        Map<String, Object> recordingData = new LinkedHashMap<>();
        recordingData.put("status", (recordingThread != null) ? "recording" : "idle");
        recordingData.put("storedByteCount", (audioData != null) ? audioData.length : 0);
        data.put("audioRecording", recordingData);
        return data;
    }

    /**
     * Builds a data map containing audio volume data.
     *
     * @return  Java Map instance containing audio volum data
     */
    @NonNull
    public synchronized Map<String, Object> buildAudioVolumeData()
    {
        Map<String, Object> data = MapUtils.createMap();
        for (int streamType : AUDIO_STREAM_TYPES)
        {
            String stream = SanbotMappings.toAudioStream(streamType);
            Integer volume = getVolume(streamType);
            data.put(stream + "Volume", (volume != null) ? volume : "not available");
        }
        return data;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Stops audio playback.
     *
     * To stop playing audio using the Android media player, releaseMediaPlayer() is called.
     *
     * @return  this method always returns @c true
     */
    private synchronized boolean stopPlayback()
    {
        releaseMediaPlayer(mediaPlayer);
        BridgeLog.debug(TAG, "Audio playback stopped");
        return true;
    }

    /**
     * Stops audio recording.
     *
     * The active worker and audio manager are captured while holding the unit lock. The manager is
     * then asked to stop, after which the method waits for the worker without holding the unit lock.
     * This allows the worker to acquire the lock and publish its completed recording state.
     *
     * @return  DataResult instance specifying operation result
     */
    private boolean stopRecording()
    {
        final Thread workerThread;
        final BridgeAudioManager audioManager;
        synchronized (this)
        {
            workerThread = recordingThread;
            audioManager = bridgeAudioManager;
            if ((audioManager == null) || (workerThread == null))
            {
                BridgeLog.info(TAG, "Audio recording not active");
                return true;
            }

            // Signal the exact session captured above while state is protected by the unit lock.
            audioManager.stopAudioRecording();
        }

        // Never wait while holding this monitor: the worker needs it to publish its final state.
        if (waitForWorkerThreadStopped(workerThread) == false)
        {
            synchronized (this)
            {
                setError(BridgeResult.Code.FAILURE, "stop_recording_failed", null);
            }
            return false;
        }
        return true;
    }

    /**
     * Handles notification that the active media player is prepared.
     *
     * @param   player          media player that raised the callback
     */
    private void handlePlaybackPrepared(MediaPlayer player)
    {
        Map<String, Object> eventData;
        try
        {
            synchronized (this)
            {
                if ((player == null) || (mediaPlayer != player)) return;
                player.start();
                audioPlayback.setStatus(AudioPlayback.Status.PLAYING);
                eventData = audioPlayback.buildStatusData();
            }
        }
        catch (RuntimeException e)
        {
            BridgeLog.warning(TAG, "Could not start prepared audio playback", e);
            handlePlaybackFinished(player, AudioPlayback.Status.ERROR);
            return;
        }
        publishEvent(BridgeProtocol.MODULE_AUDIO, BridgeProtocol.ACTION_PLAY, eventData);
    }

    /**
     * Finalizes the active playback session and publishes its final status.
     *
     * @param   player          media player that raised the callback
     * @param   status          completed or error status
     */
    private void handlePlaybackFinished(MediaPlayer player, AudioPlayback.Status status)
    {
        Map<String, Object> eventData;
        synchronized (this)
        {
            // Ignore callbacks belonging to a player that has already been replaced or stopped.
            if ((player == null) || (mediaPlayer != player)) return;
            audioPlayback.setStatus(status);
            eventData = audioPlayback.buildStatusData();
            releaseMediaPlayer(player);
        }
        publishEvent(BridgeProtocol.MODULE_AUDIO, BridgeProtocol.ACTION_PLAY, eventData);
    }

    /**
     * Releases the Android media player.
     *
     * @param   mediaPlayer     Android @c MediaPlayer instance to release
     */
    @Nullable
    private synchronized void releaseMediaPlayer(MediaPlayer mediaPlayer)
    {
        if (mediaPlayer == null) return;
        boolean activePlayer = (this.mediaPlayer == mediaPlayer);
        if (activePlayer) this.mediaPlayer = null;
        try
        {
            mediaPlayer.stop();
        }
        catch (RuntimeException ignored) {}
        try
        {
            mediaPlayer.release();
        }
        catch (RuntimeException ignored) {}
        if (activePlayer) audioPlayback.reset();
    }

    /**
     * Starts the audio recording session.
     *
     * The @c recordAudio() method implemented by  SanbotCameraManager is called to start recording.
     * A callback methid is set that checks if a stop request for the thread is received. The
     * recorded audio is copied to the @c audioData member variable after recording is complete. If
     * a file name is specified, the recorded audio is saved to file.
     *
     * @param   thread          worker thread that is requested to start
     * @param   length          number of seconds of audio to record
     * @param   filename        name of audio file, or @c null if file should not be saved
     */
    private void startWorkerThread(Thread thread, int length, String filename)
    {
        // Create a stable copy of the audio manager because recording is a long-running operation
        // that must not hold the unit lock.
        BridgeAudioManager audioManager = null;
        byte[] wavData = null;
        try
        {
            synchronized (this)
            {
                audioManager = this.bridgeAudioManager;
            }
            if (audioManager != null)
            {
                wavData = audioManager.recordAudio(length);
                Map<String, Object> eventData = MapUtils.createMap("status", "completed", "size", wavData.length);
                if (filename != null)
                {
                    // Save the file. The saveAudio() method returns the absolute file name.
                    filename = saveAudio(wavData, filename);
                    if (filename != null) eventData.put("file", filename);
                    else eventData.put("file", "<save_failed>");
                }
                publishEvent(BridgeProtocol.MODULE_AUDIO, BridgeProtocol.ACTION_RECORD, eventData);
            }
        }
        finally
        {
            // The worker owns the stream registration and releases it before making the unit
            // available for a subsequent recording session.
            if (audioManager != null)
            {
                try
                {
                    audioManager.releaseAudioStream();
                }
                catch (RuntimeException e)
                {
                    BridgeLog.warning(TAG, "Could not release audio recording stream", e);
                }
            }
            synchronized (this)
            {
                if (this.recordingThread == thread)
                {
                    audioData = wavData;
                    this.recordingThread = null;
                }
            }
        }
    }

    /**
     * Waits for the audio recording worker thread to stop.
     *
     * @param   workerThread    current audio recording thread
     *
     * @return  @c true if thread is stopped, @c false on error
     */
    private boolean waitForWorkerThreadStopped(Thread workerThread)
    {
        if (workerThread != null)
        {
            try
            {
                workerThread.join();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     * Retrieves audio data from cache.
     *
     * If a recording is currently in progress it is stopped before the in-memory @e WAV file is
     * returned.
     *
     * @return  MediaResult instance containing audio data
     */
    private MediaResult getAudioFromCache()
    {
        // Stop an active recording before returning its in-memory WAV data.
        stopRecording();

        synchronized (this)
        {
            if ((audioData == null) || (audioData.length <= AUDIO_WAV_PCM_HEADER_SIZE))
                return MediaResult.failure(BridgeResult.Code.NOT_AVAILABLE, "no recorded audio available");
             return MediaResult.success(audioData.clone(), toMimeType("WAV"));
        }
    }

    /**
     * Retrieves audio data retrieved from specified file.
     *
     * The file is retrieved from the directory speciifed by the @c AUDIO_DIRECTORY directory
     * constant.
     *
     * @param   filename        name of audio file to retrieve
     *
     * @return  MediaResult instance containing audio data or error details
     */
    @NonNull
    private MediaResult getAudioFromFile(String filename)
    {
        // Return error response if filename is not valid.
        if (StringUtils.isBlank(filename))
            return MediaResult.failure(BridgeResult.Code.INVALID_PARAMS, "audio file name is required");

        try
        {
            // Add extension if not already present.
            if (filename.lastIndexOf('.') <= filename.lastIndexOf(File.separatorChar)) filename += ".wav";

            File audioDirectory = new File(Environment.getExternalStorageDirectory(), AUDIO_DIRECTORY);
            File audioFile = FileUtils.resolveChildFile(audioDirectory, filename);
            if (audioFile.isFile() == false) throw new IOException(audioFile.getName() +  " is not a file");

            // Read file contents.
            byte[] wavData = FileUtils.readFile(audioFile);
            if (wavData.length <= AUDIO_WAV_PCM_HEADER_SIZE) throw new IOException(audioFile.getName() +  " does not contain audio data");

            return MediaResult.success(wavData, toMimeType("WAV"));
        }
        catch (Exception e)
        {
            BridgeLog.warning(TAG, "Failed to read audio file " + filename, e);
            return MediaResult.failure(BridgeResult.Code.FAILURE, "failed to read audio file");
        }
    }

    /**
     * Save the audio data to a file.
     *
     * The file is saved in the directory speciifed by the @c AUDIO_DIRECTORY directory constant. If
     * that directory does not yet exist it is created.
     *
     * @param   wavData         byte array containing WAV data
     * @param   filename        name of audio file, or @c null if file should not be saved
     *
     * @return  @c absolute path name if file was successfully saved, @c null on failure
     */
    private String saveAudio(byte[] wavData, String filename)
    {
        // Return null if WAV data is not avaiable or file name is not specified.
        if ((wavData == null) || (StringUtils.isBlank(filename))) return null;

        try
        {
            // Add extension if not already present.
            if (filename.lastIndexOf('.') <= filename.lastIndexOf(File.separatorChar)) filename += ".wav";

            // Create the bridge audio directory if necessary.
            File audioDirectory = new File(Environment.getExternalStorageDirectory(), AUDIO_DIRECTORY);
            FileUtils.ensureDirectory(audioDirectory);

            // Resolve and validate the requested path before writing the WAV data.
            File audioFile = FileUtils.resolveChildFile(audioDirectory, filename);

            // Write file contents.
            FileUtils.writeFile(audioFile, wavData);
            BridgeLog.debug(TAG, "Audio saved to " + audioFile.getPath());
            return audioFile.getAbsolutePath();
        }
        catch (Exception e)
        {
            BridgeLog.warning(TAG, "Failed to save audio", e);
            return null;
        }
    }

    @Nullable
    /**
     * Retrieves the volume for the specified audio streams.
     *
     * The actual volume and maximum volume for the stream are retrieved, and the actual volume is
     * returned as a percentage of the maximum value. If the Android audio manager does not return a
     * positive value for the maximum volume the value is considered invalid and a @c null value is
     * returned.
     *
     * @param   streamType      audio stream for which to retrieve volume
     *
     * @return  @c Integer specifying audio volume, or @c null on failure
     */
    private Integer getVolume(int streamType)
    {
        if (androidAudioManager == null) return null;

        int streamVolume = androidAudioManager.getStreamVolume(streamType);
        int maxVolume = androidAudioManager.getStreamMaxVolume(streamType);
        if (maxVolume <= 0) return null;
        return Math.round((100.0f*streamVolume)/maxVolume);
    }

    /**
     * Helper class for managing audio playback details.
     */
    private static final class AudioPlayback
    {
        /** Unique id for current playback operation. */
        private String id = "";

        /** Audio source. */
        private String source = "";

        /** Audio source type (URL or local file). */
        private String type = "";

        /** Audio stream used for playing audio. */
        private String stream = SanbotMappings.toAudioStream(DEFAULT_AUDIO_STREAM);

        /** Playback status. */
        private Status status = Status.IDLE;

        /**
         * Copies the specified values to member variable.
         *
         * @param   id          unique id for current playback operation
         * @param   source      audio source
         * @param   type        audio source type (URL or local file)
         * @param   stream      audio stream used for playing audio
         * @param   status      audio playback status
         */
        void set(String id, String source, String type, String stream, Status status)
        {
            this.id = (id != null) ? id : "";
            this.source = (source != null) ? source : "";
            this.type = (type != null) ? type : "";
            this.stream = (stream != null) ? stream : "";
            this.status = status;
        }

        /**
         * Resets member variables to iheir initial values.
         */
        void reset()
        {
            id = "";
            source = "";
            type = "";
            stream = SanbotMappings.toAudioStream(DEFAULT_AUDIO_STREAM);
            status = Status.IDLE;
        }

        /**
         * Sets the current playback status.
         *
         * @param   status     new playback status
         */
        void setStatus(Status status)
        {
            this.status = status;
        }

        /**
         * Returns @c true if media player is available for playing audio.
         *
         * @return  @c true if media player is available for playing audio, @c false if unavialble
         */
        boolean isAvailable()
        {
            return (status != Status.PRPEPARING) && (status != Status.PLAYING);
        }

        /**
         * Build a data map containing playback status data.
         *
         * @return  Java @c Map instance containing playback status data
         */
        @NonNull
        Map<String, Object> buildStatusData()
        {
            if (status != Status.IDLE) return MapUtils.createMap(
                "id", id,
                "source", source,
                "type", type,
                "stream", stream,
                "status", status.name().toLowerCase());
            else return MapUtils.createMap("status", status.name().toLowerCase());
        }

        /**
         * Enumerator specifying audio playback status.
         */
        enum Status
        {
            IDLE,
            PRPEPARING,
            PLAYING,
            COMPLETED,
            ERROR
        }
    }
}
