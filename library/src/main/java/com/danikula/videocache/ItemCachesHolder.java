package com.danikula.videocache;

import android.text.TextUtils;

import com.danikula.videocache.file.FileCache;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ItemCachesHolder {
    public static ItemCachesHolder sInstance;
    private final Map<String, FileCache> fileCacheMap = new ConcurrentHashMap<>();


    public static synchronized ItemCachesHolder getInstance() {
        if (sInstance == null) {
            sInstance = new ItemCachesHolder();
        }
        return sInstance;
    }


    public FileCache getFileCache(String id, String url, Config config) throws ProxyCacheException {
        synchronized (fileCacheMap) {
            if (fileCacheMap.containsKey(id)) {
                return fileCacheMap.get(id);
            } else {
                FileCache fileCache = new FileCache(config.generateCacheFile(id, url), config.diskUsage);
                fileCacheMap.put(id, fileCache);
                return fileCache;
            }
        }
    }

    public void removeFileCache(String id)  {
        synchronized (fileCacheMap) {
            FileCache c = fileCacheMap.remove(id);
            try {
                c.close();
            } catch (ProxyCacheException e) {
                e.printStackTrace();
            }
        }
    }

    public void removeFileCache(File file) {
        if (file == null) {
            return;
        }
        synchronized (fileCacheMap) {
            for(Map.Entry<String, FileCache> fileCache : fileCacheMap.entrySet()) {
                if (fileCache.getValue().getFile() != null && TextUtils.equals(fileCache.getValue().getFile().getPath(), file.getPath())) {
                    try {
                        fileCache.getValue().close();
                    } catch (ProxyCacheException e) {
                        e.printStackTrace();
                    }
                    fileCacheMap.remove(fileCache.getKey());
                }
            }
        }
    }

    public void removeFileCache(Cache cache) {
        if (cache == null) {
            return;
        }
        synchronized (fileCacheMap) {
            for(Map.Entry<String, FileCache> fileCache : fileCacheMap.entrySet()) {
                if (fileCache.getValue() != null && cache == fileCache.getValue()) {
                    try {
                        fileCache.getValue().close();
                    } catch (ProxyCacheException e) {
                        e.printStackTrace();
                    }
                    fileCacheMap.remove(fileCache.getKey());
                }
            }
        }
    }


}
