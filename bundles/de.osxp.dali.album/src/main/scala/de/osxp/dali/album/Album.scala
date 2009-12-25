package de.osxp.dali.album

import org.joda.time.DateTime
import javax.ws.rs._
import core.MediaType._

import scala.collection.immutable.Map

import de.osxp.dali.navigation._
import de.osxp.dali.page._

@Path("album")
object Album {
	
	@GET
	def albums: Seq[Album] = {
	    // TODO: find all albums
		Seq.empty
	}
	
	@Path("{path:.*}")
	def album(@PathParam("path") path: String): Option[Page] = {
	    // TODO: find a single album for a given path
	    val navpoint = new NavigationPointDefinition(Root)
	    var page = Page(Navigation, navpoint)
	    Some(page.build)
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