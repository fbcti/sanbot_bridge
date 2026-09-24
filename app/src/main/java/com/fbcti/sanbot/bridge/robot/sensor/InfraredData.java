/**
 * @file        InfraredData.java
 * @brief       Implements InfraredData class.
 */
package com.fbcti.sanbot.bridge.robot.sensor;

import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fbcti.sanbot.bridge.util.MapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stores and relays aggregated infrared data.
 *
 * This helper class keeps infrared data for the current reporting window and the last reporting
 * window that produced a relayed event. When the report interval elapses it computes the current
 * map and returns a payload only when the configured sensitivity threshold is met.
 *
 * @version     1.0.001
 * @date        9 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class InfraredData
{
    /** Number of infrared sensors exposed by the Sanbot SDK. */
    private static final int SENSOR_COUNT = 17;

    /** Sensor value reported if no object is detected. */
    private static final int CLEAR_VALUE = 64;

    /** Sensor value reported for strongest possible detection of object. */
    private static final int STRONG_VALUE = 4;

    /** Default sensitivity for relaying aggregated infrared map events. */
    private static final int DEFAULT_SENSITIVITY = 90;

    /** Minimum allowed infrared event sensitivity. */
    private static final int MIN_SENSITIVITY = 0;

    /** Maximum allowed infrared event sensitivity. */
    private static final int MAX_SENSITIVITY = 100;

    /** Default interval between infrared map reports. */
    private static final int DEFAULT_UPDATE_INTERVAL_MS = 1000;

    /** Minimum interval between infrared map reports. */
    private static final int MIN_UPDATE_INTERVAL_MS = 100;

    /** Maximum interval between infrared map reports. */
    private static final int MAX_UPDATE_INTERVAL_MS = 10000;

    /** Minimum normalized strength used for direction calculation. */
    private static final double DIRECTION_THRESHOLD = 0.20;

    /** Approximate horizontal infrared sensor positions, -1 left to +1 right. */
    private static final double[] SENSOR_X = {0.45, 0.25, -0.95, -0.75, -0.55, -0.25, 0.0, 0.25, 0.55, 0.75, 0.25, -0.25, 0.0, 0.85, 0.85, -0.85, -0.85};

    /** Approximate vertical infrared sensor positions, 0 bottom to 1 top. */
    private static final double[] SENSOR_Y = {0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.25, 0.25, 0.55, 0.75, 0.45, 0.75, 0.45};

    /**
     * Infrared data snapshot for current reporting interval.
     *
     * This holds one AggregateData slot per sensor and is cleared after each processed interval.
     */
    private final InfraredSnapshot currentSnapshot = new InfraredSnapshot(SENSOR_COUNT);

    /**
     * Most recently relayed infrared data snapshot.
     *
     * This is used as the comparison baseline for deciding if newly received data differs enough
     * from last sent data to justify relaying a new event.
     */
    private final InfraredSnapshot lastSentSnapshot = new InfraredSnapshot(SENSOR_COUNT);

    /** Sensitivity used to decide when to relay an aggregated infrared map event. */
    private int sensitivity = DEFAULT_SENSITIVITY;

    /** Minimum time in milliseconds between infrared data reports. */
    private int updateInterval = DEFAULT_UPDATE_INTERVAL_MS;

    /** Next time an infrared map may be reported. */
    private long nextReportTime = 0L;

    /** Flag specifying if an event has been relayed already. */
    private boolean isSent = false;

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Updates infrared data snapshot and returns event payload if sensitivity threshold is met.
     *
     * This method must be called if an infrared sensor event is received. It updates the latest
     * infrared data snapshot by updating the aggregated data for the specified sensor. If no event
     * was sent for at least the configured time, and the difference between the last sent snapshot
     * and the updated snapshot exceeds the threshold determined by the configured sensitivity, an
     * event containing the infrared data is relayed. In both cases the aggregate data for the
     * reporting window is reset.
     *
     * @param   sensor      sensor id in range [1..17]
     * @param   value       value reported by specified sensor
     *
     * @return  event payload, or @c null if the event should be suppressed
     */
    public synchronized Map<String, Object> update(int sensor, int value)
    {
        // Update sensor id to make it zero-based. Ignore if sensor id is not valid.
        int sensorId = sensor - 1;
        if ((sensorId < 0) || (sensorId >= SENSOR_COUNT)) return null;

        // Update latest infrared data snapshot.
        currentSnapshot.record(sensorId, value);

        // Return null if still within minimum allowed time between events.
        long now = SystemClock.elapsedRealtime();
        if (now < nextReportTime) return null;

        // Check if current data differs enough from last sent data.
        boolean isDifferent = (isSent == true) ? checkIfDifferent(currentSnapshot, lastSentSnapshot) : true;

        // Create map containing event data.
        Map<String, Object> latestReport = buildReport(currentSnapshot);
        nextReportTime = now + updateInterval;
        if (isDifferent == false)
        {
            currentSnapshot.reset();
            return null;
        }

        isSent = true;
        lastSentSnapshot.copyFrom(currentSnapshot);
        currentSnapshot.reset();
        return latestReport;
    }

    /**
     * Resets cached infrared data.
     *
     * This clears both the current and last-sent snapshots, the sent-state flag, and the
     * next-report deadline, so the next received sensor value can trigger a fresh
     * comparison/report cycle.
     */
    public synchronized void reset()
    {
        currentSnapshot.reset();
        lastSentSnapshot.reset();
        nextReportTime = 0L;
        isSent = false;
    }

    /**
     * Sets infrared event sensitivity.
     *
     * Higher values of the sensitivity requires a smaller relative difference between current and
     * last sent infrared data snapshots before an infrared sensor event is relayed.
     *
     * @param   sensitivity     sensitivity value from 0 to 100
     *
     * If @p sensitivity is outside the allowed range it is set to its minimum or maximum
     * allowed value.
     */
    public synchronized void setSensitivity(int sensitivity)
    {
        this.sensitivity = Math.min(Math.max(sensitivity, MIN_SENSITIVITY), MAX_SENSITIVITY);
    }

    /**
     * Sets orientation event update interval.
     *
     * The update interval defines the minimum elapsed real time between infrared data snapshot
     * evaluations. Values received within this interval are recorded in the current snapshot, and
     * only if the interval has expired the average of the values received in the interval is
     * compared to the value in the last sent snapshot to determine if a infrared sensor event is
     * relayed.
     *
     * @param   update      update interval in milliseconds
     *
     *     If @c update is outside the allowed range it is set to its minimum or maximum allowed
     *     value.
     */
    public synchronized void setUpdateInterval(int update)
    {
        this.updateInterval = Math.min(Math.max(update, MIN_UPDATE_INTERVAL_MS), MAX_UPDATE_INTERVAL_MS);
    }

    /**
     * Returns the most recently relayed infrared data snapshot.
     *
     * @return  instance of Java @c Map class containing infrared data
     *
     * If no infrared report has been relayed yet the returned map is empty. Values collected for
     * the next reporting interval are not exposed until they produce a relayed report.
     */
    @NonNull
    public synchronized Map<String, Object> toMap()
    {
        return (isSent) ? buildReport(lastSentSnapshot) : MapUtils.createMap();
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Check if two infrared data snapshots are different.
     *
     * The data values in the snapshots are converted to vectors, and if the relative distance
     * between the two vectors exceeds the threshold the function returns @c true.
     *
     * @param   snapshot1       first infrared data snapshot to compare
     * @param   snapshot2       second infrared data snapshot to compare
     *
     * @return  @c true if snapshots are significantly different, @c false if not
     */
    private boolean checkIfDifferent(@NonNull InfraredSnapshot snapshot1, InfraredSnapshot snapshot2)
    {
        if (snapshot1.hasSamples() == false)
            return (snapshot2.hasSamples() == false) ? true : false;

        // Create vectors from snapshots.
        double[] vector1 = snapshot1.buildVector();
        double[] vector2 = snapshot2.buildVector();
        if (vector1.length != vector2.length) return true;

        // Compute euclidean distance between vectors.
        double sum = 0.0;
        for (int i = 0; i < vector1.length; i++)
        {
            double difference = vector1[i] - vector2[i];
            sum += difference * difference;
        }
        double distance = Math.sqrt(sum);
        double change = relativeChange(distance);
        return (sensitivity >= MAX_SENSITIVITY) || (change >= (100.0 - sensitivity));
    }

    /**
     * Converts absolute distance to percentage of the widest range.
     *
     * @param   distance        distance to convert
     *
     * @return  relative distance, i.e. distance as percentage of widest range
     */
    private double relativeChange(double distance)
    {
        double maxDistance = (CLEAR_VALUE - STRONG_VALUE) * Math.sqrt(SENSOR_COUNT);
        if (Double.isInfinite(distance) || (distance >= maxDistance)) return 100.0;
        if (distance <= 0.0) return 0.0;
        return Math.max(0.0, Math.min(100.0, (distance / maxDistance) * 100.0));
    }

    /**
     * Builds infrared data report.
     *
     * The report contains the raw sensor readings (i.e. strength reported by each sensor), but
     * using the position of each sensor also computes an approximate direction at which an object
     * is detected. Since the position of the infrared sensors is defined as the position of the
     * sensors when the arms are pointing down, the computation is unreliable if the arms are in a
     * different position.
     *
     * @param   snapshot        data snapshot from which to create report
     *
     * @return instance of Java @c Map class containing infrared data
     */
    @NonNull
    private Map<String, Object> buildReport(@NonNull InfraredSnapshot snapshot)
    {
        Map<String, Object> data = MapUtils.createMap();
        if (snapshot.hasSamples() == false) return data;

        // Compute values aggregated over sensors.
        long timeStamp = 0L;
        StringBuilder sensorsFired = new StringBuilder();
        double maxStrength = 0.0;
        int maxSensorId = -1;
        double sumWeight = 0.0;
        double sumX = 0.0;
        double sumY = 0.0;

        for (int sensorId=0; sensorId<SENSOR_COUNT; sensorId++)
        {
            AggregateData aggregateData = snapshot.get(sensorId);
            if (aggregateData.lastTimestamp > timeStamp) timeStamp = aggregateData.lastTimestamp;
            if (aggregateData.detected()) sensorsFired.append((sensorsFired.length() == 0) ? "" : ",").append(sensorId + 1);

            double strength = aggregateData.strength();
            if (strength > maxStrength)
            {
                maxStrength = strength;
                maxSensorId = sensorId;
            }
            if (strength >= DIRECTION_THRESHOLD)
            {
                sumWeight += strength;
                sumX += SENSOR_X[sensorId]*strength;
                sumY += SENSOR_Y[sensorId]*strength;
            }
        }

        boolean detected = sumWeight > 0.0;
        double directionX = detected ? (sumX/sumWeight) : 0.0;
        double directionY = detected ? (sumY/sumWeight) : 0.0;

        // Add sensor-aggregated values.
        data.put("timestamp", timeStamp);
        data.put("sensorsFired", sensorsFired.toString());
        data.put("sensorData", snapshot.getSensorData());
        data.put("sensitivity", sensitivity);
        data.put("updateInterval", updateInterval);
        if (maxSensorId >= 0)
        {
            data.put("strongest", MapUtils.createMap("sensorId", maxSensorId+1, "strength", maxStrength));
            data.put("direction", MapUtils.createMap("x", directionX, "y", directionY, "label", direction(directionX, directionY, detected)));
        }
        return data;
    }

    /**
     * Converts an infrared direction coordinate into a coarse label.
     *
     * @param   x               x coordinate
     * @param   y               y coordinate
     * @param   detected        @c true if an object is detected, @c false if no object is detected
     *
     * @return  human-readable string specifying direction
     */
    @NonNull
    private String direction(double x, double y, boolean detected)
    {
        if (detected == false) return "none";

        String vertical;
        if (y > 0.65) vertical = "upper";
        else if (y < 0.35) vertical = "lower";
        else vertical = "middle";

        String horizontal;
        if (x < -0.45) horizontal = "left";
        else if (x > 0.45) horizontal = "right";
        else horizontal = "center";

        return vertical + "_" + horizontal;
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Stores a infrared data snapshot
     *
     * A infrared data snapshot consists an array containing of one AggregateData instance per
     * sensor.
     */
    private static final class InfraredSnapshot
    {
        /** Per-sensor aggregated state. */
        private final AggregateData[] sensors;

        /*******************************************************************************************
         * CONSTRUCTORS
         ******************************************************************************************/

        /**
         * Constructs a new InfraredSnapshot instance
         *
         * An instance of AggregateData is created for each sensor.
         *
         * @param   sensorCount     number of sensors for which to store data
         */
        InfraredSnapshot(int sensorCount)
        {
            sensors = new AggregateData[sensorCount];
            for (int i = 0; i < sensors.length; i++) sensors[i] = new AggregateData(i);
        }

        /*******************************************************************************************
         * PACKAGE-PRIVATE METHODS
         ******************************************************************************************/

        /**
         * Updates the aggregated value for the specified sensor.
         *
         * @param   sensorId        zero-based sensor id for which to store value
         * @param   value           value of reading of sensor with specified id
         */
        void record(int sensorId, int value)
        {
            sensors[sensorId].record(value);
        }

        /**
         * Returns aggregated data values of the specified sensor.
         *
         * @param   sensorId        zero-based sensor id for which to return value
         *
         * @return  AggregateData instance for specified sensor
         */
        AggregateData get(int sensorId)
        {
            return sensors[sensorId];
        }

        /**
         * Copies aggregate data value for all sensors from existing class instance.
         *
         * @param   source          instance of InfraredSnapshot from which to copy data
         */
        void copyFrom(InfraredSnapshot source)
        {
            for (int i = 0; i < sensors.length; i++) sensors[i].copyFrom(source.sensors[i]);
        }

        /**
         * Resets aggregate data values for all sensors.
         */
        void reset()
        {
            for (AggregateData sensor : sensors) sensor.reset();
        }

        /**
         * Checks if an aggregate data value for at least one sensor is set.
         *
         * @return  @c true if aggregate data value for at least on sensor is set
         */
        boolean hasSamples()
        {
            for (AggregateData sensor : sensors)
            {
                if (sensor.hasSamples()) return true;
            }
            return false;
        }

        /**
         * Builds a comparison vector from individual sensor data values.
         *
         * @return  vector of per-sensor average values
         */
        @NonNull
        double[] buildVector()
        {
            double[] vector = new double[sensors.length];
            for (int sensorId = 0; sensorId < sensors.length; sensorId++)
            {
                vector[sensorId] = sensors[sensorId].average();
            }
            return vector;
        }

        /**
         * Returns a list of data maps each containing raw data from a single sensor.
         *
         * @return  instance of Java @c List classes containing Java @c Map instances
         */
        @NonNull
        List<Map<String, Object>> getSensorData()
        {
            List<Map<String, Object>> data = new ArrayList<>();
            for (AggregateData sensor : sensors)
            {
                Map<String, Object> sensorData = sensor.toMap();
                if (sensorData != null) data.add(sensorData);
            }
            return data;
        }
    }

    /**
     * Stores aggregate data for single infrared sensor.
     *
     * Values reported by the sensors are in the range <tt>[CLEAR_VALUE...STRONG_VALUE]</tt>,
     * with @c CLEAR_VALUE reported if no object is detected, and @c STRONG_VALUE reported for
     * strongest possible detection of object. If all sensors report @c CLEAR_VALUE, no object has
     * been detected. Since sensors may report up to 10 values per second, each time update() is
     * called the number and total sum of received values is updated. By calling average() the
     * average value of these reported values can be retrieved.
     */
    private static final class AggregateData
    {
        /** Internal zero-based sensor id. */
        private final int sensorId;

        /** Number of values received. */
        private long count;

        /** Sum of all received values. */
        private long sum;

        /** Last received value. */
        private int last;

        /** Minimum received distance value. */
        private int min;

        /** Maximum received distance value. */
        private int max;

        /** Timestamp of first received value. */
        long firstTimestamp;

        /** Timestamp of last received values. */
        long lastTimestamp;

        /*******************************************************************************************
         * CONSTRUCTORS
         ******************************************************************************************/

        /**
         * Constructs new instance of AggregateData.
         *
         * @param   sensorId        zero-based sensor id for which to create instance
         */
        AggregateData(int sensorId)
        {
            this.sensorId = sensorId;
            reset();
        }

        /*******************************************************************************************
         * PACKAGE-PRIVATE METHODS
         ******************************************************************************************/

        /**
         * Updates sensor data.
         *
         * This method must be called each time an infrared sensor event is received. It updates
         * the minimum, maximum and total sum of values updated since the last reset.
         *
         * @param   value           value reported by sensor
         */
        void record(int value)
        {
            long now = System.currentTimeMillis() / 1000L;
            if (count == 0L)
            {
                min = value;
                max = value;
                firstTimestamp = now;
            }
            else
            {
                if (value < min) min = value;
                if (value > max) max = value;
            }
            last = value;
            sum += value;
            count++;
            lastTimestamp = now;
        }

        /**
         * Copies aggregate data from existing class instance.
         *
         * @param   source          instance of AggregateData from which to copy data
         */
        void copyFrom(@NonNull AggregateData source)
        {
            count = source.count;
            sum = source.sum;
            last = source.last;
            min = source.min;
            max = source.max;
            firstTimestamp = source.firstTimestamp;
            lastTimestamp = source.lastTimestamp;
        }

        /**
         * Resets aggregate data.
         */
        void reset()
        {
            count = 0L;
            sum = 0L;
            last = 0;
            min = 0;
            max = 0;
            firstTimestamp = 0L;
            lastTimestamp = 0L;
        }

        /**
         * Checks if an aggregate data value for this sensor is set.
         *
         * @return  @c true if aggregate data value for this sensor is set
         */
        boolean hasSamples()
        {
            return count > 0L;
        }

        /**
         * Checks if the sensor detected a non-clear value.
         *
         * The value is considered a non-clear value if at least one sample was received and the
         * minimum value is not equal to @c CLEAR_VALUE.
         *
         * @return  @c true is sample detected non-clear value, @c false if nor
         */
        boolean detected()
        {
            return ((hasSamples()) && (min != CLEAR_VALUE));
        }

        /**
         * Returns the average value of the received sensor values.
         *
         * If no values have been received @c CLEAR_VALUE is returned.
         *
         * @return  average value reported by sensor
         */
        double average()
        {
            return (count > 0L) ? (((double)sum) / count) : CLEAR_VALUE;
        }

        /**
         * Converts the minimum recorded sensor value to detection strength.
         *
         * The minimum value in the <em>[CLEAR_VALUE...STRONG_VALUE]</em> range is converted to
         * a detection strength in range [0..1].
         *
         * @return  sensor detection strength
         */
        double strength()
        {
            if (count == 0L) return 0.0;
            double strength = (CLEAR_VALUE - min) / (double)(CLEAR_VALUE - STRONG_VALUE);
            return Math.max(0.0, Math.min(strength, 1.0));
        }

        /**
         * Returns data map containing aggregate data for sensor.
         *
         * @return  instance of Java @c Map class containing aggregate data for sensor
         *
         * If no data has been collected by this sensor the method returns @c null.
         */
        @Nullable
        Map<String, Object> toMap()
        {
            // If no data values have been received return empty data map.
            if ((count == 0L) || (detected() == false)) return null;

            Map<String, Object> data = MapUtils.createMap("sensor", sensorId + 1);
            data.put("count", count);
            data.put("last", last);
            data.put("average", ((double)sum) / count);
            data.put("min", min);
            data.put("max", max);
            data.put("strength", strength());
            data.put("firstTimestamp", firstTimestamp);
            data.put("lastTimestamp", lastTimestamp);
            return data;
        }
    }
}
