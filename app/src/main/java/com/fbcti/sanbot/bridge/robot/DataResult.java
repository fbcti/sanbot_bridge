/**
 * @file        DataResult.java
 * @brief       Implements DataResult class.
 */
package com.fbcti.sanbot.bridge.robot;

import android.support.annotation.NonNull;

import com.sanbot.opensdk.beans.OperationResult;

/**
 * Represents bridge operation result containing structured data.
 *
 * This class extends the abstract BridgeResult class. It adds a @c data member that can either
 * represent a simple string or a string representing a JSON object. It implements a number of
 * static methods that each creates an instance of this class from specified values of the result
 * code, description and/or data, or directly from an instance of the Sanbot SDK @c OperationResult
 * class.
 *
 * @version     1.0.001
 * @date        24 Jun 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class DataResult extends BridgeResult
{
    /** Optional result data. */
    private final String data;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new DataResult instance.
     *
     * The constructor is declared @c private, instances of this class must be created using the
     * static factory methods.
     *
     * @param   code            result code
     * @param   description     optional human-readable result description
     * @param   data            result data
     *
     * If @p description is @c null the default description matching the error code is set. Empty
     * description strings are set as-is. If @p data is null an empty data string is set. String
     * data is stored as-is, because Sanbot SDK operation results already provide their data as a
     * string. In all other cases data is converted to a JSON string. The constructor is declared
     * @c private, instances of this class must be created using the static factory methods.
     */
    private DataResult(Code code, String description, Object data)
    {
        super(code, description);
        if (data == null) this.data = "";
        else if (data instanceof String) this.data = (String)data;
        else this.data = gson.toJson(data);
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Creates a bridge result object from specified values.
     *
     * This factory method allows all result properties to be specified.
     *
     * @param   code            result code
     * @param   description     brief result description
     * @param   data            result data
     *
     * @return  DataResult instance
     */
    @NonNull
    public static DataResult create(Code code, String description, Object data)
    {
        return new DataResult(code, description, data);
    }

    /**
     * Creates a DataResult instance for a successful operation.
     *
     * This factory method allows the result description and structured data to be specified, but
     * always sets the result code to @c Code.SUCCESS.
     *
     * @param   description     brief result description
     * @param   data            structured data
     *
     * @return  DataResult instance for successful operation, includes structured data
     */
    @NonNull
    public static DataResult success(String description, Object data)
    {
        return new DataResult(Code.SUCCESS, description, data);
    }

    /**
     * Creates a DataResult instance for a successful operation.
     *
     * This factory method allows the structured data to be specified, but always sets the result 
     * code to @c Code.SUCCESS and passes an empty brief result description to the constructor to
     * set the default description matching the result code. 
     *
     * @param   data            structured data
     *
     * @return  DataResult instance for successful operation, includes structured data
     */
    @NonNull
    public static DataResult success(Object data)
    {
        return new DataResult(Code.SUCCESS, null, data);
    }

    /**
     * Creates a DataResult instance for a successful operation.
     *
     * This factory method always sets the result code to @c Code.SUCCESS, passes an empty brief
     * result description to the constructor to set the default description matching the result
     * code, and does not add any structured data.
     *
     * @return  DataResult instance for successful operation, does not include structured data
     */
    @NonNull
    public static DataResult success()
    {
        return new DataResult(Code.SUCCESS, null, null);
    }

    /**
     * Creates a DataResult instance for a failed operation.
     *
     * This factory method allows the result code and structured data to be specified, but always
     * passes an empty brief result description to the constructor to set the default description
     * matching the result code.
     *
     * @param   code            result code
     * @param   data            structured data
     *
     * @return  DataResult instance for failed operation, includes structured data
     */
    @NonNull
    public static DataResult failure(Code code, Object data)
    {
        return new DataResult(code, null, data);
    }

    /**
     * Creates a DataResult instance for a failed operation.
     *
     * This factory method allows the result description and structured data to be specified, but
     * always sets the result code to @c Code.FAILURE.
     *
     * @param   description     brief result description
     * @param   data            structured data
     *
     * @return  MediaResult instance for failed operation, includes structured data
     */
    @NonNull
    public static DataResult failure(String description, Object data)
    {
        return new DataResult(Code.FAILURE, description, data);
    }

    /**
     * Creates a DataResult instance for a failed operation.
     *
     * This factory method allows the result description to be specified, but always sets the result
     * code to @c Code.FAILURE and does not add any structured data.
     *
     * @param   description     brief result description
     *
     * @return  DataResult instance for failed operation, does not include structured data
     */
    @NonNull
    public static DataResult failure(String description)
    {
        return new DataResult(Code.FAILURE, description, null);
    }

    /**
     * Creates a DataResult instance for a failed operation.
     *
     * This factory method always sets the result code to @c Code.FAILURE, passes an empty brief
     * result description to the constructor to set the default description matching the result
     * code, and does not add any structured data.
     *
     * @return  DataResult instance for failed operation, does not include structured data
     */
    @NonNull
    public static DataResult failure()
    {
        return new DataResult(Code.FAILURE, null, null);
    }

    /**
     * Creates a DataResult instance for an failed operation due to an unavailable resource.
     *
     * This factory method always sets the result code to @c Code.NOT_AVAILABLE passes an empty
     * brief result description to the constructor to set the default description matching the
     * result code, and adds a string containing error details as structured data,
     *
     * @param   resource        name of unavailable resource
     *
     * @return  DataResult instance for failed operation, includes structured data
     */
    @NonNull
    public static DataResult notavailable(String resource)
    {
        return new DataResult(Code.NOT_AVAILABLE, null, resource + " not available");
    }

    /**
     * Creates a DataResult instance for either a successful or failed operation.
     *
     * This factory method allows the structured data to be specified, but sets the result code to
     * @c Code.SUCCESS if the operation was successful or @c Code.FAILURE if the operation failed. 
     * It passes an empty brief result description to the constructor to set the default description
     * matching the result code
     * 
     * @param   success         @c true if the operation was successful
     * @param   data            structured data
     *
     * @return  instance of DataResult class, includes structured data
     */
    @NonNull
    public static DataResult execution(boolean success, Object data)
    {
        if (success) return new DataResult(Code.SUCCESS, null, data);
        return new DataResult(Code.FAILURE, null, data);
    }

    /**
     * Creates a DataResult instance from a Sanbot SDK @c OperationResult instance.
     *
     * If the @c OperationResult result code equals 1, the result code is set to @c Code.SUCCESS,
     * in all other cases it is set to @c Code.SANBOT_ERROR. The result description is created from
     * the result code and description in the @c OperationResult instance, the result data is
     * copied as-is.
     *
     * @param   result          Sanbot SDK operation result
     *
     * @return  instance of DataResult class created from a Sanbot SDK @c OperationResult
     */
    @NonNull
    public static DataResult fromOperationResult(OperationResult result)
    {
        if (result == null) return new DataResult(Code.UNKNOWN, null, null);

        int sdkCode = result.getErrorCode();
        Code code = (sdkCode == 1) ? Code.SUCCESS : Code.SANBOT_ERROR;
        String description = result.getDescription();
        description = description.toLowerCase();
        description = description.replaceAll(" ", "_");
        return new DataResult(code, String.format("%d - %s", sdkCode, description), result.getResult());
    }

    /**
     * Creates an emulated bridge result with empty result data.
     *
     * This factory method allows the structured data to be specified, but always sets the result
     * code to @c Code.SUCCESS and passes an a fixed brief result description to the constructor.
     *
     * @return  instance of DataResult class, includes structured data
     */
    @NonNull
    public static DataResult emulated(Object data)
    {
        return new DataResult(Code.SUCCESS, "operation_emulated", data);
    }

    /**
     * Creates an emulated bridge result with empty result data.
     *
     * This factory method always sets the result code to @c Code.SUCCESS, passes an a fixed brief
     * result description to the constructor, and does not add any structured data.
     *
     * @return  instance of DataResult class, does not include structured data
     */
    @NonNull
    public static DataResult emulated()
    {
        return new DataResult(Code.SUCCESS, "operation_emulated", null);
    }

    /***********************************************************************************************
     * PUBLIC GETTERS/SETTERS
     **********************************************************************************************/

    /** Returns structured data. */
    public String getData() { return data; }
}
