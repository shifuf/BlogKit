package com.blog.system.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.util.*;

/** Forwarding headers are accepted only from configured proxy socket addresses. */
@Component
public class ClientIpResolver {
    private final int proxyCount;
    @Value("${blog.security.trusted-proxy-cidrs:127.0.0.1/32,::1/128}")
    private String trustedCidrs = "127.0.0.1/32,::1/128";
    public ClientIpResolver(@Value("${blog.security.trusted-proxy-count:1}") int count) { proxyCount=Math.max(0,count); }

    public String resolve(HttpServletRequest request) {
        String remote=request.getRemoteAddr();
        if(proxyCount==0 || !trusted(remote))return remote;
        String header=request.getHeader("X-Forwarded-For");
        if(header==null||header.length()>2048)return remote;
        String[] parts=header.split(",");
        if(parts.length<proxyCount || parts.length>20)return remote;
        String candidate=parts[parts.length-proxyCount].trim();
        for(int i=parts.length-1;i>parts.length-proxyCount;i--)if(!trusted(parts[i].trim()))return remote;
        return literal(candidate)==null?remote:candidate;
    }

    public static InetAddress literal(String ip) {
        if(ip==null || ip.length()>64 || ip.contains("%") || !ip.matches("[0-9a-fA-F:.]+"))return null;
        if(!ip.contains(":")) {
            String[] pieces=ip.split("\\.",-1);
            if(pieces.length!=4)return null;
            for(String piece:pieces)try{if(piece.isEmpty()||Integer.parseInt(piece)>255)return null;}catch(NumberFormatException invalid){return null;}
        }
        try{return InetAddress.getByName(ip);}catch(Exception invalid){return null;}
    }

    private boolean trusted(String ip) {
        InetAddress address=literal(ip);if(address==null)return false;
        for(String cidr:trustedCidrs.split(","))try{
            String[] pair=cidr.trim().split("/");InetAddress network=literal(pair[0]);
            if(network==null)continue;byte[] a=address.getAddress(),n=network.getAddress();if(a.length!=n.length)continue;
            int bits=pair.length==1?a.length*8:Integer.parseInt(pair[1]);if(bits<0||bits>a.length*8)continue;
            boolean match=true;
            for(int i=0;i<a.length;i++){int count=Math.min(8,Math.max(0,bits-i*8)),mask=count==0?0:(255 << (8-count))&255;if((a[i]&mask)!=(n[i]&mask)){match=false;break;}}
            if(match)return true;
        }catch(RuntimeException invalid){/* Ignore invalid configured ranges; fail closed. */}
        return false;
    }
}
