/**
 * @file        SanbotMotionUnit.java
 * @brief       Implements the SanbotMotionUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.robot.mapping.SanbotMappings;
import com.fbcti.sanbot.bridge.transport.BridgeProtocol;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.headmotion.AbsoluteAngleHeadMotion;
import com.sanbot.opensdk.function.beans.headmotion.LocateAbsoluteAngleHeadMotion;
import com.sanbot.opensdk.function.beans.headmotion.RelativeAngleHeadMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.DistanceWheelMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.RelativeAngleWheelMotion;
import com.sanbot.opensdk.function.beans.wing.AbsoluteAngleWingMotion;
import com.sanbot.opensdk.function.beans.wing.NoAngleWingMotion;
import com.sanbot.opensdk.function.beans.wing.RelativeAngleWingMotion;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.ModularMotionManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager.WheelMotionListener;
import com.sanbot.opensdk.function.unit.WingMotionManager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages Sanbot SDK head, wing and wheel operations.
 *
 * This class implements methods that control robot motion. Moving the head is controlled by the
 * Sanbot @c HeadMotionManager API, moving the arms by the @c WingMotionManager API, and moving the
 * robot itself by the @c WheelMotionManager API. Additional modular motion (auto-charge, wander,
 * follow, and duck-run) is controlled by the @c ModularMotionManager API.
 *
 * Wheel motion commands are queued, so each command is executed only when the operation requested
 * by a previous command has completed or timed out.
 *
 * The following motion managers are exposed by the Sanbot SDK but are not supported by the Sanbot
 * S1-B2 robot type and are therefore not used by this class:
 *
 * - @c DesktopMotionManager
 * - @c FingerMotionManager
 * - @c HandMotionManager
 * - @c WaistMotionManager
 *
 * @version     1.0.004
 * @date        5 Oct 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 * @since       1.0.001
 * @changelog
 * - NEW: @c nowait parameter added to force motion commands to be executed immediately (1.0.004)
 * - NEW: robot motion commands are queued (1.0.003)
 *
 * @todo    03/10/2026 - Improve calculation of motion timeouts.
 */
public final class SanbotMotionUnit extends BridgeUnit
{
    /** Source label used for log messages. */
    private static final String TAG = SanbotMotionUnit.class.getSimpleName();

    /** First value used for generated motion-command reference IDs. */
    private static final int COMMAND_COUNT_MIN = 1000;

    /** Active Sanbot @c HeadMotionManager API instance. */
    private HeadMotionManager headMotionManager;

    /** Active Sanbot @c WingMotionManager API instance. */
    private WingMotionManager wingMotionManager;

    /** Active Sanbot @c WheelMotionManager API instance. */
    private WheelMotionManager wheelMotionManager;

    /** Active Sanbot @c ModularMotionManager API instance. */
    private ModularMotionManager modularMotionManager;

    /** Listener for wheel status events. */
    private final BridgeWheelMotionListener wheelMotionListener = new BridgeWheelMotionListener();

    /** Serializes wheel motion commands and tracks their completion. */
    private final CommandQueue commandQueue = new CommandQueue();

    /** Counter used to generate wheel motion command reference id. */
    private final AtomicInteger counter = new AtomicInteger(COMMAND_COUNT_MIN);

    /** Most recently received wheel status code. */
    private volatile String currentWheelStatus = "0";

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new SanbotMotionUnit instance.
     *
     * The base class constructor is called to copy the callback host to a member variable.
     *
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public SanbotMotionUnit(BridgeEventHost eventHost)
    {
        super(eventHost);
    }

    /**
     * @name Motion Unit Operations
     *
     * @{ 
     */ 

