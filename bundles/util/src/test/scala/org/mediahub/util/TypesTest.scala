/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.util

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

import Types._

import com.google.inject.util.Types.newParameterizedType

class TypesTest {

  @Test
  def testTypesWithRootClass {
    val actual = (classOf[WithRootClass] hierarchy).toList
    val expected = List[Class[_]](classOf[WithRootClass], classOf[Root], classOf[ScalaObject], classOf[ParentClass], classOf[Parent], classOf[Object])
    assertThat(actual, is(expected))
  }

  @Test
  def testTypesWithSubClass {
    val actual = (classOf[WithSubClass] hierarchy) toList
    val expected = List[Class[_]](classOf[WithSubClass], classOf[Sub], classOf[Root], classOf[ScalaObject], classOf[ParentClass], classOf[Parent], classOf[Object])
    assertThat(actual, is(expected))
  }

  @Test
  def testParameterizedTypeFilter {
    val services = List(RootService(), InheritedService(), SubService(), ParentService())
    // we need the parameterized type which is used to identify the typed services.
    val pt = classOf[SomeService[_]] withTypeArguments classOf[Root]
    val parialServices = services.flatMap (implementedFor[SomeService[Root]](pt))
    assertThat(parialServices, is(RootService() :: InheritedService() :: List.empty[SomeService[Root]]))
  }

}

trait SomeService[A]

case class RootService extends SomeService[Root]
case class InheritedService extends RootService
case class SubService extends SomeService[Sub]
case class ParentService extends SomeService[Parent]

trait Root
trait Sub extends Root
trait Parent
class ParentClass extends Parent
class WithRootClass extends ParentClass with Root
class WithSubClass extends ParentClass  with Sub with Root