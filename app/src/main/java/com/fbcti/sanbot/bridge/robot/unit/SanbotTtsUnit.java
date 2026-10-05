/**
 * @file        SanbotTtsUnit.java
 * @brief       Implements SanbotTtsUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.mapping.SanbotMappings;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.beans.SpeakOption;
import com.sanbot.opensdk.function.beans.speech.SpeakStatus;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.interfaces.speech.SpeakListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages Sanbot SDK text-to-speech operations.
 *
 * This class extends the abstract BridgeTtsUnit class to provide access text-to-speech features
 * using the Sanbot @c SpeechManager API.
 *
 * @version     1.0.003
 * @date        5 Oct 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 * @since       1.0.001
 * @changelog
 * - CHANGE: minor comment updates (1.0.003)
 */
public final class SanbotTtsUnit extends BridgeTtsUnit
{
    /** Human-readable name of this text-to-speech unit. */
    public static final String TTS_NAME = SANBOT_TTS_NAME;

    /** Source label used for log messages. */
//    private static final String TAG = SanbotTtsUnit.class.getCanonicalName();

    /** Active Sanbot @c SpeechManager API instance. */
    private SpeechManager speechManager = null;

    /** Text-to-speech event listener. */
    private final SpeakListener speakListener = new BridgeSpeakListener();

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new SanbotTtsUnit instance.
     *
     * The base class constructor is called to copy the persistent configuration and callback host
     * to member variables.
     *
     * @param   config          persistent bridge configuration
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public SanbotTtsUnit(BridgeConfig config, BridgeEventHost eventHost)
    {
        super(config, eventHost);
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * If the active speech manager instance referenced by the @p speechManager function parameter
     * is available it is copied to a member variable, the text-to-speech event listener is
     * registered with the speech manager, and the unit status is set.
     *
     * @param   speechManager   active Sanbot SDK @c SpeechManager API instance
     */
    @Override
    public synchronized void init(Object speechManager)
    {
        logStatus();

        // Reset default parameters if current parameters are invalid. Since this can only be the
        // case if the configuration file was corrupted, the save flag is included so the default
        // configuration is written to that file.
        if (validateParams(config.getTtsParams(config.getTtsName())).isSuccess() == false)
            updateConfig(true, false, true, null);

        // Copy speech manager and register event listener.
        this.speechManager = (speechManager instanceof SpeechManager) ? (SpeechManager)speechManager : null;
        if (this.speechManager != null) this.speechManager.setOnSpeechListener(speakListener);

        // Set status.
        if (this.speechManager != null)
        {
            ttsStatus = "initialized";
            unitStatus = UnitStatus.STARTED;
        }
        else
        {
            ttsStatus = "not initialized";
            unitStatus = UnitStatus.NOTINITIALIZED;
        }

        logStatus();
    }

    /**
     * Performs unit shutdown tasks.
     */
    @Override
    public synchronized void shutdown()
    {
        speechManager = null;
        ttsStatus = "not initialized";
        unitStatus = UnitStatus.SHUTDOWN;
        logStatus();
    }

    /***********************************************************************************************
     * PROTECTED METHODS
     **********************************************************************************************/

    /**
     * Initializes a data map containing supported languages.
     *
     * Each item in the data map consists of a language code and a human-readable language name.
     * Since the Sanbot SDK does not support different country codes for the same language the
     * language code consists of language code only.
     *
     * @return  Java @c Map instance containing supported languages
     */
    @NonNull
    protected Map<String, String> initLanguageMap()
    {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("en", "english");
        map.put("de", "german");
        map.put("da", "danish");
        map.put("fr", "french");
        map.put("it", "italian");
        map.put("es", "spanish");
        map.put("pt", "portugese");
        map.put("pl", "polish");
        map.put("tr", "turkish");
        map.put("zh", "chinese");
        map.put("jp", "japanese");
        map.put("ko", "korean");
        map.put("ar", "arabic");
        return map;
    }

    /**
     * Checks if the text-to-speech engine is initialized and ready for use.
     *
     * @return  @c true if text-to-speech engine is initialized, @c false if not
     */
    protected boolean isTtsReady()
    {
        return ((speechManager != null) || (BuildConfig.EMULATOR_MODE));
    }

    /**
     * Start speaking the specified text using current text-to-speech settings.
     *
     * The @c startSpeak() function implemented by the Sanbot SDK @c SpeechManager class is called
     * to request the text-to-speech engine to start speaking.
     *
     * @param   text            text to speak
     * @param   params          Java @c map instance containing optional text-to-speech parameters
     *
     * Supported text-to-speech parameters are:
     * - @c language: Text-to-speech language as either a two-letter language code, a locale, or an
     *      actual language name.
     * - @c pitch: Speed pitch (or intonation) normalized in [0..200] range.
     * - @c speed: Speech speed normalized in [0..200] range.
     *
     * @return  DataResult instance specifying operation result
     *
     * If the speak request is submitted the @c result property in the operation result object is a
     * string specifying a unique utterance id for this request.
     */
    @Override
    protected DataResult speechStart(String text, Map<String, Object> params)
    {
        // Exit if emulator mode is active.
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated("00000000-0000-0000-0000-000000000000");

        // Exit if speech manager object is not available.
        if (speechManager == null) return DataResult.notavailable(FuncConstant.SPEECH_MANAGER);

        // Call Sanbot SpeechManager API function.
        SpeakOption speakOption = toSpeakOption(config.getTtsParams(config.getTtsName()));
        BridgeLog.apicall("SanbotSDK", "SpeechManager", "startSpeak", speakOption.getLanguageType(), speakOption.getSpeed(), speakOption.getIntonation());
        return DataResult.fromOperationResult(speechManager.startSpeak(text, speakOption));
    }

    /**
     * Stops speaking.
     *
     * The @c stopSpeak() function implemented by the Sanbot @c SpeechManager SDK class is called
     * to request the speech synthesis engine to stop speaking.
     *
     * @return  DataResult instance specifying operation result
     */
    @Override
    protected DataResult speechStop()
    {
        // Exit if emulator mode is active.
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated();

        // Exit if speech manager object is not available.
        if (speechManager == null) return DataResult.notavailable(FuncConstant.SPEECH_MANAGER);

        // Call Sanbot SpeechManager API function.
        BridgeLog.apicall("SanbotSDK", "SpeechManager", "stopSpeak");
        return DataResult.fromOperationResult(speechManager.stopSpeak());
    }

    /**
     * Returns the current speech status.
     *
     * The @c isSpeaking() function implemented by the Sanbot @c SpeechManager SDK class is called
     * to check if the text-to-speech engine is currently speaking.
     *
     * @return  DataResult instance specifying operation result
     *
     * The @c result property in the DataResult object represents an integer value equal to 1 if
     * the engine is currently speaking, or 0 if the engine is not speaking.
     */
    @Override
    public synchronized DataResult speechStatus()
    {
        // Exit if emulator mode is active.
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated("0");

        // Exit if speech manager object is not available.
        if (speechManager == null) return DataResult.notavailable(FuncConstant.SPEECH_MANAGER);

        // Call Sanbot SpeechManager API function.
        BridgeLog.apicall("SanbotSDK", "SpeechManager", "isSpeaking");
        return DataResult.fromOperationResult(speechManager.isSpeaking());
    }

    /**
     * Returns a data map specifying speech features provided by this class.
     *
     * @return  Java @c Map instance containing text-to-speech feature data
     */
    @NonNull
    @Override
    protected Map<String, Object> buildFeatureData()
    {
        Map<String, Object> data = MapUtils.createMap("platform", "Sanbot");
        data.put("languages", ttsLanguageMap.keySet());
        return data;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Convert protocol speech synthesis speed to Sanbot speed.
     *
     * @param   speed           protocol speech synthesis speed to convert
     *
     * Sanbot speech synthesis rate is an integer number with value 50 representing normal
     * speed, and value 100 representing maximal speed.
     *
     * @return  Sanbot speech synthesis speed
     */
    private int toSanbotTtsSpeed(int speed)
    {
        int i = speed / 2;
        if (i > 100) i = 100;
        return i;
    }

    /**
     * Convert protocol speech synthesis pitch to Sanbot intonation.
     *
     * @param   pitch           protocol speech synthesis pitch to convert
     *
     * Sanbot speech synthesis intonation is an integer number with value 50 representing normal
     * intonation, and value 100 representing maximal intonation.
     *
     * @return  Sanbot speech synthesis intonation
     */
    private int toSanbotTtsIntonation(int pitch)
    {
        int i = pitch / 2;
        if (i > 100) i = 100;
        return i;
    }

    /**
     * Converts text-to-speech parameters to Sanbot SDK @c SpeakOption instance.
     *
     * @param   params          Java @c class instance containing text-to-speech parameters
     *
     * @return  @c Sanbot SDK SpeakOption instance
     */
    @NonNull
    private SpeakOption toSpeakOption(Map<String, Object> params)
    {
        String language = MapUtils.getString(params, "language", ttsLanguageMap.keySet().iterator().next());
        int speed = MapUtils.getInt(params, "speed", 100);
        int pitch = MapUtils.getInt(params, "pitch", 100);

        SpeakOption speakOption = new SpeakOption();
        Integer languageType = SanbotMappings.mapSanbotLanguageId(language);
        if (languageType != null) speakOption.setLanguageType(languageType);
        speakOption.setSpeed(toSanbotTtsSpeed(speed));
        speakOption.setIntonation(toSanbotTtsIntonation(pitch));
        return speakOption;
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Receives speech synthesis callback invocations.
     */
    private final class BridgeSpeakListener implements SpeakListener
    {
        /**
         * Called if speech synthesis status has changed.
         *
         * The event is relayed to the bridge service.
         *
         * @param   speakStatus     speech synthesis status
         */
        @Override
        public void onSpeakStatus(@NonNull SpeakStatus speakStatus)
        {
            BridgeLog.apievent("SanbotSDK", "SpeakListener", "onSpeakStatus", speakStatus.getEngine(), speakStatus.getProgress());
            publishEvent(BridgeProtocol.MODULE_SPEECH, BridgeProtocol.EVENT_SPEAK, MapUtils.createMap("id", speakStatus.getId(), "engine", speakStatus.getEngine(), "progress", speakStatus.getProgress()));
        }
    }
}
