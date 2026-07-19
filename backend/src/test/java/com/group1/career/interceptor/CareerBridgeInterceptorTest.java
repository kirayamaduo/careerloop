package com.group1.career.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerBridgeInterceptorTest {

    @Test
    void acceptsOnlyTheConfiguredBridgeKey() throws Exception {
        CareerBridgeInterceptor interceptor = new CareerBridgeInterceptor("bridge-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CareerBridgeInterceptor.HEADER_NAME, "bridge-secret");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void rejectsMissingOrWrongKeysWithResultEnvelope() throws Exception {
        CareerBridgeInterceptor interceptor = new CareerBridgeInterceptor("bridge-secret");

        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest(), missingResponse, new Object()));
        assertEquals(401, missingResponse.getStatus());
        assertEquals(
                "{\"code\":401,\"message\":\"Unauthorized\",\"data\":null}",
                missingResponse.getContentAsString());

        MockHttpServletRequest wrongRequest = new MockHttpServletRequest();
        wrongRequest.addHeader(CareerBridgeInterceptor.HEADER_NAME, "wrong");
        MockHttpServletResponse wrongResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(wrongRequest, wrongResponse, new Object()));
        assertEquals(401, wrongResponse.getStatus());
    }

    @Test
    void blankConfigurationFailsClosed() throws Exception {
        CareerBridgeInterceptor interceptor = new CareerBridgeInterceptor("  ");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CareerBridgeInterceptor.HEADER_NAME, "anything");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }
}
