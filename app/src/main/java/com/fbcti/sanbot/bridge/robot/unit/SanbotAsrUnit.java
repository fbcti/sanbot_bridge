/**
 * @file        SanbotAsrUnit.java
 * @brief       Implements SanbotAsrUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.sanbot.opensdk.function.beans.speech.Grammar;
import com.sanbot.opensdk.function.beans.speech.RecognizeTextBean;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.interfaces.speech.RecognizeListener;
import com.sanbot.opensdk.function.unit.interfaces.speech.WakenListener;

/**
 * Manages Sanbot speech recognition operations.
 *
 * This class extends the abstract BridgeUnit class to provide direct access to Sanbot speech
 * recognition resources.

 * @note
 * The Sanbot SDK uses the Google speech recognition service to translate the spoken text in written
 * text, and then performs semantic parsing of the text to obtain a topic and an action. A custom
 * grammar file may contan up to 20 entries that translate spoken phrases to a topic and action. For
 * now the parsed result is unknown, and the recognized text is returned as-is.
 *
 * @version     1.0.003
 * @date        5 Oct 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 * @since       1.0.001
 * @changelog
 * - CHANGE: multiple event names updated (1.1.003)
 */
public final class SanbotAsrUnit extends BridgeUnit
{
    /** Source label used for log messages. */
//    private static final String TAG = "SanbotAsrUnit";

    /** Persistent bridge configuration object. */
    private final BridgeConfig config;

    /** Active Sanbot @c SpeechManager API instance. */
    private SpeechManager speechManager = null;

    /** Listener for speech recognition wake/sleep events. */
    private final WakenListener wakenListener = new BridgeWakenListener();

    /** Listener for speech recognition events. */
    private final RecognizeListener recognizeListener = new BirgdeRecognizeListener();

