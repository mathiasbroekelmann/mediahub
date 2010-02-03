/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import org.osgi.framework.{Bundle, BundleContext}

import org.mediahub.web.links._

import javax.ws.rs.{Path, GET, PathParam}
import javax.ws.rs.core.{Response, Request, Context}
import javax.activation.MimeType

import Response._
import Response.Status._

/**
 * Identifies a bundle resource.
 */
case class BundleResource(val bundle: Bundle, val location: String) extends Resource[BundleResource] {
  def self = this
  override def url = bundle.getEntry(location)
  override def toString = "bundle: " + bundle + ", location: " + location
  // TODO: find a way to determine the last modified date of the resource in the bundle
  override def lastModified = Some(bundle.getLastModified)
}

object BundleResources {
  /**
   * Make a bundle resource from a location in a given bundle.
   *
   * Use it like this:
   *<pre>
   * import org.mediahub.resources.BundleResources._
   *
   * def bundle = ...
   * def resource = "/path/to/resource.ext" from bundle
   *</pre>
   */
  implicit def bundleResource(location: String) = new {
    def from(bundle: Bundle) = BundleResource(bundle, location)
    def from(bundleContext: BundleContext): BundleResource = from(bundleContext.getBundle)
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
  def bundleContext: Option[BundleContext] = None

  /**
   * provide the content types service which is used to determine the content type of a resource
   */
  def contentTypes: Option[ContentTypes] = None

  /**
   * get the requested resource.
   *
   * @param bundleId the id of the bundle which contains the resource
   * @param location the location inside the bundle of the resource.
   * @param request the request is used to determine if the resource was modified since the last request.
   */
  @Path("{bundleId}/{location:.*}")
  @GET
  def get(@PathParam("bundleId") bundleId: Long,
          @PathParam("location") location: String,
          @Context request: Request): Response = {

    // locate the bundle
    def bundle(bundleId: Long) = {
      for(bc <- bundleContext;
          bundle <- Option(bc.getBundle(bundleId)))
            yield bundle
    }

    /**
     * create a bundle resource.
     */
    def bundleResource(bundle: Bundle, location: String): BundleResource = {
      new BundleResource(bundle, location) {
        override def mimeType = contentTypes.map(ct => new MimeType(ct.contentType(location)))
      }
    }

    // locate the resource
    def resource = {
      for(bundle <- bundle(bundleId);
          resource <- bundleResource(bundle, location))
            yield (resource)
    }

    val response = for(someResource <- resource;
                       lastModified <- someResource.lastModified) yield {
      val lastModifiedDate = new java.util.Date(lastModified)
      // check last modified date
      val builder = request.evaluatePreconditions(lastModifiedDate)
      if(builder == null) {
        // resource was modified since last request
        val entityBuilder = ok(someResource)
        // use `` here since type is a keyword in scala but is a valid java identifier
        someResource.mimeType.map(mimeType => entityBuilder.`type`(mimeType.toString)).getOrElse(entityBuilder)
      } else {
        // resource was not modified
        builder.lastModified(lastModifiedDate)
      }
    }
    // if resource not found reply with 404 state.
    response.getOrElse(status(NOT_FOUND)).build
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
