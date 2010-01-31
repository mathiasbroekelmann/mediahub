/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.cache

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

class CacheTest {

  var cache: Cache = _

  @Before
  def setUp {
    cache = new CacheImpl
  }

  @Test
  def testCachedValueWithDependency {
    val key = MyCacheKey("foo")
    val result = cache.get(key)
    assertThat(result, is("foo1"))
    val result2 = cache.get(key)
    assertThat(result2, is("foo1"))
    cache.invalidate(MyDependency())
    val result3 = cache.get(key)
    assertThat(result3, is("foo2"))
  }

  @Test
  def testCachedValueWithNestedCachedValueAndDependency {
    val key = MyParentCacheKey(cache)
    val result = cache.get(key)
    assertThat(result, is("foo1bar1"))
    val result2 = cache.get(key)
    assertThat(result2, is("foo1bar1"))
    cache.invalidate(MyDependency())
    val result3 = cache.get(key)
    assertThat(result3, is("foo2bar2"))
    cache.invalidate(MyParentDependency())
    val result4 = cache.get(key)
    assertThat(result4, is("foo3bar2"))
  }
}

case class MyParentDependency extends Dependency

case class MyParentCacheKey(cache: Cache) extends CacheKey[String] {
  var computeCalled = 0
  val subKey = MyCacheKey("bar")
  def compute = {
    computeCalled += 1
    Cache.dependsOn(MyParentDependency())
    "foo" + computeCalled + cache.get(subKey)
  }
}

case class MyDependency extends Dependency

case class MyCacheKey(value: String) extends CacheKey[String] {
  var computeCalled = 0

  def compute = {
    computeCalled += 1
    Cache.dependsOn(MyDependency())
    value + computeCalled
  }
}
