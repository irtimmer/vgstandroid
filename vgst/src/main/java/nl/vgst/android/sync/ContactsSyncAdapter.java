package nl.vgst.android.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import nl.vgst.android.Api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Settings;
import android.util.Log;


public class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {
	
	private static final String TAG = "ContactsSyncAdapter";

	public ContactsSyncAdapter(Context context) {
		super(context, true);
	}

	@Override
	public void onPerformSync(final Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
	    Api api = new Api(account, getContext());
	    
		try {
			ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

			Cursor gcursor = provider.query(Settings.CONTENT_URI, new String[] {}, Settings.ACCOUNT_TYPE + "=?", new String[] {account.type}, null);
		    if (!gcursor.moveToNext()) {
		    	ContentProviderOperation.Builder settings = ContentProviderOperation.newInsert(Settings.CONTENT_URI);
		    	settings.withValue(Settings.ACCOUNT_NAME, account.name);
		    	settings.withValue(Settings.ACCOUNT_TYPE, account.type);
		    	settings.withValue(Settings.UNGROUPED_VISIBLE, true);
		        operationList.add(settings.build());
		    }
			
			JSONObject data = api.get("users/api/getMembers");
		    
			//ContentResolver contentResolver = getContext().getContentResolver();
		    Uri rawContactUri = RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name).appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type).build();
			Cursor c1 = provider.query(rawContactUri, new String[] { RawContacts._ID, RawContacts.SYNC1, RawContacts.SYNC2 }, null, null, RawContacts.SYNC1);
			
			JSONArray members = data.getJSONArray("data");
			for (int i=0;i<members.length();i++) {
				JSONObject member = members.getJSONObject(i);
				long id = member.getLong("id");
				
				boolean processed = false;
				
				while (!processed) {
					if (c1.moveToNext()) {
	    				long id2 = Long.parseLong(c1.getString(1));
	    				
	    				if (id==id2) {
	    					int oldPhotoId = -1;
	    					try {
	    						oldPhotoId = Integer.parseInt(c1.getString(2));
	    					} catch (NumberFormatException e) {}
	    					updateContact(api, account, provider, c1.getLong(0), member, oldPhotoId);
	    					processed = true;
	    				} else if (id<id2) {
	    					addContact(api, account, provider, member);
	    					c1.moveToPrevious();
	    					processed = true;
	    				} else {
	    					ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(RawContacts.CONTENT_URI);
	    					builder.withSelection(RawContacts._ID + "=?", new String[]{String.valueOf(c1.getLong(0))});
	    					operationList.add(builder.build());
	    				}
	    			} else {
	    				addContact(api, account, provider, member);
	    				processed = true;
	    			}
				}
			}
			
