/**
 * @file        SanbotSensorUnit.java
 * @brief       Implements SanbotSensorUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.mapping.SanbotMappings;
import com.fbcti.sanbot.bridge.robot.sensor.InfraredData;
import com.fbcti.sanbot.bridge.robot.sensor.OrientationData;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.ValueUtils;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.SystemManager;
//import com.sanbot.opensdk.function.unit.interfaces.hardware.ChargeStatusListener;
//import com.sanbot.opensdk.function.unit.interfaces.hardware.GravityDataListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.GyroscopeListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.InfrareListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.ObstacleListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.PIRListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.TouchSensorListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.VoiceLocateListener;
import com.sanbot.opensdk.function.unit.interfaces.system.KeyStatusListener;
import com.sanbot.opensdk.function.unit.interfaces.system.ObstacleStatusListener;
import com.sanbot.opensdk.function.unit.interfaces.system.WheelObstacleStatusListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages Sanbot SDK sensor operations.
 *
 * This class implements the following listeners for event thrown by the Sanbot @c HardwareManager
 * API:
 *
 * - @c TouchSensorListener implemented as BridgeTouchListener
 * - @c GyroscopeListener implemented as BridgeOrientationListener
 * - @c ObstacleListener implemented as BridgeObstaleListener
 * - @c PIRListener implemented as BridgePassiveIRListener
 * - @c InfrareListener implemented as BridgeActiveIRListener
 * - @c VoiceLocateListener implemented as BridgeVoiceLocateListener
 *
 * Proof-of-concept Listeners for handling @c HardwareManager API events are implemented but since
 * they do not actually receive events they may not be supported by the Sanbot S1-B2 robot type:
 *
 * - @c GravityDataListener implemented as BridgeGravityDataListener
 * - @c ChargeStatusListener implemented as BridgeChargeStatusListener
 *
 * Additional proof-of-concept events listeners are implemented to handle @c SystemManager events,
 * but again none of these receive any events:
 *
 * - @c ObstacleStatusListener implemented as BridgeSystemObstacleListener
 * - @c WheelObstacleStatusListener implemented as BridgeWheelObstacleListener
 * - @c KeyStatusListener implemented as BridgeKeyStatusListener
 *
 * The proof-of-concept listeners are only enabled for the debug build of the application.
 *
 * The following sensors are confirmed not to be supported by the Sanbot S1-B2 robot type:
 *
 * - @c UltrasonicListener
 *
 * Upon initialization of the unit event listeners marked as enabled in the bridge configuration are
 * registered. Since the Sanbot SDK does not allow a registered listener to be unregistered, once
 * registered it remains registered. If at run-time the listener is disabled, the event handler will
 * still be invoked but will simply ignore the received events.
 *
 * @version     1.0.001
 * @date        21 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 *
 * @todo    21/09/2026 - Check PO sensors, remove if not supported.
 */
public final class SanbotSensorUnit extends BridgeUnit
{
    /** Source label used for log messages. */
    private static final String TAG = SanbotSensorUnit.class.getSimpleName();

    /** List of supported sensors or group of sensors */
    public static final String[] SENSOR_NAMES = {
        BridgeProtocol.SENSOR_TOUCH,
        BridgeProtocol.SENSOR_OBSTACLE,
        BridgeProtocol.SENSOR_PASSIVEIR,
        BridgeProtocol.SENSOR_ACTIVEIR,
        BridgeProtocol.SENSOR_VOICELOCATE,
        BridgeProtocol.SENSOR_ORIENTATION,
        "pocsensors"
    };

    /** Persistent bridge configuration object. */
    private final BridgeConfig config;

    /** Active Sanbot @c HardWareManager API instance. */
    private HardWareManager hardwareManager;

    /** Active Sanbot @c SpeechManager API instance. */
    private SystemManager systemManager;

    /** Touch sensor event listener. */
    private final BridgeTouchListener touchSensorListener = new BridgeTouchListener();

    /** Orientation sensor event listener. */
    private final BridgeOrientationListener orientationListener = new BridgeOrientationListener();

    /** Obstacle sensor event listener. */
    private final BridgeObstacleListener obstacleListener = new BridgeObstacleListener();

