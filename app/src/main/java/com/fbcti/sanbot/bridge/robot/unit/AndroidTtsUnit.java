/**
 * @file        AndroidTtsUnit.java
 * @brief       Implements AndroidTtsUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages Android text-to-speech feature.
 *
 * This class extends the abstract BridgeTtsUnit class to provide access text-to-speech features
 * through the native Android @c TextToSpeech API.
 *
 * @version     1.0.001
 * @date        9 Oct 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 * @since       1.0.001
 * @changelog
 * - NEW: @c flush parameter added to @c speak request to cancel current and queued text-to-speech
 *   operations (1.0.004)
 */
public final class AndroidTtsUnit extends BridgeTtsUnit
{
    /** Source label used for log messages. */
    private static final String TAG = "AndroidTtsUnit";

    /** Human-readable name of this text-to-speech unit. */
    public static final String TTS_NAME = ANDROID_TTS_NAME;

    /** Maximum time to wait for Android TextToSpeech API initialization. */
    private static final long ANDROID_TTS_INIT_TIMEOUT_MS = 5000L;

    /** Android application context. */
    private final Context context;

    /** Active Android TextToSpeech API instance. */
    private TextToSpeech speechManager = null;

    /** Active text-to-speech engine package. */
    private String ttsEngine = null;

    /** Active text-to-speech locale. */
    private Locale ttsLocale = null;

    /** Result returned by most recent text-to-speech language update. */
    private int ttsLanguageResult = TextToSpeech.ERROR;