    /** Current speech recognizion volume. */
    private volatile Integer speechVolume = null;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new SanbotAsrUnit instance.
     *
     * The base class constructor is called to copy the callback host to a member variable, the
     * persistent bridge configuration object is copied to a class member variable.
     *
     * @param   config          persistent bridge configuration
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public SanbotAsrUnit(BridgeConfig config, BridgeEventHost eventHost)
    {
        super(eventHost);
        if (config == null) throw new IllegalArgumentException("bridge configuration object may not be null");
        this.config = config;
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * If the active speech manager instance referenced by the @p initData function parameter is
     * available it is copied to a member variable, the speech recognition event listeners are
     * registered with the speech manager, and the unit status is set.
     *
     * @param   speechManager   active Sanbot SDK @c SpeechManager API instance
     */
    public synchronized void init(Object speechManager)
    {
        // If speech recognition is not enabled just set the status.
        if (config.getEnableAsr() == false)
        {
            unitStatus = UnitStatus.DISABLED;
            return;
        }

        logStatus();

        if (BuildConfig.EMULATOR_MODE)
        {
            unitStatus = UnitStatus.EMULATED;
            return;
        }

        // Copy speech manager and register event listeners.
        this.speechManager = (speechManager instanceof SpeechManager) ? (SpeechManager)speechManager : null;
        if (this.speechManager != null)
        {
            this.speechManager.setOnSpeechListener(wakenListener);
            this.speechManager.setOnSpeechListener(recognizeListener);
            unitStatus = UnitStatus.STARTED;
        }
        else unitStatus = UnitStatus.NOTINITIALIZED;

        logStatus();
    }

    /**
     * Performs unit shutdown tasks.
     */
    @Override
    public synchronized void shutdown()
    {
        speechManager = null;
        unitStatus = UnitStatus.SHUTDOWN;
        logStatus();
    }

    /**
     * Returns a data map specifying speech recognition features.
     *
     * @return  Java @c Map instance containing speech recognition features
     */
    @NonNull
    public synchronized DataResult getFeatures()
    {
        return DataResult.success(MapUtils.createMap("platform", "Sanbot", "enabled", config.getEnableAsr()));
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Receives speech engine sleep/wakeup callback invocations.
     */
    private final class BridgeWakenListener implements WakenListener
    {
        /**
         * Called if speech engine has woken up.
         */
        @Override
        public void onWakeUp()
        {
            BridgeLog.apievent("SanbotSDK", "WakenListener", "onWakeUp");
        }

        /**
         * Called if speech engine has gone to sleep.
         */
        @Override
        public void onSleep()
        {
            BridgeLog.apievent("SanbotSDK", "WakenListener", "onSleep");
        }

        /**
         * Called if speech engine wake/sleep status has changed.
         *
         * @param   status          @c true (@c false) if speech engine is in wake (sleep) state
         */
        @Override
        public void onWakeUpStatus(boolean status)
        {
            BridgeLog.apievent("SanbotSDK", "WakenListener", "onWakeUpStatus", status);
            publishEvent(BridgeProtocol.MODULE_SPEECH, "awake", MapUtils.createMap("awake", status));
        }
    }

    /**
     * Handles speech recognition events from Sanbot @c SpeechManager API.
     */
    private final class BirgdeRecognizeListener implements RecognizeListener
    {
        /**
         * Called if speech recognition starts.
         */
        @Override
        public void onStartRecognize()
        {
            BridgeLog.apievent("SanbotSDK", "RecognizeListener", "onStartRecognize");
            publishEvent(BridgeProtocol.MODULE_SPEECH, "recognize_start", null);
        }

        /**
         * Called if speech recognition stops.
         */
        @Override
        public void onStopRecognize()
        {
            BridgeLog.apievent("SanbotSDK", "RecognizeListener", "onStopRecognize");
            publishEvent(BridgeProtocol.MODULE_SPEECH, "recognize_stop", null);
        }

        /**
         * Called if speech recognition returned text data.
         *
         * @param   textBean        Sanbot SDK @c RecognizeTextBean instance containing text
         */
        @Override
        public void onRecognizeText(@NonNull RecognizeTextBean textBean)
        {
            BridgeLog.apievent("SanbotSDK", "RecognizeListener", "onRecognizeText", "<RecognizeTextBean>");
            publishEvent(BridgeProtocol.MODULE_SPEECH, "recognize_text", MapUtils.createMap("text", textBean.getText(), "engine", textBean.getEngine(), "isLast", textBean.isLast()));
        }

        /**
         * Called if speech recognition returned semantically parsed text data.
         *
         * This method just returns @c true to stop further handling the received text. This
         * prevents the Sanbot standard application to perform whatver action was returned by the
         * semantic parser.
         * 
         * @param   grammar         Sanbot SDK @c Grammar instance containing recognition result
         *
         * @return  @c true to stop handling received text, @c false to continue handling text
         */
        @Override
        public boolean onRecognizeResult(@NonNull Grammar grammar)
        {
//            BridgeLog.apievent("SanbotSDK", "RecognizeListener", "onStopRecognize", "<Grammar>");
//            broadcastEvent(BridgeProtocol.MODULE_SPEECH, "recognize_result", MapUtils.createMap("text", grammar.getText(), "engine", grammar.getEngine(), "action", grammar.getAction(), "initialData", grammar.getInitialData(), "topic", grammar.getTopic()));
            return true;
        }

        /**
         * Called if speech recognition volume has changed.
         *
         * The event is ignored if the volume is equal to the volume stored in the @c speechVolume
         * member variable.
         * 
         * @param   volume          new volume
         */
        @Override
        public void onRecognizeVolume(int volume)
        {
            if ((speechVolume == null) || (volume != speechVolume))
            {
                BridgeLog.apievent("SanbotSDK", "RecognizeListener", "onRecognizeVolume", volume);
                publishEvent(BridgeProtocol.MODULE_SPEECH, "recognize_volume", MapUtils.createMap("volume", volume));
                speechVolume = volume;
            }
        }

        /**
         * Called if a speech recognition error occurs.
         *
         * @param   code            error code
         * @param   subcode         error subcode
         */
        @Override
        public void onError(int code, int subcode)
        {
            BridgeLog.apievent("SanbotSDK", "RecognizeListener", "onError", code, subcode);
            publishEvent(BridgeProtocol.MODULE_SPEECH, "recognize_error", MapUtils.createMap("code", code, "subcode", subcode));
        }
    }
}
