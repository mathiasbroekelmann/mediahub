/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.cache

import org.junit._
import Assert._
import org.hamcrest.Matchers._

import scala.util.Random._

class CacheStrategiesTest {

  val key = new CacheKey[String] {
    def compute = "foo"
  }
  
  @Before
  def setUp {
  }

  @After
  def tearDown {
  }

  @Test
  def testSeqSlice {
    val values = Seq(1, 2, 3, 4)
    assertThat(values.slice(0, 2), is(Seq(1, 2)))
  }

  @Test
  def testCacheKeyMetrics {
    val metrics = CacheKeyMetrics(key)
    import metrics._
    assertThat(hits, is(0L))
    assertThat(misses, is(0L))
    assertThat(computations, is(0L))
  }

  @Test
  def testCacheKeyComputations {
    val metrics = CacheKeyMetrics(key).withMaxComputationTimes(100).computed(90).computed(410)
    val computedMetrics = (1 to 30).foldLeft(metrics)((m, x) => m.computed(nextInt(300) + 100))
    import computedMetrics._
    assertThat(hits, is(0L))
    assertThat(misses, is(0L))
    assertThat(computations, is(32L))
    val stats = computedMetrics.stats
    assertThat(stats.min, is(90L))
    assertThat(stats.max, is(410L))
    assert(stats.average < 400)
    assert(stats.average > 100)
    println(computedMetrics)
  }
}
