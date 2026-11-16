package com.blog.system.service;

import com.blog.system.common.util.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class ProxyBoundaryTest {
    @Test void untrustedSocketCannotSupplyForwardedIdentity() {
        var request=new MockHttpServletRequest();request.setRemoteAddr("8.8.8.8");
        request.addHeader("X-Forwarded-For","1.1.1.1");request.addHeader("X-Real-IP","1.1.1.1");
        assertEquals("8.8.8.8",new ClientIpResolver(1).resolve(request));
    }
    @Test void zeroProxyDeploymentIgnoresAllForwardedHeaders() {
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For","1.1.1.1");
        assertEquals("127.0.0.1",new ClientIpResolver(0).resolve(request));
    }
}
