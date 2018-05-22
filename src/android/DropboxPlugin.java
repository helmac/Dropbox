package com.telerik.dropbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.users.FullAccount;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;

public class DropboxPlugin extends CordovaPlugin {
    private static final String TAG = DropboxPlugin.class.getName();
    private static final String LINK_ACCOUNT = "linkAccount";
    private static final String LINKED_ACCOUNTS = "linkedAccounts";
    private static final String SAVE_FILE = "saveFile";
    private static final String ON_PROGRESS_UPLOAD = "onProgressUpload";

    private String appKey;
    private String appSecret;

    private CallbackContext keptCallbackContext;
    private CallbackContext progressCallbackContext;

    protected void pluginInitialize() {
        Context context = this.cordova.getActivity().getApplicationContext();

        int appResId = cordova.getActivity().getResources().getIdentifier("dropbox_app_key", "string", cordova.getActivity().getPackageName());
        appKey = cordova.getActivity().getString(appResId);

        appResId = cordova.getActivity().getResources().getIdentifier("dropbox_app_secret", "string", cordova.getActivity().getPackageName());
        appSecret = cordova.getActivity().getString(appResId);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        try {
            if (action.equals(LINK_ACCOUNT)) {
                this.keptCallbackContext = callbackContext;
                linkAcccount();
            } else if (action.equals(LINKED_ACCOUNTS)) {
                linkedAccounts(callbackContext);
            } else if (action.equals(SAVE_FILE)) {
                saveFile(callbackContext, args.getJSONObject(0));
            } else if (action.equals(ON_PROGRESS_UPLOAD)) {
                this.progressCallbackContext = callbackContext;
            }
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
            return false;
        }
        return true;
    }

    private void linkAcccount() {
        if (hasToken()) {
            authenticate();
        } else {
            Auth.startOAuth2Authentication(cordova.getActivity(), appKey);
        }
    }

    private void linkedAccounts(CallbackContext callbackContext) throws JSONException, DbxException {
        DbxClientV2 mDbxClient = DropboxClientFactory.getClient();

        JSONArray jsonArray = new JSONArray();
        JSONObject jsonObject = new JSONObject();

        FullAccount account = mDbxClient.users().getCurrentAccount();
        ;
        jsonObject.put("userId", account.getAccountId());
        jsonObject.put("userName", account.getName().getDisplayName());

        jsonArray.put(jsonObject);


        callbackContext.success(jsonArray);
    }

    public void saveFile(CallbackContext callbackContext, final JSONObject jsonObject) throws JSONException {
        if (jsonObject.has("files")) {
            JSONArray jsonArray = jsonObject.getJSONArray("files");

            for (int index = 0; index < jsonArray.length(); index++) {
                String path = jsonArray.getString(index);
                Uri uri = Uri.parse(path);
                try {
                    uploadFile(uri.getPath(), jsonObject.getString("folder"), callbackContext);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        SharedPreferences prefs = this.cordova.getActivity().getSharedPreferences("dropbox-telerik", Context.MODE_PRIVATE);
        authenticate();
        String uid = Auth.getUid();
        String storedUid = prefs.getString("user-id", null);
        if (uid != null && !uid.equals(storedUid)) {
            prefs.edit().putString("user-id", uid).apply();
        }
    }

    private void authenticate() {
        SharedPreferences prefs = this.cordova.getActivity().getSharedPreferences("dropbox-telerik", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access-token", null);
        if (accessToken == null) {
            accessToken = Auth.getOAuth2Token();
            if (accessToken != null) {
                prefs.edit().putString("access-token", accessToken).apply();
                initAndLoadData(accessToken);
            }
        } else {
            Log.i(TAG, "Exist access Token " + accessToken);
            initAndLoadData(accessToken);
        }

        if (keptCallbackContext != null) {
            try {
                JSONObject result = new JSONObject();
                result.put("success", accessToken != null);
                keptCallbackContext.success(result);
            } catch (JSONException e) {
                keptCallbackContext.error(e.getMessage());
            }
        }
    }

    private void uploadFile(String filePath, String folder, final CallbackContext callbackContext) {
        final Context context = this.cordova.getActivity().getApplicationContext();

        new UploadFileTask(context, DropboxClientFactory.getClient(), new UploadFileTask.Callback() {
            @Override
            public void onProgress(long attempt, long uploaded, long size) {
                JSONObject progress = new JSONObject();
                try {
                    progress.put("attempt", attempt);
                    progress.put("uploaded", uploaded);
                    progress.put("size", size);
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, progress);
                    pluginResult.setKeepCallback(true);
                    progressCallbackContext.sendPluginResult(pluginResult);
                } catch (JSONException e) {
                    progressCallbackContext.error(e.getMessage());
                }
            }

            @Override
            public void onUploadComplete(FileMetadata result) {
                String message = result.getName() + " size " + result.getSize() + " modified " +
                        DateFormat.getDateTimeInstance().format(result.getClientModified());
                Toast.makeText(context, message, Toast.LENGTH_SHORT)
                        .show();
                try {
                    JSONObject response = new JSONObject();
                    response.put("success", true);
                    callbackContext.success(response);
                } catch (JSONException e) {
                    callbackContext.error(e.getMessage());
                }

            }

            @Override
            public void onError(Exception e) {

                Log.e(TAG, "Failed to upload file.", e);
                Toast.makeText(context,
                        "An error has occurred",
                        Toast.LENGTH_SHORT)
                        .show();
                callbackContext.error(e.getMessage());
            }
        }).execute(filePath, folder);
    }

    private boolean hasToken() {
        SharedPreferences prefs = this.cordova.getActivity().getSharedPreferences("dropbox-telerik", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access-token", null);
        return accessToken != null;
    }

    private void initAndLoadData(String accessToken) {
        DropboxClientFactory.init(accessToken);
    }
}
