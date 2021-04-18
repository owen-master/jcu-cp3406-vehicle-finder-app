package com.owenherbert.cp3406.vehiclefinder;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static java.lang.Math.round;

/**
 * GpsService class is a Service that performs long-running location and sensor listening operations
 * in the background. The GpsService class also provides some utility methods.
 *
 * @author Owen Herbert
 */
public class GpsService extends Service implements LocationListener, SensorEventListener {

    // utility constants
    private static final int UPDATE_INTERVAL_MS = 500; // update interval in milliseconds
    private static final int UPDATE_MIN_DISTANCE_M = 0; // update distance in metres

    public static final int BROADCAST_TYPE_LOCATION_UPDATE = 0;
    public static final int BROADCAST_TYPE_SENSOR_UPDATE = 1;
    public static final String BROADCAST_FIELD_TYPE = "type";
    public static final String BROADCAST_FIELD_LONGITUDE = "longitude";
    public static final String BROADCAST_FIELD_LATITUDE = "latitude";
    public static final String BROADCAST_FIELD_ORIENTATION = "orientation";

    // other
    private boolean hasInitiated; // if the location listener has initiated and provided an update
    private final LocationManager locationManager;

    // device hardware sensors
    private final SensorManager sensorManager;
    private final Sensor sensorMagneticField; // magnetic field sensor
    private final Sensor sensorAccelerometer; // accelerometer sensor

    // sensor values
    private float[] valuesAccelerometer = new float[3]; // accelerometer data
    private float[] valuesMagneticField = new float[3]; // magnetic field data
    private final float[] rotationMatrix = new float[9]; // calculated rotation matrix
    private final float[] orientationAngles = new float[3]; // calculated orientation angles

    /**
     * Constructs a GpsService object
     *
     * @param locationManager a LocationManager object
     * @param sensorManager a SensorManager object
     */
    public GpsService(LocationManager locationManager, SensorManager sensorManager) {

        this.sensorManager = sensorManager;
        this.locationManager = locationManager;

        // get default sensors
        sensorMagneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        registerListeners();
    }

    /**
     * Register sensors and location updates.
     */
    @SuppressLint("MissingPermission")
    public void registerListeners() {

        // register location updates
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS,
                UPDATE_MIN_DISTANCE_M, this);

        // register magnet field sensor listener
        sensorManager.registerListener(this, sensorMagneticField,
                SensorManager.SENSOR_DELAY_NORMAL);

        // register accelerometer sensor listener
        sensorManager.registerListener(this, sensorAccelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    /**
     * Unregister sensors and location updates.
     */
    public void unregisterListeners() {

        // remove location updates
        locationManager.removeUpdates(this);

        // unregister magnet field sensor listener
        sensorManager.unregisterListener(this, sensorMagneticField);

        // unregister accelerometer sensor listener
        sensorManager.unregisterListener(this, sensorAccelerometer);
    }

    /**
     * Returns the *TRUE NORTH* locational bearing between the provided latitude and longitude
     * points.
     *
     * @param lat1 latitude coordinate one
     * @param long1 longitude coordinate one
     * @param lat2 latitude coordinate two
     * @param long2 longitude coordinate two
     * @return the bearing as a double
     */
    public static double bearingBetweenLocations(double lat1, double long1, double lat2,
                                                 double long2) {

        double pi = Math.PI;

        double latitude1 = lat1 * pi / 180;
        double longitude1 = long1 * pi / 180;
        double latitude2 = lat2 * pi / 180;
        double longitude2 = long2 * pi / 180;

        double longitudeDistance = (longitude2 - longitude1);

        double y = Math.sin(longitudeDistance) * Math.cos(latitude2);
        double x = Math.cos(latitude1) * Math.sin(latitude2) - Math.sin(latitude1)
                * Math.cos(latitude2) * Math.cos(longitudeDistance);

        double bearing = Math.atan2(y, x);

        bearing = Math.toDegrees(bearing);
        bearing = (bearing + 360) % 360;

        return bearing;
    }

    /**
     * Returns distance in metres between the provided latitude and longitude
     * points.
     *
     * @param lat1 latitude coordinate one
     * @param long1 longitude coordinate one
     * @param lat2 latitude coordinate two
     * @param long2 longitude coordinate two
     * @return the distance in metres
     */
    public static int getDistanceBetween(double lat1, double long1, double lat2, double long2) {

        float[] distance = new float[2];
        Location.distanceBetween(lat1, long1, lat2, long2, distance);
        return (int) distance[0];
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }

    /**
     * Called when the location has changed. A wakelock is held on behalf on the listener for some
     * brief amount of time as this callback executes. If this callback performs long running
     * operations, it is the client's responsibility to obtain their own wakelock.
     *
     * @param location the Location
     */
    @Override
    public void onLocationChanged(Location location) {

        // update the initiated status if needed
        if (!hasInitiated) hasInitiated = true;

        // send location update broadcast to LocatorActivity
        Intent intent = new Intent(LocatorActivity.BROADCAST_ACTION);
        intent.putExtra(BROADCAST_FIELD_TYPE, BROADCAST_TYPE_LOCATION_UPDATE);
        intent.putExtra(BROADCAST_FIELD_LONGITUDE, location.getLongitude());
        intent.putExtra(BROADCAST_FIELD_LATITUDE, location.getLatitude());

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    /**
     * Called when there is a new sensor event. Note that "on changed" is somewhat of a misnomer,
     * as this will also be called if we have a new reading from a sensor with the exact same
     * sensor values (but a newer timestamp).
     *
     * @param sensorEvent the SensorEvent
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        // check the sensor event accuracy
        if (sensorEvent.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {

            // get sensor values
            switch (sensorEvent.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    valuesAccelerometer = sensorEvent.values;
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    valuesMagneticField = sensorEvent.values;
                    break;
            }

            // calculate orientation angle from rotation matrix
            SensorManager.getRotationMatrix(rotationMatrix, null, valuesAccelerometer,
                    valuesMagneticField);
            float[] orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles);
            double degrees = (Math.toDegrees(orientation[0]) + 360.0) % 360.0;
            int orientationAngle =  (int) round(degrees*100) / 100;

            // send sensor update broadcast to LocatorActivity
            Intent intent = new Intent(LocatorActivity.BROADCAST_ACTION);
            intent.putExtra(BROADCAST_FIELD_TYPE, BROADCAST_TYPE_SENSOR_UPDATE);
            intent.putExtra(BROADCAST_FIELD_ORIENTATION, orientationAngle);

            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onDestroy() {

        unregisterListeners();
        super.onDestroy();
    }

    public boolean hasInitiated() {

        return hasInitiated;
    }
}
