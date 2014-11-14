package com.telerik.dropbox;

import java.util.Iterator;
import java.util.List;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.dropbox.sync.android.DbxAccount;
import com.dropbox.sync.android.DbxAccountManager;

public class DropboxPlugin extends CordovaPlugin {
	
	static final int REQUEST_LINK_TO_DBX = 141114;  // Date
	static final String LINK_ACCOUNT = "linkAccount";
	static final String LINKED_ACCOUNTS = "linkedAccounts";

	DbxAccountManager accountManager;
	
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
          linkAcccount(callbackContext);
        } else if (action.equals(LINKED_ACCOUNTS)) {
          linkedAccounts(callbackContext);
        }
      } catch (Exception e) {
        callbackContext.error(e.getMessage());
        return false;
      }
      return true;
    }
  
    public void linkAcccount(CallbackContext callbackContext) {
        accountManager.startLink(cordova.getActivity(), REQUEST_LINK_TO_DBX);
    }
    
    public void linkedAccounts(CallbackContext callbackContext) throws JSONException{
    	List<DbxAccount> accounts = accountManager.getLinkedAccounts();
    	
       	JSONArray jsonArray = new JSONArray();
       
    	Iterator<DbxAccount> iterator = accounts.iterator();
    	while (iterator.hasNext()) {
    		DbxAccount account = iterator.next();
    		
    		JSONObject jsonObject = new JSONObject();
    		
    		jsonObject.put("userId", account.getUserId());
    		
    		jsonArray.put(jsonObject);
    	}
    	
    	callbackContext.success(jsonArray);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LINK_TO_DBX) {
            if (resultCode == Activity.RESULT_OK) {
            } else {
            	// TODO://
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
