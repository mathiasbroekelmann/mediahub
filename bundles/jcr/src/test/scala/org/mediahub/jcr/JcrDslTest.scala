/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.jcr

import org.junit._
import Assert._

import org.hamcrest.CoreMatchers._

import javax.jcr.Repository
import Repository._
import javax.jcr.{Session => JcrSession}
import javax.jcr.SimpleCredentials
import javax.jcr.{Node, Value, PropertyType}
import org.apache.jackrabbit.core.TransientRepository

import scala.collection.JavaConversions._

import scala.collection.mutable.ListBuffer

import java.io.File

import JcrDsl._

class JcrTest {

  private var session: JcrSession = _
  private var repository: Repository = _

  @Before
  def setUp: Unit = {
    repository = new TransientRepository(new File("target/jcr"))
    session = login("someuser", "password")
  }

  @After
  def tearDown: Unit = {
    session.logout
  }

  def login(username: String, password: String) = {
    val credentials = new SimpleCredentials(username, password.toCharArray)
    repository.login(credentials)
  }

  @Test
  def justReporting {
    println("Logged in as " + session.getUserID + " to a " + repository.name + " repository.")
  }

  @Test
  def testNodeSpike {
    val root = session.getRootNode
    val hello = root.addNode("hello")
    val world = hello.addNode("world")
    world.setProperty("message", "Hello, World!")
    session.save

    val node = root.getNode("hello/world")
    println(node.getPath)
    println(node.getProperty("message").getString)

    root.getNode("hello").remove
    session.save
  }

  @Test
  def testJcrDslWithRecursiveNodeCreation {
    val root = session.getRootNode

    root |= "path/to/some/node"

    assertThat(root.hasNode("path/to/some/node"), is(true))
  }

  @Test
  def testJcrDsl {
    val root = session.getRootNode

    (root |= "somepath") |= { n =>
      n + ("someString" -> "Hello World!")
      n + ("someInt" -> 42)
      n + ("someBoolean" -> true)
    }

    val node = root.getNode("somepath")
    assertThat(node, is(notNullValue[Node]))
    assertThat(node.getProperty("someString").getString, is("Hello World!"))
    assertThat(node.getProperty("someInt").getLong, is(42L))
    assertThat(node.getProperty("someBoolean").getBoolean, is(true))
  }

  @Test
  def testJcrDsl2 {
    val root = session.getRootNode

    (root |= "somepath") ++ ("someString" -> "Hello World!",
                             "someInt" -> 42,
                             "someBoolean" -> true,
                             "weekdays" -> List("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"))

    val node = root.getNode("somepath")
    assertThat(node, is(notNullValue[Node]))
    assertThat(node.getProperty("someString").getString, is("Hello World!"))
    assertThat(node.getProperty("someInt").getLong, is(42L))
    assertThat(node.getProperty("someBoolean").getBoolean, is(true))
    val weekdaysProperty = node.getProperty("weekdays")
    assertThat(weekdaysProperty.isMultiple, is(true))
    assertThat(weekdaysProperty.getValues.map(_.getString), is(Array("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")))
  }

  @Test
  def testJcrDslPropertiesFromMap {
    val root = session.getRootNode

    val mapOfProperties = Map("key" -> "value", "otherKey" -> 42)

    (root |= "somepath") ++ mapOfProperties

    val node = root.getNode("somepath")
    assertThat(node.getProperty("key").getString, is("value"))
    assertThat(node.getProperty("otherKey").getLong, is(42L))
  }

  @Test
  def testAccessPropertyValue {
    val root = session.getRootNode

    (root |= "somepath") ++ ("string" -> "Hello World!",
                             "int" -> 42)


    session.save
    session.logout
    val readsession = login("someotheruser", "password")
    try {
      println("reading properties")
      val node = readsession.getNode("/somepath")
      assertThat(node("string").getString, is("Hello World!"))
      assertThat(node("int").getLong, is(42L))
      node.remove
      readsession.save
      println("finished reading properties")
    }finally {
      readsession.logout
    }
  }
}

class DependencyTrackingAccessManager extends org.apache.jackrabbit.core.security.simple.SimpleAccessManager {

  override def isGranted(absPath: org.apache.jackrabbit.spi.Path, permissions: Int) = {
    val granted = super.isGranted(absPath, permissions)
    if (granted && ((permissions | org.apache.jackrabbit.core.security.AccessManager.READ) != 0)) {
      val pathAsString = absPath.getElements.map(_.getName.getLocalName).reduceLeft(_ + "/" + _)
      println("computation depends on: " + pathAsString)
      org.mediahub.cache.Cache.dependsOn(new PathAccess(pathAsString))
    }
    granted
  }
}

case class PathAccess(path: String) extends org.mediahub.cache.Dependency