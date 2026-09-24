/**
 * @file        SanbotLedUnit.java
 * @brief       Implements SanbotLedUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.robot.BridgeResult;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.beans.LED;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.interfaces.hardware.WhiteLightBrightnessListener;

import java.util.Map;

/**
 * Manages Sanbot SDK LED and head light operations.
 *
 * Colored leds are located at the left and right side of the robot head, left and right robot arm,
 * and in the robot base near the wheels. The leds can be set to be switched on, switched off or
 * flicker with a specific period. When flickering, the led can also alternate colors randomly.
 *
 * The LEDs in the robot arms are used by the system to indicate battery/charge status and are
 * updated every ten seconds, so the color and mode set for the arm LEDs is not persistent and is
 * reset by the system the next time an internal battery status event is handled.
 *
 * @version     1.0.001
 * @date        4 Aug 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class SanbotLedUnit extends BridgeUnit
{
    /** Source label used for log messages. */
//    private static final String TAG = SanbotLedUnit.class.getSimpleName();

    /** Active Sanbot @c HardWareManager API instance. */
    private HardWareManager hardwareManager = null;

    /** White light query reference for the currently pending brightness query. */
    private String headLightBrightnessQueryReference = null;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a nwe SanbotLedUnit instance.
     *
     * The base class constructor is called to copy the callback host to a member variable.
     *
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public SanbotLedUnit(BridgeEventHost eventHost)
    {
        // Call base class constructor.
        super(eventHost);
    }

    /**
      * @name LED Unit Operations
      * @{ 
     */ 

    /**
     * Switches the head light on or off.
     *
     * The @c switchWhiteLight() function implemented by the Sanbot SDK @c HardwareManager class is
     * called to switch the light on or off.
     *
     * @param   on              if @c true (@c false), switch light on (off)
     *
     * @return  DataResult instance specifying operation result
     */
    public synchronized DataResult switchHeadLight(boolean on)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (hardwareManager == null) return DataResult.notavailable(FuncConstant.HARDWARE_MANAGER);

        BridgeLog.apicall("SanbotSDK", "HardwareManager", "switchWhiteLight", on);
        return DataResult.fromOperationResult(hardwareManager.switchWhiteLight(on));
    }

    /**
     * Sets the brightness of the head light.
     *
     * The @c %setWhiteLightLevel() function implemented by the Sanbot SDK @c HardwareManager class
     * is called to set the light level.
     *
     * @param   brightness      requested brightness
     *
     * Allowed values of @p brightness are 1 (energy-saving), 2 (soft) abd 3 (bright).
     *
     * @return  DataResult instance specifying operation result
     */
    public synchronized DataResult setHeadLightBrightness(int brightness)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (hardwareManager == null) return DataResult.notavailable(FuncConstant.HARDWARE_MANAGER);

        BridgeLog.apicall("SanbotSDK", "HardwareManager", "setWhiteLightLevel", brightness);
        return DataResult.fromOperationResult(hardwareManager.setWhiteLightLevel(brightness));
    }

    /**
     * Queries the brightness of the head light.
     *
     * If a query is not currently waiting for response, the @c %queryWhiteLightBrightness()
     * function implemented by the Sanbot SDK @c HardwareManager class is called to send a request
     * to asynchroously query the brightness of the head light.
     *
     * @param   refid           query reference id
     *
     * @return  DataResult instance specifying operation result
     */
    public synchronized DataResult queryHeadLightBrightness(String refid)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (hardwareManager == null) return DataResult.notavailable(FuncConstant.HARDWARE_MANAGER);
        if (StringUtils.isBlank(refid)) return DataResult.failure(BridgeResult.Code.INVALID_PARAMS, "query reference id may not be empty");

        // If a query is currently open return an error response.
        if (headLightBrightnessQueryReference != null)
            return DataResult.failure(BridgeResult.Code.NOT_READY, "query " + headLightBrightnessQueryReference + " still waiting for response");

        // Set reference.
        headLightBrightnessQueryReference = refid;

        BridgeLog.apicall("SanbotSDK", "HardwareManager","queryWhiteLightLevel");
        DataResult result = DataResult.fromOperationResult(hardwareManager.queryWhiteLightBrightness());
        if (result.isFailure()) headLightBrightnessQueryReference = null;
        return result;
    }

    /**
     * Sets the status of a single or all colored leds.
     *
     * The @c %setLED() function implemented by the Sanbot SDK @c HardwareManager class is called
     * to set the color, flicker and random mode of a single led or all colored leds simultaneously.
     *
     * @param   part            specifies which led(s) to control
     * @param   mode            combination of color, flicker and random mode
     * @param   delayTime       flicker period in multiples of 100ms range (from 0 to 255)
     * @param   randomCount     number of colors to apply in random flicker mode (from 1 to 7)
     *
     * Possible values of @c part are specified by the SDK @c LED class (@c PART_ALL, @c PART_WHEEL,
     * @c PART_LEFT_HAND, @c PART_RIGHT_HAND, @c PART_LEFT_HEAD, @c PART_RIGHT_HEAD). The value of
     * @c mode combines the color, flicker and random mode. Possible values are @c MODE_OFF,
     * @c MODE_{COLOR} or @c MODE_FLICKER_{COLOR} with @c {COLOR} one of @c WHITE, @c RED, @c GREEM,
     * @c PINK @c PURPLE, @c BLUE or @c YELLOW, or @c MODE_FLICKER_RANDOM.
     *
     * @return  DataResult instance specifying operation result
     */
    public synchronized DataResult setLed(byte part, byte mode, byte delayTime, byte randomCount)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (hardwareManager == null) return DataResult.notavailable(FuncConstant.HARDWARE_MANAGER);

        LED led = new LED(part, mode, delayTime, randomCount);
        BridgeLog.apicall("SanbotSDK", "HardwareManager", "setLED", part, mode, delayTime, randomCount);
        return DataResult.fromOperationResult(hardwareManager.setLED(led));
    }

    /** @} */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * The active hardware manager is copied to a class member variable, the event listener is set,
     * the unit status is set, and the white head light is switched off,
     *
     * @param   hardwareManager active Sanbot @c HardwareManager instance
     */
    public synchronized void init(HardWareManager hardwareManager)
    {
        logStatus();

        this.hardwareManager = hardwareManager;

        // Set event listener.
        if (BuildConfig.EMULATOR_MODE) unitStatus = UnitStatus.EMULATED;
        else
        {
            if (this.hardwareManager == null) unitStatus = UnitStatus.INITIALIZING;
            else
            {
                this.hardwareManager.setOnHareWareListener(new SanbotWhiteLightBrightnessListener());
                unitStatus = UnitStatus.STARTED;
            }
        }

        // Switch head light off.
        switchHeadLight(false);

        logStatus();
    }

    /**
     * Performs unit shutdown tasks.
     */
    public synchronized void shutdown()
    {
        hardwareManager = null;
        unitStatus = UnitStatus.SHUTDOWN;
        logStatus();
    }

    /**
     * Switch off all LEDs and the head light.
     *
     * @return  @c true if all operations were successful, @c false on failure
     */
    public synchronized boolean reset()
    {
        error = setLed(LED.PART_ALL, (byte)0, (byte)0, (byte)0);
        if (error.isSuccess()) error = switchHeadLight(false);
        return (error.isSuccess());
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Receives and handles white light brightness events (asynchronous query responses).
     */
    private class SanbotWhiteLightBrightnessListener implements WhiteLightBrightnessListener
    {
        /**
         * Called when a white light brightness event is received.
         *
         * If called with an invalid brightness value, or if no query is currently pending, the
         * method exits. A pending query reference is copied and cleared before relaying the event
         * so duplicate callbacks are ignored.
         *
         * @param   brightness  white head light brightness
         */
        @Override
        public void onWhiteLightBrightness(byte brightness)
        {
            BridgeLog.apievent("SanbotSDK", "WhiteLightBrightnessListener", "onWhiteLightBrightness", brightness);

            String refid;
            synchronized (SanbotLedUnit.this)
            {
                if ((brightness < 0) || (brightness > 3) || (headLightBrightnessQueryReference == null)) return;
                refid = headLightBrightnessQueryReference;
                headLightBrightnessQueryReference = null;
            }

            Map<String, Object> data = queryResultData(refid);
            data.put("brightness", brightness);
            publishEvent(BridgeProtocol.MODULE_LED, BridgeProtocol.ACTION_WHITELIGHT, data);
        }
    }
}
