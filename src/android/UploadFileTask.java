package com.telerik.dropbox;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.NetworkIOException;
import com.dropbox.core.RetryException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CommitInfo;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.UploadErrorException;
import com.dropbox.core.v2.files.UploadSessionCursor;
import com.dropbox.core.v2.files.UploadSessionFinishErrorException;
import com.dropbox.core.v2.files.UploadSessionLookupErrorException;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * Async task to upload a file to a directory
 */
class UploadFileTask extends AsyncTask<String, Void, FileMetadata> {
    // Adjust the chunk size based on your network speed and reliability. Larger chunk sizes will
    // result in fewer network requests, which will be faster. But if an error occurs, the entire
    // chunk will be lost and have to be re-uploaded. Use a multiple of 4MiB for your chunk size.
    private static final long CHUNKED_UPLOAD_CHUNK_SIZE = 8L << 20; // 8MiB
    private static final int CHUNKED_UPLOAD_MAX_ATTEMPTS = 5;
    private static final String TAG = UploadFileTask.class.getName();

    private final Context mContext;
    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    public interface Callback {
        void onProgress(long attempt, long uploaded, long size);

        void onUploadComplete(FileMetadata result);

        void onError(Exception e);
    }

    UploadFileTask(Context context, DbxClientV2 dbxClient, Callback callback) {
        mContext = context;
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    @Override
    protected void onPostExecute(FileMetadata result) {
        super.onPostExecute(result);
        if (mException != null) {
            mCallback.onError(mException);
        } else if (result == null) {
            mCallback.onError(null);
        } else {
            mCallback.onUploadComplete(result);
        }
    }

    @Override
    protected FileMetadata doInBackground(String... params) {
        String path = params[0];
        Log.i(TAG, "Path: " + path);
        File localFile = new File(path);

        if (localFile != null && localFile.exists()) {
            String remoteFolderPath = params[1];
            Log.i(TAG, "folder: " + remoteFolderPath);

            long size = localFile.length();

            // assert our file is at least the chunk upload size. We make this assumption in the code
            // below to simplify the logic.
            if (size > CHUNKED_UPLOAD_CHUNK_SIZE) {
                return chunkedUploadFile(localFile, remoteFolderPath);
            } else {
                return uploadFile(localFile, remoteFolderPath);
            }
        }

        return null;
    }

    /**
     * Uploads a file in a single request. This approach is preferred for small files since it
     * eliminates unnecessary round-trips to the servers.
     *
     * @param localFile   local file to upload
     * @param dropboxPath Where to upload the file to within Dropbox
     */
    private FileMetadata uploadFile(File localFile, String dropboxPath) {
        InputStream in = null;
        try {
            in = new FileInputStream(localFile);
            
            return mDbxClient.files().uploadBuilder(dropboxFilename(localFile,dropboxPath))
                    .withMode(WriteMode.OVERWRITE)
                    .withClientModified(new Date(localFile.lastModified()))
                    .uploadAndFinish(in);
        } catch (UploadErrorException ex) {
            mException = ex;
        } catch (DbxException ex) {
            mException = ex;
        } catch (IOException ex) {
            mException = ex;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * Uploads a file in chunks using multiple requests. This approach is preferred for larger files
     * since it allows for more efficient processing of the file contents on the server side and
     * also allows partial uploads to be retried (e.g. network connection problem will not cause you
     * to re-upload all the bytes).
     *
     * @param localFile   local file to upload
     * @param dropboxPath Where to upload the file to within Dropbox
     */
    private FileMetadata chunkedUploadFile(File localFile, String dropboxPath) {
        long size = localFile.length();

        long uploaded = 0L;
        DbxException thrown = null;

        // Chunked uploads have 3 phases, each of which can accept uploaded bytes:
        //
        //    (1)  Start: initiate the upload and get an upload session ID
        //    (2) Append: upload chunks of the file to append to our session
        //    (3) Finish: commit the upload and close the session
        //
        // We track how many bytes we uploaded to determine which phase we should be in.
        String sessionId = null;
        for (int i = 0; i < CHUNKED_UPLOAD_MAX_ATTEMPTS; ++i) {
            if (i > 0) {
                mCallback.onProgress(i + 1, 0, 0);
            }
            InputStream in = null;
            try {
                in = new FileInputStream(localFile);
                // if this is a retry, make sure seek to the correct offset
                in.skip(uploaded);

                // (1) Start
                if (sessionId == null) {
                    sessionId = mDbxClient.files().uploadSessionStart()
                            .uploadAndFinish(in, CHUNKED_UPLOAD_CHUNK_SIZE)
                            .getSessionId();
                    uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                    mCallback.onProgress(i + 1, uploaded, size);
                }

                UploadSessionCursor cursor = new UploadSessionCursor(sessionId, uploaded);

                // (2) Append
                while ((size - uploaded) > CHUNKED_UPLOAD_CHUNK_SIZE) {
                    mDbxClient.files().uploadSessionAppendV2(cursor)
                            .uploadAndFinish(in, CHUNKED_UPLOAD_CHUNK_SIZE);
                    uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                    mCallback.onProgress(i + 1, uploaded, size);
                    cursor = new UploadSessionCursor(sessionId, uploaded);
                }

                // (3) Finish
                long remaining = size - uploaded;
                CommitInfo commitInfo = CommitInfo.newBuilder(dropboxFilename(localFile,dropboxPath))
                        .withMode(WriteMode.OVERWRITE)
                        .withClientModified(new Date(localFile.lastModified()))
                        .build();

                return mDbxClient.files().uploadSessionFinish(cursor, commitInfo)
                        .uploadAndFinish(in, remaining);

            } catch (RetryException ex) {
                mException = ex;
                // RetryExceptions are never automatically retried by the client for uploads. Must
                // catch this exception even if DbxRequestConfig.getMaxRetries() > 0.
                sleepQuietly(ex.getBackoffMillis());
            } catch (NetworkIOException ex) {
                mException = ex;
                // network issue with Dropbox (maybe a timeout?) try again
            } catch (UploadSessionLookupErrorException ex) {
                if (ex.errorValue.isIncorrectOffset()) {
                    mException = ex;
                    // server offset into the stream doesn't match our offset (uploaded). Seek to
                    // the expected offset according to the server and try again.
                    uploaded = ex.errorValue
                            .getIncorrectOffsetValue()
                            .getCorrectOffset();
                } else {
                    mException = ex;
                    return null;
                }
            } catch (UploadSessionFinishErrorException ex) {
                if (ex.errorValue.isLookupFailed() && ex.errorValue.getLookupFailedValue().isIncorrectOffset()) {
                    mException = ex;
                    // server offset into the stream doesn't match our offset (uploaded). Seek to
                    // the expected offset according to the server and try again.
                    uploaded = ex.errorValue
                            .getLookupFailedValue()
                            .getIncorrectOffsetValue()
                            .getCorrectOffset();
                } else {
                    mException = ex;
                    return null;
                }
            } catch (DbxException ex) {
                mException = ex;
                return null;
            } catch (IOException ex) {
                mException = ex;
                return null;
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }

        }
        return null;
    }

    private String dropboxFilename(File localFile, String dropboxPath){
        return (dropboxPath!=null && !dropboxPath.isEmpty() ? "/"+dropboxPath+"/"+localFile.getName():"/"+localFile.getName());
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            mException = new Exception("Error uploading to Dropbox: interrupted during backoff.", ex);
        }
    }
}

