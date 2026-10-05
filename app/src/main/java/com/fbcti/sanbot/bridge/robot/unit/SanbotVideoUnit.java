/**
 * @file        SanbotVideoUnit.java
 * @brief       Implements SanbotVideoUnit class.
 * @since       1.0.002
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.MediaResult;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.FileUtils;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages video recording through the Sanbot recorder broadcasts.
 *
 * Video recording is not managed through the SDK, but by broadcasting an intent directly to the
 * Sanbot core. The video data is initially stored in a temporary file. When video recording is
 * stopped, the temporary file is renamed and given an @c rec extension. This class therefore waits
 * asynchronously for a new @c rec file and publishes a @e video:record event when finalization
 * completes or times out.
 *
 * @parblock @note
 * A Sanbot @e REC recording is not a standard playable media container. The format observed on the
 * current recorder consists of proprietary packets with a 32-byte header followed by payload data.
 * Packet types 1 and 2 contain an H.264 Annex B video stream; packet type 3 contains mono 8 kHz
 * G.711 A-law audio. The @e rec extension identifies this wrapper format, not a video codec, and
 * changing the extension does not make the file playable. This class stores and returns the
 * recorder output unchanged.
 *
 * The @c tools/unpack_sanbot_rec.py utility can convert a recording to MP4. It locates the first
 * wrapped H.264 SPS packet, removes the proprietary packet headers, extracts the H.264 and A-law
 * streams, and invokes FFmpeg to remux the video and encode the audio as AAC. Python 3 and FFmpeg
 * on the system path are required:
 * @code{.sh}
 * python tools/unpack_sanbot_rec.py recording.rec recording.mp4 --frame-rate 20
 * @endcode
 * Raw @e h264 and @e alaw streams can be retained with @c --keep-streams. Because the wrapper does
 * not provide timestamps usable by FFmpeg, the utility generates them from the specified frame
 * rate; the default is 20 frames per second and must be overridden if the recording used a
 * different rate. The original @e REC file is not modified.
 * @endparblock
 *
 * @version     1.0.003
 * @date        5 Oct 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 * @since       1.0.001
 * @changelog
 * - CHANGE: minor comment updates
 */
public final class SanbotVideoUnit extends BridgeUnit
{
    /** Source label used for log messages. */
    private static final String TAG = "SanbotVideoUnit";

    /** Broadcast action that starts the Sanbot video recorder. */
    private static final String ACTION_START_RECORD = "com.qihancloud.action.START_RECORD";

    /** Broadcast action that stops the Sanbot video recorder. */
    private static final String ACTION_STOP_RECORD = "com.qihancloud.action.STOP_RECORD";

    /** External storage directory in which the Sanbot video recorder creates recordings. */
    private static final String VIDEO_SOURCE_DIRECTORY = Environment.DIRECTORY_MOVIES + "/Cruise";

    /** External storage directory in which to save video files. */
    private static final String VIDEO_TARGET_DIRECTORY = FileUtils.BRIDGE_DATA_DIRECTORY + "/video";

    /** Video file extension. */
    private static final String VIDEO_FILE_EXTENSION = "rec";

    /** Default maximum duration of a video recording in seconds. */
    public static final int VIDEO_RECORDING_DEFAULT_DURATION = 300;

    /** Estimated number of bytes written for each second of recorded video. */
    private static final long VIDEO_ESTIMATED_BYTES_PER_SECOND = 256L * 1024L;

    /** Delay between checks for a completed video recording. */
    private static final long VIDEO_FILE_POLL_DELAY_SECONDS = 2L;

    /** Maximum number of checks for a completed video recording. */
    private static final int VIDEO_FILE_POLL_MAX_COUNT = 10;

    /** Android application context used to send recorder broadcasts. */
    private final Context context;

    /** Executor used while waiting for the recorder to finalize a file. */
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    /** Pending task that checks whether the latest video recording has been completed. */
    private ScheduledFuture<?> videoFileWaitTask;

    /** Pending task that automatically stops the active video recording. */
    private ScheduledFuture<?> videoStopTask;

    /** Target video file name. If not specified, the video file retains its original name. */
    private String targetFilename = null;

    /** Current video recording lifecycle state. */
    private Status recordingState = Status.IDLE;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new SanbotVideoUnit instance.
     *
     * @param   context         Android context used to send recorder broadcasts
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public SanbotVideoUnit(@NonNull Context context, BridgeEventHost eventHost)
    {
        super(eventHost);
        this.context = context.getApplicationContext();
        unitStatus = UnitStatus.STARTED;
    }

