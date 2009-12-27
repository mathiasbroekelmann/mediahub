package de.osxp.dali.media

import java.io.{File, FileFilter}

import org.joda.time.DateTime

import javax.ws.rs._
import javax.ws.rs.core._

import de.osxp.dali.page._

import de.osxp.dali.validation._
import de.osxp.dali.validation.Validate._

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
 * a persisted file system media source.
 * 
 * @author Mathias Broekelmann
 *
 * @since 27.12.2009
 *
 */
class FilesystemMediaSourceDefinition extends MediaSourceDefinition {
    var location: String = _
}

trait FilesystemMediaSourceOperations extends MediaSourceOperations {
    @POST
    @Consumes(Array(MediaType.APPLICATION_FORM_URLENCODED))
    def update(@FormParam("name") name: String, 
               @FormParam("location") location: File, 
               @Context uriInfo: UriInfo): Response = {
        //TODO: update name and location in the file system media source
        val uri = uriInfo.getAbsolutePath
        Response.ok.location(uri).build
    }
}

object LocationNotDefined extends ValidationMessage
object LocationNotExists extends ValidationMessage
object LocationNotReadable extends ValidationMessage

/**
 * Media source implementation for file system based directories.
 * 
 * @param location the base dir where the media resources will be scanned from
 * @param filter an optional filter to narrow the scanned media resources
 * @param parent an optional parent if this media source is a child of an other media source.
 */
class FilesystemMediaSource(val name: String,
                            val location: File, 
                            val filter: Option[File => Boolean], 
                            val parent: Option[FilesystemMediaSource]) 
                            extends MediaSource 
                            with FilesystemMediaSourceOperations {
    
    type Parent = FilesystemMediaSource

    def this(location: File) = this(location.getName, location, None, None)
    
    def this(name: String, location: File) = this(name, location, None, None)
    
    def this(name: String, location: File, filter: Option[File => Boolean]) = this(name, location, filter, None)

    def isValid(receiver: ValidationCollector) = (
        Validate(receiver)
            (location != null, LocationNotDefined)
            (location.exists, LocationNotExists)
            (location.canRead, LocationNotReadable)
    )
    
    private[this] val fileFilter = new FileFilter {
        def accept(file: File) = file.canRead && 
                                 filter.map(_(file)).getOrElse(true)
    }

    /**
     * Collects all media files recursively and notifies the collector.
     * Each directory that passes the defined filter is notified as a media source to the collector.
     */
    def collect[A](collector: MediaCollector[A]): Seq[A] = {
        if(isValid) {
            location.listFiles(fileFilter).view.map(file =>
                file match {
                    case IsDirectory(dir) => collector.source(new FilesystemMediaSource(dir.getName, 
                                                                                        dir, filter, 
                                                                                        Some(this)))
                    case IsFile(file) => collector.media(new FilesystemMedia(file, Some(this))) :: Nil
                }
            ).flatten
        } else {
            Seq.empty
        }
    }

    lazy val uri = location.toURI

    override lazy val description = Some("directory [" + location.getAbsolutePath + "]")

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