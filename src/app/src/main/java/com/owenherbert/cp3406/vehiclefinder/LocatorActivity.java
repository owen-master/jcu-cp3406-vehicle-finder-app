package com.owenherbert.cp3406.vehiclefinder;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import static java.lang.Math.floor;

/**
 * LocatorActivity class extends BaseActivity and is used for the implementation and functionality
 * of the locator activity xml layout.
 *
 * @author Owen Herbert
 */
public class LocatorActivity extends AppCompatActivity {

    // preference key constants
    private static final String PREF_KEY_IMPERIAL_MEASUREMENTS = "imperialMeasurements";
    private static final String PREF_KEY_DISTANCE_COLOURS = "distanceColours";
    private static final String PREF_KEY_CLEAR_CONFIRMATION = "clearConfirmation";

    // shared preference key constants
    private static final String SPREF_KEY_IS_MARKED = "isMarked";
    private static final String SPREF_KEY_CURRENT_LONGITUDE = "currentLongitude";
    private static final String SPREF_KEY_CURRENT_LATITUDE = "currentLatitude";
    private static final String SPREF_KEY_MARKED_LONGITUDE = "markedLongitude";
    private static final String SPREF_KEY_MARKED_LATITUDE = "markedLatitude";
    private static final String SPREF_KEY_TN_BRNG_TO_MARKED_LOC = "tnBearingToMarkedLocation";

    private static final String SPREF_DEFAULT_STRING = "0";
    private static final String SPREF_KEY = "locatorActivityState";

    public static final String BROADCAST_ACTION = "sensorGpsUpdate";

    // colour constants
    private static final int COLOUR_YELLOW = Color.rgb(95, 91, 45);
    private static final int COLOUR_GREEN = Color.rgb(20, 100, 60);
    private static final int COLOUR_RED = Color.rgb(95, 45, 49);

    // utility variables
    private GpsService gpsService; // gps manager
    private ImageView directionImageView; // the direction ImageView object
    private TextView distanceTextView; // the distance text view
    private Button toggleButton; // the toggle button
    private boolean isMarked; // if the user has marked a position

