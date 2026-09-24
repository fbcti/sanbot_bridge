package com.sanbot.opensdk.base;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public abstract class BindBaseService extends Service
{
    protected Object getUnitManager(Object manager)
    {
        return null;
    }

    public byte[] getFaceImage() { return new byte[0]; }

    protected void register(Class<?> serviceClass)
    {
    }

    protected void connService()
    {
    }

    protected void onMainServiceConnected()
    {
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }
}
