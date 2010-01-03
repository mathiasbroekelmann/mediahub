package org.mediahub.media

import com.google.inject._

import javax.ws.rs._
import javax.ws.rs.core._
import Response.Status._

import org.mediahub.page._

import org.mediahub.persistence._

/**
 * Identifies a type of a media source.
 */
abstract case class MediaSourceType[A<:MediaSource] {
    
    /**
     * create a new instance of media source definition for the given name.
     */
    def create(name: String): MediaSourceDefinition[A]
}

/**
 * the persistent entity of a media source.
 * 
 * @author Mathias Broekelmann
 *
 * @since 29.12.2009
 *
 */
trait MediaSourceDefinition[A<:MediaSource] extends PersistedEntity {
    val name: String
    
    /**
     * if that definition is able to create a valid media source it will return Some otherwise None.
     */
    def apply: Option[A]
    
    /**
     * Convenience method to execute a given function in the context of a valid media source instance.
     */
    def apply[B](f: A => B): Option[B] = apply.map(f)
}

@Path("media/sources")
trait MediaSourcesOperations {
    
    @GET
    def show = Page(this).build
    
    /**
     * Provide a list of all media sources
     */
    def sources: Seq[MediaSourceDefinition[_]]
    
    /**
     * start the creation of a new media source for the given type.
     */
    @POST
    def create(@FormParam("name") name: String,
               @FormParam("type") mediaSourceType: MediaSourceType[MediaSource],
               @Context uriInfo: UriInfo): Response

    @Path("{name}")
    def get(@PathParam("name") name: String): Option[MediaSourceDefinition[_]]
}

/**
 * contract to access persisted media sources
 * 
 * @author Mathias Broekelmann
 *
 * @since 29.12.2009
 *
 */
trait MediaSourceDefinitionDao {

    def persist(definition: MediaSourceDefinition[_])

    def findAll: Seq[MediaSourceDefinition[_]]

    def byName(name: String): Option[MediaSourceDefinition[_]]
}

/**
 * 
 * @author Mathias Broekelmann
 *
 * @since 29.12.2009
 *
 */
trait MediaSources extends MediaSourcesOperations {
    
    val dao: MediaSourceDefinitionDao
    
    /**
     * Create a raw uninitialized media source.
     */
    def create(name: String,
               mediaSourceType: MediaSourceType[MediaSource],
               uriInfo: UriInfo): Response = {
        val mediaSource = mediaSourceType.create(name)
        val uri = uriInfo.getAbsolutePathBuilder.path(classOf[MediaSourcesOperations], "get").build(name)
        Response.created(uri).build
    }
    
    /**
     * Provide a list of all media sources.
     */
    def sources = dao.findAll

    /**
     * Locate a single media source instance.
     */
    def get(name: String) = dao.byName(name)
}

/**
 * mixin trait for operations on media sources.
 */
trait MediaSourceOperations[A<:MediaSource] extends MediaSourceDefinition[A] {
    /**
     * get the page containing this media source as the main content
     */
    @GET
    def get = Page(this.asInstanceOf[MediaSourceDefinition[A]]).build
}