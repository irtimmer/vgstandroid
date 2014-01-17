package nl.vgst.android.account;

import java.io.IOException;

import nl.vgst.android.Api;
import nl.vgst.android.R;
import nl.vgst.android.Vgst;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends AccountAuthenticatorActivity {
	
	private static final String TAG = "Login";

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		this.setContentView(R.layout.activity_login);
		
		//When update show current username
		if (icicle!=null) {
			TextView tvUsername = (TextView) findViewById(R.id.username);
			String accountName = icicle.getString(AccountManager.KEY_ACCOUNT_NAME);
			tvUsername.setText(accountName);
		}
	}

	public void onCancelClick(View v) {
		this.finish();
	}

	public void onSaveClick(View v) {
		TextView tvUsername = (TextView) findViewById(R.id.username);
		TextView tvPassword = (TextView) findViewById(R.id.password);

		boolean hasErrors = false;

		if (tvUsername.length() < 3) {
			hasErrors = true;
			tvUsername.setError(getResources().getText(R.string.too_short));
		}
		if (tvPassword.length() < 3) {
			hasErrors = true;
			tvPassword.setError(getResources().getText(R.string.too_short));
		}
		
		if (hasErrors)
			return;
		
		ProgressDialog dialog = ProgressDialog.show(this, "Login", getResources().getText(R.string.checking));
		
		new LoginTask(tvUsername, tvPassword, dialog).execute();
	}
	
	private class LoginTask extends AsyncTask<Void, Void, Boolean> {
		private TextView tvUsername, tvPassword;
		private ProgressDialog dialog;
		
		private String username, password;
		
		//Error message when server connection problems
		private int toastRes;
		
		public LoginTask(TextView tvUsername, TextView tvPassword, ProgressDialog dialog) {
			this.tvUsername = tvUsername;
			this.tvPassword = tvPassword;
			this.dialog = dialog;
			
			username = tvUsername.getText().toString();
			password = tvPassword.getText().toString();
		}
		
		@Override
		protected Boolean doInBackground(Void... args) {
			Api api = new Api(username, password);
			try {
				JSONObject data = api.get("api/checkCredentials");

				boolean succeed = data.getBoolean("data");
				if (succeed) {
					AccountManager accMgr = AccountManager.get(LoginActivity.this);
					Account account = null;
					for (Account acc:accMgr.getAccounts()) {
						if (acc.name==username)
							account = acc;
					}
					
					if (account==null) {
						account = new Account(username, Vgst.ACCOUNT_TYPE);
						accMgr.addAccountExplicitly(account, password, null);

						ContentResolver.setIsSyncable(account, "com.android.contacts", 1);
						ContentResolver.setIsSyncable(account, "com.android.calendar", 1);

						Bundle params = new Bundle();
						params.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false);
						params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
						
						setSync(account, params);
						
						ContentResolver.setSyncAutomatically(account, "com.android.contacts", true);
						ContentResolver.setSyncAutomatically(account, "com.android.calendar", true);
					} else
						accMgr.setPassword(account, password);
					
				}
				
				return succeed;
			} catch (IOException e) {
				toastRes = R.string.server_failed;
				Log.e(TAG, "Kan data niet lezen", e);
			} catch (JSONException e) {
				toastRes = R.string.server_failed;
				Log.e(TAG, "Kan data niet lezen", e);
			}
			
			return true;
		}
		
		@TargetApi(Build.VERSION_CODES.FROYO)
		private void setSync(Account account, Bundle params) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
				params.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, false);
				ContentResolver.addPeriodicSync(account, "com.android.contacts", params, 60 * 60 * 24);
				ContentResolver.addPeriodicSync(account, "com.android.calendar", params, 60 * 60 * 24);
			}
		}
		
		@Override
		protected void onCancelled() {
			dialog.dismiss();
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			dialog.dismiss();
			
			if (!result) {
				tvUsername.setError(getResources().getText(R.string.incorrect_credentials));
				tvPassword.setError(getResources().getText(R.string.incorrect_credentials));
			} else if (toastRes != 0)
				Toast.makeText(LoginActivity.this, toastRes, Toast.LENGTH_LONG).show();
			else {
				Toast.makeText(LoginActivity.this, "Login succesfull", Toast.LENGTH_LONG).show();
				// Now we tell our caller, could be the Android Account Manager or even
				// our own application
				// that the process was successful

				Intent intent = new Intent();
				intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, username);
				intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Vgst.ACCOUNT_TYPE);
				intent.putExtra(AccountManager.KEY_PASSWORD, password);
				setAccountAuthenticatorResult(intent.getExtras());
				setResult(RESULT_OK, intent);
				finish();
			}
		}
	}

}
