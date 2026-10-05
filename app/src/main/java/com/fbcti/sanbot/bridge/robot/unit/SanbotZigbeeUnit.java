/**
 * @file        SanbotZigbeeUnit.java
 * @brief       Implements SanbotZigbeeUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.JsonUtils;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.google.gson.JsonObject;
import com.sanbot.opensdk.function.unit.ZigbeeManager;

/**
 * Manages Sanbot SDK Zigbee operations.
 *
 * This class extends the abstract BridgeCameraUnit class to provide access to the Sanbot Zigbee
 * controller.
 *
 * @version     1.0.003
 * @date        5 Oct 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 * @since       1.0.003
 */
public class SanbotZigbeeUnit extends BridgeUnit
{
    /** Source label used for log messages. */
    private static final String TAG = "SanbotZigbeeUnit";

    /** Active Sanbot @c ZigbeeManager API instance. */
    private ZigbeeManager zigbeeManager = null;

    /** Flag specifying if this unit is enabled. */
    private final boolean enabled;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new SanbotZigbeeUnit instance.
     *
     * @param   config          persistent bridge configuration
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public SanbotZigbeeUnit(@NonNull BridgeConfig config, BridgeEventHost eventHost)
    {
        super(eventHost);
        config.setEnableZigbee(false);
        enabled = config.getEnableZigbee();
    }

    /**
     * @name Zigbee Unit Operations
     * @{
     */

    /**
     * For test purposes only.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public DataResult test(int testcase)
    {
        if (unitStatus == UnitStatus.DISABLED) return DataResult.failure(BridgeResult.Code.NOT_AVAILABLE, "Zigbee unit");

        if (zigbeeManager == null) return DataResult.failure(BridgeResult.Code.NOT_AVAILABLE, "ZigbeeManager");

        switch (testcase)
        {
            case 0:
                return DataResult.fromOperationResult(zigbeeManager.setAllowJoinTime(600));
            case 1:
                return DataResult.fromOperationResult(zigbeeManager.getWhiteList());
            case 2:
                JsonObject json = new JsonObject();
                json.addProperty("macaddr", "00178801080ae53c");
                return DataResult.fromOperationResult(zigbeeManager.addWhiteList(json.toString()));
            default:
                return DataResult.failure("invalid test case " + testcase);
        }
    }

    /**
     * @}
     */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * If the unit is enabled, and the active Zigbee manager instance referenced by the
     * @p zigbeeManager function parameter is available it is copied to a member variable, the
     * Zigbee event listener is registered with the speech manager, and the unit status is set.
     *
     * @param   zigbeeManager   active Sanbot SDK @c ZigbeeManager API instance
     */
    public synchronized void init(Object zigbeeManager)
    {
        logStatus();

        if (enabled == false) unitStatus = UnitStatus.DISABLED;
        else
        {
            // Copy zigbee manager and register event listener.
            this.zigbeeManager = (zigbeeManager instanceof ZigbeeManager) ? (ZigbeeManager)zigbeeManager : null;
            if (this.zigbeeManager != null) this.zigbeeManager.setZigbeeListener(new BridgeZigbeeListener());
            BridgeLog.debug(TAG, "Zigbee is enabled");

            // Set status.
            if (this.zigbeeManager != null) unitStatus = UnitStatus.STARTED;
            else unitStatus = UnitStatus.NOTINITIALIZED;
        }
        logStatus();
    }

    /**
     * Performs unit shutdown tasks.
     */
    public void shutdown()
    {
        unitStatus = UnitStatus.SHUTDOWN;
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Implements Zigbee event handlers.
     *
     * This class implements methods that handle events thrown by the Sanbot @c ZigbeeManager API.
     * Currently each of these methods just publises a bridge event.
     */
    private final class BridgeZigbeeListener implements ZigbeeManager.ZigbeeListener
    {
        /**
         * Called when the Zigbee status changes.
         *
         * This method just publishes a bridge event containing the new Zigbee status in the event
         * data.
         *
         * @param   s           new Zigbee status
         */
        @Override
        public void notifyStatusChange(@NonNull String s)
        {
            BridgeLog.apievent("SanbotSDK", "ZigbeeListener", "notifyStatusChange", s);
            publishEvent(BridgeProtocol.MODULE_ROBOT, BridgeProtocol.EVENT_ZIGBEE,
                MapUtils.createMap("status", JsonUtils.parseObject(s)));
        }

        /**
         * Called when Zigbee information is received.
         *
         * This method just publishes a bridge event containing the received Zigbee information in
         * the event data.
         *
         * @param   s           received Zigbee information
         */
        @Override
        public void notifyInfo(@NonNull String s)
        {
            BridgeLog.apievent("SanbotSDK", "ZigbeeListener", "notifyInfo", s);
            publishEvent(BridgeProtocol.MODULE_ROBOT, BridgeProtocol.EVENT_ZIGBEE,
                MapUtils.createMap("info", JsonUtils.parseObject(s)));
        }

        /**
         * Called when Zigbee whitelist is updated.
         *
         * This method just publishes a bridge event containing the updated Zigbee whitelist in the
         * event data.
         *
         * @param   s           updated Zigbee whitelist
         */
        @Override
        public void notifyWhiteList(@NonNull String s)
        {
            BridgeLog.apievent("SanbotSDK", "ZigbeeListener", "notifyWhiteList", s);
            publishEvent(BridgeProtocol.MODULE_ROBOT, BridgeProtocol.EVENT_ZIGBEE,
                MapUtils.createMap("whitelist", JsonUtils.parseObject(s)));
        }
    }
}