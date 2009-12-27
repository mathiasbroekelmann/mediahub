package de.osxp.dali.album

import org.joda.time.DateTime
import javax.ws.rs._
import core.MediaType._

import scala.collection.immutable.Map

import de.osxp.dali.navigation._
import de.osxp.dali.page._

object Alben extends NavigationPointDefinition(Root)

@Path("/")
class Alben {
    
    @GET
	def albums: Page[Seq[Album]] = {
	    // TODO: find all albums
	    Page(Seq.empty[Album])(Navigation).is(Alben).title("Alben").build
	}
	
    @GET
	@Path("{path:.+}")
	def album(@PathParam("path") path: String): Page[Album] = {
	    // TODO: find a single album for a given path
        val album: Album = null
	    var page = Page(album)(Navigation).is(Alben)
	    page(PageTitle).is("foo")
	    page.build
	}
}

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