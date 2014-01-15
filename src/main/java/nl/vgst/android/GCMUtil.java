package nl.vgst.android;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import nl.vgst.android.account.LoginActivity;

public class GCMUtil {
	
	private final static String TAG = "GCMUtil";
	
	public final static String SENDER_ID = "534236984922";
	
	private final static String PROPERTY_REG_ID = "registration_id";
	private final static String PROPERTY_APP_VERSION = "app_version";
	
	public static boolean checkPlayServices(final Activity activity, final PendingIntent pendingIntent) {
	    int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
	    if (resultCode != ConnectionResult.SUCCESS) {
	        if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
	            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, activity, 0);
				dialog.show();
				dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialogInterface) {
						try {
							pendingIntent.send(LoginActivity.RESULT_CANCELED);
						} catch (PendingIntent.CanceledException e) {
							Log.e(TAG, "Intent canceled");
						}
					}
				});
		        return false;
	        } else {
	            Log.w(TAG, "This device is not supported.");
	        }
	    }
		pendingIntent.cancel();
	    return true;
	}
	
	/**
	 * Gets the current registration ID for application on GCM service.
	 * If result is empty, the app needs to register.
	 * @return registration ID, or null if there is no existing registration ID.
	 */
	public static String getRegistrationId(Context context) {
	    final SharedPreferences prefs = context.getSharedPreferences(Main.class.getSimpleName(), Context.MODE_PRIVATE);
	    String registrationId = prefs.getString(PROPERTY_REG_ID, "");
	    if (registrationId.equals("")) {
	        Log.i(TAG, "Registration not found.");
	        return null;
	    }
	    // Check if app was updated; if so, it must clear the registration ID
	    // since the existing regID is not guaranteed to work with the new
	    // app version.
	    int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
	    
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		    if (registeredVersion != packageInfo.versionCode) {
		        Log.i(TAG, "App version changed.");
		        return null;
		    }
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Error retreiving app version", e);
		}
	    return registrationId;
	}
	
	private static void storeRegistrationId(Context context, String regId) {
	    final SharedPreferences prefs = context.getSharedPreferences(Main.class.getSimpleName(), Context.MODE_PRIVATE);
	    int appVersion = 0;
	    
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			appVersion = packageInfo.versionCode;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Error retreiving app version", e);
		}
	    
	    Log.i(TAG, "Saving regId on app version " + appVersion);
	    SharedPreferences.Editor editor = prefs.edit();
	    editor.putString(PROPERTY_REG_ID, regId);
	    editor.putInt(PROPERTY_APP_VERSION, appVersion);
	    editor.commit();
	}
	
	public static void register(Context context) {
		new RegisterTask().execute(context);
	}
	
	private static class RegisterTask extends AsyncTask<Context, Void, Void> {
		@Override
		protected Void doInBackground(Context... context) {
			try {
				GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context[0]);
				String regId = gcm.register(GCMUtil.SENDER_ID);
				Log.i(TAG, "Registered GCM with id " + regId);
				
				Api api = new Api(context[0]);
				JSONObject data = api.get("api/register/registration_id/"+regId);
				if (data.getBoolean("data")) {
					Log.i(TAG, "GCM registration id send to server");
					storeRegistrationId(context[0], regId);
				} else {
					Log.e(TAG, "Can't register GCM with server");
					gcm.unregister();
				}
			} catch (IOException e) {
				Log.e(TAG, "Error contacting server during GCM registration", e);
			} catch (JSONException e) {
				Log.e(TAG, "Can't read response from server for registration", e);
			}	
				
			return null;
		}
	}

}
