package de.osxp.dali.album

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

import de.osxp.dali.navigation._


class AlbumTest {
    @Test
    def test {
        val albums = Alben.albums
        val contents = albums.contents
        val nav = albums(Navigation)
        assertThat(nav, is(Some(Alben).asInstanceOf[Option[NavigationPointDefinition]]))
    }
}
