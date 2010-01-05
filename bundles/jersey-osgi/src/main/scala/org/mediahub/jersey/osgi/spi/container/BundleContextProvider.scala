package org.mediahub.jersey.osgi.spi.container

import org.osgi.framework.BundleContext

import javax.servlet.ServletContext

/**
 * Contract to resolve the bundle context.
 */
trait BundleContextProvider {
    
    /**
     * Provide the bundle context. Implementations may use the provided servlet context to locate the bundle context.
     */
    def bundleContext(context: ServletContext): BundleContext
}