/**
 * @file        JsonResponse.java
 * @brief       Implements JsonResponse class.
 */
package com.fbcti.sanbot.bridge.transport;

import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.util.JsonUtils;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Represents a protocol-neutral bridge response containing optional JSON payload data.
 *
 * Instances of this class are sent to clients to report the outcome of @c info and @c command
 * bridge requests. The response object is immutable - all member variables are declared @c final.
 *
 * @version     1.0.001
 * @date        21 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public class JsonResponse extends BridgeResponse
{
    /** Structured payload data. */
    public final JsonElement data;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a nwe instance of JsonResponse class from individual bridge request parameters.
     *
     * Response properties are copied to member variables. The constructor is declared @c private,
     * instances of this class must be created using the static factory methods.
     *
     * @param   id              request id
     * @param   type            request type
     * @param   module          request module
     * @param   action          request action
     * @param   code            HTTP-like response status code
     * @param   message         human-readable response message
     * @param   data            structured response data
     */
    private JsonResponse(String id, String type, String module, String action, int code, String message, JsonElement data)
    {
        super(id, type, module, action, code, message);
        this.data = data;
    }

    /**
     * Constructs a nwe instance of JsonResponse class from BridgeRequest instance.
     *
     * Response properties are copied to member variables. The constructor is declared @c private,
     * instances of this class must be created using the static factory methods.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   code            HTTP-like response status code
     * @param   message         human-readable response message
     * @param   data            structured response data
     */
    private JsonResponse(BridgeRequest request, int code, String message, JsonElement data)
    {
        super(request, code, message);
        this.data = data;
    }

    /**
     * @name    Public Static Factory Methods
     * @{ 
     */ 

    /**
     * Creates a response object with explicitly supplied structured data.
     *
     * @param   id              request id
     * @param   type            request type
     * @param   module          request module
     * @param   action          request action
     * @param   code            HTTP-like response status code
     * @param   message         human-readable response message
     * @param   data            structured response data
     *
     * @return  instance of JsonResponse class
     */
    @NonNull
    public static JsonResponse create(String id, String type, String module, String action, int code, String message, JsonElement data)
    {
        return new JsonResponse(id, type, module, action, code, message, data);
    }

    /**
     * Creates a response object without structured data.
     *
     * @param   id              request id
     * @param   type            request type
     * @param   module          request module
     * @param   action          request action
     * @param   code            HTTP-like response status code
     * @param   message         human-readable response message
     *
     * @return  instance of JsonResponse class
     */
    @NonNull
    public static JsonResponse create(String id, String type, String module, String action, int code, String message)
    {
        return new JsonResponse(id, type, module, action, code, message, null);
    }

    /**
     * Creates a response object with explicitly supplied structured data.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   code            HTTP-like response status code
     * @param   message         human-readable response message
     * @param   data            structured response data
     *
     * @return  instance of JsonResponse class
     */
    @NonNull
    public static JsonResponse create(BridgeRequest request, int code, String message, JsonElement data)
    {
        return new JsonResponse(request, code, message, data);
    }

    /**
     * Creates a response object without structured data.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   code            HTTP-like response status code
     * @param   message         human-readable response message
     *
     * @return  instance of JsonResponse class
     */
    @NonNull
    public static JsonResponse create(BridgeRequest request, int code, String message)
    {
        return new JsonResponse(request, code, message, null);
    }

    /**
     * Creates a response object with the bridge result object as structured data.
     *
     * A JSON object is created from the bridge result object, the result code and result message
     * are set according to the error code in the bridge result.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   result          bridge result data
     *
     * @return  instance of @c JsonResponse class
     */
    @NonNull
    public static JsonResponse bridgeResult(BridgeRequest request, DataResult result)
    {
        JsonElement data = bridgeResultToJson(result);
        if ((result != null) && (result.isSuccess())) return new JsonResponse(request, CODE_OK, MESSAGE_SDK_SUCCESS, data);
        else return new JsonResponse(request, CODE_INTERNAL_SERVER_ERROR, MESSAGE_SDK_ERROR, data);
    }

    /**
     * Creates a response object for a failed request.
     *
     * Both response code and response message are specified by method parameters. Structured
     * response data is not available.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   code            HTTP-like response status code
     * @param   message         human-readable response message
     *
     * @return  instance of @c JsonResponse class
     */
    @NonNull
    public static JsonResponse error(BridgeRequest request, int code, String message)
    {
        return new JsonResponse(request, code, message, null);
    }

    /**
     * Creates a response object for a bad request.
     *
     * The response code is fixed, the response message is specified by method parameters.
     * Structured response data is not available.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   message         human-readable response message
     *
     * @return  instance of @c JsonResponse class
     */
    @NonNull
    public static JsonResponse badRequest(BridgeRequest request, String message)
    {
        return new JsonResponse(request, CODE_BAD_REQUEST, message, null);
    }

    /**
     * Creates a response object for a unauthorized request.
     *
     * The response code is fixed, the response message is specified by method parameters.
     * Structured response data is not available.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   message         human-readable response message
     *
     * @return  instance of @c JsonResponse class
     */
    @NonNull
    public static JsonResponse unauthorized(BridgeRequest request, String message)
    {
        return new JsonResponse(request, CODE_UNAUTHORIZED, message, null);
    }

    /**
     * Creates a bridge response for a forbidden request.
     *
     * The response code is fixed, the response message is specified by method parameters.
     * Structured response data is not available.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   message         human-readable response message
     *
     * @return  instance of @c JsonResponse class
     */
    @NonNull
    public static JsonResponse forbidden(BridgeRequest request, String message)
    {
        return new JsonResponse(request, JsonResponse.CODE_FORBIDDEN, message, null);
    }

    /**
     * Creates a bridge response for an unsupported request.
     *
     * The response code is fixed, the response message is specified by method parameters.
     * Structured response data is not available.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   message         human-readable response message
     *
     * @return  instance of @c JsonResponse class
     */
    @NonNull
    public static JsonResponse notFound(BridgeRequest request, String message)
    {
        return new JsonResponse(request, CODE_NOT_FOUND, message, null);
    }

    /**
     * Creates a bridge response for an unacceptable request.
     *
     * The response code is fixed, the response message is specified by function parameters.
     * Structured response data is not available.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   message         human-readable response message
     *
     * @return  instance of @c JsonResponse class
     */
    public static JsonResponse notAcceptable(BridgeRequest request, String message)
    {
        return new JsonResponse(request, CODE_NOT_ACCEPTABLE, message, null);
    }

    /**
     * Creates a bridge response for a not allowed request.
     *
     * The response code is fixed, the response message is specified by function parameters.
     * Structured response data is not available.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   message         human-readable response message
     *
     * @return  instance of @c JsonResponse class
     */
    @NonNull
    public static JsonResponse notAllowed(BridgeRequest request, String message)
    {
        return new JsonResponse(request, CODE_NOT_ACCEPTABLE, message, null);
    }

    /**
     * Creates a bridge response for a request that caused an internal server error.
     *
     * The response code is fixed, the response message is specified by function parameters.
     * Structured response data is not available.
     *
     * @param   request         bridge request objet from which to copy request properties
     * @param   message         human-readable response message
     *
     * @return  instance of @c JsonResponse class
     */
    @NonNull
    public static JsonResponse internalError(BridgeRequest request, String message)
    {
        return new JsonResponse(request, CODE_INTERNAL_SERVER_ERROR, message, null);
    }

    /** @} */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Returns a JSON object containing the bridge response data.
     *
     * Structured data is only added to the JSON object if not @c null.
     *
     * @return  JSON object containing bridge response data
     */
    public JsonObject toJson()
    {
        JsonObject jsonObject = new JsonObject();
        if (id != null) jsonObject.addProperty(BridgeProtocol.FIELD_ID, id);
        jsonObject.addProperty(BridgeProtocol.FIELD_TYPE, BridgeProtocol.TYPE_RESPONSE);
        if (module != null) jsonObject.addProperty(BridgeProtocol.FIELD_MODULE, module);
        if (action != null) jsonObject.addProperty(BridgeProtocol.FIELD_ACTION, action);
        jsonObject.addProperty("code", code);
        if (message != null) jsonObject.addProperty(BridgeProtocol.FIELD_MESSAGE, message);
        if (hasData(data)) jsonObject.add(BridgeProtocol.FIELD_DATA, data);
        return jsonObject;
    }

    /**
     * Returns a string containing a JSON representation of the bridge response object.
     *
     * @return  string containing JSON representation of bridge response object
     */
    public String toString()
    {
        return toJson().toString();
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Converts a bridge result object into a JSON object.
     *
     * The @c errorCode and @c description properties are copied as-is. The @c result property is a
     * string that may represent a primitive type, a JSON array, or a JSON object and must be parsed
     * before being copied.
     *
     * @param   result          bridge result object to parse
     *
     * @return  JSON object containing bridge result data
     */
    private static JsonObject bridgeResultToJson(DataResult result)
    {
        if (result == null) return null;

        JsonObject jsonResult = new JsonObject();
        jsonResult.addProperty("errorCode", result.getCodeAsString());
        jsonResult.addProperty("description", result.getDescription());
        JsonElement jsonParsed = new Gson().toJsonTree(JsonUtils.parseObject(result.getData()));
        jsonResult.add("result", jsonParsed);
        return jsonResult;
    }

    /**
     * Returns @c true if a response data object contains additional data.
     *
     * @param   data            response data object
     *
     * @return  @c true if the data object should be serialized
     */
    private static boolean hasData(JsonElement data)
    {
        if ((data == null) || (data.isJsonNull())) return false;
        if (data.isJsonObject()) return data.getAsJsonObject().entrySet().isEmpty() == false;
        if (data.isJsonArray()) return data.getAsJsonArray().size() > 0;
        return true;
    }
}