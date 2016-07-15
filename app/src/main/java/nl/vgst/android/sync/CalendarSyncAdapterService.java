/**
 * Copyright (C) 2014 Iwan Timmer
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