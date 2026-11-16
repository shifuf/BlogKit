package com.blog.system.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

/**
 * 评论回邮等场景的邮箱校验：格式必过；MX/A 仅在明确无记录时拒绝，DNS 异常放行。
 */
public final class EmailAddressValidator {
    private static final Logger log = LoggerFactory.getLogger(EmailAddressValidator.class);
    /** 与消息平台 / 前台一致的宽松格式 */
    private static final Pattern EMAIL_FORMAT =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final long DNS_TIMEOUT_MS = 1500L;
    private static final ExecutorService DNS_EXECUTOR = new java.util.concurrent.ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(32), r -> {
        Thread t = new Thread(r, "email-dns-check");
        t.setDaemon(true);
        return t;
    }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

    private EmailAddressValidator() {
    }

    public static boolean isValidFormat(String email) {
        if (!StringUtils.hasText(email)) return false;
        String v = email.trim();
        if (v.length() < 5 || v.length() > 254) return false;
        return EMAIL_FORMAT.matcher(v).matches();
    }

    public static String extractDomain(String email) {
        if (!StringUtils.hasText(email)) return "";
        String v = email.trim();
        int at = v.lastIndexOf('@');
        if (at < 0 || at == v.length() - 1) return "";
        return v.substring(at + 1).trim().toLowerCase();
    }

    /**
     * @return true=域名可收信或无法判定（放行）；false=明确无 MX 且无 A/AAAA
     */
    public static boolean hasMxOrA(String domain) {
        if (!StringUtils.hasText(domain)) return false;
        String d = domain.trim().toLowerCase();
        try {
            return runWithTimeout(() -> {
                if (lookupMx(d)) return true;
                return hasAddressRecord(d);
            }, DNS_TIMEOUT_MS);
        } catch (TimeoutException te) {
            log.info("邮箱域名 DNS 超时，放行 domain={}", d);
            return true;
        } catch (Exception ex) {
            log.info("邮箱域名 DNS 异常，放行 domain={} err={}", d, ex.getMessage());
            return true;
        }
    }

    private static boolean lookupMx(String domain) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "1000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        InitialDirContext ctx = new InitialDirContext(env);
        try {
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute mx = attrs.get("MX");
            return mx != null && mx.size() > 0;
        } finally {
            try {
                ctx.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private static boolean hasAddressRecord(String domain) {
        try {
            InetAddress[] addrs = InetAddress.getAllByName(domain);
            return addrs != null && addrs.length > 0;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private static boolean runWithTimeout(Callable<Boolean> task, long timeoutMs)
            throws TimeoutException, Exception {
        Future<Boolean> future = DNS_EXECUTOR.submit(task);
        try {
            Boolean result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return result != null && result;
        } catch (TimeoutException te) {
            future.cancel(true);
            throw te;
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() == null ? ee : ee.getCause();
            if (cause instanceof Exception e) throw e;
            throw new Exception(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        }
    }
}
