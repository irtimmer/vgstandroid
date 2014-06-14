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
import android.content.Context;

public class Api {
	
	public final static String HOST = "https://vgst.nl/";
	
	public Api(Context context) {
		AccountManager accounts = AccountManager.get(context);
		init(accounts.getAccountsByType(Vgst.ACCOUNT_TYPE)[0], accounts);
	}

	public Api(final Account account, Context context) {
		init(account, AccountManager.get(context));
	}
	
	public Api(String username, String password) {
		init(username, password);
	}
	
	private void init(final String username, final String password) {
		Authenticator.setDefault(new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password.toCharArray());
			};
		});
	}
	
	private void init(Account account, AccountManager accounts) {
		init(account.name, accounts.getPassword(account));
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
		HttpURLConnection urlConnection = (HttpURLConnection) new URL(HOST+url).openConnection();
		try {
			//FIX: Gzip enconding levert problemen in combinatie met Android en HTTPS
			urlConnection.setRequestProperty("Accept-Encoding", "identity");
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
