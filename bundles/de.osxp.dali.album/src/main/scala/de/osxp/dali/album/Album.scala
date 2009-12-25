package de.osxp.dali.album

import org.joda.time.DateTime
import javax.ws.rs._
import core.MediaType._

import scala.collection.immutable.Map

import de.osxp.dali.navigation._

abstract case class ContentOfPage[A] {
	def unspecifiedAt(page: Page): Option[A] = None
}

object Content extends ContentOfPage[AnyRef]

object Navigation extends ContentOfPage[NavigationPoint]

object Page extends PageBuilderOperations

trait PageBuilderOperations {
	
	val parts = scala.collection.mutable.Map[ContentOfPage[Any], Any]()
	
	def update[A>:Any](part: ContentOfPage[A], value: A) = parts(part) = value
	
	def apply[A>:Any](part: ContentOfPage[A]): A = parts(part)
	
	def newBuilder[A>:Any](contentOfPage: ContentOfPage[A], value: Seq[A]): PageBuilder = {
		val newParts = parts + (contentOfPage -> value)
		new PageBuilder {
			override val parts = newParts
		}
	}
}

trait PageBuilder extends PageBuilderOperations {
	self =>
	
	def build: Page = {
		new Page {
			val content = Map.empty ++ self.parts
			
			def apply[A>:Any](contentOfPage: ContentOfPage[A]): Option[A] = 
				content.get(contentOfPage)
					   .orElse(contentOfPage.unspecifiedAt(this))
		}
	}
}

trait Page {
	
	/**
	 * get the content of the page for the given content of page type.
	 * 
	 * @param contentOfPage the type of content in the page
	 * 
	 * @return None if no content is defined for this page, otherwise Some(content)
	 */
	def apply[A>:Any](contentOfPage: ContentOfPage[A]): Option[A]
}

@Path("album")
object Album {
	
	@GET
	def albums: Seq[Album] = {
		Seq.empty
	}
	
	@Path("{path:.*}")
	def album(@PathParam("path") path: String): Option[Album] = {
		None
	}
}

trait Album extends Container[Element]  {
}

trait Element {
	type ParentType
	
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

trait Container[A<:Element] extends Element {
	def elements: Seq[A]
}