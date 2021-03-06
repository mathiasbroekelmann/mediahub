package org.mediahub.page

import org.apache.commons.lang.ClassUtils._

/**
 * Defines a type of content in a page.
 * 
 * @author Mathias Broekelmann
 *
 * @since 25.12.2009
 *
 */
abstract case class ContentOfPage[+A] {
    /**
     * Optionally provide a value for this content type if it is not defined for this type on the given page.
     * By default this method returns None
     */
    def unspecifiedAt(page: Page[_]): Option[A] = None
    
    override def toString: String = {
        getShortClassName(getClass)
    }
    
    override def equals(other: Any): Boolean = {
        other match {
            case that: ContentOfPage[_] => 
                (that canEqual this) && that.getClass == getClass
            case _ => false
        }
    }
    
    override def hashCode: Int = {
        getClass.hashCode
    }
}

/**
 * Identifies the title of the page.
 * 
 * @author Mathias Broekelmann
 *
 * @since 26.12.2009
 *
 */
object PageTitle extends ContentOfPage[String]

/**
 * Complement object to build the page content.
 * 
 * @author Mathias Broekelmann
 *
 * @since 25.12.2009
 *
 */
object Page {
    
    /**
     * start building a page with the given main content.
     */
    def apply[A<:AnyRef](contentOfPage: A): PageBuilder[A] = new PageBuilder[A] {
        val content = contentOfPage
    }
    
    /**
     * 
     * @author Mathias Broekelmann
     *
     * @since 25.12.2009
     *
     */
    trait PageBuilder[A] {
        self =>
        
        val content: A
        
        val other = Map[ContentOfPage[_], AnyRef]()
        
        def mainContent[A<:AnyRef](main: A) = new PageBuilder[A] {
            val content = main
        }
        
        def apply[B<:AnyRef](contentOfPage: ContentOfPage[B]) = new PageContentBuilder[A, B] {
            def is(value: B): PageBuilder[A] = new PageBuilder[A] {
                val content = self.content
                override val other = self.other(contentOfPage) = value
                self
            }
        }
        
        def build: Page[A] = 
            new Page[A] {
                val content = self.content
                val other = Map.empty ++ self.other
            }
        
        def title(title: String): PageBuilder[A] = self(PageTitle).is(title)
    }
    
    trait PageContentBuilder[A, B] {
        def is(value: B): PageBuilder[A]
    }
}

/**
 * A page contains the content of a single page.
 * 
 * @author Mathias Broekelmann
 *
 * @since 26.12.2009
 *
 */
trait Page[A] {
    
    /**
     * Get the main content of a page.
     */
    def content: A
    
    /**
     * returns the complete contents of this page.
     */
    def other: Map[ContentOfPage[_], AnyRef]
    
    /**
     * get the content of the page for the given content of page type.
     * 
     * @param contentOfPage the type of content in the page
     * 
     * @return None if no content is defined for this page, otherwise Some(content)
     */
    def apply[A](contentOfPage: ContentOfPage[A]): Option[A] = 
        other.get(contentOfPage)
             .orElse(contentOfPage.unspecifiedAt(this))
             .map(_.asInstanceOf[A])

    /**
     * Convenience accessor to get the title of a page.
     */
    def title: Option[String] = this(PageTitle)
}