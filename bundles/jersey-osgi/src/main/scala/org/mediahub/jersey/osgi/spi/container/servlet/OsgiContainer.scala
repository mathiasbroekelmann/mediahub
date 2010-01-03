package org.mediahub.jersey.osgi.spi.container.servlet

import com.sun.jersey.spi.container.servlet.ServletContainer
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.servlet.WebConfig;

import org.mediahub.jersey.osgi.spi.container.OsgiComponentProviderFactory

import com.google.inject.{Singleton, Inject}

import org.osgi.service.http.HttpService

import javax.ws.rs.core.Application

import org.osgi.framework.BundleContext

/**
 * A {@link Servlet} or {@link Filter} for deploying root resource classes
 * with OSGi integration.
 * <p>
 * This class will be registered using {@link HttpService} if found.
 * <p>
 * This class extends {@link ServletContainer} and initiates the
 * {@link WebApplication} with an OSGi-based {@link IoCComponentProviderFactory},
 * {@link OsgiComponentProviderFactory}, such that instances of resource and
 * provider classes declared and managed by OSGi can be obtained.
 * <p>
 * OSGi-bound services will be automatically registered if such
 * services are root resource classes, provider classes or instances of {@link Application}.
 *
 * @author Mathias Broekelmann
 */
class OsgiContainer @Inject() (bundleContext: BundleContext) extends ServletContainer {

    override protected def getDefaultResourceConfig(props: java.util.Map[String, AnyRef],
                                                    webConfig: WebConfig): ResourceConfig = {
        new DefaultResourceConfig
    }

    override protected def initiate(config: ResourceConfig, webapp: WebApplication) {
        webapp.initiate(config, new OsgiComponentProviderFactory(config, bundleContext));
    }
}
