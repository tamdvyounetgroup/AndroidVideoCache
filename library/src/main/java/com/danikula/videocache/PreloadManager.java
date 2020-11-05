package com.danikula.videocache;

import android.util.Log;

import com.danikula.videocache.file.FileCache;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.OkHttpClient;
import okhttp3.Request;

import static com.danikula.videocache.ProxyCacheUtils.DEFAULT_BUFFER_SIZE;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;

public class PreloadManager {
    private static PreloadManager sInstance;
    private Config config;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Map<String, Future> preloadFutures = new HashMap<>();
    private static final int MAX_REDIRECTS = 3;

    private PreloadManager() {
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public static PreloadManager getInstance() {
        if (sInstance == null) {
            sInstance = new PreloadManager();
        }
        return sInstance;
    }

    public void preload(String id, String url, long preloadLength) {
        if (preloadFutures.containsKey(id)) {
            Log.d("Preload", "is already in preload :" + id);
            return;
        }
        Log.d("Preload", "try preload :" + id);
        Future t = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    FileCache cache = new FileCache(config.generateCacheFile(id, url), config.diskUsage);
                    if (cache.isCompleted() || cache.available() > 0) {
                        Log.d("Preload", "can't preload" + url);
                        return;
                    }

                    HttpURLConnection connection = openConnection(url, preloadLength, -1);
                    BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream(), DEFAULT_BUFFER_SIZE);
                    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                    int readBytes;
                    while ((readBytes = inputStream.read(buffer)) != -1) {
                        cache.append(buffer, readBytes);
                    }
                    cache.close();
                    inputStream.close();
                    connection.disconnect();
                    preloadFutures.remove(id);
                    Log.d("Preload", "preload success :" + id);
                } catch (ProxyCacheException | IOException e) {
                    Log.d("Preload", "preload failed :" + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        preloadFutures.put(url, t);

    }

    private void injectCustomHeaders(HttpURLConnection connection, String url) {
        Map<String, String> extraHeaders = config.headerInjector.addHeaders(url);
        for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
    }

    private HttpURLConnection openConnection(String url, long size, int timeout) throws IOException, ProxyCacheException {
        HttpURLConnection connection;
        boolean redirected;
        int redirectCount = 0;
        do {
            connection = (HttpURLConnection) new URL(url).openConnection();
            injectCustomHeaders(connection, url);
            if (size > 0) {
                connection.setRequestProperty("Range", "bytes=0-" + size);
            }
            if (timeout > 0) {
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);
            }
            int code = connection.getResponseCode();
            redirected = code == HTTP_MOVED_PERM || code == HTTP_MOVED_TEMP || code == HTTP_SEE_OTHER;
            if (redirected) {
                url = connection.getHeaderField("Location");
                redirectCount++;
                connection.disconnect();
            }
            if (redirectCount > MAX_REDIRECTS) {
                throw new ProxyCacheException("Too many redirects: " + redirectCount);
            }
        } while (redirected);
        return connection;
    }


    public boolean isPreloading(String id) {
        return preloadFutures.containsKey(id);
    }

    public void cancel(String id) {
        if (preloadFutures.containsKey(id)) {
            preloadFutures.remove(id).cancel(true);
        }
    }

    public void cancelAll() {
        for (String key : preloadFutures.keySet()) {
            preloadFutures.remove(key).cancel(true);
        }
    }

    public boolean hasCache(String id) {
        return false;
    }


}
