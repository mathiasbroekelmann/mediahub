package de.osxp.dali.frontend.resources

import javax.ws.rs.{Path, GET, PathParam}
import javax.ws.rs.core.{Response, MediaType}


import Response.{ok, status}
import Response.Status.NOT_FOUND

import com.google.inject.Inject
import com.google.inject.name.Named

import java.util.Date
import java.io.OutputStream
import java.net.URI

import org.apache.commons.io.output.ByteArrayOutputStream

import net.sf.jmimemagic.MagicMatchNotFoundException
import net.sf.jmimemagic.Magic.getMagicMatch

/**
 * Identifies a resource.
 * 
 * @author Mathias Broekelmann
 *
 * @since 22.12.2009
 *
 */
trait Resource {
    
    /**
     * Optionally provide the last modified time stamp.
     */
    def lastModified: Option[Long]
    
    /**
     * Write the content of the resource to the given output stream.
     */
    def writeTo[A<:OutputStream](out: A): A
    
    /**
     * the size (if known) of the resource.
     */
    def size: Option[Long]
    
    /**
     * the uri of the resource.
     */
    def uri: java.net.URI
    
    /**
     * the content type of this resource
     */
    def contentType: String
}

/**
 * a resource loader is used to load a resource.
 * 
 * @author Mathias Broekelmann
 *
 * @since 22.12.2009
 *
 */
trait ResourceLoader {
    
    /**
     * return some resource for the given location. Return none if the resource is not found.
     */
    def getResource(location: String): Option[Resource]
}

/**
 * Resolves static resources.
 */
@Path("/")
class StaticResources @Inject() (val loader: ResourceLoader) {
    
    /**
     * Resolves a resource for a given path. that path will be used as a location for the resource loader.
     */
    @GET
    @Path("{path:.+}")
    def resolveResource(@PathParam("path") location: String): Response = {
        loader
            .getResource(location)
            .map(found _)
            .getOrElse(status(NOT_FOUND))
            .build
    }
    
    /**
     * build the response for a found resource.
     */
    private[this] def found(resource: Resource) = {
        var response = ok(resource, resource.contentType)
        for(time <- resource.lastModified; if(time > 0)) {
            response = response.lastModified(new Date(time))
        }
        response
    }
    
    private[this] def resolveMediaType(resource: Resource): MediaType = {
        val bytes = resource.writeTo(new ByteArrayOutputStream).toByteArray
        try {
            getMagicMatch(bytes).getMimeType.split('/') match {
                case Array(main) => new MediaType(main, "")
                case Array(main, sub) => new MediaType(main, sub)
                case _ => MediaType.APPLICATION_OCTET_STREAM_TYPE
            }
        } catch {
            case ex: MagicMatchNotFoundException => MediaType.APPLICATION_OCTET_STREAM_TYPE
        }
    }
}