    /**
     * Moves the robot for a specified time, or stops moving.
     *
     * The @c submit() method implemented by the CommandQueue class is called to execute the
     * @c WheelMotionManager SDK method to move the robot immediately, or postpone execution until
     * the current wheel command completes.
     *
     * @param   action          wheels action
     * @param   speed           speed with which to move wheels
     * @param   duration        motion duration in units of 100ms
     * @param   nowait          if @c true, discard waiting commands and interrupt the active motion
     *
     * Possible values of @p action are specified by the Sanbot SDK @c NoAngleWheelMotion class.
     *
     * Allowed values of @p speed are from 1 to 10. At SDK level, a @p duration of 0 makes the robot
     * continue moving until a stop command is received. The queue nevertheless assigns that command
     * a one-second bookkeeping timeout; expiration advances the queue but does not physically stop
     * the robot.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult moveRobotDuration(byte action, int speed, int duration, boolean nowait)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wheelMotionManager == null) return DataResult.notavailable(FuncConstant.WHEELMOTION_MANAGER);

        // Convert the 100-ms duration to seconds and allow approximately one second of grace.
        int timeout = (duration + 10)/10;

        NoAngleWheelMotion motion = new NoAngleWheelMotion(action, speed, duration);
        return commandQueue.submit(FuncConstant.WHEEL_MOTION_NO_ANGLE, motion, nowait, timeout);
    }

    /**
     * Moves the robot a specified distance forward, or stops moving forward.
     *
     * The @c submit() method implemented by the CommandQueue class is called to execute the
     * @c WheelMotionManager SDK method to move the robot immediately, or postpone execution until
     * the current wheel command completes.
     *
     * @param   action          wheels action
     * @param   speed           speed with which to move wheels
     * @param   distance        number of centimeters to move robot
     * @param   nowait          if @c true, discard waiting commands and interrupt the active motion
     *
     * Possible values of @p action are specified by Sanbot SDK @c DistanceWheelMotion class members
     * (@c ACTION_FORWARD_RUN and @c ACTION_STOP_RUN). Allowed values of @p speed are from 1 to 10.
     * If @p distance is 0 the robot will continue to move until a stop command is received.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult moveRobotForward(byte action, int speed, int distance, boolean nowait)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wheelMotionManager == null) return DataResult.notavailable(FuncConstant.WHEELMOTION_MANAGER);

        // Estimate the motion duration in seconds from distance and speed. Integer division can
        // produce zero, in which case the queue does not schedule a timeout.
        int timeout = distance/(10 + 4*speed);

        DistanceWheelMotion motion = new DistanceWheelMotion(action, speed, distance);
        return commandQueue.submit(FuncConstant.WHEEL_MOTION_DISTANCE, motion, nowait, timeout);
    }

    /**
     * Turns the robot left or right by the specified angle, or stops turning.
     *
     * The @c submit() method implemented by the CommandQueue class is called to execute the
     * @c WheelMotionManager SDK method to turn the robot immediately, or postpone execution until
     * the current wheel command completes.
     *
     * @param   action          wheels action
     * @param   speed           speed with which to move wheels
     * @param   angle           angle to rotate robot by
     * @param   nowait          if @c true, discard waiting commands and interrupt the active motion
     *
     * Possible values of @p action are specified by Sanbot SDK @c RelativeAngleWheelMotion class
     * members (@c TURN_LEFT, @c TURN_RIGHT and @c TURN_STOP). Allowed values of @p speed are from 1
     * to 10.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult turnRobotByAngle(byte action, int speed, int angle, boolean nowait)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wheelMotionManager == null) return DataResult.notavailable(FuncConstant.WHEELMOTION_MANAGER);

        // Estimate the turn duration in seconds and allow one second of grace.
        int timeout = angle/20 + 1;

        RelativeAngleWheelMotion motion = new RelativeAngleWheelMotion(action, speed, angle);
        return commandQueue.submit(FuncConstant.WHEEL_MOTION_RELATIVE_ANGLE, motion, nowait, timeout);
    }

    /**
     * Stops moving the robot.
     *
     * All waiting motion commands are discarded, queue processing is suspended, and a physical
     * stop command is sent to the Sanbot SDK. If the SDK rejects the stop command, the cancellation
     * flag is cleared so the active command remains tracked; commands already discarded from the
     * waiting queue are not restored.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult stopMovingRobot()
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wheelMotionManager == null) return DataResult.notavailable(FuncConstant.WHEELMOTION_MANAGER);

        // Discard waiting commands and prevent another command from starting during the stop.
        commandQueue.cancel();

        // Stop the current motion.
        NoAngleWheelMotion motion = new NoAngleWheelMotion(NoAngleWheelMotion.ACTION_STOP, 1, 0);
        BridgeLog.apicall("SanbotSDK", "WheelMotionManager", "doNoAngleMotion", motion.getAction(), 1, 0);
        DataResult result = DataResult.fromOperationResult(wheelMotionManager.doNoAngleMotion(motion));
        if (result.isFailure()) commandQueue.stopFailed();
        return result;
    }

    /**
     * Enables or disables modular motion wander mode.
     *
     * If enabled, the robot moves randomly through the room.
     *
     * @param   enable          @c true to enable wander modular motion mode
     * @param   userinfo        additional info for modular motion
     *
     * @return  DataResult instance specifying operation result
     *
     * @todo    22/09/2026 - Find out what @c userinfo is for.
     */
    @NonNull
    public synchronized DataResult switchRobotWanderMode(boolean enable, String userinfo)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (modularMotionManager == null) return DataResult.notavailable(FuncConstant.MODULARMOTION_MANAGER);

