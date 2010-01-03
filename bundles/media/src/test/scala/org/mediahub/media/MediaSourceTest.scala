package org.mediahub.media

import org.junit._
import org.hamcrest.CoreMatchers._
import Assert._

import java.io.File

import org.apache.commons.io.FileUtils._

import org.joda.time._
import DateTimeUtils._


class MediaSourceTest {
    
    private[this] var now: DateTime = _
    
    @Before
    def setup {
        now = new DateTime().withMillisOfSecond(0)
    }
    
    @After
    def tearDown {
        setCurrentMillisSystem
    }
    
    @Test
    def testFilesystemLastmodified {
        val latestFile = new File("target/latest.file")
        touch(latestFile)
        latestFile.setLastModified(now.getMillis)
        val mediaSource = new FilesystemMediaSource(new File("."))
        mediaSource.lastModified.map { lastModified =>
            assertThat(lastModified, is(now))
        }.getOrElse(fail("Expected at least a single last modified date time."))
    }

    @Ignore("just for manual testing a larger file base")
    @Test
    def testFilesystemLastmodifiedInFotos {
        val mediaSource = new FilesystemMediaSource(new File("/media/fotos"))
        mediaSource.lastModified.map { lastModified =>
            println(lastModified)
        }
    }

    @Test
    def testFotos {
        val mediaSource = new FilesystemMediaSource(new File("/media/fotos"))
        mediaSource.collect(new MediaCollector[Unit] {
            override def source(source: MediaSource) = {
                println(source)
                super.source(source)
            }
            
            def media(media: Media) {
                println(media)
            }
        })
    }
}
