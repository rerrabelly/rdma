/*
 * Copyright 2015 herd contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.finra.herd.app.security;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.servlet.ServletException;

import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.finra.herd.app.AbstractAppTest;
import org.finra.herd.app.HttpSecurityHeadersFilter;

/**
 * This class tests functionality of the Cache Control Filter.
 */
public class HttpSecurityHeadersFilterTest extends AbstractAppTest
{
    @Test
    public void testCacheControl() throws IOException, ServletException
    {
        HttpSecurityHeadersFilter httpSecurityHeadersFilter = new HttpSecurityHeadersFilter();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        httpSecurityHeadersFilter.doFilter(new MockHttpServletRequest(), mockHttpServletResponse, new MockFilterChain());
        assertEquals(200, mockHttpServletResponse.getStatus());
        assertEquals("no-store, no-cache, must-revalidate, max-age=0", mockHttpServletResponse.getHeader("Cache-Control"));
        assertEquals("no-cache", mockHttpServletResponse.getHeader("Pragma"));
        assertEquals("frame-ancestors 'none'; script-src 'self'; " +
            "connect-src 'self'; img-src 'self'; base-uri 'self'; form-action 'self'",
            mockHttpServletResponse.getHeader("Content-Security-Policy"));
    }
}
