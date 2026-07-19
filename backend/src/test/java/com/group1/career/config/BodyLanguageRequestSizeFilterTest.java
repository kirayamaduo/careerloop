package com.group1.career.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BodyLanguageRequestSizeFilterTest {

    private final BodyLanguageRequestSizeFilter filter = new BodyLanguageRequestSizeFilter();

    @Test
    @DisplayName("Oversize frame is rejected from Content-Length before MVC reads the body")
    void rejectsKnownOversizeBodyEarly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/body-language/frame");
        request.setContent(new byte[(int) BodyLanguageRequestSizeFilter.MAX_REQUEST_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":413"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Normal frame request continues down the filter chain")
    void allowsNormalBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/body-language/frame");
        request.setContent(new byte[1024]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
