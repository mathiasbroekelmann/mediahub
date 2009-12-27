package de.osxp.dali.media

import java.io.{File, FileFilter}

import org.joda.time.DateTime

import javax.ws.rs._

import de.osxp.dali.page._

@Path("media/source/filesystem")
trait FilesystemMediaSourceResource {
    
    @GET
    def mediaSources: Page[Seq[FilesystemMediaSource]] = {
        null
    }
}

/**
 * directory extractor.
 */
object IsDirectory {
    def unapply(file: File): Option[File] = if(file.isDirectory) Some(file) else None
}

/**
 * file extractor.
 */
object IsFile {
    def unapply(file: File): Option[File] = if(file.isFile) Some(file) else None
}

/**
 * Media source implementation for file system based directories.
 * 
 * @param basedir the base dir where the media resources will be scanned from
 * @param filter an optional filter to narrow the scanned media resources
 * @param parent an optional parent if this media source is a child of an other media source.
 */
class FilesystemMediaSource(val basedir: File, 
                            val filter: Option[File => Boolean], 
                            val parent: Option[FilesystemMediaSource]) extends MediaSource {
    
    assert(basedir != null, "basedir must be defined for a file system based media source.")
    
    type Parent = FilesystemMediaSource

    def this(basedir: File) = this(basedir, None, None)
    
    def this(basedir: File, filter: Option[File => Boolean]) = this(basedir, filter, None)

    private[this] val fileFilter = new FileFilter {
        def accept(file: File) = file.canRead && 
                                 filter.map(_(file)).getOrElse(true)
    }

    /**
     * Collects all media files recursively and notifies the collector.
     * Each directory that passes the defined filter is notified as a media source to the collector.
     */
    def collect[A](collector: MediaCollector[A]): Seq[A] = {
        if(basedir.exists && basedir.canRead) {
            basedir.listFiles(fileFilter).view.map(file =>
                file match {
                    case IsDirectory(dir) => collector.source(new FilesystemMediaSource(dir, filter, Some(this)))
                    case IsFile(file) => collector.media(new FilesystemMedia(file, Some(this))) :: Nil
                }
            ).flatten
        } else {
            Seq.empty
        }
    }
    
    lazy val name = basedir.getName
    
    lazy val uri = basedir.toURI
    
    override lazy val description = Some("directory [" + basedir.getAbsolutePath + "]")

    override lazy val toString = "directory [" + url.toString + "]"

    override def canEqual(other: Any) = other.isInstanceOf[FilesystemMediaSource]
}

/**
 * A file system based media resource.
 */
class FilesystemMedia(val file: File, val parent: Option[FilesystemMediaSource]) extends Media {
    
    type Parent = FilesystemMediaSource
    
    lazy val uri = file.toURI
    
    lazy val name = file.getName
    
    override lazy val lastModified = {
        if(file.lastModified > 0) { 
            Some(new DateTime(file.lastModified)) 
        } else {
            None
        }
    }
    override lazy val size = Some(file.length)
    
    override lazy val description = Some("file [" + file.getAbsolutePath + "]")
}