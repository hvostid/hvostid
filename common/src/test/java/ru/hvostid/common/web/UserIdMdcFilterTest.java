package ru.hvostid.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.hvostid.common.http.SecurityHeaders;

class UserIdMdcFilterTest {
    private final UserIdMdcFilter filter = new UserIdMdcFilter();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void putsHeaderValueInMdc_andClearsAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityHeaders.USER_ID, "u-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] inFlight = new String[1];
        FilterChain chain = (_, _) -> inFlight[0] = MDC.get(UserIdMdcFilter.MDC_KEY);

        filter.doFilter(request, response, chain);

        assertThat(inFlight[0]).isEqualTo("u-42");
        assertThat(MDC.get(UserIdMdcFilter.MDC_KEY)).isNull();
    }

    @Test
    void noHeader_clearsStaleMdcFromPreviousRequestOnReusedThread() throws Exception {
        MDC.put(UserIdMdcFilter.MDC_KEY, "u-stale");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (_, _) -> {
            /* no-op */
        };

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(UserIdMdcFilter.MDC_KEY)).isNull();
    }
}