    /** Passive infrared (PIR) sensor event listener. */
    private final BridgePassiveIRListener passiveIRListener = new BridgePassiveIRListener();

    /** Active infrared (AIR) sensor event listener. */
    private final BridgeActiveIRListener activeIRListener = new BridgeActiveIRListener();

    /** Voice locate sensor event listener. */
    private final BridgeVoiceLocateListener voiceLocateListener = new BridgeVoiceLocateListener();

    /** POC: Gravity sensor event listener. */
    // private final BridgeGravityListener gravityListener = new BridgeGravityListener();

    /** POC: Charge sensor status event listener. */
    // private final BridgeChargeStatusListener chargeStatusListener = new BridgeChargeStatusListener();

    /** POC: System obstacle status sensor event listener. */
    // private final BridgeSystemObstacleListener systemObstacleListener = new BridgeSystemObstacleListener();

    /** POC: Wheel obstacle sensor event listener. */
    // private final BridgeWheelObstacleListener wheelObstacleListener = new BridgeWheelObstacleListener();

    /** POC: Key status sensor event listener. */
    // private final BridgeKeyStatusListener keyStatusListener = new BridgeKeyStatusListener();

    /** Flag specifying if touch sensor event listener is registered. */
    private volatile boolean touchListenerRegistered = false;

    /** Flag specifying if orientation sensor event listener is registered. */
    private volatile boolean orientationListenerRegistered = false;

    /** Flag specifying if obstacle sensor event listener is registered. */
    private volatile boolean obstacleListenerRegistered = false;

    /** Flag specifying if passive infrared sensor event listener is registered. */
    private volatile boolean pirListenerRegistered = false;

    /** Flag specifying if active infrared sensor event listener is registered. */
    private volatile boolean infraredListenerRegistered = false;

    /** Flag specifying if voice locate event listener is registered. */
    private volatile boolean voiceLocateListenerRegistered = false;

    /** POC: Flag specifying if proof-of-concept event listeners are registered. */
    private volatile boolean pocListenerRegistered = false;

    /** Helper responsible for infrared aggregation, sensitivity, and report generation. */
    private final InfraredData infraredData = new InfraredData();

    /** Helper responsible for orientation storage, sensitivity, and change calculation. */
    private final OrientationData orientationData = new OrientationData();

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new SanbotSensorUnit instance.
     *
     * The base class constructor is called to copy the callback host to a member variables, and
     * copies the persistent configuration to a member variable
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
    public SanbotSensorUnit(BridgeConfig config, BridgeEventHost eventHost)
    {
        super(eventHost);
        if (config == null) throw new IllegalArgumentException("bridge configuration object may not be null");
        this.config = config;
    }

    /**
     * @name Sensor Unit Operations
     *
     * @{ 
     */

    /**
     * Updates sensor parameters.
     *
     * @param   reset           if @c true, reset parameters to defaults
     * @param   reload          if @c true, reload parameters from configuration file
     * @param   save            if @c true, save updated parameters to configuration file
     * @param   params          Java @c map instance containing sensor parameters
     *
     * If @p reset or @p reload is @c true, the parameters for all sensors are either reset to their
     * default values or reload from the configuration file, ignoring all other parameters in the
     * @p params map. If @p reset is @c false but the @p params map contains an @c all item, all
     * sensor enabled states are set to the value of the @c enable parameter in that item if valid
     * and all other parameters are ignored. In all other cases the parameters in the @p params map
     * are applied to the specified (group of) sensors.
     *
     * @return  DataResult instance specifying operation result
     *
     * The @c result property in the operation result object contains the updated sensor
     * parameters,
     */
    public synchronized DataResult updateConfig(boolean reset, boolean reload, boolean save, Map<String, Object> params)
    {
        if (reset) resetSensorParams();
        else if (reload) reloadSensorParams();
        else
        {
            // Exit if no parameters are specified.
            if ((params == null) || (params.isEmpty())) return DataResult.failure(ERROR_INVALID_PARAMS, "no sensor settings specified");

            Object o = params.get(BridgeProtocol.SENSOR_ALL);
            if (o != null)
            {
                // Parameter all is specified. If valid, enable or disable all sensors.
                params.remove(BridgeProtocol.SENSOR_ALL);
                Map<String, Object> all = MapUtils.castObject(params.get(BridgeProtocol.SENSOR_ALL));
                if (all.isEmpty() == false)
                {
                    Boolean enable = ValueUtils.toBoolean(all.get("enable"));
                    if (enable != null) updateSensorStates(enable);
                    else return DataResult.failure(BridgeResult.Code.FAILURE, "invalid sensor state");
                }
            }
            else
            {
                // Parameter all not specified, apply parameters to specified (group of) sensors.
                updateSensorParams(params);
            }
        }

        // Save parameters if required.
        if ((save) && (config.save() == false)) return DataResult.failure("config_save_failed", config.getSensorParams());
        else return DataResult.success(config.getSensorParams());
    }