    /** Last Android text-to-speech initialization status. */
    private volatile int ttsInitStatus = TextToSpeech.ERROR;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new AndroidTtsUnit instance.
     *
     * The base class constructor is called to copy the persistent configuration and callback host
     * to member variables. The Android application context is validated and copied to a member
     * variable.
     *
     * @param   context         Android application context
     * @param   config          persistent bridge configuration
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public AndroidTtsUnit(Context context, BridgeConfig config, BridgeEventHost eventHost)
    {
        super(config, eventHost);

        // Set Android application context.
        if (context == null) throw new IllegalArgumentException("context may not be null");
        Context applicationContext = context.getApplicationContext();
        this.context = (applicationContext != null) ? applicationContext : context;
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * The text-to-speech parameters are validated, and the unit status is set. Since the method
     * runs in the main thread, initialization of the actual engine is not yet possible so that is
     * done when the first request to speak a text is received.
     *
     * @param   initData        initialization data (unused)
     */
    @Override
    public synchronized void init(Object initData)
    {
        logStatus();

        // Reset default parameters if current parameters are invalid. Since this can only be the
        // case if the configuration file was corrupted, the save flag is included so the default
        // configuration is written to that file.
        if (validateParams(config.getTtsParams(config.getTtsName())).isSuccess() == false)
            updateConfig(true, false, true, null);

        // initialization complete.
        unitStatus = UnitStatus.STARTED;

        logStatus();
    }

    /**
     * Performs unit shutdown tasks.
     */
    @Override
    public synchronized void shutdown()
    {
        shutdownTts();
        speechManager = null;
        unitStatus = UnitStatus.SHUTDOWN;
        BridgeLog.info(TAG, "Unit status is " + unitStatus.toString());
    }

    /***********************************************************************************************
     * PROTECTED METHODS
     **********************************************************************************************/

    /**
     * Initializes a data map containing supported languages.
     *
     * Each item in the data map consists of a language code and a human-readable language name.
     * A single language code may be associated with multiple language names.
     *
     * @return  Java @c Map instance containing supported languages
     */
    @NonNull
    protected Map<String, String> initLanguageMap()
    {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("en-GB", "english");
        map.put("en-US", "english");
        map.put("nl-NL", "dutch");
        map.put("de-DE", "german");
        map.put("da-DK", "danish");
        map.put("fr-FR", "french");
        map.put("it-IT", "italian");
        map.put("es-ES", "spanish");
        map.put("pt-PT", "portugese");
        map.put("pl-PL", "polish");
        map.put("tr-TR", "turkish");
        map.put("sh-CN", "chinese");
        map.put("jp-JP", "japanese");
        map.put("ko-KR", "korean");
        map.put("ar-SA", "arabic");
        return map;
    }

    /**
     * Checks if the text-to-speech engine is initialized and ready for use.
     *
     * @return  @c true if text-to-speech engine is initialized, @c false if not
     */
    protected boolean isTtsReady()
    {
        return ((speechManager != null) && (ttsInitStatus == TextToSpeech.SUCCESS));
    }

    /**
     * Start speaking the specified text using current text-to-speech settings.
     *
     * If the text length exceeds the maximum an error result is returned. The @c speak() function
     * implemented by the Android SDK @c TextToSpeech class is called to request the text-to-speech
     * engine to start speaking.
     *
     * @param   text            text to speak
     * @param   params          Java @c map instance containing optional text-to-speech parameters
     *
     * By default, speech operations are queued so a speech operation is only executed if previous
     * speech operations are competed.  A @c flush parameter may be supplied to flush the current
     * speech operation and remove all earlier speech operation from the queue so the specified text
     * is spoken immediately.
     *
     * @return  DataResult instance specifying operation result
     *
     * If the speak request is submitted the @c result property in the operation result object is a
     * string specifying a unique utterance id for this request.
     */
    @Override
    protected DataResult speechStart(String text, Map<String, Object> params)
    {
        // Make sure text-to-speech engine is available.
        DataResult result = ensureTtsReady();
        if (result.isFailure()) return result;

        // Check if text length does not exceed maximum.
        int maxLength = TextToSpeech.getMaxSpeechInputLength();
        if (text.length() > maxLength) return DataResult.failure(ERROR_INVALID_PARAMS, "text length may not exceed " + maxLength + " characters");

        // Exit if speech manager object is not available.
        if (speechManager == null) return DataResult.notavailable("TextToSpeech");

        // Create a unique utterance id.
        String utteranceId = "android-tts-" + System.currentTimeMillis();

        // Get queue parameter parameter.
        Object flush = (params.containsKey("flush")) ? params.get("flush") : false;
        int queue = ((flush instanceof Boolean) && ((Boolean)flush) == true) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;

        // Call Android TextToSpeech API function.
        BridgeLog.apicall("AndroidSDK", "TextToSpeech", "speak", "queue", queue, "utteranceId", utteranceId);
        int resultCode = speechManager.speak(text, queue, null, utteranceId);
        Map<String, Object> data = MapUtils.createMap("utteranceId", utteranceId, "resultCode", resultCode);
        if (resultCode == TextToSpeech.SUCCESS) return DataResult.success("Android TTS request accepted", data);
        else return DataResult.failure("Android TTS request failed", data);
    }

    /**
     * Stops speaking.
     *
     * The @c stopSpeak() function implemented by the Android SDK @c TextToSpeech class is called
     * to request the text-to-speech engine to stop speaking.
     *
     * @return  DataResult instance specifying operation result
     */
    @Override
    protected DataResult speechStop()
    {
        // Exit if speech manager object is not available.
        if (speechManager == null) return DataResult.notavailable("TextToSpeech");

        // Call Android TextToSpeech API function.
        BridgeLog.apicall("AndroidSDK", "TextToSpeech", "stop");
        int resultCode = speechManager.stop();
        Map<String, Object> data = MapUtils.createMap("resultCode", resultCode);
        if (resultCode == TextToSpeech.SUCCESS) return DataResult.success("Android TTS request accepted", data);
        else return DataResult.failure("Android TTS request failed", data);
    }

    /**
     * Returns the current speech status.
     *
     * The @c isSpeaking() function implemented by the Android SDK @c TextToSpeech class is called
     * to check if the text-to-speech engine is currently speaking.
     *
     * @return  DataResult instance specifying operation result
     *
     * The @c result property in the DataResult object represents an integer value equal to 1 if
     * the engine is currently speaking, or 0 if the engine is not speaking.
     */
    @Override
    public DataResult speechStatus()
    {
        // Exit if speech manager object is not available.
        if (speechManager == null) return DataResult.notavailable("TextToSpeech");

        // Call Android TextToSpeech API function.
        BridgeLog.apicall("AndroidSDK", "TextToSpeech", "isSpeaking");
        return DataResult.success((speechManager.isSpeaking()) ? "1" : "0");
    }

    /**
     * Sets the specified parameters for the current text-to-speech engine.
     *
     * The BCP 47 locale tag specified in the speech settings is converted to locale and if
     * supported by the text-to-speech engine is set as the active locale. The generic speech speed
     * and pitch values are converted to Android-specific values.
     *
     * @param   params          text-to-speech parameters to apply
     *
     * @return  DataResult instance specifying operation result
     *
     * The @c errorCode property in the operation result is -1 if applying the parameters failed.
     * Additional data is available in the @c description and @c result properties.
     */
    protected DataResult applyTtsParams(Map<String, Object> params)
    {
        // If text-to-speech engine is not available yet
        if (isTtsReady() == false) return DataResult.failure("Android TTS not initialized");

        // Get parameters.
        String language = MapUtils.getString(params, "language", DEFAULT_LANGUAGE);
        String voice = MapUtils.getString(params, "voice", "");
        int speed = MapUtils.getInt(params, "speed", DEFAULT_TTS_SPEED);
        int pitch = MapUtils.getInt(params, "pitch", DEFAULT_TTS_PITCH);

        // Set the first matching BCP 47 locale tag supported by the text-to-speech engine.
        List<String> localeTags = getMatchingLocaleTags(language);
        ttsLocale = null;
        ttsLanguageResult = TextToSpeech.ERROR;
        for (String localeTag : localeTags)
        {
            Locale locale = toLocale(localeTag, false);
            if (locale == null) continue;

            int languageResult = speechManager.setLanguage(locale);
            if (languageResult >= TextToSpeech.LANG_AVAILABLE)
            {
                ttsLocale = locale;
                ttsLanguageResult = languageResult;
                break;
            }
            ttsLanguageResult = languageResult;
        }

        // If the language could not be set return error response.
        if (ttsLocale == null)
        {
            return DataResult.failure("Android TTS language not available", MapUtils.createMap("engine", ttsEngine, "language", language, "localeTags", localeTags, "result", ttsLanguageResult));
        }

        // Set speech rate, pitch and voice.
        DataResult result;
        result = setTtsSpeechRate(speed);
        if (result.isFailure()) return result;
        result = setTtsPitch(pitch);
        if (result.isFailure()) return result;
        setTtsVoice(voice, ttsLocale);

        // All done.
        return DataResult.success();
    }

    /**
     * Build a data map containing speech features provided by this class.
     *
     * Feature data can only be retrieved from a fully initialized instance of the Android
     * @c TextToSpeech SDK class, so ensureTtsReady() is called, a collection of available
     * text-to-speech engines is created, and the features for each engine are retrieved.
     *
     * @return  Java @c Map instance containing text-to-speech feature data
     */
    @NonNull
    @Override
    protected Map<String, Object> buildFeatureData()
    {
        // Create data map.
        Map<String, Object> data = MapUtils.createMap("platform", "Android");
        data.put("languages", ttsLanguageMap.keySet());

        // Ensure text-to-speech engine is available.
        DataResult result = ensureTtsReady();
        if (result.isFailure()) return data;

        // Create collection of available text-to-speech engines.
        String defaultEngine = "";
        List<TextToSpeech.EngineInfo> engines = new ArrayList<>();
        TextToSpeech temporaryTts = initTemporaryTts(null);
        if (temporaryTts != null)
        {
            try
            {
                defaultEngine = temporaryTts.getDefaultEngine();
                List<TextToSpeech.EngineInfo> availableEngines = temporaryTts.getEngines();
                if (availableEngines != null) engines.addAll(availableEngines);
            }
            finally
            {
                temporaryTts.shutdown();
            }
        }

        // Add engine features to data map.
        List<Object> engineData = new ArrayList<>();
        for (TextToSpeech.EngineInfo engineInfo : engines)
        {
            engineData.add(buildEngineData(engineInfo.name));
        }
        data.put("engines", engineData);
        data.put("defaultEngine", defaultEngine);
        return data;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Initializes the speech engine if not already initialized.
     *
     * @return  DataResult instance specifying operation result
     */
    private DataResult ensureTtsReady()
    {
        // Retrieve text-to-speech parameters from configuration. Since the configuration should
        // contain validated parameters only, the default values supplied here should never be used.
        Map<String, Object> params = config.getTtsParams(config.getTtsName());
        String engine = MapUtils.getString(params, "engine", "");

        // Create TextToSpeech instance.
        String requestedEngine = StringUtils.isBlank(engine) ? null : engine;
        if ((speechManager == null) || (ttsInitStatus != TextToSpeech.SUCCESS) || (StringUtils.compare(ttsEngine, requestedEngine) == false))
        {
            speechManager = initTts(requestedEngine);
            if (isTtsReady() == false) return DataResult.failure(ttsStatus, params);

            DataResult result = applyTtsParams(params);
            if (result.isFailure() == false) return result;
        }
        ttsEngine = requestedEngine;
        return DataResult.success(params);
    }

    /**
     * Initializes the text-to-speech engine.
     *
     * A new instance of the Android SDK @c TextToSpeech class is created, and the method waits
     * until initialization is complete or an initialization timeout occurs. To prevent blocking,
     * the method exits if the bridge runs in the Android UI main thread.
     *
     * @param   engine          engine name to use, or null to use default engine
     *
     * @return  Android SDK @c TextToSpeech instance, or @c null on failure
     */
    @Nullable
    private TextToSpeech initTts(String engine)
    {
        if (isTtsServiceAvailable(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE, engine) == false)
        {
            ttsStatus = (engine == null) ? "no Android TTS engine installed" : "Android TTS engine " + engine + " not installed";
            return null;
        }

        // Shuts down the current text-to-speech engine if required.
        shutdownTts();

        // Create event listener to catch stext-to-speech engine initialization event.
        final CountDownLatch initComplete = new CountDownLatch(1);
        final AtomicInteger initStatus = new AtomicInteger(TextToSpeech.ERROR);
        TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener()
        {
            @Override
            public void onInit(int status)
            {
                initStatus.set(status);
                ttsInitStatus = status;
                ttsStatus = (status == TextToSpeech.SUCCESS) ? "initialized" : "initialization failed";
                initComplete.countDown();
            }
        };

        // Create TextToSpeech object. Return null on failure.
        try
        {
            ttsStatus = "initializing";
            speechManager = (engine == null) ? new TextToSpeech(context, listener) : new TextToSpeech(context, listener, engine);
        }
        catch (RuntimeException e)
        {
            ttsStatus = e.getClass().getSimpleName();
            return null;
        }

        // Check if the current process is running on the Android UI main thread. If this is the
        // case, don't continue to prevent blocking that thread.
        if (Looper.myLooper() == Looper.getMainLooper())
        {
            ttsStatus = "initialization pending";
            return null;
        }

        // Wait for the initialization to complete. Return null if initialization is not complete
        // after the configured time or is interrupted.
        try
        {
            if (initComplete.await(ANDROID_TTS_INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) == false)
            {
                speechManager.shutdown();
                ttsStatus = "initialization timed out";
                return null;
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            speechManager.shutdown();
            ttsStatus = "initialization interrupted";
            return null;
        }

        // Check if initialization was successful.
        if (initStatus.get() != TextToSpeech.SUCCESS)
        {
            String failureStatus = ttsStatus;
            speechManager.shutdown();
            ttsStatus = failureStatus;
            return null;
        }
        return speechManager;
    }

    /**
     * Initializes a temporary text-to-speech engine.
     *
     * A temporary text-to-speech engine is only used to retrieve information from, not to
     * actually execute speech commands. A new instance of the Android SDK @c TextToSpeech class is
     * created, and the method waits until initialization is complete or an initialization time out
     * occurs.
     *
     * @param   engine          engine name to use, or null to use default engine
     *
     * @return  Android SDK @c TextToSpeech instance, or @c null on failure
     */
    @Nullable
    private TextToSpeech initTemporaryTts(String engine)
    {
        // Create event listener to catch text-to-speech initialization event.
        final CountDownLatch initComplete = new CountDownLatch(1);
        final AtomicInteger initStatus = new AtomicInteger(TextToSpeech.ERROR);
        TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener()
        {
            @Override
            public void onInit(int status)
            {
                initStatus.set(status);
                initComplete.countDown();
            }
        };

        // Create TextToSpeech object. Return null on failure.
        TextToSpeech speechManager;
        try
        {
            speechManager = (engine == null) ? new TextToSpeech(context, listener) : new TextToSpeech(context, listener, engine);
        }
        catch (RuntimeException e)
        {
            BridgeLog.warning(TAG, "Android temporary TTS initialization failed", e);
            return null;
        }

        // Wait for the initialization to complete. Return null if initialization is not complete
        // after the configured time or is interrupted.
        try
        {
            if (initComplete.await(ANDROID_TTS_INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) == false)
            {
                speechManager.shutdown();
                return null;
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            speechManager.shutdown();
            return null;
        }

        // Check if initialization was successful.
        if (initStatus.get() != TextToSpeech.SUCCESS)
        {
            speechManager.shutdown();
            return null;
        }
        return speechManager;
    }

    /**
     * Shuts down the current text-to-speech engine.
     */
    private void shutdownTts()
    {
        // Shutdown engine if it exists.
        if (speechManager != null)
        {
            try
            {
                speechManager.stop();
                speechManager.shutdown();
            }
            catch (RuntimeException e)
            {
                BridgeLog.warning(TAG, "Android TTS shutdown failed", e);
            }
        }

        // Reset member variables.
        speechManager = null;
        ttsEngine = null;
        ttsInitStatus = TextToSpeech.ERROR;
        ttsLocale = null;
        ttsLanguageResult = TextToSpeech.ERROR;
        ttsStatus = "not initialized";
        unitStatus = UnitStatus.SHUTDOWN;
        BridgeLog.info(TAG, "Unit status is " + unitStatus.toString());
    }

    /**
     * Sets the text-to-speech speech speed.
     *
     * The platform-agnostic value of the speed is converted to an Android-specific value.
     *
     * @param   speed           platform-agnostic speech speed
     *
     * @return  instance of DataResult class specifying operation result
     *
     * The @c errorCode property in the operation result is -1 if the settings could not be applied,
     * additional data is available in the @c description and @c result properties.
     */
    private DataResult setTtsSpeechRate(int speed)
    {
        float value = toAndroidTtsSpeechRate(speed);
        if (speechManager.setSpeechRate(value) != TextToSpeech.SUCCESS)
            return DataResult.failure("Android TTS speech rate is invalid");
        return DataResult.execution(true, null);
    }

    /**
     * Sets the text-to-speech speech pitch.
     *
     * The platform-agnostic value of the pitch is converted to an Android-specific value.
     *
     * @param   pitch           platform-agnostic pitch
     *
     * @return  instance of DataResult class specifying operation result
     *
     * The @c errorCode property in the operation result is -1 if the settings could not be applied,
     * additional data is available in the @c description and @c result properties.
     */
    private DataResult setTtsPitch(int pitch)
    {
        float value = toAndroidTtsPitch(pitch);
        if (speechManager.setPitch(value) != TextToSpeech.SUCCESS)
            return DataResult.failure("Android TTS pitch is invalid");
        return DataResult.execution(true, null);
    }

    /**
     * Sets the text-to-speech voice.
     *
     * @param   voiceName       name of voice to set
     * @param   locale          locale for which to set voice
     */
    private void setTtsVoice(String voiceName, Locale locale)
    {
        // If text-to-speech has not yet been initialized the voice can not be selected.
        if ((speechManager == null) || StringUtils.isBlank(voiceName)) return;

        // Retrieve the list of voices. If this fails, keep using the default voice.
        Set<Voice> voices;
        try
        {
            voices = speechManager.getVoices();
        }
        catch (RuntimeException e)
        {
            return;
        }
        if (voices == null) return;

        String requested = StringUtils.trim(voiceName);
        for (Voice availableVoice : voices)
        {
            if (requested.equals(availableVoice.getName())) speechManager.setVoice(availableVoice);
        }

        for (Voice availableVoice : voices)
        {
            Locale voiceLocale = availableVoice.getLocale();
            if ((voiceLocale != null) && voiceLocale.equals(locale) && requested.equalsIgnoreCase(availableVoice.getName()))
                speechManager.setVoice(availableVoice);
        }
    }

    /**
     * Builds a data map containing text-to-speech engine properties.
     *
     * @param   engineName      name of text-to-speech engine for which to return data
     *
     * @return  instance of Java Map containing engine data
     */
    @NonNull
    private Map<String, Object> buildEngineData(String engineName)
    {
        Map<String, Object> data = MapUtils.createMap();
        TextToSpeech temporaryTts = initTemporaryTts(engineName);
        if (temporaryTts != null)
        {
            try
            {
                data.put("defaultLanguage", temporaryTts.getDefaultVoice().getLocale());
                addVoiceFeatureData(temporaryTts, data);
            }
            finally
            {
                temporaryTts.shutdown();
            }
        }
        return data;
    }

    /**
     * Adds Android Lollipop voice metadata to speech engine feature data.
     *
     * @param   temporaryTts    initialized temporary text-to-speech engine
     * @param   data            engine data map to update
     */
    private void addVoiceFeatureData(@NonNull TextToSpeech temporaryTts, Map<String, Object> data)
    {
        Set<Voice> voices = temporaryTts.getVoices();
        Map<String, Object> voiceData = MapUtils.createMap();
        if (voices != null)
        {
            for (Voice availableVoice : voices) voiceData.put(availableVoice.getName(), buildVoiceData(availableVoice));
            data.put("voices", voiceData);
        }

        Voice defaultVoice = temporaryTts.getDefaultVoice();
        if (defaultVoice != null) data.put("defaultVoice", buildVoiceData(defaultVoice));
    }

    /**
     * Builds a data map containing text-to-speech voice properties.
     *
     * @param   voice           voice object to return data from
     *
     * @return  instance of Java Map containing voice data
     */
    @NonNull
    private Map<String, Object> buildVoiceData(@NonNull Voice voice)
    {
        Map<String, Object> data = MapUtils.createMap();
        data.put("locale", (voice.getLocale() == null) ? "" : voice.getLocale().toString());
        data.put("quality", voice.getQuality());
        data.put("latency", voice.getLatency());
        data.put("networkConnectionRequired", voice.isNetworkConnectionRequired());
        return data;
    }

    /**
     * Checks if the package contains a service that provides the specified action.
     *
     * @param   action          action to check
     * @param   packageName     package to check
     *
     * @return  true if service provides action
     */
    private boolean isTtsServiceAvailable(String action, String packageName)
    {
        try
        {
            PackageManager packageManager = context.getPackageManager();
            Intent intent = new Intent(action);
            if (packageName != null) intent.setPackage(packageName);

            List<ResolveInfo> packages = packageManager.queryIntentServices(intent, 0);
            return (packages != null) && (packages.isEmpty() == false);
        }
        catch (RuntimeException e)
        {
            BridgeLog.warning(TAG, "Could not query packages", e);
            return true;
        }
    }

    /**
     * Convert protocol text-to-speech speed to Android speech rate.
     *
     * @param   speed           protocol text-to-speech speed to convert
     *
     * @return  Android text-to-speech speed rate
     */
    private float toAndroidTtsSpeechRate(int speed)
    {
        float value = speed / 100.0f;
        if (value < 0.1f) return 0.1f;
        return Math.min(value, 4.0f);
    }

    /**
     * Convert protocol text-to-speech pitch to Android pitch.
     *
     * @param   pitch           protocol text-to-speech pitch to convert
     *
     * @return  Android text-to-speech pitch
     */
    private float toAndroidTtsPitch(int pitch)
    {
        float value = pitch / 100.0f;
        if (value < 0.1f) return 0.1f;
        return Math.min(value, 4.0f);
    }

    /**
     * Converts the locale tag to a Java @c Locale class instance.
     *
     * @param   localeTag       locale tag to convert
     * @param   useDefault      return default locale if no locale tag is specified
     *
     * @return  Java @c locale instance matching locale tag
     */
    @Nullable
    private Locale toLocale(String localeTag, boolean useDefault)
    {
        if (StringUtils.isBlank(localeTag)) return (useDefault) ? Locale.getDefault() : null;

        String[] parts = StringUtils.trim(localeTag).replace('_', '-').split("-");
        if (parts.length == 1) return new Locale(parts[0]);
        if (parts.length == 2) return new Locale(parts[0], parts[1].toUpperCase(Locale.US));
        return new Locale(parts[0], parts[1].toUpperCase(Locale.US), parts[2]);
    }
}