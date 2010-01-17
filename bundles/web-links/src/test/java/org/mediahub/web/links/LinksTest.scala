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

class LinksTest {

  var links: Links = _
  var context: LinkContext = _

  @Before
  def setUp {
    context = new LinkContext {
      def baseUri[A<:AnyRef](clazz: ClassManifest[A]): Option[UriBuilder] = {
        Some(UriBuilder.fromUri(URI.create("/context")))
      }

      def resolverFor[A<:AnyRef](clazz: ClassManifest[A]): Seq[LinkResolver[A]] = Seq.empty
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
}

@Path("root")
class MyRootResource {

  @Path("foo")
  def someAction = null

  @Path("foo/{id}")
  def sub(@PathParam("id") id: String) = null
}

class MySubResource(val id: String)