    /**
     * @name Video Unit Operations
     * @{
     */

    /**
     * Starts recording video.
     *
     * At least 150% of the estimated video file size must be available. If this is not the case an
     * error response is returned. After starting video recording, a task is scheduled that executes
     * stopRecording() after the specified duration.
     *
     * @param   filename        name of video file
     * @param   duration        maximum recording duration in seconds, or 0 for default duration
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult startRecording(String filename, int duration)
    {
        // Return error response if video recorded is not available.
        if (unitStatus == UnitStatus.SHUTDOWN) return DataResult.notavailable("video_recorder");

        // Check if not already recording.
        if (recordingState != Status.IDLE) return DataResult.failure("video recording already active");

        // Set default duration if duration is 0.
        if (duration <= 0) duration = VIDEO_RECORDING_DEFAULT_DURATION;

        // Ensure external storage has at least 150% of the estimated recording size available.
        long estimatedBytes = VIDEO_ESTIMATED_BYTES_PER_SECOND*duration;
        Boolean spaceAvailable = FileUtils.checkAvailableSpace( Environment.getExternalStorageDirectory(), estimatedBytes, 150);
        if (spaceAvailable == null) return DataResult.failure("storage_space_compute_error");
        if (spaceAvailable == false) return DataResult.failure("storage_space_insufficient");

        // Start recording.
        broadcastIntent(ACTION_START_RECORD);
        targetFilename = filename;
        recordingState = Status.RECORDING;

        // Automatically stop this recording after its configured maximum duration. The session id
        // prevents a canceled task that has already started from stopping a subsequent recording.
        videoStopTask = executor.schedule(new Runnable()
        {
            @Override
            public void run()
            {
                synchronized (SanbotVideoUnit.this)
                {
                    if (recordingState != Status.RECORDING) return;
                    videoStopTask = null;
                    stopRecording();
                }
            }
        }, duration, TimeUnit.SECONDS);

        return DataResult.success();
    }

    /**
     * Stops recording video.
     *
     * If video is currently being recorded, video recording is stopped by broadcasting an intent
     * directly to the Sanbot core. Before stopping, a list of stored video files is created. After
     * recording is stopped, waitForVideoFile() is called to wait until the the updated list of
     * video files contains a new entry.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult stopRecording()
    {
        // Return error response if video recorded is not available.
        if (unitStatus == UnitStatus.SHUTDOWN) return DataResult.notavailable("video_recorder");

        // Check if recording is active.
        if (recordingState == Status.IDLE) return DataResult.failure("video recording not active");
        if (recordingState == Status.FINALIZING) return DataResult.failure("video recording is being finalized");

        // A manual stop makes the scheduled automatic stop unnecessary.
        cancelVideoStopTask();

        // Snapshot video recording files so the file produced by this recording can be identified.
        Set<String> existingFiles = listVideoFiles();

        // Stop recording.
        broadcastIntent(ACTION_STOP_RECORD);
        recordingState = Status.FINALIZING;

        // Wait for video file to become available.
        waitForVideoFile(existingFiles, 0);

        return DataResult.success();
    }

    /**
     * Rettuns the list of available video recordings.
     *
     * The list of files with a name containing the video file extension in the script directory is
     * retrieved.
     *
     * @return  DataResult instance containing list of video recordings
     */
    @NonNull
    public synchronized DataResult getRecordingList()
    {
        List<String> recordings = FileUtils.listFileNames(VIDEO_TARGET_DIRECTORY, VIDEO_FILE_EXTENSION);
        return DataResult.success(MapUtils.createMap("recordings", recordings));
    }

