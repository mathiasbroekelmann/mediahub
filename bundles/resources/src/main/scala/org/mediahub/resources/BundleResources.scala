/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import org.osgi.framework.{Bundle, BundleContext}

import org.mediahub.web.links._

import javax.activation.MimeType

import javax.ws.rs.{Path, GET, PathParam}
import javax.ws.rs.core.{Response, Request, Context}
import Response._
import Response.Status._

import Resource._

/**
 * Identifies a bundle resource.
 */
case class BundleResource(val bundle: Bundle, val location: String) extends Resource {
  type Self = BundleResource
  def self = this
  override def url = bundle.getEntry(location)
  override def toString = "bundle: " + bundle + ", location: " + location
  // TODO: find a way to determine the last modified date of the resource in the bundle
  override def lastModified = Some(bundle.getLastModified)
}

/**
 * defines supporting functions for working with bundle resources
 */
object BundleResources {
  /**
   * Make a bundle resource from a location in a given bundle.
   *
   * Use it like this:
   *<pre>
   * import org.mediahub.resources.BundleResources._
   *
   * def bundle = ...
   * def resource = "/path/to/resource.ext" in bundle
   *</pre>
   */
  implicit def bundleResource(location: String) = new {

    /**
     * create a bundle resource from a bundle.
     */
    def in(bundle: Bundle) = BundleResource(bundle, location)

    /**
     * create a bundle resource from a bundle context.
     */
    def in(bundleContext: BundleContext): BundleResource = in(bundleContext.getBundle)
  }
}

/**
 * R root REST resource for locating bundle resources.
 */
@Path("bundle/resource")
class BundleResources {

  /**
   * the bundle context is used to determine the bundle for a resource.
   */
  def bundleContext: BundleContext = error("bundle context is not defined")

  /**
   * provide the content types service which is used to determine the content type of a resource
   */
  def contentTypes: ContentTypes = error("bundle context is not defined")

  /**
   * get the requested resource.
   *
   * @param bundleId the id of the bundle which contains the resource
   * @param location the location inside the bundle of the resource.
   * @param request the request is used to determine if the resource was modified since the last request.
   *
   * @return the response containing the StreamingOutput of the resource if it could be found or a response with the http status 404.
   */
  @Path("{bundleId}/{location:.*}")
  @GET
  def get(@PathParam("bundleId") bundleId: Long,
          @PathParam("location") location: String,
          @Context request: Request): Response = {

    /**
     * create a bundle resource.
     */
    def bundleResource(bundle: Bundle): BundleResource = {
      new BundleResource(bundle, location) {
        override def mimeType = Some(new MimeType(contentTypes.contentType(location)))
      }
    }

    /**
     * locate the resource
     */
    def resource: Option[Resource] = {
      
      /**
       * locate the bundle for the given id
       */
      def bundle: Option[Bundle] = {
        Option(bundleContext.getBundle(bundleId))
      }
      
      bundle match {
        case Some(bundle) => Some(bundleResource(bundle))
        case None => None
      }
    }

    resource match {
      case Some(resource) if resource.isDefined => resource on(request) build
      case _ => status(NOT_FOUND).build
    }
  }

}

/**
 * a link resolver for bundle resources
 */
class BundleResourceLinkResolver extends LinkResolver[BundleResource] {

  /**
   * if the resource exists a link will be build for it.
   */
  def apply(target: BundleResource, builder: ResourceLinkBuilder): Option[LinkBuilder] = {
    for(resource <- target) yield {
      val bundleId = long2Long(resource.bundle.getBundleId)
      builder.resolvedBy[BundleResources].action("get", bundleId, resource.location)
    }
  }
}