			while (c1.moveToNext()) {
				ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(RawContacts.CONTENT_URI);
				builder.withSelection(RawContacts._ID + "=?", new String[]{String.valueOf(c1.getLong(0))});
				operationList.add(builder.build());
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
		}
	}
	
	private void updateContact(Api api, Account account, ContentProviderClient provider, long id, JSONObject member, int oldPhotoId) throws IOException, RemoteException, OperationApplicationException, JSONException {
		ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
		
		ContentProviderOperation.Builder raw = ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI);
		raw.withSelection(ContactsContract.RawContacts.SYNC1 + "=?", new String[]{String.format("%09d", member.getLong("id"))});
		
		ContentProviderOperation.Builder email = ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI);
		email.withSelection(ContactsContract.CommonDataKinds.Email.RAW_CONTACT_ID + "=?" + " AND " + ContactsContract.Data.MIMETYPE + "=?", new String[]{String.valueOf(id), ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE});
		
		ContentProviderOperation.Builder name = ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI);
		name.withSelection(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID + "=?" + " AND " + ContactsContract.Data.MIMETYPE + "=?", new String[]{String.valueOf(id), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE});
		
		ContentProviderOperation.Builder telephone = ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI);
		telephone.withSelection(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID + "=?" + " AND " + ContactsContract.Data.MIMETYPE + "=?", new String[]{String.valueOf(id), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE});
		
		ContentProviderOperation.Builder photo = null;
		int photoId = member.getInt("photoId");
		
		if (photoId!=oldPhotoId) {
			if (oldPhotoId>0) {
				photo = ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI);
				photo.withSelection(ContactsContract.CommonDataKinds.Photo.RAW_CONTACT_ID + "=?" + " AND " + ContactsContract.Data.MIMETYPE + "=?", new String[]{String.valueOf(id), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE});
			} else if (photoId>0){
				photo = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);				
			} else {
				ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(RawContacts.CONTENT_URI);
				builder.withSelection(ContactsContract.CommonDataKinds.Photo.RAW_CONTACT_ID + "=?" + " AND " + ContactsContract.Data.MIMETYPE + "=?", new String[]{String.valueOf(id), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE});
				operationList.add(builder.build());
			}
		}
		
		setBuilders(api, operationList, id, member, raw, name, email, telephone, photo);
		
		provider.applyBatch(operationList);
		operationList.clear();
	}

	private void addContact(Api api, Account account, ContentProviderClient provider, JSONObject member) throws IOException, RemoteException, OperationApplicationException, JSONException {
		ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
		ContentProviderOperation.Builder raw = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
		raw.withValue(RawContacts.ACCOUNT_NAME, account.name);
		raw.withValue(RawContacts.ACCOUNT_TYPE, account.type);
		raw.withValue(RawContacts.SYNC1, String.format("%09d", member.getLong("id")));

		ContentProviderOperation.Builder email = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
		ContentProviderOperation.Builder name = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
		ContentProviderOperation.Builder telephone = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
		ContentProviderOperation.Builder photo = null;
		if (member.getInt("photoId")>0)
			photo = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
		
		setBuilders(api, operationList, 0, member, raw, name, email, telephone, photo);
		provider.applyBatch(operationList);
		operationList.clear();
	}
	
	private void setBuilders(Api api, List<ContentProviderOperation> operationList, long id, JSONObject member, ContentProviderOperation.Builder raw, ContentProviderOperation.Builder name, ContentProviderOperation.Builder email, ContentProviderOperation.Builder telephone, ContentProviderOperation.Builder photo) throws IOException, JSONException {
        raw.withValue(RawContacts.SYNC2, member.get("photoId"));
        operationList.add(raw.build());

        if (photo!=null) {
    		if (id==0)
    			photo.withValueBackReference(ContactsContract.CommonDataKinds.Photo.RAW_CONTACT_ID, 0);
    		else
    			photo.withValue(ContactsContract.CommonDataKinds.Photo.RAW_CONTACT_ID, id);
    		
			photo.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
			
			try {
				byte[] data = api.download("users/profile/photo/type/big/id/"+member.getLong("id"));
		
				photo.withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, data);
				operationList.add(photo.build());
			} catch (IOException e) {
				Log.e(TAG, "Can't download image", e);
			}
        }

		if (id==0)
			name.withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, 0);
		else
			name.withValue(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, id);
		
		name.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
		name.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, member.getString("firstName"));
		name.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, member.getString("lastName"));
		operationList.add(name.build());
		
		if (id==0)
			telephone.withValueBackReference(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, 0);
		else
			telephone.withValue(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, id);

		telephone.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
		telephone.withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, member.getString("telephone"));
		telephone.withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
        operationList.add(telephone.build());
        
		if (id==0)
			email.withValueBackReference(ContactsContract.CommonDataKinds.Email.RAW_CONTACT_ID, 0);
		else
			email.withValue(ContactsContract.CommonDataKinds.Email.RAW_CONTACT_ID, id);

		email.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
		email.withValue(ContactsContract.CommonDataKinds.Email.DATA, member.getString("email"));
		email.withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME);
        operationList.add(email.build());

	}

}
