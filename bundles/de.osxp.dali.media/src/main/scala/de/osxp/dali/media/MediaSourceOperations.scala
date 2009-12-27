package de.osxp.dali.media

import com.google.inject._

import javax.ws.rs._
import javax.ws.rs.core._
import Response.Status._

import de.osxp.dali.page._

@Path("media/sources")
trait MediaSourcesOperations {
    
    @GET
    def show = Page(this).build
    
    /**
     * Provide a list of all media sources
     */
    def sources: Seq[MediaSource]
    
    /**
     * start the creation of a new media source for the given type.
     */
    @POST
    @Consumes(Array(MediaType.APPLICATION_FORM_URLENCODED))
    def create(@FormParam("name") name: String,
               @FormParam("type") msType: String,
               @Context uriInfo: UriInfo): Response

    @Path("{name}")
    def get(@PathParam("name") name: String): Option[MediaSource]
}

trait MediaSourceDao {
    def byName(name: String): Option[MediaSource]
    
    def findAll: Seq[MediaSource]
}

trait MediaSourceTypeRegistry {
    def resolveType(msType: String): Option[MediaSourceType[MediaSource]]
}

/**
 * Identifies a type of a media source.
 */
abstract case class MediaSourceType[A<:MediaSource] {
    def create(name: String): A
}


class MediaSources @Inject() (dao: MediaSourceDao, registry: MediaSourceTypeRegistry) extends MediaSourcesOperations {
    def create(name: String,
               msType: String,
               uriInfo: UriInfo): Response = {
        val response = registry.resolveType(msType)
                               .map(_.create(name))
                               .map { source =>
            val uri = uriInfo.getAbsolutePathBuilder.path(classOf[MediaSourcesOperations], "get").build(name)
            Response.created(uri)
        }
        response.getOrElse(Response.status(BAD_REQUEST)).build
    }
    
    def sources = dao.findAll

    def get(name: String): Option[MediaSource] = dao.byName(name)
}

trait MediaSourceOperations {
    @GET
    def get = Page(this).build
}