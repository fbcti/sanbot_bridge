/**
 * @file        SanbotFaceUnit.java
 * @brief       Implements SanbotFaceUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.beans.EmotionsType;
import com.sanbot.opensdk.function.unit.SystemManager;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages Sanbot SDK face emotion operations.
 *
 * Face emotions are controlled by the Sanbot @c SystemManager API. By default emotions are not
 * persistent and revert back to the default face emotion after a certain time. This class allows
 * emotions to be held by setting a scheduled task that reloads the emotion before it falls back to
 * the default.
 *
 * @version     1.0.001
 * @date        4 Aug 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class SanbotFaceUnit extends BridgeUnit
{
    /** Source label used for log messages. */
    private static final String TAG = SanbotFaceUnit.class.getSimpleName();

    /** Active Sanbot @c SystemManager API instance. */
    private SystemManager systemManager;

    /** Executor used to refresh held emotions. */
    private ScheduledExecutorService emotionExecutor;

    /** Scheduled task used to refresh the held emotion. */
    private ScheduledFuture<?> heldEmotionTask;

    /** Scheduled task used to stop the held emotion. */
    private ScheduledFuture<?> heldEmotionStopTask;

    /** Type of the currently held emotion, or null if no emotion is held. */
    private volatile EmotionsType heldEmotionType;

    /** Duration for the currently held emotion. */
    private int heldEmotionDurationSeconds;

    /** Refresh interval for the currently held emotion. */
    private int heldEmotionRefreshSeconds;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new SanbotFaceUnit instance.
     *
     * The base class constructor is called to copy the callback host to a member variable.
     *
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public SanbotFaceUnit(BridgeEventHost eventHost)
    {
        // Call base class constructor.
        super(eventHost);
    }

    /**
     * @name Emotion Unit Operations
     *
     * @{ 
     */ 

    /**
     * Sets a non-persistent emotion.
     *
     * If required, the schedule to hold the current emotion is cancelled before setting the
     * requested emotion.
     *
     * @param   emotionType     emotion to be set
     *
     * Possible values of @p type are defined by the Sanbot SDK @c EmotionsType enumerator. If
     * @c null the default emotion is set by cancelling the current emotion,
     *
     * @return  DataResult instance specifying operation result
     */
    public synchronized DataResult setEmotion(EmotionsType emotionType)
    {
        if (emotionType == null)
        {
            cancelEmotion(true);
            return DataResult.success();
        }

        cancelEmotion(false);
        return showEmotion(emotionType);
    }

    /**
     * Sets a persistent emotion.
     *
     * After setting the emotion the holdEmotion() function is called to ensure the emotion does not
     * fall back to the default.
     *
     * @param   emotionType     emotion to be set
     * @param   duration        time to hold emotion in seconds, or 0 to hold emotion indefinitely
     * @param   refresh         refresh time in seconds
     *
     * Possible values of @p type are defined by the Sanbot SDK @c EmotionsType enumerator.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult setEmotion(EmotionsType emotionType, int duration, int refresh)
    {
        DataResult result = setEmotion(emotionType);
        if (result.isFailure()) return result;

        // Schedule emotion refresh.
        return holdEmotion(emotionType, duration, refresh);
    }

    /** @} */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * The Sanbot SDK @c SystemManager object is copied to a class member variable, and the unit
     * status is set.
     *
     * @param   systemManager   active Sanbot @c SystemManager instance
     */
    public synchronized void init(SystemManager systemManager)
    {
        logStatus();

        this.systemManager = systemManager;

        if (BuildConfig.EMULATOR_MODE) unitStatus = UnitStatus.EMULATED;
        else unitStatus = (this.systemManager != null) ? UnitStatus.STARTED : UnitStatus.INITIALIZING;

        logStatus();
    }

    /**
     * Performs unit shutdown tasks.
     *
     * If required, the scheduled task to hold the current emotion is stopped if required. The task
     * executor is stopped.
     */
    public synchronized void shutdown()
    {
        cancelEmotion(false);
        systemManager = null;
        if (emotionExecutor != null)
        {
            emotionExecutor.shutdownNow();
            emotionExecutor = null;
        }
        unitStatus = UnitStatus.SHUTDOWN;
        BridgeLog.info(TAG, "Unit status is " + unitStatus.toString());
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Shows the specified non-persistent emotion.
     *
     * If the unit is initialized the @c showEmotion() function implemented by the Sanbot SDK
     * @c WheelManager class is called to show the specified emotion.
     *
     * @param   emotionType     emotion to set
     *
     * @return  instance of DataResult class
     */
    private synchronized DataResult showEmotion(EmotionsType emotionType)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated(emotionType.name());
        if (systemManager == null) return DataResult.notavailable(FuncConstant.SYSTEM_MANAGER);

        BridgeLog.apicall("SanbotSDK", "SystemManager", "showEmotion", emotionType.toString());
        return DataResult.fromOperationResult(systemManager.showEmotion(emotionType));
    }

    /**
     * Starts a scheduled task to refresh the current emotion.
     *
     * A scheduled task is started that refreshes the current emotion to prevent fall back to the
     * default emotion. If the specified duration is larger than zero, a second scheduled task is
     * set to cancel the refresh task after that duration. If the duration is zero, the cancel task
     * is not scheduled,  so the current emotion is held indefinitely.
     *
     * @param   emotionType     emotion to be set
     * @param   duration        time to hold emotion in seconds, or 0 to hold emotion indefinitely
     * @param   refresh         refresh time in seconds
     *
     * @return  instance of DataResult class
     */
    @NonNull
    private synchronized DataResult holdEmotion(EmotionsType emotionType, int duration, int refresh)
    {
        cancelRefreshTimers();

        heldEmotionType = emotionType;
        heldEmotionRefreshSeconds = refresh;
        heldEmotionDurationSeconds = duration;

        // Set scheduled task to refresh emotion.
        if (emotionExecutor == null) emotionExecutor = Executors.newSingleThreadScheduledExecutor();
        heldEmotionTask = emotionExecutor.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                EmotionsType emotion = heldEmotionType;
                if (emotion == null) return;

                DataResult refreshResult = showEmotion(emotion);
                if (refreshResult.isFailure())
                    BridgeLog.warning(TAG, "Held emotion refresh failed: " + refreshResult.getDescription());
            }
        }, refresh, refresh, TimeUnit.SECONDS);

        // If emotion must not be held indefinitely set task to stop scheduled task.
        if (duration > 0)
        {
            heldEmotionStopTask = emotionExecutor.schedule(new Runnable()
            {
                @Override
                public void run()
                {
                    cancelEmotion(true);
                }
            }, duration, TimeUnit.SECONDS);
        }

        String time = (duration > 0) ? " for" + duration + " seconds" : "until canceled";
        String message = "holding emotion " + emotionType.name().toLowerCase(Locale.US) + time;
        return DataResult.success(message);
    }

    /**
     * Cancels the scheduled task that refreshes the current emotion.
     *
     * @param restoreDefault if @c true, restore to default emotion
     */
    private synchronized void cancelEmotion(boolean restoreDefault)
    {
        boolean hadTask = cancelRefreshTimers();
        heldEmotionType = null;
        heldEmotionRefreshSeconds = 0;
        heldEmotionDurationSeconds = 0;

        // If there was no held emotion there is nothing more to do.
        if (hadTask == false)
        {
            DataResult.success("no held emotion was active");
            return;
        }

        // Set default emotion if required.
        if (restoreDefault)
        {
            DataResult restoreResult = showEmotion(EmotionsType.NORMAL);
            if (restoreResult.isFailure())
                BridgeLog.warning(TAG, "Failed to restore normal emotion: " + restoreResult.getDescription());
        }
        DataResult.success("stopped held emotion refresh");
    }

    /**
     * Cancels active refresh timers.
     *
     * @return  @c true if a timer was canceled
     */
    private synchronized boolean cancelRefreshTimers()
    {
        boolean hadTask = false;
        if (heldEmotionTask != null)
        {
            heldEmotionTask.cancel(false);
            heldEmotionTask = null;
            hadTask = true;
        }
        if (heldEmotionStopTask != null)
        {
            heldEmotionStopTask.cancel(false);
            heldEmotionStopTask = null;
            hadTask = true;
        }
        return hadTask;
    }

    /**
     * Builds a data map containing current unit status data.
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    public synchronized Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = super.buildStatusData();
        if (heldEmotionType != null)
        {
            data.put("heldEmotion", true);
            data.put("heldEmotionType", heldEmotionType.name());
            data.put("heldEmotionDurationSeconds", heldEmotionDurationSeconds);
            data.put("heldEmotionRefreshSeconds", heldEmotionRefreshSeconds);
        }
        else data.put("heldEmotion", false);
        return data;
    }
}