    /**
     * Returns cached orientation sensor data.
     *
     * @return  DataResult instance specifying operation result
     *
     * The @c result property in the operation result object contains the cached orientation data.
     */
    public synchronized DataResult getOrientationData()
    {
        Map<String, Object> data = orientationData.toMap();
        return (data != null) ? DataResult.success(data) : DataResult.notavailable("cached orientation data");
    }

    /**
     * Returns cached infrared sensor data.
     *
     * @return  DataResult instance specifying operation result
     *
     * The @c result property in the operation result object contains the cached infrared data.
     */
    public synchronized DataResult getInfraredData()
    {
        Map<String, Object> data = infraredData.toMap();
        return (data.isEmpty() == false) ? DataResult.success(data) : DataResult.notavailable("cached infrared data");
    }

    /** @} */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * The active hardware manager and speech manager are copied to memer variablss, the (groups of)
     * sensors are initialized, and the unit status is set.
     *
     * @param   hardwareManager active Sanbot @c HardWareManager API instance
     * @param   systemManager   active Sanbot @c SystemManager API instance
     */
    public synchronized void init(HardWareManager hardwareManager, SystemManager systemManager)
    {
        logStatus();

        this.hardwareManager = hardwareManager;
        this.systemManager = systemManager;
        resetRegistrationState();
        initSensors();

        // Set unit status.
        if (BuildConfig.EMULATOR_MODE) unitStatus = UnitStatus.EMULATED;
        else unitStatus = ((this.systemManager != null) && (this.hardwareManager != null)) ? UnitStatus.STARTED : UnitStatus.INITIALIZING;

        logStatus();
    }

    /**
     * Performs unit shutdown tasks.
     */
    @Override
    public synchronized void shutdown()
    {
        resetRegistrationState();
        systemManager = null;
        hardwareManager = null;
        unitStatus = UnitStatus.SHUTDOWN;
        logStatus();
    }

    /**
     * Initializes all (groups of) sensors.
     *
     * The initSensor() method is called for all (groups of) sensors
     */
    public synchronized void initSensors()
    {
        for (String name : SENSOR_NAMES) initSensor(name);
    }

    /**
     * Builds a data map containing current unit status data.
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    @Override
    public Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = super.buildStatusData();
        data.put("sensorParams", config.getSensorParams());
        Map<String, Object> listenerData = MapUtils.createMap();
        addListenerStatus(listenerData, BridgeProtocol.SENSOR_TOUCH, touchListenerRegistered);
        addListenerStatus(listenerData, BridgeProtocol.SENSOR_ORIENTATION, orientationListenerRegistered);
        addListenerStatus(listenerData, BridgeProtocol.SENSOR_OBSTACLE, obstacleListenerRegistered);
        addListenerStatus(listenerData, BridgeProtocol.SENSOR_PASSIVEIR, pirListenerRegistered);
        addListenerStatus(listenerData, BridgeProtocol.SENSOR_ACTIVEIR, infraredListenerRegistered);
        addListenerStatus(listenerData, BridgeProtocol.SENSOR_VOICELOCATE, voiceLocateListenerRegistered);
        data.put("listeners", listenerData);
        return data;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Sets updated sensor parameters.
     *
     * The persistent configuration is updated, and initSensor() is called to re-initialize the
     * sensors.
     *
     * @param   params          Java @c Map instance containing sensor parameters
     */
    private void updateSensorParams(@NonNull Map<String, Object> params)
    {
        for (Map.Entry<String, Object> entry : params.entrySet())
        {
            String name = entry.getKey();
            Map<String, Object> sensorParams = MapUtils.castObject(entry.getValue());
            if ((sensorParams.isEmpty() == false) && (config.setSensorParams(name, sensorParams)))
            {
                initSensor(name);
            }
        }
    }

