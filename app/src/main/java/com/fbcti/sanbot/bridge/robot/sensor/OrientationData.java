/**
 * @file        OrientationData.java
 * @brief       Implements OrientationData class.
 */
package com.fbcti.sanbot.bridge.robot.sensor;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.util.MapUtils;

import java.util.Map;

/**
 * Stores latest orientation data and determines if updates should be relayed.
 *
 * This helper class stores the most recent yaw, pitch, and roll values for info requests. The same
 * object also tracks the last relayed values so events can be filtered using the configured
 * sensitivity.
 *
 * @version     1.0.001
 * @date        9 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class OrientationData
{
    /** Default sensitivity for relaying orientation events. */
    private static final int DEFAULT_SENSITIVITY = 90;

    /** Minimum allowed orientation event sensitivity. */
    private static final int MIN_SENSITIVITY = 0;

    /** Maximum allowed orientation event sensitivity. */
    private static final int MAX_SENSITIVITY = 100;

    /** Meaningful wrapped yaw range used for normalized orientation deltas. */
    private static final double YAW_RANGE_DEGREES = 180.0;

    /** Meaningful pitch range used for normalized orientation deltas. */
    private static final double PITCH_RANGE_DEGREES = 60.0;

    /** Meaningful roll range used for normalized orientation deltas. */
    private static final double ROLL_RANGE_DEGREES = 60.0;

    /** Current orientation data snapshot. */
    private final OrientationSnapshot currentSnapshot = new OrientationSnapshot();

    /**
     * Most recently relayed orientation data snapshot.
     *
     * This is used as the comparison baseline for deciding if newly received data differs enough
     * from last sent data to justify relaying a new event.
     */
    private final OrientationSnapshot lastSentSnapshot = new OrientationSnapshot();

    /** Configured event sensitivity. */
    private int sensitivity = DEFAULT_SENSITIVITY;

    /** Flag specifying if an event has been relayed already. */
    private boolean isSent = false;

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Updates orientation data snapshot and returns event payload if sensitivity threshold is met.
     *
     * This method must be called if a orientation sensor event is received. It updates the latest
     * orientation data snapshot by replacing the cached yaw, pitch and roll angles by the specified
     * values. If no event has yet been relayed, or the difference between the last sent snapshot
     * and the updated snapshot exceeds the threshold determined by the configured sensitivity, an
     * event containing the orientation data is relayed.
     *
     * @param   yaw             angle of rotation around vertical axis (cardinal direction)
     * @param   pitch           angle of up-down rotation (tilt)
     * @param   roll            angle of left-right rotation (bank)
     *
     * @return  event payload, or @c null if the event should be suppressed
     */
    @Nullable
    public synchronized Map<String, Object> update(double yaw, double pitch, double roll)
    {
        // Update latest orientation data snapshot.
        currentSnapshot.record(yaw, pitch, roll);

        // Check if current data differs enough from last sent data.
        boolean isDifferent = (isSent) ? checkIfDifferent(currentSnapshot, lastSentSnapshot) : true;
        if (isDifferent == false) return null;

        // Create map containing event data.
        Map<String, Object> data = toMap();
        if (data == null) return null;

        isSent = true;
        lastSentSnapshot.copyFrom(currentSnapshot);
        return data;
    }

    /**
     * Resets cached orientation data.
     *
     * This clears both snapshots and the sent-state flag, so the next received sensor value is
     * treated as the first value in a fresh comparison cycle.
     */
    public synchronized void reset()
    {
        currentSnapshot.reset();
        lastSentSnapshot.reset();
        isSent = false;
    }

    /**
     * Sets orientation event sensitivity.
     *
     * Higher values of the sensitivity requires a smaller relative difference between current and
     * last sent orientation data snapshots before a orientation event is relayed.
     *
     * @param   sensitivity     sensitivity value in range [0..100]
     *
     * If @p sensitivity is outside the allowed range it is set to its minimum or maximum allowed
     * value.
     */
    public synchronized void setSensitivity(int sensitivity)
    {
        this.sensitivity = Math.min(Math.max(sensitivity, MIN_SENSITIVITY), MAX_SENSITIVITY);
    }

    /**
     * Returns string representation of cached orientation data.
     *
     * @return  string representation of cached orientation data
     */
    @NonNull
    @Override
    public synchronized String toString()
    {
        if (isSet()) return String.format("yaw=%.1f, pitch=%.1f, roll=%.1f", currentSnapshot.yaw, currentSnapshot.pitch, currentSnapshot.roll);
        return "not available";
    }

    /**
     * Returns cached orientation data.
     *
     * @return  instance of Java @c Map class containing orientation data
     *
     * If no orientation data value has been received yet the method returns @c null.
     */
    @Nullable
    public synchronized Map<String, Object> toMap()
    {
        if (isSet() == false) return null;

        Map<String, Object> data = MapUtils.createMap();
        data.put("timestamp", currentSnapshot.timeStamp);
        data.put("yaw", roundToSingleDecimal(currentSnapshot.yaw));
        data.put("pitch", roundToSingleDecimal(currentSnapshot.pitch));
        data.put("roll", roundToSingleDecimal(currentSnapshot.roll));
        data.put("sensitivity", sensitivity);
        return data;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Returns @c true if orientation data snapshot is available.
     *
     * Since a timestamp is recorded for each received data snapshot, a timestamp equal to 0 means
     * no data snapshot is available.
     *
     * @return  @c true if orientation data snapshot is available, @c false data is not available
     */
    private synchronized boolean isSet()
    {
        return (currentSnapshot.timeStamp > 0L);
    }

    /**
     * Check if two orientation data snapshots are different.
     *
     * The relative change for the yaw, pitch and roll angles are computed, and if the maximum of
     * these changes exceeds the threshold the function returns @c true.
     *
     * @param   snapshot1       first orientation data snapshot to compare
     * @param   snapshot2       second orientation data snapshot to compare
     *
     * @return  @c true if snapshots are significantly different, @c false if not
     */
    private boolean checkIfDifferent(OrientationSnapshot snapshot1, OrientationSnapshot snapshot2)
    {
        // Compute relative changes for three angles.
        double yawChange = relativeChange(smallestAngle(snapshot1.yaw, snapshot2.yaw), YAW_RANGE_DEGREES);
        double pitchChange = relativeChange(smallestAngle(snapshot1.pitch, snapshot2.pitch), PITCH_RANGE_DEGREES);
        double rollChange = relativeChange(smallestAngle(snapshot1.roll, snapshot2.roll), ROLL_RANGE_DEGREES);

        // Check if maximum change exceeds minimum value defined by sensitivity.
        double maxChange = Math.max(yawChange, Math.max(pitchChange, rollChange));
        return (sensitivity == MAX_SENSITIVITY) || (maxChange >= (100.0 - sensitivity));
    }

    /**
     * Returns the smallest angle between specified angles.
     *
     * The smallest angle between two angles is always within the [0..180] range.
     *
     * @param   angle1          first angle
     * @param   angle2          second angle
     *
     * @return  smallest angle between values
     */
    private double smallestAngle(double angle1, double angle2)
    {
        return Math.abs(((angle2 - angle1 + 180) % 360 + 360) % 360 - 180);
    }

    /**
     * Converts absolute angle change to percentage of specified range.
     *
     * @param   change      absolute angle change
     * @param   range       angle range
     *
     * @return  relative change, i.e. change as percentage of range
     *
     * If the result of the calculation is outside [0..100] range it is set to 0 or 100.
     */
    private double relativeChange(double change, double range)
    {
        if (range <= 0.0) return 0.0;
        return Math.max(0.0, Math.min(100.0, (Math.abs(change)/range)*100.0));
    }

    /**
     * Rounds a floating-point value to one decimal place.
     *
     * @param   value           floating-point value to round
     *
     * @return  floating-point value rounded to one decimal place
     */
    private double roundToSingleDecimal(double value)
    {
        return Math.round(value * 10.0) / 10.0;
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Stores snapshot of the current orientation values.
     *
     * A orientation data snapshot specifies a timestamp, yaw, pitch, and roll.
     */
    private static final class OrientationSnapshot
    {
        /** Timestamp of last update. */
        private long timeStamp = 0L;

        /** Yaw angle in degrees. */
        double yaw = 0.0;

        /** Pitch angle in degrees. */
        double pitch = 0.0;

        /** Roll angle in degrees. */
        double roll = 0.0;

        /**
         * Store values of yaw, pitch and roll.
         *
         * @param   yaw         angle of rotation around vertical axis (cardinal direction)
         * @param   pitch       angle of up-down rotation (tilt)
         * @param   roll        angle of left-right rotation (bank)
         */
        void record(double yaw, double pitch, double roll)
        {
            this.timeStamp = System.currentTimeMillis() / 1000L;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        /**
         * Copy yaw, pitch and roll from existing class instance.
         *
         * @param   source          instance of OrientationSnapshot from which to copy data
         */
        void copyFrom(OrientationSnapshot source)
        {
            this.timeStamp = source.timeStamp;
            this.yaw = source.yaw;
            this.pitch = source.pitch;
            this.roll = source.roll;
        }

        /**
         * Resets yaw, pitch and roll.
         *
         * This also clears the timestamp so the snapshot is considered not available until a new
         * value is received.
         */
        void reset()
        {
            this.timeStamp = 0L;
            this.yaw = 0.0;
            this.pitch = 0.0;
            this.roll = 0.0;
        }
    }
}