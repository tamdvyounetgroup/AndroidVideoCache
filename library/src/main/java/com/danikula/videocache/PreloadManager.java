package com.danikula.videocache;

import android.util.Log;

import com.danikula.videocache.file.FileCache;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.danikula.videocache.ProxyCacheUtils.DEFAULT_BUFFER_SIZE;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;

public class PreloadManager {
    private static PreloadManager sInstance;
    private static final String TAG = "PreloadManager";
    private Config config;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Map<String, Future> preloadFutures = new HashMap<>();
    private static final int MAX_REDIRECTS = 3;
    private HttpProxyCacheServer proxyCacheServer;

    private PreloadManager() {
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public void setProxyCacheServer(HttpProxyCacheServer server) {
        this.proxyCacheServer = server;
    }

    public static PreloadManager getInstance() {
        if (sInstance == null) {
            sInstance = new PreloadManager();
        }
        return sInstance;
    }

    public void preload(String id, String url, long preloadLength) {
        if (preloadFutures.containsKey(id)) {
            Log.d(TAG, "is already in preload :" + id);
            return;
        }
        Log.d(TAG, "try preload :" + id);
        Future t = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    //FileCache cache = new FileCache(config.generateCacheFile(id, url), config.diskUsage);
//                    cache = ItemCachesHolder.getInstance().getFileCache(id, url, config);
//                    if (cache.isCompleted() || cache.available() > 0) {
//                        Log.d(TAG, "can't preload" + url);
//                        return;
//                    }
                    if (proxyCacheServer == null) {
                        Log.d(TAG, "cache server not init can't preload");
                        return;
                    }
                    File file = config.generateCacheFile(id, url);
                    if (file.exists()) {
                        Log.d(TAG, "file has exist " + file.getName());
                        return;
                    }
                    File tempFile = new File(file.getParentFile(), file.getName() + ".download");

                    if (tempFile.exists()) {
                        Log.d(TAG, "preload file has exist " + tempFile.getName());

                        return;
                    }
                    String proxyUrl = proxyCacheServer.getProxyUrl(id, url);
                    Response response = request(proxyUrl, preloadLength, -1);

                    BufferedInputStream inputStream = new BufferedInputStream(response.body().byteStream(), DEFAULT_BUFFER_SIZE);
                    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                    while ((inputStream.read(buffer)) != -1) {
                    }
                    inputStream.close();
                    response.close();

                    preloadFutures.remove(id);
                    //ItemCachesHolder.getInstance().removeFileCache(id);
                    Log.d(TAG, "preload success :" + id);
                } catch (ProxyCacheException | IOException e) {
                    Log.d(TAG, "preload failed :" + e.getMessage());
                    e.printStackTrace();
                    preloadFutures.remove(id);
                }
            }
        });
        preloadFutures.put(url, t);

    }

    private Response request(String url, long size,  int timeout) throws IOException, ProxyCacheException {
        Response response;
        boolean redirected;
        int redirectCount = 0;

        do {
            Request.Builder requestBuilder = new Request.Builder().url(url);
            injectCustomHeaders(requestBuilder, url);
            if (size > 0) {
                requestBuilder.addHeader("Range", "bytes=0-" + size);
            }
            OkHttpClient.Builder clientBuilder = OkHttpProvider.INSTANCE.getBuilder();
            clientBuilder.followRedirects(false);
            if (timeout > 0) {
                clientBuilder.connectTimeout(timeout, TimeUnit.SECONDS);
                clientBuilder.readTimeout(timeout, TimeUnit.SECONDS);
            }
            response = clientBuilder.build().newCall(requestBuilder.build()).execute();
            redirected = response.isRedirect();
            if (redirected) {
                url = response.header("Location");
                redirectCount++;
                response.close();
            }
            if (redirectCount > MAX_REDIRECTS) {
                throw new ProxyCacheException("Too many redirects: " + redirectCount);
            }
        } while (redirected);
        return response;
    }


    private void injectCustomHeaders(Request.Builder request, String url) {
        Map<String, String> extraHeaders = config.headerInjector.addHeaders(url);
        for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }
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
