package org.wordpress.android.ui.stats;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.VolleyError;

import org.wordpress.android.R;
import org.wordpress.android.ui.stats.model.AuthorsModel;
import org.wordpress.android.ui.stats.service.StatsService;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.StringUtils;

import java.io.Serializable;
import java.util.Locale;


public abstract class StatsAbstractListFragment extends StatsAbstractFragment {

    protected static final int NO_STRING_ID = -1;

    protected TextView mEmptyLabel;
    protected TextView mTotalsLabel;
    protected LinearLayout mListContainer;
    protected LinearLayout mList;
    protected Serializable mDatamodel;
    protected Button mViewAll;

    protected SparseBooleanArray mGroupIdToExpandedMap;

    protected abstract int getEntryLabelResId();
    protected abstract int getTotalsLabelResId();
    protected abstract int getEmptyLabelTitleResId();
    protected abstract int getEmptyLabelDescResId();
    protected abstract StatsService.StatsEndpointsEnum getSectionToUpdate();
    protected abstract void updateUI();
    protected abstract boolean isExpandableList();
    protected abstract boolean isViewAllOptionAvailable();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view;
        if (isExpandableList()) {
            view = inflater.inflate(R.layout.stats_expandable_list_fragment, container, false);
        } else {
            view = inflater.inflate(R.layout.stats_list_fragment, container, false);
        }

        TextView titleTextView = (TextView) view.findViewById(R.id.stats_pager_title);
        titleTextView.setText(getTitle().toUpperCase(Locale.getDefault()));

        TextView entryLabel = (TextView) view.findViewById(R.id.stats_list_entry_label);
        entryLabel.setText(getEntryLabelResId());
        TextView totalsLabel = (TextView) view.findViewById(R.id.stats_list_totals_label);
        totalsLabel.setText(getTotalsLabelResId());

        mEmptyLabel = (TextView) view.findViewById(R.id.stats_list_empty_text);
        mTotalsLabel = (TextView) view.findViewById(R.id.stats_module_totals_label);
        mList = (LinearLayout) view.findViewById(R.id.stats_list_linearlayout);
        mList.setVisibility(View.VISIBLE);
        mListContainer = (LinearLayout) view.findViewById(R.id.stats_list_container);
        mViewAll = (Button) view.findViewById(R.id.btnViewAll);

        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.d(AppLog.T.STATS, this.getTag() + " > onCreate");
        mGroupIdToExpandedMap = new SparseBooleanArray();

        if (savedInstanceState != null) {
            AppLog.d(AppLog.T.STATS, this.getTag() + " > restoring instance state");
            if (savedInstanceState.containsKey(ARG_REST_RESPONSE)) {
                mDatamodel = savedInstanceState.getSerializable(ARG_REST_RESPONSE);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        AppLog.d(AppLog.T.STATS, this.getTag() + " > saving instance state");
        outState.putSerializable(ARG_REST_RESPONSE, mDatamodel);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        AppLog.d(AppLog.T.STATS, this.getTag() + " > onPause");
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getActivity());
        lbm.unregisterReceiver(mReceiver);
    }

    @Override
    public void onResume() {
        AppLog.d(AppLog.T.STATS, this.getTag() + " > onResume");
        super.onResume();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getActivity());
        lbm.registerReceiver(mReceiver, new IntentFilter(StatsService.ACTION_STATS_UPDATED));
        lbm.registerReceiver(mReceiver, new IntentFilter(StatsService.ACTION_STATS_UPDATING));

        // Init the UI
        if (mDatamodel != null) {
            updateUI();
        } else {
            showEmptyUI(true);
        }
    }

    protected void showLoadingUI() {
        mEmptyLabel.setText("Loading...");
        mEmptyLabel.setVisibility(View.VISIBLE);
        mList.setVisibility(View.GONE);
        mListContainer.setVisibility(View.GONE);
        return;
    }

    protected void showEmptyUI(boolean show) {
        if (show) {
            mGroupIdToExpandedMap.clear();
            String label;
            if (getEmptyLabelDescResId() == NO_STRING_ID) {
                label = "<b>" + getString(getEmptyLabelTitleResId()) + "</b>";
            } else {
                label = "<b>" + getString(getEmptyLabelTitleResId()) + "</b> " + getString(getEmptyLabelDescResId());
            }
            if (label.contains("<")) {
                mEmptyLabel.setText(Html.fromHtml(label));
            } else {
                mEmptyLabel.setText(label);
            }
            mEmptyLabel.setVisibility(View.VISIBLE);
            mListContainer.setVisibility(View.GONE);
            mList.setVisibility(View.GONE);
        } else {
            mEmptyLabel.setVisibility(View.GONE);
            mListContainer.setVisibility(View.VISIBLE);
            mList.setVisibility(View.VISIBLE);
            if (!isSingleView() && isViewAllOptionAvailable()) {
                // No view all button if already in single view
                configureViewAllButton();
            } else {
                mViewAll.setVisibility(View.GONE);
            }
            //StatsUIHelper.reloadGroupViews(getActivity(), mAdapter, mGroupIdToExpandedMap, mList);
        }
    }

    private void configureViewAllButton() {
        if (isSingleView()) {
            // No view all button if you're already in single view
            mViewAll.setVisibility(View.GONE);
            return;
        }
        mViewAll.setVisibility(View.VISIBLE);
        mViewAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                lauchViewAllActivity(mDatamodel);
            }
        });
    }

    protected int getMaxNumberOfItemsToShowInList() {
        return isSingleView() ? 100 : 10;
    }

    /*
 * receives broadcast when data has been updated
 */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = StringUtils.notNullStr(intent.getAction());

            if (!(action.equals(StatsService.ACTION_STATS_UPDATED) || action.equals(StatsService.ACTION_STATS_UPDATING))) {
                return;
            }

            if (!intent.hasExtra(StatsService.EXTRA_ENDPOINT_NAME)) {
                return;
            }

            StatsService.StatsEndpointsEnum sectionToUpdate = (StatsService.StatsEndpointsEnum) intent.getSerializableExtra(StatsService.EXTRA_ENDPOINT_NAME);
            if (sectionToUpdate != getSectionToUpdate()) {
                return;
            }

            mGroupIdToExpandedMap.clear();
            if (action.equals(StatsService.ACTION_STATS_UPDATED)) {
                Serializable dataObj = intent.getSerializableExtra(StatsService.EXTRA_ENDPOINT_DATA);
               /* if (dataObj == null || dataObj instanceof VolleyError) {
                    //TODO: show the error on the section ???
                    return;
                }*/
                mDatamodel = (dataObj == null || dataObj instanceof VolleyError) ? null : dataObj;
                updateUI();
            } if (action.equals(StatsService.ACTION_STATS_UPDATING)) {
                showLoadingUI();
            }

            return;
        }
    };
}