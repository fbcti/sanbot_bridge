/**
 * @file        SanbotMotionUnit.java
 * @brief       Implements SanbotMotionUnit class.
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

import java.util.Map;

/**
 * Manages Sanbot SDK head, wing and wheel operations.
 *
 * This class implements methods that control robot motion. Moving the head is controlled by the
 * Sanbot @c HeadMotionManager API, moving the arms by the @c WingMotionManager API, and moving the
 * robot itself by the @c WheelMotionManager API. Additional modular motion (auto-charge, wander,
 * follow, and duck-run) is controlled by the @c ModularMotionManager API.
 *
 * The following motion managers are exposed by the Sanbot SDK but are not supported by the Sanbot
 * S1-B2 robot type and are therefore not used by this class:
 *
 * - @c DesktopMotionManager
 * - @c FingerMotionManager
 * - @c HandMotionManager
 * - @c WaistMotionManager
 *
 * @version     1.0.001
 * @date        4 Aug 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class SanbotMotionUnit extends BridgeUnit
{
    /** Source label used for log messages. */
//    private static final String TAG = SanbotMotionUnit.class.getSimpleName();

    /** Active Sanbot @c HeadMotionManager API instance. */
    private HeadMotionManager headMotionManager;

    /** Active Sanbot @c WingMotionManager API instance. */
    private WingMotionManager wingMotionManager;

    /** Active Sanbot @c WheelMotionManager API instance. */
    private WheelMotionManager wheelMotionManager;

    /** Active Sanbot @c ModularMotionManager API instance. */
    private ModularMotionManager modularMotionManager;

    /** Wheel motion event listener. */
    private final BridgeWheelMotionListener wheelMotionListener = new BridgeWheelMotionListener();

    /** Current wheel status. */
    String currentWheelStatus = "0";

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
     * The @c doDistanceMotion() function implemented by the Sanbot SDK @c WheelManager class
     * is called to instruct the robot to move for a specified duration, or to stop moving.
     *
     * @param   action          wheels action
     * @param   speed           speed with which to move wheels
     * @param   duration        motion duration in units of 100ms
     *
     * Possible values of @p action is specified by Sanbot SDK @c NoAngleWheelMotion class members:
     *
     * Allowed values of @p speed are from 1 to 10. If @p duration is 0 the robot will continue to
     * move until a stop command is received.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult moveRobotDuration(byte action, int speed, int duration)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wheelMotionManager == null) return DataResult.notavailable(FuncConstant.WHEELMOTION_MANAGER);

        NoAngleWheelMotion motion = new NoAngleWheelMotion(action, speed, duration);
        BridgeLog.apicall("SanbotSDK", "WheelMotionManager", "doNoAngleMotion", action, speed, duration);
        return DataResult.fromOperationResult(wheelMotionManager.doNoAngleMotion(motion));
    }

    /**
     * Moves the robot a specified distance forward, or stops moving forward.
     *
     * The @c doDistanceMotion() function implemented by the Sanbot SDK @c WheelManager class
     * is called to instruct the robot to move a specified distance forward, or to stop moving.
     *
     * @param   action          wheels action
     * @param   speed           speed with which to move wheels
     * @param   distance        number of centimeters to move robot
     *
     * Possible values of @p action is specified by Sanbot SDK  @c DistanceWheelMotion class members
     * (@c ACTION_FORWARD_RUN and @c ACTION_STOP_RUN). Allowed values of @p speed are from 1 to 10.
     * If @p distance is 0 the robot will continue to move until a stop command is received.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult moveRobotForward(byte action, int speed, int distance)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wheelMotionManager == null) return DataResult.notavailable(FuncConstant.WHEELMOTION_MANAGER);

        DistanceWheelMotion motion = new DistanceWheelMotion(action, speed, distance);
        BridgeLog.apicall("SanbotSDK", "WheelMotionManager", "doDistanceMotion", action, speed, distance);
        return DataResult.fromOperationResult(wheelMotionManager.doDistanceMotion(motion));
    }

    /**
     * Turns the robot left or right by the specified angle, or stops turning.
     *
     * The @c doRelativeAngleMotion() function implemented by the Sanbot SDK @c WheelManager class
     * is called to instruct the robot to turn either left or right by the specified angle relative
     * to the current orientation, or to stop turning.
     *
     * @param   action          wheels action
     * @param   speed           speed with which to move wheels
     * @param   angle           angle to rotate robot by
     *
     * Possible values of @p action is specified by Sanbot SDK @c RelativeAngleWheelMotion class
     * members (@c TURN_LEFT, @c TURN_RIGHT and @c TURN_STOP). Allowed values of @p speed are from 1
     * to 10.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult turnRobotByAngle(byte action, int speed, int angle)
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wheelMotionManager == null) return DataResult.notavailable(FuncConstant.WHEELMOTION_MANAGER);

        RelativeAngleWheelMotion motion = new RelativeAngleWheelMotion(action, speed, angle);
        BridgeLog.apicall("SanbotSDK", "WheelMotionManager", "doRelativeAngleMotion", action, speed, angle);
        return DataResult.fromOperationResult(wheelMotionManager.doRelativeAngleMotion(motion));
    }

    /**
     * Stops moving the robot.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult stopMovingRobot()
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (wheelMotionManager == null) return DataResult.notavailable(FuncConstant.WHEELMOTION_MANAGER);

        NoAngleWheelMotion motion = new NoAngleWheelMotion(NoAngleWheelMotion.ACTION_STOP, 1, 0);
        BridgeLog.apicall("SanbotSDK", "WheelMotionManager", "doNoAngleMotion", motion.getAction(), 1, 0);
        return DataResult.fromOperationResult(wheelMotionManager.doNoAngleMotion(motion));
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
     * @param   enable          @c true to enable wander modular motion mode
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
     * The @c doAbsoluteAngleMotion() function implemented by the Sanbot SDK @c HeadManager class is
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
     * Moves the head to the specified absolute position and lock head if requested.
     *
     * @param   action          lock action
     * @param   hAngle          horizontal head angle
     * @param   vAngle          vertical head angle
     *
     * Possible values of @p action are specified by Sanbot SDK @c LocateAbsoluteAngleHeadMotion
     * class members (@c ACTION_NO_LOCK, @c ACTION_HORIZONTAL_LOCK, @c ACTION_VERTICAL_LOCK and
     * @c ACTION_BOTH_LOCK). Allowed values of @p hangle are from 0 degrees (facing left) to 180
     * degrees (facing right) for horizontal motion. Allowed valued of @p vAngle are from 7 degrees
     * (facing down) to 30 (facing up) for vertical motion.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult moveHeadToAbslutePosition(byte action, int hAngle, int vAngle)
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
     * The @c doRelativeAngleMotion() function implemented by the Sanbot SDK @c HeadManager class is
     * called to instruct the robot to move the head horizontally or vertically @e by the specified
     * angle relative to the current position, or stop moving the head. The head will not move beyond
     * the extreme horizontal positions of 0 degrees (facing left) and 180 degrees (facing right)
     * and vertical positions of 7 degrees (facing down) or 30 degrees (facing up).
     *
     * @param   action          head action
     * @param   angle           angle to move head by
     *
     * Possible values of @p action are specified by @c RelativeAngleHeadMotion SDK class members
     * (@c ACTION_STOP, @c ACTION_LEFT, @c ACTION_UP, @c ACTION_DOWN, @c ACTON_LEFTUP,
     * @c ACTON_RIGHTUP, @c ACTON_LEFTDOWN and @c ACTON_RIGHTDOWN). Allowed values of @p angle are
     * from 0 to 180 for horizonal motion and 0 to 25 vertical for vertical motion.
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
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult stopMovingHead()
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();

        RelativeAngleHeadMotion motion = new RelativeAngleHeadMotion(RelativeAngleHeadMotion.ACTION_STOP, 0);
        BridgeLog.apicall("SanbotSDK", "HeadMotionManager", "doRelativeAngleMotion", motion.getAction(), 0);
        return DataResult.fromOperationResult(headMotionManager.doRelativeAngleMotion(motion));
    }

    /**
     * Resets the head to the central horizontal and vertical positions.
     *
     * The doResetMotion() implemented by the Sanbot SDK @c HeadMotionManager does not actually do
     * anything, so to reset the head it is moved first to the central vertical position and than
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

        // Move head to central horizonal position.
        int hAngle = SanbotMappings.mapHeadAngle(0, true);
        AbsoluteAngleHeadMotion hMotion = new AbsoluteAngleHeadMotion(AbsoluteAngleHeadMotion.ACTION_HORIZONTAL, hAngle);
        BridgeLog.apicall("SanbotSDK", "HeadMotionManager", "doAbsoluteAngleMotion", hMotion.getAction(), hAngle);
        return DataResult.fromOperationResult(headMotionManager.doAbsoluteAngleMotion(hMotion));
    }

    /**
     * Moves the head to the central horizontal position and lock the head motor.
     *
     * The @c dohorizontalCenterLockMotion() function implemented by the Sanbot SDK @c HeadManager
     * class is called to instruct the robot to move the head to the central horizontal position and
     * lock the head motor.
     *
     * @return  DataResult instance specifying operation result
     */
    @NonNull
    public synchronized DataResult lockHeadInCentralPosition()
    {
        if (unitStatus == UnitStatus.EMULATED) return DataResult.emulated();
        if (headMotionManager == null) return DataResult.notavailable(FuncConstant.HEADMOTION_MANAGER);

        BridgeLog.apicall("SanbotSDK", "HeadMotionManager", "dohorizontalCenterLockMotion");
        return DataResult.fromOperationResult(headMotionManager.dohorizontalCenterLockMotion());
    }

    /**
     * Moves one or both arms either up or down, or stops moving the arms.
     *
     * The @c doNoAngleMotion() function implemented by the Sanbot SDK @c WingManager class is
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
     * The @c doAbsoluteAngleMotion() function implemented by the Sanbot SDK @c WingManager @c class
     * called to instruct the robot to move one or both arms @e to the specified angle.
     *
     * @param   part            specifies which arm(s) to move
     * @param   speed           speed with which to move arm(s)
     * @param   angle           angle to move arm(s) to
     *
     * Possible values of @p part are specified by @c RelativeAngleWingMotion class members
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
     * The @c doRelativeAngleMotion() function implemented by the Sanbot SDK @c WingManager class is
     * called to instruct the robot to move one or both arms @e by the specified angle relative to
     * the current. The arms will not move beyond the extreme positions of 0 degrees (pointing up)
     * and 270 degrees (pointing backwards).
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
     * the wheel motion manager, and the unit status is set.
     *
     * @param   headMotionManager       active Sanbot @c HeadManager API instance
     * @param   wingMotionManager       active Sanbot @c WingManager API instance
     * @param   wheelMotionManager      active Sanbot @c WheelManager API instance
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
     */
    public synchronized void shutdown()
    {
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
     * @return  Java @c Map instance containing status data
     */
    @NonNull
    public Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = super.buildStatusData();
        data.put("wheels", toWheelStatus(currentWheelStatus));
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
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Converts the Sanbot SDK wheel status to a human-readable status.
     *
     * @param   wheelStatus     Sanbot SDK wheel status
     *
     * @return  human-readable string specifying wheel status
     *
     * @todo    14/07/2026 - Find out human-readable wheel statuses.
     */
    public static String toWheelStatus(@NonNull String wheelStatus)
    {
        switch (wheelStatus) {
            case "0":
                return "idle";
            case "1":
                return "active_1";
            case "3":
                return "turn_left";
            case "4":
                return "turn_right";
            case "5":
                return "active_5";
            default:
                return wheelStatus;
        }
    }

    /** Adds the modular motion status to the specified data map.
     *
     * @param   data            data map to which to add modular motion status
     * @param   mode            modular motion mode for which to add status
     * @param   status          respone to modular mtion status reqyest
     */
    private void setModularMotionStatus(Map<String, Object> data, String mode, OperationResult status)
    {
        if (status == null) data.put(mode, "<unknown>");
        else data.put(mode, ("1".equals(status.getResult())) ? true : false);
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Receives wheel status callback invocations.
     */
    private final class BridgeWheelMotionListener implements WheelMotionListener
    {
        /**
         * Called if a wheel status event is received.
         *
         * If the reported wheel status differs from the current wheel status the event is relayed
         * to the bridge unit.
         *
         * @param   s
         */
        @Override
        public void onWheelStatus(@NonNull String s)
        {
            BridgeLog.apievent("SanbotSDK", "WheelMotionListener", "onWheelStatus", s);

            if (s.equals(currentWheelStatus) == false)
            {
                currentWheelStatus = s;
                publishEvent(BridgeProtocol.MODULE_ROBOT, "wheels",
                    MapUtils.createMap("status", currentWheelStatus));
            }
        }
    }
}
