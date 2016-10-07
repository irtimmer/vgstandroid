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

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import nl.vgst.android.Api;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class CalendarSyncAdapter extends AbstractThreadedSyncAdapter {

	private static final String TAG = "CalendarSyncAdapter";
	private static final int MAX_OPERATIONS = 100;

	public CalendarSyncAdapter(Context context) {
		super(context, true);
	}

	@Override
	public void onPerformSync(final Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
		try {
			Api api = new Api(account, getContext());
			ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

			Cursor gcursor = provider.query(Calendars.CONTENT_URI, new String[] {Calendars._ID}, Calendars.ACCOUNT_TYPE + "=?", new String[] {account.type}, null);
			if (!gcursor.moveToNext()) {
				ContentValues values = new ContentValues();
				values.put(Calendars.ACCOUNT_NAME, account.name);
				values.put(Calendars.ACCOUNT_TYPE, account.type);
				values.put(Calendars.NAME, "VGST");
				values.put(Calendars.CALENDAR_DISPLAY_NAME, "VGST");
				values.put(Calendars.CALENDAR_COLOR, -4521848);
				values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ);
				values.put(Calendars.SYNC_EVENTS, true);
				Uri uri = provider.insert(asSyncAdapter(Calendars.CONTENT_URI, account.name, account.type), values);
				gcursor = provider.query(Calendars.CONTENT_URI, new String[] {Calendars._ID}, Calendars.ACCOUNT_TYPE + "=?", new String[] {account.type}, null);
				gcursor.moveToNext();
			}
			long calendarId = gcursor.getLong(0);

			JSONObject data = api.get("activities/api/getEvents");

			Uri uri = asSyncAdapter(Events.CONTENT_URI, account.name, account.type);
			Cursor c1 = provider.query(uri, new String[] { Events._ID, Events._SYNC_ID, Events.SYNC_DATA1 }, null, null, Events._SYNC_ID);

			Set<Long> removeIds = new HashSet<>();
			Map<Long, Long> syncIds = new HashMap<>();
			while (c1.moveToNext()) {
				removeIds.add(c1.getLong(0));
				syncIds.put(Long.parseLong(c1.getString(1)), c1.getLong(0));
			}

			JSONArray events = data.getJSONArray("data");
			for (int i=0;i<events.length();i++) {
				JSONObject event = events.getJSONObject(i);
				long id = event.getLong("id");

				if (syncIds.containsKey(id)) {
					Log.d(TAG, "Update event " + id);
					updateEvent(operationList, calendarId, account, id, event);
					syncResult.stats.numUpdates++;
					removeIds.remove(syncIds.get(id));
				} else {
					Log.d(TAG, "Create event " + id);
					addEvent(operationList, calendarId, account, event);
					syncResult.stats.numInserts++;
				}

				if (operationList.size() > MAX_OPERATIONS) {
					provider.applyBatch(operationList);
					operationList.clear();
				}
			}

			for (long id:removeIds) {
				Log.d(TAG, "Delete event " + id);
				ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(uri);
				builder.withSelection(Events._ID + "=?", new String[]{String.valueOf(id)});
				operationList.add(builder.build());
				if (operationList.size() > MAX_OPERATIONS) {
					provider.applyBatch(operationList);
					operationList.clear();
				}
				syncResult.stats.numDeletes++;
			}

			provider.applyBatch(operationList);
		} catch (IOException e) {
			Log.e(TAG, "Kan data niet lezen", e);
			syncResult.stats.numIoExceptions++;
		} catch (RemoteException e) {
			Log.e(TAG, "Probleem met data", e);
			syncResult.databaseError = true;
		} catch (OperationApplicationException e) {
			Log.e(TAG, "Synchronisatie data incorrect", e);
			syncResult.databaseError = true;
		} catch (JSONException e) {
			Log.e(TAG, "Probleem met data", e);
			syncResult.stats.numParseExceptions++;
		} catch (AuthenticatorException e) {
			Log.e(TAG, "Probleem met authenticatie", e);
			syncResult.databaseError = true;
		} catch (OperationCanceledException e) {
			Log.e(TAG, "Probleem met authenticatie", e);
			syncResult.databaseError = true;
		}
	}

	private void updateEvent(ArrayList<ContentProviderOperation> operationList, long calendarId, Account account, long id, JSONObject event) throws JSONException, RemoteException, OperationApplicationException {
		ContentProviderOperation.Builder raw = ContentProviderOperation.newUpdate(asSyncAdapter(Events.CONTENT_URI, account.name, account.type));
		raw.withSelection(Events._SYNC_ID + "=? AND " + Events.CALENDAR_ID + "=?", new String[]{String.format("%09d", id), String.format("%09d", calendarId)});
		raw.withValue(Events.DTSTART, String.format("%09d", event.getLong("start")*1000));
		raw.withValue(Events.DTEND, String.format("%09d", event.getLong("end")*1000));
		raw.withValue(Events.TITLE, event.getString("title"));

		operationList.add(raw.build());
	}

	private void addEvent(ArrayList<ContentProviderOperation> operationList, long calendarId, Account account, JSONObject event) throws JSONException, RemoteException, OperationApplicationException {
		ContentProviderOperation.Builder raw = ContentProviderOperation.newInsert(asSyncAdapter(Events.CONTENT_URI, account.name, account.type));
		raw.withValue(Events.CALENDAR_ID, calendarId);
		raw.withValue(Events._SYNC_ID, String.format("%09d", event.getLong("id")));
		raw.withValue(Events.DTSTART, String.format("%09d", event.getLong("start")*1000));
		raw.withValue(Events.DTEND, String.format("%09d", event.getLong("end")*1000));
		raw.withValue(Events.TITLE, event.getString("title"));
		raw.withValue(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

		operationList.add(raw.build());
	}

	private static Uri asSyncAdapter(Uri uri, String account, String accountType) {
		Uri.Builder builder = uri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true");
		builder.appendQueryParameter(Calendars.ACCOUNT_NAME, account);
		builder.appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType);
		return builder.build();
	}
}
