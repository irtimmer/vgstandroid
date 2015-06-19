/**
 * Copyright (C) 2013-2015 Iwan Timmer
 *
 * This file is part of VGSTAndroid.
 *
 * VGSTAndroid is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * VGSTAndroid is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VGSTAndroid.  If not, see <http://www.gnu.org/licenses/>.
 */

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
import android.content.res.Configuration;
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
			if (accountName != null) {
				tvUsername.setText(accountName);
				tvUsername.setEnabled(false);
			}
		}
	}

	public void onCancelClick(View v) {
		setResult(RESULT_CANCELED);
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

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		findViewById(R.id.logo).setVisibility(newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO?View.VISIBLE:View.INVISIBLE);
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
				return data.getBoolean("data");
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

				AccountManager accMgr = AccountManager.get(LoginActivity.this);
				Account account = null;
				for (Account acc:accMgr.getAccountsByType(Vgst.ACCOUNT_TYPE)) {
					if (acc.name==username)
						account = acc;
				}

				if (account==null) {
					account = new Account(username, Vgst.ACCOUNT_TYPE);
					accMgr.addAccountExplicitly(account, password, null);

					ContentResolver.setIsSyncable(account, "com.android.contacts", 1);
					ContentResolver.setIsSyncable(account, "com.android.calendar", Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH ? 1 : 0);

					Bundle params = new Bundle();
					params.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false);
					params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);

					setSync(account, params);

					ContentResolver.setSyncAutomatically(account, "com.android.contacts", true);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
						ContentResolver.setSyncAutomatically(account, "com.android.calendar", true);
				} else
					accMgr.setPassword(account, password);

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
