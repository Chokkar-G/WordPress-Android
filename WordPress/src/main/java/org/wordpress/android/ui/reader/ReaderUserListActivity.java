package org.wordpress.android.ui.reader;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.ListView;

import org.wordpress.android.R;
import org.wordpress.android.datasets.ReaderCommentTable;
import org.wordpress.android.datasets.ReaderPostTable;
import org.wordpress.android.datasets.ReaderUserTable;
import org.wordpress.android.models.ReaderUserList;
import org.wordpress.android.ui.reader.adapters.ReaderUserAdapter;
import org.wordpress.android.ui.reader.utils.ReaderUtils;

/*
 * displays a list of users who like a specific reader post
 */
public class ReaderUserListActivity extends ActionBarActivity {
    private static final String LIST_STATE = "list_state";
    private Parcelable mListState = null;
    private ListView mListView;

    private ListView getListView() {
        if (mListView == null) {
            mListView = (ListView) findViewById(android.R.id.list);
        }
        return mListView;
    }

    private ReaderUserAdapter mAdapter;
    private ReaderUserAdapter getAdapter() {
        if (mAdapter == null) {
            mAdapter = new ReaderUserAdapter(this, mDataLoadedListener);
        }
        return mAdapter;
    }

    /*
     * called by adapter when data has been loaded
     */
    private final ReaderInterfaces.DataLoadedListener mDataLoadedListener = new ReaderInterfaces.DataLoadedListener() {
        @Override
        public void onDataLoaded(boolean isEmpty) {
            // restore listView state so user returns to the previously scrolled-to item
            if (!isEmpty && mListState != null) {
                getListView().onRestoreInstanceState(mListState);
                mListState = null;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.reader_activity_userlist);
        setTitle(null);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        long blogId = getIntent().getLongExtra(ReaderConstants.ARG_BLOG_ID, 0);
        long postId = getIntent().getLongExtra(ReaderConstants.ARG_POST_ID, 0);
        long commentId = getIntent().getLongExtra(ReaderConstants.ARG_COMMENT_ID, 0);

        if (savedInstanceState != null) {
            mListState = savedInstanceState.getParcelable(LIST_STATE);
        }

        getListView().setAdapter(getAdapter());
        loadUsers(blogId, postId, commentId);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (getListView().getFirstVisiblePosition() > 0) {
            outState.putParcelable(LIST_STATE, getListView().onSaveInstanceState());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void loadUsers(final long blogId,
                           final long postId,
                           final long commentId) {
        new Thread() {
            @Override
            public void run() {
                final String title = getTitleString(blogId, postId, commentId);

                final ReaderUserList users;
                if (commentId == 0) {
                    // commentId is empty (not passed), so we're showing users who like a post
                    users = ReaderUserTable.getUsersWhoLikePost(
                            blogId,
                            postId,
                            ReaderConstants.READER_MAX_USERS_TO_DISPLAY);
                } else {
                    // commentId is non-empty, so we're showing users who like a comment
                    users = ReaderUserTable.getUsersWhoLikeComment(
                            blogId,
                            commentId,
                            ReaderConstants.READER_MAX_USERS_TO_DISPLAY);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            setTitle(title);
                            getAdapter().setUsers(users);
                        }
                    }
                });
            }
        }.start();
    }

    private String getTitleString(final long blogId,
                                  final long postId,
                                  final long commentId) {
        final int numLikes;
        final boolean isLikedByCurrentUser;
        if (commentId == 0) {
            numLikes = ReaderPostTable.getNumLikesForPost(blogId, postId);
            isLikedByCurrentUser = ReaderPostTable.isPostLikedByCurrentUser(blogId, postId);
        } else {
            numLikes = ReaderCommentTable.getNumLikesForComment(blogId, postId, commentId);
            isLikedByCurrentUser = ReaderCommentTable.isCommentLikedByCurrentUser(blogId, postId, commentId);
        }
        return ReaderUtils.getLongLikeLabelText(this, numLikes, isLikedByCurrentUser);
    }

}
