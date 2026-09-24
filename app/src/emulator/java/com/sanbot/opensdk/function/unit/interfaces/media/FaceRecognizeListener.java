package com.sanbot.opensdk.function.unit.interfaces.media;

import com.sanbot.opensdk.function.beans.FaceRecognizeBean;

import java.util.List;

public interface FaceRecognizeListener extends MediaListener
{
    public void recognizeResult(List<FaceRecognizeBean> faceData);
}
