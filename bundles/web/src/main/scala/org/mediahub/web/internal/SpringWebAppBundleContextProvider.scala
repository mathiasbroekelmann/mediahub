package org.mediahub.web.internal

import org.mediahub.jersey.osgi.spi.container.BundleContextProvider

import javax.servlet.ServletContext

import org.osgi.framework.BundleContext

import org.springframework.osgi.web.context.support.OsgiBundleXmlWebApplicationContext.BUNDLE_CONTEXT_ATTRIBUTE

/**
 * Locates the bundle context from a spring osgi bundle application context.
 */
class SpringWebAppBundleContextProvider extends BundleContextProvider {

    def bundleContext(context: ServletContext): BundleContext = {
        context.getAttribute(BUNDLE_CONTEXT_ATTRIBUTE).asInstanceOf[BundleContext]
    }
}