    /**
     * Remove a single video recording or all video recordings.
     *
     * @param   filename        name of video file to remove, or @c null to remove all video files
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult removeRecording(String filename)
    {
        File videoDirectory = new File(Environment.getExternalStorageDirectory(), VIDEO_TARGET_DIRECTORY);
        try
        {
            if (filename == null)
            {
                Set<String> failedFiles = FileUtils.deleteRegularFiles(videoDirectory);
                if (failedFiles.isEmpty() == false)
                    return DataResult.failure("failed to remove one or more video files", failedFiles);
            }
            else FileUtils.deleteChildFile(videoDirectory, filename, VIDEO_FILE_EXTENSION);

            return DataResult.success();
        }
        catch (IOException e)
        {
            BridgeLog.warning(TAG, "Failed to remove video file " + filename, e);
            return DataResult.failure("failed to remove video file", e.getMessage());
        }
    }

    /**
     * Retrieves video data retrieved from specified file.
     *
     * If a video file name is specified the file is retrieved from the directory specified by the
     * @c VIDEO_DIRECTORY constant. If no file name is specified, findLatestVideoFile() is called
     * to retrieve the most recent saved video file.
     *
     * @param   filename        name of audio file to retrieve
     *
     * @return  MediaResult instance containing audio data or error details
     */
    @NonNull
    public synchronized MediaResult getRecording(String filename)
    {
        try
        {
            File videoFile;
            if (StringUtils.isBlank(filename) == false)
            {
                // Add extension if not already present.
                if (filename.lastIndexOf('.') <= filename.lastIndexOf(File.separatorChar)) filename += "." + VIDEO_FILE_EXTENSION;

                File videoDirectory = new File(Environment.getExternalStorageDirectory(), VIDEO_TARGET_DIRECTORY);
                videoFile = FileUtils.resolveChildFile(videoDirectory, filename);
            }
            else videoFile = findLatestVideoFile(new HashSet<>());

            if (videoFile == null) throw new IOException("No video file found");
            else if (videoFile.isFile() == false) throw new IOException(videoFile.getName() +  " is not a file");

            // Read file contents.
            byte[] videoData = FileUtils.readFile(videoFile);

            return MediaResult.success(videoData, toMimeType("RAW"));
        }
        catch (Exception e)
        {
            BridgeLog.warning(TAG, "Failed to read video file " + filename, e);
            return MediaResult.failure(BridgeResult.Code.FAILURE, "failed to read video file");
        }
    }

