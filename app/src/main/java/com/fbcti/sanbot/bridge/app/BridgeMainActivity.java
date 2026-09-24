/**
 * @file        BridgeMainActivity.java
 * @brief       Implements BridgeMainActivity class.
 */
package com.fbcti.sanbot.bridge.app;

import com.fbcti.sanbot.bridge.BuildConfig;
import com.fbcti.sanbot.bridge.R;
import com.fbcti.sanbot.bridge.robot.DataResult;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.StringUtils;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.SpeechManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides robot bridge main window.
 *
 * This class extends the Sanbot SDK @c BindBaseActivity class to provide a user interface that
 * allows the user to start, stop, and monitor a single BridgeService instance that represents the
 * background bridge service. The window displays both the bridge service status and  traffic log
 * messages.
 *
 * The bridge service is automatically started when the activity is created. By default, the main
 * window is visible. The bridge service is responsible to relaunch it whenever another robot home
 * window covers it.
 *
 * If this activity is launched with the @c EXTRA_START_HIDDEN intent extra set to @c true, it
 * starts without initializing the user interface, starts the bridge service, and finishes itself,
 * leaving the application running in pure background mode.
 *
 * Since speech recognition is only supported through an Android activity, the activity also owns
 * the BridgeSpeechHost instance that provides speech recognition capabilities. If the application
 * runs in pure background mode, the speech recognition feature is not available.
 *
 * The application requires the following Android permissions:
 * - @c ACCESS_WIFI_STATE,
 * - @c READ_EXTERNAL_STORAGE,
 * - @c WRITE_EXTERNAL_STORAGE,
 * - @c CAMERA.
 *
 * The application will request to add permissions that are not currently granted.
 *
 * To allow development and debugging of the application without actually deploying it on the Sanbot
 * robot a special emulator release of the application can be build that does not call any method
 * implemented by the Sanbot SDK.
 *
 * @version     1.0.001
 * @date        5 sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public class BridgeMainActivity extends BindBaseActivity
{
    /** Source label used for log messages. */
    private static final String TAG = "BridgeMainActivity";

    /** Intent extra to start the bridge service without user interface. */
    public static final String EXTRA_START_HIDDEN = "com.fbcti.sanbot.bridge.extra.START_HIDDEN";

    /** Request code for required permission request. */
    private static final int REQUIRED_PERMISSION_REQUEST_CODE = 1001;

    /** Number of milliseconds between scheduled updates of user interface controls. */
    private static final long UI_REFRESH_POLL_INTERVAL_MS = 1000L;

    /**
     * Maximum time to wait for a background request to finish on the user interface thread.
     *
     * Specifies the time the thread waits for confirmation if a full-screen overlay image is either
     * displayed or hidden.
     */
    private static final long UI_THREAD_OPERATION_TIMEOUT_MS = 5000L;

    /** Weak reference to the active activity. */
    private static volatile WeakReference<BridgeMainActivity> activeActivityReference = new WeakReference<>(null);

    /** Speech recognition capability provider. */
    private final BridgeSpeechHost speechHost = new BridgeSpeechHost(this);

    /** Flag specifying if user interface is available. */
    private boolean uiAvailable = false;

    /** Instance of user interface handler class. */
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    /**
     * @name User Interface Controls
     * @{
     */

    /** Text view control showing bridge service status. */
    private TextView statusTextView;

    /** Scroll view control containing traffic log view. */
    private ScrollView logScrollView;

    /** Text view control showing traffic log messages. */
    private TextView logTextView;

    /** Full-screen overlay view used to show images. */
    private FrameLayout screenImageOverlayView;

    /** String containing traffic log messages. */
    private String logText;

    /** Currently displayed screen image bitmap. */
    private Bitmap screenImageBitmap;

    /** Indicates if the screen image overlay is visible. */
    private boolean screenImageVisible;

    /** Indicates if system UI visibility is saved. */
    private boolean screenImageSystemUiSaved;

    /** System UI visibility value before showing image. */
    private int previousSystemUIVisibility;

    /** Runnable managing periodic updates of UI controls. */
    private final Runnable uiUpdateRunnable = new UiUpdateRunnable();

    /**
     * @}
     */

    /**
     * @name    Activity Callbacks
     * These callbacks override methods implemented by the Sanbot SDK @c BindBaseActivity class.
     * @{
     */

    /**
     * Callback invoked when the activity is created.
     *
     * If the launch intent is launched with the @c EXTRA_START_HIDDEN extra set to @c true, the
     * activity starts the background bridge service and finishes without initializing the user
     * interface, leaving the service running in pure background mode.
     *
     * For a normal launch, the main activity is bound to the Sanbot SDK, the user interface is
     * initialized, the member variables representing the user interface controls are set, and
     * callbacks are added to handle user interface control events.
     *
     * The bridge service requires a number of Android permissions. If permissions have already been
     * granted, the bridge service is started. If one or more permissions have not yet been granted,
     * a permissions request is submitted. The onRequestPermissionsResult() callback that handles
     * the event raised when the permissions are granted or denied is responsible for starting the
     * bridge service.
     *
     * @param   savedState      last saved state of activity
     */
    @Override
    protected void onCreate(Bundle savedState)
    {
        Intent launchIntent = getIntent();
        boolean startHidden = ((launchIntent != null) && (launchIntent.getBooleanExtra(EXTRA_START_HIDDEN, false)));
        BridgeLog.info(TAG, startHidden ? "Starting SanbotBridge main activity in hidden mode" : "Starting SanbotBridge main activity");

        // If application is started in hidden mode and all required permissions have been granted
        // the service is started without initializing the main activity.
        if (startHidden)
        {
            super.onCreate(savedState);
            if (hasRequiredPermissions())
            {
                startService(new Intent(this, BridgeService.class));
                finish();
            }
            else requestRequiredPermissions();
            return;
        }

        // Bind the main activity to the Sanbot SDK if not in emulator mode.
        if ((BuildConfig.EMULATOR_MODE == false) && (startHidden == false)) register(BridgeMainActivity.class);
        super.onCreate(savedState);

        // Remove EXTRA_START_HIDDEN flag from intent to guarantees the flag is only set if
        // explicitly added to the startup command.
        try { if (launchIntent != null) launchIntent.removeExtra(EXTRA_START_HIDDEN); }
        catch (NullPointerException ignore) {}
        setIntent(launchIntent);

        // Initialize user interface.
        activeActivityReference = new WeakReference<>(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.bridge_activity);
        setTitle(R.string.title_bridge);

        // Set member variables corresponding to user interface controls.
        statusTextView = findViewById(R.id.bridge_status_text);
        logScrollView = findViewById(R.id.log_scroll_view);
        logTextView = findViewById(R.id.log_text);

        // Start bridge service if required permissions are granted. If not, request permissions.
        if (hasRequiredPermissions()) startService(new Intent(this, BridgeService.class));
        else requestRequiredPermissions();

        // Refresh user interface controls.
        refreshUIControls();
    }

    /**
     * Callback invoked when activity is successfully bound to Sanbot SDK.
     *
     * The Sanbot SDK @c SpeechManager instance is retrieved and assigned to the speech host.
     */
    @Override
    protected void onMainServiceConnected()
    {
        speechHost.setSpeechManager((SpeechManager)getUnitManager(FuncConstant.SPEECH_MANAGER));
    }

    /**
     * Callback invoked when activity is destroyed.
     *
     * Any full-screen overlay image is hidden and the speech host is destroyed. The @c %onDestroy()
     * method implemented by the Sanbot SDK @c BindBaseActivity class is called to perform further
     * handling of the event.
     */
    @Override
    protected void onDestroy()
    {
        BridgeLog.info(TAG, "Destroying SanbotBridge main activity");
        hideScreenImage();
        speechHost.destroy();
        if (getActiveActivity() == this) activeActivityReference = new WeakReference<>(null);
        uiAvailable = false;
        super.onDestroy();
    }

    /**
     * Callback invoked when activity is paused.
     *
     * The Java @c Runnable instance performing scheduled user interface control updates is removed
     * from the user interface handler. If the BridgeService instance exists, the keep-visible loop
     * is disabled and the activity is marked hidden. This allows pressing the @e Back button to
     * hide the bridge screen without the service immediately bringing it back to the foreground.
     * The @c %onPause() method implemented by the Sanbot SDK @c BindBaseActivity base class is
     * called to perform further handling of the event.
     */
    @Override
    protected void onPause()
    {
        BridgeLog.info(TAG, "Pausing SanbotBridge main activity");
        uiAvailable = false;
        uiHandler.removeCallbacks(uiUpdateRunnable);
        BridgeService service = BridgeService.getInstance();
        if (service != null)
        {
            service.setKeepActivityVisible(false);
            service.setActivityVisible(false);
        }
        super.onPause();
    }

    /**
     * Called when activity is resumed.
     *
     * The @c %onResume() method implemented by the Sanbot SDK @c BindBaseActivity base class is
     * called to perform initial handling of the event. If the BridgeService instance exists, the
     * keep-visible loop is started to display this activity and keep it visible. The runnable
     * performing the scheduled refresh of user interface controls is added to the user interface
     * handler.
     */
    @Override
    protected void onResume()
    {
        BridgeLog.info(TAG, "Resuming SanbotBridge main activity");
        super.onResume();
        activeActivityReference = new WeakReference<>(this);
        uiAvailable = true;

        BridgeService service = BridgeService.getInstance();
        if (service != null)
        {
            service.setActivityVisible(true);
            service.setKeepActivityVisible(true);
        }

        uiHandler.removeCallbacks(uiUpdateRunnable);
        uiHandler.post(uiUpdateRunnable);
        refreshUIControls();
    }

    /**
     * Callback invoked when the activity window gains or loses focus.
     *
     * The @c %onWindowFocusChanged() method implemented by the Sanbot SDK @c BindBaseActivity base
     * class is called to perform initial handling of the event. Immersive mode must be re-applied
     * after focus changes because Android may restore the navigation controls while dispatching
     * focus to the activity.
     *
     * @param   hasFocus        @c true if activity window has focus
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if ((hasFocus) && (screenImageVisible)) setScreenImageOverlayFlags();
    }

    /**
     * Callback invoked when the @e Back button is pressed.
     *
     * This method just writes a log message. The @c %onBackPressed() method implemented by the
     * Sanbot SDK @c BindBaseActivity base class is called to perform further handling of the event.
     */
    public void onBackPressed()
    {
        BridgeLog.info(TAG, "Back button pressed");
        super.onBackPressed();
    }

    /**
     * Callback invoked when a menu is created.
     *
     * The menu options are added to the menu. The method returns @c true to display the menu.
     *
     * @param   menu            menu that is created
     *
     * @return  the method always returns the @c true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.app_actions, menu);
        return true;
    }

    /**
     * Callback invoked when a menu option is selected.
     *
     * The handler method for the selected menu option is called. The method returns @c true for the
     * handled menu options to stop default handling of the event. For other menu options the method
     * just calls the handler implemented by the Sanbot SDK @c BindBaseActivity base class.
     *
     * @param   item            selected menu item
     *
     * @return  @c true for handled events, return value of default handler for other events
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.action_settings)
        {
            openSettings();
            return true;
        }
        if (itemId == R.id.action_restart_service)
        {
            restartBridge();
            return true;
        }
        if (itemId == R.id.action_stop_bridge)
        {
            stopBridge();
            return true;
        }
        if (itemId == R.id.action_about)
        {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Callback invoked when permissions are granted or denied.
     *
     * The @c %onRequestPermissionsResult() method implemented by the Sanbot SDK @c BindBaseActivity
     * base class is called to perform initial handling of the event. If the request code is equal
     * to @c REQUIRED_PERMISSION_REQUEST_CODE, i.e. this event was triggered by the request sent by
     * this application, the method checks if these permissions are granted and if so starts the
     * bridge service. If permissions are denied, the application exits.
     *
     * @param   requestCode     permission request code
     * @param   permissions     list of requested permissions
     * @param   grantResults    list of granted permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // If request is not for required bridge permissions no further handling is required.
        if (requestCode != REQUIRED_PERMISSION_REQUEST_CODE) return;

        // Check if requested permissions were granted.
        boolean granted = (grantResults.length > 0);
        for (int grantResult : grantResults) granted &= (grantResult == PackageManager.PERMISSION_GRANTED);

        // Start bridge service if permissions are granted. If not, stop the app.
        if (granted) startService(new Intent(this, BridgeService.class));
        else
        {
            Toast.makeText(this, R.string.required_permission_needed, 3*Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
        }
    }

    /** @} */

    /***********************************************************************************************
     * PACKAGE-PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Displays a full-screen overlay image.
     *
     * Full-screen overlay image changes must run on the Android main activity user interface
     * thread but requests to display the image always originate from a background thread (i.e. from
     * the bridge service thread), so showOverlayImageFromBackgroundThread() is called to request
     * the main activity user interface thread to execute the showOverlayImage(). To allow the main
     * activity itself to request the image to be displayed, showOverlayImage() is called directly
     * if already on the main activity user interface thread (but this should currently never be the
     * case).
     *
     * @param   imageFile       image file to display
     *
     * @return  DataResult instance describing success or failure
     */
    DataResult showScreenImage(final File imageFile)
    {
        // If UI is not available the image can't be shown.
        if (uiAvailable == false) return DataResult.failure("main activity user interface not available");

        // If image file name is not specified there is nothing to show.
        if (imageFile == null) return DataResult.failure("image file not specified");

        // If image file does not exist there is nothing to show.
        if ((imageFile.exists() == false) || (imageFile.isFile() == false))
            return DataResult.failure("image file not found", imageFile.getAbsolutePath());

        // Decode the image.
        final Bitmap bitmap = decodeScreenImage(imageFile);
        if (bitmap == null) return DataResult.failure("failed to decode image file", imageFile.getAbsolutePath());

        if (Looper.myLooper() != Looper.getMainLooper())
        {
            // Request is coming from background (t.e not user interface) thread.
            return showOverlayImageFromBackgroundThread(bitmap);
        }

        // Request is coming from main activity user interface thread. For future use only.
        return showOverlayImage(bitmap);
    }

    /**
     * Hides the full-screen overlay image.
     *
     * Full-screen overlay image changes must run on the Android main activity user interface
     * thread but requests to hide the image always originate from a background thread (i.e. from
     * the bridge service thread), so hideOverlayImageFromBackgroundThread() is called to request
     * the main activity user interface thread to execute the hideOverlayImage(). To allow the main
     * activity itself to request the image to be hidden, hideOverlayImage() is called directly if
     * already on the main activity user interface thread (but this should currently never be the
     * case).
     *
     * @return  DataResult instance describing success or failure
     */
    DataResult hideScreenImage()
    {
        if (Looper.myLooper() != Looper.getMainLooper())
        {
            // Request is coming from background (I.e non-UI) thread.
            return hideOverlayImageFromBackgroundThread();
        }

        // Request is coming from main activity user interface thread. For future use only.
        return hideScreenImageOverlay();
    }

    /**
     * Returns the main activity instance specified by the weak reference.
     *
     * @return  active bridge activity, or @c null if not available
     */
    static BridgeMainActivity getActiveActivity()
    {
        return activeActivityReference.get();
    }

    /**
     * Returns the active speech host.
     *
     * @return  active speech host, or @c null if not available
     */
    @Nullable
    static BridgeSpeechHost getSpeechHost()
    {
        BridgeMainActivity activity = getActiveActivity();
        return (activity != null) ? activity.speechHost : null;
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Opens the bridge configuration activity.
     *
     * This method is called if the user clicks the @e Settings button in the menu bar. It starts
     * the activity that provides the bridge settings screen.
     */
    private void openSettings()
    {
        startActivity(new Intent(this, BridgeSettingsActivity.class));
    }

    /**
     * Restarts the bridge service.
     *
     * This method is called if the user selects the <em>Restart Bridge</em> option from the menu.
     * If the BridgeService instance exists, it is requested to restart itself. If it does not 
     * exist, the @c startService() method implemented by the Sanbot SDK @c BindBaseActivity base
     * class is called to start the service.
     */
    private void restartBridge()
    {
        BridgeService service = BridgeService.getInstance();
        if (service != null)
        {
            service.restartBridgeService(true);
            refreshUIControls();
            return;
        }

        // The service does not exist, so most likely the service is not started. Call stopService()
        // before starting it anyway.
        Intent serviceIntent = new Intent(this, BridgeService.class);
        stopService(serviceIntent);
        startService(serviceIntent);
        refreshUIControls();
    }

    /**
     * Stops the bridge.
     *
     * This method is called if the user selects the <em>Stop Bridge</em> option from the menu. If
     * the BridgeService instance exists, it is requested to no longer keep the activity visible. 
     * The @c stopService() method implemented by the Sanbot SDK @c BindBaseActivity base class is
     * called to stop the service if exists, and the activity is terminated, effectively stopping
     * the application.
     */
    private void stopBridge()
    {
        BridgeService service = BridgeService.getInstance();
        if (service != null) service.setKeepActivityVisible(false);

        // The service may or may not exist, so call stopService() just in case.
        stopService(new Intent(this, BridgeService.class));
        finishAndRemoveTask();
    }

    /**
     * Displays a full-screen overlay image from a non-UI thread.
     *
     * This method requests the main activity user interface thread to execute showOverlayImage()
     * and waits either for that method to return or until a timeout occurs.
     *
     * @param   bitmap       bitmap to display
     *
     * @return  DataResult specifying operation result
     */
    @NonNull
    private DataResult showOverlayImageFromBackgroundThread(final Bitmap bitmap)
    {
        final CountDownLatch overlayShown = new CountDownLatch(1);
        final AtomicReference<DataResult> resultRef = new AtomicReference<>();
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    resultRef.set(showOverlayImage(bitmap));
                }
                catch (RuntimeException e)
                {
                    recycleBitmap(bitmap);
                    resultRef.set(DataResult.failure("screen image display failed",
                        e.getClass().getSimpleName() + (StringUtils.isBlank(e.getMessage()) ? "" : ": " + e.getMessage())));
                }
                finally
                {
                    overlayShown.countDown();
                }
            }
        });

        try
        {
            if (overlayShown.await(UI_THREAD_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            {
                DataResult result = resultRef.get();
                return (result != null) ? result : DataResult.failure("empty screen image display result");
            }
            return DataResult.failure("screen image display timed out");
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return DataResult.failure("screen image display interrupted");
        }
    }

    /**
     * Displays a full-screen overlay image from the current UI thread.
     *
     * If an overlay image is already displayed it is detached before displaying a new image.
     *
     * @param   bitmap          bitmap to display
     *
     * @return  DataResult specifying operation result
     */
    private DataResult showOverlayImage(Bitmap bitmap)
    {
        if (bitmap == null) return DataResult.failure("screen image bitmap not available");

        // Remove previous image.
        Bitmap previousBitmap = screenImageBitmap;
        detachOverlay();
        screenImageBitmap = bitmap;
        screenImageVisible = true;

        try
        {
            enterOverlayImageMode();
            attachOverlay(bitmap);
        }
        catch (RuntimeException e)
        {
            detachOverlay();
            screenImageBitmap = null;
            screenImageVisible = false;
            exitOverlayImageMode();
            recycleBitmap(bitmap);
            if (previousBitmap != bitmap) recycleBitmap(previousBitmap);
            return DataResult.failure("screen image display failed",
                e.getClass().getSimpleName() + (StringUtils.isBlank(e.getMessage()) ? "" : ": " + e.getMessage()));
        }

        // Recycle bitmap if not same as current bitmap.
        if (previousBitmap != bitmap) recycleBitmap(previousBitmap);

        return DataResult.success("screen image displayed");
    }

    /**
     * Hides full-screen overlay image from a non-UI thread.
     *
     * This method requests the main activity user interface thread to execute hideOverlayImage()
     * and waits either for that method to return or until a timeout occurs.
     *
     * @return  DataResult specifying operation result
     */
    @NonNull
    private DataResult hideOverlayImageFromBackgroundThread()
    {
        final CountDownLatch overlayHidden = new CountDownLatch(1);
        final AtomicReference<DataResult> resultRef = new AtomicReference<>();
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    resultRef.set(hideScreenImageOverlay());
                }
                catch (RuntimeException e)
                {
                    resultRef.set(DataResult.failure("screen image hide failed",
                        e.getClass().getSimpleName() + (StringUtils.isBlank(e.getMessage()) ? "" : ": " + e.getMessage())));
                }
                finally
                {
                    overlayHidden.countDown();
                }
            }
        });

        try
        {
            if (overlayHidden.await(UI_THREAD_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            {
                DataResult result = resultRef.get();
                return (result != null) ? result : DataResult.failure("empty screen image hide result");
            }
            return DataResult.failure("screen image hide timeout");
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return DataResult.failure("screen image hide interrupted");
        }
    }

    /**
     * Hides the full-screen overlay image from the current UI thread.
     *
     * This method updates activity and window state, so it must be called on the main activity UI
     * thread.
     *
     * @return  DataResult instance describing success or failure
     */
    private DataResult hideScreenImageOverlay()
    {
        boolean wasVisible = screenImageVisible;
        screenImageVisible = false;
        detachOverlay();
        clearScreenImageBitmap();
        if ((wasVisible) || (screenImageSystemUiSaved)) exitOverlayImageMode();
        return DataResult.success("screen image hidden");
    }

    /**
     * Attaches the screen overlay to the activity window.
     *
     * The regular activity content area excludes the robot firmware's bottom system strip. An
     * attached application window can request a layout in the full screen area.
     *
     * @param   bitmap          bitmap to display
     */
    private void attachOverlay(Bitmap bitmap)
    {
        screenImageOverlayView = new FrameLayout(this);
        screenImageOverlayView.setBackgroundColor(0xFF000000);
        screenImageOverlayView.setClickable(true);
        screenImageOverlayView.setFocusable(true);

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setContentDescription(getString(R.string.screen_image_content_description));
        screenImageOverlayView.addView(imageView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        Button dismissButton = new Button(this);
        dismissButton.setText(R.string.screen_image_dismiss);
        dismissButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                hideScreenImage();
            }
        });

        int margin = Math.round(24.0f * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.RIGHT);
        buttonParams.setMargins(margin, margin, margin, margin);
        screenImageOverlayView.addView(dismissButton, buttonParams);

        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowParams.token = getWindow().getDecorView().getWindowToken();

        getWindowManager().addView(screenImageOverlayView, windowParams);
        screenImageOverlayView.requestFocus();
        setScreenImageOverlayFlags();
        screenImageOverlayView.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                if (screenImageVisible) setScreenImageOverlayFlags();
            }
        }, 250L);
    }

    /**
     * Detaches the screen overlay if it is visible.
     */
    private void detachOverlay()
    {
        if (screenImageOverlayView == null) return;

        try { getWindowManager().removeView(screenImageOverlayView); }
        catch (IllegalArgumentException ignored) {}

        screenImageOverlayView = null;
    }

    /**
     * Hides system user interface controls while the screen overlay image is visible.
     */
    private void enterOverlayImageMode()
    {
        View decorView = getWindow().getDecorView();
        if (screenImageSystemUiSaved == false)
        {
            previousSystemUIVisibility = decorView.getSystemUiVisibility();
            screenImageSystemUiSaved = true;
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setScreenImageOverlayFlags();
        if (getSupportActionBar() != null) getSupportActionBar().hide();
    }

    /**
     * Restores system user interface controls after the screen overlay image is hidden.
     */
    private void exitOverlayImageMode()
    {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (screenImageSystemUiSaved)
        {
            getWindow().getDecorView().setSystemUiVisibility(previousSystemUIVisibility);
            screenImageSystemUiSaved = false;
        }
        if (getSupportActionBar() != null) getSupportActionBar().show();
    }

    /**
     * Applies immersive system user interface flags for the overlay image.
     */
    private void setScreenImageOverlayFlags()
    {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (screenImageOverlayView != null) screenImageOverlayView.setSystemUiVisibility(flags);
    }

    /**
     * Downsamples the image file to (roughly) the screen size.
     *
     * @param   imageFile       image file to downsample
     *
     * @return  downsampled bitmap, or @c null on failure
     */
    private Bitmap decodeScreenImage(File imageFile)
    {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int targetWidth = Math.max(1, metrics.widthPixels);
        int targetHeight = Math.max(1, metrics.heightPixels);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), bounds);
        if ((bounds.outWidth <= 0) || (bounds.outHeight <= 0)) return null;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateBitmapSampleSize(bounds, targetWidth, targetHeight);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
    }

    /**
     * Calculates a power-of-two downsample factor for bitmap decoding.
     *
     * The width and height are repeatedly divided by two until no longer lager than the width and
     * height specified by the target size.
     *
     * @param   options         bitmap bounds options
     * @param   targetWidth     target bitmap width
     * @param   targetHeight    target bitmap height
     *
     * @return  bitmap sample size
     */
    private int calculateBitmapSampleSize(BitmapFactory.Options options, int targetWidth, int targetHeight)
    {
        int sampleSize = 1;
        int imageWidth = options.outWidth;
        int imageHeight = options.outHeight;

        while (((imageWidth/(sampleSize*2)) >= targetWidth) && ((imageHeight/(sampleSize*2)) >= targetHeight))
        {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    /**
     * Releases the bitmap currently displayed as a full-screen overlay image.
     */
    private void clearScreenImageBitmap()
    {
        recycleBitmap(screenImageBitmap);
        screenImageBitmap = null;
    }

    /**
     * Releases a bitmap if it is still valid.
     *
     * @param   bitmap          bitmap to recycle
     */
    private void recycleBitmap(Bitmap bitmap)
    {
        if ((bitmap != null) && (bitmap.isRecycled() == false)) bitmap.recycle();
    }

    /**
     * Shows application information.
     *
     * This method is called if the user selects the @e About option from the menu. It shows an
     * alert screen with information on the application.
     */
    private void showAboutDialog()
    {
        String message = getString(R.string.about_message,
                getString(R.string.app_name),
                BuildConfig.VERSION_NAME,
                getString(R.string.about_copyright));

        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setIcon(R.mipmap.ic_launcher)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * Checks if the application has permissions required by the bridge service.
     *
     * For Android versions up to @e Lollipop the permissions are always granted if specified in the
     * manifest file. For later version an explicit check is performed.
     *
     * @return  @c true if app has required permissions, @c false if not
     */
    private boolean hasRequiredPermissions()
    {
        return ((android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M)
            || ((ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            && (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            && (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        ));
    }

    /**
     * Requests permissions required by the bridge service.
     *
     * If the requested permissions are granted, an event with the specified request code is raised.
     * This event will trigger the execution of the onRequestPermissionsResult() method.
     */
    private void requestRequiredPermissions()
    {
        ActivityCompat.requestPermissions(this,
            new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            },
            REQUIRED_PERMISSION_REQUEST_CODE);
    }

    /**
     * Populates the service status text view control with the specified string.
     *
     * @param   value           string value representing bridge status
     */
    private void setStatusText(String value)
    {
        // Exit if the control has not yet been created.
        if (statusTextView == null) return;

        statusTextView.setText(value);
    }

    /**
     * Populates the traffic log text view control with the specified string.
     *
     * If necessary, a message is put on the message queue of the associated scroll control to
     * scroll to the bottom of the text view control.
     *
     * @param   value           string value representing traffic log
     */
    private void setLogText(String value)
    {
        // Exit if the control has not yet been created.
        if (statusTextView == null) return;

        // If text to display is already being shown there is nothing to do.
        if (value == null) value = "";
        if (value.equals(logText)) return;

        // Set text.
        logText = value;
        if (logTextView != null) logTextView.setText(value);

        scrollLogToBottom();
    }

    /**
     * Scrolls the traffic log to the bottom after the updated text has been measured.
     */
    private void scrollLogToBottom()
    {
        if ((logScrollView == null) || (logTextView == null)) return;

        logScrollView.post(new Runnable()
        {
            @Override
            public void run()
            {
                int viewportHeight = logScrollView.getHeight() - logScrollView.getPaddingTop()
                    - logScrollView.getPaddingBottom();
                int scrollY = Math.max(0, logTextView.getHeight() - viewportHeight);
                logScrollView.scrollTo(0, scrollY);
            }
        });
    }

    /**
     * Populate UI controls with actual values.
     *
     * If the BridgeService instance does not exist, the bridge status and traffic log text view
     * controls are populated with initial texts. If it does exit, the controls are populated with
     * the actual bridge status and traffic log retrieved from the service.
     */
    private void refreshUIControls()
    {
        BridgeService service = BridgeService.getInstance();
        if (service == null)
        {
            setStatusText(getString(R.string.bridge_not_active));
            setLogText(getString(R.string.log_waiting));
            return;
        }

        String bridgeStatus = service.getBridgeStatus(this);
        String trafficLog = service.getTrafficLog();
        setStatusText(bridgeStatus);
        setLogText(StringUtils.isBlank(trafficLog) ? getString(R.string.log_waiting) : trafficLog);
    }


    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Helper class responsible for running periodic updates of user interface controls.
     */
    private final class UiUpdateRunnable implements Runnable
    {
        /**
         * Callback invoked if a scheduled task must be executed.
         *
         * The user interface controls are updated by calling refreshUIControls(), and the task
         * reschedules itself. This loop continues until the runnable is explicitly removed from the
         * user interface handler.
         */
        @Override
        public void run()
        {
            refreshUIControls();
            uiHandler.postDelayed(this, UI_REFRESH_POLL_INTERVAL_MS);
        }
    }
}