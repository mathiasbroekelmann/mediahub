/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import org.mediahub.web.links._

import javax.activation.MimeType

import java.io._
import java.net.{URI, URL}
import org.apache.commons.io.output._
import org.apache.commons.io.output.{ByteArrayOutputStream => ACByteArrayOutputStream}
import org.apache.commons.io.IOUtils._

import javax.ws.rs.core.{Response, Request, Context}
import Response._
import Response.Status._

import org.mediahub.util.Dates._

trait ResourceLike {

  type Parent

  /**
   * optionally provide the parent.
   */
  def parent: Option[Parent] = None

  /**
   * Provide the uri to identify this resource.
   */
  def uri: URI
  
  /**
   * The name of the resource.
   * Which is the part after the last slash in the path of the uri.
   */
  def name: String = uri.getPath.split('/').last

  /**
   * returns true if the resource exists
   */
  def exists: Boolean

  /**
   * the url of the resource
   */
  def url: URL = uri.toURL

  /**
   * optionally provide the time in millis since 1st jan 1970 when this resource was last modified.
   */
  def lastModified: Option[Long]

  /**
   * compare the uri of the resources.
   * If overriden in subclasses you must also override #canEqual
   */
  override def equals(other: Any) = other match {
    case r: ResourceLike if r.canEqual(this) => r.uri == uri
    case _ => false
  }

  /**
   * delegates to the hashcode of the uri
   */
  override def hashCode = uri.hashCode

  /**
   * overwrite in subclasses if #equals is overriden.
   */
  def canEqual(other: Any) = other.isInstanceOf[ResourceLike]
}

/**
 * Identifies a resource
 */
trait Resource extends ResourceLike {

  /**
   * returns the size of the resource
   */
  def size: Long = {
    writeTo(new CountingOutputStream(NullOutputStream.NULL_OUTPUT_STREAM)).getByteCount
  }

  /**
   * returns the inputstream of the resource.
   */
  def inputStream: java.io.InputStream = url.openStream

  def read[A](f: (java.io.InputStream => A)): A = {
    val in = inputStream
    try {
      f(in)
    } finally {
      in.close
    }
  }

  /**
   * write the contents of the resource to the given outputstream.
   * The given input is returned for convienience.
   */
  def writeTo[A<:OutputStream](out: A): A = {
    val in = inputStream
    try {
      copy(inputStream, out)
    } finally {
      in.close
    }
    out
  }

  /**
   * the contents of the resource as a byte array.
   */
  def bytes: Array[Byte] = writeTo(new ACByteArrayOutputStream).toByteArray

  /**
   * The mimetype if it could be determined
   */
  def mimeType: Option[javax.activation.MimeType] = None
}

/**
 * just a container which has childs
 */
trait Container extends ResourceLike {

  type Element
  type Repr <: Traversable[Element]

  /**
   * provide the childs of this container
   */
  def childs: Repr
}

/**
 * companion object for resources.
 */
object Resource {

  /**
   * implicit conversion of a resource to a StreamingOutput
   */
  implicit def resourceToStreamingOutput(resource: Resource) = new javax.ws.rs.core.StreamingOutput {
    /**
     * convienience method to create an instanceof javax.ws.rs.core.StreamingOutput
     */
    def asStreamingOutput = this

    /**
     * actually write the resource to the given outputstream
     */
    def write(out: OutputStream) = resource.writeTo(out)
  }

  /**
   * build a response for a given resource.
   */
  implicit def respond(resource: Resource) = new {

    /**
     * build the response for the given request of this resource.
     */
    def on(request: Request): ResponseBuilder = {
      /**
       * evaluate conditional request by checking the last modified date.
       */
      def evaluatePreconditions(lastModified: java.util.Date): ResponseBuilder = {
        (Option(request.evaluatePreconditions(lastModified)) match {
            case Some(rb) => rb
            case None => ok(resource.asStreamingOutput)
          }).lastModified(lastModified)
      }

      /**
       * use mimetype if defined.
       */
      def withMimeType(rb: ResponseBuilder): ResponseBuilder = {
        resource.mimeType match {
          // since type is a keyword in scala we have to escape it here.
          case Some(mt) => rb.`type`(mt.toString)
          case None => rb
        }
      }

      /**
       * evaluate conditional request if the resource defines a last modified date.
       */
      val responseBuilder = resource.lastModified match {
        case Some(millis) => evaluatePreconditions(millis)
        case None => ok(resource.asStreamingOutput)
      }

      withMimeType(responseBuilder)
    }
  }
}