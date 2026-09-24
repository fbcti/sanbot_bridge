/**
 * @file        BridgeService.java
 * @brief       Implements sanbot.bridge.BridgeService class.
 */
package com.fbcti.sanbot.bridge.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.R;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.camera.AndroidCameraManager;
import com.fbcti.sanbot.bridge.robot.mapping.SanbotMappings;
import com.fbcti.sanbot.bridge.robot.camera.OrbbecCameraManager;
import com.fbcti.sanbot.bridge.robot.camera.SanbotCameraManager;
import com.fbcti.sanbot.bridge.robot.unit.AndroidCameraUnit;
import com.fbcti.sanbot.bridge.robot.unit.AndroidSystemUnit;
import com.fbcti.sanbot.bridge.robot.unit.AndroidTtsUnit;
import com.fbcti.sanbot.bridge.robot.unit.BridgeAudioUnit;
import com.fbcti.sanbot.bridge.robot.unit.BridgeCameraUnit;
import com.fbcti.sanbot.bridge.robot.unit.BridgeTtsUnit;
import com.fbcti.sanbot.bridge.robot.unit.BridgeUnit;
import com.fbcti.sanbot.bridge.robot.unit.OrbbecCameraUnit;
import com.fbcti.sanbot.bridge.robot.unit.SanbotAsrUnit;
import com.fbcti.sanbot.bridge.robot.unit.SanbotCameraUnit;
import com.fbcti.sanbot.bridge.robot.unit.SanbotFaceUnit;
import com.fbcti.sanbot.bridge.robot.unit.SanbotLedUnit;
import com.fbcti.sanbot.bridge.robot.unit.SanbotMotionUnit;
import com.fbcti.sanbot.bridge.robot.unit.SanbotSensorUnit;
import com.fbcti.sanbot.bridge.robot.unit.SanbotTtsUnit;
import com.fbcti.sanbot.bridge.transport.BridgeEvent;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.transport.BridgeRequest;
import com.fbcti.sanbot.bridge.transport.BridgeTransport;
import com.fbcti.sanbot.bridge.transport.HttpServer;
import com.fbcti.sanbot.bridge.transport.JsonResponse;
import com.fbcti.sanbot.bridge.transport.MediaResponse;
import com.fbcti.sanbot.bridge.transport.RestTransport;
import com.fbcti.sanbot.bridge.transport.WebSocketTransport;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.FileUtils;
import com.fbcti.sanbot.bridge.util.JsonUtils;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.google.gson.JsonObject;
import com.sanbot.opensdk.base.BindBaseService;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.ModularMotionManager;
import com.sanbot.opensdk.function.unit.SystemManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;
import com.sanbot.opensdk.function.unit.WingMotionManager;
import com.sanbot.opensdk.function.unit.ZigbeeManager;
import com.sanbot.opensdk.function.unit.interfaces.IDarlingListener;
import com.sanbot.opensdk.setting.Setting;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controls Sanbot robot using REST and WebSocket clients.
 *
 * This class extends the Android @c BindBaseService class and provides the bridge service that
 * allows remote control of the robot using REST and WebSocket clients. It manages the lifecycle of
 * the bridge application, creates and initialized the HTTP server, keeps the main activity window
 * visible, and owns the traffic log.
 *
 * @version     1.0.001
 * @date        21 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public class BridgeService extends BindBaseService
{
    /** Source label for system log messages. */
    private static final String TAG = "BridgeService";

    /** Path used for WebSocket upgrade requests. */
    private static final String WEBSOCKET_PATH = "/ws";

    /**
     * Maximum number of log lines to keep in memory.
     *
     * This should at least match the number of lines that can be displayed in the log control in
     * the main screen of the application.
     */
    private static final int MAX_REQUEST_LOG_LINES = 50;

    /** Maximum number of characters to store for one traffic log message. */
    private static final int MAX_TRAFFIC_LOG_MESSAGE_LENGTH = 132;

    /** Date format for traffic log messages. */
    private final SimpleDateFormat TRAFFIC_LOG_TIMEFORMAT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    /** Time to wait for BridgeMainActivity to become available for service-owned UI requests. */
    private static final long BRIDGE_MAIN_ACTIVITY_WAIT_MS = 3000L;

    /** Poll interval while waiting for BridgeMainActivity to become available. */
    private static final long BRIDGE_MAIN_ACTIVITY_POLL_MS = 100L;

    /** Time for BridgeMainActivity to wait for the SpeechManager. */
    private static final long BRIDGE_MAIN_ACTIVITY_SPEECH_WAIT_MS = 3000L;

    /** Poll interval for speech events. */
    private static final long BRIDGE_MAIN_ACTIVITY_SPEECH_POLL_MS = 100L;

    /** Delay before starting a new service instance after a restart request. */
    private static final long SERVICE_RESTART_DELAY_MS = 250L;

    /** External storage directory containing script files. */
    private static final String SCRIPT_DIRECTORY = FileUtils.BRIDGE_DATA_DIRECTORY + "/scripts";

    /** Script file extension, */
    private static final String SCRIPT_EXTENSION = "scr";

    /** Thread-save singleton instance of this class. */
    private static volatile BridgeService instance;

    /** Instance of class managing bridge settings. */
    private final BridgeConfig config = new BridgeConfig(this);

    /** Thread-save string specifying bridge service IP address. */
    private volatile String bridgeIpAddress = "0.0.0.0";

    /** Thread-save integer specifying bridge service IP port. */
    private volatile Integer bridgeIpPort = 0;

    /** HTTP server that handles REST requests and WebSocket upgrades. */
    private HttpServer httpServer;

    /** REST transport. */
    private final RestTransport restTransport = new RestTransport(new RestTransportHost());

    /** WebSocket transport. */
    private final WebSocketTransport webSocketTransport = new WebSocketTransport(new WebSocketTransportHost());

    /** Bridge protocol message router. */
    private final BridgeRequestHandler bridgeRequestHandler = new BridgeRequestHandler(this);

    /** Bridge script runner. */
    private BridgeScriptRunner scriptRunner = null;

    /** Sanbot system manager. */
    private SystemManager systemManager;

    /** Sanbot hardware manager. */
    private HardWareManager hardwareManager;

    /** Modular motion manager. */
    private ModularMotionManager modularMotionManager;

    /** Sanbot Zigbee manager. */
    private ZigbeeManager zigbeeManager;

    /** Android camera manager. */
    private AndroidCameraManager androidCameraManager;

    /** Orbbec camera manager. */
    private OrbbecCameraManager orbbecCameraManager;

    /** Sanbot camera manager. */
    private SanbotCameraManager sanbotCameraManager;

    /** Instance of class managing Android system operations. */
    private AndroidSystemUnit androidSystemUnit;

    /** Instance of class managing Sanbot SDK head, wing and wheel operations. */
    private SanbotMotionUnit sanbotMotionUnit;

    /** Instance of class managing Sanbot SDK LEDs and head light operations. */
    private SanbotLedUnit sanbotLedUnit;

    /** Instance of class managing Sanbot SDK face emotion operations. */
    private SanbotFaceUnit sanbotFaceUnit;

    /** Instance of class for managing Sanbot SDK sensor events. */
    private SanbotSensorUnit sanbotSensorUnit;

    /** Instance of class managing Android camera operations. */
    private AndroidCameraUnit androidCameraUnit;

    /** Instance of class managing the Orbbec 3D camera operations. */
    private OrbbecCameraUnit orbbecCameraUnit;

    /** Instance of class managing Sanbot HD camera operations. */
    private SanbotCameraUnit sanbotCameraUnit;

    /** Instance of class managing audio operations. */
    private BridgeAudioUnit bridgeAudioUnit;

    /** Instance of class managing text-to-speech operations. */
    private BridgeTtsUnit bridgeTtsUnit;

    /** Instance of class managing Sanbot SDK speech recognition operations. */
    private SanbotAsrUnit sanbotAsrUnit;

    /**
     * @name    Event Listeners / Callback Hosts
     * @{
     */

    /** Listener for network connectivity events. */
    private final BroadcastReceiver connectivityEventListener = new ConnectivityEventListener();

    /** Listener for home alarm events. */
    private final IDarlingListener homeAlarmEventListener = new HomeAlarmEventListener();

    /**
     * No-op listener used when home alarm detection is disabled.
     *
     * There is not method that unregisters a home alarm event listener from the Sanbot
     * @c SystemManager, so home alarm detection is disabled by replacing the actual listener by
     * this dummy.
     */
    private final IDarlingListener disabledHomeAlarmEventListener = new DisabledHomeAlarmEventListener();

    /** Executor for callback methods invoked by HttpServer instance.  */
    private final HttpServer.Host httpServerHost = new HttpServerHost();

    /** Executor for callback methods invoked by AndroidSystemUnit instance.  */
    private final AndroidSystemUnit.ScreenHost androidScreenHost = new AndroidScreenHost();

    /** Executor for callback methods invoked by BridgeScriptRunner instance.  */
    private final BridgeScriptRunner.RequestHost scriptRequestHost = new ScriptRequestHost();

    /** Executor for callback methods requesting an event to be published. */
    private final BridgeEventHost bridgeEventHost = new EventHost();

    /**
     * @}
     */

    /** Lock used to synchronize calls to request handler methods. */
    private final Object requestLock = new Object();

    /** Lock used to synchronize calls to traffic log methods. */
    private final Object logLock = new Object();

    /** Thread-save flag specifying if service is running.  */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Thread-save flag specifying if connectivity receiver is registered.  */
    private final AtomicBoolean receiverRegistered = new AtomicBoolean(false);

    /** Double ended queue for storing traffic log messages. */
    private final Deque<String> trafficLog = new ArrayDeque<>();

    /** Current bridge status. */
    private volatile String bridgeStatus = "starting";

    /** Thread-save flag specifying if a bridge activity window is currently visible. */
    private volatile boolean activityVisible;

    /** Thread-save flag specifying if bridge main activity is must remain visible. */
    private volatile boolean keepActivityVisible;

    /** Thread-save value specifying time it took to launch the activity. */
    private volatile long lastActivityLaunchAttemptMs;

    /** Thread-save flag specifying if service must be restarted after it is stopped. */
    private volatile boolean restartAfterDestroy;

    /** Executor for scheduled tasks. */
    private ScheduledExecutorService monitorExecutor;

    /**
     * @name    Service Event Handlers
     *
     * These event handlers override the methods implemented by the Sanbot SDK @c BindBaseService
     * class.
     * @{
     */

    /**
     * Called when the bridge service is created.
     *
     * The bridge configuration is loaded from the configuration file.  If the application runs in
     * true robot mode the service is registered with the Sanbot SDK. The method that handles the
     * event that signals registration is complete is responsible to complete the activation
     * process.
     */
    @Override
    public void onCreate()
    {
        // Set service state.
        BridgeLog.info(TAG, "Starting bridge background service");
        instance = this;
        running.set(true);

        // Load bridge configuration.
        config.load();
        restartAfterDestroy = false;

        if (BuildConfig.EMULATOR_MODE) activateEmulatorRuntime();
        else
        {
            // Bind the service to the Sanbot SDK. if successful, the onMainServiceConnected() event
            // handler will complete the activation process.
            register(BridgeService.class);
            super.onCreate();
        }

        // Register connectivity receiver.
        registerConnectivityEventListener();

        // Starts health monitor.
        monitorExecutor = Executors.newSingleThreadScheduledExecutor();
        startHealthMonitor();
    }

    /**
     * Called when the bridge service is destroyed.
     *
     * The bridge units and camera units are shut down, WebSocket sessions are closed and the HTTP
     * server is stopped before calling the base class @c %onDestroy() method. If required, the
     * service is restarted after being destroyed.
     */
    @Override
    public void onDestroy()
    {
        // Stop the script runner if currently active.
        stopScript();

        // Set service state.
        BridgeLog.info(TAG, "Stopping bridge background service");
        instance = null;
        running.set(false);
        keepActivityVisible = false;
        activityVisible = false;

        // Unregister connectivity receiver.
        unregisterConnectivityEventListener();

        // Shut down bridge units.
        shutdownBridgeUnit(androidSystemUnit);
        androidSystemUnit = null;
        shutdownBridgeUnit(sanbotMotionUnit);
        sanbotMotionUnit = null;
        shutdownBridgeUnit(sanbotLedUnit);
        sanbotLedUnit = null;
        shutdownBridgeUnit(sanbotFaceUnit);
        sanbotFaceUnit = null;
        shutdownBridgeUnit(sanbotSensorUnit);
        sanbotSensorUnit = null;
        shutdownBridgeUnit(androidCameraUnit);
        androidCameraUnit = null;
        shutdownBridgeUnit(orbbecCameraUnit);
        orbbecCameraUnit = null;
        shutdownBridgeUnit(sanbotCameraUnit);
        sanbotCameraUnit = null;
        shutdownBridgeUnit(bridgeAudioUnit);
        bridgeAudioUnit = null;
        shutdownBridgeUnit(bridgeTtsUnit);
        bridgeTtsUnit = null;
        shutdownBridgeUnit(sanbotAsrUnit);
        sanbotAsrUnit = null;

        // Shut down camera managers.
        if (androidCameraManager != null) androidCameraManager.shutdown();
        androidCameraManager = null;
        if (sanbotCameraManager != null) sanbotCameraManager.shutdown();
        sanbotCameraManager = null;
        if (orbbecCameraManager != null) orbbecCameraManager.shutdown();
        orbbecCameraManager = null;

        // Release home alarm listener.
        enableHomeAlarmDetection(false);

        // Close WebsocketSessions and stop HTTP server.
        webSocketTransport.closeAllSessions(true);
        stopHttpServer();

        // Shut down health monitor executor.
        if (monitorExecutor != null)
        {
            monitorExecutor.shutdownNow();
            monitorExecutor = null;
        }

        // Call super class method only if running in true robot mode.
        if (BuildConfig.EMULATOR_MODE == false) super.onDestroy();

        if (restartAfterDestroy)
        {
            final Context context = getApplicationContext();
            BridgeLog.info(TAG, "Bridge background service restart scheduled");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    BridgeLog.info(TAG, "Restarting bridge background service");
                    context.startService(new Intent(context, BridgeService.class));
                }
            }, SERVICE_RESTART_DELAY_MS);
        }
    }

    /**
     * Called when the bridge service is successfully bound to Sanbot SDK.
     *
     * If not running in emulator mode the activateRobotRuntime() method is called to complete the
     * activation process.
     */
    @Override
    protected void onMainServiceConnected()
    {
        // This event handler shouldn't be called in emulator mode but just to be safe.
        if (BuildConfig.EMULATOR_MODE)
        {
            BridgeLog.warning(TAG, "Ignoring unexpected SDK callback in emulator mode");
            return;
        }

        activateRobotRuntime();
    }

    /** @} */

    /**
     * @name Bridge Request Handlers
     *
     * Most bridge requests are handled by one of the BridgeUnit classes, but a number of generic
     * requests are handled by the bridge service itself. All of them return a Sanbot @c DataResult
     * instance that contains a numerical error code (1 on success, negative value on failure), a
     * short description of the result, and optional additional result data. The result data is
     * always a string, but may represent an integer, boolean or Json object.
     * @{
     */

    /**
     * Returns OpenAPI information.
     *
     * The buildOpenAPI() method is called to collect the requested OpenAPI data.
     *
     * @return  DataResult instance containing OpenAPI data
     *
     * The @c result property in the operation result object contains the collected information.
     */
    synchronized DataResult getOpenApiData()
    {
        return DataResult.success(buildOpenApiData());
    }

    /**
     * Returns bridge application status data.
     *
     * The methods matching the units in the specified list to collect the requested status data.
     *
     * @param   units           list of bridge unit names for which to return status data
     *
     * Supported values of @p unit are
     * - @c bridge
     * - @c system
     * - @c motion
     * - @c led
     * - @c face
     * - @c sensor
     * - @c camera
     * - @c audio
     * - @c tts
     * - @c asr
     *
     * If @p units is an empty list, the buildFullStatusData() method is called to collect and
     * return all available status data.
     *
     * @return  DataResult instance containing status data
     *
     * The @c result property in the operation result object contains the status data.
     */
    synchronized DataResult getStatusData(List<String> units)
    {
        if (units == null) units = new ArrayList<>();
        if (units.isEmpty())
        {
            units.add("bridge");
            units.add("system");
            units.add("motion");
            units.add("led");
            units.add("face");
            units.add("sensor");
            units.add("camera");
            units.add("audio");
            units.add("tts");
            units.add("asr");
        }
        return DataResult.execution(true, buildStatusData(units));
    }

    /**
     * Returns bridge application configuration data.
     *
     * @return  DataResult instance containing configuration data
     *
     * The @c result property in the operation result object contains the configuration data.
     */
    synchronized DataResult getConfigData()
    {
        String configJson = config.get();
        if (configJson != null) return DataResult.success(configJson);
        else return DataResult.notavailable("configuration file");
    }

    /**
     * Returns battery status data.
     *
     * @return  DataResult instance containing status data
     *
     * The @c result property in the operation result object contains the collected information.
     */
    synchronized DataResult getBatteryStatusData()
    {
        return DataResult.execution(true, buildBatteryStatusData());
    }

    /**
     * Returns the robot device id.
     *
     * The @c %getDeviceId() method implemented by the Sanbot @c %SystemManager API is called to
     * retrieve the device id.
     *
     * @return  DataResult instance containing robot device id
     *
     * The @c result property in the operation result object is a string specifying the device id.
     */
    synchronized DataResult getDeviceId()
    {
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated("emulator");
        if (systemManager == null) return DataResult.notavailable(FuncConstant.SYSTEM_MANAGER);

        // Call Sanbot SDK method.
        logCall("SystemManager.getDeviceId");
        return DataResult.execution(true, systemManager.getDeviceId());
    }

    /**
     * Returns the main service version.
     *
     * The @c %getMainServiceVersion() method implemented by the Sanbot %c SystemManager API is
     * called to retrieve the service version.
     *
     * @return  DataResult instance containing main service version
     *
     * The @c result property in the operation result object is a string specifying the service
     * version.
     */
    synchronized DataResult getMainServiceVersion()
    {
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated("emulator");
        if (systemManager == null) return DataResult.notavailable(FuncConstant.SYSTEM_MANAGER);

        // Call Sanbot SDK method.
        logCall("SystemManager.getMainServiceVersion");
        return DataResult.success(systemManager.getMainServiceVersion());
    }

    /**
     * Returns the current battery level.
     *
     * The @c %getBatteryValue() method implemented by the Sanbot %c SystemManager API is called to
     * retrieve the battery level.
     *
     * @return  DataResult instance containing current battery level
     *
     * The @c result property in the operation result object is a string representation of the
     * numeric battery level.
     */
    synchronized DataResult getBatteryLevel()
    {
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated("unknown");
        if (systemManager == null) return DataResult.notavailable(FuncConstant.SYSTEM_MANAGER);

        // Call Sanbot SDK method.
        logCall("SystemManager.getBatteryValue");
        return DataResult.success(Integer.toString(systemManager.getBatteryValue()));
    }

    /**
     * Returns the battery status.
     *
     * The @c %queryBatteryStatus() method implemented by the Sanbot %c HardwareManager API is
     * called to retrieve the battery status.
     *
     * @return  DataResult instance containing current battery status
     *
     * The @c result property in the operation result object is a string representation of a JSON
     * object
     * @verbatim
     * {
     *      "value": numerical value of battery status,
     *      "mode": string representation of battery status
     * }
     * @endverbatim
     */
    synchronized DataResult getBatteryStatus()
    {
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated(SanbotMappings.toBatteryStatus(0));
        if (systemManager == null) return DataResult.notavailable(FuncConstant.SYSTEM_MANAGER);

        // Call Sanbot SDK method.
        logCall("SystemManager.getBatteryStatus");
        return DataResult.success(SanbotMappings.toBatteryStatus(systemManager.getBatteryStatus()));
    }

    /**
     * Requests the Sanbot speech engine to wake up.
     *
     * Since only subclasses of the Sanbot SDK @c BindBaseActivity class can request the speech
     * engine to wake up, the request is forwarded to the BridgeSpeechHost instance.
     *
     * @param   languageType    optional Sanbot language code for wake-up command
     *
     * @return  DataResult instance containing operation result
     */
    synchronized DataResult speechDoWakeup(String languageType)
    {
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated();

        BridgeSpeechHost speechHost = getSpeechHost();
        if (speechHost == null) return DataResult.failure("Speech activity not connected");
        if (speechHost.isReady() == false) return DataResult.failure("Speech activity not ready");

        logCall("BridgeSpeechHost.doWakeup", "languageType", languageType);
        return speechHost.doWakeup(languageType);
    }

    /**
     * Requests the Sanbot speech engine to got to sleep.
     *
     * Since only subclasses of the Sanbot SDK @c BindBaseActivity class can request the speech
     * engine to go to sleep, the request is forwarded to the BridgeSpeechHost instance.
     *
     * @return  DataResult instance containing operation result
     *
     * @todo    06/09/2026 - Add request chain to BridgeRequestHandler
     */
    synchronized DataResult speechDoSleep()
    {
        if (BuildConfig.EMULATOR_MODE) return DataResult.emulated();

        BridgeSpeechHost speechHost = getSpeechHost();
        if (speechHost == null) return DataResult.failure("Speech activity not connected");
        if (speechHost.isReady() == false) return DataResult.failure("Speech activity not ready");

        logCall("BridgeSpeechHost.doSleep");
        return speechHost.doSleep();
    }

    /**
     * Configure battery charge mode.
     *
     * The system parameters specifying if automatic charging is enabled and the level beneath which
     * the robot will recharge (if automatic charging is enabled) are set.
     *
     * @param   auto            @c if true, automatic charging is enabled
     * @param   level           battery level below which robot will recharge
     *
     * @return  DataResult instance specifying operation result
     */
    synchronized DataResult configBattery(boolean auto, int level)
    {
        // Enable or diable autocharge.
        if (setSystemParam("battery_auto_charge", (auto) ? "true" : "false") == false)
            return DataResult.failure(BridgeResult.Code.FAILURE, "failed to set battery_auto_charge system parameter");

        // Set battery level below which robot will recharge.
        String value = null;
        if ((level == 10) || (level == 20) || (level == 30) || (level == 40)) value = String.format("%d", level);
        if ((value != null) && (setSystemParam("battery_auto_charge_degree", value) == false))
            return DataResult.failure(BridgeResult.Code.FAILURE, "failed to set battery_auto_charge_degree system parameter");
        return DataResult.success();
    }

    /**
     * Enables or disables modular motion auto-charge mode.
     *
     * @param   charge          @c true to charge battery, @c false to cancel
     *
     * If @p charge equals @c true the robot is instructed to move to the charging pile, if @c false
     * moving to the charging pile is cancelled.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    synchronized DataResult chargeBattery(boolean charge)
    {
        if (modularMotionManager == null) return DataResult.notavailable(FuncConstant.MODULARMOTION_MANAGER);

        BridgeLog.apicall("SanbotSDK", "ModularMotionManager", "switchCharge", charge);
        return DataResult.fromOperationResult(modularMotionManager.switchCharge(charge));
    }

    /**
     * Writes the contents of the script to a local file.
     *
     * @param   name            name of script file to execute
     * @param   content         string containing script contents
     *
     * The script name is assumed to have already been validated.
     *
     * @return  DataResult instance containing operation result
     */
    synchronized DataResult writeScript(@NonNull String name, @NonNull String content)
    {
        try
        {
            // Create file name from script name,
            String filename = name + "." + SCRIPT_EXTENSION;

            // Create the bridge script directory if necessary.
            File scriptDirectory = new File(Environment.getExternalStorageDirectory(), SCRIPT_DIRECTORY);
            FileUtils.ensureDirectory(scriptDirectory);

            // Resolve and validate the requested path before writing the script data.
            File scriptFile = FileUtils.resolveChildFile(scriptDirectory, filename);

            // Write contents to file.
            FileUtils.writeTextFile(scriptFile, content);
            BridgeLog.debug(TAG, "Script saved to " + scriptFile.getPath());
            return DataResult.success();
        }
        catch (Exception e)
        {
            BridgeLog.warning(TAG, "Failed to save script", e);
            return DataResult.failure("script_save_failed", e.getMessage());
        }
    }

    /**
     * Rettuns the list of available scripts.
     *
     * The list of files with a name containng the script file extension in the script directory is
     * retrieved. The script name is just the file name without the extension.
     *
     * @return  DataResult instance containing list of scripts
     */
    synchronized DataResult getScriptList()
    {
        List<String> scripts = new ArrayList<>();
        List<String> filenames = FileUtils.listFileNames(SCRIPT_DIRECTORY, SCRIPT_EXTENSION);
        if (filenames != null)
        {
            for (String filename : filenames) scripts.add(FileUtils.removeFileExtension(filename));
        }
        return DataResult.success(MapUtils.createMap("scripts", scripts));
    }

    /**
     * Starts execution of a script.
     *
     * A new BridgeScriptRunner instance is created and requested to start the script.
     *
     * @param   name            name of script file to execute
     *
     * The script name is assumed to have already been validated.
     *
     * @return  DataResult instance containing operation result
     */
    synchronized DataResult startScript(@NonNull String name)
    {
        if ((scriptRunner != null) && (scriptRunner.isAlive()))
            return DataResult.failure("script_running", "A script is already running");
        try
        {
            // Resolve and validate the script file.
            File scriptDirectory = new File(Environment.getExternalStorageDirectory(), SCRIPT_DIRECTORY);
            File scriptFile = FileUtils.resolveChildFile(scriptDirectory, name + "." + SCRIPT_EXTENSION);
            if ((scriptFile.isFile() == false) || (scriptFile.canRead() == false))
                return DataResult.failure("script_failed", "Cannot read script file " + name);

            scriptRunner = new BridgeScriptRunner(scriptFile, scriptRequestHost);
            scriptRunner.start();
            return DataResult.success("script_started", null);
        }
        catch (Exception e)
        {
            return DataResult.failure("script_failed", "Failed to start script " + e);
        }

    }

    /**
     * Stops execution of a running script.
     *
     * @return  DataResult instance containing operation result
     */
    synchronized DataResult stopScript()
    {
        // Joining here can deadlock with a runner waiting for service/request locks.
        // Retain the reference until it exits to prevent overlapping scripts.
        if (scriptRunner != null) scriptRunner.kill();
        return DataResult.success("script_stopped", null);
    }

    /**
     * Resets the robot.
     *
     * All motion is stopped and the head and arms are reset to the default positions, all
     * LEDs and the head light are switched off, the default emotion is set, text-to-speech is
     * stopped, speech recognision is stopped, and audio playback and/or recording is stopped.
     *
     * @return  DataResult instance containing operation result
     */
    synchronized DataResult resetRobot()
    {
        DataResult result;

        // Stop all robot motion and reset head and arms.
        sanbotMotionUnit = getSanbotMotionUnit();
        if (sanbotMotionUnit.reset() == false) return sanbotMotionUnit.getError();

        // Switch off all LEDs and head light.
        sanbotLedUnit = getSanbotLedUnit();
        if (sanbotLedUnit.reset() == false) return sanbotLedUnit.getError();

        // Set default emotion.
        result = getSanbotFaceUnit().setEmotion(null);
        if (result.isFailure()) return result;

        // Stop text-to-speech.
        result = getBridgeTtsUnit().stopSpeaking();
        if (result.isFailure()) return result;

        // Stop speech recognsition.
        result = speechDoSleep();
        if (result.isFailure()) return result;

        // Stop audio playback and/or recording.
        result = getBridgeAudioUnit().stopAudio();
        if (result.isFailure()) return result;

        return DataResult.success();
    }

    /**
     * Requests execution of a test functions.
     *
     * @param   func            name of function to execute
     * @param   params          optional string parameters
     *
     * @return  DataResult instance containing operation result
     */
    synchronized DataResult test(String func, List<String> params)
    {
        if (StringUtils.isBlank(func)) DataResult.failure("test function not specified");
        return testFunction(func, params);
    }

    /** @} */

    /**
     * @name Data Builders
     * @{
     */

    /**
     * Builds a data map containing information on the REST API provided by the bridge service
     *
     * The relevant OpenAPI data items are collected and returned in a data map.
     *
     * @return  instance of Java @c Map class containing OpenAPI data
     */
    @NonNull
    private Map<String, Object> buildOpenApiData()
    {
        Map<String, Object> data = MapUtils.createMap();
        data.put("ipAddress", getLocalIpAddress());
        data.put("httpPort", config.getHttpPort());
        data.put("auth", "Send the configured API key in the X-API-Key header");
        data.put("examples", new String[]{
            "GET /v1/info/bridge/openapi",
            "GET /v1/info/bridge/status",
            "GET /v1/info/bridge/status?unit=system.camera",
            "GET /v1/info/sensor/status",
            "GET /v1/info/sensor/orientation",
            "GET /v1/info/sensor/infrared",
            "GET /v1/info/camera/features",
            "GET /v1/info/camera/status",
            "GET /v1/info/audio/volume",
            "GET /v1/info/audio/status",
            "GET /v1/info/speech/features",
            "GET /v1/info/speech/status",
            "POST /v1/query/list/whitelight {\"refid\":\"req12345\"}",
            "POST /v1/command/robot/move {\"direction\":\"forward\",\"speed\":5,\"duration\":10}",
            "POST /v1/command/robot/walk {\"distance\":50,\"speed\":5}",
            "POST /v1/command/robot/turn {\"direction\":\"left\",\"angle\":90,\"speed\":5}",
            "POST /v1/command/robot/stop {}",
            "POST /v1/command/robot/modular {\"mode\":\"charge\", \"enable\":true}",
            "POST /v1/command/head/move {\"direction\":\"right_up\",\"angle\":10}",
            "POST /v1/command/head/turn {\"direction\":\"left\",\"angle\":15}",
            "POST /v1/command/head/nod {\"direction\":\"up\",\"angle\":10}",
            "POST /v1/command/head/reset {}",
            "POST /v1/command/head/stop {}",
            "POST /v1/command/head/center {}",
            "POST /v1/command/head/whitelight {\"on\":true,\"brightness\":3}",
            "POST /v1/command/head/led {\"side\":\"left\",\"color\":\"blue\"}",
            "POST /v1/command/arms/move {\"side\":\"both\",\"direction\":\"up\",\"angle\":45,\"speed\":5}",
            "POST /v1/command/arms/stop {\"part\":\"both\"}",
            "POST /v1/command/arms/reset {\"part\":\"both\"}",
            "POST /v1/command/arms/led {\"side\":\"left\",\"color\":\"blue\",\"flicker\":0,\"random\":0}",
            "POST /v1/command/leds/set {\"part\":\"all\",\"color\":\"blue\",\"flicker\":0,\"random\":0}",
            "POST /v1/command/face/emotion {\"emotion\":\"SMILE\",\"refresh\":4}",
            "POST /v1/command/sensor/config {\"pir\":true,\"obstacle\":true,\"touch\":true,\"infrared\":{\"enable\":true,\"update\":1000,\"sensitivity\":90},\"voicelocate\":true,\"orientation\":false}",
            "POST /v1/command/camera/config {\"camera\":\"sanbot\",\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}",
            "POST /v1/command/camera/snapshot {\"camera\":\"sanbot\",\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}",
            "POST /v1/command/camera/picture {\"camera\":\"sanbot\",\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}",
            "POST /v1/command/camera/face {\"index\":0,\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}",
            "POST /v1/command/audio/volume {\"music\":50}",
            "POST /v1/command/audio/play {\"url\":\"http://10.30.12.203:8099/test.wav\"}",
            "POST /v1/command/audio/record {\"length\":5}",
            "POST /v1/command/audio/stop {}",
            "POST /v1/command/screen/image {\"filename\":\"welcome.png\"}",
            "POST /v1/command/speech/config {\"language\":\"nl-NL\",\"speed\":100,\"pitch\":100}",
            "POST /v1/command/speech/say {\"text\":\"Hello from selected TTS engine\",\"language\":\"en-US\"}",
            "POST /v1/command/speech/stop {}",
            "POST /v1/command/script/upload {\"filename\":\"script.txt\"}",
            "POST /v1/command/script/sart {}",
            "POST /v1/command/script/stop {}",
            "GET /v1/media/camera/image",
            "GET /v1/media/camera/snapshot",
            "GET /v1/media/camera/snapshot?camera=head",
            "GET /v1/media/camera/snapshot?camera=body&warmuptime=3000&exposure=max",
            "GET /v1/media/camera/picture",
            "GET /v1/media/camera/snapshot?camera=orbbec",
            "GET /v1/media/camera/snapshot?camera=android&zoom=50",
            "GET /v1/stream/camera/video?camera=body&size=m&quality=40&fps=15",
        });
        return data;
    }

    /**
     * Builds a data map containing status data of selected bridge units.
     *
     * The status of a unit is included in the data map only if it is included in the specified
     * list.
     *
     * @param   units           list of units for which to include data
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    private Map<String, Object> buildStatusData(@NonNull List<String> units)
    {
        Map<String, Object> data = MapUtils.createMap();
        if (units.contains("bridge")) data.put("bridgeUnit", buildBridgeStatusData());
        if (units.contains("system")) data.put("systemUnit", buildSystemStatusData());
        if (units.contains("motion")) data.put("motionUnit", getSanbotMotionUnit().buildStatusData());
        if (units.contains("led")) data.put("ledUnit", getSanbotLedUnit().buildStatusData());
        if (units.contains("face")) data.put("faceUnit", getSanbotFaceUnit().buildStatusData());
        if (units.contains("sensor")) data.put("sensorUnit", getSanbotSensorUnit().buildStatusData());
        if (units.contains("camera"))
        {
            Map<String, Object> cameraData = new LinkedHashMap<>();
            cameraData.put("android", getAndroidCameraUnit().buildStatusData());
            cameraData.put("orbbec", getOrbbecCameraUnit().buildStatusData());
            cameraData.put("sanbot", getSanbotCameraUnit().buildStatusData());
            data.put("cameraUnit", cameraData);
        }
        if (units.contains("audio")) data.put("audioUnit", getBridgeAudioUnit().buildStatusData());
        if (units.contains("tts")) data.put("ttsUnitStatus", getBridgeTtsUnit().buildStatusData());
        if (units.contains("asr")) data.put("asrUnitStatus", getSanbotAsrUnit().buildStatusData());
        return data;
    }

    /**
     * Builds a data map containing bridge status data.
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    private Map<String, Object> buildBridgeStatusData()
    {
        Map<String, Object> data = MapUtils.createMap();

        // Add application status data.
        data.put("appName", getString(R.string.title_bridge));
        data.put("appId", BuildConfig.APPLICATION_ID);
        data.put("appStatus", bridgeStatus);
        data.put("robotName", config.getRobotName());
        String appMode = (BuildConfig.EMULATOR_MODE == false) ? "robot" : "emulator";
        if (BuildConfig.DEBUG) appMode += ",debug";
        data.put("appMode", appMode);
        data.put("appVersion", BuildConfig.VERSION_NAME);

        // Add robot status data,
        data.put("robotDeviceId", getDeviceId().getData());
        data.put("robotServiceVersion", getMainServiceVersion().getData());
        data.put("robotSdkName", com.sanbot.opensdk.BuildConfig.APPLICATION_ID);
        data.put("robotSdkVersion", com.sanbot.opensdk.BuildConfig.SDK_VERSION);

        // Add network status data,
        String ipAddress = getLocalIpAddress();
        int ipPort = config.getHttpPort();
        data.put("ipAddress", ipAddress);
        data.put("ipPort", ipPort);
        data.put("restUrl", "http://" + ipAddress + ":" + ipPort + "/v1");
        data.put("webSocketUrl", "ws://" + ipAddress + ":" + ipPort + WEBSOCKET_PATH);
        data.put("webSocketSession", webSocketTransport.getConnectedSessionCount());
        data.put("configFile", config.getConfigFilePath());

        return data;
    }

    /**
     * Builds a data map containing system status data.
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    private Map<String, Object> buildSystemStatusData()
    {
        Map<String, Object> data = MapUtils.createMap();
        data.put("hardwareManager", hardwareManager != null);
        data.put("systemManager", systemManager != null);

        // Add home alarm detection status.
        data.put("homeAlarmDetection", config.getEnableAlarmDetection());

        // Add battery status data,
        data.putAll(buildBatteryStatusData());

        return data;
    }

    /**
     * Builds a data map containing battery status data.
     *
     * @return  Java @c Map instance containing status data
     */
    private Map<String, Object> buildBatteryStatusData()
    {
        Map<String, Object> data = MapUtils.createMap();
        Object batteryStatus = JsonUtils.parseObject(getBatteryStatus().getData());
        Object batteryLevel = JsonUtils.parseObject(getBatteryLevel().getData());
        if (batteryStatus != null) data.put("batteryStatus", batteryStatus);
        if (batteryLevel != null) data.put("batteryLevel", batteryLevel);
        return data;
    }

    /**
     * Returns the speech features.
     *
     * The text-to-speech and speech recognition features are retrieved from the active TTS and ASR
     * units and combined into a single data map.
     *
     * @return  Java @c Map instance containing speech feature data
     */
    DataResult buildSpeechFeatureData()
    {
        Map<String, Object> data = MapUtils.createMap();

        // Add text-to-speech features.
        Object ttsFeatures = JsonUtils.parseObject(getBridgeTtsUnit().getFeatures().getData());
        if (ttsFeatures != null) data.put("tts", ttsFeatures);

        // Add speech recognition features.
        Object asrFeatures = JsonUtils.parseObject(getSanbotAsrUnit().getFeatures().getData());
        if (asrFeatures != null) data.put("asr", asrFeatures);

        return DataResult.success(data);
    }

    /** @} */

    /***********************************************************************************************
     * PACKAGE-PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Restarts the bridge service.
     *
     * The restart flag is set, and stopSelf() is called to stop the service. This method only stops
     * the service; the onDestroy() method that handles the event signaling the @c BindBaseService
     * is stopped is responsible for restarting the service.
     *
     * @param   fromMenu        @c true if restart was requested from the main activity menu
     */
    synchronized void restartBridgeService(boolean fromMenu)
    {
        // Exit if service is not running.
        if (running.get() == false) return;

        bridgeStatus = "restarting";
        BridgeLog.info(TAG, "Restarting bridge background service");
        if (fromMenu) clearTrafficLog();
        keepActivityVisible = false;
        activityVisible = false;
        restartAfterDestroy = true;
        stopSelf();
    }

    /**
     * Returns the current bridge settings.
     *
     * The method just returns the BridgeConfig instance referenced by the @c settings member
     * variable.
     *
     * @return  BridgeConfig instance containing active bridge settings
     */
    synchronized BridgeConfig getConfig()
    {
        return config;
    }

    /**
     * Set a flag that specifies if a bridge activity window is currently visible.
     *
     * @param   visible         if @c true, a bridge activity window is visible
     */
    synchronized void setActivityVisible(boolean visible)
    {
        activityVisible = visible;
    }

    /**
     * Sets a flag that specifies if the main activity is to remain visible.
     *
     * If the main activity is to remain visible the service will bring it back to the foreground if
     * it is covered by another window.
     *
     * @param   keepVisible     if @c true, the main activity is to remain visible
     */
    synchronized void setKeepActivityVisible(boolean keepVisible)
    {
        keepActivityVisible = keepVisible;
        if (keepVisible == false) activityVisible = false;

        if (keepVisible) ensureMainActivityVisible();
    }

    /**
     * Returns existing or new instance of AndroidCameraManager class.
     *
     * The Android camera manager provides access to the Android camera features through the native
     * Android @c Camera API.
     *
     * @return  instance of AndroidCameraManager class
     */
    @NonNull
    synchronized AndroidCameraManager getAndroidCameraManager()
    {
        if (androidCameraManager == null) androidCameraManager = new AndroidCameraManager(bridgeEventHost);
        return androidCameraManager;
    }

    /**
     * Returns existing or new instance of OrbbecCameraManager class.
     *
     * The Orbbec camera manager provides access to the Orbbec camera features through the OpenNI2
     * @c OpenNI API.
     *
     * @return  instance of OrbbecCameraManager class
     */
    @NonNull
    synchronized OrbbecCameraManager getOrbbecCameraManager()
    {
        if (orbbecCameraManager == null) orbbecCameraManager = new OrbbecCameraManager(bridgeEventHost);
        return orbbecCameraManager;
    }

    /**
     * Returns existing or new instance of SanbotCameraManager class.
     *
     * The Sanbot camera manager provides access to the Sanbot HD camera and audio features through
     * the Sanbot @c HDCameraManager API.
     *
     * @return  instance of SanbotCameraManager class
     */
    @NonNull
    synchronized SanbotCameraManager getSanbotCameraManager()
    {
        if (sanbotCameraManager == null) sanbotCameraManager = new SanbotCameraManager(bridgeEventHost);
        return sanbotCameraManager;
    }

    /**
     * Returns existing or new instance of AndroidSystemUnit class.
     *
     * @return  instance of AndroidSystemUnit class
     */
    @NonNull
    synchronized AndroidSystemUnit getAndroidSystemUnit()
    {
        if (androidSystemUnit == null) androidSystemUnit = new AndroidSystemUnit(bridgeEventHost);
        return androidSystemUnit;
    }

    /**
     * Returns existing or new instance of SanbotMotionUnit class.
     *
     * @return  instance of SanbotMotionUnit class
     */
    @NonNull
    synchronized SanbotMotionUnit getSanbotMotionUnit()
    {
        if (sanbotMotionUnit == null) sanbotMotionUnit = new SanbotMotionUnit(bridgeEventHost);
        return sanbotMotionUnit;
    }

    /**
     * Returns existing or new instance of SanbotLedUnit class.
     *
     * @return  instance of SanbotLedUnit class
     */
    @NonNull
    synchronized SanbotLedUnit getSanbotLedUnit()
    {
        if (sanbotLedUnit == null) sanbotLedUnit = new SanbotLedUnit(bridgeEventHost);
        return sanbotLedUnit;
    }

    /**
     * Returns existing or new instance of SanbotFaceUnit class.
     *
     * @return  instance of SanbotFaceUnit class
     */
    @NonNull
    synchronized SanbotFaceUnit getSanbotFaceUnit()
    {
        if (sanbotFaceUnit == null) sanbotFaceUnit = new SanbotFaceUnit(bridgeEventHost);
        return sanbotFaceUnit;
    }

    /**
     * Returns existing or new instance of SanbotSensorUnit class.
     *
     * @return  instance of SanbotSensorUnit class
     */
    @NonNull
    synchronized SanbotSensorUnit getSanbotSensorUnit()
    {
        if (sanbotSensorUnit == null) sanbotSensorUnit = new SanbotSensorUnit(config, bridgeEventHost);
        return sanbotSensorUnit;
    }

    /**
     * Returns existing or new instance of AndroidCameraUnit class.
     *
     * @return  instance of AndroidCameraUnit class
     */
    @NonNull
    synchronized AndroidCameraUnit getAndroidCameraUnit()
    {
        if (androidCameraUnit == null) androidCameraUnit = new AndroidCameraUnit(getApplicationContext(), config, bridgeEventHost);
        return androidCameraUnit;
    }

    /**
     * Returns existing or new instance of OrbbecCameraUnit class.
     *
     * @return  instance of OrbbecCameraUnit class
     */
    @NonNull
    synchronized OrbbecCameraUnit getOrbbecCameraUnit()
    {
        if (orbbecCameraUnit == null) orbbecCameraUnit = new OrbbecCameraUnit(getApplicationContext(), config, bridgeEventHost);
        return orbbecCameraUnit;
    }

    /**
     * Returns existing or new instance of SanbotCameraUnit class.
     *
     * @return  instance of SanbotCameraUnit class
     */
    @NonNull
    synchronized SanbotCameraUnit getSanbotCameraUnit()
    {
        if (sanbotCameraUnit == null) sanbotCameraUnit = new SanbotCameraUnit(getApplicationContext(), config, bridgeEventHost);
        return sanbotCameraUnit;
    }

    /**
     * Returns existing or new instance of concrete subclass of BridgeCameraUnit base class.
     *
     * @param   camera          camera name or alias for which to unit class
     *
     * @return  instance of either AndroidCameraUnit, OrbbecCameraUnit, or SanbotCameraUnit class
     */
    synchronized BridgeCameraUnit getBridgeCameraUnit(@NonNull String camera)
    {
        switch (camera)
        {
            case AndroidCameraManager.CAMERA_NAME:
            case AndroidCameraManager.CAMERA_ALIAS:
                return getAndroidCameraUnit();
            case OrbbecCameraManager.CAMERA_NAME:
            case OrbbecCameraManager.CAMERA_ALIAS:
                return getOrbbecCameraUnit();
            case SanbotCameraManager.CAMERA_NAME:
            case SanbotCameraManager.CAMERA_ALIAS:
                return getSanbotCameraUnit();
            default:
                return null;
        }
    }

    /**
     * Returns existing or new instance of BridgeAudioUnit class.
     *
     * @return  instance of BridgeAudioUnit class
     */
    @NonNull
    synchronized BridgeAudioUnit getBridgeAudioUnit()
    {
        if (bridgeAudioUnit == null) bridgeAudioUnit = new BridgeAudioUnit(bridgeEventHost);
        return bridgeAudioUnit;
    }

    /**
     * Returns existing or new instance of subclass of BridgeTtsUnit base class.
     *
     * Depending on the value of the @c speechUnit member variable an instance of either
     * AndroidTtsUnit or SanbotTtsUnit class is returned.
     *
     * @return  instance of either SanbotTtsUnit or AndroidTtsUnit class
     */
    @NonNull
    synchronized BridgeTtsUnit getBridgeTtsUnit()
    {
        if (bridgeTtsUnit == null)
        {
            final Context context = getApplicationContext();
            bridgeTtsUnit = AndroidTtsUnit.TTS_NAME.equals(config.getTtsName())
                ? new AndroidTtsUnit(context, config, bridgeEventHost)
                : new SanbotTtsUnit(config, bridgeEventHost);
            bridgeTtsUnit.init(getSanbotSpeechManager());
        }
        return bridgeTtsUnit;
    }

    /**
     * Returns existing or new instance of SanbotAsrUnit class.
     *
     * @return  instance of SanbotAsrUnit class
     */
    @NonNull
    synchronized SanbotAsrUnit getSanbotAsrUnit()
    {
        if (sanbotAsrUnit == null)
        {
            sanbotAsrUnit = new SanbotAsrUnit(config, bridgeEventHost);
            sanbotAsrUnit.init(getSanbotSpeechManager());
        }
        return sanbotAsrUnit;
    }

    /**
     * Returns the current bridge status.
     *
     * This method returns a string that specifies if the bridge service is active or inactive, and
     * if active specifies the IP address and port that accepts client connections.
     *
     * @param   context         current Android context
     *
     * @return  string representing bridge status
     */
    @NonNull
    String getBridgeStatus(Context context)
    {
        // If the HTTP server is not listening the service is not active,
        boolean listening = ((httpServer != null) && (httpServer.isListening()));
        if (listening == false) return context.getString(R.string.bridge_not_active);

        // Get IP address. If the last known IP address is not specified, get the local address.
        String ipAddress = bridgeIpAddress;
        if ((StringUtils.isBlank(ipAddress)) || ("0.0.0.0".equals(ipAddress))) ipAddress = getLocalIpAddress();

        // Format status string.
        return context.getString(R.string.bridge_listening_at, ipAddress, config.getHttpPort());
    }

    /**
     * Returns the recent in-memory traffic log for the activity status panel.
     *
     * @return  string containing newline-separated log entries, newest first.
     */
    @NonNull
    String getTrafficLog()
    {
        synchronized (logLock)
        {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (String line : trafficLog)
            {
                if (first == false) builder.append('\n');
                builder.append(line);
                first = false;
            }
            return builder.toString();
        }
    }

    /**
     * Returns the singleton instance of this class.
     *
     * @return  singleton instance of this class, or @c null if instance is not yet created
     */
    static BridgeService getInstance()
    {
        return instance;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Activates the service running in true robot mode.
     *
     * The HTTPServer object is created if it does not already exists, and the Sanbot SDK managers,
     * camera managers, and bridge units are initialized.
     */
    private synchronized void activateRobotRuntime()
    {
        // Create and start HTTP server if not already active.
        ensureHttpServer();
        bridgeIpAddress = getLocalIpAddress();
        bridgeStatus = "ready";

        // Init Sanbot SDK subsystem managers.
        systemManager = (SystemManager)getUnitManager(FuncConstant.SYSTEM_MANAGER);
        hardwareManager = (HardWareManager)getUnitManager(FuncConstant.HARDWARE_MANAGER);
        modularMotionManager = (ModularMotionManager)getUnitManager(FuncConstant.MODULARMOTION_MANAGER);
        zigbeeManager = (ZigbeeManager)getUnitManager((FuncConstant.ZIGBEE_MANAGER));

        BridgeLog.info(TAG, "Starting camera managers...");

        // Initialize Android camera manager.
        androidCameraManager = getAndroidCameraManager();
        androidCameraManager.init(getApplicationContext());

        // Initialize Orbbec media controller.
        orbbecCameraManager = getOrbbecCameraManager();
        orbbecCameraManager.init(getApplicationContext());

        // Initialize Sanbot media controller.
        sanbotCameraManager = getSanbotCameraManager();
        sanbotCameraManager.init(getApplicationContext(), getUnitManager(FuncConstant.HDCAMERA_MANAGER), config.getEnableFaceDetection());

        // Initialize bridge units.
        initBridgeUnits();

        // Set home alarm listener.
        enableHomeAlarmDetection(config.getEnableAlarmDetection());

        // If the main activity started this service, it may already have resumed before the service
        // finished binding to the Sanbot SDK. Adopt that activity so keep-visible mode is enabled.
        adoptActiveMainActivity();
    }

    /**
     * Activates the service running in emulator mode.
     *
     * The HTTPServer object is created if it does not already exists, the Sanbot SDK managers,
     * camera managers, and and bridge units are initialized. Since in emulator mode the service can
     * not access the cameras and is not registered with the Sanbot SDK, all manager objects have
     * @c null values.
     */
    private synchronized void activateEmulatorRuntime()
    {
        // Create and start HTTP server if not already active.
        ensureHttpServer();
        bridgeIpAddress = getLocalIpAddress();
        bridgeStatus = "ready";

        // Initialize Sanbot SDK hardware manager.
        systemManager = null;
        hardwareManager = null;

        // Initialize camera managers.
        androidCameraManager = null;
        orbbecCameraManager = null;
        sanbotCameraManager = null;

        // Initialize bridge units.
        initBridgeUnits();
    }

    /**
     * Initialize the bridge units.
     *
     * For each bridge unit the existing class instance si retrieved or a new one is created, and
     * the @c %init() method implemented by the bridge unit class is called.
     */
    private void initBridgeUnits()
    {
        BridgeLog.info(TAG, "Starting bridge units...");

        // Initialize the system unit.
        getAndroidSystemUnit().init(androidScreenHost);

        // Initialize the motion unit.
        getSanbotMotionUnit().init(
            (HeadMotionManager)getUnitManager(FuncConstant.HEADMOTION_MANAGER),
            (WingMotionManager)getUnitManager(FuncConstant.WINGMOTION_MANAGER),
            (WheelMotionManager)getUnitManager(FuncConstant.WHEELMOTION_MANAGER),
           modularMotionManager
        );

        // Initialize the LED unit.
        getSanbotLedUnit().init(hardwareManager);

        // Initialize the face unit.
        getSanbotFaceUnit().init(systemManager);

        // Initialize the sensor unit.
        getSanbotSensorUnit().init(hardwareManager, systemManager);

        // Initialize the camera units.
        getAndroidCameraUnit().init(androidCameraManager);
        getOrbbecCameraUnit().init(orbbecCameraManager);
        getSanbotCameraUnit().init(sanbotCameraManager);

        // Initialize the audio units.
        getBridgeAudioUnit().init((AudioManager)getSystemService(Context.AUDIO_SERVICE), sanbotCameraManager);

        // Initializer the speech units.
        if (bridgeTtsUnit == null) getBridgeTtsUnit();
        else bridgeTtsUnit.init(getSanbotSpeechManager());

        if (sanbotAsrUnit == null) getSanbotAsrUnit();
        else sanbotAsrUnit.init(getSanbotSpeechManager());

        BridgeLog.info(TAG, "All bridge units started...");
    }

    /**
     * Registers the connectivity event listener.
     *
     * The connectivity event listener receives events if the network connectivity status changes.
     * This method calls the @c %registerReceiver() implemented by the Android @c ContextWrapper
     * API.
     */
    private void registerConnectivityEventListener()
    {
        // If the connectivity is already set there is nothing to do.
        if (receiverRegistered.getAndSet(true)) return;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityEventListener, filter);
    }

    /**
     * Unregisters the connectivity event listener.
     *
     * This method just calls the @c %unregisterReceiver() implemented by the Android
     * @c ContextWrapper API and catches exceptions.
     */
    private void unregisterConnectivityEventListener()
    {
        // If the connectivity is not set there is nothing to do.
        if (receiverRegistered.getAndSet(false) == false) return;

        try { unregisterReceiver(connectivityEventListener); }
        catch (IllegalArgumentException ignored) {}
    }

    /**
     * Starts the health monitor.
     *
     * A new Java @c Runnable instance is created to ensures the HTTP listener port is active,
     * restarting the HttpServer if this is not the case. It also makes sure the main activity
     * remains visible if required.
     */
    private void startHealthMonitor()
    {
        // If the health monitor task executor does not exist the health monitor can't be started.
        if (monitorExecutor == null) return;

        // Schedule health monitor task.
        monitorExecutor.scheduleWithFixedDelay(new Runnable()
        {
            /**
             * Run the health monitor check.
             */
            @Override
            public void run()
            {
                // If bridge service is not running the health monitor can't be started.
                if (running.get() == false) return;

                boolean listenerMissing = ((httpServer == null) || (httpServer.isListening() == false));
                if (listenerMissing) ensureHttpServer();

                ensureMainActivityVisible();
            }
        }, 2L, 2L, TimeUnit.SECONDS);
    }

    /**
     * Returns the bridge main activity if active.
     *
     * The bridge main activity instance is required when an image must be shown on the screen. If
     * the bridge main activity is not currently active, this method launches it and waits until it
     * becomes available.
     *
     * @return  BridgeMainActivity instance, or @c null if not available
     */
    private BridgeMainActivity getMainActivity()
    {
        // Return bridge main activity if active.
        BridgeMainActivity activity = BridgeMainActivity.getActiveActivity();
        if (activity != null) return activity;

        // Bridge main activity is not yet active, launch it and wait briefly for it to appear.
        launchMainActivity();
        return waitForMainActivityHost(BRIDGE_MAIN_ACTIVITY_WAIT_MS);
    }

    /**
     * Relaunches the main bridge activity when keep-on-top mode is active.
     *
     * This method is called by the health check to make sure the main activity window remaine
     * visible. Since thet check does not run continuously, the main activity window may briefy
     * lose visibility.
     */
    private synchronized void ensureMainActivityVisible()
    {
        if ((running.get() == false) || (keepActivityVisible == false) || (activityVisible == true)) return;

        // If last attempt to bring the activity to the foreground was less than 1500 ms ago do not
        // try again.
        long now = System.currentTimeMillis();
        if (now - lastActivityLaunchAttemptMs < 1500L) return;

        // Bring main activity window to foreground.
        lastActivityLaunchAttemptMs = now;
        Intent intent = new Intent(this, BridgeMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try
        {
            startActivity(intent);
        }
        catch (Exception e)
        {
            BridgeLog.warning(TAG, "Could not relaunch bridge main activity", e);
        }
    }

    /**
     * Launches the bridge main activity.
     *
     * A new Android intent is created for the BridgeMainActivity and the activity is started.
     */
    private synchronized void launchMainActivity()
    {
        // If bridge service is not running there is nothing to do.
        if (running.get() == false) return;

        // If last attempt to launch the activity was less than 1500 ms ago do not try again.
        long now = System.currentTimeMillis();
        if (now - lastActivityLaunchAttemptMs < 1500L) return;
        lastActivityLaunchAttemptMs = now;

        Intent intent = new Intent(this, BridgeMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try
        {
            startActivity(intent);
        }
        catch (Exception e)
        {
            BridgeLog.error(TAG, "Failed to launch bridge main activity", e);
        }
    }

    /**
     * Waits for the bridge main activity to become available.
     *
     * If this method is called from the main activity user-interface thread the main activity
     * obviously exists, so the activity is immediately returned. If not, the @c getActiveActivity()
     * method implemented by the BridgeMainActicity class is called repeatedly until it either
     * returns a non-@c null value or the timeout is expired.
     *
     * @param   timeoutMs       maximum time to wait for main activity to become available
     *
     * @return  BridgeMainActivity instance, or @c null if not available after timeout expires
     */
    private BridgeMainActivity waitForMainActivityHost(long timeoutMs)
    {
        // Return activity if already available.
        if (Looper.myLooper() == Looper.getMainLooper()) return BridgeMainActivity.getActiveActivity();

        // Wait for activity to become available.
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (running.get() && (System.currentTimeMillis() < deadline))
        {
            BridgeMainActivity activity = BridgeMainActivity.getActiveActivity();
            if (activity != null) return activity;
            try
            {
                Thread.sleep(BRIDGE_MAIN_ACTIVITY_POLL_MS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return BridgeMainActivity.getActiveActivity();
    }

    /**
     * Enables keep-visible mode when the main bridge activity is already alive.
     *
     * This covers startup ordering where the activity has resumed before the service has completed
     * Sanbot SDK binding and become available through @c getInstance().
     *
     */
    private synchronized void adoptActiveMainActivity()
    {
        if (BuildConfig.EMULATOR_MODE) return;
        if (BridgeMainActivity.getActiveActivity() == null) return;

        activityVisible = true;
        setKeepActivityVisible(true);
    }

    /**
     * Waits until the bridge speech host becomes available.
     *
     * The speech host is owned by the bridge main activity. If this method is called from that main
     * activity user-interface thread, the main activity obviously exists, so the speech host is
     * immediately returned. If not, the @c getActiveSpeechHost() method implemented by the
     * BridgeMainActicity class is called repeatedly until it either returns a non-@c null value or
     * the timeout is expired.
     *
     * @param   timeoutMs       maximum time to wait for speech host to become available
     *
     * @return  BridgeSpeechHost instance, or @c null if not available after timeout expires
     */
    private BridgeSpeechHost waitForSpeechHost(long timeoutMs)
    {
        if (Looper.myLooper() == Looper.getMainLooper()) return BridgeMainActivity.getSpeechHost();

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (running.get() && (System.currentTimeMillis() < deadline))
        {
            BridgeSpeechHost speechHost = BridgeMainActivity.getSpeechHost();
            if ((speechHost != null) && speechHost.isReady()) return speechHost;
            try
            {
                Thread.sleep(BRIDGE_MAIN_ACTIVITY_SPEECH_POLL_MS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return BridgeMainActivity.getSpeechHost();
    }

    /**
     * Creates the HTTP server using the specified listener port.
     *
     * @param   httpPort        HTTP listener port
     *
     * @return  new HttpServer instance
     */
    @NonNull
    private synchronized HttpServer createHttpServer(int httpPort)
    {
        return new HttpServer(config.getRobotName(), httpPort, restTransport, webSocketTransport, WEBSOCKET_PATH, httpServerHost);
    }

    /**
     * Stops the HTTP server if it exists.
     */
    private synchronized void stopHttpServer()
    {
        if (httpServer != null)
        {
            httpServer.stop();
            httpServer = null;
        }
    }

    /**
     * Starts the HTTP server if it is not already active.
     *
     */
    private synchronized void ensureHttpServer()
    {
        // If the bridge service is not running there is nothing to do.
        if (running.get() == false) return;

        // If server is already running there is nothing to do.
        HttpServer server = getHttpServer();
        if (server.isListening()) return;

        server.start();
    }

    /**
     * Returns the existing HTTP server or create a new one.
     *
     * @return  existing or new HttpServer instance
     */
    private HttpServer getHttpServer()
    {
        if (httpServer == null) httpServer = createHttpServer(config.getHttpPort());
        return httpServer;
    }

    /**
     * Shuts down a bridge unit.
     *
     * The listener for bridge events is deleted, and the @c %shutdown() method implemented by the
     * BridgeUnit class is called.
     *
     * @param   unit            BrideUnit instance to shut down
     */
    private void shutdownBridgeUnit(BridgeUnit unit)
    {
        if (unit == null) return;

        try
        {
            unit.unregisterEventListener();
            unit.shutdown();
        }
        catch (RuntimeException e)
        {
            BridgeLog.warning(TAG, "Failed to shut down " + unit.getClass().getSimpleName(), e);
        }
    }

    /**
     * Enable or disable home alarm detection.
     *
     * @param   enable          @c true (@c false) to enable (disable) home alarm detection
     */
    private void enableHomeAlarmDetection(boolean enable)
    {
        if (systemManager == null) return;
        if (enable) systemManager.setOnIDarlingListener(homeAlarmEventListener);
        else systemManager.setOnIDarlingListener(disabledHomeAlarmEventListener);
    }

    /**
     * Returns the Sanbot speech manager if a Sanbot speech component needs it.
     *
     * The Sanbot SDK @c SpeechManager instance is required by the Sanbot TTS unit and by the
     * Sanbot ASR unit. If Android TTS is selected and ASR is disabled, no Sanbot speech component
     * is active and the speech manager is not requested. In emulator mode the Sanbot speech
     * manager is unavailable and the method returns @c null.
     *
     * @return  Sanbot SDK @c SpeechManager instance, or @c null if not needed or unavailable
     */
    private Object getSanbotSpeechManager()
    {
        if (BuildConfig.EMULATOR_MODE) return null;
        if (AndroidTtsUnit.TTS_NAME.equals(config.getTtsName()) && (config.getEnableAsr() == false)) return null;
        return getUnitManager(FuncConstant.SPEECH_MANAGER);
    }

    /**
     * Returns the speech host that executes speech engine wake/sleep requests.
     *
     * The Sanbot @c SpeechManager @c %doWakeup() and @c %doSleep() methods can not be called from
     * classes that extend the Sanbot SDK BindBaseService class. A speech host linked to the
     * BridgeMainActivity class is responsible for executing these methods. This method returns
     * that speech host.
     *
     * @return  BridgeSpeechHost instance
     */
    private BridgeSpeechHost getSpeechHost()
    {
        BridgeSpeechHost speechHost = BridgeMainActivity.getSpeechHost();
        if ((speechHost != null) && speechHost.isReady()) return speechHost;

        // Bridge main activity is not yet active, launch it and wait briefly for speech binding.
        launchMainActivity();
        return waitForSpeechHost(BRIDGE_MAIN_ACTIVITY_SPEECH_WAIT_MS);
    }

    /**
     * Dispatches bridge @e info, @e query and @e command requests.
     *
     * The BridgeRequestHandler method matching the request type is called.
     *
     * @param   request         bridge request to handle
     *
     * @return  JsonResponse instance containing operation result
     */
    private JsonResponse routeBridgeRequest(BridgeRequest request)
    {
        synchronized (requestLock)
        {
            if (request == null)
                return JsonResponse.error(null, JsonResponse.CODE_BAD_REQUEST, "no request data");

            try
            {
                if (BridgeProtocol.TYPE_INFO.equals(request.type))
                    return bridgeRequestHandler.handleInfoRequest(request);
                if (BridgeProtocol.TYPE_QUERY.equals(request.type))
                    return bridgeRequestHandler.handleQueryRequest(request);
                if (BridgeProtocol.TYPE_COMMAND.equals(request.type))
                    return bridgeRequestHandler.handleCommandRequest(request);
                return JsonResponse.notFound(request, "unknown request " + request.toString());
            }
            catch (RuntimeException e)
            {
                return JsonResponse.internalError(request, e.getClass().getSimpleName());
            }
        }
    }

    /**
     * Dispatches bridge @e media requests.
     *
     * @param   request         bridge media request to dispatch
     *
     * @return  instance of @c MediaResponse class containing media data
     */
    private MediaResponse routeMediaRequest(BridgeRequest request)
    {
        // If request data is not available something is wrong ...
        if (request == null) return MediaResponse.create(null, 400, "no request data");

        synchronized (requestLock)
        {
            return bridgeRequestHandler.handleMediaRequest(request);
        }
    }

    /**
     * Clears the recent in-memory traffic log.
     */
    private void clearTrafficLog()
    {
        synchronized (logLock)
        {
            trafficLog.clear();
        }
    }

    /**
     * Writes Sanbot SDK function call details to the Android log.
     *
     * @param   func            Sanbot SDK function name
     * @param   args            function arguments
     *
     * The @p args parameter represents a list of key-value pairs, each pair specifying the name and
     * the value of a Sanbot SDK function parameter.
     */
    private void logCall(String func, Object... args)
    {
        String kvp = "";
        List<String> kvpList = new ArrayList<>();
        boolean isKey = true;
        for (Object arg : args)
        {
            if (isKey) kvp = arg + "=";
            else
            {
                kvp = kvp + arg;
                kvpList.add(kvp);
            }
            isKey = !isKey;
        }
        BridgeLog.debug(TAG, String.format("%s (%s)",  func, TextUtils.join(",", kvpList)));
    }

    /**
     * Adds the specified message to the in-memory traffic log.
     *
     * The message is added, and if the number of messages exceeds the configured maximum the first
     * message form the log is deleted.
     *
     * @param   message         message to be added
     */
    private void appendTrafficLog(String message)
    {
        // Write message to ADB log.
        if (message != null) Log.d("BridgeTraffic", message);

        // Write message to message log. If the message length exceeds the maximum it is truncated.
        String logMessage = (message == null) ? "" : message.replace('\n', ' ').replace('\r', ' ').trim();
        if (logMessage.length() > MAX_TRAFFIC_LOG_MESSAGE_LENGTH)
            logMessage = logMessage.substring(0, MAX_TRAFFIC_LOG_MESSAGE_LENGTH) + "...";

        String stampedMessage = TRAFFIC_LOG_TIMEFORMAT.format(new Date()) + " " + logMessage;
        synchronized (logLock)
        {
            trafficLog.addLast(stampedMessage);
            while (trafficLog.size() > MAX_REQUEST_LOG_LINES) trafficLog.removeFirst();
        }
    }

    /**
     * Checks if the specified API key is valid.
     *
     * The specified API key is compared to the API key in the bridge settings.
     *
     * @param   apiKey          API key to check
     *
     * @return  @c true if API key is valid, @c false if invalid
     */
    private boolean isApiKeyValid(String apiKey)
    {
        return ((apiKey != null) && (config.getApiKey().equals(apiKey.trim())));
    }

    /**
     * Returns the local IP address associated with the HTTP listener port.
     *
     * The robot firmware can abort inside Android's native network-interface enumeration path, so
     * this method uses WifiManager instead. The listener itself is bound to 0.0.0.0; this value is
     * only used for status and client connection hints.
     *
     * @return string specifying IP address, or <c>0.0.0.0</c> on failure
     */
    private String getLocalIpAddress()
    {
        String wifiIpAddress = getWifiIpAddress();
        return StringUtils.isBlank(wifiIpAddress) ? "0.0.0.0" : wifiIpAddress;
    }

    /**
     * Returns the current WiFi IPv4 address.
     *
     * @return string specifying IP address, or @c null on failure
     */
    private String getWifiIpAddress()
    {
        try
        {
            WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) return null;

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) return null;

            int ipAddress = wifiInfo.getIpAddress();
            if (ipAddress == 0) return null;

            return String.format(Locale.US, "%d.%d.%d.%d",
                (ipAddress & 0xff),
                ((ipAddress >> 8) & 0xff),
                ((ipAddress >> 16) & 0xff),
                ((ipAddress >> 24) & 0xff));
        }
        catch (Exception e)
        {
            if (BuildConfig.DEBUG)
            {
                BridgeLog.warning(TAG, "Could not determine Wi-Fi IP address", e);
            }
            return null;
        }
    }

    /**
     * Sets a system parameter.
     *
     * @param   key             name of parameter to set
     * @param   value           value of paremeter to set
     *
     * @return  @c true if parameter was set, or @c falee on failure
     */
    private boolean setSystemParam(String key, String value)
    {
        try
        {
            Setting.putString(getApplicationContext().getContentResolver(), key, value);
            BridgeLog.error(TAG, "system parameter \""+ key + "\" set to \"" + value + "\"");
            return true;
        }
        catch (Exception e)
        {
            BridgeLog.error(TAG, "failed to set system parameter", e);
            return false;
        }

    }


    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Implements network connectivity event handlers.
     *
     * This class implement methods that handle events thrown by the Android WiFi subsystem.
     */
    private final class ConnectivityEventListener extends BroadcastReceiver
    {
        /**
         * Called when network connection status change event is received.
         *
         * If the service is running the HTTP server is restarted.
         *
         * @param   context         Android context
         * @param   intent          Android intent
         */
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (running.get() == false) return;

            String action = (intent != null) ? intent.getAction() : "unknown";
            BridgeLog.info(TAG, "Connectivity change received: " + action);
            ensureHttpServer();
        }
    }

    /**
     * Implements home alarm event handlers.
     *
     * This class implements methods that handle events thrown by the Sanbot @ Alarm application.
     */
    private final class HomeAlarmEventListener implements IDarlingListener
    {
        /**
         * Called when a home alarm is received.
         *
         * The event is broadcast to all WebSocket clients.
         *
         * @param   type            numerical alarm type
         *
         * The numerical @p type parameter is converted to a human-readable type name.
         */
        @Override
        public void onAlarm(int type)
        {
            Map<String, Object> data = MapUtils.createMap("type", type, "name", SanbotMappings.toAlarmName(type));
            webSocketTransport.broadcastEvent(BridgeProtocol.MODULE_ROBOT, BridgeProtocol.EVENT_ALARM, data);
        }
    }

    /**
     * Implements no-op home alarm event handlers.
     *
     * This class implements a method that ignores events thrown by the Sanbot @ Alarm application.
     * An instance of this class must be registered with the Sanbot @c SystemManager API to disable
     * the bridge application from handling home alarm events.
     */
    private static final class DisabledHomeAlarmEventListener implements IDarlingListener
    {
        @Override
        /**
         * Called when a home alarm is received.
         *
         * The event is ignored.
         *
         * @param   type            numerical alarm type
         *
         * The numerical @p type parameter is converted to a human-readable type name.
         */
        public void onAlarm(int type) {}
    }

    /**
     * Implements callback methods invoked by HttpServer instance.
     *
     * This class implements methods that are called by the HTTPServer instance if the connection
     * connection status changes, and implements a method called to add data to the traffic log.
     */
    private final class HttpServerHost implements HttpServer.Host
    {
        /**
         * Called when the HTTP server is starting.
         *
         * This method just updates the bridge status.
         */
        @Override
        public void onServerStarting()
        {
            bridgeStatus = "starting";
        }

        /**
         * Called when the HTTP server finished starting and is now listening for events.
         *
         * This method just copies the bridge IP address to a member variable and updates the bridge
         * status.
         *
         * @param   port            HTTP listener port
         * @param   webSocketPath   path used for WebSocket upgrade requests
         */
        @Override
        public void onServerListening(int port, String webSocketPath)
        {
            bridgeIpAddress = getLocalIpAddress();
            bridgeIpPort = port;
            bridgeStatus = "listening";
            BridgeLog.info(TAG, "Bridge server listening on ws://" + bridgeIpAddress + ":" + bridgeIpPort + webSocketPath);
        }

        /**
         * Called when the HTTP server fails.
         *
         * This method just updates the bridge status.
         *
         * @param   throwable       failure exception
         */
        @Override
        public void onServerFailed(Throwable throwable)
        {
            bridgeStatus = "failed";
            BridgeLog.error(TAG, "Server failed", throwable);
        }

        /**
         * Called to add a message to the traffic log.
         *
         * This method just calls the @c %appendTrafficLog method implemented by the enclosing
         * BridgeService class.
         *
         * @param   message         message to be added to traffic log
         */
        @Override
        public void appendTrafficLog(String message)
        {
            BridgeService.this.appendTrafficLog(message);
        }
    }

    /**
     * Implements callback methods invoked by AndroidSystemUnit instance.
     *
     * This class implements methods called by the AndroidSystemUnit instance to show or hide a
     * full-screen overlay image on the robot screen.
     */
    private final class AndroidScreenHost implements AndroidSystemUnit.ScreenHost
    {
        /**
         * Displays a full-screen overlay image.
         *
         * The @c %showScreenImage() method implemented by the BridgeMainActivity class is called.
         *
         * @param   imageFile       image file to be shown
         *
         * @return  DataResult instance containing result data
         */
        @Override
        public DataResult showScreenImage(java.io.File imageFile)
        {
            BridgeMainActivity activity = getMainActivity();
            if (activity == null) return DataResult.notavailable("main_activity");
            return activity.showScreenImage(imageFile);
        }

        /**
         * Hides the full-screen overlay image.
         *
         * The @c %hideScreenImage() method implemented by the BridgeMainActivity class is called.
         *
         * @return  DataResult instance containing result data
         */
        @Override
        public DataResult hideScreenImage()
        {
            BridgeMainActivity activity = getMainActivity();
            if (activity == null) return DataResult.notavailable("main_activity");
            return activity.hideScreenImage();
        }
    }

    /**
     * Implements callback methods invoked by BridgeScriptRunner instance.
     *
     * This class implement a method called by the BridgeScriptRunner to execute a bridge request.
     */
    private final class ScriptRequestHost implements BridgeScriptRunner.RequestHost
    {
        /**
         * Executes the bridge request.
         *
         * If the bridge runner script thread is active routeBridgeRequest() is called to execute
         * the request.
         *
         * @param   request     bridge request to be executed
         *
         * @return  JsonResponse instance containing operation result
         */
        public JsonResponse execute(BridgeRequest request)
        {
            synchronized (requestLock)
            {
                if (Thread.currentThread().isInterrupted() == false) return routeBridgeRequest(request);
                else return JsonResponse.error(request, JsonResponse.CODE_BAD_REQUEST, "Script stopped");
            }
        }
    }

    /**
     * Implements callback methods for publishing bridge events.
     */
    private final class EventHost implements BridgeEventHost
    {
        /**
         * Called to broadcast a bridge unit event to all WebSocket clients.
         *
         * @param   bridgeEvent bridge unit event
         */
        @Override
        public void publishEvent(BridgeEvent bridgeEvent)
        {
            if (bridgeEvent != null) webSocketTransport.broadcastEvent(
                bridgeEvent.getModule(), bridgeEvent.getEvent(), bridgeEvent.getData());
        }
    }

    /**
     * Implements callback methods invoked by RestTransport class.
     *
     * This class implements methods that are called by the RestTransport class to requests to the
     * bridge request handler.
     */
    private final class RestTransportHost implements BridgeTransport.Host
    {
        /**
         * Called if a bridge request is received.
         *
         * The routeBridgeRequest() method is called to forward the request to the request handler.
         *
         * @param   request         bridge request to handle
         *
         * @return  JsonResponse instance containing response data
         */
        @Override
        public JsonResponse routeBridgeRequest(BridgeRequest request)
        {
            return BridgeService.this.routeBridgeRequest(request);
        }

        /**
         * Routes a media request.
         *
         * The routeMediaRequest() method is called to forward the request to the request handler.
         *
         * @param   request         media request to handle
         *
         * @return  MediaResponse instance containing response data
         */
        @Override
        public MediaResponse routeMediaRequest(BridgeRequest request)
        {
            return BridgeService.this.routeMediaRequest(request);
        }

        /**
         * Handles a audio or video stream request.
         *
         * The request is forwarded to the Sanbot media unit.
         *
         * @param   outputStream    stream to write audio or video data to
         * @param   socket          I/O socket for output stream
         * @param   request         audio or video stream request data
         *
         * @throws  IOException     thrown if audio or video data could not be written to stream
         */
        @Override
        public void routeStreamRequest(OutputStream outputStream, Socket socket, BridgeRequest request)
        {
            if ((request != null) && BridgeProtocol.ACTION_VIDEO.equals(request.action))
            {
                JsonObject payload = JsonUtils.toJsonObject(request.payload, new JsonObject());
                String camera = JsonUtils.getString(payload, "camera");
                if (StringUtils.isBlank(camera)) camera = BridgeCameraUnit.DEFAULT_CAMERA_NAME;

                Map<String, Object> params = MapUtils.fromJsonObject(payload);
                String cameraName = BridgeCameraUnit.parseCameraParams(camera, params);
                BridgeCameraUnit cameraUnit = (cameraName != null) ? getBridgeCameraUnit(cameraName) : null;
                if (cameraUnit == null)
                {
                    appendTrafficLog(BridgeTransport.clientIp(socket) + " OUT invalid camera name");
                    return;
                }

                try
                {
                    cameraUnit.writeVideoStream(outputStream, socket, params);
                }
                catch (IOException e)
                {
                    appendTrafficLog(BridgeTransport.clientIp(socket) + " OUT IO error " + e.getClass().getSimpleName());
                }
            }
            else appendTrafficLog(BridgeTransport.clientIp(socket) + " OUT invalid request");
        }

        /**
         * Checks if the specified API key is valid.
         *
         * @param   apiKey          API key to check
         *
         * @return  @c true if API key is valid, @c false if invalid
         */
        @Override
        public boolean isApiKeyValid(String apiKey)
        {
            return BridgeService.this.isApiKeyValid(apiKey);
        }

        /**
         * Adds a message to the traffic log.
         *
         * @param   message         message to be added to traffic log
         */
        @Override
        public void appendTrafficLog(String message)
        {
            BridgeService.this.appendTrafficLog(message);
        }
    }

    /**
     * Implements callback methods for invoked by WebSocketTransport class.
     *
     * This class implements methods that are called by the WebSocketTransport class to requests to
     * the bridge request handler.
     */
    private final class WebSocketTransportHost implements BridgeTransport.Host
    {
        /**
         * Called if a bridge request is received.
         *
         * The routeBridgeRequest() method is called to forward the request to the request handler.
         *
         * @param   request         bridge request to handle
         *
         * @return  JsonResponse instance containing response data
         */
        @Override
        public JsonResponse routeBridgeRequest(BridgeRequest request)
        {
            return BridgeService.this.routeBridgeRequest(request);
        }

        /**
         * Routes a media request.
         *
         * The routeMediaRequest() method is called to forward the request to the request handler.
         *
         * @param   request         BridgeRequest instance containing request data
         *
         * @return  MediaResponse instance containing response data
         */
        @Override
        public MediaResponse routeMediaRequest(BridgeRequest request)
        {
            return BridgeService.this.routeMediaRequest(request);
        }

        /**
         * Handles an audio or video stream request.
         *
         * Since the WebSocket protocol does not support audio or video stream requests a warning
         * message is written to the traffic log.
         *
         * @param   outputStream    stream to write audio or video data to
         * @param   socket          I/O socket for output stream
         * @param   request         audio or video stream request data
         *
         * Since streaming is not supported none of the method parameters are actually used.
         *
         * @throws  IOException     thrown if audio or video data could not be written to stream
         */
        @Override
        public void routeStreamRequest(OutputStream outputStream, Socket socket, BridgeRequest request)
        {
            appendTrafficLog(BridgeTransport.clientIp(socket) + " OUT media streaming not supported");
        }

        /**
         * Checks if the specified API key is valid.
         *
         * @param   apiKey          API key to check
         *
         * @return  @c true if API key is valid, @c false if invalid
         */
        @Override
        public boolean isApiKeyValid(String apiKey)
        {
            return BridgeService.this.isApiKeyValid(apiKey);
        }

        /**
         * Adds a message to the traffic log.
         *
         * @param   message         message to be added to traffic log
         */
        @Override
        public void appendTrafficLog(String message)
        {
            BridgeService.this.appendTrafficLog(message);
        }
    }

    /***********************************************************************************************
     * METHODS FOR TESTING PURPOSES ONLY
     **********************************************************************************************/

    private DataResult testFunction(String func, List<String> params)
    {
        if ("queryPIRStatus".equals(func)) return DataResult.fromOperationResult(hardwareManager.queryPirStatus(1));
        if ("queryGravityData".equals(func)) return DataResult.fromOperationResult(hardwareManager.queryGravityData());
        if ("queryBatteryStatus".equals(func)) return DataResult.fromOperationResult(hardwareManager.queryBatteryStatus());
        if ("zigbee".equals(func)) return testZigbee();

        return DataResult.failure("unknown test function");
    }

    @NonNull
    private DataResult testZigbee()
    {
        zigbeeManager.setZigbeeListener(new ZigbeeManager.ZigbeeListener()
        {
            @Override
            public void notifyWhiteList(@NonNull String s)
            {
                BridgeLog.debug("ZIGBEE", "notifyWhiteList " + s);
            }

            @Override
            public void notifyStatusChange(@NonNull String s)
            {
                BridgeLog.debug("ZIGBEE", "notifyStatusChange " + s);

            }

            @Override
            public void notifyInfo(@NonNull String s)
            {
                BridgeLog.debug("ZIGBEE", " notifyInfo" + s);

            }
        });

/*
        JsonObject json = new JsonObject();
        json.addProperty("macaddr", "00178801080ae53c");
        BridgeLog.debug("[ZIGBEE1]", zigbeeManager.addWhiteList(json.toString()).getResult());
        BridgeLog.debug("[ZIGBEE2]", zigbeeManager.getWhiteList().getResult());
        BridgeLog.debug("[ZIGBEE3]", zigbeeManager.switchWhtieList(false).getResult());
        BridgeLog.debug("[ZIGBEE4]", zigbeeManager.getWhiteList().getResult());
        BridgeLog.debug("[ZIGBEE5]", zigbeeManager.setAllowJoinTime(600).getResult());

*/
        return DataResult.success();
    }
}
