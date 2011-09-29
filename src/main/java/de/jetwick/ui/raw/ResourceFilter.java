/*
 * Copyright 2011 Peter Karich, jetwick_@_pannous_._info.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.ui.raw;

import de.jetwick.util.Helper;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter to set 'far future' expiration date
 * 
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class ResourceFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ResourceFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        HttpServletResponse rsp = (HttpServletResponse) response;
        // DUPLICATE code in Jetslide.configureResponse
        // Last-Modified: Mon, 29 Jun 1998 02:28:12 GMT
        SimpleDateFormat formatter = new SimpleDateFormat(Helper.cacheDateFormatString);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        int minutes = 30 * 24 * 60;
        rsp.setHeader("Cache-Control", "public, max-age=" + minutes * 60);

//        logger.info("setHeader called " + ((HttpServletResponse) response).toString());

        // Continue
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
