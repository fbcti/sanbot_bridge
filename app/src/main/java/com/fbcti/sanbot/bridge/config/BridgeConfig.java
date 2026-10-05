/**
 * @file        BridgeConfig.java
 * @brief       Implements BridgeConfig class.
 */
package com.fbcti.sanbot.bridge.config;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.robot.camera.OrbbecCameraManager;
import com.fbcti.sanbot.bridge.robot.unit.BridgeCameraUnit;
import com.fbcti.sanbot.bridge.robot.unit.BridgeTtsUnit;
import com.fbcti.sanbot.bridge.robot.unit.SanbotSensorUnit;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages bridge service configuration settings.
 *
 * The class provides methods for setting and retrieving configuration settings, resetting settings
 * to their default values, and for reading settings from and saving settings to the configuration
 * file.
 *
 * A number of settings are configured in the user interface provided by the BridgeSettingsActivity
 * class.
 * - @c robotName - friendly robot name
 * - @c httpPort - HTTP listener port used by clients to connect to the service
 * - @c apiKey - API authorization key required for REST requests
 * - @c ttsName - name of active speech synthesis platform
 * - @c enableAsr - flag specifying if speech recognition is enabled
 * - @c enableFaceDetection - flag specifying if face detection is enabled
 * - @c enableAlarmDetection - flag specifying if alarm detection is enabled
 * - @c enableZigbee - flag specifying if Zigbee is enabled
 *
 * Additional settings are set on demand by a REST or WebSocket API request:
 * - @c cameraName - name of active camera
 * - @c cameras - camera parameters grouped per camera, and optionally per camera sensor
 * - @c sensorParams - Sanbot hardware sensor parameters
 * - @c ttsParams - speech synthesis parameters
 *
 * @version     1.0.003
 * @date        10 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 * @since       1.0.001
 * @changelog
 * - ADD: setting specifying if Zigbee is enabled (1.1.003)
 */
public final class BridgeConfig
{
    /**
     * Source label used for log messages.
     */
    private static final String TAG = "BridgeConfig";

    /**
     * Instance of class for serializing/deserializing JSON objects.
     */
    private static final Gson gson = new Gson();

    /**
     * Configuration file name. The file is located in the application's private storage.
     */
    private static final String BRIDGE_CONFIG_FILE = "config.json";

    /**
     * Default friendly robot name.
     */
    private static final String DEFAULT_ROBOT_NAME = "Sanbot";

    /**
     * Default HTTP listener port.
     */
    private static final int DEFAULT_HTTP_PORT = 8088;

    /**
     * Default API authorization key.
     */
    private static final String DEFAULT_API_KEY = "sanbot-bridge";

    /**
     * Default value of flag specifying if speech recognition is enabled.
     */
    private static final boolean DEFAULT_ENABLE_ASR = false;

    /**
     * Default value of flag specifying if face detection is enabled.
     */
    private static final boolean DEFAULT_ENABLE_FACE_DETECTION = true;

    /**
     * Default value of flag specifying if alarm detection is enabled.
     */
    private static final boolean DEFAULT_ENABLE_ALARM_DETECTION = true;

    /**
     * Default value of flag specifying if Zigbee is enabled.
     */
    private static final boolean DEFAULT_ENABLE_ZIGBEE = true;

    /**
     * Default camera name.
     */
    private static final String DEFAULT_CAMERA_NAME = BridgeCameraUnit.DEFAULT_CAMERA_NAME;

    /**
     * Default speech synthesis platform name.
     */
    private static final String DEFAULT_TTS_NAME = BridgeTtsUnit.DEFAULT_TTS_NAME;

    /**
     * Application context used to resolve the private configuration file path.
     */
    private transient final Context context;

    /**
     * String specifying friendly robot name.
     */
    private String robotName;

    /**
     * Integer specifying HTTP listener port.
     */
    private Integer httpPort;

    /**
     * String specifying API authorization key.
     */
    private String apiKey;

    /**
     * String specifying name of active speech synthesis platform.
     */
    private String ttsName;

    /**
     * Flag specifying if speech recognition is enabled.
     */
    private Boolean enableAsr;

    /**
     * Flag specifying if alarm detection is enabled.
     */
    private Boolean enableAlarmDetection;

    /**
     * Flag specifying if face detection is enabled.
     */
    private Boolean enableFaceDetection;

    /**
     * Flag specifying Zigbee unit is enabled.
     */
    private Boolean enableZigbee;

    /**
     * String specifying name of active camera.
     */
    private String cameraName;

    /**
     * Instance of embedded class managing camera parameters.
     */
    private CameraConfig cameras = new CameraConfig();

    /**
     * Instance of embedded class managing Sanbot hardware sensor parameters.
     */
    private SensorParams sensorParams = new SensorParams();

