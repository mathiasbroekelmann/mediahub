package org.mediahub.album

import com.google.inject.{AbstractModule, Provider}
import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.util.Attributes.names
import org.ops4j.peaberry.Peaberry.service
import javax.ws.rs.ext.{MessageBodyWriter}
import org.mediahub.page.{Page}

import org.osgi.framework._

/**
 * album service module.
 */
class AlbumOsgiModule extends AbstractModule {
    def configure {
    }
}