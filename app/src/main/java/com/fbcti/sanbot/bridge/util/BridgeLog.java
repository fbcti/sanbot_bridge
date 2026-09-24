/**
 * @file        BridgeLog.java
 * @brief       Implements BridgeLog utility class.
 */
package com.fbcti.sanbot.bridge.util;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides a shared Android log tag while preserving the class/source label in the message.
 *
 * @version     1.0.001
 * @date        21 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class BridgeLog
{
    /** Android log tag used by all bridge log messages. */
    private static final String LOG_TAG = "SanbotBridge";

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /** Private constructor preventing utility class instantiation. */
    private BridgeLog() {}

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Writes a @e verbose log message.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     */
    public static void verbose(String source, String message)
    {
        Log.v(LOG_TAG, format(source, message));
    }

    /**
     * Writes a @e verbose log message including a stack trace.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     * @param   throwable       error/exception details
     */
    public static void verbose(String source, String message, Throwable throwable)
    {
        Log.v(LOG_TAG, format(source, message), throwable);
    }

    /**
     * Writes a @e debug log message.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     */
    public static void debug(String source, String message)
    {
        Log.d(LOG_TAG, format(source, message));
    }

    /**
     * Writes a @e debug log message including a stack trace.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     * @param   throwable       error/exception details
     */
    public static void debug(String source, String message, Throwable throwable)
    {
        Log.d(LOG_TAG, format(source, message), throwable);
    }

    /**
     * Writes an @e info log message.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     */
    public static void info(String source, String message)
    {
        Log.i(LOG_TAG, format(source, message));
    }

    /**
     * Writes an @e info log message including a stack trace.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     * @param   throwable       error/exception details
     */
    public static void info(String source, String message, Throwable throwable)
    {
        Log.i(LOG_TAG, format(source, message), throwable);
    }

    /**
     * Writes a @e warning log message.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     */
    public static void warning(String source, String message)
    {
        Log.w(LOG_TAG, format(source, message));
    }

    /**
     * Writes a @e warning log message including a stack trace.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     * @param   throwable       error/exception details
     */
    public static void warning(String source, String message, Throwable throwable)
    {
        Log.w(LOG_TAG, format(source, message), throwable);
    }

    /**
     * Writes an @e error log message.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     */
    public static void error(String source, String message)
    {
        Log.e(LOG_TAG, format(source, message));
    }

    /**
     * Writes an @e error log message including a stack trace.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     * @param   throwable       error/exception details
     */
    public static void error(String source, String message, Throwable throwable)
    {
        Log.e(LOG_TAG, format(source, message), throwable);
    }

    /**
     * Writes an log message for an API call.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   api             API name
     * @param   method          method name
     * @param   args            method arguments
     */
    public static void apicall(String source, String api, String method, Object... args)
    {
        List<String> kvpList = new ArrayList<>();
        for (Object arg : args) kvpList.add(arg.toString());
        BridgeLog.verbose(source, String.format("*** %s: %s(%s) ***", api, method, TextUtils.join(",", kvpList)));
    }

    /**
     * Writes an log message for an API event.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   api             API name
     * @param   method          even handler method name
     * @param   args            method arguments
     */
    public static void apievent(String source, String api, String method, Object... args)
    {
        List<String> kvpList = new ArrayList<>();
        for (Object arg : args) kvpList.add(arg.toString());
        BridgeLog.verbose(source, String.format("+++ %s: %s(%s) +++", api, method, TextUtils.join(",", kvpList)));
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Formats log message.
     *
     * @param   source          log source (name of class from which the function is called)
     * @param   message         log message
     *
     * @return  formatted log message
     */
    @NonNull
    private static String format(String source, String message)
    {
        if (message == null) message = "";
        if ((source == null) || (source.isEmpty())) return message;
        return "[" + source + "] " + message;
    }
}