package nl.vgst.android.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class CalendarSyncAdapterService extends Service {

	@Override
	public IBinder onBind(Intent intent) {
		return new CalendarSyncAdapter(this).getSyncAdapterBinder();
	}

}
