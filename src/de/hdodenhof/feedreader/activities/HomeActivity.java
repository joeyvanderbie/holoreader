package de.hdodenhof.feedreader.activities;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.HeaderViewListAdapter;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import de.hdodenhof.feedreader.R;
import de.hdodenhof.feedreader.fragments.ArticleListFragment;
import de.hdodenhof.feedreader.fragments.FeedListFragment;
import de.hdodenhof.feedreader.listadapters.RSSAdapter;
import de.hdodenhof.feedreader.listadapters.RSSArticleAdapter;
import de.hdodenhof.feedreader.listadapters.RSSFeedAdapter;
import de.hdodenhof.feedreader.misc.FragmentCallback;
import de.hdodenhof.feedreader.misc.Helpers;
import de.hdodenhof.feedreader.provider.RSSContentProvider;
import de.hdodenhof.feedreader.provider.SQLiteHelper.ArticleDAO;
import de.hdodenhof.feedreader.provider.SQLiteHelper.FeedDAO;
import de.hdodenhof.feedreader.tasks.AddFeedTask;
import de.hdodenhof.feedreader.tasks.RefreshFeedTask;

/**
 * 
 * @author Henning Dodenhof
 * 
 */
public class HomeActivity extends SherlockFragmentActivity implements FragmentCallback, OnItemClickListener {

    @SuppressWarnings("unused")
    private static final String TAG = HomeActivity.class.getSimpleName();
    private static final String PREFS_NAME = "Feedreader";

    private boolean mTwoPane = false;
    private boolean mUnreadOnly;
    private ProgressDialog mSpinner;
    private ArticleListFragment mArticleListFragment;
    private FeedListFragment mFeedListFragment;
    private SharedPreferences mPreferences;
    private HashSet<Integer> mFeedsUpdating;
    private MenuItem mRefreshItem;
    private Resources mResources;

    /**
     * Handles messages from AsyncTasks started within this activity
     */
    Handler mAsyncHandler = new AsynHandler(this);

    private static class AsynHandler extends Handler {
        private final WeakReference<HomeActivity> mTargetReference;

        AsynHandler(HomeActivity target) {
            mTargetReference = new WeakReference<HomeActivity>(target);
        }

        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            HomeActivity mTarget = mTargetReference.get();

