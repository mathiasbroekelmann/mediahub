/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

import Filesystem._

import java.io._

import javax.jcr.Repository
import Repository._
import javax.jcr.{Session => JcrSession}
import javax.jcr.SimpleCredentials
//import javax.jcr.{Node, Value, PropertyType}
import org.apache.jackrabbit.core.TransientRepository

import scala.collection.JavaConversions._

import ResourceTracking._

import org.mediahub.jcr.JcrDsl._

class TrackingTest {

  @Test
  def testCollect {
    val resources = recursivly(new File("/media/fotos")) // new File(System.getProperty("user.home") + "/Bilder")
    val base = session.getRootNode |= "resources"
    val resourcesWithNode = collect(resources).into(base)
    val start = System.currentTimeMillis
    for ((resource, node) <- resourcesWithNode) {
      //println("node: " + node + ", resource: " + resource)
    }
    println("time for collecting resources " + resourcesWithNode.size + ": " + (System.currentTimeMillis - start) + " ms")
    session.save
  }

  @Test
  def testCollectedResources {
    val base = session.getRootNode |= "resources"
    val resourcesWithNode = resources.from(base) {
      case uri if uri.getScheme == "file" => FileResource(uri)
    }
    val start = System.currentTimeMillis
    for ((resource, node) <- resourcesWithNode) {
      //println("collected node: " + node + ", resource: " + resource)
    }
    println("time for traversing resources " + resourcesWithNode.size + ": in store: " + (System.currentTimeMillis - start) + " ms")
  }

  def recursivly(resource: ResourceLike): Traversable[Resource] = {
    resource match {
      case dir: DirectoryResource => dir.childs.toStream.flatMap(recursivly)
      case resource: Resource => Seq(resource)
      case other => Seq.empty
    }
  }

  private var session: JcrSession = _
  private var repository: Repository = _

  @Before
  def setUp {
    repository = new TransientRepository(new File("target/jcr"))
    session = login("someuser", "password")
  }

  @After
  def tearDown {
    session.logout
  }

  def login(username: String, password: String) = {
    val credentials = new SimpleCredentials(username, password.toCharArray)
    repository.login(credentials)
  }
}
