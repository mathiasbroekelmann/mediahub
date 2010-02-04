/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import org.mediahub.web.links._

import javax.activation.MimeType

import java.io.{OutputStream}

import java.io._
import org.apache.commons.io.output._
import org.apache.commons.io.output.{ByteArrayOutputStream => ACByteArrayOutputStream}
import org.apache.commons.io.IOUtils._

import javax.ws.rs.core.{Response, Request, Context}
import Response._
import Response.Status._

/**
 * Identifies a resource
 */
trait Resource {

  /**
   * defines the concrete type of the resource
   */
  type Self <: Resource

  /**
   * provide the concrete typesafe instance of the resource.
   */
  protected def self: Self

  /**
   * The url of the resource.
   */
  def url: java.net.URL

  /**
   * transform this resource to something else if it is defined.
   */
  def map[B](f: Self => B): Option[B] = {
    if(isDefined) Some(f(self)) else None
  }

  /**
   * transform this resource to something else if it is defined.
   */
  def flatMap[B](f: Self => Option[B]): Option[B] = {
    if(isDefined) f(self) else None
  }

  /**
   * evaluates given predicate if this resource is defined. if predicate resolves true this is returned.
   */
  def filter(p: Self => Boolean): Option[Self] = {
    if(isDefined && p(self)) Some(self) else None
  }

  /**
   * Returns true if the url is defined, otherwise false.
   */
  def isDefined: Boolean = url != null

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
   * the string representation of the resource.
   */
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
        case Some(date) => evaluatePreconditions(new java.util.Date(date))
        case None => ok(resource.asStreamingOutput)
      }

      withMimeType(responseBuilder)
    }
  }
}