            switch (msg.what) {
            case 1:
                // added feed
                mTarget.callbackFeedAdded(msg.arg1);
                break;
            case 2:
                // feed refresh
                mTarget.callbackFeedRefresh(msg.arg1);
                break;
            case 9:
                // something went wrong while adding a feed
                mTarget.callbackError();
                break;
            default:
                break;
            }
        }
    };

    /**
     * Update feed list and dismiss spinner after new feed has been added
     */
    private void callbackFeedAdded(int feedID) {
        mSpinner.dismiss();
        refreshFeed(feedID);
    }

    /**
     * Update list of running tasks and dismiss spinner when all tasks are done
     */
    @SuppressLint("NewApi")
    private void callbackFeedRefresh(int feedID) {
        mFeedsUpdating.remove(feedID);
        if (mFeedsUpdating.size() == 0) {
            mRefreshItem.getActionView().clearAnimation();
            mRefreshItem.setActionView(null);
        }
    }

    private void callbackError() {
        mSpinner.dismiss();
        showDialog("An error occured", "Something went wrong while adding the feed");
    }

    /**
     * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {

        }

        setContentView(R.layout.activity_home);

        mResources = getResources();

        mFeedListFragment = (FeedListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_feedlist);
        mArticleListFragment = (ArticleListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_articlelist);
        if (mArticleListFragment != null) {
            mTwoPane = true;
        }

        mPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mUnreadOnly = mPreferences.getBoolean("unreadonly", true);

        mFeedsUpdating = new HashSet<Integer>();
    }

    /**
     * @see android.support.v4.app.FragmentActivity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        mUnreadOnly = mPreferences.getBoolean("unreadonly", true);
        invalidateOptionsMenu();

        mFeedListFragment.setUnreadOnly(mUnreadOnly);
        if (mTwoPane) {
            mArticleListFragment.setUnreadOnly(mUnreadOnly);
        }
    }

    /**
     * @see de.hdodenhof.feedreader.misc.FragmentCallback#onFragmentReady(android.support.v4.app.Fragment)
     */
    public void onFragmentReady(Fragment fragment) {
        if (mTwoPane && fragment instanceof FeedListFragment) {
            ((FeedListFragment) fragment).setChoiceModeSingle();
        }
    }

    /**
     * @see de.hdodenhof.feedreader.misc.FragmentCallback#isDualPane()
     */
    public boolean isDualPane() {
        return mTwoPane;
    }

    /**
     * @see de.hdodenhof.feedreader.misc.FragmentCallback#isPrimaryFragment(android.support.v4.app.Fragment)
     */
    public boolean isPrimaryFragment(Fragment fragment) {
        return fragment instanceof FeedListFragment;
    }

    /**
     * Starts an AsyncTask to fetch a new feed and add it to the database
     * 
     * @param feedUrl
     *            URL of the feed to fetch
     */
    private void addFeed(String feedUrl) {
        mSpinner = ProgressDialog.show(this, "", mResources.getString(R.string.PleaseWait), true);
        AddFeedTask mAddFeedTask = new AddFeedTask(mAsyncHandler, this);
        mAddFeedTask.execute(feedUrl);
    }

    /**
     * Spawns AsyncTasks to refresh all feeds
     * 
     * @param item
     *            MenuItem that holds the refresh animation
     */
    private void refreshFeeds() {
        boolean mIsConnected = Helpers.isConnected(this);

        if (mIsConnected) {
            for (Integer mFeedID : queryFeeds()) {
                refreshFeed(mFeedID);
            }
        } else {
            showDialog(mResources.getString(R.string.NoConnectionTitle), mResources.getString(R.string.NoConnectionText));
        }
    }

    /**
     * 
     * @param feedID
     */
    @SuppressLint("NewApi")
    private void refreshFeed(int feedID) {
        if (mFeedsUpdating.size() == 0) {
            mRefreshItem.setActionView(R.layout.actionview_refresh);
        }
        if (mFeedsUpdating.contains(feedID)) {
            return;
        }
        mFeedsUpdating.add(feedID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            new RefreshFeedTask(mAsyncHandler, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, feedID);
        } else {
            new RefreshFeedTask(mAsyncHandler, this).execute(feedID);
        }
    }

    /**
     * Queries all feed ids
     * 
     * @return All feed ids in a HashMap
     */
    private HashSet<Integer> queryFeeds() {
        HashSet<Integer> mFeedsUpdating = new HashSet<Integer>();

        ContentResolver mContentResolver = getContentResolver();
        Cursor mCursor = mContentResolver.query(RSSContentProvider.URI_FEEDS, new String[] { FeedDAO._ID, FeedDAO.NAME, FeedDAO.URL }, null, null, null);

        if (mCursor.getCount() > 0) {
            mCursor.moveToFirst();
            do {
                mFeedsUpdating.add(mCursor.getInt(mCursor.getColumnIndex(FeedDAO._ID)));
            } while (mCursor.moveToNext());
        }
        mCursor.close();

        return mFeedsUpdating;
    }

    /**
     * Shows a simple dialog
     * 
     * @param title
     *            Dialog title
     * @param message
     *            Dialog message
     */
    private void showDialog(String title, String message) {
        AlertDialog.Builder mAlertDialog = new AlertDialog.Builder(this);
        mAlertDialog.setTitle(title);
        mAlertDialog.setMessage(message);
        mAlertDialog.setPositiveButton(mResources.getString(R.string.PositiveButton), null);
        mAlertDialog.show();
    }

    /**
     * Shows a dialog to add a new feed URL
     */
    private void showAddDialog() {
        boolean mIsConnected = Helpers.isConnected(this);

        if (mIsConnected) {
            AlertDialog.Builder mAlertDialog = new AlertDialog.Builder(this);

            mAlertDialog.setTitle(mResources.getString(R.string.AddFeedDialogTitle));
            mAlertDialog.setMessage(mResources.getString(R.string.AddFeedDialogText));

            final EditText mInput = new EditText(this);
            mInput.setText("http://t3n.de/news/feed");
            mAlertDialog.setView(mInput);

            mAlertDialog.setPositiveButton(mResources.getString(R.string.PositiveButton), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String value = mInput.getText().toString();
                    addFeed(value);
                }
            });

            mAlertDialog.setNegativeButton(mResources.getString(R.string.NegativeButton), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            });
            mAlertDialog.show();
        } else {
            showDialog(mResources.getString(R.string.NoConnectionTitle), mResources.getString(R.string.NoConnectionText));
        }
    }

    /**
     * Updates all fragments or launches a new activity (depending on the activities current layout) whenever a feed or article in one of the fragments has been
     * clicked on
     * 
     * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView, android.view.View, int, long)
     */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        RSSAdapter mAdapter;
        if (parent.getAdapter() instanceof HeaderViewListAdapter) {
            HeaderViewListAdapter mWrapperAdapter = (HeaderViewListAdapter) parent.getAdapter();
            mAdapter = (RSSAdapter) mWrapperAdapter.getWrappedAdapter();
        } else {
            mAdapter = (RSSAdapter) parent.getAdapter();
        }

        Cursor mCursor;
        int mFeedID;

        switch (mAdapter.getType()) {
        case RSSAdapter.TYPE_FEED:
            if (position == 0) {
                mFeedID = -1;
            } else {
                mCursor = ((RSSFeedAdapter) mAdapter).getCursor();
                mCursor.moveToPosition(position - 1);
                mFeedID = mCursor.getInt(mCursor.getColumnIndex(FeedDAO._ID));
            }

            if (mTwoPane) {
                mArticleListFragment.selectFeed(mFeedID);
            } else {
                Intent mIntent = new Intent(this, DisplayFeedActivity.class);
                mIntent.putExtra("feedid", mFeedID);
                startActivity(mIntent);
            }

            break;

        // DualPane layout only
        case RSSAdapter.TYPE_ARTICLE:
            mCursor = ((RSSArticleAdapter) mAdapter).getCursor();
            mCursor.moveToPosition(position);

            int mArticleID = mCursor.getInt(mCursor.getColumnIndex(ArticleDAO._ID));

            ArrayList<String> mArticles = new ArrayList<String>();

            mCursor.moveToFirst();
            do {
                mArticles.add(mCursor.getString(mCursor.getColumnIndex(ArticleDAO._ID)));
            } while (mCursor.moveToNext());

            Intent mIntent = new Intent(this, DisplayFeedActivity.class);
            mIntent.putExtra("articleid", mArticleID);
            mIntent.putStringArrayListExtra("articles", mArticles);
            startActivity(mIntent);
            break;

        default:
            break;
        }
    }

    /**
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater mMenuInflater = getSupportMenuInflater();
        mMenuInflater.inflate(R.menu.main, menu);

        if (!mUnreadOnly) {
            menu.getItem(2).setIcon(R.drawable.checkbox_checked);
        }
        mRefreshItem = menu.getItem(0);

        return true;
    }

    /**
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.item_refresh:
            refreshFeeds();
            return true;
        case R.id.item_add:
            showAddDialog();
            return true;
        case R.id.item_toggle:
            mUnreadOnly = !mUnreadOnly;

            SharedPreferences.Editor mEditor = mPreferences.edit();
            mEditor.putBoolean("unreadonly", mUnreadOnly);
            mEditor.commit();

            if (mUnreadOnly) {
                Toast.makeText(this, mResources.getString(R.string.ToastUnreadArticles), Toast.LENGTH_SHORT).show();
                item.setIcon(R.drawable.checkbox_unchecked);
            } else {
                Toast.makeText(this, mResources.getString(R.string.ToastAllArticles), Toast.LENGTH_SHORT).show();
                item.setIcon(R.drawable.checkbox_checked);
            }
            mFeedListFragment.setUnreadOnly(mUnreadOnly);
            if (mTwoPane) {
                mArticleListFragment.setUnreadOnly(mUnreadOnly);
            }
            return true;
        case R.id.item_editfeeds:
            Intent mIntent = new Intent(this, EditFeedsActivity.class);
            startActivity(mIntent);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

}