    /**
     * Resets all sensor parameters to their default values.
     *
     * All sensor parameters in the persistent configuration are reset to their default values and
     * initSensor() is called to re-initialize the sensors.
     */
    private void resetSensorParams()
    {
        for (Map.Entry<String, Object> entry : config.getSensorParams().entrySet())
        {
            String name = entry.getKey();
            if (config.resetSensorParams(name)) initSensor(name);
        }
    }

    /**
     * Reloads all sensor parameters from configuration file.
     *
     * All sensor parameters in the persistent configuration are reloaded from the configuration
     * file and initSensor() is called to re-initialize the sensors.
     */
    private void reloadSensorParams()
    {
        for (Map.Entry<String, Object> entry : config.getSensorParams().entrySet())
        {
            String name = entry.getKey();
            if (config.reloadSensorParams(name)) initSensor(name);
        }
    }

    /**
     * Updates sensor enabled states.
     *
     * For each sensor the current enabled state is compared to the state specified in the
     * persistent configuration. If different, the enabled state is updated in the configuration
     * and initSensor() is called to re-initialize the sensors.
     *
     * @param   enable          id @c true (@c false) the sensor is enabled (disabled)
     */
    private void updateSensorStates(boolean enable)
    {
        for (Map.Entry<String, Object> entry : config.getSensorParams().entrySet())
        {
            String name = entry.getKey();
            Map<String, Object> sensorParams = MapUtils.castObject(entry.getValue());
            Boolean current = ValueUtils.toBoolean(sensorParams.get("enable"));
            if ((current != null) && (current != enable) && (config.setSensorParams(name, sensorParams)))
            {
                initSensor(name);
            }
        }
        initPOCSensor(new LinkedHashMap<>());
    }

    /**
     * Initializes (or re-initialize) the sensor with the specified sensor.
     *
     * This is just a dispatcher that calls the method matching the sensor name.
     *
     * @param   name            name of sensor or group of sensors
     */
    private synchronized void initSensor(String name)
    {
        Map<String, Object> sensorParams = config.getSensorParams(name);
        switch (name)
        {
            case BridgeProtocol.SENSOR_TOUCH:
                initTouchSensor(sensorParams);
                break;
            case BridgeProtocol.SENSOR_ORIENTATION:
                initOrientationSensor(sensorParams);
                break;
            case BridgeProtocol.SENSOR_OBSTACLE:
                initObstacleSensor(sensorParams);
                break;
            case BridgeProtocol.SENSOR_PASSIVEIR:
                initPIRSensor(sensorParams);
                break;
            case BridgeProtocol.SENSOR_ACTIVEIR:
                initInfraredSensor(sensorParams);
                break;
            case BridgeProtocol.SENSOR_VOICELOCATE:
                initVoiceLocateSensor(sensorParams);
                break;
            case "pocsensors":
                initPOCSensor(sensorParams);
                break;
            default:
                BridgeLog.info(TAG, "invalid sensor name " + name + " ignored");
        }
    }

    /**
     * Initialize (or re-initialize) touch sensor event listener.
     *
     * @param   params          Java @c Map instance containing sensor parameters
     *
     * No parameters are supported for the touch sensor.
     */
    private void initTouchSensor(Map<String, Object> params)
    {
        // Set event listener if the sensor is enabled and not already registered.
        if ((config.isSensorEnabled(BridgeProtocol.SENSOR_TOUCH) == false)
            || (touchListenerRegistered) || (hardwareManager == null)) return;
        BridgeLog.debug(TAG, "Add event listener for touch sensor");
        hardwareManager.setOnHareWareListener(touchSensorListener);
        touchListenerRegistered = true;
    }