    /**
     * @}
     */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit shutdown tasks.
     *
     * If video recording is currently active the current recording is stopped, if waiting for a
     * video file the wait is canceled.
     */
    @Override
    public synchronized void shutdown()
    {
        // If video recording is currently active stop recording.
        if (recordingState == Status.RECORDING) broadcastIntent(ACTION_STOP_RECORD);

        // If currently waiting for video file cancel wait.
        if (videoFileWaitTask != null) videoFileWaitTask.cancel(false);
        cancelVideoStopTask();

        // Shut down executor task and update status.
        videoFileWaitTask = null;
        executor.shutdownNow();
        recordingState = Status.IDLE;
        unitStatus = UnitStatus.SHUTDOWN;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /** Cancels and clears the pending automatic video stop task, if any. */
    private void cancelVideoStopTask()
    {
        if (videoStopTask != null) videoStopTask.cancel(false);
        videoStopTask = null;
    }

    /**
     * Schedules a check for the recording produced after a request to stop recording.
     *
     * The first check is scheduled. If the file is available, videoFileAvailable() is called to
     * publish an event with the file name in the event data. If not available this function is
     * called recursively until either the file is available, or the maximum number of attempts is
     * exceeded. If the maximum number of attempts is exceeded videoFileFailure() is called to
     * publish an event to notify video recording failed.
     *
     * @param   existingFiles   list of video files that exist prior request to stop recording
     * @param   pollCount       counter for poll
     */
    private synchronized void waitForVideoFile(final Set<String> existingFiles, int pollCount)
    {
        // If the unit is not active or video recording is already finalizing there is nothing to
        // do.
        if ((unitStatus == UnitStatus.SHUTDOWN) || (recordingState != Status.FINALIZING) || (executor.isShutdown()))
            return;

        // Schedule the first check.
        videoFileWaitTask = executor.schedule(new Runnable()
        {
            @Override
            public void run()
            {
                synchronized (SanbotVideoUnit.this)
                {
                    if ((unitStatus == UnitStatus.SHUTDOWN) || (recordingState != Status.FINALIZING)) return;
                }

                // Check if new file is available.
                File videoFile = findLatestVideoFile(existingFiles);
                if (videoFile != null)
                {
                    videoFileAvailable(videoFile);
                    return;
                }

                // New file is not available. Check if maximum number of checks is exceeded.
                int nextPollCount = pollCount + 1;
                if (nextPollCount >= VIDEO_FILE_POLL_MAX_COUNT)
                {
                    videoFileFailure();
                    return;
                }

                // Schedule new check.
                waitForVideoFile(existingFiles, nextPollCount);
            }
        }, VIDEO_FILE_POLL_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Returns the names of all completed video recordings currently in the recorder directory.
     *
     * @return  Java @c Set instance containing video file names.
     */
    @NonNull
    private Set<String> listVideoFiles()
    {
        Set<String> names = new HashSet<>();
        File[] files = getRecordedVideoDirectory().listFiles();
        if (files == null) return names;

        for (File file : files)
        {
            if (isVideoFile(file)) names.add(file.getName());
        }
        return names;
    }

    /**
     * Returns the newest video file with a file name not present before recording stopped.
     *
     * The new list of files is retrieved, and the newest file that is included in this list but not
     * in the list referenced by the @p existingFiles parameter is returned.
     *
     * @param   existingFiles   list of video files that exist prior request to stop recording
     *
     * @return  Java @c File instance representing newest video file, or @c null if file not found
     */
    @Nullable
    private File findLatestVideoFile(@NonNull Set<String> existingFiles)
    {
        File[] files = getRecordedVideoDirectory().listFiles();
        if (files == null) return null;

        File newest = null;
        for (File file : files)
        {
            // Ignore files that appear in the list of existing files.
            if ((isVideoFile(file) == false) || (existingFiles.contains(file.getName()))) continue;

            // If file is newer than current newest fils replace newest file.
            if ((newest == null) || (file.lastModified() > newest.lastModified())) newest = file;
        }
        return newest;
    }

    /**
     * Finalizes a succesfull video recording.
     *
     * The temporary video file is moved to the directory specified by the @c VIDEO_TARGET_DIRECTORY
     * constant. The status is reset, and an event containing the operation result and the name of
     * the video file is published.
     *
     * @param   tempFile        recorded video file
     */
    private synchronized void videoFileAvailable(@NonNull File tempFile)
    {
        if ((unitStatus == UnitStatus.SHUTDOWN) || (recordingState != Status.FINALIZING)) return;

        // Move file to target directory.
        File targetDirectory = new File(Environment.getExternalStorageDirectory(), VIDEO_TARGET_DIRECTORY);
        File targetFile = FileUtils.moveFile(tempFile, targetDirectory, targetFilename);
        if (targetFile == null)
        {
            BridgeLog.info(TAG, "Failed to move video file " + tempFile.getName() + " to recording " + targetDirectory.getName());
            publishEvent(BridgeProtocol.MODULE_VIDEO, BridgeProtocol.ACTION_RECORD, MapUtils.createMap(
                "status", "failed", "reason", "recording_file_not_moved", "file", tempFile.getAbsolutePath(), "size", tempFile.length()));
        }
        else
        {
            BridgeLog.info(TAG, "Video recording saved as " + targetFile.getAbsolutePath());
            publishEvent(BridgeProtocol.MODULE_VIDEO, BridgeProtocol.ACTION_RECORD, MapUtils.createMap(
                "status", "completed", "file", targetFile.getAbsolutePath(), "size", targetFile.length()));
        }
        videoFileWaitTask = null;
        recordingState = Status.IDLE;
    }

    /**
     * Finalizes an unsuccessful video recording.
     *
     * The status is reset, and an error event is published.
     */
    private void videoFileFailure()
    {
        synchronized (this)
        {
            if ((unitStatus == UnitStatus.SHUTDOWN) || (recordingState != Status.FINALIZING)) return;
            videoFileWaitTask = null;
            recordingState = Status.IDLE;
        }

        BridgeLog.warning(TAG, "Timed out waiting for completed video recording");
        publishEvent(BridgeProtocol.MODULE_VIDEO, BridgeProtocol.ACTION_RECORD,
            MapUtils.createMap("status", "failed", "reason", "recording_file_timeout"));
    }

    /**
     * Sends an implicit broadcast, including stopped receiver packages.
     *
     * @param   action          string representing intent to send
     */
    private void broadcastIntent(String action)
    {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
    }

    /**
     * Returns the directory in which the Sanbot recorder stores video files.
     *
     * @return  directory in which the Sanbot recorder stores video files
     */
    @NonNull
    private File getRecordedVideoDirectory()
    {
        return new File(Environment.getExternalStorageDirectory(), VIDEO_SOURCE_DIRECTORY);
    }

    /**
     * Check if the specified file name is a video file.
     *
     * The file is a video file if it is a regular, non-empty file with a filename with the
     * specified extension.
     *
     * @param   file            file to check
     *
     * @return  @c true if file is a video file, @c false if file is not a video file
     */
    private boolean isVideoFile(File file)
    {
        return ((file != null) && (file.isFile()) && (file.length() > 0L) &&
            (VIDEO_FILE_EXTENSION.equalsIgnoreCase(FileUtils.getFileExtension(file.getName()))));
    }

    /**
     * Enumerator specifying video recording status.
     */
    private enum Status
    {
        IDLE,               ///< Recorder is idle.
        RECORDING,          ///< Video is being recorded.
        FINALIZING          ///< A video recording is being finalized.
    }
}
