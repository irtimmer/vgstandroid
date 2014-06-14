package nl.vgst.android;

import java.io.IOException;

import nl.vgst.android.account.LoginActivity;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

/**
 * Main activity to show login screen or redirect user to website
 * @author Iwan Timmer
 */
public class Main extends Activity {

	private static final int REQUEST_GCM = 0, REQUEST_LOGIN = 1;
	
	private static final String TAG = "MAIN";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash);
		
		if (GCMUtil.checkPlayServices(this, createPendingResult(REQUEST_GCM, new Intent(), PendingIntent.FLAG_ONE_SHOT))) {
			AccountManager accMgr = AccountManager.get(this);
			nextActivity(accMgr.getAccountsByType(Vgst.ACCOUNT_TYPE).length>0);
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_LOGIN) {
			if (resultCode==LoginActivity.RESULT_OK)
				nextActivity(true);
			else if (resultCode==RESULT_CANCELED)
				finish();
		} else {
			AccountManager accMgr = AccountManager.get(this);
			nextActivity(accMgr.getAccountsByType(Vgst.ACCOUNT_TYPE).length>0);
		};
	}
	
	private void nextActivity(boolean authenticated) {
		if (authenticated) {
			//Check if user is registered for GCM if Play Services are available
			if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)==ConnectionResult.SUCCESS && GCMUtil.getRegistrationId(this)==null)
				GCMUtil.register(this);
			
			new LoginTask().execute();
		} else {
			Intent intent = new Intent(this, LoginActivity.class);
			startActivityForResult(intent, REQUEST_LOGIN);
		}		
	}
	
	private class LoginTask extends AsyncTask<Void, Void, String> {

		private final static String LOGIN = "LOGIN";

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
			} catch (AuthenticationException e) {
				return LOGIN;
			} catch (JSONException e) {
				Log.e(TAG, "Kan data niet lezen", e);
			} catch (IOException e) {
				Log.e(TAG, "Kan data niet lezen", e);
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(String result) {
			if (result==LOGIN) {
				Intent intent = new Intent(Main.this, LoginActivity.class);
				startActivityForResult(intent, 0);
			} else if (result!=null) {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(result));
				startActivity(intent);
				finish();
			} else {
				AlertDialog dialog = new AlertDialog.Builder(Main.this).create();
				dialog.setTitle(R.string.warning_title);
				dialog.setMessage(getString(R.string.server_failed));
				dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});
				dialog.show();
			}
		}
		
		@Override
		protected void onCancelled() {
			finish();
		}

	}

}
