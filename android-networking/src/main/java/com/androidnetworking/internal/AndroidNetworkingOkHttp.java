package com.androidnetworking.internal;

/**
 * Created by amitshekhar on 22/03/16.
 */

import android.content.Context;

import com.androidnetworking.common.AndroidNetworkingData;
import com.androidnetworking.common.AndroidNetworkingRequest;
import com.androidnetworking.common.Constants;
import com.androidnetworking.core.Core;
import com.androidnetworking.error.AndroidNetworkingError;
import com.androidnetworking.interfaces.DownloadListener;
import com.androidnetworking.interfaces.DownloadProgressListener;
import com.androidnetworking.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import static com.androidnetworking.common.Method.DELETE;
import static com.androidnetworking.common.Method.GET;
import static com.androidnetworking.common.Method.HEAD;
import static com.androidnetworking.common.Method.PATCH;
import static com.androidnetworking.common.Method.POST;
import static com.androidnetworking.common.Method.PUT;

public class AndroidNetworkingOkHttp {

    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final int DOWNLOAD_CHUNK_SIZE = 2048;

    private static OkHttpClient sHttpClient = getClient();

    public static AndroidNetworkingData performSimpleRequest(AndroidNetworkingRequest request) throws AndroidNetworkingError {
        AndroidNetworkingData data = new AndroidNetworkingData();
        Request okHttpRequest = null;
        try {
            Request.Builder builder = new Request.Builder().url(request.getUrl());
            builder.addHeader(HEADER_USER_AGENT, "Android");
            Headers requestHeaders = request.getHeaders();
            if (requestHeaders != null) {
                builder.headers(requestHeaders);
                if (!requestHeaders.names().contains(HEADER_USER_AGENT)) {
                    builder.addHeader(HEADER_USER_AGENT, "Android");
                }
            }
            switch (request.getMethod()) {
                case GET: {
                    builder = builder.get();
                    break;
                }
                case POST: {
                    builder = builder.post(request.getRequestBody());
                    break;
                }
                case PUT: {
                    builder = builder.put(request.getRequestBody());
                    break;
                }
                case DELETE: {
                    builder = builder.delete(request.getRequestBody());
                    break;
                }
                case HEAD: {
                    builder = builder.head();
                    break;
                }
                case PATCH: {
                    builder = builder.patch(request.getRequestBody());
                    break;
                }
            }
            if (request.getCacheControl() != null) {
                builder.cacheControl(request.getCacheControl());
            }
            okHttpRequest = builder.build();
            Response okResponse = sHttpClient.newCall(okHttpRequest).execute();
            data.url = okResponse.request().url();
            data.code = okResponse.code();
            data.headers = okResponse.headers();
            data.source = okResponse.body().source();
            data.length = okResponse.body().contentLength();
        } catch (IOException ioe) {
            if (okHttpRequest != null) {
                data.url = okHttpRequest.url();
            }
            throw new AndroidNetworkingError(data, ioe);
        }

        return data;
    }

    public static AndroidNetworkingData performDownloadRequest(AndroidNetworkingRequest request) throws AndroidNetworkingError {
        AndroidNetworkingData data = new AndroidNetworkingData();
        Request okHttpRequest = null;
        try {
            Request.Builder builder = new Request.Builder().url(request.getUrl());
            builder.addHeader(HEADER_USER_AGENT, "Android");
            Headers requestHeaders = request.getHeaders();
            if (requestHeaders != null) {
                builder.headers(requestHeaders);
                if (!requestHeaders.names().contains(HEADER_USER_AGENT)) {
                    builder.addHeader(HEADER_USER_AGENT, "Android");
                }
            }
            builder = builder.get();
            if (request.getCacheControl() != null) {
                builder.cacheControl(request.getCacheControl());
            }
            okHttpRequest = builder.build();
            Response okResponse = sHttpClient.newCall(okHttpRequest).execute();
            data.url = okResponse.request().url();
            data.code = okResponse.code();
            data.headers = okResponse.headers();
            ResponseBody body = okResponse.body();
            data.length = body.contentLength();
            BufferedSource source = body.source();
            File file = new File(request.getDirPath() + File.separator + request.getFileName());
            BufferedSink sink = Okio.buffer(Okio.sink(file));
            long bytesRead = 0;
            while (source.read(sink.buffer(), DOWNLOAD_CHUNK_SIZE) != -1) {
                bytesRead += DOWNLOAD_CHUNK_SIZE;
                updateProgress(bytesRead, data.length, request.getDownloadProgressListener());
            }
            sink.writeAll(source);
            sink.close();
            updateCompletion(request.getDownloadListener());
        } catch (IOException ioe) {
            if (okHttpRequest != null) {
                data.url = okHttpRequest.url();
            }
            throw new AndroidNetworkingError(data, ioe);
        }
        return data;
    }

    public static AndroidNetworkingData performUploadRequest(AndroidNetworkingRequest request) throws AndroidNetworkingError {
        AndroidNetworkingData data = new AndroidNetworkingData();
        Request okHttpRequest = null;
        try {
            Request.Builder builder = new Request.Builder().url(request.getUrl());
            builder.addHeader(HEADER_USER_AGENT, "Android");

            Headers requestHeaders = request.getHeaders();
            if (requestHeaders != null) {
                builder.headers(requestHeaders);
                if (!requestHeaders.names().contains(HEADER_USER_AGENT)) {
                    builder.addHeader(HEADER_USER_AGENT, "Android");
                }
            }
            builder = builder.post(new RequestProgressBody(request.getMultiPartRequestBody(), request.getUploadProgressListener()));
            if (request.getCacheControl() != null) {
                builder.cacheControl(request.getCacheControl());
            }
            okHttpRequest = builder.build();
            Response okResponse = sHttpClient.newCall(okHttpRequest).execute();
            data.url = okResponse.request().url();
            data.code = okResponse.code();
            data.headers = okResponse.headers();
            data.source = okResponse.body().source();
            data.length = okResponse.body().contentLength();
        } catch (IOException ioe) {
            if (okHttpRequest != null) {
                data.url = okHttpRequest.url();
            }
            throw new AndroidNetworkingError(data, ioe);
        }
        return data;
    }


    public static void updateProgress(final long bytesDownloaded, final long totalBytes, final DownloadProgressListener downloadProgressListener) {
        if (downloadProgressListener != null) {
            Core.getInstance().getExecutorSupplier().forMainThreadTasks().execute(new Runnable() {
                @Override
                public void run() {
                    downloadProgressListener.onProgress(bytesDownloaded, totalBytes);
                }
            });
        }
    }

    public static void updateCompletion(final DownloadListener downloadListener) {
        if (downloadListener != null) {
            Core.getInstance().getExecutorSupplier().forMainThreadTasks().execute(new Runnable() {
                @Override
                public void run() {
                    downloadListener.onDownloadComplete();
                }
            });
        }
    }

    public static OkHttpClient getClient() {
        if (sHttpClient == null) {
            return getDefaultClient();
        }
        return sHttpClient;
    }

    public static OkHttpClient getDefaultClient() {
        return new OkHttpClient().newBuilder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public static void setClientWithCache(Context context) {
        sHttpClient = new OkHttpClient().newBuilder()
                .cache(Utils.getCache(context, Constants.MAX_CACHE_SIZE, Constants.CACHE_DIR_NAME))
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public static void setClient(OkHttpClient okHttpClient) {
        sHttpClient = okHttpClient;
    }

}