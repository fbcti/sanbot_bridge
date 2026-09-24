/**
 * @file        HttpStatus.java
 * @brief       Implements HttpStatus enumerator.
 */
package com.fbcti.sanbot.bridge.transport;

/**
 * Provides functions for converting HTTP status codes for and from HTTP status texts.
 *
 * @version     1.0.001
 * @date        21 Jul 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public enum HttpStatus
{
    OK(200, "Ok"),                                          ///< 200 OK
    NO_CONTENT(204, "No content"),                          ///< 204 No content
    UNKNOWN(210, "Unknown result"),                         ///< 210 Unknown result
    BAD_REQUEST(400, "Bad request"),                        ///< 400 Bad request
    UNAUTHORIZED(401, "Unauthorized"),                      ///< 401 Unauthorized
    FORBIDDEN(403, "Forbidden"),                            ///< 403 Forbidden
    NOT_FOUND(404, "Not found"),                            ///< 404 Not found
    NOT_ALLOWED(405, "Not allowed"),                        ///< 405 Not allowed
    NOT_ACCEPTABLE(406, "Not acceptable"),                  ///< 406 Not acceptable
    INTERNAL_SERVER_ERROR(500, "Internal server error"),    ///< 500 Internal server error
    NOT_IMPLEMENTED(501, "Not implemented"),                ///< 501 Not implemented
    SERVICE_UNAVAILABLE(503, "Service unavailable");        ///< 503 Service unavailable

    /** Http status code. */
    public final int code;

    /** Http status text. */
    public final String text;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new HttpStatus instance.
     *
     * The function parameters are copied to class member variables.
     *
     * @param code              HTTP status code
     * @param text              HTTP status text
     */
    HttpStatus(int code, String text)
    {
        this.code = code;
        this.text = text;
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Returns the HttpStatus enumerator value matching the specified status text.
     *
     * @param   text            HTTP status text
     *
     * @return  instance of HttpStatus enumerator
     *
     * If no value matching the text is found the @c UNKNOWN value is returned.
     */
    public static HttpStatus fromText(String text)
    {
        if (text == null) return UNKNOWN;

        String statusText = text.trim();
        for (HttpStatus status : values())
        {
            if (status.text.equalsIgnoreCase(statusText)) return status;
        }
        return UNKNOWN;
    }

    /**
     * Returns the HTTP status code matching the specified HTTP status text.
     *
     * @param   text            HTTP status text
     *
     * @return  numerical status code
     *
     * If the text does not match any of the configured texts the @c UNKNOWN code is returned.
     */
    public static int toCode(String text)
    {
        return fromText(text).code;
    }

    /**
     * Returns the HTTP status text matching the specified HTTP status code.
     *
     * @param   code            HTTP status code
     *
     * @return  HTTP status text
     *
     * If the code does not match any of the configured codes the @c INTERNAL_SERVER_ERROR text is
     * returned.
     */
    public static String toText(int code)
    {
        for (HttpStatus status : values())
        {
            if (status.code == code) return status.text;
        }

        if ((code >= 200) && (code < 300)) return OK.text;
        if ((code >= 400) && (code < 500)) return BAD_REQUEST.text;
        return INTERNAL_SERVER_ERROR.text;
    }

    /**
     * Returns HTTP status code and text as a angle string
     *
     * @return  string containing both status code and text
     */
    @Override
    public String toString()
    {
        return String.format("%s %s", code, text);
    }
}