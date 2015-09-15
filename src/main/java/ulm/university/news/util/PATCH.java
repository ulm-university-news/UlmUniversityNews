package ulm.university.news.util;

import javax.ws.rs.HttpMethod;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Creating an annotation @PATCH which can be used within the Jersey framework to map incoming HTTP PATCH requests
 * to the handler methods.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@HttpMethod("PATCH")
public @interface PATCH {
}
