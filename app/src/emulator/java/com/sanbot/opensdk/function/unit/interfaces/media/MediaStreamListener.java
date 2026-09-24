package com.sanbot.opensdk.function.unit.interfaces.media;

public interface MediaStreamListener extends MediaListener
{
    void getVideoStream(int channel, byte[] buffer, int width, int height);

    void getAudioStream(int channel, byte[] buffer);
}
