package nl.vgst.android.account;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AccountAuthenticatorService extends Service {

	public IBinder onBind(Intent intent) {
		return new AccountAuthenticator(this).getIBinder();
	}

}