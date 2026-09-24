package com.sanbot.opensdk.function.unit;

import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.SpeakOption;
import com.sanbot.opensdk.function.beans.WakeUpOption;
import com.sanbot.opensdk.function.unit.interfaces.speech.SpeechListener;

public class SpeechManager
{
    public OperationResult startSpeak(String text)
    {
        return new OperationResult();
    }

    public OperationResult startSpeak(String text, SpeakOption option)
    {
        return new OperationResult();
    }

    public OperationResult stopSpeak()
    {
        return new OperationResult();
    }

    public OperationResult isSpeaking()
    {
        return new OperationResult();
    }

    // POC_ACTIVITY_SPEECH_BEGIN: emulator methods used by the activity-owned speech host.
    public OperationResult doWakeUp()
    {
        return new OperationResult();
    }

    public OperationResult doWakeUp(WakeUpOption option)
    {
        return new OperationResult();
    }

    public OperationResult doSleep()
    {
        return new OperationResult();
    }

    public void setOnSpeechListener(SpeechListener listener)
    {
    }
    // POC_ACTIVITY_SPEECH_END

}
