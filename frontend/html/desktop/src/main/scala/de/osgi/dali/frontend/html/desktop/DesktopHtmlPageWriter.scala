package de.osgi.dali.frontend.html.desktop

import javax.ws.rs._
import ext.{Provider, MessageBodyWriter}
import core.{MediaType, MultivaluedMap}
import MediaType.{APPLICATION_XHTML_XML, TEXT_HTML}

import java.io.{OutputStream, IOException}
import java.lang.annotation.Annotation
import java.lang.reflect.Type

import de.osxp.dali.page.{Page, ContentOfPage}

/**
 * message body writer for instances of page.
 * 
 * @author Mathias Broekelmann
 *
 * @since 22.12.2009
 *
 */
@Provider
@Produces(Array(APPLICATION_XHTML_XML, TEXT_HTML))
class DesktopHtmlPageWriter extends MessageBodyWriter[Page] {

    def isWriteable(clazz: Class[_], genericType: Type,
            annotations: Array[Annotation], mediaType: MediaType): Boolean = {
        classOf[Page].isAssignableFrom(clazz)
    }
    
    def getSize(page: Page, clazz: Class[_], genericType: Type,
            annotations: Array[Annotation], mediaType: MediaType): Long = {
        -1
    }
    
    def writeTo(page: Page, clazz: Class[_], genericType: Type,
            annotations: Array[Annotation], mediaType: MediaType,
            httpHeaders: MultivaluedMap[String, Object],
            out: OutputStream): Unit = {
    }
}