        if (StringUtils.isBlank(userinfo))
        {
            BridgeLog.apicall("SanbotSDK", "ModularMotionManager", "switchWander", enable);
            return DataResult.fromOperationResult(modularMotionManager.switchWander(enable));
        }
        BridgeLog.apicall("SanbotSDK", "ModularMotionManager", "switchWander", enable, userinfo);
        return DataResult.fromOperationResult(modularMotionManager.switchWander(enable, userinfo));
    }

    /**
     * Enables or disables modular motion follow mode.
     *
     * @param   enable          @c true to enable follow modular motion mode
     * @param   userinfo        additional info for modular motion
     *
     * @return  DataResult instance specifying operation result
     *
     * @todo    14/07/2026 - Find out what this mode actually is.
     * @todo    22/09/2026 - Find out what @c userinfo is for.
     */
    @NonNull
    public synchronized DataResult switchRobotFollowMode(boolean enable, String userinfo)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (modularMotionManager == null) return DataResult.notavailable(FuncConstant.MODULARMOTION_MANAGER);

        if (StringUtils.isBlank(userinfo))
        {
            BridgeLog.apicall("SanbotSDK", "ModularMotionManager", "switchFollow", enable);
            return DataResult.fromOperationResult(modularMotionManager.switchFollow(enable));
        }
        BridgeLog.apicall("SanbotSDK", "ModularMotionManager", "switchFollow", enable, userinfo);
        return DataResult.fromOperationResult(modularMotionManager.switchFollow(enable, userinfo));
    }

    /**
     * Enables or disables modular motion duck-run mode.
     *
     * If enabled, the robot constantly attempts to turn away from a live person close to the robot.
     *
     * @param   enable          @c true to enable duck-run modular motion mode
     * @param   userinfo        additional info for modular motion
     *
     * @return  DataResult instance specifying operation result
     *
     * @todo    22/09/2026 - Find out what @c userinfo is for.
     */
    @NonNull
    public synchronized DataResult switchRobotDuckRunMode(boolean enable, String userinfo)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (modularMotionManager == null) return DataResult.notavailable(FuncConstant.MODULARMOTION_MANAGER);

        if (StringUtils.isBlank(userinfo))
        {
            BridgeLog.apicall("SanbotSDK", "ModularMotionManager", "switchDuckRun", enable);
            return DataResult.fromOperationResult(modularMotionManager.switchDuckRun(enable));
        }
        BridgeLog.apicall("SanbotSDK", "ModularMotionManager", "switchDuckRun", enable, userinfo);
        return DataResult.fromOperationResult(modularMotionManager.switchDuckRun(enable, userinfo));
    }

    /**
     * Moves the head horizontally or vertically @e to the specified angle.
     *
     * The @c doAbsoluteAngleMotion() method implemented by the Sanbot SDK @c HeadMotionManager is
     * called to instruct the robot to move the head either horizontally or vertically @e to the
     * specified angle.
     *
     * @param   action          head action
     * @param   angle           angle to move head to
     *
     * Possible values of @p action are specified by Sanbot SDK @c AbsoluteAngleHeadMotion class
     * members (@c ACTION_VERTICAL and @c ACTION_HORIZONTAL). Allowed values of @p angle are from 0
     * degrees (facing left) to 180 degrees (facing right) for horizontal motion, and from 7 degrees
     * (facing down) to 30 (facing up) for vertical motion.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult moveHeadToAngle(byte action, int angle)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (headMotionManager == null) return DataResult.notavailable(FuncConstant.HEADMOTION_MANAGER);

        AbsoluteAngleHeadMotion motion = new AbsoluteAngleHeadMotion(action, angle);
        BridgeLog.apicall("SanbotSDK", "HeadMotionManager", "doAbsoluteAngleMotion", action, angle);
        return DataResult.fromOperationResult(headMotionManager.doAbsoluteAngleMotion(motion));
    }

    /**
     * Moves the head to the specified absolute position and locks either axis if requested.
     *
     * The @c doAbsoluteLocateMotion() method implemented by the Sanbot SDK @c HeadMotionManager is
     * called to instruct the robot to move the head to the specified horizontal and vertical
     * angles.
     *
     * @param   action          lock action
     * @param   hAngle          horizontal head angle
     * @param   vAngle          vertical head angle
     *
     * Possible values of @p action are specified by Sanbot SDK @c LocateAbsoluteAngleHeadMotion
     * class members (@c ACTION_NO_LOCK, @c ACTION_HORIZONTAL_LOCK, @c ACTION_VERTICAL_LOCK and
     * @c ACTION_BOTH_LOCK). Allowed values of @p hAngle are from 0 degrees (facing left) to 180
     * degrees (facing right) for horizontal motion. Allowed values of @p vAngle are from 7 degrees
     * (facing down) to 30 (facing up) for vertical motion.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult moveHeadToAbsolutePosition(byte action, int hAngle, int vAngle)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (headMotionManager == null) return DataResult.notavailable(FuncConstant.HEADMOTION_MANAGER);

        LocateAbsoluteAngleHeadMotion motion = new LocateAbsoluteAngleHeadMotion(action, hAngle, vAngle);
        BridgeLog.apicall("SanbotSDK", "HeadMotionManager", "doAbsoluteLocateMotion", action, hAngle, vAngle);
        return DataResult.fromOperationResult(headMotionManager.doAbsoluteLocateMotion(motion));
    }

    /**
     * Moves the head horizontally or vertically @e by the specified angle.
     *
     * The @c doRelativeAngleMotion() method implemented by the Sanbot SDK @c HeadMotionManager is
     * called to instruct the robot to move the head horizontally or vertically @e by the specified
     * angle relative to the current position, or stop moving the head. The head will not move
     * beyond the extreme horizontal positions of 0 degrees (facing left) and 180 degrees (facing
     * right) and vertical positions of 7 degrees (facing down) or 30 degrees (facing up).
     *
     * @param   action          head action
     * @param   angle           angle to move head by
     *
     * Possible values of @p action are specified by @c RelativeAngleHeadMotion SDK class members
     * (@c ACTION_STOP, @c ACTION_LEFT, @c ACTION_RIGHT, @c ACTION_UP, @c ACTION_DOWN,
     * @c ACTION_LEFTUP, @c ACTION_RIGHTUP, @c ACTION_LEFTDOWN and @c ACTION_RIGHTDOWN). Allowed
     * values of @p angle are from 0 to 180 degrees for horizontal motion and from 0 to 25 degrees
     * for vertical motion.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult moveHeadByAngle(byte action, int angle)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (headMotionManager == null) return DataResult.notavailable(FuncConstant.HEADMOTION_MANAGER);

        RelativeAngleHeadMotion motion = new RelativeAngleHeadMotion(action, angle);
        BridgeLog.apicall("SanbotSDK", "HeadMotionManager", "doDRelativeAngleMotion", action, angle);
        return DataResult.fromOperationResult(headMotionManager.doRelativeAngleMotion(motion));
    }

    /**
     * Stops moving the head.
     *
     * The @c doRelativeAngleMotion() method implemented by the Sanbot SDK @c HeadMotionManager is
     * called to instruct the robot to stop moving the head.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult stopMovingHead()
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (headMotionManager == null) return DataResult.notavailable(FuncConstant.HEADMOTION_MANAGER);

        RelativeAngleHeadMotion motion = new RelativeAngleHeadMotion(RelativeAngleHeadMotion.ACTION_STOP, 0);
        BridgeLog.apicall("SanbotSDK", "HeadMotionManager", "doRelativeAngleMotion", motion.getAction(), 0);
        return DataResult.fromOperationResult(headMotionManager.doRelativeAngleMotion(motion));
    }

    /**
     * Resets the head to the central horizontal and vertical positions.
     *
     * The @c doResetMotion() method implemented by the Sanbot SDK @c HeadMotionManager does not
     * actually move the head, so the head is moved first to the central vertical position and then
     * to the central horizontal position.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult resetHead()
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();

        // Move head to central vertical position.
        int vAngle = SanbotMappings.mapHeadAngle(0, false);
        AbsoluteAngleHeadMotion vMotion = new AbsoluteAngleHeadMotion(AbsoluteAngleHeadMotion.ACTION_VERTICAL, vAngle);
        BridgeLog.apicall("SanbotSDK", "HeadMotionManager", "doAbsoluteAngleMotion", vMotion.getAction(), vAngle);
        DataResult vResult = DataResult.fromOperationResult(headMotionManager.doAbsoluteAngleMotion(vMotion));
        if (vResult.isFailure()) return vResult;

        // Move head to central horizontal position.
        int hAngle = SanbotMappings.mapHeadAngle(0, true);
        AbsoluteAngleHeadMotion hMotion = new AbsoluteAngleHeadMotion(AbsoluteAngleHeadMotion.ACTION_HORIZONTAL, hAngle);
        BridgeLog.apicall("SanbotSDK", "HeadMotionManager", "doAbsoluteAngleMotion", hMotion.getAction(), hAngle);
        return DataResult.fromOperationResult(headMotionManager.doAbsoluteAngleMotion(hMotion));
    }

    /**
     * Moves the head to the central horizontal position and locks the head motor.
     *
     * The @c dohorizontalCenterLockMotion() method implemented by the Sanbot SDK
     * @c HeadMotionManager is called to instruct the robot to move the head to the central
     * horizontal position and lock the head motor.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult lockHeadInCentralPosition()
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (headMotionManager == null) return DataResult.notavailable(FuncConstant.HEADMOTION_MANAGER);

        BridgeLog.apicall("SanbotSDK", "HeadMotionManager", "doHorizontalCenterLockMotion");
        return DataResult.fromOperationResult(headMotionManager.dohorizontalCenterLockMotion());
    }

    /**
     * Moves one or both arms either up or down, or stops moving the arms.
     *
     * The @c doNoAngleMotion() method implemented by the Sanbot SDK @c WingMotionManager is
     * called to instruct the robot to move one or both arms, to stop moving the wing(s) or to reset
     * the arms to the default position. Arms continue to move until the extreme position of 0
     * degrees (pointing up) or 270 degrees (pointing backwards) is reached, or a stop command is
     * received.
     *
     * @param   part            specifies which arm(s) to move
     * @param   speed           speed with which to move arm(s)
     * @param   action          wing action
     *
     * Possible values of @p part and @p action are specified by @c NoAngleWingMotion SDK class
     * members (@c PART_LEFT, @c PART_RIGHT, @c PART_BOTH, @c ACTION_UP, @c ACTION_DOWN,
     * @c ACTION_STOP and @c ACTION_RESET). Allowed values of @p speed are from 1 to 8.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult moveArms(byte part, int speed, byte action)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wingMotionManager == null) return DataResult.notavailable(FuncConstant.WINGMOTION_MANAGER);

        NoAngleWingMotion motion = new NoAngleWingMotion(part, speed, action);
        BridgeLog.apicall("SanbotSDK", "WingMotionManager", "doNoAngleMotion", part, speed, action);
        return DataResult.fromOperationResult(wingMotionManager.doNoAngleMotion(motion));
    }

    /**
     * Moves one or both arms @e to the specified angle.
     *
     * The @c doAbsoluteAngleMotion() method implemented by the Sanbot SDK @c WingMotionManager is
     * called to instruct the robot to move one or both arms @e to the specified angle.
     *
     * @param   part            specifies which arm(s) to move
     * @param   speed           speed with which to move arm(s)
     * @param   angle           angle to move arm(s) to
     *
     * Possible values of @p part are specified by @c AbsoluteAngleWingMotion class members
     * (@c PART_LEFT, @c PART_RIGHT and @c PART_BOTH). Allowed values of @p speed are from 1 to 8,
     * allowed values of @p angle are from 0 degrees (pointing up) to 270 degrees (point backwards).
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult moveArmsToAngle(byte part, int speed, int angle)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wingMotionManager == null) return DataResult.notavailable(FuncConstant.WINGMOTION_MANAGER);

        AbsoluteAngleWingMotion motion = new AbsoluteAngleWingMotion(part, speed, angle);
        BridgeLog.apicall("SanbotSDK", "WingMotionManager", "doAbsoluteAngleMotion", part, speed, angle);
        return DataResult.fromOperationResult(wingMotionManager.doAbsoluteAngleMotion(motion));
    }

    /**
     * Moves one or both arms @e by the specified angle.
     *
     * The @c doRelativeAngleMotion() method implemented by the Sanbot SDK @c WingMotionManager is
     * called to instruct the robot to move one or both arms @e by the specified angle relative to
     * the current position. The arms will not move beyond the extreme positions of 0 degrees
     * (pointing up) and 270 degrees (pointing backwards).
     *
     * @param   part            specifies which arm(s) to move
     * @param   speed           speed with which to move arm(s)
     * @param   action          arm action
     * @param   angle           angle to move arm(s) by
     *
     * @return  DataResult instance specifying operation result
     *
     * Possible values of @p part and @p action are specified by @c RelativeAngleWingMotion SDK
     * class members (@c PART_LEFT, @c PART_RIGHT, @c PART_BOTH, @c ACTION_UP and @c ACTION_DOWN).
     * Allowed values of @p speed are from 1 to 8, allowed values of @p angle are from 0 degrees
     * (pointing up) to 270 degrees (point backwards).
     */
    @NonNull
    public synchronized DataResult moveArmsByAngle(byte part, int speed, byte action, int angle)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wingMotionManager == null) return DataResult.notavailable(FuncConstant.WINGMOTION_MANAGER);

        RelativeAngleWingMotion motion = new RelativeAngleWingMotion(part, speed, action, angle);
        BridgeLog.apicall("SanbotSDK", "WingMotionManager", "doRelativeAngleMotion", part, speed, action, angle);
        return DataResult.fromOperationResult(wingMotionManager.doRelativeAngleMotion(motion));
    }

    /**
     * Stops moving one or both arms.
     *
     * The @c doNoAngleMotion() method implemented by the Sanbot SDK @c WingMotionManager is
     * called to instruct the robot to stop moving one or both arms.
     *
     * @param   part            specifies which arm(s) to stop
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult stopMovingArms(Byte part)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();

        NoAngleWingMotion motion = new NoAngleWingMotion(part, 1, NoAngleWingMotion.ACTION_STOP);
        BridgeLog.apicall("SanbotSDK", "WingMotionManager", "doNoAngleMotion", part, 1, NoAngleWingMotion.ACTION_STOP);
        return DataResult.fromOperationResult(wingMotionManager.doNoAngleMotion(motion));
    }

    /**
     * Resets one or both arms to the downward position.
     *
     * The @c doNoAngleMotion() method implemented by the Sanbot SDK @c WingMotionManager is
     * called to instruct the robot to reset the position of one or both arms.
     *
     * @param   part            specifies which arm(s) to reset
     * @param   speed           speed with which to move arm(s)
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult resetArms(Byte part, int speed)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();

        NoAngleWingMotion motion = new NoAngleWingMotion(part, speed, NoAngleWingMotion.ACTION_RESET);
        BridgeLog.apicall("SanbotSDK", "WingMotionManager", "doNoAngleMotion", part, 1, NoAngleWingMotion.ACTION_RESET);
        return DataResult.fromOperationResult(wingMotionManager.doNoAngleMotion(motion));
    }

    /** @} */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * The active head motion manager, wing motion manager, wheel motion manager and modular motion
     * manager are copied to member variables, the wheel status event listener is registered with
     * the wheel motion manager, the cached wheel status is reset to idle, and the unit status is
     * derived from emulator mode and manager availability. After reinitialization, the first timed
     * wheel command lazily creates a new command timer if the previous timer was shut down.
     *
     * @param   headMotionManager       active Sanbot @c HeadMotionManager API instance
     * @param   wingMotionManager       active Sanbot @c WingMotionManager API instance
     * @param   wheelMotionManager      active Sanbot @c WheelMotionManager API instance
     * @param   modularMotionManager    active Sanbot @c ModularMotionManager API instance
     */
    public void init(HeadMotionManager headMotionManager, WingMotionManager wingMotionManager, WheelMotionManager wheelMotionManager, ModularMotionManager modularMotionManager)
    {
        logStatus();

        this.headMotionManager = headMotionManager;
        this.wingMotionManager = wingMotionManager;
        this.wheelMotionManager = wheelMotionManager;
        this.modularMotionManager = modularMotionManager;

        // Add listener for wheel motion events.
        currentWheelStatus = "0";
        if (wheelMotionManager != null) wheelMotionManager.setWheelMotionListener(wheelMotionListener);

        // Set unit status.
        if (BuildConfig.EMULATOR_MODE) unitStatus = UnitStatus.EMULATED;
        else unitStatus = ((headMotionManager != null) && (wingMotionManager != null) && (wheelMotionManager != null) && (modularMotionManager != null))
            ? UnitStatus.STARTED : UnitStatus.INITIALIZING;

        logStatus();
    }

    /**
     * Performs unit shutdown tasks.
     *
     * Discards all active and waiting command bookkeeping, permanently cancels the current timeout
     * timer, releases the SDK manager references, resets the cached wheel status and marks the unit
     * as shut down. This method does not send a physical stop command to the robot; callers that
     * require motion to stop must request that before shutdown.
     */
    public synchronized void shutdown()
    {
        commandQueue.shutdown();
        headMotionManager = null;
        wingMotionManager = null;
        wheelMotionManager = null;
        modularMotionManager = null;
        currentWheelStatus = "0";
        unitStatus = UnitStatus.SHUTDOWN;
        logStatus();
    }

    /**
     * Stops all motion and resets arms and head to default positions.
     *
     * Operations are performed sequentially in the following order: wheels, head, arms, head reset
     * and arm reset. Processing stops at the first failure, which is retained in the unit's
     * inherited error field.
     *
     * @return  @c true if all operations were successful, @c false on failure
     */
    public boolean reset()
    {
        error = stopMovingRobot();
        if (error.isSuccess()) error = stopMovingHead();
        if (error.isSuccess()) error = stopMovingArms(NoAngleWingMotion.PART_BOTH);
        if (error.isSuccess()) error = resetHead();
        if (error.isSuccess()) error = resetArms(NoAngleWingMotion.PART_BOTH, 1);
        return (error.isSuccess());
    }

    /**
     * Builds a data map containing current unit status data.
     *
     * In addition to the base unit status, the map contains the latest human-readable wheel status,
     * the number of waiting commands (excluding the active command), and the available modular
     * motion statuses.
     *
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    public Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = super.buildStatusData();
        data.put("wheelStatus", toWheelStatus(currentWheelStatus));
        data.put("commandsQueued", commandQueue.size());
        if (modularMotionManager != null)
        {
            setModularMotionStatus(data, "charging", modularMotionManager.getAutoChargeStatus());
            setModularMotionStatus(data, "wandering", modularMotionManager.getWanderStatus());
            setModularMotionStatus(data, "following", modularMotionManager.getFollowStatus());
            setModularMotionStatus(data, "duckrunning", modularMotionManager.getDuckRunStatus());
        }
        return data;
    }

    /***********************************************************************************************
     * STATUS AND HELPER METHODS
     **********************************************************************************************/

    /**
     * Converts the Sanbot SDK wheel status to a human-readable status.
     *
     * @param   wheelStatus     Sanbot SDK wheel status
     *
     * @return  mapped human-readable status, or the original value if the code is unknown
     */
    public static String toWheelStatus(@NonNull String wheelStatus)
    {
        switch (wheelStatus) {
            case "0":
                return "idle";
            case "1":
                return "forward";
            case "2":
                return "backward";
            case "3":
                return "turn_left";
            case "4":
                return "turn_right";
            case "5":
                return "braking";
            default:
                return wheelStatus;
        }
    }

    /**
     * Adds the modular motion status to the specified data map.
     *
     * @param   data            data map to which to add modular motion status
     * @param   mode            modular motion mode for which to add status
     * @param   status          response to modular motion status request
     */
    private void setModularMotionStatus(Map<String, Object> data, String mode, OperationResult status)
    {
        if (status == null) data.put(mode, "<unknown>");
        else data.put(mode, ("1".equals(status.getResult())));
    }

    /***********************************************************************************************
     * NESTED CLASSES
     **********************************************************************************************/

    /**
     * Receives wheel-status callbacks from the Sanbot SDK.
     */
    private final class BridgeWheelMotionListener implements WheelMotionListener
    {
        /**
         * Processes a reported wheel-status value.
         *
         * If the reported wheel status differs from the current wheel status the event is relayed
         * to the bridge unit and the cached status is updated. A transition to status @c "0"
         * completes the active command or pending cancellation and may start the next waiting
         * command. A stop event is published only when that completion leaves the queue idle.
         * Repeated reports of the same status are ignored.
         *
         * @param   s           string representation of numerical wheel status
         */
        @Override
        public void onWheelStatus(@NonNull String s)
        {
            BridgeLog.apievent("SanbotSDK", "WheelMotionListener", "onWheelStatus", s);

           if (s.equals(currentWheelStatus) == false)
            {
                currentWheelStatus = s;

                // Publish the raw SDK wheel-status code.
                publishEvent(BridgeProtocol.MODULE_ROBOT, BridgeProtocol.EVENT_MOVE, MapUtils.createMap("status", currentWheelStatus));

                // An idle transition completes the current queue operation.
                if ((s.equals("0")) && (commandQueue.completeCommand(null))) publishEvent(BridgeProtocol.MODULE_ROBOT, BridgeProtocol.EVENT_STOP);
            }
        }
    }

    /**
     * Helper class for serializing wheel motion commands.
     *
     * Submitting a wheel motion command directly to the Sanbot @c WheelMotionManager API while
     * another is running interrupts the earlier motion operation. To preserve every request, this
     * class implements an @c active member variable that represents the current command, and stores
     * consecutive commands in a queue. The active command is retained after submitted to the Sanbot
     * @c WheelMotionManager API until either the wheel status changes to idle or a timeout expires.
     * Timeout completion advances only queue bookkeeping; it does not send a physical stop command
     * to the robot.
     *
     * A command whose @c nowait flag is set interrupts this sequence only when another command is
     * active. All waiting commands are discarded, the active command's timeout is cancelled, and
     * the new command replaces it immediately. The new SDK call itself interrupts the physical
     * operation; the queue does not send a separate stop command. If no command is active,
     * @c nowait has no effect.
     *
     * Calling cancel() empties the waiting queue and marks the active command as being cancelled.
     * The cancellation state is finalized when the wheel status changes to idle or a timeout
     * expires. Queue mutations and compound state.
     */
    private class CommandQueue
    {
        /**
         * Queue containing commands waiting to be executed.
         *
         * If a command is executed it is taken from the queue and copied to the @c active member
         * variable, so the command being executed is not in the queue.
         */
        private final Deque<Command> queue = new ArrayDeque<>();

        /** Command currently being executed. */
        private Command active;

        /**
         * Command timeout.
         *
         * An optional timeout can be specified for a command to make sure the queue is not blocked
         * in case the event signaling the wheel status changes to idle is never received.
         */
        private Timer timer = new Timer("SanbotMotionTimeout", true);

        /** Task performed if command timeout expires. */
        private TimerTask timeoutTask;

        /**
         * Flag specifying command execution is cancelled.
         *
         * New commands may be queued while this flag is set, but none are started. The flag is
         * cleared when the wheel status changes to idle or a timeout expires, or by calling
         * stopFailed() if the Sanbot @c WheelMotionManager API rejects a stop request.
         */
        private boolean cancelling;

        /**
         * Submits a new command to the queue.
         *
         * A new command is created from the specified motion type and parameters. If @p nowait is
         * true and a command is active, all waiting commands are discarded and the new command
         * replaces the active command. Otherwise, if a command is active or cancellation is in
         * progress, the new command is appended to the waiting queue and a successful
         * @c operation_queued result is returned. If the queue is idle, the new command is made
         * active normally; in that case @p nowait has no effect.
         *
         * @param   type        motion type as defined in Sanbot @c FuncConstant API
         * @param   params      SDK motion bean whose concrete type is determined by @p type
         * @param   nowait      if @c true, discard waiting commands and interrupt the active command
         * @param   timeout     command timeout in seconds; a non-positive value disables it
         *
         * @return  DataResult instance specifying operation result
         */
        private DataResult submit(int type, Object params, boolean nowait, int timeout)
        {
            Command command;
            synchronized (this)
            {
                command = new Command(type, params, nowait, timeout);
                if ((command.nowait) && (active != null))
                {
                    BridgeLog.debug(TAG, "Motion command " + command.id + " interrupts active command "
                        + active.id + " and discards " + queue.size() + " queued commands");
                    queue.clear();
                    cancelTimeout();
                    cancelling = false;
                }
                else if ((active != null) || (cancelling))
                {
                    // A command is in progress, so the new command is queued.
                    queue.addLast(command);
                    return DataResult.success("operation_queued", command.getData());
                }

                // Claim the command before releasing the queue monitor and calling the SDK.
                active = command;
            }

            // Do not hold the queue monitor while startCommands() calls the external SDK.
            return startCommands(command);
        }

        /**
         * Clears all queued commands and marks the active command as being cancelled.
         *
         * The waiting queue is emptied. The cancellation flag is set only when there is an active
         * command.
         */
        private synchronized void cancel()
        {
            queue.clear();
            cancelling = (active != null);
        }

        /**
         * Shuts down the queue bookkeeping and timeout timer.
         *
         * The current timeout task and timer are cancelled, all active and waiting command state is
         * discarded, and cancellation state is cleared. No physical stop command is sent to the
         * robot.
         */
        private synchronized void shutdown()
        {
            cancelTimeout();
            if (timer != null)
            {
                timer.cancel();
                timer = null;
            }
            queue.clear();
            active = null;
            cancelling = false;
        }

        /**
         * Returns a best-effort snapshot of the number of waiting commands.
         *
         * @return  number of commands waiting in the queue
         */
        private int size()
        {
            return queue.size();
        }

        /**
         * Starts commands until one is accepted or no pending commands remain.
         *
         * The supplied command is executed only while it is still active and cancellation is not in
         * progress. If the command is accepted by the Sanbot @c WheelMotionManager API accepts the
         * command, a timeout is scheduled when its timeout value is positive and the command
         * remains active until the wheel status changes to idle or a timeout expires.
         *
         * A command rejected by the SDK cannot produce a completion callback, so it is removed
         * immediately and, provided ownership and cancellation state have not changed, processing
         * continues with the next waiting command. The method retains the result of the first
         * supplied command even if it subsequently attempts queued commands.
         *
         * @param   command       first command to execute
         *
         * @return  DataResult instance specifying result of command submission request
         */
        @NonNull
        private DataResult startCommands(Command command)
        {
            DataResult result = null;

            // Skip rejected commands until one is accepted or no waiting command remains.
            while (command != null)
            {
                synchronized (this)
                {
                    // Selection and SDK execution are separate; recheck ownership before execution.
                    if ((active != command) || (cancelling))
                    {
                        if (result == null) result = DataResult.failure("operation_cancelled");
                        break;
                    }
                }

                DataResult thisResult;
                try
                {
                    thisResult = command.execute();
                    if (thisResult == null) thisResult = DataResult.failure("empty motion command result");
                    BridgeLog.debug(TAG, "Result of motion command " + command.id + " is " + thisResult.getCodeAsString());
                }
                catch (RuntimeException e)
                {
                    thisResult = DataResult.failure("motion command failed", e.getClass().getSimpleName());
                }
                if (result == null) result = thisResult;

                // An accepted command stays active pending a callback or timeout.
                if (thisResult.isSuccess())
                {
                    startTimeout(command);
                    break;
                }

                synchronized (this)
                {
                    // A concurrent callback or cancellation may already have changed queue state.
                    if (active != command) break;

                    active = null;
                    if (cancelling) break;

                    command = pollFirst();
                    if (command != null) active = command;
                }
            }

            return (result != null) ? result : DataResult.failure("motion command was not started");
        }

        /**
         * Completes the specified command and starts the next queued command, if present.
         *
         * A non-null command identifies the command whose timeout fired. Completion is ignored if
         * that command is no longer active, preventing an obsolete timeout from completing a newer
         * command. A null value is used by the wheel-idle callback and completes the current active
         * command or pending cancellation; this assumes the idle transition belongs to the command
         * currently tracked as active. After clearing the current state, the first waiting command
         * is registered as active under the monitor and then started outside the monitor.
         *
         * @param   command     command expected to be active, or @c null for a wheel-idle callback
         *
         * @return  @c true if completion was handled and the queue is idle afterward
         */
        private boolean completeCommand(Command command)
        {
            Command next;
            synchronized (this)
            {
                if ((active == null) && (cancelling == false)) return false;
                if (command != null)
                {
                    if (active != command) return false;
                    BridgeLog.warning(TAG, "Motion command " +  command.id + " timed out after " + command.timeout + " seconds");
                }
                cancelTimeout();
                active = null;
                cancelling = false;
                next = pollFirst();

                // Claim the next command before releasing the monitor and calling the SDK.
                if (next != null) active = next;
            }

            // Execute the claimed command without holding the queue monitor.
            if (next != null) startCommands(next);

            synchronized (this)
            {
                return (active == null) && (queue.isEmpty()) && (cancelling == false);
            }
        }

        /**
         * Restores normal queue processing if a stop request failed.
         *
         * The cancellation flag is cleared. If command submission failed during cancellation and
         * consequently left no active command, the next waiting command is claimed and started.
         * Otherwise, the existing active command remains responsible for later completion.
         */
        private void stopFailed()
        {
            Command next = null;
            synchronized (this)
            {
                if (cancelling == false) return;

                cancelling = false;
                if (active == null)
                {
                    next = pollFirst();
                    if (next != null) active = next;
                }
            }

            if (next != null) startCommands(next);
        }

        /**
         * Starts the timeout for the active command.
         *
         * No timeout is scheduled when the timeout value is non-positive, the supplied command is
         * no longer active, or cancellation is in progress. When the timeout expires,
         * {@link #completeCommand(Command)} completes it only if it is still active. This identity
         * check is required because cancelling a TimerTask cannot stop a task that already started.
         * Expiration advances the queue but does not physically stop the robot.
         *
         * @param   command     command for which to set timeout
         */
        private synchronized void startTimeout(@NonNull Command command)
        {
            if ((command.timeout <= 0) || (active != command) || (cancelling)) return;
            cancelTimeout();

            BridgeLog.debug(TAG, "Scheduling timeout for motion command " + command.id + " after "
                + command.timeout + " seconds");

            timeoutTask = new TimerTask()
            {
                @Override
                public void run()
                {
                    BridgeLog.debug(TAG, "Timeout expired for motion command " + command.id);
                    if (completeCommand(command)) publishEvent(BridgeProtocol.MODULE_ROBOT, BridgeProtocol.EVENT_STOP);
                }
            };

            // Timer.cancel() is permanent; recreate the timer when this queue is reused after
            // shutdown and reinitialization.
            if (timer == null) timer = new Timer("SanbotMotionTimeout", true);
            timer.schedule(timeoutTask, command.timeout*1000L);
        }

        /**
         * Cancels and forgets the currently registered timeout task.
         *
         * A task that has already started may still call completeCommand(); the expected-command
         * identity check makes that late invocation harmless.
         */
        private void cancelTimeout()
        {
            if (timeoutTask == null) return;
            timeoutTask.cancel();
            timeoutTask = null;
        }

        /**
         * Removes and returns the next waiting command.
         *
         * Callers must hold this object's monitor while invoking this helper.
         *
         * @return  next pending command, or @c null if the queue is empty
         */
        private Command pollFirst()
        {
            Command command = queue.pollFirst();
            if (command != null)  BridgeLog.debug(TAG, "Dequeued motion command " + command.id + ", " +
                queue.size() + " remain in queue");
            return command;
        }
    }

    /**
     * Helper class for storing a single motion command.
     */
    private class Command
    {
        /** Motion type defined by the Sanbot @c FuncConstant API. */
        final int type;

        /** Motion parameters. */
        final Object params;

        /** True if this command may replace an active command instead of waiting in the queue. */
        final boolean nowait;

        /** Timeout in seconds; a non-positive value disables timeout scheduling. */
        final int timeout;

        /** Reference id returned to clients when this command is queued. */
        final String id;

        /**
         * Constructs a new Command instance.
         *
         * The motion type, parameters and timeout are copied to member variables, and a reference
         * id is generated from the shared command counter.
         *
         * @param   type        motion type as defined in Sanbot @c FuncConstant API
         * @param   params      SDK motion bean whose concrete type is determined by @p type
         * @param   nowait      if @c true, discard waiting commands and interrupt the active command
         * @param   timeout     command timeout in seconds; a non-positive value disables it
         */
        private Command(int type, Object params, boolean nowait, int timeout)
        {
            this.type = type;
            this.params = params;
            this.nowait = nowait;
            this.timeout = timeout;

            if (counter.get() == Integer.MAX_VALUE) counter.set(COMMAND_COUNT_MIN);
            this.id = String.format("%d", counter.getAndIncrement());
        }

        /**
         * Executes the command.
         *
         * Invokes the Sanbot @c WheelMotionManager API method corresponding to the motion type.
         * Unsupported types produce a failure result instead of calling the SDK.
         *
         * @return  DataResult instance specifying operation result
         */
        private DataResult execute()
        {
            BridgeLog.debug(TAG, "Execute motion command " + id);
            switch (type)
            {
                case FuncConstant.WHEEL_MOTION_NO_ANGLE:
                    NoAngleWheelMotion noAngleWheelMotion = (NoAngleWheelMotion)params;
                    BridgeLog.apicall("SanbotSDK", "WheelMotionManager", "doNoAngleMotion",
                        noAngleWheelMotion.getAction(), noAngleWheelMotion.getSpeed(),
                        intVal(noAngleWheelMotion.getLsbDuration(), noAngleWheelMotion.getMsbDuration()));
                    return DataResult.fromOperationResult(wheelMotionManager.doNoAngleMotion(noAngleWheelMotion));
                case FuncConstant.WHEEL_MOTION_DISTANCE:
                    DistanceWheelMotion distanceWheelMotion = (DistanceWheelMotion)params;
                    BridgeLog.apicall("SanbotSDK", "WheelMotionManager", "doDistanceMotion",
                        distanceWheelMotion.getAction(), distanceWheelMotion.getSpeed(),
                        intVal(distanceWheelMotion.getLsbDistance(), distanceWheelMotion.getMsbDistance()));
                    return DataResult.fromOperationResult(wheelMotionManager.doDistanceMotion(distanceWheelMotion));
                case FuncConstant.WHEEL_MOTION_RELATIVE_ANGLE:
                    RelativeAngleWheelMotion relativeAngleWheelMotion = (RelativeAngleWheelMotion)params;
                    BridgeLog.apicall("SanbotSDK", "WheelMotionManager", "doRelativeAngleMotion",
                        relativeAngleWheelMotion.getAction(), relativeAngleWheelMotion.getSpeed(),
                        intVal(relativeAngleWheelMotion.getLsbAngle(), relativeAngleWheelMotion.getMsbAngle()));
                    return DataResult.fromOperationResult(wheelMotionManager.doRelativeAngleMotion(relativeAngleWheelMotion));
            }
            return DataResult.failure("unsupported motion command", type);
        }

        /**
         * Returns command details suitable for an @c operation_queued response.
         *
         * @return  map containing the reference ID, motion type and timeout in seconds
         */
        @NonNull
        public Map<String, Object> getData()
        {
            return MapUtils.createMap("refId", id, "type", type, "timeout", timeout);
        }

        /**
         * Combines two unsigned bytes into a 16-bit little-endian integer.
         *
         * @param   lsb         least-significant byte
         * @param   msb         most-significant byte
         *
         * @return  value represented by @p lsb and @p msb
         */
        private int intVal(byte lsb, byte msb)
        {
            return (lsb & 0xFF) + 256*(msb & 0xFF);
        }
    }
}