    /**
     * Instance of embedded class managing speech synthesis parameters.
     */
    private TtsParams ttsParams = new TtsParams();

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new BridgeConfig instance with default setting values.
     *
     * The application context reference is copied to a member variable, and setDefault() is called
     * to set default configuration settings.
     *
     * @param   context         application context
     */
    public BridgeConfig(Context context)
    {
        Context appContext = context;
        if (context != null)
        {
            try
            {
                appContext = context.getApplicationContext();
            }
            catch (NullPointerException ignored)
            {
            }
        }

        this.context = appContext;
        setDefaults();
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Copies settings from existing BridgeConfig instance.
     *
     * @param   config          BridgeConfig instance from which to copy settings
     */
    public void copy(BridgeConfig config)
    {
        if (config == null) return;
        config.normalizeConfigModel();

        setRobotName(config.robotName);
        setHttpPort(config.httpPort);
        setApiKey(config.apiKey);
        setEnableAsr(config.enableAsr);
        setEnableFaceDetection(config.enableFaceDetection);
        setEnableAlarmDetection(config.enableAlarmDetection);
        setEnableZigbee(config.enableZigbee);
        setCameraName(config.getCameraName());
        this.cameras = new CameraConfig(config.cameras);
        this.sensorParams.copyFrom(config.sensorParams);
        setTtsName(config.getTtsName());
        this.ttsParams.copyFrom(config.ttsParams);
    }

    /**
     * Reads bridge configuration settings from configuration file.
     *
     * If the configuration file does not yet exist a new one is created. If the file  does exist, a
     * JSON string containing the configuration data is retrieved, and if successful the data is
     * deserialized by the @c Gson.fromJson() library function. If the configuration data could not
     * be retrieved, or the JSON string  could not be deserialized, the configuration settings
     * retain their current values.
     *
     * @return  @c true if configuration was successfully loaded, @c false on failure
     */
    public boolean load()
    {
        // Persist the defaults on first use so the active configuration is represented on disk.
        File configFile = getConfigFile();
        if (configFile.exists() == false)
        {
            setDefaults();
            return save();
        }

        // Retrieve JSON string containing confiurtion data.
        String jsonObject = get();
        if (jsonObject == null) return false;

        try
        {
            // Parse JSON object. Exit method on failure.
            BridgeConfig config = gson.fromJson(jsonObject, BridgeConfig.class);
            if (config == null) return false;
            config.normalizeConfigModel();

            // Copy retrieved configuration settings to current settings.
            copy(config);
            BridgeLog.debug(TAG, "Bridge settings read from " + configFile.getAbsolutePath());
            return true;
        }
        catch (JsonSyntaxException e)
        {
            BridgeLog.warning(TAG, "Could not parse bridge settings", e);
            return false;
        }
    }

    /**
     * Returns the configuration as a single JSON string.
     *
     * The configuration is read from the configuration file located in the application's private
     * storage.
     *
     * @return  JSON string containing configuration data, or @c null on failure
     */
    @Nullable
    public String get()
    {
        try
        {
            // Load JSON object from configuration file if the file exists. Exit method on failure.
            File configFile = getConfigFile();
            if ((configFile.exists() == false) || (configFile.isFile() == false)) return null;
            return readTextFile(configFile);
        }
        catch (IOException e)
        {
            BridgeLog.warning(TAG, "Could not read bridge settings", e);
            return null;
        }
    }

    /**
     * Writes bridge settings to configuration file.
     *
     * The instance of this class is serialized by the @c Gson.toJson() library function, and the
     * parsed data is written to the configuration file as a single JSON object.
     *
     * @return  @c true if configuration was successfully written, @c false on failure
     */
    public boolean save()
    {
        try
        {
            writeTextFile(getConfigFile(), gson.toJson(this));
            return true;
        }
        catch (IOException e)
        {
            BridgeLog.warning(TAG, "Could not save bridge settings", e);
            return false;
        }
    }

    /**
     * Returns configuration file path.
     *
     * @return  string specifying configuration file path
     */
    public String getConfigFilePath()
    {
        return getConfigFile().getAbsolutePath();
    }

    /**
     * Returns configured friendly robot name.
     *
     * @return  configured friendly robot name, or default value if not configured
     */
    public String getRobotName()
    {
        return (StringUtils.isBlank(robotName) == false) ? robotName : DEFAULT_ROBOT_NAME;
    }

    /**
     * Returns configured HTTP listener port.
     *
     * @return  configured HTTP port, or default port if not configured
     */
    public int getHttpPort()
    {
        return (httpPort != null) ? httpPort : DEFAULT_HTTP_PORT;
    }

    /**
     * Returns configured API authorization key.
     *
     * @return  configured API authorization key, or default value if not configured
     */
    public String getApiKey()
    {
        return (StringUtils.isBlank(apiKey) == false) ? apiKey : DEFAULT_API_KEY;
    }

    /**
     * Returns configured flag specifying if speech recognition is enabled.
     *
     * @return  configured speech recognition enabled flag, or default value if not configured
     */
    public boolean getEnableAsr()
    {
        return (enableAsr != null) ? enableAsr : DEFAULT_ENABLE_ASR;
    }

    /**
     * Returns configured flag specifying if face detection is enabled.
     *
     * @return  configured face detection flag, or default value if not configured
     */
    public boolean getEnableFaceDetection()
    {
        return (enableFaceDetection != null) ? enableFaceDetection : DEFAULT_ENABLE_FACE_DETECTION;
    }

    /**
     * Returns configured flag specifying if alarm detection is enabled.
     *
     * @return  configured alarm detection flag, or default value if not configured
     */
    public boolean getEnableAlarmDetection()
    {
        return (enableAlarmDetection != null) ? enableAlarmDetection : DEFAULT_ENABLE_ALARM_DETECTION;
    }

    /**
     * Returns configured flag specifying if alarm detection is enabled.
     *
     * @return  configured alarm detection flag, or default value if not configured
     */
    public boolean getEnableZigbee()
    {
        return (enableZigbee != null) ? enableZigbee : DEFAULT_ENABLE_ZIGBEE;
    }

    /**
     * Returns the configured camera name.
     *
     * @return  configured camera name
     */
    public String getCameraName()
    {
        return (StringUtils.isBlank(cameraName) == false) ? cameraName : DEFAULT_CAMERA_NAME;
    }

    /**
     * Returns parameters for specified camera.
     *
     * @param   cameraName      name of camera for which to return parameters
     *
     * @return  instance of Java @c Map class containing configured parameters
     */
    @NonNull
    public Map<String, Object> getCameraParams(String cameraName)
    {
        return cameras.getCameraParams(cameraName);
    }

    /**
     * Returns camera-sensor parameters for a single configured sensor of the specified camera.
     *
     * @param   cameraName      name of camera for which to return sensor parameters
     * @param   sensorName      name of camera sensor for which to return parameters
     *
     * @return  instance of Java @c Map class containing configured sensor parameters
     */
    @NonNull
    public Map<String, Object> getCameraSensorParams(String cameraName, String sensorName)
    {
        return cameras.getSensorParams(cameraName, sensorName);
    }

    /**
     * Returns parameters for specified sensor or group of sensors.
     *
     * @param   sensorName      name of sensor or group of sensors for which to return parameters
     *
     * @return  instance of Java @c Map class containing configured parameters
     */
    public Map<String, Object> getSensorParams(String sensorName)
    {
        return sensorParams.getParams(sensorName);
    }

    /**
     * Returns data map containing configured sensor parameters.
     *
     * @return  instance of Java @c Map class containing configured parameters
     */
    public Map<String, Object> getSensorParams()
    {
        return sensorParams.toMap();
    }

    /**
     * Checks if the sensor or group of sensors with the specified name is enabled.
     *
     * @param   sensorName      name of sensor or group of sensors to check
     *
     * @return  @c true if sensor or group of sensors is enabled, @c false otherwise
     */
    public boolean isSensorEnabled(String sensorName)
    {
        return sensorParams.isEnabled(sensorName);
    }

    /**
     * Returns configured speech synthesis platform name.
     *
     * @return  configured speech synthesis platform name, or default value if not configured
     */
    public String getTtsName()
    {
        return (StringUtils.isBlank(ttsName) == false) ? ttsName : DEFAULT_TTS_NAME;
    }

    /**
     * Returns parameters for specified speech synthesis platform.
     *
     * @param   ttsName         name of speech synthesis platform for which to return parameters
     *
     * @return  instance of Java @c Map class containing configured parameters
     */
    public Map<String, Object> getTtsParams(String ttsName)
    {
        return ttsParams.getParams(ttsName);
    }

    /**
     * Returns data map containing configured speech synthesis platform parameters.
     *
     * @return  instance of Java @c Map class containing configured parameters
     */
    public Map<String, Object> getTtsParams()
    {
        return ttsParams.toMap();
    }

    /**
     * Sets friendly robot name.
     *
     * @param   robotName       friendly robot name
     *
     * If @p robotName is not a valid friendly robot name the default robot name is set instead.
     */
    public void setRobotName(String robotName)
    {
        String trimmed = StringUtils.trim(robotName);
        if (validateRobotName(trimmed)) this.robotName = trimmed;
        else if (this.robotName == null) this.robotName = DEFAULT_ROBOT_NAME;
    }

    /**
     * Sets HTTP listener port.
     *
     * @param   httpPort    HTTP listener port
     *
     * If @p httpPort is not a valid HTTP listener port and the HTTP listener port has not yet been
     * set the default value is set instead.
     */
    public void setHttpPort(Integer httpPort)
    {
        if (validateHttpPort(httpPort)) this.httpPort = httpPort;
        else if (this.httpPort == null) this.httpPort = DEFAULT_HTTP_PORT;
    }

    /**
     * Sets API authorization key.
     *
     * @param   apiKey          API authorization key
     *
     * If @p apiKey is not a valid API authorization key and the API authorization key has not yet
     * been set the default key is set instead.
     */
    public void setApiKey(String apiKey)
    {
        String trimmed = StringUtils.trim(apiKey);
        if (validateApiKey(trimmed)) this.apiKey = trimmed;
        else if (this.apiKey == null) this.apiKey = DEFAULT_API_KEY;
    }

    /**
     * Sets flag specifying if speech recognition is enabled.
     *
     * @param   flag            flag specifying if speech recognition is enabled
     *
     * If @p flag is not a boolean and the flag has not yet been set the default value is set
     * instead.
     */
    public void setEnableAsr(Boolean flag)
    {
        enableAsr = (flag != null) ? flag : DEFAULT_ENABLE_ASR;
    }

    /**
     * Sets flag specifying if face detection is enabled.
     *
     * @param   flag            flag specifying if face detection is enabled
     *
     * If @p flag is not a boolean and the flag has not yet been set the default value is set
     * instead.
     */
    public void setEnableFaceDetection(Boolean flag)
    {
        enableFaceDetection = (flag != null) ? flag : DEFAULT_ENABLE_FACE_DETECTION;
    }

    /**
     * Sets flag specifying if alarm detection is enabled.
     *
     * @param   flag            flag specifying if alarm detection is enabled
     *
     * If @p flag is not a boolean and the flag has not yet been set the default value is set
     * instead.
     */
    public void setEnableAlarmDetection(Boolean flag)
    {
        enableAlarmDetection = (flag != null) ? flag : DEFAULT_ENABLE_ALARM_DETECTION;
    }

    /**
     * Sets flag specifying if Zigbee is enabled.
     *
     * @param   flag            flag specifying if Zigbee is enabled
     *
     * If @p flag is not a boolean and the flag has not yet been set the default value is set
     * instead.
     */
    public void setEnableZigbee(Boolean flag)
    {
        enableZigbee = (flag != null) ? flag : DEFAULT_ENABLE_ZIGBEE;
    }

    /**
     * Sets camera name.
     *
     * @param   cameraName      camera name
     *
     * If @p cameraName is not a valid camera name and the camera name has not yet been set the
     * default value is set.
     */
    public void setCameraName(String cameraName)
    {
        cameraName = BridgeCameraUnit.validateCameraName(cameraName);
        if (cameraName != null) this.cameraName = cameraName;
        else if (this.cameraName == null) this.cameraName = DEFAULT_CAMERA_NAME;
    }

    /**
     * Sets parameters for selected camera.
     *
     * @param   cameraName      name of camera for which to set parameters
     * @param   cameraParams    Java @c Map instance containing parameters
     *
     * @return  @c true if parameters were set, @c false if @p cameraName is invalid
     */
    public boolean setCameraParams(String cameraName, Map<String, Object> cameraParams)
    {
        return this.cameras.setCameraParams(cameraName, cameraParams);
    }

    /**
     * Resets parameters for specified camera to their default values.
     *
     * @param   cameraName      name of camera for which to reset parameters
     *
     * @return  @c true if parameters were reset, @c false if @p cameraName is invalid
     */
    public boolean resetCameraParams(String cameraName)
    {
        return this.cameras.resetCameraParams(cameraName);
    }

    /**
     * Reloads parameters for specified camera from the configuration file.
     *
     * @param   cameraName      name of camera for which to reload parameters
     *
     * @return  @c true if parameters were reloaded, @c false if @p cameraName is invalid
     */
    public boolean reloadCameraParams(String cameraName)
    {
        BridgeConfig tmpConfig = new BridgeConfig(context);
        if (tmpConfig.load() == false) return false;
        return this.cameras.copyCameraParams(cameraName, tmpConfig.cameras);
    }

    /**
     * Sets parameters for selected camera sensor.
     *
     * @param   cameraName      name of camera for which to set sensor parameters
     * @param   sensorName      name of camera sensor for which to set parameters
     * @param   sensorParams    Java @c Map instance containing parameters
     *
     * @return  @c true if parameters were set, @c false if either name is invalid
     */
    public boolean setCameraSensorParams(String cameraName, String sensorName, Map<String, Object> sensorParams)
    {
        return this.cameras.setSensorParams(cameraName, sensorName, sensorParams);
    }

    /**
     * Resets parameters for the specified camera sensor to their default values.
     *
     * @param   cameraName      name of camera for which to reset sensor parameters
     * @param   sensorName      name of camera sensor for which to reset parameters
     *
     * @return  @c true if parameters were reset, @c false if either name is invalid
     */
    public boolean resetCameraSensorParams(String cameraName, String sensorName)
    {
        return this.cameras.resetSensorParams(cameraName, sensorName);
    }

    /**
     * Reloads parameters for the specified camera sensor from the configuration file.
     *
     * @param   cameraName      name of camera for which to reload sensor parameters
     * @param   sensorName      name of camera sensor for which to reload parameters
     *
     * @return  @c true if parameters were reloaded, @c false if either name is invalid
     */
    public boolean reloadCameraSensorParams(String cameraName, String sensorName)
    {
        BridgeConfig tmpConfig = new BridgeConfig(context);
        if (tmpConfig.load() == false) return false;
        return this.cameras.copySensorParams(cameraName, sensorName, tmpConfig.cameras);
    }

    /**
     * Sets parameters for selected sensor or group of sensors.
     *
     * @param   sensorName      name of sensor or group of sensors for which to set parameters
     * @param   sensorParams    Java @c Map instance containing parameters
     *
     * @return  @c true if parameters were set, @c false if @p sensorName is invalid
     */
    public boolean setSensorParams(String sensorName, Map<String, Object> sensorParams)
    {
        return this.sensorParams.setParams(sensorName, sensorParams);
    }

    /**
     * Resets parameters for specified sensor or group of sensors to their default values.
     *
     * @param   sensorName      name of sensor or group of sensors for which to reset parameters
     *
     * @return  @c true if parameters were reset, @c false if @p sensorName is invalid
     */
    public boolean resetSensorParams(String sensorName)
    {
        return this.sensorParams.resetParams(sensorName);
    }

    /**
     * Reloads parameters for specified sensor or group os sensors from the configuration file.
     *
     * @param   sensorName      name of speech sensor or group os sensors for which to reload
     *                          parameters
     *
     * @return  @c true if parameters were reloaded, @c false if @p sensorName is invalid
     */
    public boolean reloadSensorParams(String sensorName)
    {
        BridgeConfig tmpConfig = new BridgeConfig(context);
        if (tmpConfig.load() == false) return false;

        Map<String, Object> tmpParams = tmpConfig.sensorParams.params.get(sensorName);
        if (tmpParams == null) return false;

        this.sensorParams.params.put(sensorName, tmpParams);
        return true;
    }

    /**
     * Sets speech synthesis platform name.
     *
     * @param   ttsName         speech synthesis platform name
     *
     * If @p ttsName s not a valid speech synthesis platform name and the speech synthesis platform
     * name has not yet been set the default value is set.
     */
    public void setTtsName(String ttsName)
    {
        String match = StringUtils.fromArray(BridgeTtsUnit.TTS_NAMES, StringUtils.trim(ttsName), true);
        if (match != null) this.ttsName = match;
        else if (this.ttsName == null) this.ttsName = DEFAULT_TTS_NAME;
    }

    /**
     * Sets parameters for the specified speech synthesis platform.
     *
     * @param   ttsName         name of speech synthesis platform for which to set parameters
     * @param   ttsParams       Java @c Map instance containing parameters
     *
     * @return  @c true if parameters were set, @c false if @p ttsName is invalid
     */
    public boolean setTtsParams(String ttsName, Map<String, Object> ttsParams)
    {
        return this.ttsParams.setParams(ttsName, ttsParams);
    }

    /**
     * Resets parameters for specified speech synthesis platform to their default values.
     *
     * @param   ttsName         name of speech synthesis platform for which to reset parameters
     *
     * @return  @c true if parameters were reset, @c false if @p ttsName is invalid
     */
    public boolean resetTtsParams(String ttsName)
    {
        return this.ttsParams.resetParams(ttsName);
    }

    /**
     * Reloads parameters for specified speech synthesis platform the from configuration file.
     *
     * @param   ttsName         name of speech synthesis platform for which to reload parameters
     *
     * @return  @c true if parameters were reloaded, @c false if @p ttsName is invalid
     */
    public boolean reloadTtsParams(String ttsName)
    {
        BridgeConfig tmpConfig = new BridgeConfig(context);
        if (tmpConfig.load() == false) return false;

        Map<String, Object> tmpParams = tmpConfig.ttsParams.params.get(ttsName);
        if (tmpParams == null) return false;

        this.ttsParams.params.put(ttsName, tmpParams);
        return true;
    }

    /**
     * Validates friendly robot name.
     *
     * The specified robot name is valid if it is not empty.
     *
     * @param   robotName       friendly robot name to validate
     *
     * @return  @c true if robot name is valid, @c false if invalid
     */
    public boolean validateRobotName(String robotName)
    {
        return (StringUtils.isBlank(robotName) == false);
    }

    /**
     * Validates HTTP listener port.
     *
     * The specified HTTP listener port is valid if it is in the supported unprivileged port range.
     *
     * @param   httpPort        integer specifying HTTP listener port to validate
     *
     * @return  @c true if HTTP listener port is valid, @c false if invalid
     */
    public boolean validateHttpPort(Integer httpPort)
    {
        return ((httpPort != null) && (httpPort >= 1024) && (httpPort <= 65535));
    }

    /**
     * Validates API authorization key.
     *
     * The specified AP authorization key is valid if it is not empty.
     *
     * @param   apiKey          string specifying API authorization key to validate
     *
     * @return  @c true if API authorization key is valid, @c false if invalid
     */
    public boolean validateApiKey(String apiKey)
    {
        return (StringUtils.isBlank(apiKey) == false);
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Reverts all configuration settings to their default values.
     */
    private void setDefaults()
    {
        setRobotName(DEFAULT_ROBOT_NAME);
        setHttpPort(DEFAULT_HTTP_PORT);
        setApiKey(DEFAULT_API_KEY);
        setEnableAlarmDetection(DEFAULT_ENABLE_ALARM_DETECTION);
        setEnableAsr(DEFAULT_ENABLE_ASR);
        setEnableFaceDetection(DEFAULT_ENABLE_FACE_DETECTION);
        setEnableZigbee(DEFAULT_ENABLE_ZIGBEE);
        setCameraName(DEFAULT_CAMERA_NAME);
        cameras = new CameraConfig();
        sensorParams = new SensorParams();
        setTtsName(DEFAULT_TTS_NAME);
        ttsParams = new TtsParams();
    }

    /**
     * Makes sure the configuration model is initialized.
     */
    private void normalizeConfigModel()
    {
        if (cameras == null) cameras = new CameraConfig();
        else cameras.initCameraConfig();

        if (sensorParams == null) sensorParams = new SensorParams();
        if (ttsParams == null) ttsParams = new TtsParams();
    }

    /**
     * Returns the configuration file object.
     *
     * The full path of the configuration file is constructed from the application's private storage
     * directory and the configuration file name.
     *
     * @return  instance of Java @c File class representing file to read
     */
    @NonNull
    private File getConfigFile()
    {
        if (context == null) throw new IllegalStateException("Application context is required");

        return new File(context.getFilesDir(), BRIDGE_CONFIG_FILE);
    }

    /**
     * Reads specified text file.
     *
     * @param   file            instance of Java @c File class representing file to read
     *
     * @return  string containing file content
     *
     * @throws  IOException thrown if file could not be read
     */
    @NonNull
    private String readTextFile(File file) throws IOException
    {
        try (FileInputStream inputStream = new FileInputStream(file))
        {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int read;
            while ((read = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, read);
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes text to the specified file.
     *
     * @param   file            file to write
     * @param   text            text to write
     *
     * @throws  IOException thrown if file could not be written
     */
    private void writeTextFile(File file, String text) throws IOException
    {
        File parent = file.getParentFile();
        if ((parent != null) && (parent.exists() == false) && (parent.mkdirs() == false))
            throw new IOException("Could not create folder " + parent.getAbsolutePath());
        if ((parent != null) && (parent.isDirectory() == false))
            throw new IOException("Folder path " + parent.getAbsolutePath() + " is not a directory");

        try (FileOutputStream outputStream = new FileOutputStream(file, false))
        {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Abstract class for storing parameters grouped into named sections.
     */
    static abstract class AbstractParams
    {
        /** Data map containing parameters for each section. */
        protected final Map<String, Map<String, Object>> params = new LinkedHashMap<>();

        /*******************************************************************************************
         * PACKAGE-PRIVATE METHODS
         ******************************************************************************************/

        /**
         * Returns a copy of the parameters in the specified section.
         *
         * @param   sectionName     name of the section to retrieve
         *
         * @return  map containing section parameters
         */
        Map<String, Object> getParams(String sectionName)
        {
            Map<String, Object> sectionParams = params.get(sectionName);
            return (sectionParams != null) ? copySection(sectionParams) : new LinkedHashMap<>();
        }

        /**
         * Merges parameters into the specified section.
         *
         * Only known sections can be updated. Incoming values are copied and then normalized before
         * they become part of the stored configuration.
         *
         * @param   sectionName     name of the section to update
         * @param   sectionParams   parameters to merge into the section
         *
         * @return  @c true if parameters were set, @c false if section does not exist
         */
        boolean setParams(String sectionName, Map<String, Object> sectionParams)
        {
            // If the specified section does not exist or no new parameters are supplied there is
            // nothing to do.
            if ((this.params.containsKey(sectionName) == false) || (sectionParams == null)) return false;

            // Get current parameters in specified section. If not yet present set default values.
            Map<String, Object> currentParams = params.get(sectionName);
            if (currentParams == null)
            {
                currentParams = initTtsParams();
                params.put(sectionName, currentParams);
            }

            // Merge new parameters and current parameters. Existing current parameters are
            // overwritten by new parameters.
            currentParams.putAll(sectionParams);

            // Make sure there are no missing or invalid parameters.
            normalizeParams(currentParams);

            return true;
        }

        /**
         * Resets parameters in specified section to their default values.
         *
         * @param   sectionName     name of section for which to reset parameters
         *
         * @return  @c true if parameters were reset, @c false if section does not exist
         */
        boolean resetParams(String sectionName)
        {
            if (params.containsKey(sectionName) == false) return false;

            params.put(sectionName, initTtsParams());
            return true;
        }

        /**
         * Returns a deep copy of all stored sections and their parameters.
         *
         * @return  instance of Java @c Map class containing copied parameter
         */
        Map<String, Object> toMap()
        {
            Map<String, Object> sectionParams = MapUtils.createMap();
            for (Map.Entry<String, Map<String, Object>> section : params.entrySet())
            {
                sectionParams.put(section.getKey(), copySection(section.getValue()));
            }
            return sectionParams;
        }

        /*******************************************************************************************
         * PROTECTED METHODS
         ******************************************************************************************/

        /**
         * Replaces all parameters from an existing AbstractParams instance.
         *
         * @param   source          AbstractParams instance to copy
         */
        protected void copyFrom(AbstractParams source)
        {
            if (source == null) return;

            params.clear();
            for (Map.Entry<String, Map<String, Object>> section : source.params.entrySet())
            {
                params.put(section.getKey(), copySection(section.getValue()));
            }
        }

        /**
         * Copies and normalizes parameters in single section.
         *
         * @param   sectionParams   section from which to copy parameters
         *
         * @return  instance of Java @c Map class containing copied and normalized parameters
         */
        protected Map<String, Object> copySection(Map<String, Object> sectionParams)
        {
            Map<String, Object> copyParams = new LinkedHashMap<>();
            if (sectionParams != null) copyParams.putAll(sectionParams);
            normalizeParams(copyParams);
            return copyParams;
        }

        /**
         * Creates default parameters in single section.
         *
         * Subclasses must implement this abstract method to create a single section containing
         * default parameters.
         *
         * @return  instance of Java @c Map class containing normalized parameters
         */
        protected abstract Map<String, Object> initTtsParams();

        /**
         * Normalizes parameters in specified section.
         *
         * Subclasses use this hook to fill in missing defaults or coerce values into the expected
         * types.
         *
         * @param   sectionParams   parameters to normalize
         */
        protected void normalizeParams(Map<String, Object> sectionParams) {}
    }

    /**
     * Stores parameters for supported sensors or group of sensors.
     *
     * This class extends the AbstractParams class. It stores the parameters for each supported
     * sensor. For sensors of the same type only single set of parameters can be configured, so each
     * item in the @c params member variable contains parameters for a single sensor or a group of
     * sensors of the same type.
     */
    static final class SensorParams extends AbstractParams
    {
        /*******************************************************************************************
         * CONSTRUCTORS
         ******************************************************************************************/

        /**
         * Constructs new SensorParams instance.
         *
         * The initTtsParams() method is called for each supported sensor or group of sensors to
         * create a set of default parameters.
         */
        public SensorParams()
        {
            for (String sensorName : SanbotSensorUnit.SENSOR_NAMES) params.put(sensorName, initTtsParams());
        }

        /*******************************************************************************************
         * PACKAGE-PRIVATE METHODS
         ******************************************************************************************/

        /**
         * Returns the enabled state for the specified sensor or group of sensors.
         *
         * @param   sensor      sensor or group of sensors for which to return enabled state
         *
         * @return  @c true if the sensor is enabled, @c false if is disabled or invalid
         */
        boolean isEnabled(String sensor)
        {
            Map<String, Object> sensorSettings = params.get(sensor);
            return ((sensorSettings != null) && (Boolean.TRUE.equals(sensorSettings.get("enable"))));
        }

        /**
         * Copies sensor parameters from existing SensorParams instance.
         *
         * @param   source      SensorParams instance from which to copy parameters
         */
        void copyFrom(SensorParams source)
        {
            super.copyFrom(source);
        }

        /*******************************************************************************************
         * PROTECTED METHODS
         ******************************************************************************************/

        /**
         * Creates set of default parameters for single speech synthesis platform.
         *
         * @return  Java @c Map instance containing default parameters
         */
        @NonNull
        @Override
        protected Map<String, Object> initTtsParams()
        {
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("enable", false);
            return settings;
        }

        /**
         * Normalizes parameters for single sensor or group of sensors.
         *
         * If the @c enable parameter is not present it is added with value @c false.
         *
         * @param   settings        sensor settings map to normalize
         */
        @Override
        protected void normalizeParams(@NonNull Map<String, Object> settings)
        {
            if ((settings.get("enable") instanceof Boolean) == false) settings.put("enable", false);
        }
    }

    /**
     * Stores parameters for supported cameras.
     *
     * This class contains a CameraParams instance for each
     * supported camera.
     */
    static final class CameraConfig
    {
        /** Sensor names for cameras with multiple sensors. */
        private static final String[] SENSOR_NAMES = { "color", "depth", "ir" };

        /** Data map containing CameraParams instance for each supported camera. */
        private final Map<String, CameraParams> cameras = new LinkedHashMap<>();

        /*******************************************************************************************
         * CONSTRUCTORS
         ******************************************************************************************/

        /**
         * Constructs a new CameraConfig instance.
         *
         * The parameters for each supported camera are initialized by calling initCameraConfig().
         */
        CameraConfig()
        {
            initCameraConfig();
        }

        /**
         * Copies an existing CameraConfig instance.
         *
         * The camera parameters are copied from the CameraConfig instance, and initCameraConfig()
         * is called to add default parameters for cameras for which no parameters are available.
         *
         * @param   cameraConfig    CameraConfig instance to copy, or null for defaults
         */
        CameraConfig(CameraConfig cameraConfig)
        {
            if (cameraConfig != null)
            {
                for (Map.Entry<String, CameraParams> entry : cameraConfig.cameras.entrySet())
                {
                    cameras.put(entry.getKey(), new CameraParams(entry.getValue()));
                }
            }
            initCameraConfig();
        }

        /*******************************************************************************************
         * PACKAGE-PRIVATE METHODS
         ******************************************************************************************/

        /**
         * Initializes the camera configuration.
         *
         * If the CameraParams list does not yet contain an entry for a supported camera, a new
         * entry is added. If the entry already exists, the parameters for all available sensors
         * are initialized.
         */
        void initCameraConfig()
        {
            for (String[] cameraName : BridgeCameraUnit.CAMERA_NAMES)
            {
                String name = cameraName[0];
                if (cameras.containsKey(name) == false)
                {
                    cameras.put(name, new CameraParams(name));
                }
                else
                {
                    CameraParams camera = cameras.get(name);
                    if (camera != null) camera.initSensorConfig();
                }
            }
        }

        /**
         * Returns the parameters for the specified camera.
         *
         * @param   cameraName      name of camera for which to return parameters
         *
         * @return  Java @c Map instance containing parameters
         *
         * If the specified camera is not valid an empty data map is returned.
         */
        @NonNull
        Map<String, Object> getCameraParams(String cameraName)
        {
            CameraParams params = getCameraParamsFromMap(cameraName);
            return (params != null) ? params.getCameraParams() : new LinkedHashMap<>();
        }

        /**
         * Sets the parameters for the specified camera.
         *
         * @param   cameraName      name of camera for which to set parameters
         * @param   cameraParams    parameters to set
         *
         * @return  @c true if parameters were successfully set
         */
        boolean setCameraParams(String cameraName, Map<String, Object> cameraParams)
        {
            CameraParams params = getCameraParamsFromMap(cameraName);
            if (params == null) return false;
            params.setCameraParams(cameraParams);
            return true;
        }

        /**
         * Copies the parameters for the specified camera from the specified source.
         *
         * @param   cameraName      name of camera for which to copy parameters
         * @param   cameraConfig    source from which to copy parameters
         *
         * @return  @c true if parameters were successfully copied
         */
        boolean copyCameraParams(String cameraName, CameraConfig cameraConfig)
        {
            CameraParams params = (cameraConfig != null) ? cameraConfig.getCameraParamsFromMap(cameraName) : null;
            if (params == null) return false;

            return setCameraParams(cameraName, params.getCameraParams());
        }

        /**
         * Resets the parameters for the specified camera.
         *
         * @param   cameraName      name of camera for which to reset parameters
         *
         * @return  @c true if parameters were successfully reset
         */
        boolean resetCameraParams(String cameraName)
        {
            CameraParams params = getCameraParamsFromMap(cameraName);
            if (params == null) return false;
            params.resetCameraParams();
            return true;
        }

        /**
         * Returns the parameters for the specified camera sensor.
         *
         * @param   cameraName      name of camera for which to return parameters
         * @param   sensorName      name of sensor for which to return parameters
         *
         * @return  Java @c Map instance containing parameters
         *
         * If the specified camera or section is not valid an empty data map is returned.
         */
        @NonNull
        Map<String, Object> getSensorParams(String cameraName, String sensorName)
        {
            CameraParams params = getCameraParamsFromMap(cameraName);
            return (params != null) ? params.getSensorParams(sensorName) : new LinkedHashMap<>();
        }

        /**
         * Sets the parameters for the specified camera sensor.
         *
         * @param   cameraName      name of camera for which to set parameters
         * @param   sensorName      name of sensor for which to set parameters
         * @param   sensorParams    parameters to set
         *
         * @return  @c true if parameters were successfully set
         */
        boolean setSensorParams(String cameraName, String sensorName, Map<String, Object> sensorParams)
        {
            CameraParams params = getCameraParamsFromMap(cameraName);
            if (params == null) return false;
            return params.setSensorParams(sensorName, sensorParams);
        }

        /**
         * Copies the parameters for the specified camera sensor from the specified source.
         *
         * @param   cameraName      name of camera for which to copy parameters
         * @param   sensorName      name of sensor for which to copy parameters
         * @param   cameraConfig    source from which to copy cameras
         *
         * @return  @c true if parameters were successfully copied
         */
        boolean copySensorParams(String cameraName, String sensorName, CameraConfig cameraConfig)
        {
            CameraParams params = (cameraConfig != null) ? cameraConfig.getCameraParamsFromMap(cameraName) : null;
            if (params == null) return false;
            return setSensorParams(cameraName, sensorName, params.getSensorParams(sensorName));
        }

        /**
         * Resets the parameters for the specified camera sensor.
         *
         * @param   cameraName      name of camera for which to reset parameters
         * @param   sensorName      name of sensor for which to reset parameters
         *
         * @return  @c true if parameters were successfully reset
         */
        boolean resetSensorParams(String cameraName, String sensorName)
        {
            CameraParams params = getCameraParamsFromMap(cameraName);
            return (params != null) && params.resetSensorParams(sensorName);
        }

        /*******************************************************************************************
         * PRIVATE METHODS
         ******************************************************************************************/

        /**
         * Returns the parameter for the specified camera.
         *
         * @param   cameraName      name of camera for which to return section
         *
         * @return  CameraParams instance, or @c null if camera name is invalid
         */
        @Nullable
        private CameraParams getCameraParamsFromMap(String cameraName)
        {
            String validated = BridgeCameraUnit.validateCameraName(cameraName);
            return (validated != null) ? cameras.get(validated) : null;
        }

        /*******************************************************************************************
         * CLASSES / ENUMERATORS / INTERFACES
         ******************************************************************************************/

        /**
         * Stores parameters for supported cameras.
         */
        static final class CameraParams
        {
            /** Camera name. */
            private final String cameraName;

            /** Camera parameters. */
            private final Map<String, Object> cameraParams = new LinkedHashMap<>();

            /** Sensor parameters. */
            private final Map<String, Map<String, Object>> sensorParams = new LinkedHashMap<>();

            /***************************************************************************************
             * CONSTRUCTORS
             **************************************************************************************/

            /**
             * Constructs a new CameraParams instance.
             *
             * The parameters for each supported camera sensor are initialized by calling
             * initSensorConfig().
             *
             * @param   cameraName      name of camera for which to construct CameraParams instance
             */
            CameraParams(String cameraName)
            {
                this.cameraName = cameraName;
                initSensorConfig();
            }

            /**
             * Copies an existing CopyParams instance.
             *
             * The sensor parameters for each sensors and the camera parameters are copied from the
             * CameraParams instance. After copying, initSensorConfig() is called to add parameters
             * for sensors for which no parameters are available.
             *
             * @param   cameraParams    CameraParams instance to copy, or null for defaults
             */
            CameraParams(CameraParams cameraParams)
            {
                this.cameraName = (cameraParams != null) ? cameraParams.cameraName : null;
                if (cameraParams != null)
                {
                    this.cameraParams.putAll(cameraParams.cameraParams);
                    for (Map.Entry<String, Map<String, Object>> sensor : cameraParams.sensorParams.entrySet())
                    {
                        sensorParams.put(sensor.getKey(), copyCameraParams(sensor.getValue()));
                    }
                }
                initSensorConfig();
            }

            /***************************************************************************************
             * PACKAGE-PRIVATE METHODS
             **************************************************************************************/

            /**
             * Initializes the sensor configuration.
             *
             * If the CameraParams list does not yet contain an entry for a supported sensor, a new
             * entry is added. If the entry already exists, the parameters for all available sensors
             * are copied.
             */
            void initSensorConfig()
            {
                if (OrbbecCameraManager.CAMERA_NAME.equals(cameraName))
                {
                    for (String sensorName : SENSOR_NAMES)
                    {
                        if (sensorParams.containsKey(sensorName) == false) sensorParams.put(sensorName, new LinkedHashMap<>());
                    }
                }
                else sensorParams.clear();
            }

            /**
             * Returns the parameters for the current camera.
             *
             * @return  Java @c Map containing camera parameters
             */
            @NonNull
            Map<String, Object> getCameraParams()
            {
                return copyCameraParams(cameraParams);
            }

            /**
             * Sets the parameters for the current camera.
             *
             * @param   cameraParams    parameters to set
             */
            void setCameraParams(Map<String, Object> cameraParams)
            {
                this.cameraParams.clear();
                if (cameraParams != null) this.cameraParams.putAll(cameraParams);
            }

            /**
             * Returns the parameters for the specified camera sensor.
             *
             * @param   sensorName      name of sensor for which to return parameters
             *
             * @return  Java @c Map instance containing parameters
             *
             * If the specified camera or section is not valid an empty data map is returned.
             */
            @NonNull
            Map<String, Object> getSensorParams(String sensorName)
            {
                Map<String, Object> sensorParams = this.sensorParams.get(sensorName);
                return (sensorParams != null) ? copyCameraParams(sensorParams) : new LinkedHashMap<>();
            }

            /**
             * Sets the parameters for the specified camera sensor.
             *
             * @param   sensorName      name of sensor for which to set parameters
             * @param   sensorParams    parameters to set
             *
             * @return  @c true if parameters were successfully set
             */
            boolean setSensorParams(String sensorName, Map<String, Object> sensorParams)
            {
                if (sensorName == null) return false;
                if (this.sensorParams.containsKey(sensorName) == false) return false;
                this.sensorParams.put(sensorName, copyCameraParams(sensorParams));
                return true;
            }

            /**
             * Resets the parameters for the current camera.
             */
            void resetCameraParams()
            {
                cameraParams.clear();
            }

            /**
             * Resets the parameters for the current camera sensor.
             *
             * @param   sensorName      name of sensor for which to reset parameters
             *
             * @return  @c true if parameters were successfully reset
             */
            boolean resetSensorParams(String sensorName)
            {
                if (sensorParams.containsKey(sensorName) == false) return false;
                sensorParams.put(sensorName, new LinkedHashMap<>());
                return true;
            }

            /***************************************************************************************
             * PRIVATE METHODS
             **************************************************************************************/

            /**
             * Returns a copy of the specified parameters.
             *
             * @param   params          parameters to copy
             *
             * @return  Java @c Map instance containing parameters
             */
            @NonNull
            private static Map<String, Object> copyCameraParams(Map<String, Object> params)
            {
                Map<String, Object> copyParams = new LinkedHashMap<>();
                if (params != null) copyParams.putAll(params);
                return copyParams;
            }
        }
    }

    /**
     * Stores parameters for supported text-to=speech platforms.
     *
     * This class extends the AbstractParams class. It stores the parameters for each supported
     * supported text-to=speech platform.
     */
    static final class TtsParams extends AbstractParams
    {
        /*******************************************************************************************
         * CONSTRUCTORS
         ******************************************************************************************/

        /**
         * Constructs new TtsParams instance.
         *
         * The initTtsParams() method is called for each supported text-to=speech platform to create a
         * set of default parameters.
         */
        public TtsParams()
        {
            for (String ttsName : BridgeTtsUnit.TTS_NAMES) params.put(ttsName, initTtsParams());
        }

        /*******************************************************************************************
         * PACKAGE-PRIVATE METHODS
         ******************************************************************************************/

        /**
         * Copies text-to=speech platform parameters from existing TtsParams instance.
         *
         * @param   source      TtsParams instance from which to copy parameters
         */
        void copyFrom(TtsParams source)
        {
            super.copyFrom(source);
        }

        /*******************************************************************************************
         * PROTECTED METHODS
         ******************************************************************************************/

        /**
         * Normalizes parameters for single text-to=speech engine.
         *
         * After normalization, the data map referenced by the @p params parameter include:
         *
         * - @c engine: Any string with leading and trailing white space trimmed.
         * - @c language: Any string with leading and trailing white space trimmed.
         * - @c voice: Any string with leading and trailing white space trimmed.
         * - @c speed: Integer in range <tt>[0..200]</tt>.
         * - @c pitch: Integer in range <tt>[0..200]</tt>.
         *
         * Parameter values are trimmed and converted to lower case.
         *
         * @param   params        sensor settings map to normalize
         */
        @Override
        protected void normalizeParams(Map<String, Object> params)
        {
            if (params == null) return;

            Object engine = params.get("engine");
            if ((engine instanceof String) && (StringUtils.isBlank((String)engine) == false)) params.put("engine", StringUtils.trim((String)engine));
            else params.put("engine", "");

            Object language = params.get("language");
            if ((language instanceof String) && (StringUtils.isBlank((String)language) == false)) params.put("language", StringUtils.trim((String)language));
            else params.put("language", BridgeTtsUnit.DEFAULT_LANGUAGE);

            Object voice = params.get("voice");
            if ((voice instanceof String) && (StringUtils.isBlank((String)voice) == false)) params.put("voice", StringUtils.trim((String)voice));
            else params.put("voice", "");

            Object speed = params.get("speed");
            if (speed instanceof Integer) params.put("speed", Math.max(0, Math.min(200, (Integer)speed)));
            else params.put("speed", BridgeTtsUnit.DEFAULT_TTS_SPEED);

            Object pitch = params.get("pitch");
            if (pitch instanceof Integer) params.put("pitch", Math.max(0, Math.min(200, (Integer)pitch)));
            else params.put("pitch", BridgeTtsUnit.DEFAULT_TTS_PITCH);
        }

        /**
         * Creates map containing default parameter values for single text-to=speech engine.
         *
         * @return  Java @c Map instance containing default parameters
         */
        @NonNull
        @Override
        protected Map<String, Object> initTtsParams()
        {
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("engine", "");
            settings.put("language", BridgeTtsUnit.DEFAULT_LANGUAGE);
            settings.put("voice", "");
            settings.put("speed", BridgeTtsUnit.DEFAULT_TTS_SPEED);
            settings.put("pitch", BridgeTtsUnit.DEFAULT_TTS_PITCH);
            return settings;
        }
    }
}
