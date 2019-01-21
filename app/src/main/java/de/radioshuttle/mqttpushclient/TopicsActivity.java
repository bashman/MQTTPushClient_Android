/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.DialogFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Connection;
import de.radioshuttle.net.Request;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.TopicsRequest;
import de.radioshuttle.utils.Utils;

import static de.radioshuttle.mqttpushclient.PushAccount.Topic.*;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class TopicsActivity extends AppCompatActivity
        implements TopicsRecyclerViewAdapter.RowSelectionListener,
        CertificateErrorDialog.Callback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topics);

        setTitle(getString(R.string.title_topics));

        mViewModel = ViewModelProviders.of(this).get(TopicsViewModel.class);
        boolean topicsLoaded = mViewModel.initialized;

        mViewModel.topicsRequest.observe(this, new Observer<Request>() {
            @Override
            public void onChanged(@Nullable Request request) {
                if (request != null && request instanceof TopicsRequest) {
                    TopicsRequest topicsRequest = (TopicsRequest) request;
                    PushAccount b = topicsRequest.getAccount();
                    if (b.status == 1) {
                        if (mTopicsRecyclerViewAdapter != null && mTopicsRecyclerViewAdapter.getItemCount() == 0) {
                            if (b.topics.size() > 0) {
                                // special case: configuration change and load in progress -> update view
                                mTopicsRecyclerViewAdapter.setData(b.topics);
                            }
                        }
                    } else {
                        if (mViewModel.isCurrentRequest(request)) {
                            mViewModel.confirmResultDelivered();
                            mSwipeRefreshLayout.setRefreshing(false);
                            invalidateOptionsMenu();

                            /* handle cerificate exception */
                            if (b.hasCertifiateException()) {
                                /* only show dialog if the certificate has not already been denied */
                                if (!AppTrustManager.isDenied(b.getCertificateException().chain[0])) {
                                    FragmentManager fm = getSupportFragmentManager();

                                    String DLG_TAG = CertificateErrorDialog.class.getSimpleName() + "_" +
                                            AppTrustManager.getUniqueKey(b.getCertificateException().chain[0]);

                                    /* check if a dialog is not already showing (for this certificate) */
                                    if (fm.findFragmentByTag(DLG_TAG) == null) {
                                        CertificateErrorDialog dialog = new CertificateErrorDialog();
                                        Bundle args = CertificateErrorDialog.createArgsFromEx(
                                                b.getCertificateException(), request.getAccount().pushserver);
                                        if (args != null) {
                                            int cmd = ((TopicsRequest) request).mCmd;
                                            if (cmd == Cmd.CMD_ADD_TOPICS || cmd == Cmd.CMD_UPD_TOPICS) {
                                                Cmd.Topic val;
                                                Iterator<Map.Entry<String, Cmd.Topic>> it = topicsRequest.mTopics.entrySet().iterator();
                                                if (it.hasNext()) {
                                                    Map.Entry<String, Cmd.Topic> e = it.next();
                                                    args.putString("topic_name", e.getKey());
                                                    val = e.getValue();
                                                    args.putInt("topic_prio", val.type);
                                                    args.putString("topic_script", val.script);
                                                }
                                            } else if (cmd == Cmd.CMD_DEL_TOPICS) {
                                                args.putStringArrayList("topics_del", new ArrayList<>(topicsRequest.mDelTopics));
                                            }
                                            args.putInt("cmd", cmd);

                                            dialog.setArguments(args);
                                            dialog.show(getSupportFragmentManager(), DLG_TAG);
                                        }
                                    }
                                }
                            } /* end dialog already showing */
                            b.setCertificateExeption(null); // mark es "processed"
                            /* end handle cerificate exception */

                            /* handle insecure connection */
                            if (b.inSecureConnectionAsk) {
                                if (Connection.mInsecureConnection.get(b.pushserver) == null) {
                                    FragmentManager fm = getSupportFragmentManager();

                                    String DLG_TAG = InsecureConnectionDialog.class.getSimpleName() + "_" + b.pushserver;

                                    /* check if a dialog is not already showing (for this host) */
                                    if (fm.findFragmentByTag(DLG_TAG) == null) {
                                        InsecureConnectionDialog dialog = new InsecureConnectionDialog();
                                        Bundle args = InsecureConnectionDialog.createArgsFromEx(b.pushserver);
                                        if (args != null) {
                                            dialog.setArguments(args);
                                            dialog.show(getSupportFragmentManager(), DLG_TAG);
                                        }
                                    }
                                }
                            }
                            b.inSecureConnectionAsk = false; // mark as "processed"

                        }
                        if (b.requestStatus != Cmd.RC_OK) {
                            String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                            if (b.requestStatus == Cmd.RC_MQTT_ERROR || (b.requestStatus == Cmd.RC_NOT_AUTHORIZED && b.requestErrorCode != 0)) {
                                t = TopicsActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                            }
                            showErrorMsg(t);
                        } else {
                            if (topicsRequest.requestStatus != Cmd.RC_OK) { // topics add or delete result
                                String t = (topicsRequest.requestErrorTxt == null ? "" : topicsRequest.requestErrorTxt);
                                if (topicsRequest.requestStatus == Cmd.RC_MQTT_ERROR) {
                                    t = TopicsActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                                }
                                showErrorMsg(t);
                            } else {
                                if (mSnackbar != null && mSnackbar.isShownOrQueued()) {
                                    mSnackbar.dismiss();
                                }
                            }
                        }
                        if (mTopicsRecyclerViewAdapter != null) {
                            mTopicsRecyclerViewAdapter.setData(b.topics);
                        }
                    }
                }
            }
        });

        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_ACCOUNT_JSON);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);

        try {
            mViewModel.init(json);
            TextView server = findViewById(R.id.push_notification_server);
            TextView key = findViewById(R.id.account_display_name);
            server.setText(mViewModel.pushAccount.pushserver);
            key.setText(mViewModel.pushAccount.getDisplayName());
            if (!hastMultipleServer) {
                server.setVisibility(View.GONE);
            }
        } catch (JSONException e) {
            Log.e(TAG, "parse error", e);
        }

        mListView = findViewById(R.id.topicsListView);
        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mListView.addItemDecoration(itemDecoration);
        mListView.setItemAnimator(null);
        mListView.setLayoutManager(new LinearLayoutManager(this));

        if (mViewModel.selectedTopics != null && mViewModel.selectedTopics.size() > 0) {
            mActionMode = startSupportActionMode(mActionModeCallback);
        }

        mTopicsRecyclerViewAdapter = new TopicsRecyclerViewAdapter(this, mViewModel.selectedTopics, new TopicsRecyclerViewAdapter.RowSelectionListener() {
            @Override
            public void onSelectionChange(int noOfSelectedItemsBefore, int noOfSelectedItems) {
                if (noOfSelectedItemsBefore == 0 && noOfSelectedItems > 0) {
                    mActionMode = startSupportActionMode(mActionModeCallback);
                } else if (noOfSelectedItemsBefore > 0 && noOfSelectedItems == 0) {
                    if (mActionMode != null)
                        mActionMode.finish();
                }
            }
        });
        mListView.setAdapter(mTopicsRecyclerViewAdapter);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        mSwipeRefreshLayout.setRefreshing(mViewModel.isRequestActive());

        if (!topicsLoaded) {
            refresh();
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

    protected void handleBackPressed() {
        setResult(AppCompatActivity.RESULT_CANCELED); //TODO:
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean handled;
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
                handled = true;
                break;
            case R.id.menu_refresh:
                refresh();
                handled = true;
                break;
            case R.id.action_add:
                showEditDialog(mViewModel.lastEnteredTopic, MODE_ADD,null, null, null);
                handled = true;
                break;
            default:
                handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }

    protected void showEditDialog(String topic, int mode, String errorMsg, Integer notificationType, String javaScriptSr) {
        if (mViewModel.isRequestActive()) {
            Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
        } else {
            Bundle topicArgs = getIntent().getExtras();
            EditTopicDlg dlg = new EditTopicDlg();
            Bundle args = new Bundle();
            args.putString(PARAM_ACCOUNT_JSON, topicArgs.getString(PARAM_ACCOUNT_JSON));
            args.putString(ARG_JAVASCRIPT, javaScriptSr);
            args.putInt(ARG_EDIT_MODE, mode);
            if (topic != null) {
                args.putString(ARG_TOPIC, topic);
            }
            if (errorMsg != null) {
                args.putString(ARG_TOPIC_ERROR, errorMsg);
            }
            if (notificationType != null) {
                args.putInt(ARG_TOPIC_NTYPE, notificationType);
            }

            dlg.setArguments(args);

            dlg.show(getSupportFragmentManager(), EditTopicDlg.class.getSimpleName());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_topics, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = menu.findItem(R.id.menu_refresh);
        if (m != null) {
            m.setEnabled(!mViewModel.isRequestActive());
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onSelectionChange(int noOfSelectedItemsBefore, int noOfSelectedItems) {
    }

    protected void refresh() {
        if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.getTopics(this);
        }
    }

    public void deleteTopics(List<String> topics) {
        if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.deleteTopics(this, topics);
        }
    }

    public void addTopic(String topic, int prio, String javaScriptSrc) {
        if (Utils.isEmpty(topic)) {
            showEditDialog(mViewModel.lastEnteredTopic,MODE_ADD, getString(R.string.error_empty_field), prio, javaScriptSrc);
        } else if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            PushAccount.Topic t = new PushAccount.Topic();
            t.name = topic;
            t.prio = prio;
            t.jsSrc = javaScriptSrc;
            mViewModel.addTopic(this, t);
        }
        if (!Utils.isEmpty(topic)) {
            mViewModel.lastEnteredTopic = topic;
        }
    }

    public void updateTopic(String topic, int prio, String javaScriptSrc) {
        if (Utils.isEmpty(topic)) {
            showEditDialog(mViewModel.lastEnteredTopic,MODE_EDIT, getString(R.string.error_empty_field), prio, javaScriptSrc);
        } else if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            PushAccount.Topic t = new PushAccount.Topic();
            t.name = topic;
            t.prio = prio;
            t.jsSrc = javaScriptSrc;
            mViewModel.updateTopic(this, t);
        }
        if (!Utils.isEmpty(topic)) {
            mViewModel.lastEnteredTopic = topic;
        }
    }

    protected void showErrorMsg(String msg) {
        View v = findViewById(R.id.rView);
        if (v != null) {
            mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.activity_topics_action, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            boolean handled = false;
            switch (item.getItemId()) {
                case R.id.action_delete_topics:
                    if (mViewModel.isRequestActive()) {
                        Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
                    } else {
                        ConfirmDeleteDlg dlg = new ConfirmDeleteDlg();
                        Bundle args = new Bundle();
                        args.putStringArrayList(SEL_ROWS, new ArrayList<String>(mViewModel.selectedTopics));
                        dlg.setArguments(args);
                        dlg.show(getSupportFragmentManager(), ConfirmDeleteDlg.class.getSimpleName());
                    }

                    handled = true;
                    break;
                case R.id.action_edit_topic:
                    if (mViewModel.selectedTopics.size() != 1) {
                        Toast.makeText(getApplicationContext(), R.string.select_single_topic, Toast.LENGTH_LONG).show();
                    } else {
                        if (mViewModel.isRequestActive()) {
                            Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
                        } else {
                            if (mViewModel.selectedTopics.size() == 1 && mViewModel != null) {
                                String t = mViewModel.selectedTopics.iterator().next();
                                TopicsRecyclerViewAdapter a = (TopicsRecyclerViewAdapter) mListView.getAdapter();
                                if (a != null) {
                                    ArrayList<PushAccount.Topic> list = a.getTopics();
                                    if (list != null) {
                                        for(PushAccount.Topic topic : list) {
                                            if (topic.name.equals(t)) {
                                                showEditDialog(topic.name, MODE_EDIT, null, topic.prio, topic.jsSrc);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    handled = true;
                    break;
            }

            return handled;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (mTopicsRecyclerViewAdapter != null)
                mTopicsRecyclerViewAdapter.clearSelection();
            mActionMode = null;
        }
    };

    @Override
    public void retry(Bundle args) {
        if (args != null) {
            int cmd = args.getInt("cmd", 0);
            if (cmd == 0) {
                refresh();
            } else if (cmd == Cmd.CMD_ADD_TOPICS || cmd == Cmd.CMD_UPD_TOPICS) {
                PushAccount.Topic t = new PushAccount.Topic();
                t.name = args.getString("topic_name");
                t.prio = args.getInt("topic_prio");
                t.jsSrc = args.getString("topic_script");

                if (!mViewModel.isRequestActive()) {
                    mSwipeRefreshLayout.setRefreshing(true);
                    if (cmd == Cmd.CMD_ADD_TOPICS) {
                        mViewModel.addTopic(getApplicationContext(), t);
                    } else {
                        mViewModel.updateTopic(getApplicationContext(), t);
                    }
                }

            } else if (cmd == Cmd.CMD_DEL_TOPICS && args.getStringArrayList("topics_del") != null) {
                deleteTopics(args.getStringArrayList("topics_del"));
            }
        }

    }

    public static class ConfirmDeleteDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = getArguments();
            final ArrayList<String> topics = args.getStringArrayList(SEL_ROWS);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_delete_topic_title));
            if (topics.size() == 1) {
                builder.setMessage(getString(R.string.dlg_delete_topic_msg));
            } else {
                builder.setMessage(getString(R.string.dlg_delete_topic_msg_pl));
            }

            builder.setPositiveButton(R.string.action_delete_topics, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Activity a = getActivity();
                    if (a instanceof TopicsActivity) {
                        ((TopicsActivity) a).deleteTopics(topics);
                    }
                }
            });

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            AlertDialog dlg = builder.create();
            dlg.setCanceledOnTouchOutside(false);

            return dlg;
        }
    }

    public static class EditTopicDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = getArguments();

            final int mode = args.getInt(ARG_EDIT_MODE);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            LayoutInflater inflater = (LayoutInflater) builder.getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
            View body = inflater.inflate(R.layout.dlg_topics_body, null);

            final Spinner s = body.findViewById(R.id.notificationtype_spinner);
            if (s != null) {
                s.setAdapter(new NotificationTypeAdapter(getContext()) {
                });
                s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        Map.Entry<Integer, String> sel =
                                (Map.Entry<Integer, String>) parent.getItemAtPosition(position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {

                    }
                });
            }

            builder.setView(body);
            EditText e = body.findViewById(R.id.topic);
            String errorMsg = args.getString(ARG_TOPIC_ERROR);
            String topic = args.getString(ARG_TOPIC);

            Integer notificationType = args.getInt(ARG_TOPIC_NTYPE, -1);
            if (notificationType != -1) {
                NotificationTypeAdapter a = (NotificationTypeAdapter) s.getAdapter();
                for (int i = 0; i < s.getAdapter().getCount(); i++) {
                    if (a.getItem(i).getKey() == notificationType.intValue()) {
                        s.setSelection(i);
                        break;
                    }
                }
            }

            if (mode == MODE_ADD) {
                builder.setTitle(getString(R.string.dlg_add_topic_title));
                e.setText(topic);
                if (!Utils.isEmpty(errorMsg)) {
                    e.setError(errorMsg);
                }

                builder.setPositiveButton(R.string.action_add, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Activity a = getActivity();
                        if (a instanceof TopicsActivity) {
                            EditText v = getDialog().findViewById(R.id.topic);
                            if (v != null) {
                                int prio = getSelectedNotificationType(s);
                                ((TopicsActivity) a).addTopic(v.getText().toString(), prio, mJavaScriptSrc);
                            }
                        }
                    }
                });
            } else { // mode == MODE_EDIT
                builder.setTitle(getString(R.string.dlg_update_topic_title));
                if (e != null) {
                    e.setVisibility(View.GONE);
                }
                TextView label = body.findViewById(R.id.topicLabel);
                label.setText(R.string.dlg_edit_topic_label2);

                TextView e2 = body.findViewById(R.id.topic2);
                e2.setVisibility(View.VISIBLE);
                e2.setText(topic);

                builder.setPositiveButton(R.string.action_update_topic, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Activity a = getActivity();
                        if (a instanceof TopicsActivity) {
                            int prio = getSelectedNotificationType(s);
                            ((TopicsActivity) a).updateTopic(args.getString(ARG_TOPIC), prio, mJavaScriptSrc);
                        }
                    }
                });
            }

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            mJavaScriptSrc = args.getString(ARG_JAVASCRIPT);

            // filter script button
            jsButton = body.findViewById(R.id.filterButton);
            // final String javaScriptSrc = null;
            if (jsButton != null) {
                setFilterButtonLabel();
                jsButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!mActivityStarted) {
                            mActivityStarted = true;
                            Bundle args = getArguments();
                            String topic = args.getString(ARG_TOPIC);

                            Intent intent = new Intent(getContext(), JavaScriptEditorActivity.class);
                            if (Utils.isEmpty(mJavaScriptSrc)) {
                                intent.putExtra(JavaScriptEditorActivity.ARG_TITLE, getString(R.string.title_add_javascript));
                            } else {
                                intent.putExtra(JavaScriptEditorActivity.ARG_TITLE, getString(R.string.title_edit_javascript));
                                intent.putExtra(JavaScriptEditorActivity.ARG_JAVASCRIPT, mJavaScriptSrc);
                            }

                            String header = getString(R.string.dlg_filter_header);
                            if (!Utils.isEmpty(topic)) {
                                intent.putExtra(JavaScriptEditorActivity.ARG_TOPIC, topic);
                                header += " " + topic;
                            }
                            header += ":";
                            intent.putExtra(JavaScriptEditorActivity.ARG_HEADER, header);
                            intent.putExtra(JavaScriptEditorActivity.ARG_JSPREFIX, "function filterMsg(msg) {\n var content = msg.content;");
                            intent.putExtra(JavaScriptEditorActivity.ARG_JSSUFFIX, " return content;\n}");
                            intent.putExtra(JavaScriptEditorActivity.ARG_COMPONENT, JavaScriptEditorActivity.CONTENT_FILTER);

                            String acc = args.getString(PARAM_ACCOUNT_JSON);
                            if (!Utils.isEmpty(acc)) {
                                intent.putExtra(JavaScriptEditorActivity.ARG_ACCOUNT, acc);
                            }
                            startActivityForResult(intent, 1);
                        }
                    }
                });
            }

            AlertDialog dlg = builder.create();
            dlg.setCanceledOnTouchOutside(false);

            return dlg;
        }

        protected int getSelectedNotificationType(Spinner s) {
            Object o = s.getSelectedItem();
            int prio = NOTIFICATION_HIGH; // default
            if (o != null && o instanceof Map.Entry<?, ?>) {
                Map.Entry<Integer, String> m = (Map.Entry<Integer, String>) s.getSelectedItem();
                Log.d(TAG, "selection: " + m.getKey() + " " + m.getValue());
                prio = m.getKey();
            }
            return prio;
        }

        protected void setFilterButtonLabel() {
            // Log.d(TAG, "updated js source: " + mJavaScriptSrc);
            String buttonLabel = jsButton.getText().toString();
            boolean emptyCode = Utils.isEmpty(mJavaScriptSrc);
            if (emptyCode && !buttonLabel.equals(getString(R.string.dlg_filter_button_add))) {
                jsButton.setText(R.string.dlg_filter_button_add);
            }
            if (!emptyCode && !buttonLabel.equals(getString(R.string.dlg_filter_button_edit))) {
                jsButton.setText(R.string.dlg_filter_button_edit);
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            mActivityStarted = false;
            // requestCode == 1 for JavaScriptEditor
            if (requestCode == 1 && resultCode == AppCompatActivity.RESULT_OK) {
                mJavaScriptSrc = data.getStringExtra(JavaScriptEditorActivity.ARG_JAVASCRIPT);
                setFilterButtonLabel();
            }
        }

        String mJavaScriptSrc;
        Button jsButton;
        boolean mActivityStarted;
    }



    private TopicsViewModel mViewModel;

    private ActionMode mActionMode;
    private Snackbar mSnackbar;
    private TopicsRecyclerViewAdapter mTopicsRecyclerViewAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mListView;

    private final static String SEL_ROWS = "SEL_ROWS";
    private final static String ARG_EDIT_MODE = "ARG_EDIT_MODE";
    private final static String ARG_TOPIC = "ARG_TOPIC";
    private final static String ARG_JAVASCRIPT = "ARG_JAVASCRIPT";
    private final static int MODE_ADD = 0;
    private final static int MODE_EDIT = 2;
    private final static String ARG_TOPIC_ERROR = "ARG_TOPIC_ERROR";
    private final static String ARG_TOPIC_NTYPE = "ARG_NOTIFICATION_TYPE";

    private final static String TAG = TopicsActivity.class.getSimpleName();

}
