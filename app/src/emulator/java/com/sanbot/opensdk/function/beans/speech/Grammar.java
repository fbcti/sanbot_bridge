package com.sanbot.opensdk.function.beans.speech;

public class Grammar
{
    private String text = "";
    private String engine = "";
    private String action = "";
    private String initialData = "";
    private String topic = "";

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

    public String getAction()
    {
        return action;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getInitialData()
    {
        return initialData;
    }

    public void setInitialData(String initialData)
    {
        this.initialData = initialData;
    }

    public String getTopic()
    {
        return topic;
    }

    public void setTopic(String topic)
    {
        this.topic = topic;
    }
}
