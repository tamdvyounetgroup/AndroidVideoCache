package com.danikula.videocache;

import android.text.TextUtils;

import com.danikula.videocache.headers.EmptyHeadersInjector;
import com.danikula.videocache.headers.HeaderInjector;
import com.danikula.videocache.sourcestorage.SourceInfoStorage;
import com.danikula.videocache.sourcestorage.SourceInfoStorageFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.danikula.videocache.Preconditions.checkNotNull;
import static com.danikula.videocache.ProxyCacheUtils.DEFAULT_BUFFER_SIZE;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;

/**
 * {@link Source} that uses http resource as source for {@link ProxyCache}.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
public class HttpUrlSource implements Source {

    private static final Logger LOG = LoggerFactory.getLogger("HttpUrlSource");

    private static final int MAX_REDIRECTS = 5;
    private final SourceInfoStorage sourceInfoStorage;
    private final HeaderInjector headerInjector;
    private SourceInfo sourceInfo;
    private Response response;
    private InputStream inputStream;

    public HttpUrlSource(String url) {
        this(url, SourceInfoStorageFactory.newEmptySourceInfoStorage());
    }

    public HttpUrlSource(String url, SourceInfoStorage sourceInfoStorage) {
        this(url, sourceInfoStorage, new EmptyHeadersInjector());
    }

    public HttpUrlSource(String url, SourceInfoStorage sourceInfoStorage, HeaderInjector headerInjector) {
        this.sourceInfoStorage = checkNotNull(sourceInfoStorage);
        this.headerInjector = checkNotNull(headerInjector);
        SourceInfo sourceInfo = sourceInfoStorage.get(url);
        this.sourceInfo = sourceInfo != null ? sourceInfo :
                new SourceInfo(url, Integer.MIN_VALUE, ProxyCacheUtils.getSupposablyMime(url));
    }

    public HttpUrlSource(HttpUrlSource source) {
        this.sourceInfo = source.sourceInfo;
        this.sourceInfoStorage = source.sourceInfoStorage;
        this.headerInjector = source.headerInjector;
    }

    @Override
    public synchronized long length() throws ProxyCacheException {
        if (sourceInfo.length == Integer.MIN_VALUE) {
            fetchContentInfo();
        }
        return sourceInfo.length;
    }

    @Override
    public boolean open(long offset) throws ProxyCacheException {
        boolean needCover=false;//缓存出错重新下载需要覆盖旧文件
        try {
            try {
                response = request(offset, -1);
            } catch (IOException e) {
                response = request(0, -1);
                needCover = true;
            }

            String mime = response.header("content-type");
            inputStream = new BufferedInputStream(response.body().byteStream(), DEFAULT_BUFFER_SIZE);
            long length = readSourceAvailableBytes(response, offset, response.code());
            this.sourceInfo = new SourceInfo(sourceInfo.url, length, mime);
            this.sourceInfoStorage.put(sourceInfo.url, sourceInfo);
        } catch (IOException e) {
            throw new ProxyCacheException("Error opening connection for " + sourceInfo.url + " with offset " + offset, e);
        }
        return needCover;
    }

    private long readSourceAvailableBytes(Response response, long offset, int responseCode) throws IOException {
        long contentLength = getContentLength(response);
        return responseCode == HTTP_OK ? contentLength
                : responseCode == HTTP_PARTIAL ? contentLength + offset : sourceInfo.length;
    }

    private long getContentLength(Response response) {
        String contentLengthValue = response.header("Content-Length");
        return contentLengthValue == null ? -1 : Long.parseLong(contentLengthValue);
    }

    @Override
    public void close() throws ProxyCacheException {
        if (response != null) {
            try {
                response.close();
            } catch (NullPointerException | IllegalArgumentException e) {
                String message = "Wait... but why? WTF!? " +
                        "Really shouldn't happen any more after fixing https://github.com/danikula/AndroidVideoCache/issues/43. " +
                        "If you read it on your device log, please, notify me danikula@gmail.com or create issue here " +
                        "https://github.com/danikula/AndroidVideoCache/issues.";
                throw new RuntimeException(message, e);
            } catch (ArrayIndexOutOfBoundsException e) {
                LOG.error("Error closing connection correctly. Should happen only on Android L. " +
                        "If anybody know how to fix it, please visit https://github.com/danikula/AndroidVideoCache/issues/88. " +
                        "Until good solution is not know, just ignore this issue :(", e);
            }
        }
    }

    @Override
    public int read(byte[] buffer) throws ProxyCacheException {
        if (inputStream == null) {
            throw new ProxyCacheException("Error reading data from " + sourceInfo.url + ": connection is absent!");
        }
        try {
            return inputStream.read(buffer, 0, buffer.length);
        } catch (InterruptedIOException e) {
            throw new InterruptedProxyCacheException("Reading source " + sourceInfo.url + " is interrupted", e);
        } catch (IOException e) {
            throw new ProxyCacheException("Error reading data from " + sourceInfo.url, e);
        }
    }

    private void fetchContentInfo() throws ProxyCacheException {
        LOG.debug("Read content info from " + sourceInfo.url);
        Response response = null;
        InputStream inputStream = null;
        try {
            response = request(0, 10000);
            long length = getContentLength(response);
            String mime = response.header("content-type");
            inputStream = response.body().byteStream();
            this.sourceInfo = new SourceInfo(sourceInfo.url, length, mime);
            this.sourceInfoStorage.put(sourceInfo.url, sourceInfo);
            LOG.debug("Source info fetched: " + sourceInfo);
        } catch (IOException e) {
            LOG.error("Error fetching info from " + sourceInfo.url, e);
        } finally {
            ProxyCacheUtils.close(inputStream);
            if (response != null) {
               response.close();
            }
        }
    }

    private Response request(long offset, int timeout) throws IOException, ProxyCacheException {
        Response response;
        boolean redirected;
        int redirectCount = 0;
        String url = this.sourceInfo.url;

        do {
            LOG.debug("Open connection " + (offset > 0 ? " with offset " + offset : "") + " to " + url);
            Request.Builder requestBuilder = new Request.Builder().url(url);
            injectCustomHeaders(requestBuilder, url);
//            connection = (HttpURLConnection) new URL(url).openConnection();
//            injectCustomHeaders(connection, url);
            if (offset > 0) {
                requestBuilder.addHeader("Range", "bytes=" + offset + "-");
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
        Map<String, String> extraHeaders = headerInjector.addHeaders(url);
        for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }
    }

    public synchronized String getMime() throws ProxyCacheException {
        if (TextUtils.isEmpty(sourceInfo.mime)) {
            fetchContentInfo();
        }
        return sourceInfo.mime;
    }

    public String getUrl() {
        return sourceInfo.url;
    }

    @Override
    public String toString() {
        return "HttpUrlSource{sourceInfo='" + sourceInfo + "}";
    }
}
