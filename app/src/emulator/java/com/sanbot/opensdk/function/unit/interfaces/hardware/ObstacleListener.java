package com.sanbot.opensdk.function.unit.interfaces.hardware;

public interface ObstacleListener extends HardWareListener
{
    void onObstacleStatus(boolean blocked);
}
