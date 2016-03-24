package com.wm.remusic.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wm.remusic.uitl.IConstants;
import com.wm.remusic.info.MusicInfo;
import com.wm.remusic.provider.PlaylistsManager;
import com.wm.remusic.R;
import com.wm.remusic.dialog.AddPlaylistDialog;
import com.wm.remusic.fragment.DividerItemDecoration;
import com.wm.remusic.service.MediaService;
import com.wm.remusic.service.MusicPlayer;
import com.wm.remusic.uitl.DragSortRecycler;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by wm on 2016/3/12.
 */
public class PlaylistSelectActivity extends AppCompatActivity implements View.OnClickListener {

    ArrayList<MusicInfo> arrayList;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    SelectAdapter mAdapter;
    private Toolbar toolbar;
    private PlaylistsManager pManager;
    private long playlistId;
    ActionBar ab;
    private LinearLayout l1, l2, l3;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select);
        pManager = PlaylistsManager.getInstance(this);

        l1 = (LinearLayout) findViewById(R.id.select_next);
        l2 = (LinearLayout) findViewById(R.id.select_addtoplaylist);
        l3 = (LinearLayout) findViewById(R.id.select_del);
        l1.setOnClickListener(this);
        l2.setOnClickListener(this);
        l3.setOnClickListener(this);

        recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);


        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ab = getSupportActionBar();
        ab.setHomeAsUpIndicator(R.drawable.actionbar_back);
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setTitle("已选择0项");
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        new loadSongs().execute("");

    }


    @Override
    public void onClick(View v) {
        final ArrayList<MusicInfo> selectList = mAdapter.getSelectedItem();
        switch (v.getId()) {

            case R.id.select_next:
                long[] list = new long[selectList.size()];
                for (int i = 0; i < mAdapter.getSelectedItem().size(); i++) {
                    list[i] = selectList.get(i).songId;
                }
                MusicPlayer.playNext(this, list, -1);
                break;
            case R.id.select_addtoplaylist:
                long[] list1 = new long[selectList.size()];
                for (int i = 0; i < mAdapter.getSelectedItem().size(); i++) {
                    list1[i] = selectList.get(i).songId;
                }
                AddPlaylistDialog.newInstance(list1).show(getSupportFragmentManager(), "add");
                Intent intent = new Intent(MediaService.PLAYLIST_CHANGED);
                sendBroadcast(intent);

                break;
            case R.id.select_del:
                new AlertDialog.Builder(this).setTitle("确定删除歌曲吗").
                        setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                File file;

                                for (int i = 0; i < selectList.size(); i++) {
                                    String path = selectList.get(i).data;
                                    file = new File(path);
                                    if (file.exists())
                                        file.delete();
                                    if (file.exists() == false) {
                                        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                                Uri.parse("file://" + path)));
                                        sendBroadcast(new Intent(MediaService.PLAYLIST_CHANGED));
                                    }
                                }
                                dialog.dismiss();
                            }
                        }).
                        setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();

                break;
        }
    }


    @Override
    public void onStop() {
        super.onStop();
        Intent intent = new Intent();
        intent.setAction(IConstants.PLAYLIST_ITEM_MOVED);
        PlaylistSelectActivity.this.sendBroadcast(intent);
        finish();
    }


    //异步加载recyclerview界面
    private class loadSongs extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            if (getIntent().getParcelableArrayListExtra("ids") != null) {
                arrayList = getIntent().getParcelableArrayListExtra("ids");
                playlistId = getIntent().getLongExtra("playlistid", -1);

            }
            mAdapter = new SelectAdapter(arrayList);
            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {
            recyclerView.setAdapter(mAdapter);
            recyclerView.addItemDecoration(new DividerItemDecoration(PlaylistSelectActivity.this, DividerItemDecoration.VERTICAL_LIST));
            recyclerView.setAdapter(mAdapter);
            DragSortRecycler dragSortRecycler = new DragSortRecycler();
            dragSortRecycler.setViewHandleId(R.id.select_move);

            dragSortRecycler.setOnItemMovedListener(new DragSortRecycler.OnItemMovedListener() {
                @Override
                public void onItemMoved(int from, int to) {
                    Log.d("queue", "onItemMoved " + from + " to " + to);
                    MusicInfo musicInfo = mAdapter.getMusicAt(from);
                    boolean f = mAdapter.isItemChecked(from);
                    boolean t = mAdapter.isItemChecked(to);
                    mAdapter.removeSongAt(from);
                    mAdapter.setItemChecked(from, t);
                    mAdapter.addSongTo(to, musicInfo);
                    mAdapter.setItemChecked(to, f);
                    mAdapter.notifyDataSetChanged();

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            pManager.delete(playlistId);
                            for (int i = 0; i < mAdapter.mList.size(); i++) {
                                pManager.Insert(PlaylistSelectActivity.this, playlistId, mAdapter.mList.get(i).songId, i);

                            }

                        }
                    }, 100);

                    //MusicPlayer.moveQueueItem(from, to);
                    Intent intent = new Intent();
                    intent.setAction(IConstants.PLAYLIST_ITEM_MOVED);
                    PlaylistSelectActivity.this.sendBroadcast(intent);

                }
            });

            recyclerView.addItemDecoration(dragSortRecycler);
            recyclerView.addOnItemTouchListener(dragSortRecycler);
            recyclerView.addOnScrollListener(dragSortRecycler.getScrollListener());

            //recyclerView.getLayoutManager().scrollToPosition(mAdapter.currentlyPlayingPosition);

        }

        @Override
        protected void onPreExecute() {

        }
    }


    public class SelectAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private ArrayList<MusicInfo> mList;
        ArrayList selected;

        public SelectAdapter(ArrayList<MusicInfo> list) {
            if (list == null) {
                throw new IllegalArgumentException("model Data must not be null");
            }
            mList = list;
        }


        public ArrayList<MusicInfo> getSelectedItem() {


            ArrayList<MusicInfo> selectList = new ArrayList<>();
            for (int i = 0; i < mList.size(); i++) {
                if (isItemChecked(i)) {
                    selectList.add(mList.get(i));
                }
            }
            return selectList;
        }


        //更新adpter的数据
        public void updateDataSet(ArrayList<MusicInfo> list) {
            this.mList = list;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {

            View itemView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.playlist_select_item, viewGroup, false);
            return new ListItemViewHolder(itemView);
        }

        private SparseBooleanArray mSelectedPositions = new SparseBooleanArray();
        private boolean mIsSelectable = false;

        private void setItemChecked(int position, boolean isChecked) {
            mSelectedPositions.put(position, isChecked);
        }

        private boolean isItemChecked(int position) {
            return mSelectedPositions.get(position);
        }

        private void setSelectable(boolean selectable) {
            mIsSelectable = selectable;
        }

        private boolean isSelectable() {
            return mIsSelectable;
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int i) {
            MusicInfo model = mList.get(i);
            //设置条目状态
            ((ListItemViewHolder) holder).mainTitle.setText(model.musicName);
            ((ListItemViewHolder) holder).title.setText(model.artist);
            ((ListItemViewHolder) holder).checkBox.setChecked(isItemChecked(i));
            ((ListItemViewHolder) holder).checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isItemChecked(i)) {
                        setItemChecked(i, false);
                    } else {
                        setItemChecked(i, true);
                    }
                    ab.setTitle("已选择" + getSelectedItem().size() + "项");
                }
            });
            ((ListItemViewHolder) holder).itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isItemChecked(i)) {
                        setItemChecked(i, false);
                    } else {
                        setItemChecked(i, true);
                    }
                    notifyItemChanged(i);
                    ab.setTitle("已选择" + getSelectedItem().size() + "项");
                }
            });


        }

        public MusicInfo getMusicAt(int i) {
            return mList.get(i);
        }

        public void addSongTo(int i, MusicInfo musicInfo) {
            mList.add(i, musicInfo);
        }

        public void removeSongAt(int i) {
            mList.remove(i);
        }

        @Override
        public int getItemCount() {
            return mList == null ? 0 : mList.size();
        }

        public class ListItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            //ViewHolder
            CheckBox checkBox;
            TextView mainTitle, title;
            ImageView move;

            ListItemViewHolder(View view) {
                super(view);
                this.mainTitle = (TextView) view.findViewById(R.id.select_title_main);
                this.title = (TextView) view.findViewById(R.id.select_title_small);
                this.checkBox = (CheckBox) view.findViewById(R.id.select_checkbox);
                this.move = (ImageView) view.findViewById(R.id.select_move);

                //为每个条目设置监听
                view.setOnClickListener(this);

            }


            @Override
            public void onClick(View v) {

            }

        }
    }

}