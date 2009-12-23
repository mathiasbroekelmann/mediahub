package de.osxp.dali.frontend.resources

import javax.ws.rs.ext.{Provider, MessageBodyWriter}
import javax.ws.rs.core.{MediaType, MultivaluedMap}

import java.io.{OutputStream, IOException}
import java.lang.annotation.Annotation
import java.lang.reflect.Type

import org.apache.commons.io.CopyUtils.copy
import org.apache.commons.io.output.{CountingOutputStream, NullOutputStream}

/**
 * message body writer for instances of spring resource.
 * 
 * @author Mathias Broekelmann
 *
 * @since 22.12.2009
 *
 */
@Provider
class ResourceMessageWriter extends MessageBodyWriter[Resource] {

    def isWriteable(clazz: Class[_], genericType: Type,
            annotations: Array[Annotation], mediaType: MediaType): Boolean = {
        classOf[Resource].isAssignableFrom(clazz)
    }
    
    def getSize(resource: Resource, clazz: Class[_], genericType: Type,
            annotations: Array[Annotation], mediaType: MediaType): Long = resource.size.getOrElse(-1)
    
    def writeTo(resource: Resource, clazz: Class[_], genericType: Type,
            annotations: Array[Annotation], mediaType: MediaType,
            httpHeaders: MultivaluedMap[String, Object],
            out: OutputStream): Unit = {
        resource.writeTo(out)
    }
}
