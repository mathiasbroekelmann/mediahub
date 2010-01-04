package org.mediahub.album

import org.joda.time.DateTime
import javax.ws.rs._
import core.MediaType._

import scala.collection.immutable.Map

import org.mediahub.navigation._
import org.mediahub.page._

@Path("alben")
trait AlbenOperations {
    @GET
    def albums: Page[Seq[Album]]

    @GET
    @Path("{path:.+}")
    def album(@PathParam("path") path: String): Page[Album]
}

class Alben extends AlbenOperations {
    
	def albums: Page[Seq[Album]] = {
	    // TODO: find all albums
	    Page(Seq.empty[Album])(Navigation).is(Album).title("Alben").build
	}
	
	def album(path: String): Page[Album] = {
	    // TODO: find a single album for a given path
        val album: Album = null
	    var page = Page(album)(Navigation).is(Album)
	    page(PageTitle).is("foo")
	    page.build
	}
}

object Album extends NavigationPointDefinition(Root)

trait Album extends Container[Element]  {
    override type ParentType<:Album
}

trait Element {
	type ParentType<:Container[Element]
	
	/**
	 * the name of the element
	 */
	def name: String
	
	/**
	 * the parent if available
	 */
	def parent: Option[ParentType]
	
	/**
	 * date time when this element was created.
	 */
	def createdAt: DateTime
}

/**
 * A container has elements.
 * 
 * @author Mathias Broekelmann
 *
 * @since 25.12.2009
 *
 */
trait Container[A<:Element] extends Element {
    
    /**
     * the elements in this container
     */
	def elements: Seq[A]
}