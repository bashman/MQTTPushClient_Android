/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.app.AlertDialog;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import android.content.DialogInterface;
import android.content.Intent;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Iterator;

import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.fcm.Notifications;
import de.radioshuttle.net.ActionsRequest;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.Request;

import static de.radioshuttle.mqttpushclient.AccountListActivity.RC_ACTIONS;
import static de.radioshuttle.mqttpushclient.AccountListActivity.RC_SUBSCRIPTIONS;
import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;

public class MessagesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);
        setTitle(getString(R.string.title_messages));

        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_ACCOUNT_JSON);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);
        mListView = findViewById(R.id.messagesListView);
        try {
            PushAccount b = PushAccount.createAccountFormJSON(new JSONObject(json));
            TextView server = findViewById(R.id.push_notification_server);
            TextView key = findViewById(R.id.account_display_name);
            server.setText(b.pushserver);
            key.setText(b.getDisplayName());
            if (!hastMultipleServer) {
                server.setVisibility(View.GONE);
            }
            mViewModel = ViewModelProviders.of(
                    this, new MessagesViewModel.Factory(b.pushserverID, b.getMqttAccountName(), getApplication()))
                    .get(MessagesViewModel.class);
            if (mViewModel.pushAccount == null)
                mViewModel.pushAccount = b;

            mActionsViewModel = ViewModelProviders.of(this).get(ActionsViewModel.class);
            boolean actionsLoaded = mActionsViewModel.initialized;
            mActionsViewModel.init(json);


            final MessagesPagedListAdapter adapter = new MessagesPagedListAdapter(this);
            mListView.setAdapter(adapter);
            mAdapterObserver = new RecyclerView.AdapterDataObserver() {
                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    // Log.d(TAG, "item inserted: " + positionStart + " cnt: " + itemCount);
                    if (positionStart == 0) {
                        int pos = ((LinearLayoutManager) mListView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
                        if (pos >= 0) {
                            MessagesPagedListAdapter a = (MessagesPagedListAdapter) mListView.getAdapter();
                            if (a != null) {
                                mListView.scrollToPosition(0);
                            }
                        }
                    }
                }
            };
            adapter.registerAdapterDataObserver(mAdapterObserver);
            mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        // Log.d(TAG, "scroll state dragging");
                        adapter.clearSelection();
                    }
                }
            });

            mViewModel.messagesPagedList.observe(this, new Observer<PagedList<MqttMessage>>() {
                @Override
                public void onChanged(@Nullable PagedList<MqttMessage> mqttMessages) {
                    adapter.submitList(mqttMessages, mViewModel.newItems);
                }
            });

            mActionsViewModel.actionsRequest.observe(this, new Observer<Request>() {
                @Override
                public void onChanged(@Nullable Request request) {
                    if (request != null && request instanceof ActionsRequest) {
                        ActionsRequest actionsRequest = (ActionsRequest) request;
                        PushAccount b = actionsRequest.getAccount();
                        if (b.status == 1) {
                            // special case: configuration change and load in progress -> update view
                        } else {
                            if (mActionsViewModel.isCurrentRequest(request)) {
                                mActionsViewModel.confirmResultDelivered();
                                mSwipeRefreshLayout.setRefreshing(false);
                                invalidateOptionsMenu();

                                /* handle cerificate exception */
                                if (request.hasCertifiateException()) {
                                    /* only show dialog if the certificate has not already been denied */
                                    if (!AppTrustManager.isDenied(request.getCertificateException().chain[0])) {
                                        FragmentManager fm = getSupportFragmentManager();

                                        String DLG_TAG = CertificateErrorDialog.class.getSimpleName() + "_" +
                                                AppTrustManager.getUniqueKey(request.getCertificateException().chain[0]);

                                        /* check if a dialog is not already showing (for this certificate) */
                                        if (fm.findFragmentByTag(DLG_TAG) == null) {
                                            CertificateErrorDialog dialog = new CertificateErrorDialog();
                                            Bundle args = CertificateErrorDialog.createArgsFromEx(
                                                    request.getCertificateException(), request.getAccount().pushserver);
                                            if (args != null) {
                                                dialog.setArguments(args);
                                                dialog.show(getSupportFragmentManager(), DLG_TAG);
                                                Log.d(TAG, "dialog show!!"); //TODO: remove
                                            }
                                        }
                                    }
                                } /* end dialog already showing */
                                request.setCertificateExeption(null); // mark es "processed"
                                /* end handle cerificate exception */

                            }
                            if (b.requestStatus != Cmd.RC_OK) {
                                String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                                if (b.requestStatus == Cmd.RC_MQTT_ERROR || b.requestStatus == Cmd.RC_NOT_AUTHORIZED) {
                                    t = MessagesActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                                }
                                showErrorMsg(t);
                            } else {
                                if (actionsRequest.requestStatus != Cmd.RC_OK) { // getActions() or publish failed
                                    String t = (actionsRequest.requestErrorTxt == null ? "" : actionsRequest.requestErrorTxt);
                                    if (actionsRequest.requestStatus == Cmd.RC_MQTT_ERROR) {
                                        t = MessagesActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                                    }
                                    showErrorMsg(t);
                                } else {
                                    if (mSnackbar != null && mSnackbar.isShownOrQueued()) {
                                        mSnackbar.dismiss();
                                    }
                                }
                            }
                        }
                    }
                }
            });

            mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
            mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    refreshActions(false);
                }
            });

            if (!actionsLoaded) {
                refreshActions(true);
            } else {
                mSwipeRefreshLayout.setRefreshing(mActionsViewModel.isRequestActive());

            }


        } catch (JSONException e) {
            Log.e(TAG, "parse error", e);
        }

        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mListView.addItemDecoration(itemDecoration);
        // mListView.setItemAnimator(null);
        mListView.setLayoutManager(new LinearLayoutManager(this));



        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_messages, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        ActionsRequest r = (ActionsRequest) mActionsViewModel.actionsRequest.getValue();
        if (r != null)
        {
            if (r.mActions != null) {
                menu.removeGroup(ACTION_ITEM_GROUP_ID);
                MenuItem item;

                int i = 0;
                for(ActionsViewModel.Action a : r.mActions) {
                    item = menu.add(ACTION_ITEM_GROUP_ID, i++,  200+ i, a.name );
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                }
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    protected void showErrorMsg(String msg) {
        View v = findViewById(R.id.rView);
        if (v != null) {
            mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getGroupId() == ACTION_ITEM_GROUP_ID) {
            if (!mActivityStarted) {
                String actionCmd = item.getTitle().toString();
                Log.d(TAG, "action item clicked: " +  actionCmd);
                ActionsRequest r = (ActionsRequest) mActionsViewModel.actionsRequest.getValue();
                if (r != null) {
                    for(Iterator<ActionsViewModel.Action> it = r.mActions.iterator(); it.hasNext();) {
                        ActionsViewModel.Action a = it.next();
                        if (a.name.equals(actionCmd)) {
                            mActionsViewModel.publish(getApplicationContext(), a);
                            Toast.makeText(this, getString(R.string.action_cmd_exe, actionCmd), Toast.LENGTH_LONG).show();
                            break;
                        }
                    }
                }
            }
            return true;
        }

        switch (item.getItemId()) {
            case android.R.id.home :
                handleBackPressed();
                return true;
            case R.id.action_subscriptions :
                if (!mActivityStarted) {
                    mActivityStarted = true;
                    Bundle args = getIntent().getExtras();
                    Intent intent = new Intent(this, TopicsActivity.class);
                    intent.putExtra(PARAM_ACCOUNT_JSON, args.getString(PARAM_ACCOUNT_JSON));
                    intent.putExtra(PARAM_MULTIPLE_PUSHSERVERS, args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS));
                    startActivityForResult(intent, RC_SUBSCRIPTIONS);
                }
                return true;
            case R.id.menu_actions :
                if (!mActivityStarted) {
                    mActivityStarted = true;
                    Bundle args = getIntent().getExtras();
                    Intent intent = new Intent(this, ActionsActivity.class);
                    intent.putExtra(PARAM_ACCOUNT_JSON, args.getString(PARAM_ACCOUNT_JSON));
                    intent.putExtra(PARAM_MULTIPLE_PUSHSERVERS, args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS));
                    startActivityForResult(intent, RC_ACTIONS);
                }
                return true;
            case R.id.menu_delete :
                if (!mActivityStarted) {
                    showDeleteDialog();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void doRefresh() {
        if (mViewModel != null) {
            mViewModel.refresh();
        }
    }

    protected void refreshActions(boolean setRefreshing) {
        if (!mActionsViewModel.isRequestActive()) {
            if (setRefreshing)
                mSwipeRefreshLayout.setRefreshing(true);
            mActionsViewModel.getActions(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mListView != null && mAdapterObserver != null) {
            RecyclerView.Adapter a = mListView.getAdapter();
            if (a != null) {
                a.unregisterAdapterDataObserver(mAdapterObserver);
            }
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

    protected void showDeleteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String title = getString(R.string.dlg_del_messages_title);
        String all = getString(R.string.dlg_item_delete_all);
        String oneDay = getString(R.string.dlg_item_delete_older_one_day);

        builder.setTitle(title);

        final int[] selection = new int[] {1};
        builder.setSingleChoiceItems(new String[]{all, oneDay}, selection[0], new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                selection[0] = item;
            }
        });
        builder.setPositiveButton(getString(R.string.action_delete_msgs), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Long before;
                if (selection[0] == 0) {
                    before = null;
                } else {
                    before = new Date().getTime() - (24L * 1000L * 3600L);
                }
                MessagesPagedListAdapter a = (MessagesPagedListAdapter) mListView.getAdapter();
                if (a != null) {
                    a.clearSelection();
                }
                mViewModel.deleteMessages(before);
            }
        });
        builder.setNegativeButton(getString(R.string.action_cancel), null);
        AlertDialog dlg = builder.create();

        dlg.show();

    }


    protected void handleBackPressed() {
        String name = mViewModel.pushAccount.getNotifcationChannelName();
        Notifications.cancelAll(this, name); // clear systen notification tray
        Notifications.cancelAll(this, name + ".a"); // clear systen notification tray
        Notifications.resetNewMessageCounter(this, mViewModel.pushAccount.pushserver, mViewModel.pushAccount.getMqttAccountName());
        setResult(AppCompatActivity.RESULT_CANCELED); //TODO:
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_ACTIONS) {
            refreshActions(true);
        }
        mActivityStarted = false;
    }

    public final static String PARAM_MULTIPLE_PUSHSERVERS = "PARAM_MULTIPLE_PUSHSERVERS";

    private Snackbar mSnackbar;
    private RecyclerView mListView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView.AdapterDataObserver mAdapterObserver;
    private MessagesViewModel mViewModel;
    private ActionsViewModel mActionsViewModel;
    private boolean mActivityStarted;

    private final static int ACTION_ITEM_GROUP_ID = Menu.FIRST + 1;

    private final static String TAG = MessagesActivity.class.getSimpleName();
}
