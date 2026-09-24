package com.sanbot.opensdk.function.beans.speech;

public class SpeakStatus
{
    private String id = "";
    private String engine = "";
    private int progress = 0;

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getEngine()
    {
        return engine;
    }

    public void setEngine(String engine)
    {
        this.engine = engine;
    }

    public int getProgress()
    {
        return progress;
    }

    public void setProgress(int progress)
    {
        this.progress = progress;
    }
}
