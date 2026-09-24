package com.sanbot.opensdk.beans;

public class OperationResult
{
    private final int errorCode;
    private final String description;
    private final String result;

    public OperationResult()
    {
        this(1, "Emulator operation result", "emulator");
    }

    public OperationResult(int errorCode, String description, String result)
    {
        this.errorCode = errorCode;
        this.description = description;
        this.result = result;
    }

    public int getErrorCode()
    {
        return errorCode;
    }

    public String getDescription()
    {
        return description;
    }

    public String getResult()
    {
        return result;
    }
}
