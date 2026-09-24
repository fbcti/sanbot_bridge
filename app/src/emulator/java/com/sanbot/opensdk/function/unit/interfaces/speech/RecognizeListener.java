package com.sanbot.opensdk.function.unit.interfaces.speech;

import com.sanbot.opensdk.function.beans.speech.Grammar;
import com.sanbot.opensdk.function.beans.speech.RecognizeTextBean;

public interface RecognizeListener extends SpeechListener
{
    void onStartRecognize();

    void onStopRecognize();

    boolean onRecognizeResult(Grammar grammar);

    void onRecognizeText(RecognizeTextBean recognizeTextBean);

    void onRecognizeVolume(int volume);

    void onError(int errorCode, int subCode);
}
