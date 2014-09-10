/**
 * Copyright (C) 2013, 2014 Iwan Timmer
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

import nl.vgst.android.Vgst;
import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * AccountAuthenticator for VGST accounts
 * @author Iwan Timmer
 */
public class AccountAuthenticator extends AbstractAccountAuthenticator {
	
	private Context	context;
	
	public AccountAuthenticator(Context context) {
		super(context);
		this.context = context;
	}

	@Override
	public Bundle addAccount(AccountAuthenticatorResponse response,
			String accountType, String authTokenType,
			String[] requiredFeatures, Bundle options)
			throws NetworkErrorException {
		
		Intent intent = new Intent(context, LoginActivity.class);
		intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
		
		Bundle reply = new Bundle();
		reply.putParcelable(AccountManager.KEY_INTENT, intent);

		return reply;
	}

	@Override
	public Bundle hasFeatures(AccountAuthenticatorResponse response,
			Account account, String[] features) throws NetworkErrorException {
		final Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
        return result;
	}

	@Override
	public Bundle updateCredentials(AccountAuthenticatorResponse response,
			Account account, String authTokenType, Bundle options) {
		Intent intent = new Intent(context, LoginActivity.class);
		intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
		intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name);
		
		Bundle reply = new Bundle();
		reply.putParcelable(AccountManager.KEY_INTENT, intent);

		return reply;
	}
	
	@Override
	public Bundle confirmCredentials(AccountAuthenticatorResponse response,
			Account account, Bundle options) {
		return null;
	}

	@Override
	public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
		return null;
	}

	@Override
	public Bundle getAuthToken(AccountAuthenticatorResponse response,
			Account account, String authTokenType, Bundle options)
			throws NetworkErrorException {
		return null;
	}

	@Override
	public String getAuthTokenLabel(String authTokenType) {
		if (Vgst.AUTHTOKEN_TYPE_FULL_ACCESS.equals(authTokenType))
            return Vgst.AUTHTOKEN_TYPE_FULL_ACCESS_LABEL;
        else
            return authTokenType + " (Label)";
	}
}