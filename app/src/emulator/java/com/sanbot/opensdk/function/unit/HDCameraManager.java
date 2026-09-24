package com.sanbot.opensdk.function.unit;

import android.graphics.Bitmap;

import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.StreamOption;
import com.sanbot.opensdk.function.unit.interfaces.media.MediaListener;

public class HDCameraManager
{
    public void setMediaListener(MediaListener listener) {}

    public OperationResult openStream(StreamOption option)
    {
        return new OperationResult();
    }

    public OperationResult closeStream(int handle)
    {
        return new OperationResult();
    }

    public Bitmap getVideoImage() { return null; }
}
