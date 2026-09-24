package com.fbcti.sanbot.bridge.robot.unit;

/**
 * @file        BridgeTtsUnit.java
 * @brief       Declares abstract BridgeTtsUnit class.
 */

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.util.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for classes that manage text-to-speech operations.
 *
 * This class implements methods that are shared by derived speech unit classes and declares
 * abstract methods that must be derived by those classes.
 *
 * @version     1.0.001
 * @date        11 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public abstract class BridgeTtsUnit extends BridgeUnit
{
    /** Human-readable name of Android  text-to-speech unit. */
    public static final String ANDROID_TTS_NAME = "android";

    /** Human-readable name of Sanbot  text-to-speech unit. */
    public static final String SANBOT_TTS_NAME = "sanbot";

    /** Supported text-to-speech platform names. */
    public static final String[] TTS_NAMES = { ANDROID_TTS_NAME, SANBOT_TTS_NAME };

    /** Name of default text-to-speech platform. */
    public static final String DEFAULT_TTS_NAME = ANDROID_TTS_NAME;

    /** Default speech language. */
    public static final String DEFAULT_LANGUAGE = "en-US";

    /** Default speed for synthesized speech. */
    public static final int DEFAULT_TTS_SPEED = 100;

    /** Default pitch (or intonation) for synthesized speech. */
    public static final int DEFAULT_TTS_PITCH = 100;

    /** Maps BCP 47 locale tags to human-readable language names. */
    protected final Map<String, String> ttsLanguageMap = initLanguageMap();

    /** Persistent bridge configuration object. */
    protected final BridgeConfig config;

    /** Human-readable text-to-speech status. */
    protected volatile String ttsStatus = "not initialized";

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs the base portion of a text-to-speech unit.
     *
     * The base class constructor is called to copy the callback host to a member variable, and the
     * persistent bridge configuration object are copied to s member variable.
     *
     * @param   config          persistent bridge configuration
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     *
     * @throws  IllegalArgumentException    thrown if @p config parameter is @c null
     */
    public BridgeTtsUnit(BridgeConfig config, BridgeEventHost eventHost)
    {
        super(eventHost);
        if (config == null) throw new IllegalArgumentException("bridge configuration object may not be null");

        this.config = config;
    }

    /**
     * @name Common Text-to-Speech Unit Operations
     *
     * @{
     */

    /**
     * Updates text-to-speech parameters.
     *
     * @param   reset           if @c true, reset parameters to defaults
     * @param   reload          if @c true, reload parameters from configuration file
     * @param   save            if @c true, save updated parameters to configuration file
     * @param   params          Java @c map instance containing text-to-speech parameters
     *
     * If @p reset or @p reload is @c true, the parameters for the current text-to-speech platform
     * are reset to their default values or reload from the configuration file, ignoring all other
     * parameters in the @p params map. In all other cases the parameters in the @p params map are
     * applied.
     *
     * @return  DataResult instance specifying operation result
     */
    public synchronized DataResult updateConfig(boolean reset, boolean reload, boolean save, Map<String, Object> params)
    {
        if (reset) config.resetTtsParams(config.getTtsName());
        else if (reload) config.reloadTtsParams(config.getTtsName());
        else  if ((params != null) && (params.isEmpty() == false))
        {
            // Validate parameters.
            DataResult result = validateParams(params);
            if (result.isFailure()) return result;
            config.setTtsParams(config.getTtsName(), params);
        }

        // Apply the updated parameters if the text-to-speech engine is available.
        if (isTtsReady())
        {
            DataResult result = applyTtsParams(params);
            if (result.isFailure()) return result;
        }

        // Save parameters if required.
        if ((save) && (config.save() == false)) return DataResult.failure("failed to save config", config.getTtsParams());

        return DataResult.success();
    }

    /**
     * Starts speaking specified text using specified or current text-to-speech settings.
     *
     * If the data map referenced by the @p params parameter is not empty, updateConfig() is called
     * to update the persistent configuration object with the specified text-to-speech parameters.
     * In all other cases, the current text-to-speech parameters remain active. The startSpeech()
     * method is called to start speaking.
     *
     * @param   text            text to speak
     * @param   params          Java @c map instance containing optional text-to-speech parameters
     *
     * @return  DataResult instance specifying operation result
     *
     * If the speak request is submitted the @c result property in the operation result object is a
     * string specifying a unique utterance id for this request.
     */
    public synchronized DataResult startSpeaking(String text, Map<String, Object> params)
    {
        // Update configuration if parameters are supplied. Exit if parameters are invalid.
        if ((params != null) && (params.isEmpty() == false))
        {
            DataResult result = updateConfig(false, false, false, params);
            if (result.isFailure()) return result;
        }

        return speechStart(text, params);
    }

    /**
     * The stopSpeech() method is called to stop speaking.
     *
     * @return  DataResult instance specifying operation result
     */
    public synchronized DataResult stopSpeaking()
    {
        return speechStop();
    }

    /**
     * Returns current speech status.
     *
     * The speechStatus() method is called to check if the text-to-speech engine is currently
     * speaking.
     *
     * @return  DataResult instance specifying operation result
     */
    public synchronized DataResult isSpeaking()
    {
        return speechStatus();
    }

    /**
     * Returns a data map containing supported text-to-speech features.
     *
     * @return  DataResult instance containing operation result
     */
    public synchronized DataResult getFeatures()
    {
        return DataResult.success(buildFeatureData());
    }

    /*
     * @}
     */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * This abstract method must be implemented by subclasses to perform platform-specific
     * initialization tasks.
     *
     * @param   initData        initialization data
     */
    public abstract void init(Object initData);

    /**
     * Returns a data map containing status data for the active text-to-speech platform.
     *
     * @return  Java @c Map instance containing status data
     */
    public synchronized Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = super.buildStatusData();
        data.put("platform", config.getTtsName());
        data.put("isReady", isTtsReady());
        data.put("isSpeaking", "1".equals(isSpeaking().getData()));
        data.put("settings", config.getTtsParams(config.getTtsName()));
        return data;
    }

    /***********************************************************************************************
     * PROTECTED METHODS
     **********************************************************************************************/

    /**
     * Initializes a data map containing supported languages.
     *
     * This abstract method must be implemented by subclasses to initialize the platform-specific
     * list of languages.
     *
     * @return  Java @c Map instance containing supported languages
     */
    protected abstract Map<String, String> initLanguageMap();

    /**
     * Sets specified parameters for the current text-to-speech engine.
     *
     * This abstract method can be overridden by subclasses if platform-specific text-to-speech
     * configuration tasks are required.
     *
     * @param   params          text-to-speech parameters to apply
     *
     * @return  DataResult instance specifying operation result
     */
    protected DataResult applyTtsParams(Map<String, Object> params) { return DataResult.success(); }

    /**
     * Checks if the text-to-speech engine is initialized and ready for use.
     *
     * This abstract method must be implemented by subclasses.
     *
     * @return  @c true if text-to-speech engine is initialized, @c false if not
     */
    protected abstract boolean isTtsReady();

    /**
     * Start speaking the specified text using current text-to-speech settings.
     *
     * This abstract method must be implemented by subclasses.
     *
     * @param   text            text to speak
     * @param   params          Java @c map instance containing optional text-to-speech parameters
     *
     * @return      DataResult instance specifying operation result
     */
    protected abstract DataResult speechStart(String text, Map<String, Object> params);

    /**
     * Stops speaking.
     *
     * This abstract method must be implemented by subclasses.
     *
     * @return  DataResult instance specifying operation result
     */
    protected abstract DataResult speechStop();

    /**
     * Returns the current speech status.
     *
     * This abstract method must be implemented by subclasses to return speech status,
     *
     * @return  DataResult instance specifying operation result
     */
    protected abstract DataResult speechStatus();

    /**
     * Build a data map containing speech features provided by this class.
     *
     * This abstract method must be implemented by subclasses to return the tetx-to-speech features
     * provided by the text-to-speech platform.
     *
     * @return  Java @c Map instance containing feature data
     */
    protected abstract Map<String, Object> buildFeatureData();

    /**
     * Returns locale tags that match specified language.
     *
     * A language matches if it equals a supported locale tag, equals the language code part before
     * the first dash in that locale tag, or equals the human-readable language name.
     *
     * @param   language        language for which to return matching locale tags
     *
     * @return  list of matching locale tags
     */
    protected List<String> getMatchingLocaleTags(String language)
    {
        List<String> list = new LinkedList<>();
        String normalized = StringUtils.normalize(language);
        if (normalized == null) return list;

        for (String key : ttsLanguageMap.keySet())
        {
            String normalizedKey = StringUtils.normalize(key);
            if (normalizedKey == null) continue;

            int separator = normalizedKey.indexOf('-');
            String shortCode = (separator >= 0) ? normalizedKey.substring(0, separator) : normalizedKey;
            String value = StringUtils.normalize(ttsLanguageMap.get(key));
            if ((normalized.equals(normalizedKey)) || (normalized.equals(shortCode)) || (normalized.equals(value)))
                list.add(key);
        }
        return list;
    }

    /**
     * Validates common text-to-speech parameters.
     *
     * The following parameters are validated
     *
     * - @c language - Must match a supported locale tag, language code, or language name.
     * - @c speed - Must be a number in range <em>[0..200]</em>.
     * - @c pitch - Must be a number in range <em>[0..200]</em>.
     *
     * All parameters are optional.
     *
     * @param   params          Java @c map instance containing parameters to validate
     *
     * @return  DataResult instance specifying operation result
     */
    protected DataResult validateParams(@NonNull Map<String, Object> params)
    {
        Object o = params.get("language");
        if (o instanceof String)
        {
            String language = StringUtils.normalize((String)o);
            if ((StringUtils.isBlank(language) == false) && getMatchingLocaleTags(language).isEmpty())
                return DataResult.failure(ERROR_INVALID_PARAMS, "unsupported language " + language);
        }

        Object speed = params.get("speed");
        if ((speed instanceof Integer) && (((Integer)speed < 0) || ((Integer)speed > 200)))
            return DataResult.failure(ERROR_INVALID_PARAMS, String.format("speed value %d invalid", (Integer)speed));

        Object pitch = params.get("pitch");
        if ((pitch instanceof Integer) && (((Integer)pitch < 0) || ((Integer)pitch > 200)))
            return DataResult.failure(ERROR_INVALID_PARAMS, String.format("pitch value %d invalid", (Integer)pitch));

        return DataResult.success();
    }
}