package com.blog.system.common.util;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Resolves and checks the exact addresses used by the socket; TLS keeps the original hostname. */
@Component
public class PublicHttpFetcher {
    private final OutboundUrlValidator validator;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "public-http-deadline"); t.setDaemon(true); return t;
    });
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"public-fetch");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public PublicHttpFetcher(OutboundUrlValidator validator) { this.validator = validator; }
    @jakarta.annotation.PreDestroy void close() { deadlines.shutdownNow(); workers.shutdownNow(); }

    public record Result(int status, String body) {}
    public Result fetch(String url, String accept, int maxBytes, long timeoutMs) throws IOException {
        Future<Result> result;
        try { result=workers.submit(()->fetchInternal(url,accept,maxBytes,timeoutMs)); }
        catch(RejectedExecutionException busy){throw new IOException("Public fetch is busy");}
        try{return result.get(Math.min(8000,Math.max(100,timeoutMs)),TimeUnit.MILLISECONDS);}
        catch(InterruptedException interrupted){result.cancel(true);Thread.currentThread().interrupt();throw new IOException("Fetch interrupted");}
        catch(TimeoutException timeout){result.cancel(true);throw new IOException("Fetch timed out");}
        catch(ExecutionException failed){throw new IOException("Public fetch failed",failed.getCause());}
    }
    private Result fetchInternal(String url, String accept, int maxBytes, long timeoutMs) throws IOException {
        URI uri = validator.validatePublic(url);
        long boundedTimeout = Math.min(8000, Math.max(100, timeoutMs));
        DnsResolver publicDns = new DnsResolver() {
            @Override public InetAddress[] resolve(String host) throws UnknownHostException {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                for (InetAddress address : addresses) if (OutboundUrlValidator.isPrivate(address))
                    throw new UnknownHostException("Private destination blocked");
                return addresses;
            }
            @Override public String resolveCanonicalHostname(String host) throws UnknownHostException {
                resolve(host); return host;
            }
        };
        var manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(publicDns).build();
        try (var client = HttpClients.custom().setConnectionManager(manager)
                .disableRedirectHandling().disableAutomaticRetries().disableContentCompression()
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(boundedTimeout))
                        .setResponseTimeout(Timeout.ofMilliseconds(boundedTimeout)).build()).build()) {
            var request = new HttpGet(uri);
            request.setHeader("Accept", accept);
            request.setHeader("User-Agent", "BlogPublicFetch/2.0");
            ScheduledFuture<?> cancel = deadlines.schedule(request::cancel, boundedTimeout, TimeUnit.MILLISECONDS);
            try {
                return client.execute(request, response -> {
                    int code = response.getCode();
                    if (code < 200 || code >= 300 || response.getEntity() == null) return new Result(code, "");
                    if (response.getEntity().getContentLength() > maxBytes) throw new IOException("Response too large");
                    try (var input = response.getEntity().getContent()) {
                        byte[] bytes = input.readNBytes(maxBytes + 1);
                        if (bytes.length > maxBytes) throw new IOException("Response too large");
                        return new Result(code, new String(bytes, StandardCharsets.UTF_8));
                    }
                });
            } finally { cancel.cancel(false); }
        }
    }
}
