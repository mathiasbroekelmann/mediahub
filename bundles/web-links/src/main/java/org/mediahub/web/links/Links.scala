/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.aperto.sandbox.scala

import java.net.URI
import javax.ws.rs._
import core._
import scala.xml._

trait UriScheme[A] {
  def resolveUri(target: A): Option[URI]
}

class ResourceUriScheme(@Context() uriInfo: UriInfo) extends UriScheme[AnyRef] {

  def resolveUri(target: AnyRef): Option[URI] = None
}

@Path("path")
class RootResource {
  @GET
  def get = None

  @Path("{id}")
  def get(@PathParam("id") id: String) = None
}

//@ResolvedBy(classOf[MyResource])
class SubResource {
  @GET
  def getSub = None

  @GET
  @Path("{id}")
  def getSub(@PathParam("id") id: String) = None
}

object Test {
  val root = new RootResource
  val sub = new SubResource

  val html = <p>
                <a href={linkTo(root)}>Link to a root resource with path /path</a>
                <a href={linkTo(root).action(_.get("foo"))}>Link to a root resource with path /path/foo</a>
                <a href={linkTo(sub)}>Link to a sub resource. If no link resolver is registered that is capable of resolving a path to such resource a runtime error is thrown.</a>
                <a href={linkTo(sub).fragment("divid")}>Link to a sub resource with a defined fragment id. If no link resolver is registered that is capable of resolving a path to such resource a runtime error is thrown.</a>
                <a href={linkTo(sub).resolvedBy[RootResource]}>Link to a sub resource by explicitly defining the root resource.</a>
                <a href={linkTo(sub).action(_.getSub("bar"))
                                    .resolvedBy[RootResource]
                                    .action(_.get("foo"))}>Link to a sub resource resolved by explicitly defining the root resource with path /path/foo/bar</a>
             </p>

  def linkTo[A](target: A): FragmentResourceActionLinkBuilder[A] = null
}

object LinkBuilder {
  implicit def linkBuilderToNodeSeq(builder: LinkBuilder): NodeSeq = {
    // TODO: implement logic to build a Seq[Node] from a link builder
    Text(builder.build.toString)
  }
}

/**
 * a link resolver is used to resolve the resource that is used to determine a sub resource.
 */
trait LinkResolver[A] {

  /**
   * resolve the link to the given target instance.
   */
  def resolveLinkTo(target: A, builder: ResourceLinkBuilder[A]): LinkBuilder
}

trait LinkBuilder {
  /**
   * build the actual uri.
   */
  def build: URI
}

trait FragmentResourceActionLinkBuilder[A] extends ResourceActionLinkBuilder[A] {
  /**
   * define the fragment of the link.
   */
  def fragment(fragment: String): ResourceActionLinkBuilder[A]
}

trait ResourceActionLinkBuilder[A] extends ResourceLinkBuilder[A] {
  /**
   * specify the action (method) that is used to resolve the resource. The actual method is not executed.
   * Only the passed parameters where collected to build the link.
   * @param call call the method with the parameters that should be used to build the link.
   * Use null for any @Context annotated or other uri unrelated parameters.
   * It is also possible to pass null for uri related parameters that have a given default value.
   */
  def action(call: (A => Any)): ResourceLinkBuilder[A]
}

trait ResourceLinkBuilder[A] extends LinkBuilder {

  /**
   * Specify the resource class which is capable to resolve the resource.
   */
  def resolvedBy[B](implicit clazz: ClassManifest[B]): ResourceActionLinkBuilder[B]
}