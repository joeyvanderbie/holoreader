package de.hdodenhof.feedreader.fragments;

import java.util.ArrayList;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import de.hdodenhof.feedreader.adapters.RSSArticleAdapter;
import de.hdodenhof.feedreader.listeners.OnFragmentReadyListener;
import de.hdodenhof.feedreader.misc.RSSMessage;
import de.hdodenhof.feedreader.models.Article;
import de.hdodenhof.feedreader.models.Feed;

/**
 * 
 * @author Henning Dodenhof
 *
 */
public class ArticleListFragment extends ListFragment implements RSSFragment {

        @SuppressWarnings("unused")
        private static final String TAG = ArticleListFragment.class.getSimpleName();

        private ListView mArticlesListView;
        private RSSArticleAdapter mArticleAdapter;
        private boolean mInitialized = false;;

        private void refreshAdapter(ArrayList<Article> articles) {
                mArticleAdapter.clear();
                for (Article mArticle : articles) {
                        mArticleAdapter.add(mArticle);
                }
                mArticleAdapter.notifyDataSetChanged();
        }

        public void handleMessage(RSSMessage message) {
                ArrayList<Article> mArticleList;

                switch (message.type) {
                case RSSMessage.INITIALIZE:
                        mArticleList = new ArrayList<Article>();

                        int mPos = 0;
                        int mCurrent = 0;

                        for (Feed mFeed : message.feeds) {
                                for (Article mArticle : mFeed.getArticles()) {
                                        mArticleList.add(mArticle);
                                        if (message.article != null && mArticle.getId() == message.article.getId()) {
                                                mCurrent = mPos;
                                        }
                                        mPos++;
                                }
                        }
                        refreshAdapter(mArticleList);
                        if (message.article != null) {
                                mArticlesListView.setItemChecked(mCurrent, true);
                                int mPosition = (mCurrent - 1 < 0) ? 0 : mCurrent - 1;
                                mArticlesListView.setSelectionFromTop(mPosition, 0);                                  
                        }

                        break;
                case RSSMessage.FEEDLIST_UPDATED:
                        mArticleList = new ArrayList<Article>();

                        for (Feed mFeed : message.feeds) {
                                for (Article mArticle : mFeed.getArticles()) {
                                        mArticleList.add(mArticle);
                                }
                        }

                        refreshAdapter(mArticleList);
                        break;
                case RSSMessage.FEED_SELECTED:
                        mArticleList = new ArrayList<Article>();

                        for (Article mArticle : message.feed.getArticles()) {
                                mArticleList.add(mArticle);
                        }

                        refreshAdapter(mArticleList);
                        break;
                case RSSMessage.POSITION_CHANGED:
                        if (mInitialized) {
                                mArticlesListView.setItemChecked(message.position, true);
                                int mPosition = (message.position - 1 < 0) ? 0 : message.position - 1;
                                mArticlesListView.setSelectionFromTop(mPosition, 0);  
                        }
                        break;
                case RSSMessage.CHOICE_MODE_SINGLE:
                        mArticlesListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                        break;
                default:
                        break;
                }
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
                super.onActivityCreated(savedInstanceState);

                if (savedInstanceState != null) {

                }

                mArticleAdapter = new RSSArticleAdapter(getActivity(), new ArrayList<Article>());

                this.setEmptyText("No articles");
                this.setListAdapter(mArticleAdapter);
                mArticlesListView = getListView();

                mArticlesListView.setOnItemClickListener((OnItemClickListener) getActivity());

                mInitialized = true;
                ((OnFragmentReadyListener) getActivity()).onFragmentReady(this);

        }

}