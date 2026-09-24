/**
 * @file        BridgeEventHost.java
 * @brief       Declares BridgeEventHost interface.
 */
package com.fbcti.sanbot.bridge.app;

import com.fbcti.sanbot.bridge.transport.BridgeEvent;

/**
 * Defines event host callback contract.
 *
 * This interface must be implemented by classes to forward bridge requests to the bridge service.
 * The bridge service is responsible for publishing the forwarded events,
 *
 * @version     1.0.001
 * @date        7 sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public interface BridgeEventHost
{
    void publishEvent(BridgeEvent event);
}