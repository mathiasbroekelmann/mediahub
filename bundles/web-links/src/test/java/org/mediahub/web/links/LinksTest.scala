/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.web.links

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

import Links._

import javax.ws.rs._
import core._

import java.net._

import java.lang.reflect._

import scala.reflect.ClassManifest._

class LinksTest {

  var links: Links = _
  var context: LinkContext = _

  @Before
  def setUp {
    context = new LinkContext {
      override def baseUri[A<:AnyRef](implicit clazz: ClassManifest[A]): Option[URI] = {
        Some(URI.create("/context"))
      }
      override def resolver = Seq(new SomeOtherLinkResolver, new AnyRefLinkResolver, new SomeLinkResolver)
    }
    links = new Links(context)
  }

  @Test
  def rootResources {
    val link: String = links.linkTo[MyRootResource]
    println(link)
    assertThat(link, is("/context/root"))
    val linkToSomeAction: String = links.linkTo[MyRootResource].action("someAction")
    println(linkToSomeAction)
    assertThat(linkToSomeAction, is("/context/root/foo"))
  }

  @Test
  def subResources {
    val link: String = links.linkTo[MySubResource].resolvedBy[MyRootResource].action("sub", "id" -> "bar")
    println(link)
    assertThat(link, is("/context/root/foo/bar"))
  }

  @Test
  def subResourcesWithHelpOfLinkResolver {
    val link: String = links.linkTo(new MySubResource("baz"))
    println(link)
    assertThat(link, is("/context/root/foo/baz"))
  }

  @Test
  def testResolveGenericType {
    val resolver = Seq(new SomeLinkResolver, new SomeOtherLinkResolver, new AnyRefLinkResolver)
    val validResolvers = Links.typeOf(resolver, classOf[LinkResolver[_]], fromClass(classOf[MySubResource]).erasure)

    println("resolvers: " + validResolvers)
  }
}

class AnyRefLinkResolver extends LinkResolver[AnyRef] {
  def apply(target: AnyRef, builder: ResourceLinkBuilder): Option[LinkBuilder] =
    None
}

class SomeOtherLinkResolver extends LinkResolver[String] {
  def apply(target: String, builder: ResourceLinkBuilder): Option[LinkBuilder] =
    None
}

class SomeLinkResolver extends LinkResolver[MySubResource] {
  def apply(target: MySubResource, builder: ResourceLinkBuilder): Option[LinkBuilder] =
    Some(builder.resolvedBy[MyRootResource].action("sub", "id" -> target.id))
}

@Path("root")
class MyRootResource {

  @Path("foo")
  def someAction = null

  @Path("foo/{id}")
  def sub(@PathParam("id") id: String) = null
}

class MySubResource(val id: String)

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

object Foo {
  val root = new RootResource
  val sub = new SubResource

  val html =
  <p>
    <a href={linkTo(root)}>Link to a root resource with path /path</a>
    <a href={linkTo(root).action(_.get("foo"))}>Link to a root resource with path /path/foo</a>
    <a href={linkTo(sub)}>Link to a sub resource. If no link resolver is registered that is capable of resolving a path to such resource a runtime error is thrown.</a>
    <a href={linkTo(sub).fragment("divid")}>Link to a sub resource with a defined fragment id. If no link resolver is registered that is capable of resolving a path to such resource a runtime error is thrown.</a>
    <a href={linkTo(sub).resolvedBy[RootResource]}>Link to a sub resource by explicitly defining the root resource.</a>
    <a href={linkTo(sub).action(_.getSub("bar"))
                        .resolvedBy[RootResource]
                        .action(_.get("foo"))}>Link to a sub resource resolved by explicitly defining the root resource with path /path/foo/bar</a>
  </p>
}