    /**
     * Initialize (or re-initialize) orientation sensor event listener.
     *
     * @param   params          Java @c Map instance containing sensor parameters
     *
     * Supported orientation sensor parameters are:
     * - @c sensitivity - specifies how much the orientation values must have been changed to relay
     *                    an event to the bridge service
     */
    private void initOrientationSensor(@NonNull Map<String, Object> params)
    {
        // Set sensor parameters.
        Object orientationSensitivityValue = params.get("sensitivity");
        if (orientationSensitivityValue instanceof Number)
            orientationData.setSensitivity(((Number)orientationSensitivityValue).intValue());

        // Set event listener if the sensor is enabled and not already registered.
        if ((config.isSensorEnabled(BridgeProtocol.SENSOR_ORIENTATION) == false)
            || (orientationListenerRegistered) || (hardwareManager == null)) return;
        BridgeLog.debug(TAG, "Add event listener for orientation sensor");
        hardwareManager.setOnHareWareListener(orientationListener);
        orientationListenerRegistered = true;
    }

    /**
     * Initialize (or re-initialize) obstacle sensor event listener.
     *
     * @param   params          Java @c Map instance containing sensor parameters
     *
     * No parameters are supported for the obstacle sensor.
     */
    private void initObstacleSensor(Map<String, Object> params)
    {
        // Set event listener if the sensor is enabled and not already registered.
        if ((config.isSensorEnabled(BridgeProtocol.SENSOR_OBSTACLE) == false)
            || (obstacleListenerRegistered) || (hardwareManager == null)) return;
        BridgeLog.debug(TAG, "Add event listener for obstacle sensor");
        hardwareManager.setOnHareWareListener(obstacleListener);
        obstacleListenerRegistered = true;
    }

    /**
     * Initialize (or re-initialize) passive infrared (PIR) sensor event listener.
     *
     * @param   params          Java @c Map instance containing sensor parameters
     *
     * No parameters are supported for the passive infrared sensor.
     */
    private void initPIRSensor(Map<String, Object> params)
    {
        // Set event listener if the sensor is enabled and not already registered.
        if ((config.isSensorEnabled(BridgeProtocol.SENSOR_PASSIVEIR) == false)
            || (pirListenerRegistered) || (hardwareManager == null)) return;
        BridgeLog.debug(TAG, "Add event listener for passive infrared (PIR) sensors");
        hardwareManager.setOnHareWareListener(passiveIRListener);
        pirListenerRegistered = true;
    }

    /**
     * Initialize (or re-initialize) active infrared (AIR) sensor event listener.
     *
     * @param   params          Java @c Map instance containing sensor parameters
     *
     * Supported active infrared sensor parameters are:
     * - @c update - Specifies the minimum time between relaying events to the bridge service.
     * - @c sensitivity - Specifies how much the orientation values must have been changed to relay
     *                    an event to the bridge service.
     */
    private void initInfraredSensor(@NonNull Map<String, Object> params)
    {
        // Set sensor parameters.
        Object updateValue = params.get("update");
        if (updateValue instanceof Number) infraredData.setUpdateInterval(((Number)updateValue).intValue());
        Object sensitivityValue = params.get("sensitivity");
        if (sensitivityValue instanceof Number) infraredData.setSensitivity(((Number)sensitivityValue).intValue());

        // Set event listener if the sensor is enabled and not already registered.
        if ((config.isSensorEnabled(BridgeProtocol.SENSOR_ACTIVEIR) == false)
            || (infraredListenerRegistered) || (hardwareManager == null)) return;
        BridgeLog.debug(TAG, "Add event listener for active infrared (AIR) sensors");
        hardwareManager.setOnHareWareListener(activeIRListener);
        infraredListenerRegistered = true;
    }

    /**
     * Initialize (or re-initialize) voice locate sensor event listener.
     *
     * @param   params          Java @c Map instance containing sensor parameters
     *
     * No parameters are supported for the voice locate sensor.
     */
    private void initVoiceLocateSensor(Map<String, Object> params)
    {
        // Set event listener if the sensor is enabled and not already registered.
        if ((config.isSensorEnabled(BridgeProtocol.SENSOR_VOICELOCATE) == false)
            || (voiceLocateListenerRegistered) || (hardwareManager == null)) return;
        BridgeLog.debug(TAG, "Add event listener for voice locate sensor");
        hardwareManager.setOnHareWareListener(voiceLocateListener);
        voiceLocateListenerRegistered = true;
    }

