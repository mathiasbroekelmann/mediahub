/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import org.osgi.framework.{Bundle, BundleContext}

import org.mediahub.web.links._

import javax.activation.MimeType
import javax.ws.rs.ext.{Provider, MessageBodyWriter}
import javax.ws.rs.core.{MediaType, MultivaluedMap}

import java.io.{OutputStream}
import java.lang.annotation.Annotation
import java.lang.reflect.Type

import java.io._
import org.apache.commons.io.output._
import org.apache.commons.io.output.{ByteArrayOutputStream => ACByteArrayOutputStream}
import org.apache.commons.io.IOUtils._

/**
 * Uses a provided view renderer to render any kind of object.
 */
@Provider
class ResourceMessageBodyWriter extends MessageBodyWriter[ResourceLike] {

  def isWriteable(clazz: Class[_], genericType: Type,
                  annotations: Array[Annotation], mediaType: MediaType): Boolean = true

  def getSize(resource: ResourceLike, clazz: Class[_], genericType: Type,
              annotations: Array[Annotation], mediaType: MediaType): Long = {
    resource.size
  }

  def writeTo(resource: ResourceLike, clazz: Class[_], genericType: Type,
              annotations: Array[Annotation], mediaType: MediaType,
              httpHeaders: MultivaluedMap[String, Object],
              out: OutputStream): Unit = {
    // TODO: write caching header
    resource.writeTo(out)
  }
}


trait Resource[A<:ResourceLike] extends ResourceLike {

  def self: A

  def map[B](f: A => B): Option[B] = {
    if(isDefined) Some(f(self)) else None
  }

  def flatMap[B](f: A => Option[B]): Option[B] = {
    if(isDefined) f(self) else None
  }

  def filter(p: A => Boolean): Option[A] = {
    if(isDefined && p(self)) Some(self) else None
  }
}

/**
 * A handle for url based resources.
 */
trait ResourceLike {
  def url: java.net.URL

  def isDefined: Boolean = url != null

  def size: Long = {
    writeTo(new CountingOutputStream(NullOutputStream.NULL_OUTPUT_STREAM)).getByteCount
  }
  def inputStream: java.io.InputStream = url.openStream

  def writeTo[A<:OutputStream](out: A): A = {
    val in = inputStream
    try {
      copy(inputStream, out)
    } finally {
      in.close
    }
    out
  }

  def bytes: Array[Byte] = writeTo(new ACByteArrayOutputStream).toByteArray

  override def toString = url.toString

  /**
   * provide the last modified time in millis if possible
   */
  def lastModified: Option[Long] = None

  /**
   * Provide the uri to identify this resource.
   */
  def uri: java.net.URI = url.toURI

  /**
   * The mimetype if it could be determined
   */
  def mimeType: Option[javax.activation.MimeType] = None
}