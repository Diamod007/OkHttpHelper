package com.xiaolei.okhttputil.interceptor;

import android.content.Context;

import com.xiaolei.okhttputil.Catch.DiskCache;
import com.xiaolei.okhttputil.Catch.CacheType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.*;

/**
 * 高级缓存,可以缓存一切数据
 * Created by xiaolei on 2018/2/7.
 */
public class CacheInterceptor implements Interceptor
{
    private DiskCache diskCache;
    private static final String cacheDirName = "ResponseCache";
    
    private final String headerSplite = "@:header:@"; // header与header之间的分隔符
    private final String headKVSplite = "@:header:=@";// heade里面，Key与Value的分隔符
    
    public CacheInterceptor(Context context, File cacheDir)
    {
        diskCache = DiskCache.getInstance(cacheDir, context);
    }

    public CacheInterceptor(Context context)
    {
        this(context, new File(context.getCacheDir(), cacheDirName));
    }

    @Override
    public Response intercept(Chain chain) throws IOException
    {
        Request request = chain.request();
        String cacheHead = request.header("cache");
        String cache_control = request.header("Cache-Control");
        if ("true".equals(cacheHead) ||                              // 意思是要缓存
                (cache_control != null && !cache_control.isEmpty())) // 这里还支持WEB端协议的缓存头
        {
            String url = request.url().url().toString();
            String reqBodyStr = getPostParams(request);
            String key = url + "?" + reqBodyStr;//链接就是缓存的key
            try
            {
                // 网络正常，缓存正常数据
                Response response = chain.proceed(request);
                if (response.isSuccessful()) // 正常了，才去缓存数据
                {
                    storeResponse(response, key);
                    response = getResponse(key, request, false);
                }
                return response;
            } catch (Exception e)
            {
                // 网络异常，取缓存
                if (diskCache.containsKey(key)) // 如果缓存里面有数据
                {
                    // 则取缓存
                    return getResponse(key, request, true);
                } else
                {
                    // 否则，正常流程走
                    return chain.proceed(request);
                }
            }
        } else
        {
            Response response = chain.proceed(request);
            return response;
        }
    }

    /**
     * 根据key，以及request，获取对应的Response响应
     * @param key
     * @param request
     * @param isFromCache 是否来源于缓存
     * @return
     */
    private Response getResponse(String key, Request request, boolean isFromCache)
    {
        InputStream inputStream = diskCache.getStream(key);
        String mediaTypeStr = diskCache.getString(key + "@:mediaType");
        String protocolStr = diskCache.getString(key + "@:protocol");
        String messageStr = diskCache.getString(key + "@:message");
        String headerStr = diskCache.getString(key + "@:headers");
        headerStr = headerStr == null ? "" : headerStr;//防止为空

        int contentLength = 0;
        Protocol protocol = (protocolStr == null || protocolStr.isEmpty()) ? Protocol.HTTP_1_1 : Protocol.valueOf(protocolStr);
        MediaType mediaType = (mediaTypeStr == null) ? null : MediaType.parse(mediaTypeStr);
        Map<String, String> headmap = new LinkedHashMap<>();
        String heads[] = headerStr.split(headerSplite);
        for (String a : heads)
        {
            String keyValue[] = a.split(headKVSplite);
            if (keyValue.length == 2 && keyValue[0] != null && keyValue[1] != null)
            {
                headmap.put(keyValue[0], keyValue[1]);
            }
        }
        Headers headers = Headers.of(headmap);
        try
        {
            contentLength = inputStream.available();
        } catch (IOException e)
        {
            contentLength = 0;
        }
        Source source = Okio.source(inputStream);
        BufferedSource bufferedSource = Okio.buffer(source);
        ResponseBody body = ResponseBody.create(mediaType, contentLength, bufferedSource);
        Response response = new Response.Builder().
                code(200).
                body(body).
                headers(headers).
                request(request).
                message(isFromCache ? CacheType.DISK_CACHE : messageStr).
                protocol(protocol).
                build();
        return response;
    }

    /**
     * 将Response全部缓存起来
     *
     * @param response
     */
    private void storeResponse(Response response, String key)
    {
        ResponseBody responseBody = response.body();
        String message = response.message();
        Headers headers = response.headers();
        if (responseBody != null)
        {
            MediaType mediaType = responseBody.contentType();
            String typeStr = "";
            if (mediaType != null)
            {
                typeStr = mediaType.toString();
            }
            Protocol protocol = response.protocol();

            if (headers != null)
            {
                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 0; i < headers.size(); i++)
                {
                    String name = headers.name(i);
                    String value = headers.get(name);
                    stringBuilder.append(name).append(headKVSplite).append(value);
                    if (i + 1 != headers.size())
                    {
                        // 调皮的分隔符
                        stringBuilder.append(headerSplite);
                    }
                }
                diskCache.put(key + "@:headers", stringBuilder.toString());
                stringBuilder.delete(0, stringBuilder.length() - 1);//清空
            }

            diskCache.put(key, responseBody.byteStream());
            diskCache.put(key + "@:mediaType", typeStr);
            diskCache.put(key + "@:protocol", protocol.name() + "");
            diskCache.put(key + "@:message", message == null ? "" : message);
        }
    }

    /**
     * 获取在Post方式下。向服务器发送的参数
     * 如果是Get，那么返回空字符串
     * @param request
     * @return
     */
    private String getPostParams(Request request)
    {
        String reqBodyStr = "";
        String method = request.method();
        if ("POST".equals(method)) // 如果是Post，则尽可能解析每个参数
        {
            StringBuilder sb = new StringBuilder();
            if (request.body() instanceof FormBody)
            {
                FormBody body = (FormBody) request.body();
                if (body != null)
                {
                    for (int i = 0; i < body.size(); i++)
                    {
                        sb.append(body.encodedName(i)).append("=").append(body.encodedValue(i)).append(",");
                    }
                    sb.delete(sb.length() - 1, sb.length());
                }
                reqBodyStr = sb.toString();
                sb.delete(0, sb.length());
            }
        }
        return reqBodyStr;
    }
}