    // positioning variables
    private double currentLongitude; // current longitude of the device
    private double currentLatitude; // current latitude of the device
    private double markedLongitude; // marked longitude of vehicle
    private double markedLatitude; // marked latitude of vehicle
    private int bearingToMarkedLocation; // bearing to marked long/lat

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (item.getItemId() == R.id.settings_icon) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_locator);

        // get LocationManager
        LocationManager locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);

        // find interface views
        directionImageView = findViewById(R.id.imageView);
        distanceTextView = findViewById(R.id.distanceTextView);
        toggleButton = findViewById(R.id.toggleButton);

        // create GpsService
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        gpsService = new GpsService(locationManager, sensorManager);

        // check if a previous state of LocatorActivity has been saved to shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences(SPREF_KEY, Context.MODE_PRIVATE);

        if (sharedPreferences.getBoolean(SPREF_KEY_IS_MARKED, false)) {

            isMarked = sharedPreferences.getBoolean(SPREF_KEY_IS_MARKED, false);
            currentLongitude = Double.parseDouble(sharedPreferences.getString(
                    SPREF_KEY_CURRENT_LONGITUDE, SPREF_DEFAULT_STRING));
            currentLatitude = Double.parseDouble(sharedPreferences.getString(
                    SPREF_KEY_CURRENT_LATITUDE, SPREF_DEFAULT_STRING));
            markedLongitude = Double.parseDouble(sharedPreferences.getString(
                    SPREF_KEY_MARKED_LONGITUDE, SPREF_DEFAULT_STRING));
            markedLatitude = Double.parseDouble(sharedPreferences.getString(
                    SPREF_KEY_MARKED_LATITUDE, SPREF_DEFAULT_STRING));
            bearingToMarkedLocation = sharedPreferences.getInt(
                    SPREF_KEY_TN_BRNG_TO_MARKED_LOC, 0);

            updateActivityInterface();
            setButtonToClearPosition();
        } else {
            reset(false);
        }

        registerBroadcastReceiver();
    }

    @Override
    protected void onResume() {

        super.onResume();

        // register listeners
        gpsService.registerListeners();
        registerBroadcastReceiver();
    }

    @Override
    protected void onPause() {

        super.onPause();

        // unregister listeners
        gpsService.unregisterListeners();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sensorGpsUpdate);

        // save LocatorActivity state in shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences(SPREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putBoolean(SPREF_KEY_IS_MARKED, isMarked);
        editor.putString(SPREF_KEY_CURRENT_LONGITUDE, String.valueOf(currentLongitude));
        editor.putString(SPREF_KEY_CURRENT_LATITUDE, String.valueOf(currentLatitude));
        editor.putString(SPREF_KEY_MARKED_LONGITUDE, String.valueOf(markedLongitude));
        editor.putString(SPREF_KEY_MARKED_LATITUDE, String.valueOf(markedLatitude));
        editor.putInt(SPREF_KEY_TN_BRNG_TO_MARKED_LOC, bearingToMarkedLocation);
        editor.apply();
    }

    /**
     * Registers the broadcast receiver.
     */
    private void registerBroadcastReceiver() {

        LocalBroadcastManager.getInstance(this).registerReceiver(sensorGpsUpdate,
                new IntentFilter(BROADCAST_ACTION));
    }

    // handler for received Intents for the "sensorGpsUpdate" event
    private final BroadcastReceiver sensorGpsUpdate = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            switch (intent.getIntExtra(GpsService.BROADCAST_FIELD_TYPE, 0)) {
                case GpsService.BROADCAST_TYPE_LOCATION_UPDATE:

                    currentLongitude = intent.
                            getDoubleExtra(GpsService.BROADCAST_FIELD_LONGITUDE, 0);
                    currentLatitude = intent.
                            getDoubleExtra(GpsService.BROADCAST_FIELD_LATITUDE, 0);

                    break;
                case GpsService.BROADCAST_TYPE_SENSOR_UPDATE:

                    if (gpsService.hasInitiated() && isMarked) {
                        float tnBearingBetweenLocations =
                                (float) GpsService.bearingBetweenLocations(currentLatitude,
                                        currentLongitude, markedLatitude, markedLongitude);
                        bearingToMarkedLocation = (int) (tnBearingBetweenLocations -
                                intent.getIntExtra(GpsService.BROADCAST_FIELD_ORIENTATION, 0));
                    }

                    break;
            }

            updateActivityInterface();
        }
    };

    /**
     * Marks or clears the Vehicle Locator current position depending on the current state.
     *
     * @param view the event source object
     */
    public void onToggleButtonClick(View view) {

        try {

            // if the user already has a location marked then it will be cleared, if not then it
            // will be created
            if (isMarked) {

                // if user has clear confirmation enabled in settings then get user confirmation
                if (isClearConfirmationEnabled()) {

                    // create dialog
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.confirmation_description)
                            .setTitle(R.string.confirmation_title)
                            .setPositiveButton(R.string.confirmation_positive_button,
                                    (dialogInterface, i) -> reset(true))
                            .setNegativeButton(R.string.confirmation_negative_button,
                                    (dialogInterface, i) -> dialogInterface.dismiss())
                            .create().show();
                } else {
                    reset(true);
                }
            } else {
                markCurrentPosition();
            }
        } catch (LocatorActivityException err) {
            makeToast(R.string.location_not_ready);
        }
    }

    /**
     * Checks if imperial measurements is enabled in settings.
     *
     * @return imperial measurements enabled
     */
    private boolean isImperialMeasurementsEnabled() {

        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREF_KEY_IMPERIAL_MEASUREMENTS, true);
    }

    /**
     * Checks if distance colours are enabled in settings.
     *
     * @return distance colours enabled
     */
    private boolean isDistanceColoursEnabled() {

        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREF_KEY_DISTANCE_COLOURS, true);
    }

    /**
     * Checks if clear confirmations are enabled in settings.
     *
     * @return clear confirmations enabled
     */
    private boolean isClearConfirmationEnabled() {

        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREF_KEY_CLEAR_CONFIRMATION, true);
    }

    /**
     * Sets the interface button to the clear position state
     */
    private void setButtonToClearPosition() {

        toggleButton.setText(R.string.clear_position);
    }

    /**
     * Sets the interface button to the mark position state
     */
    private void setButtonToMarkPosition() {

        toggleButton.setText(R.string.mark_position);
    }

    /**
     * Resets the application interface.
     *
     * @param notifyWithToast if a toast should be created
     */
    private void reset(boolean notifyWithToast) {

        if (notifyWithToast) makeToast(R.string.vehicle_position_cleared);

        isMarked = false;

        // reset direction image view
        directionImageView.setRotation(0);
        directionImageView.setColorFilter(getResources().getColor(R.color.black));

        // reset distance text view
        distanceTextView.setTextColor(getResources().getColor(R.color.black));
        distanceTextView.setText("");

        setButtonToMarkPosition();
    }

    /**
     * Gets the current latitude and longitude from the gps service and updates the marked position.
     */
    private void markCurrentPosition() throws LocatorActivityException {

        // throw exception if the GpsService does not yet have a current location
        if (!gpsService.hasInitiated()) {
            throw new LocatorActivityException();
        }

        // update the marked location with a new location object
        markedLatitude = currentLatitude;
        markedLongitude = currentLongitude;

        isMarked = true;

        setButtonToClearPosition();

        makeToast(R.string.vehicle_position_marked);
    }

    /**
     * Updates the activity interface to match latest data.
     */
    private void updateActivityInterface() {

        if (isMarked) {

            int distanceInMetres = GpsService.getDistanceBetween(currentLatitude, currentLongitude,
                    markedLatitude, markedLongitude);

            // update distance text view content
            if (isImperialMeasurementsEnabled()) {
                int feet = (int) floor(distanceInMetres / 0.3048); // convert metres to feet
                String feetString = "" + feet;
                distanceTextView.setText(String.format(getResources()
                        .getString(R.string.format_distance_feet), feetString));
            } else {
                String metresString = "" + distanceInMetres;
                distanceTextView.setText(String.format(getResources()
                        .getString(R.string.format_distance_metres), metresString));
            }

            // rotate image view
            directionImageView.setRotation(bearingToMarkedLocation);

            // updates colours of direction image view and distance text view

            // if distance colours are enabled then update component colours accordingly
            if (isDistanceColoursEnabled()) {
                if (distanceInMetres > 200) { // red if distance > 200m
                    directionImageView.setColorFilter(COLOUR_RED);
                    distanceTextView.setTextColor(COLOUR_RED);
                } else if (distanceInMetres > 50) { // yellow if distance > 50m
                    directionImageView.setColorFilter(COLOUR_YELLOW);
                    distanceTextView.setTextColor(COLOUR_YELLOW);
                } else { // green
                    directionImageView.setColorFilter(COLOUR_GREEN);
                    distanceTextView.setTextColor(COLOUR_GREEN);
                }
            } else {
                directionImageView.setColorFilter(Color.BLACK);
                distanceTextView.setTextColor(Color.BLACK);
            }
        }
    }

    /**
     * Creates and shows a Toast.
     *
     * @param resId the resource ID of the string
     */
    private void makeToast(int resId) {

        String string = getString(resId);
        Toast.makeText(this, string, Toast.LENGTH_SHORT).show();
    }

    public static class LocatorActivityException extends RuntimeException {

        public LocatorActivityException() {

            super();
        }
    }
}