    /**
     * Initialize (or re-initialize) proof-of-concept sensor event listeners.
     *
     * @param   params          Java @c Map instance containing sensor parameters (unused)
     *
     * No parameters are supported for the proof-of-concept sensors.
     */
    private void initPOCSensor(Map<String, Object> params)
    {
        // Set event listener if the sensor is enabled and not already registered.
        if ((BuildConfig.DEBUG == false) || (pocListenerRegistered) || (systemManager == null)) return;
        BridgeLog.debug(TAG, "Add event listener for proof-of-concept sensors");
    //    hardwareManager.setOnHareWareListener(chargeStatusListener);
    //    hardwareManager.setOnHareWareListener(gravityListener);
    //    systemManager.setOnObstacleStatusListener(systemObstacleListener);
    //    systemManager.setOnWheelObstacleStatusListener(wheelObstacleListener);
    //    systemManager.setKeyStatusListener(keyStatusListener);

        pocListenerRegistered = true;
    }

    /**
     * Adds the status of the even listener for the specified event name to the data map.
     *
     * The event listener status consists of the registration status and the enabled status. Once
     * registered, there is no way to unregister a listener. However, the listener can be disabled,
     * i.e. the event handler is still called but ignores the event.
     *
     * @param   data            data map to add listener status to
     * @param   name            name of sensor or group of sensors
     * @param   isRegistered    @c true (@c false) if listener is registered (unregistered)
     */
    private void addListenerStatus(Map<String, Object> data, String name, boolean isRegistered)
    {
        String status = (isRegistered) ? "registered" : "not registered";
        if (isRegistered) status += "," + ((config.isSensorEnabled(name)) ? "enabled" : "disabled");
        data.put(name, status);
    }

