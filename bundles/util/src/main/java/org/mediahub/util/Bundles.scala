/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.util

import org.osgi.framework.{Bundle}

/**
 * Provide functions releated to bundles.
 */
object Bundles {

  /**
   * Resolve a bundle by its symbolic name.
   * 
   * Locating a bundle via its symbolic name is sometimes necessary if for example a resource is should
   * be used from that bundle where it is known to provide that resource.
   *
   * To get the bundle with the symbolic name "org.mediahub.html" use this:
   * <pre>
   * import org.mediahub.util.Bundles._
   * val ctx: BundleContext = ...
   * "org.mediahub.html" resolvedBy ctx match {
   *   case Some(bundle) => doSomethingWith(bundle)
   *   case None => error("bundle not found")
   * }
   * // more explicit:
   * val bundle: Option[Bundle] = bundle("org.mediahub.html") resolvedBy ctx
   * </pre>
   */
  implicit def bundle(bundleSymbolicName: String) = new {

    /**
     * abstract type for a bundle context which returns an array of bundles.
     */
    type BundleContext = {
      def getBundles: Array[Bundle]
    }

    /**
     * search the bundle for the symbolic name in the given bundle context.
     *
     * @param bundleContext the context to locate the bundle from.
     * @return Some(bundle) if found otherwise None
     */
    def resolvedBy(bundleContext: BundleContext): Option[Bundle] = {
      bundleContext.getBundles.find { bundle =>
        bundle.getSymbolicName == bundleSymbolicName
      }
    }
  }
}