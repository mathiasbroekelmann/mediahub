package de.osxp.dali.frontend.resources;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.ResponseBuilder;

/**
 * @author Mathias Broekelmann
 *
 * @since 23.12.2009
 *
 */
public final class ResponseBuilders {
    private ResponseBuilders() {
    }

    public static ResponseBuilder mediaType(final ResponseBuilder builder, final MediaType mediaType) {
        return builder.type(mediaType);
    }

    public static ResponseBuilder mediaType(final ResponseBuilder builder, final String mediaType) {
        return builder.type(mediaType);
    }
}
