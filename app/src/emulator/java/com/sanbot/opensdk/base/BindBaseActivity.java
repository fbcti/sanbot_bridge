package com.sanbot.opensdk.base;

import android.support.v7.app.AppCompatActivity;
public abstract class BindBaseActivity extends AppCompatActivity
{
    protected Object getUnitManager(Object manager) { return null; }
    protected void register(Class<?> activityClass) {}
    protected void onMainServiceConnected() {}
}
