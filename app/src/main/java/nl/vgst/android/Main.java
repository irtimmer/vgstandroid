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

package nl.vgst.android;

import nl.vgst.android.account.LoginActivity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

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

		AccountManager accMgr = AccountManager.get(this);
		nextActivity(accMgr.getAccountsByType(Vgst.ACCOUNT_TYPE).length>0);
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
			AccountManager accounts = AccountManager.get(Main.this);
			Account account = accounts.getAccountsByType(Vgst.ACCOUNT_TYPE)[0];
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				ContentResolver.setIsSyncable(account, "com.android.calendar", 1);
			}
			accounts.getAuthToken(account, Vgst.AUTHTOKEN_TYPE_FULL_ACCESS, (Bundle) null, this, new AccountManagerCallback<Bundle>() {
				@Override
				public void run(AccountManagerFuture<Bundle> future) {
					Bundle result = null;
					try {
						result = future.getResult();
					} catch (Exception e) {
						e.printStackTrace();
						return;
					}

					if (result.getInt(AccountManager.KEY_ERROR_CODE, 0) == 0) {
						String token = result.getString(AccountManager.KEY_AUTHTOKEN);
						String username = result.getString(AccountManager.KEY_ACCOUNT_NAME);
						String url = Api.HOST + "login/token?token=" + token;
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
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
			}, (Handler) null);
		} else {
			Intent intent = new Intent(this, LoginActivity.class);
			startActivityForResult(intent, REQUEST_LOGIN);
		}
	}

}
