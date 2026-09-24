/**
 * @file        BridgeEvent.java
 * @brief       Implements BridgeEvent class.
 */
package com.fbcti.sanbot.bridge.transport;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.util.StringUtils;

/**
 * Represents an event raised by a bridge unit.
 *
 * Bridge units create instances of this class and pass them to the BridgeService class through the
 * unit event listener. The service decides whether the event is logged and published.
 *
 * @version     1.0.001
 * @date        21 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class BridgeEvent
{
    /** Robot module that raised the event. */
    private final String module;

    /** Event name. */
    private final String event;

    /** Optional event data. */
    private final Object data;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs an event with additional data.
     *
     * The constructor is declared @c private, instances of this class must be created using the
     * static factory methods.
     *
     * @param   module          robot module that raised the event
     * @param   event           event name
     * @param   data            optional event data
     *
     * Both @p module and @p event are mandatory, @c data may be @c null.
     */
    private BridgeEvent(String module, String event, Object data)
    {
        this.module = requireValue("module", module);
        this.event = requireValue("event", event);
        this.data = data;
    }

    /**
     * @name    Public Static Factory Methods
     * @{ 
     */ 

    /**
     * Creates a BridgeEvent object containing additional data.
     *
     * @param   module          robot module that raised the event
     * @param   event           event name
     * @param   data            optional event data
     *
     * Both @p module and @p event are mandatory, @c data may be @c null.
     * 
     * @return  instance of BridgeEvent class
     */
    @NonNull
    public static BridgeEvent create(String module, String event, Object data)
    {
        return new BridgeEvent(module, event, data);    
    }

    /**
     * Creates a BridgeEvent object containing additional data.
     *
     * @param   module          robot module that raised the event
     * @param   event           event name
     *
     * @return  instance of BridgeEvent class
     */
    @NonNull
    public static BridgeEvent create(String module, String event)
    {
        return new BridgeEvent(module, event, null);
    }

    /** @{ */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Returns the robot module that raised the event.
     *
     * @return  robot module that raised the event
     */
    public String getModule()
    {
        return module;
    }

    /**
     * Returns the event name.
     *
     * @return  event name
     */
    public String getEvent()
    {
        return event;
    }

    /**
     * Returns optional event data.
     *
     * @return  event data, or @c null if no event data is available
     */
    public Object getData()
    {
        return data;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Returns trimmed parameter value or throw exception if value is @c null or empty.
     *
     * @param   name            parameter name used in the exception message
     * @param   value           value to check
     *
     * @return  trimmed value
     *
     * @throws  IllegalArgumentException thrown if @p value is @c null
     */
    @NonNull
    private static String requireValue(String name, String value)
    {
        value = StringUtils.trim(value);

        // Throw exception if string is null.
        if (value == null) throw new IllegalArgumentException(name + " may not be null");

        // Throw exception if string is empty.
        if (value.isEmpty()) throw new IllegalArgumentException(name + " may not be empty");

        return value;
    }
}