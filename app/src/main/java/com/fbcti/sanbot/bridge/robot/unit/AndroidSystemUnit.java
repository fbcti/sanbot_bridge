/**
 * @file        AndroidSystemUnit.java
 * @brief       Implements AndroidSystemUnit class.
 */
package com.fbcti.sanbot.bridge.robot.unit;

import android.os.Environment;
import android.support.annotation.NonNull;

import com.fbcti.sanbot.bridge.app.BridgeEventHost;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.FileUtils;
import com.fbcti.sanbot.bridge.util.MapUtils;
import com.fbcti.sanbot.bridge.util.StringUtils;

import java.io.File;
import java.util.Map;

/**
 * Manages Android system screen operations.
 *
 * This class extends the abstract BridgeUnit class to provide direct access to Android system
 * resources for screen overlay features.
 *
 * @version     1.0.001
 * @date        10 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class AndroidSystemUnit extends BridgeUnit
{
    /** Source label used for log messages. */
    private static final String TAG = "AndroidSystemUnit";

    /** External storage directory containing images that can be displayed on the robot screen. */
    private static final String IMAGE_DIRECTORY = FileUtils.BRIDGE_DATA_DIRECTORY + "/images";

    /** Callback host to which to send screen requests. */
    private ScreenHost screenHost;

    /** Current image file name. */
    private String imageFileName = null;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new AndroidSystemUnit instance.
     *
     * The base class constructor is called to copy the callback host to a member variable.
     *
     * @param   eventHost       callback host to which to forward bridge unit events
     *
     * The @p eventHost parameter represents an implementation of the BridgeEventHost interface that
     * allows this class to forward bridge unit events to be published by the BridgeService
     * instance.
     */
    public AndroidSystemUnit(BridgeEventHost eventHost)
    {
        super(eventHost);
    }

    /**
     * @name Android System Unit Operations
     * @{
     */

    /**
     * Displays an image on the robot screen.
     *
     * The image is displayed by BridgeMainActivity and remains visible until a request to hide the
     * image is received or the on-screen close button is pressed.
     *
     * @param   fileName        name of image file to display
     *
     * The @p fileName parameter is resolved relative to the bridge image directory defined by the
     * @c IMAGE_DIRECTORY constant, which itself is resolved relative to the external storage
     * directory. If empty, the image is hidden so the main window becomes visible.
     *
     * @return  DataResult instance containing operation result
     */
    public synchronized DataResult showScreenImage(String fileName)
    {
        if (screenHost == null) return DataResult.notavailable("screen_manager");

        imageFileName = (StringUtils.isBlank(fileName) == false) ? fileName.trim() : null;
        if (imageFileName == null) return screenHost.hideScreenImage();

        File requestedFile = new File(imageFileName);
        File imageDirectory = new File(Environment.getExternalStorageDirectory(), IMAGE_DIRECTORY);
        if (requestedFile.isAbsolute()) return DataResult.failure("file path must be relative", imageFileName);

        File imageFile = new File(imageDirectory, imageFileName);
        if (imageFile.isFile() == false) return DataResult.failure("image file not found", imageFile.getAbsolutePath());

        return screenHost.showScreenImage(imageFile);
    }

    /** @} */

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Performs unit initialization tasks.
     *
     * The screen manager object is copied to a class member variable, and the unit status is set.
     *
     * @param   screenManager       Android screen manager
     */
    public synchronized void init(ScreenHost screenManager)
    {
        this.screenHost = screenManager;
        unitStatus = (screenManager != null) ? UnitStatus.STARTED : UnitStatus.NOTINITIALIZED;
        BridgeLog.info(TAG, "Unit status is " + unitStatus);
    }

    /**
     * Performs unit shutdown tasks.
     *
     * Releases created resources.
     */
    public synchronized void shutdown()
    {
        screenHost = null;
        unitStatus = UnitStatus.SHUTDOWN;
        BridgeLog.info(TAG, "Unit status is " + unitStatus);
    }

    /**
     * Builds a data map containing unit status data.
     *
     * @return  instance of Java @c Map class containing status data
     */
    @NonNull
    public synchronized Map<String, Object> buildStatusData()
    {
        Map<String, Object> data = MapUtils.createMap();
        data.put("screenImage", (imageFileName != null) ? imageFileName : "<none>");
        return data;
    }

    /**
     * Defines BridgeService request callback contract.
     *
     * Ths interface must be implemented by the BridgeService class to allow sending request to show
     * and hide full-screen overlay images to the bridge service.
     */
    public interface ScreenHost
    {
        /**
         * Displays a full-screen overlay image.
         *
         * @param   imageFile       image file to be shown
         *
         * @return  DataResult instance containing result data
         */
        DataResult showScreenImage(File imageFile);

        /**
         * Hides the full-screen overlay image.
         *
         * @return  DataResult instance containing result data
         */
        DataResult hideScreenImage();
    }
}