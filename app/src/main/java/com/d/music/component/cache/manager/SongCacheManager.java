package com.d.music.component.cache.manager;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.d.music.component.cache.base.AbstractCacheManager;
import com.d.music.component.cache.base.ExpireQueue;
import com.d.music.component.cache.listener.CacheListener;
import com.d.music.component.cache.utils.threadpool.ThreadPool;
import com.d.music.component.media.HitTarget;
import com.d.music.data.database.greendao.bean.MusicModel;
import com.d.music.transfer.manager.Transfer;
import com.d.music.util.FileUtils;

/**
 * Created by D on 2017/10/18.
 */
public class SongCacheManager extends AbstractCacheManager<MusicModel, String> {
    private volatile static SongCacheManager mInstance;

    private ExpireQueue<Bean> mExpireQueue;

    private SongCacheManager(Context context) {
        super(context);
        mLruCache.setCount(0);
        mExpireQueue = new ExpireQueue<>(1, 2);
        mExpireQueue.setOnExpireListener(new ExpireQueue.OnExpireListener<Bean>() {
            @Override
            public void onExpire(Bean value) {
                Log.e("Cache", "Expire");
                error(value.key, new Exception("Expire!"), value.listener);
            }
        });
    }

    public static SongCacheManager getInstance(Context context) {
        if (mInstance == null) {
            synchronized (SongCacheManager.class) {
                if (mInstance == null) {
                    mInstance = new SongCacheManager(context);
                }
            }
        }
        return mInstance;
    }

    @NonNull
    @Override
    protected String getPreFix() {
        return "";
    }

    public void load(final Context context, final MusicModel key, final CacheListener<String> listener) {
        if (isLoading(key, listener)) {
            return;
        }
        if (isLru(key, listener)) {
            return;
        }
        mExpireQueue.add(new Bean(key, listener));
        if (mExpireQueue.isFullLoad()) {
            return;
        }
        final Bean bean = mExpireQueue.take();
        if (bean == null) {
            return;
        }
        absLoad(bean);
    }

    private void absLoad(@NonNull final Bean item) {
        ThreadPool.getInstance().executeTask(new Runnable() {
            @Override
            public void run() {
                if (isDisk(item.key, item.listener)) {
                    next(item);
                    return;
                }

                Transfer.downloadSongCache(item.key, new Transfer.OnTransferCallback<MusicModel>() {
                    @Override
                    public void onFirst(MusicModel model) {

                    }

                    @Override
                    public void onSecond(MusicModel model) {
                        final String path = HitTarget.hitSong(model);
                        putDisk(item.key, path);
                        success(item.key, path, item.listener);
                        next(item);
                    }

                    @Override
                    public void onError(MusicModel model, Throwable e) {
                        Log.e("Cache", e.toString());
                        e.printStackTrace();
                        error(item.key, e, item.listener);
                        next(item);
                    }
                });
            }
        });
    }

    private void next(final Bean item) {
        ThreadPool.getInstance().executeMain(new Runnable() {
            @Override
            public void run() {
                mExpireQueue.remove(item);
                Bean bean = mExpireQueue.take();
                if (bean == null) {
                    return;
                }
                absLoad(bean);
            }
        });
    }

    @Override
    protected void absLoad(Context context, MusicModel model, CacheListener<String> listener) {

    }

    @Override
    protected boolean isLru(MusicModel key, CacheListener<String> listener) {
        final String path = HitTarget.hitSong(key);
        if (!TextUtils.isEmpty(path) && FileUtils.isFileExist(path)) {
            success(key, path, listener);
            return true;
        }
        return false;
    }

    @Override
    protected void putLru(MusicModel key, String value) {

    }

    @Override
    protected String getDisk(MusicModel key) {
        return null;
    }

    @Override
    protected void putDisk(MusicModel key, String value) {

    }

    static class Bean {
        final MusicModel key;
        final CacheListener<String> listener;

        Bean(MusicModel key, CacheListener<String> listener) {
            this.key = key;
            this.listener = listener;
        }
    }
}
