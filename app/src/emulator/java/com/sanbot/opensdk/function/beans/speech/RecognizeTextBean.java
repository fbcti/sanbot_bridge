package com.sanbot.opensdk.function.beans.speech;

public class RecognizeTextBean
{
    private String text = "";
    private String engine = "";
    private boolean last = false;

    public String getText()
    {
        return text;
    }

    public void setText(String text)
    {
        this.text = text;
    }

    public String getEngine()
    {
        return engine;
    }

    public void setEngine(String engine)
    {
        this.engine = engine;
    }

    public boolean isLast()
    {
        return last;
    }

    public void setLast(boolean last)
    {
        this.last = last;
    }
}
