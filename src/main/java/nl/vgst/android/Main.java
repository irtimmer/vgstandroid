package nl.vgst.android;

import java.io.IOException;

import nl.vgst.android.account.LoginActivity;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

/**
 * Main activity to show login screen or redirect user to website
 * @author Iwan Timmer
 */
public class Main extends Activity {
	
	private static final String TAG = "MAIN";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash);
		
		if (GCMUtil.checkPlayServices(this, createPendingResult(0, new Intent(), PendingIntent.FLAG_ONE_SHOT))) {
			AccountManager accMgr = AccountManager.get(this);
			nextActivity(accMgr.getAccountsByType(Vgst.ACCOUNT_TYPE).length>0);
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		nextActivity(resultCode==LoginActivity.RESULT_OK);
	}
	
	private void nextActivity(boolean authenticated) {
		if (authenticated) {
			//Check if user is registered for GCM if Play Services are available
			if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)==ConnectionResult.SUCCESS && GCMUtil.getRegistrationId(this)==null)
				GCMUtil.register(this);
			
			new LoginTask().execute();
		} else {
			Intent intent = new Intent(this, LoginActivity.class);
			startActivityForResult(intent, 0);
		}		
	}
	
	private class LoginTask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... args) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				AccountManager accounts = AccountManager.get(Main.this);
				Account account = accounts.getAccountsByType(Vgst.ACCOUNT_TYPE)[0];
				ContentResolver.setIsSyncable(account, "com.android.calendar", 1);
				ContentResolver.setSyncAutomatically(account, "com.android.calendar", true);
			}

			Api api = new Api(Main.this);
			try {
				JSONObject data = api.get("api/createToken").getJSONObject("data");
				int userId = data.getInt("userId");
				String token = data.getString("token");
				
				return Api.HOST + "login/token/id/" + userId + "/token/" + token;
			} catch (JSONException e) {
				Log.e(TAG, "Kan data niet lezen", e);
			} catch (IOException e) {
				Log.e(TAG, "Kan data niet lezen", e);
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(String result) {
			if (result!=null) {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(result));
				startActivity(intent);
				finish();
			} else {
				Intent intent = new Intent(Main.this, LoginActivity.class);
				startActivityForResult(intent, 0);
			}
		}
		
		@Override
		protected void onCancelled() {
			finish();
		}

	}

}
