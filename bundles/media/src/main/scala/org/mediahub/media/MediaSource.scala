package org.mediahub.media

import java.net.{URL, URI}

import org.joda.time.DateTime

import org.apache.commons.lang.builder._

import org.mediahub.validation._
import org.mediahub.persistence._

/**
 * Defines a resource with common properties.
 */
trait Resource {

    /**
     * Define the type of the parent resource
     */
    type Parent <: Resource
    
    /**
     * Optionally a parent of this resource.
     */
    def parent: Option[Parent]
    
    /**
     * the url to refer to this resource.
     */
    def url: URL = uri.toURL
    
    /**
     * the uri of the resource.
     */
    def uri: URI
    
    /**
     * Name of the resource
     */
    def name: String

    /**
     * Description of the resource.
     */
    def description: Option[String] = None

    /**
     * Returns a date at which the resource was last modified.
     */
    def lastModified: Option[DateTime] = None

    override def hashCode = new HashCodeBuilder().append(url).toHashCode

    override def equals(other: Any): Boolean = other match {
        case that: Resource => (that canEqual this) && new EqualsBuilder().append(url, that.url).isEquals
        case _ => false
    }
    
    def canEqual(other: Any) = other.isInstanceOf[Resource]
}


/**
 * A media source defines the contract for various sources.
 */
trait MediaSource extends Resource {
    
    type Parent <: MediaSource

    /**
     * Actually collect all medias and nested media sources into the given media collector.
     */
    def collect[A](collector: MediaCollector[A]): Seq[A]
    
    override def lastModified: Option[DateTime] = {
        val lastModifiedCollector = new MediaCollector[Option[DateTime]] {
            def media(media: Media): Option [DateTime] = media.lastModified
        }
        
        def lastModifieds = for(option <- collect(lastModifiedCollector); 
                                lastModified <- option)
                            yield (lastModified)
        val sorted = lastModifieds.sortWith(_.compareTo(_) > 0)
        sorted.headOption
    }

    override def toString = "mediasource [" + url.toString + "]"

    override def canEqual(other: Any) = other.isInstanceOf[MediaSource]
    
    def isValid: Boolean = isValid(new ValidationCollector {
        def report(message: ValidationMessage) {}
    })
    
    def isValid(collector: ValidationCollector): Boolean
}

/**
 * a media identifies an resource which is a single media like an image or video file.
 */
trait Media extends Resource {
    
    type Parent <: MediaSource
    
    /**
     * the content type (if known) of the media.
     */
    def contentType: Option[String] = None
    
    /**
     * the size (if known) of the media.
     */
    def size: Option[Long] = None

    override def toString = "media [" + url.toString + "]"

    override def canEqual(other: Any) = other.isInstanceOf[Media]
}

/**
 * A media collector collects the media and possibly nested media sources.
 */
trait MediaCollector[+A] {
    
    /**
     * notify the collector about a nested media source. By default a media collector will traverse the given media source.
     * Override this method if traversing the media source should be modified in some way.
     */
    def source(source: MediaSource): Seq[A] = source.collect(this)
    
    /**
     * notify the collector about a single media instance.
     */
    def media(media: Media): A
}