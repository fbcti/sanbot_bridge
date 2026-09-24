/**
 * @file        BridgeSettingsActivity.java
 * @brief       Implements BridgeSettingsActivity class.
 */
package com.fbcti.sanbot.bridge.app;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.fbcti.sanbot.bridge.R;
import com.fbcti.sanbot.bridge.config.BridgeConfig;
import com.fbcti.sanbot.bridge.robot.unit.AndroidTtsUnit;
import com.fbcti.sanbot.bridge.robot.unit.SanbotTtsUnit;
import com.fbcti.sanbot.bridge.util.ValueUtils;

/**
 * Provides bridge application settings window.
 *
 * This class extends the Android @c AppCompatActivity class to provide a user interface that allows
 * the user to update the bridge service configuration. It contains controls for setting
 * - Robot name.
 * - HTTP listener port.
 * - API Authorization key.
 * - Flag specifying which text-to-speech engine is used (Sanbot or native Android).
 * - Flag specifying if speech recognition is enabled.
 * - Flag specifying if face detection is enabled.
 * - Flag specifying if home alarm detection is enabled.
 *
 * The @e Save button allows updated settings to be saved to teh configuration file, the @e Reset
 * button can be clicked to reset the settings to their default values. Changes do take effect after
 * restarting the bridge application.
 *
 * @version     1.0.001
 * @date        5 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public class BridgeSettingsActivity extends AppCompatActivity
{
    /**
     * @name User Interface Controls
     * @{
     */

    /** Text edit control showing friendly robot name. */
    private EditText robotNameEditText;

    /** Text edit control showing HTTP listener port. */
    private EditText httpPortEditText;

    /** Text edit control showing API authorization key. */
    private EditText apiKeyEditText;

    /** Switch enabling use of Android TTS capabilities. */
    private Switch androidTtsSwitch;

    /** Switch enabling speech recognition. */
    private Switch enableAsrSwitch;

    /** Switch enabling face detection. */
    private Switch faceDetectionSwitch;

    /** Switch enabling home alarm detection. */
    private Switch homeAlarmDetectionSwitch;

    /**
     * @}
     */

    /**
     * @name    Activity Callbacks
     * These callbacks override methods implemented by the Sanbot SDK @c BindBaseActivity class.
     * @{ 
     */ 

    /**
     * Callback invoked when activity is created.
     *
     * The user interface is initialized, the member variables representing the UI controls are set,
     * and event handlers are added to relevant controls. The updateUIControls() method is called to
     * display the actual values of the configuration settings.
     *
     * @param   savedState      last saved state of activity
     */
    @Override
    protected void onCreate(Bundle savedState)
    {
        super.onCreate(savedState);
        setContentView(R.layout.settings_activity);
        setTitle(R.string.title_settings);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set member variables corresponding to UI controls.
        robotNameEditText = findViewById(R.id.settings_robot_name_edit_text);
        httpPortEditText = findViewById(R.id.settings_http_port_edit_text);
        apiKeyEditText = findViewById(R.id.settings_api_key_edit_text);
        androidTtsSwitch = findViewById(R.id.settings_android_tts_switch);
        enableAsrSwitch = findViewById(R.id.settings_enable_asr_switch);
        faceDetectionSwitch = findViewById(R.id.settings_face_detection_switch);
        homeAlarmDetectionSwitch = findViewById(R.id.settings_home_alarm_detection_switch);

        // Set event handlers for save and reset buttons.
        Button saveButton = findViewById(R.id.settings_save_button);
        Button resetButton = findViewById(R.id.settings_reset_button);
        saveButton.setOnClickListener(v -> saveSettings());
        resetButton.setOnClickListener(v -> resetConfig());

        // Update UI controls.
        updateUIControls();
    }

    /**
     * Callback invoked when activity is paused.
     *
     * The runnable performing scheduled UI control updates is removed from the UI handler. The
     * @c %onPause() method implemented by the Android SDK @c AppCompatActivity class is called to
     * perform further handling of the event.
     */
    @Override
    protected void onPause()
    {
        BridgeService service = BridgeService.getInstance();
        if (service != null) service.setActivityVisible(false);
        super.onPause();
    }

    /**
     * Callback invoked when activity is resumed.
     *
     * The @c %onResume() method implemented by the Sanbot SDK @c AppCompatActivity class is called
     * to perform initial handling of the event. If the BridgeService instance exists, it is
     * requested to show this activity. The runnable that performs the scheduled refresh of user
     * interface controls is added to the UI handler.
     */
    @Override
    protected void onResume()
    {
        super.onResume();

        BridgeService service = BridgeService.getInstance();
        if (service != null) service.setActivityVisible(true);

        updateUIControls();
    }

    /**
     * Callback invoked when menu is created.
     *
     * If the menu item linked to the @e Back button in the tool bar is selected the activity is
     * closed, and the method returns @c true to stop further handling of the event. For other
     * menu options the method calls the handler implemented by the @c AppCompatActivity base class.
     *
     * @param   item            selected menu item
     *
     * @return  @c true for handled events, return value of default handler for other events
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
        {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** @} */

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Saves the bridge settings.
     *
     * This method is called if the @e Save button is clicked. It retrieves the value of the
     * settings specified in the text edit controls and calls the BridgeConfig.save() method to
     * save the settings to the configuration file.
     */
    private void saveSettings()
    {
        // Get HTTP port and API key from controls.
        String robotName = getRobotName();
        Integer httpPort = getHttpPort();
        String apiKey = getApiKey();
        if ((robotName == null) || (httpPort == null) || (apiKey == null)) return;

        // Update persisted configuration, preserving settings not exposed here.
        BridgeConfig settings = getCurrentBridgeSettings();
        settings.setRobotName(robotName);
        settings.setHttpPort(httpPort);
        settings.setApiKey(apiKey);
        settings.setTtsName(getUseAndroidTtsEngine() ? AndroidTtsUnit.TTS_NAME : SanbotTtsUnit.TTS_NAME);
        settings.setEnableAsr(isEnableAsrSelected());
        settings.setEnableFaceDetection(getEnableFaceDetection());
        settings.setEnableAlarmDetection(getEnableHomeAlarmDetection());

        // Save the new config.
        String message = (settings.save() == true)
            ? getString(R.string.config_save_success, settings.getConfigFilePath())
            : getString(R.string.config_save_error, settings.getConfigFilePath());
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Resets bridge settings to defaults.
     *
     * This method is called if the @e Reset button is clicked. It resets the controls to the
     * default values without applying them to the running bridge. The defaults can then be saved
     * to the configuration file by clicking the @e Save button.
     */
    private void resetConfig()
    {
        setConfigControls(new BridgeConfig(this));
    }

    /**
     * Returns persistent bridge settings, falling back to defaults if the configuration file is
     * absent or invalid.
     */
    private BridgeConfig getCurrentBridgeSettings()
    {
        BridgeConfig settings = new BridgeConfig(this);
        settings.load();
        return settings;
    }

    /**
     * Updates user interface controls.
     *
     * The method calls the updateConfigControls() method to update the controls with the actual
     * values of the configuration settings.
     */
    private void updateUIControls()
    {
        updateConfigControls();
    }

    /**
     * Updates user interface controls with actual configuration setting values.
     *
     * If the instance of the BridgeService class exists, the actual configuration settings are
     * retrieved from the bridge service and displayed. If the service does not exist, the settings
     * are retrieved from the configuration file and displayed.
     */
    private void updateConfigControls()
    {
        setConfigControls(getCurrentBridgeSettings());
    }

    /**
     * Updates user interface controls with values from the specified configuration settings.
     *
     * @param   config          configuration settings to display
     */
    private void setConfigControls(BridgeConfig config)
    {
        setRobotName(config.getRobotName(), false);
        setHttpPort(config.getHttpPort(), false);
        setApiKey(config.getApiKey(), false);
        setUseAndroidTtsEngine(AndroidTtsUnit.TTS_NAME.equals(config.getTtsName()));
        setEnableAsr(config.getEnableAsr());
        setEnableFaceDetection(config.getEnableFaceDetection());
        setEnableHomeAlarmDetection(config.getEnableAlarmDetection());
    }

    /**
     * Populates the <em>Robot Name</em> text edit control with the specified value.
     *
     * @param   value           friendly robot name
     * @param   select          if @c true, the text in the control is selected
     */
    private void setRobotName(String value, boolean select)
    {
        if ((value != null) && (robotNameEditText != null))
        {
            robotNameEditText.setText(value);
            if (select) robotNameEditText.setSelection(value.length());
        }
    }

    /**
     * Populates the <em>HTTP Listener Port</em>> text edit control with the specified value.
     *
     * @param   value           HTTP listener port
     * @param   select          if @c true, the text in the control is selected
     */
    private void setHttpPort(Integer value, boolean select)
    {
        if ((value != null) && (httpPortEditText != null))
        {
            String s = Integer.toString(value);
            httpPortEditText.setText(String.format("%d", value));
            if (select) apiKeyEditText.setSelection(s.length());
        }
    }

    /**
     * Populates the <em>API Authorization Key</em>> text edit control with the specified value.
     *
     * @param   value           API authorization key
     * @param   select          if @c true, the text in the control is selected
     */
    private void setApiKey(String value, boolean select)
    {
        if ((value != null) && (apiKeyEditText != null))
        {
            apiKeyEditText.setText(value);
            if (select) apiKeyEditText.setSelection(value.length());
        }
    }

    /**
     * Sets the <em>Use Android Text-to-Speech Engine</em> switch control.
     *
     * @param   value           flag specifying if Android text-to-speech engine is used
     */
    private void setUseAndroidTtsEngine(boolean value)
    {
        if (androidTtsSwitch != null) androidTtsSwitch.setChecked(value);
    }

    /**
     * Sets the <em>Enable Speech Recognition</em> switch control to the specified value.
     *
     * @param   value           flag specifying if speech recognition is enabled
     */
    private void setEnableAsr(boolean value)
    {
        if (enableAsrSwitch != null) enableAsrSwitch.setChecked(value);
    }

    /**
     * Sets the <em>Enable Face Detection</em> switch control to the specified value.
     *
     * @param   value           flag specifying if face detection is enabled
     */
    private void setEnableFaceDetection(boolean value)
    {
        if (faceDetectionSwitch != null) faceDetectionSwitch.setChecked(value);
    }

    /**
     * Sets the <em>Enable Home Alarm Detection</em> switch control to the specified value.
     *
     * @param   value           flag specifying if home alarm detection is enabled
     */
    private void setEnableHomeAlarmDetection(boolean value)
    {
        if (homeAlarmDetectionSwitch != null) homeAlarmDetectionSwitch.setChecked(value);
    }

    /**
     * Retrieves value from <em>Robot Name</em> text edit control.
     *
     * The robot name is validated, if not valid an error message is displayed.
     *
     * @return  friendly robot name, or @c null if control contains an invalid value
     */
    private String getRobotName()
    {
        String robotName = robotNameEditText.getText().toString().trim();
        if (getCurrentBridgeSettings().validateRobotName(robotName) == false)
        {
            robotNameEditText.setError(getString(R.string.robot_name_invalid));
            return null;
        }
        return robotName;
    }

    /**
     * Retrieves value from <em>HTTP Listener Port</em> text edit control.
     *
     * The HTTP listener port is validated, if not valid an error message is displayed.
     *
     * @return  HTTP port, or @c null if control contains an invalid value
     */
    private Integer getHttpPort()
    {
        String s = httpPortEditText.getText().toString().trim();
        Integer port = ValueUtils.toInteger(s);
        BridgeConfig settings = new BridgeConfig(this);
        if ((port == null) || (settings.validateHttpPort(port) == false))
        {
            httpPortEditText.setError(getString(R.string.http_port_invalid));
            return null;
        }
        return port;
    }

    /**
     * Retrieves value from <em>API Authorization Key</em> text edit control.
     *
     * The API Authorization key is validated, if not valid an error message is displayed.
     *
     * @return  API authorization key, or @c null if control contains an invalid value
     */
    private String getApiKey()
    {
        String apiKey = apiKeyEditText.getText().toString().trim();
        if (getCurrentBridgeSettings().validateApiKey(apiKey) == false)
        {
            apiKeyEditText.setError(getString(R.string.api_key_invalid));
            return null;

        }
        return apiKey;
    }

    /**
     * Retrieves value from <em>Use Android Text-to-Speech Engine</em> switch control.
     *
     * @return  @c true if Android text-to-speech engine is used
     */
    private boolean getUseAndroidTtsEngine()
    {
        return ((androidTtsSwitch != null) && (androidTtsSwitch.isChecked()));
    }

    /**
     * Retrieves value from <em>Enable Speech Recognition</em> switch control.
     *
     * @return  @c true if speech recognition is enabled
     */
    private boolean isEnableAsrSelected()
    {
        return ((enableAsrSwitch != null) && (enableAsrSwitch.isChecked()));
    }

    /**
     * Retrieves value from <em>Enable Face Detection</em> switch control.
     *
     * @return  @c true if face detection is enabled
     */
    private boolean getEnableFaceDetection()
    {
        return ((faceDetectionSwitch == null) || (faceDetectionSwitch.isChecked()));
    }

    /**
     * Retrieves value from <em>Enable Home Alarm Detection</em> switch control.
     *
     * @return  @c true if home alarm detection is enabled
     */
    private boolean getEnableHomeAlarmDetection()
    {
        return ((homeAlarmDetectionSwitch == null) || (homeAlarmDetectionSwitch.isChecked()));
    }
}