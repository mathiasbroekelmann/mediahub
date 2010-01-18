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
      override def baseUri[A<:AnyRef](implicit clazz: ClassManifest[A]): Option[UriBuilder] = {
        Some(UriBuilder.fromUri(URI.create("/context")))
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