/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ThreadPoolExecutor;

import de.radioshuttle.net.DashboardRequest;
import de.radioshuttle.net.Request;
import de.radioshuttle.utils.Utils;

public class ImageChooserViewModel extends AndroidViewModel {

    public ImageChooserViewModel(@NonNull Application application, boolean selectionMode) {
        super(application);
        mSelctionMode = selectionMode;

        ImageDataSource.Factory factory = new ImageDataSource.Factory(this);
        mLiveDataInternalImages = new LivePagedListBuilder(factory, 100).build();

        ImageDataSourceUser.Factory userFactory = new ImageDataSourceUser.Factory(this);
        mLiveDataUserImages = new LivePagedListBuilder(userFactory, 100).build();
        mImportedFilesErrorLiveData = new MutableLiveData<>();

        IntentFilter intentFilter = new IntentFilter(ImportFiles.INTENT_ACTION);
        // intentFilter.addAction(FirebaseTokens.TOKEN_UPDATED);
        LocalBroadcastManager.getInstance(application).registerReceiver(broadcastReceiver, intentFilter);


    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (getApplication() != null && broadcastReceiver != null) {
            LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(broadcastReceiver);
        }
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(ImportFiles.INTENT_ACTION)) {
                String jsonResult = intent.getStringExtra(ImportFiles.ARG_RESULT_JSON);
                int cnt = 0;
                try {
                    JSONArray jsonArray = new JSONArray(jsonResult);
                    JSONArray jsError = new JSONArray();
                    for(int i = 0; i < jsonArray.length(); i++) {
                        JSONObject object = jsonArray.getJSONObject(i);
                        int status = object.optInt("status", -1);
                        if (status > 0) {
                            jsError.put(object);
                        } else if (status == ImportFiles.STATUS_OK) {
                            cnt++;
                        }
                    }
                    mImportedFilesErrorLiveData.setValue(jsError);
                    if (cnt > 0) {
                        // reload
                        mLiveDataUserImages.getValue().getDataSource().invalidate();
                    }
                } catch(Exception e) {
                    Log.e(TAG, "Error parsing json: ", e);
                }
            }
        }
    };

    public boolean isSelectionMode() {
        return mSelctionMode;
    }

    public final LiveData<PagedList<ImageResource>> mLiveDataInternalImages;
    public final LiveData<PagedList<ImageResource>> mLiveDataUserImages;
    public MutableLiveData<JSONArray> mImportedFilesErrorLiveData;

    private boolean mSelctionMode;

    public static class Factory implements ViewModelProvider.Factory {

        public Factory(Application app, boolean selectionMode) {
            this.app = app;
            this.selectionMode = selectionMode;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new ImageChooserViewModel(app, selectionMode);
        }

        Application app;
        boolean selectionMode;
    }

    private final static String TAG = ImageChooserViewModel.class.getSimpleName();
}
