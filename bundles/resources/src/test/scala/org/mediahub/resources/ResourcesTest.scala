/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

import java.io._

class ResourcesTest {

  @Before
  def setUp: Unit = {
  }

  @After
  def tearDown: Unit = {
  }

  @Test
  def example {

    def show(resource: ResourceLike) = resource match {
      case r: Resource => println("Resource: " + r)
      case c: Container => println("Container: " + c)
      case other => println("unknown: " + other)
    }

    val root = new DirectoryResource(new File("."))
    val childs = root.childs
    for (resource <- childs)
      yield(show(resource))
  }

  @Test
  def testScalaBasics {
    val abc = 'a' to 'z'
    assertThat(abc.mkString, is("abcdefghijklmnopqrstuvwxyz"))
    val abcMod = abc.filterNot("jgw".contains(_))
    assertThat("/css/styles.css".split('/').last, is("styles.css"))
    assertThat("/css/".split('/').last, is("css"))
  }
}