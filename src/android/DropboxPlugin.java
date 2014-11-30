package com.telerik.dropbox;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.dropbox.sync.android.*;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

public class DropboxPlugin extends CordovaPlugin {

	static final int REQUEST_LINK_TO_DBX = 141114;
	static final String LINK_ACCOUNT = "linkAccount";
	static final String LINKED_ACCOUNTS = "linkedAccounts";
	static final String SAVE_FILE = "saveFile";

	DbxAccountManager accountManager;

	CallbackContext keptCallbackContext;

	protected void pluginInitialize() {
		Context context= this.cordova.getActivity().getApplicationContext();

		int appResId = cordova.getActivity().getResources().getIdentifier("app_key", "string", cordova.getActivity().getPackageName());
		String appKey = cordova.getActivity().getString(appResId);

		appResId = cordova.getActivity().getResources().getIdentifier("app_secret", "string", cordova.getActivity().getPackageName());

		String appSecret = cordova.getActivity().getString(appResId);

		accountManager = DbxAccountManager.getInstance(context, appKey, appSecret);
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		try {
			if (action.equals(LINK_ACCOUNT)) {
				this.keptCallbackContext = callbackContext;
				linkAcccount();
			}
			else if (action.equals(LINKED_ACCOUNTS)) {
				linkedAccounts(callbackContext);
			}
			else if (action.equals(SAVE_FILE)){
				saveFile(callbackContext, args.getJSONObject(0));
			}
		} catch (Exception e) {
			callbackContext.error(e.getMessage());
			return false;
		}
		return true;
	}

	public void linkAcccount() {
		((CordovaActivity) this.cordova.getActivity()).setActivityResultCallback(this);
		accountManager.startLink(cordova.getActivity(), REQUEST_LINK_TO_DBX);
	}

	public void linkedAccounts(CallbackContext callbackContext) throws JSONException{
		List<DbxAccount> accounts = accountManager.getLinkedAccounts();

		JSONArray jsonArray = new JSONArray();

		for (final DbxAccount account : accounts) {
			JSONObject jsonObject = new JSONObject();

			jsonObject.put("userId", account.getUserId());
			jsonObject.put("userName", account.getAccountInfo().userName);

			jsonArray.put(jsonObject);
		}

		callbackContext.success(jsonArray);
	}

	public void saveFile(CallbackContext callbackContext, final JSONObject jsonObject) throws JSONException{
		if (jsonObject.has("files")){
			JSONArray jsonArray = jsonObject.getJSONArray("files");

			for(int index = 0; index < jsonArray.length(); index++) {
				String path = jsonArray.getString(index);
				Uri uri = Uri.parse(path);

				File file = new File(uri.getPath());
				try {
					DbxFileSystem dbxFs = DbxFileSystem.forAccount(accountManager.getLinkedAccount());

					String fileName = file.getName();

					DbxPath dbxPath = new DbxPath(fileName);

					if (jsonObject.has("folder")){
						DbxPath dbxFolder = new DbxPath("/" + jsonObject.getString("folder"));
						dbxPath = new DbxPath(dbxFolder, fileName);
					}

					DbxFile dbxFile = dbxFs.create(dbxPath);
					dbxFile.writeFromExistingFile(file, false);

					JSONObject result = new JSONObject();

					result.put("success", true);

					callbackContext.success(result);

				} catch (Exception e) {
					callbackContext.error(e.getMessage());
				}
			}
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_LINK_TO_DBX) {
			if (keptCallbackContext != null) {
				try {
					JSONObject result = new JSONObject();
					result.put("success", resultCode == Activity.RESULT_OK);
					keptCallbackContext.success(result);
				} catch (JSONException e) {
					keptCallbackContext.error(e.getMessage());
				}
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}
}
