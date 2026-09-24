/**
 * @file        AudioFrameBuffer.java
 * @brief       Implements AudioFrameBuffer class.
 */
package com.fbcti.sanbot.bridge.robot.media;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.robot.camera.interfaces.BridgeAudioManager;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.CommonUtils;
import com.fbcti.sanbot.bridge.util.MapUtils;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Buffers raw audio frames received from the Sanbot audio stream.
 *
 * This class puts received audio frames received from the SanbotCameraManaer instance onto a queue.
 * If recordAudio() is called from a separate thread to start recording audio, frames are taken from
 * the queue and copied to an output stream to produce raw @e PCM data. Once recording stops either
 * on demand or because the specified recording length is exceded, an in-memory @e WAV file is
 * created from the audio data on the stream.
 *
 * @version     1.0.001
 * @date        8 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class AudioFrameBuffer
{
    /** Source label used for log messages. */
    private static final String TAG = "AudioFrameBuffer";

    /** Sample rate. */
    private static final int WAV_SAMPLE_RATE_HZ = 8000;

    /** Channel count (1 for mono, 2 for stereo). */
    private static final int WAV_CHANNEL_COUNT = 1;

    /** Bits per sample. */
    private static final int WAV_BITS_PER_SAMPLE = 16;

    /** Size of @e WAV header. */
    private static final int WAV_HEADER_SIZE = 44;

    /** Maximum time to wait for audio frame to become available. */
    private static final long AUDIO_FRAME_MAX_WAIT_MS = 1000L;

    /** Maximum number of audio frames to queue. */
    private static final int MAX_AUDIO_FRAME_QUEUE_CAPACITY = 100;

    /** Buffer containing audio frames waiting to be consumed. */
    private final BlockingQueue<byte[]> audioFrameQueue = new ArrayBlockingQueue<>(MAX_AUDIO_FRAME_QUEUE_CAPACITY);

    /** Flag specifying whether audio frames should be buffered for an active capture operation. */
    private volatile boolean audioCaptureEnabled;

    /** Number of frames captured in current session. */
    private int captureFrameCount = 0;

    /** Total number of payload bytes captured in current session. */
    private long captureByteCount = 0L;

    /** Smallest size of payload  captured in current session. */
    private int captureMinFrameSize = 0;

    /** Largest size of payload  captured in current session. */
    private int captureMaxFrameSize = 0;

    /** Size of the latest captured payload frame. */
    private int captureLastFrameSize = 0;

    /** Timestamp when last active capture started. */
    private long captureStartedAtMs = 0L;

    /** Timestamp of the most recently captured frame. */
    private long captureLastFrameAtMs = 0L;

    /** Sum of observed inter-frame delays for the last active capture. */
    private long captureInterFrameDeltaTotalMs = 0L;

    /** Number of inter-frame delays contributing to the last active capture sum. */
    private int captureInterFrameDeltaCount = 0;

    /** Total number of received audio frames. */
    private final AtomicLong audioFrameCount = new AtomicLong();

    /** Total number of dropped audio frames. */
    private final AtomicLong audioDroppedFrameCount = new AtomicLong();

    /** Total number of received audio bytes. */
    private final AtomicLong audioByteCount = new AtomicLong();

    /** Lock used to keep capture diagnostics internally consistent. */
    private final Object diagnosticsLock = new Object();

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Copies a single audio frame into the buffer.
     *
     * If capture is enabled, the capture diagnostic data is updated and the frame is added to the
     * queue. if this fails, the frame is considered dropped.
     *
     * @param   buffer          byte array containing audio frame data
     */
    public void setData(byte[] buffer)
    {
        // Exit if buffer is empty.
        if ((buffer == null) || (buffer.length == 0)) return;

        audioFrameCount.incrementAndGet();
        audioByteCount.addAndGet(buffer.length);

        // Exit if audio capture is not enabled.
        if (audioCaptureEnabled == false) return;

        // Update capture diagnostic data.
        updateCaptureDiagnostics(buffer);

        // Add the frame to the queue. If this fails, remove the first frame from the queue and try
        // again. If it still fails, increment the number of dropped frames.
        byte[] frame = buffer.clone();
        if (audioFrameQueue.offer(frame) == false)
        {
            if (audioFrameQueue.poll() == null) audioDroppedFrameCount.incrementAndGet();
            if (audioFrameQueue.offer(frame) == false) audioDroppedFrameCount.incrementAndGet();
        }
    }

    /**
     * Enables or disables audio capture for an active audio client operation.
     *
     * The @c audioCaptureEnabled flag is set, if audio capture is enabled the capture diagnostic
     * data is reset, and the frame queue is cleared.
     *
     * @param   enabled         @c true to buffer incoming audio frames
     */
    public void setAudioCaptureEnabled(boolean enabled)
    {
        audioCaptureEnabled = enabled;
        if (enabled) resetCaptureDiagnostics();
        audioFrameQueue.clear();
    }

    /**
     * Records audio frames for the specified duration and returns an in-memory @e WAV file.
     *
     * Audio capture is enabled to start collecting frames in the queue. While the maximum duration
     * is not yet expired or recording is to be stopped on demand, takeAudioFrame() is called to
     * retrieve the earliest from the queue, and the frame is written to the output stream. If the
     * maximum duration is expired, audio capture is disabled and a @c WAV file is created from the
     * raw audio data in the output stream.
     *
     * @param   duration            recording duration in seconds
     * @param   audioManagerHost    callback host from which to retrieve audio capture status update
     *
     * @return  WAV audio data
     */
    @NonNull
    public byte[] recordAudio(int duration, AudioManagerHost audioManagerHost)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + duration*1000L;

        setAudioCaptureEnabled(true);
        try
        {
            BridgeLog.info(TAG, "Audio recording started");

            // Reserve bytes for WAV header.
            outputStream.write(new byte[WAV_HEADER_SIZE], 0, WAV_HEADER_SIZE);

            while (System.currentTimeMillis() < deadline)
            {
                // If recording is to be stopped on demand stop recording.
                if ((audioManagerHost != null) && audioManagerHost.getStatus() == BridgeAudioManager.AudioStatus.RECORD_STOPPING) break;

                // Get frame from queue and write to output stream.
                long remainingMs = deadline - System.currentTimeMillis();
                byte[] frame = takeAudioFrame(Math.min(AUDIO_FRAME_MAX_WAIT_MS, Math.max(1L, remainingMs)));
                if ((frame != null) && (frame.length > 0)) outputStream.write(frame, 0, frame.length);
            }
        }
        finally
        {
            setAudioCaptureEnabled(false);
        }

        BridgeLog.info(TAG, "Audio recording stopped - capture frames=" + captureFrameCount
            + ", bytes=" + captureByteCount
            + ", avgFrame=" + ((captureFrameCount > 0) ? (captureByteCount / captureFrameCount) : 0L)
            + ", avgDeltaMs=" + ((captureInterFrameDeltaCount > 0) ? (captureInterFrameDeltaTotalMs / captureInterFrameDeltaCount) : 0L));
        return createWavData(outputStream.toByteArray());
    }

    /**
     * Wraps raw @e PCM audio data in a @e WAV container.
     *
     * Th @c WAV header is added to the start of the byte array
     *
     * @param   bytes           byte array containing raw audio data and reserved header bytes
     *
     * @return  in-memory @e WAV file
     */
    @NonNull
    public byte[] createWavData(@NonNull byte[] bytes)
    {
        int blockAlign = WAV_CHANNEL_COUNT * (WAV_BITS_PER_SAMPLE / 8);
        int byteRate = WAV_SAMPLE_RATE_HZ * blockAlign;
        int byteCount = bytes.length;
        CommonUtils.copyStringToByteArray(bytes, 0, "RIFF");
        CommonUtils.copyIntToByteArray(bytes, 4, byteCount - 8);
        CommonUtils.copyStringToByteArray(bytes, 8, "WAVE");
        CommonUtils.copyStringToByteArray(bytes, 12, "fmt ");
        CommonUtils.copyIntToByteArray(bytes, 16, 16);
        CommonUtils.copyShortToByteArray(bytes, 20, 1);
        CommonUtils.copyShortToByteArray(bytes, 22, WAV_CHANNEL_COUNT);
        CommonUtils.copyIntToByteArray(bytes, 24, WAV_SAMPLE_RATE_HZ);
        CommonUtils.copyIntToByteArray(bytes, 28, byteRate);
        CommonUtils.copyShortToByteArray(bytes, 32, blockAlign);
        CommonUtils.copyShortToByteArray(bytes, 34, WAV_BITS_PER_SAMPLE);
        CommonUtils.copyStringToByteArray(bytes, 36, "data");
        CommonUtils.copyIntToByteArray(bytes, 40, byteCount - WAV_HEADER_SIZE);
        return bytes;
    }

    /**
     * Returns data map containing audio frame buffer status data.
     *
     * @return  Java @c Map instance containing audio buffer status data
     */
    @NonNull
    public Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = MapUtils.createMap();
        data.put("totalAudioFrameCount", audioFrameCount.get());
        data.put("totalAudioDroppedFrameCount", audioDroppedFrameCount.get());
        data.put("totalAudioByteCount", audioByteCount.get());
        data.put("totalAudioQueuedFrameCount", audioFrameQueue.size());

        synchronized (diagnosticsLock)
        {
            data.put("captureFrameCount", captureFrameCount);
            data.put("captureByteCount", captureByteCount);
            data.put("captureMinFrameSize", captureMinFrameSize);
            data.put("captureMaxFrameSize", captureMaxFrameSize);
            data.put("captureLastFrameSize", captureLastFrameSize);
            data.put("captureStartedAtMs", captureStartedAtMs);
            data.put("captureLastFrameAtMs", captureLastFrameAtMs);
            data.put("captureAverageFrameSize", (captureFrameCount > 0) ? (captureByteCount/captureFrameCount) : 0L);
            data.put("captureAverageInterFrameMs", (captureInterFrameDeltaCount > 0) ? (captureInterFrameDeltaTotalMs/captureInterFrameDeltaCount) : 0L);
        }
        return data;
    }

    /**
     * Performs shutdown tasks.
     *
     * This method just resets the @c audioCaptureEnabled flag so captured frames are no longer
     * copied into the queue.
     */
    public void clear()
    {
        setAudioCaptureEnabled(false);
    }


    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Waits for the next audio frame.
     *
     * This method polls the queue and returns the first frame on the queue if available.
     *
     * @param   timeoutMs       maximum wait time in milliseconds
     *
     * @return  first frame from queue, or @c null if frame is not available
     */
    @Nullable
    private byte[] takeAudioFrame(long timeoutMs)
    {
        try
        {
            return audioFrameQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Updates the diagnostic data for the current capture session.
     *
     * @param   bytes           bytes containing audio frame data
     */
    private void updateCaptureDiagnostics(@NonNull byte[] bytes)
    {
        long now = System.currentTimeMillis();
        synchronized (diagnosticsLock)
        {
            captureFrameCount++;
            captureByteCount += bytes.length;
            captureLastFrameSize = bytes.length;
            if ((captureMinFrameSize == 0) || (bytes.length < captureMinFrameSize)) captureMinFrameSize = bytes.length;
            if (bytes.length > captureMaxFrameSize) captureMaxFrameSize = bytes.length;
            if (captureLastFrameAtMs > 0L)
            {
                captureInterFrameDeltaTotalMs += Math.max(0L, now - captureLastFrameAtMs);
                captureInterFrameDeltaCount++;
            }
            captureLastFrameAtMs = now;
        }
    }

    /**
     * Resets the diagnostic data for the current capture session.
     */
    private void resetCaptureDiagnostics()
    {
        synchronized (diagnosticsLock)
        {
            captureFrameCount = 0;
            captureByteCount = 0L;
            captureMinFrameSize = 0;
            captureMaxFrameSize = 0;
            captureLastFrameSize = 0;
            captureStartedAtMs = System.currentTimeMillis();
            captureLastFrameAtMs = 0L;
            captureInterFrameDeltaTotalMs = 0L;
            captureInterFrameDeltaCount = 0;
        }
    }

    /**
     * Defines BridgeAudioManager callback contract.
     *
     * Ths interface must be implemented by class that implements the BridgeAudioManager interface
     * to allow the current audio device status to be retrieved.
     */
    public interface AudioManagerHost
    {
        /**
         * Returns current audio device status
         *
         * @return  BridgeAudioManager.AudioStatus enumerator value current audio device status
         */
        BridgeAudioManager.AudioStatus getStatus();
    }
}