    /**
     * Resets all listener registration flags.
     */
    private void resetRegistrationState()
    {
        touchListenerRegistered = false;
        orientationListenerRegistered = false;
        obstacleListenerRegistered = false;
        pirListenerRegistered = false;
        infraredListenerRegistered = false;
        voiceLocateListenerRegistered = false;
        pocListenerRegistered = false;
        orientationData.reset();
        infraredData.reset();
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Receives touch sensor callback invocations.
     */
    private final class BridgeTouchListener implements TouchSensorListener
    {
        /**
         * Called if one of the touch sensor triggers an event.
         *
         * The onTouch(int, boolean) method is called to relay the event to the bridge service.
         *
         * @param   sensorId        id of touch sensor that triggered the event
         */
        @Override
        public void onTouch(int sensorId)
        {
            BridgeLog.apievent(TAG, "SanbotSDK", "TouchSensorListener", "onTouch", sensorId);
            if (config.isSensorEnabled(BridgeProtocol.SENSOR_TOUCH) == false) return;

            onTouch(sensorId, true);
        }

        /**
         * Called if one of the touch sensor triggers an event.
         *
         * The human-readable sensor name matching the sensor id is retrieved. An event containing
         * the original event data as well as the sensor name is relayed to the bridge service.
         *
         * @param   sensorId        id of touch sensor that triggered the event
         * @param   touched         @c true if sensor is touched, @c false if is released
         *
         */
        @Override
        public void onTouch(int sensorId, boolean touched)
        {
            BridgeLog.apievent("SanbotSDK", "TouchSensorListener", "onTouch", sensorId, touched);
            if (config.isSensorEnabled(BridgeProtocol.SENSOR_TOUCH) == false) return;

            // Create and relay event.
            String name = SanbotMappings.toTouchSensor(sensorId);
            Map<String, Object> data = MapUtils.createMap("sensorId", sensorId, "sensorName", name, "touched", touched);
            publishEvent(BridgeProtocol.MODULE_SENSOR, BridgeProtocol.SENSOR_TOUCH + "_" +
                (touched ? BridgeProtocol.EVENT_PRESSED : BridgeProtocol.EVENT_RELEASED), data);
        }
    }

    /**
     * Receives orientation sensor callback invocations.
     */
    private final class BridgeOrientationListener implements GyroscopeListener
    {
        /**
         * Called if orientation check result is received.
         *
         * This event is ignored.
         *
         * @param   sensorOk        @c true if the auxiliary sensor check passed
         * @param   compassOk       @c true if compass is ok
         */
        @Override
        public void gyroscopeCheckResult(boolean sensorOk, boolean compassOk)
        {
            BridgeLog.apievent("SanbotSDK", "GyroscopeListener", "gyroscopeCheckResult", sensorOk, compassOk);
        }

        /**
         * Called if orientation data is received.
         *
         * The OrientationData.update() function is called to update the orientation data with the
         * values from the event data. If that method returns a non-null result, an event containing
         * the updated data is relayed to the bridge service.
         *
         * @param   yaw         angle of rotation around vertical axis (cardinal direction)
         * @param   pitch       angle of up-down rotation (tilt)
         * @param   roll        angle of left-right rotation (bank)
         */
        @Override
        public void gyroscopeData(float yaw, float pitch, float roll)
        {
            BridgeLog.apievent("SanbotSDK", "GyroscopeListener", "gyroscopeData", yaw, pitch, roll);
            if (config.isSensorEnabled(BridgeProtocol.SENSOR_ORIENTATION) == false) return;

            // Update data, and create and relay event if required.
            Map<String, Object> data = orientationData.update(yaw, pitch, roll);
            if (data != null) publishEvent(BridgeProtocol.MODULE_SENSOR, BridgeProtocol.SENSOR_ORIENTATION, data);
        }
    }

    /**
     * Handles obstacle sensor events.
     */
    private final class BridgeObstacleListener implements ObstacleListener
    {
        /**
         * Called if the obstacle sensor triggers an event.
         *
         * An event containing the original event data is relayed to the bridge service.
         *
         * @param   blocked         @c true if blocked by obstacle, @c false if not longer blocked
         *
         */
        @Override
        public void onObstacleStatus(boolean blocked)
        {
            BridgeLog.apievent("SanbotSDK", "ObstacleListener", "onObstacleStatus", blocked);
            if (config.isSensorEnabled(BridgeProtocol.SENSOR_OBSTACLE) == false) return;

            // Create and relay event.
            publishEvent(BridgeProtocol.MODULE_SENSOR, BridgeProtocol.SENSOR_OBSTACLE + "_" +
                ((blocked) ? BridgeProtocol.EVENT_DETECTED : BridgeProtocol.EVENT_CLEARED));
        }
    }

    /**
     * Receives passive infrared (PIR) sensor callback invocations.
     */
    private final class BridgePassiveIRListener implements PIRListener
    {
        /**
         * Called if one of passive infrared (PIR) sensors triggers an event.
         *
         * The human-readable sensor name matching the sensor id is retrieved. An event containing
         * the original event data as well as the sensor name is relayed to the bridge service.
         *
         * @param   detected        @c true if motion is detected, @c false if motion has stopped
         * @param   sensorId        id of PIR sensor that triggered the event
         */
        @Override
        public void onPIRCheckResult(boolean detected, int sensorId)
        {
            BridgeLog.apievent("SanbotSDK", "ObstacleListener", "onPIRCheckResult", detected, sensorId);
            if (config.isSensorEnabled(BridgeProtocol.SENSOR_PASSIVEIR) == false) return;

            // Create and relay event.
            String name = SanbotMappings.toPIRSensor(sensorId);
            Map<String, Object> data = MapUtils.createMap("sensorId", sensorId, "sensorName", name, "detected", detected);
            publishEvent(BridgeProtocol.MODULE_SENSOR, BridgeProtocol.SENSOR_PASSIVEIR + "_" +
                ((detected) ? BridgeProtocol.EVENT_DETECTED : BridgeProtocol.EVENT_CLEARED), data);
        }
    }

    /**
     * Receives infrared sensor callback invocations.
     */
    private final class BridgeActiveIRListener implements InfrareListener
    {
        /**
         * Called if one of active infrared (AIR) sensors triggers an event.
         *
         * The InfraredData.update() function is called to update the infrared data with the value
         * from the event data. If that method returns a non-null result, an event containing the
         * updated data is relayed to the bridge service.
         *
         * @param   sensorId        id of infrared sensor that triggered the event
         * @param   distance        distance to detected object
         */
        @Override
        public void infrareDistance(int sensorId, int distance)
        {
            BridgeLog.apievent("SanbotSDK", "ObstacleListener", "infrareDistance", sensorId, distance);
            if (config.isSensorEnabled(BridgeProtocol.SENSOR_ACTIVEIR) == false) return;

            // Update data, and create and relay event if required.
            Map<String, Object> data = infraredData.update(sensorId, distance);
            if (data != null) publishEvent(BridgeProtocol.MODULE_SENSOR, BridgeProtocol.SENSOR_ACTIVEIR, data);
        }
    }

    /*
     * Receives voice location callback invocations.
     */
    private final class BridgeVoiceLocateListener implements VoiceLocateListener
    {
        /**
         * Called if voice locate triggers an event.
         *
         * The event is relayed to the bridge service.
         *
         * @param   angle           angle from which voice is heard
         */
        @Override
        public void voiceLocateResult(int angle)
        {
            BridgeLog.apievent("SanbotSDK", "VoiceLocateListener", "voiceLocateResult", angle);
            if (config.isSensorEnabled(BridgeProtocol.SENSOR_VOICELOCATE) == false) return;

            // Create and relay event.
            Map<String, Object> data = MapUtils.createMap("angle", angle);
            publishEvent(BridgeProtocol.MODULE_SENSOR, BridgeProtocol.SENSOR_VOICELOCATE, data);
        }
    }

    /*
     * POC: Receives charge status callback invocations.
     *
    private static class BridgeChargeStatusListener implements ChargeStatusListener
    {
         *
         * Called if charge status event is received.
         *
         *
         * @param   i1              to be determined
         * @param   i2              to be determined
         *
        @Override
        public void onChargeStatus(int i1, int i2)
        {
            BridgeLog.apievent("SanbotSDK", "ChargeStatusListener", "onChargeStatus", i1, i2);
        }
    }*/

    /*
     * POC: Receives gravity callback invocations.
     *
    private static class BridgeGravityListener implements GravityDataListener
    {
         *
         * Called if gravity event is received.
         *
         * @param   v           to be determined
         *
        @Override
        public void onGravityDataResult(float v)
        {
            BridgeLog.apievent("SanbotSDK", "GravityDataListener", "onGravityDataResult", v);
        }
    }*/

    /*
     * POC: Receives obstacle callback invocations.
     *
    private static class BridgeSystemObstacleListener implements ObstacleStatusListener
    {

         *
         * Called if obstacle status changes.
         *
         * @param   i1              to be determined
         * @param   i2              to be determined
         *
        @Override
        public void onObstacleStatus(int i1, int i2)
        {
            BridgeLog.apievent("SanbotSDK", "ObstacleStatusListener", "onObstacleStatus", i1, i2);
        }
    }*/

    /*
     * POC: Receives wheel obstacle callback invocations.
     *
    private static class BridgeWheelObstacleListener implements WheelObstacleStatusListener
    {

         *
         * Called if wheel obstacle status changes.
         *
         * @param   i1              to be determined
         * @param   i2              to be determined
         *
        @Override
        public void onWheelObstacleStatus(int i1, int i2)
        {
            BridgeLog.apievent("SanbotSDK", "WheelObstacleStatusListener", "onWheelObstacleStatus", i1, i2);
        }
    }*/

    /*
     * POC: Receives key status callback invocations.
     *
    private static class BridgeKeyStatusListener implements KeyStatusListener
    {
         *
         * Called if key status changes.
         *
         * @param   code        key code
         * @param   status      key status
         *
        @Override
        public void onKeyStatus(int code, String status)
        {
            BridgeLog.apievent("SanbotSDK", "KeyStatusListener", "onKeyStatus", code, status);
        }
    }*/
}
