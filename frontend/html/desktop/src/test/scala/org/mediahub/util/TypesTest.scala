/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.util

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

import org.mediahub.util.Types._

class TypesTest {

    @Before
    def setUp {
    }

    @After
    def tearDown {
    }

    @Test
    def testTypesWithRootClass {
      val actual = typesOf(classOf[WithRootClass]).toList
      val expected = List[Class[_]](classOf[WithRootClass], classOf[Root], classOf[ScalaObject], classOf[ParentClass], classOf[Parent], classOf[Object])
      assertThat(actual, is(expected))
    }

    @Test
    def testTypesWithSubClass {
      val actual = typesOf(classOf[WithSubClass]).toList
      val expected = List[Class[_]](classOf[WithSubClass], classOf[Root], classOf[Sub], classOf[ScalaObject], classOf[ParentClass], classOf[Parent], classOf[Object])
      assertThat(actual, is(expected))
    }
}

trait Root
trait Sub extends Root
trait Parent
class ParentClass extends Parent
class WithRootClass extends ParentClass with Root
class WithSubClass extends ParentClass with Root with Sub