package com.sanbot.opensdk.function.unit.interfaces.speech;

import com.sanbot.opensdk.function.beans.speech.SpeakStatus;

public interface SpeakListener extends SpeechListener
{
    void onSpeakStatus(SpeakStatus speakStatus);
}
