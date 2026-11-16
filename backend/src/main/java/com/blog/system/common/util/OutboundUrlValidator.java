package com.blog.system.common.util;

import com.blog.system.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

/** Trusted integration destinations and untrusted public fetches have different policies. */
@Component
public class OutboundUrlValidator {
    private final Set<String> privateHostAllowlist;
    public OutboundUrlValidator(@Value("${blog.security.outbound-private-host-allowlist:localhost,127.0.0.1,::1}") String allowlist) {
        privateHostAllowlist=Arrays.stream(allowlist.split(",")).map(String::trim).filter(StringUtils::hasText)
                .map(s->s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
    public URI validate(String value) { return validate(value,true); }
    public URI validatePublic(String value) { return validate(value,false); }
    private URI validate(String value, boolean trusted) {
        try {
            URI uri=URI.create(value==null?"":value.trim());
            if(!Set.of("http","https").contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT))
                    || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo()!=null)
                throw new BusinessException("出站地址仅支持不含凭据的 HTTP/HTTPS URL");
            String host=uri.getHost().replace("[","").replace("]","").toLowerCase(Locale.ROOT);
            if(trusted&&privateHostAllowlist.contains(host))return uri;
            for(InetAddress address:InetAddress.getAllByName(host)) if(isPrivate(address))
                throw new BusinessException("出站地址不允许访问内网、本机或链路本地地址");
            return uri;
        } catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException("出站地址解析失败");}
    }
    public static boolean isPrivate(InetAddress address) {
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()
                ||address.isSiteLocalAddress()||address.isMulticastAddress())return true;
        byte[] bytes=address.getAddress();int first=bytes[0]&255;
        if(address instanceof Inet4Address) {
            int second=bytes[1]&255;
            return first==0||first==10||first==127||first>=224 ||(first==100&&second>=64&&second<=127)
                    ||(first==169&&second==254)||(first==172&&second>=16&&second<=31)||(first==192&&second==168);
        }
        return !(address instanceof Inet6Address)||(first&254)==252||(first==254&&(bytes[1]&192)==128);
    }
}
