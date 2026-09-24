/**
 * @file        BridgeSpeechHost.java
 * @brief       Implements BridgeSpeechHost class.
 */
package com.fbcti.sanbot.bridge.app;

import android.os.Looper;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.WakeUpOption;
import com.sanbot.opensdk.function.unit.SpeechManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides activity-bound Sanbot speech functionality.
 *
 * The Sanbot @c SpeechManager API @c %doWakeup() and @c %doSleep() methods can only be called from
 * classes that extend the Sanbot SDK BindBaseActivity class. This helper class wraps calls to those
 * methods so they are executed from the BridgeMainActivity instance UI thread.
 *
 * @version     1.0.001
 * @date        5 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
final class BridgeSpeechHost
{
    /** Source label for system log messages. */
    private static final String TAG = "BridgeSpeechHost";

    /** Maximum time to wait for speech calls to finish. */
    private static final long SPEECH_CALL_TIMEOUT_MS = 5000L;

    /** Activity that owns this speech host. */
    private final BridgeMainActivity activity;

    /** Sanbot SDK @c SpeechManager class. */
    private volatile SpeechManager speechManager;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new BridgeSpeechHost instance.
     *
     * The reference to the BridgeMainActivity instance is copied to a member variable if not
     * @c null. If @c null, an exception is thrown.
     *
     * @param   activity        activity that owns this speech host
     *
     * @throws  IllegalArgumentException    thrown when @p activity is @c null
     */
    BridgeSpeechHost(BridgeMainActivity activity)
    {
        if (activity == null) throw new IllegalArgumentException("activity may not be null");
        this.activity = activity;
    }

    /***********************************************************************************************
     * PACKAGE-PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Copies Sanbot @c SpeechManager API to a member variable.
     *
     * @param   speechManager   Sanbot @c SpeechManager API, or @c null if unavailable
     */
    void setSpeechManager(SpeechManager speechManager)
    {
        this.speechManager = speechManager;
    }

    /**
     * Destroys the Sanbot SDK @c SpeechManager instance.
     */
    void destroy()
    {
        speechManager = null;
    }

    /**
     * Checks if speech capabilities are available.
     *
     * If the application is running in emulator mode the speech capabilities are considered to be
     * always available. If not, speech capabilities are available if the Sanbot @c SpeechManager
     * API is available.
     *
     * @return  @c true if speech engine is available to execute speech calls
     */
    boolean isReady()
    {
        return ((BuildConfig.EMULATOR_MODE == true) || (speechManager != null));
    }

    /**
     * Requests the Sanbot speech engine to wake up.
     *
     * The wakeup request is executed by calling the runSpeechCall() method to make sure it runs on
     * the main activity UI thread.
     *
     * @param   languageType    optional Sanbot language code for wake-up command
     *
     * @return  DataResult instance containing speech call result
     */
    DataResult doWakeup(String languageType)
    {
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated("emulator");
        if (speechManager == null) return DataResult.notavailable(FuncConstant.SPEECH_MANAGER);

        return runSpeechCall(new SpeechCall()
        {
            @Override
            public OperationResult run()
            {
                if (languageType == null) return speechManager.doWakeUp();

                WakeUpOption wakeUpOption = new WakeUpOption();
                wakeUpOption.setLanguageType(languageType);
                return speechManager.doWakeUp(wakeUpOption);
            }
        });
    }

    /**
     * Requests the Sanbot speech engine to sleep.
     *
     * The sleep request is executed by calling the runSpeechCall() method to make sure it runs on
     * the main activity UI thread.
     *
     * @return  DataResult instance containing speech call result
     */
    DataResult doSleep()
    {
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated("emulator");
        if (speechManager == null) return DataResult.notavailable(FuncConstant.SPEECH_MANAGER);

        return runSpeechCall(new SpeechCall()
        {
            @Override
            public OperationResult run()
            {
                return speechManager.doSleep();
            }
        });
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Runs a Sanbot SDK speech call on the main activity user interface thread.
     *
     * If the current thread is the main activity user interface thread, the runSpeechCallSafely()
     * method is called. If not, the BridgeMainActivity instance is requested to execute
     * runSpeechCallSafely() instead.
     *
     * @param   speechCall      speech call to run
     *
     * @return  DataResult instance containing speech call result
     */
    private DataResult runSpeechCall(SpeechCall speechCall)
    {
        // If already on main activity thread call function to execute speech operation.
        if (Looper.myLooper() == Looper.getMainLooper()) return runSpeechCallSafely(speechCall);

        // Request main activity thread to execute speech operation.
        CountDownLatch callCompleted = new CountDownLatch(1);
        AtomicReference<DataResult> resultRef = new AtomicReference<>();
        activity.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                try { resultRef.set(runSpeechCallSafely(speechCall)); }
                finally { callCompleted.countDown(); }
            }
        });

        try
        {
            if (callCompleted.await(SPEECH_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS) == false)
                return DataResult.failure("speech call timed out");
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return DataResult.failure("speech call interrupted");
        }

        DataResult result = resultRef.get();
        return (result != null) ? result : DataResult.failure("no result from speech call");
    }

    /**
     * Executes the specified speech call.
     *
     * @param   speechCall      speech call to execute
     *
     * @return operation result
     */
    private DataResult runSpeechCallSafely(SpeechCall speechCall)
    {
        try
        {
            return DataResult.fromOperationResult(speechCall.run());
        }
        catch (RuntimeException e)
        {
            BridgeLog.warning(TAG, "Speech operation failed", e);
            return DataResult.failure(e.getClass().getSimpleName(), (e.getMessage() == null) ? e.getMessage() : "");
        }
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Defines BridgeSpeechHost request callback contract.
     *
     * This interface must be implemented by the BridgeSpeechHost class to allow executing speech
     * calls in a separate thread.
     */
    private interface SpeechCall
    {
        /**
         * Requests execution of a speech call.
         *
         * @return  Sanbot SDK @c OperationResult instance containing speech call result
         */
        OperationResult run();
    }
}