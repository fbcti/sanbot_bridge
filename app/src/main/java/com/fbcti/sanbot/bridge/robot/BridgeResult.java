/**
 * @file        BridgeResult.java
 * @brief       Implements BridgeResult class.
 */
package com.fbcti.sanbot.bridge.robot;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.transport.HttpStatus;
import com.fbcti.sanbot.bridge.util.StringUtils;
import com.google.gson.Gson;

/**
 * Represents a generic bridge operation result.
 *
 * This abstract class implements functionality common to the specific result classes.
 *
 * @version     1.0.001
 * @date        24 Jun 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public abstract class BridgeResult
{
    /** Instance of class for serializing/deserializing JSON objects. */
    protected static final Gson gson = new Gson();

    /** Result code. */
    protected final Code code;

    /** Brief result description. */
    protected final String description;

    /** HTTP status code matching the result code. */
    protected final int statusCode;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs the base portion bridge operation result.
     *
     * The result code is copied to a member variable. If a description is specified it is copied to
     * a member variable, if not the default description matching the result code is set. The HTTP
     * status code matching the result code is copied to a member variable.
     *
     * @param   code            result code
     * @param   description     brief result description
     */
    BridgeResult(@NonNull Code code, String description)
    {
        this.code = code;
        this.description = (StringUtils.isBlank(description) == false) ? description.trim() :
            code.description();
        this.statusCode = code.statusCode();
    }

    /***********************************************************************************************
     * PUBLIC GETTERS/SETTERS
     **********************************************************************************************/

    /** Returns the result code. */
    public Code getCode() { return code; }

    /** Returns a string representation of the result code. */
    public String getCodeAsString() { return code.name(); }

    /** Returns the brief result description. */
    public String getDescription() { return description; }

    /** Returns the HTTP status code. */
    public int getStatusCode() { return statusCode; }

    /** Returns @c true if the result code specifies a successful operation. */
    public boolean isSuccess() { return code == Code.SUCCESS; }

    /** Returns @c true if the result code specifies a failed operation. */
    public boolean isFailure() { return code != Code.SUCCESS; }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Defines result codes.
     *
     * For each result code a default brief result description and a matching HTTP status code is
     * specified.
     */
    public enum Code
    {
        SUCCESS("operation_success", HttpStatus.OK.code),
        FAILURE("operation_failure", HttpStatus.INTERNAL_SERVER_ERROR.code),
        SANBOT_ERROR("sanbot_sdk_error", HttpStatus.INTERNAL_SERVER_ERROR.code),
        INVALID_PARAMS("invalid_parameter", HttpStatus.BAD_REQUEST.code),
        NOT_AVAILABLE("resource_not_available", HttpStatus.SERVICE_UNAVAILABLE.code),
        NOT_READY("not_available", HttpStatus.SERVICE_UNAVAILABLE.code),
        NOT_SUPPORTED("feature_not_supported", HttpStatus.NOT_IMPLEMENTED.code),
        UNKNOWN("unknown_result", HttpStatus.UNKNOWN.code);

        /** Default brief result description. */
        private final String description;

        /** HTTP status code. */
        private final int statusCode;

        /**
         * Creates enumerator value.
         *
         * @param   description     default brief result description
         * @param   statusCode      HTTP status code
         */
        Code(String description, int statusCode)
        {
            this.description = description;
            this.statusCode = statusCode;
        }

        /**
         * Returns default brief result description matching current value.
         *
         * @return  default brief result description matching current value
         */
        public String description() { return description; }

        /**
         * Returns HTTP status code matching current enumerator value.
         *
         * @return  HTTP status code matching current enumerator value
         */
        public int statusCode()
        {
            return statusCode;
        }
    }
}
