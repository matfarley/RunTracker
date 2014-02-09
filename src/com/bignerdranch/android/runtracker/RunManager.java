package com.bignerdranch.android.runtracker;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class RunManager {
	private static final String TAG = "RunManager";
	
	private static final String PREFS_FILE = "runs";
	private static final String PREF_CURRENT_RUN_ID = "RunManager.currentRunId";
	
	static final String ACTION_LOCATION = 
			"com.bignerdranch.android.runtracker.ACTION_LOCATION";
	static final String TEST_PROVIDER = "TEST_PROVIDER";
	
	private static RunManager sRunManager;
	private Context mAppContext;
	private LocationManager mLocationManager;
	private RunDatabaseHelper mHelper;
	private SharedPreferences mPrefs;
	private long mCurrentRunId;
	
	// The private constructor forces users to use RunManager.get(context)
	private RunManager(Context appContext){
		mAppContext = appContext;
		mLocationManager = (LocationManager)mAppContext
				.getSystemService(Context.LOCATION_SERVICE);
		mHelper = new RunDatabaseHelper(mAppContext);
		mPrefs = mAppContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
		mCurrentRunId = mPrefs.getLong(PREF_CURRENT_RUN_ID, -1);
	}
	
	public static RunManager get(Context c){
		if(sRunManager == null){
			//Use the application context to avoid leaking activities - 
			//Is a wider context that has a lifecycle matching the application rather than a single activity
			sRunManager = new RunManager(c.getApplicationContext());
		}
		return sRunManager;
	}
	
	private PendingIntent getLocationPendingIntent(boolean shouldCreate){
		Intent broadcast = new Intent(ACTION_LOCATION);
		int flags = shouldCreate ? 0 : PendingIntent.FLAG_NO_CREATE;
		return PendingIntent.getBroadcast(mAppContext, 0, broadcast, flags);
	}
	
	public void startLocationUpdates(){
		String provider = LocationManager.GPS_PROVIDER;
		
		//IF YOU HAVE THE TEST PROVIDER AND IT IS ENABLED USE IT.
		if(mLocationManager.getProvider(TEST_PROVIDER) != null && mLocationManager.isProviderEnabled(TEST_PROVIDER)){
			provider = TEST_PROVIDER;
		}
		Log.d(TAG, "Using provider " + provider);
		
		//Get the last known location and broadcast it if you have one
		Location lastKnown = mLocationManager.getLastKnownLocation(provider);
		if(lastKnown != null){
			//Reset time to now
			lastKnown.setTime(System.currentTimeMillis());
			broadcastLocation(lastKnown);
		}
		
		//START UPDATES FROM LOCATION MANAGER
		PendingIntent pi = getLocationPendingIntent(true);
		mLocationManager.requestLocationUpdates(provider, 0, 0, pi);
	}
	
	private void broadcastLocation(Location location){
		Intent broadcast = new Intent(ACTION_LOCATION);
		broadcast.putExtra(LocationManager.KEY_LOCATION_CHANGED, location);
		mAppContext.sendBroadcast(broadcast);
	}
	
	public Run startNewRun(){
		//Insert a run into the db
		Run run = insertRun();
		//start tracking the run
		startTrackingRun(run);
		return run;
	}
	
	public void startTrackingRun(Run run){
		//Keep the ID
		mCurrentRunId = run.getId();
		//Store it in shared preferences
		mPrefs.edit().putLong(PREF_CURRENT_RUN_ID, mCurrentRunId).commit();
		//Start location updates
		startLocationUpdates();
	}
	
	public void stopRun(){
		stopLocationUpdates();
		mCurrentRunId = -1;
		mPrefs.edit().remove(PREF_CURRENT_RUN_ID).commit();
	}
	
	private Run insertRun(){
		Run run = new Run();
		run.setId(mHelper.insertRun(run));
		return run;
	}
	
	public void stopLocationUpdates(){
		PendingIntent pi = getLocationPendingIntent(false);
		if (pi != null){
			mLocationManager.removeUpdates(pi);
			pi.cancel();
		}
	}
	
	public boolean isTrackingRun(){
		return getLocationPendingIntent(false) != null;
	}

}
