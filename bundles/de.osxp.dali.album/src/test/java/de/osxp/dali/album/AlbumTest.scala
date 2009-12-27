package de.osxp.dali.album

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

import de.osxp.dali.navigation._


class AlbumTest {
    @Test
    def test {
        val albums = new Alben().albums
        val contents = albums.other
        val nav = albums(Navigation)
        assertThat(nav, is(Some(Alben).asInstanceOf[Option[NavigationPointDefinition]]))
    }
}
