package com.sanbot.opensdk.function.unit.interfaces.speech;

public interface WakenListener extends SpeechListener
{
    void onWakeUp();

    void onSleep();

    void onWakeUpStatus(boolean awake);
}
