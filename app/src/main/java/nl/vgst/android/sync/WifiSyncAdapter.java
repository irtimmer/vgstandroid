/**
 * Copyright (C) 2015 Iwan Timmer
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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import nl.vgst.android.Api;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class WifiSyncAdapter extends AbstractThreadedSyncAdapter {

    private final static String TAG = "WifiSyncAdapter";

    public WifiSyncAdapter(Context context) {
        super(context, true);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        SharedPreferences prefs = getContext().getSharedPreferences("nl.vgst.wifi", Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        WifiManager manager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        try {
            Api api = new Api(account, getContext());
            JSONObject data = api.get("users/api/getWifi");

            Set<String> networkSet = new HashSet<>();
            JSONArray networks = data.getJSONArray("data");
            for (int i=0;i<networks.length();i++) {
                JSONObject network = networks.getJSONObject(i);
                int nid = prefs.getInt("network." + network.getInt("id"), 0);
                networkSet.add(Integer.toString(network.getInt("id")));

                if (nid == 0) {
                    WifiConfiguration config = new WifiConfiguration();
                    config.SSID = '"' + network.getString("ssid") + '"';
                    config.preSharedKey = '"' + network.getString("password") + '"';
                    Log.v(TAG, "Add network " + network.getInt("id") + " " + config.SSID);
                    int id = manager.addNetwork(config);
                    manager.enableNetwork(id, false);
                    edit.putInt("network." + network.getInt("id"), id);
                } else {
                    WifiConfiguration config = new WifiConfiguration();
                    config.networkId = nid;
                    config.SSID = '"' + network.getString("ssid") + '"';
                    config.preSharedKey = '"' + network.getString("password") + '"';
                    Log.v(TAG, "Update network " + network.getInt("id") + " " + config.SSID);
                    manager.updateNetwork(config);
                }
            }

            Set<String> oldNetworkSet = prefs.getStringSet("networks.list", null);
            if (oldNetworkSet != null) {
                oldNetworkSet.removeAll(networkSet);
                for (String network:oldNetworkSet) {
                    Log.v(TAG, "Remove network " + network);
                    int nid = prefs.getInt("network." + network, 0);
                    manager.removeNetwork(nid);
                }
                edit.putStringSet("networks.list", networkSet);
            }

            edit.commit();
        } catch (IOException e) {
            Log.e(TAG, "Kan data niet lezen", e);
            syncResult.stats.numIoExceptions++;
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
}
