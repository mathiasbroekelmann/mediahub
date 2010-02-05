/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.util

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

import Bundles._

import org.osgi.framework.Bundle

import org.mockito.Mockito._

class BundlesTest {

  @Test
  def findNoBundle {
    "bundle.not.available" resolvedBy ctx match {
      case Some(bundle) => fail("expected to find no bundle")
      case None => // expected result
    }
  }

  @Test
  def findSomeBundle {
    bundle(bundleName) resolvedBy ctx match {
      case Some(bundle) => assertThat(bundle, is(bundleToFind))
      case None => fail("expected to find some bundle")
    }
  }

  def ctx = new {
    def getBundles: Array[Bundle] = Array(bundleMock("foo.bar"), bundleToFind)
  }

  val bundleName = "bundle.to.find"

  val bundleToFind = bundleMock(bundleName)
  
  def bundleMock(symbolicName: String): Bundle = {
    val bundle = mock(classOf[Bundle], symbolicName)
    when(bundle.getSymbolicName).thenReturn(symbolicName)
    when(bundle.toString).thenReturn(symbolicName)
    bundle
  }
}
