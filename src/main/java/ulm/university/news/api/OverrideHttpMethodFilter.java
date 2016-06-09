package ulm.university.news.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * This filter is used because clients may want to override the HTTP POST method by setting the X-HTTP-Method-Override
 * header. Clients may need to use this because sending some HTTP methods like PATCH are no supported in some APIs.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@PreMatching
@Provider
public class OverrideHttpMethodFilter implements ContainerRequestFilter {
    /** The logger instance for ModeratorController. */
    private static final Logger logger = LoggerFactory.getLogger(OverrideHttpMethodFilter.class);

    private static final String OVERRIDE_HEADER = "X-HTTP-Method-Override";

    private boolean override(String method, ContainerRequestContext request) {
        if (!method.isEmpty()) {
            request.setMethod(method);
            return true;
        }
        return false;
    }

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        if (request.getMethod().equalsIgnoreCase("POST"))
            if (override(request.getHeaders().getFirst(OVERRIDE_HEADER), request)) {
                logger.info("Method POST was overridden by method {}", request.getHeaders().getFirst(OVERRIDE_HEADER));
            }
    }
}