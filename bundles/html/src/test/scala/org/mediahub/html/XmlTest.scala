/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.html

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

import scala.xml._

import Xml._

class XmlTest {


  @Test
  def spike {
    val withTitle = <link title={Some("foo")} />
    assertThat((withTitle \ "@title").toString, is("foo"))
    
    val none: Option[String] = None
    val woTitle = <link title={none} />
    assertThat(woTitle \ "@title", is(NodeSeq.Empty))
  }
}