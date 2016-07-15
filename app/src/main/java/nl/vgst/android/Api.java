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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;

public class Api {
	
	public final static String HOST = "https://vgst.nl/";

	private AccountManager accounts;
	private String token;

	public Api(Context context) throws AuthenticatorException, OperationCanceledException, IOException {
		AccountManager accounts = AccountManager.get(context);
		init(accounts.getAccountsByType(Vgst.ACCOUNT_TYPE)[0], accounts);
	}

	public Api(final Account account, Context context) throws AuthenticatorException, OperationCanceledException, IOException {
		init(account, AccountManager.get(context));
	}
	
	public Api(final String username, final String password) {
		Authenticator.setDefault(new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password.toCharArray());
			}

			;
		});
	}
	
	private void init(Account account, AccountManager accounts) throws AuthenticatorException, OperationCanceledException, IOException {
		this.accounts = accounts;
		token = accounts.blockingGetAuthToken(account, Vgst.AUTHTOKEN_TYPE_FULL_ACCESS, true);
		if (token == null) {
			throw new AuthenticatorException("Can't get authentication token");
		}
	}

	public JSONObject get(String url) throws IOException, JSONException {
		byte[] data = download(url);
		String json = new String(data, "UTF-8");
		
		Object object = new JSONTokener(json).nextValue();
	    
		if (object instanceof JSONObject)
	        return (JSONObject) object;
		else 
			throw new JSONException("Incorrect format \"" + object + "\" from " + HOST+url);
	}
	
	public byte[] download(String url) throws IOException {
		if (accounts != null) {
			url += "?token="+token;
		}

		HttpURLConnection urlConnection = (HttpURLConnection) new URL(HOST+url).openConnection();
		try {
			//FIX: Gzip enconding levert problemen in combinatie met Android en HTTPS
			urlConnection.setRequestProperty("Accept-Encoding", "identity");
			if (urlConnection.getResponseCode()==403) {
				if (accounts != null)
					accounts.invalidateAuthToken(Vgst.ACCOUNT_TYPE, token);

				throw new AuthenticationException();
			}

			if (urlConnection.getResponseCode()==401)
				throw new AuthenticationException();

			InputStream in = urlConnection.getInputStream();
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] b = new byte[1024];
			int length = 0;
			while ((length = in.read(b))>0)
				buffer.write(b, 0, length);
			
			return buffer.toByteArray();
		} finally {
			urlConnection.disconnect();
		}
	}

}
