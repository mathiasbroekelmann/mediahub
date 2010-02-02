/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import org.osgi.framework.{Bundle, BundleContext}

import org.mediahub.web.links._

import javax.ws.rs.{Path, GET, PathParam}
import javax.ws.rs.core.{Response}
import Response._
import Response.Status._

import java.io._
import org.apache.commons.io.output._
import org.apache.commons.io.IOUtils._

object Resources {
  implicit def bundleResource(location: String) = new {
    def from(bundle: Bundle) = BundleResource(bundle, location)
    def from(bundleContext: BundleContext) = BundleResource(bundleContext.getBundle,
                                                            location)
  }
}

/**
 * a root rest resource for locating bundle resources
 */
@Path("resource/bundle")
abstract class BundleResources {
  def bundleContext: BundleContext

  /**
   *
   */
  @Path("{bundleId}/${location:.*}")
  @GET
  def get(@PathParam("bundleId") bundleId: Long, @PathParam("location") location: String): Response = {
    val bundle = Option(bundleContext.getBundle(bundleId))
    bundle.map(b => ok(BundleResource(b, location)).build)
          .getOrElse(status(NOT_FOUND).build)
  }
}

/**
 * A handle for url based resources.
 */
trait Resource {
  def url: java.net.URL
  
  def exists: Boolean = url != null
  def size: Long = {
    writeTo(new CountingOutputStream(NullOutputStream.NULL_OUTPUT_STREAM)).getByteCount
  }
  def inputStream: java.io.InputStream = url.openStream
  def writeTo[A<:OutputStream](out: A): A = {
    copy(inputStream, out)
    out
  }
  def bytes: Array[Byte] = toByteArray(inputStream)
  override def toString = url.toString
  
  def lastModified: Option[Long] = None
  def uri: java.net.URI = url.toURI
  def mimeType: Option[javax.activation.MimeType] = None
}

/**
 * a bundle resource.
 */
case class BundleResource(bundle: Bundle, location: String) extends Resource {
  override def url = bundle.getEntry(location)
  override def toString = "bundle: " + bundle + ", location: " + location
  // TODO: find a way to determine the last modified date of the resource in the bundle
  override def lastModified = Some(bundle.getLastModified)
}

class BundleResourceLinkResolver extends LinkResolver[BundleResource] {
  def apply(target: BundleResource, builder: ResourceLinkBuilder): Option[LinkBuilder] = {
    None
  }
}
