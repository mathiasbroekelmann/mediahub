package de.osxp.dali.resteasy;

import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;

/**
 * @author Mathias Broekelmann
 *
 * @since 02.01.2010
 *
 */
public class WebModule extends ServletModule {
    @Override
    protected void configureServlets() {
        bind(ResteasyJaxRsDispacher.class).in(Scopes.SINGLETON);
        serve("/*").with(ResteasyJaxRsDispacher.class);
    }
}
