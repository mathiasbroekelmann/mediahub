package de.osxp.dali.page

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
    def unspecifiedAt(page: Page): Option[A] = None
    
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
 * Identifies the main content of the page.
 * 
 * @author Mathias Broekelmann
 *
 * @since 25.12.2009
 *
 */
case class PageContent[A] extends ContentOfPage[A]

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
    
    def apply: PageBuilder = new PageBuilder {}
    
    /**
     * build page with the given main content.
     */
    def apply[A<:AnyRef](contentOfPage: ContentOfPage[A]): PageContentBuilder[A] = apply.apply(contentOfPage)
    
    /**
     * 
     * @author Mathias Broekelmann
     *
     * @since 25.12.2009
     *
     */
    trait PageBuilder {
        self =>
        
        val content = scala.collection.mutable.Map[ContentOfPage[_], AnyRef]()
        
        def apply[A<:AnyRef](contentOfPage: ContentOfPage[A]) = new PageContentBuilder[A] {
            def is(value: A): PageBuilder = {
                content(contentOfPage) = value
                self
            }
        }
        
        def build: Page = 
            new Page {
                val contents = Map.empty ++ self.content
            }
        
        def title(title: String): PageBuilder = apply(PageTitle).is(title)
    }
    
    trait PageContentBuilder[A] {
        def is(value: A): PageBuilder
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
trait Page {
    
    /**
     * returns the complete contents of this page.
     */
    def contents: Map[ContentOfPage[_], AnyRef]
    
    /**
     * get the content of the page for the given content of page type.
     * 
     * @param contentOfPage the type of content in the page
     * 
     * @return None if no content is defined for this page, otherwise Some(content)
     */
    def apply[A](contentOfPage: ContentOfPage[A]): Option[A] = 
        contents.get(contentOfPage)
               .orElse(contentOfPage.unspecifiedAt(this))
               .map(_.asInstanceOf[A])

    /**
     * Convenience accessor to get the title of a page.
     */
    def title: Option[String] = this(PageTitle)
    
    /**
     * Convenience accessor to get the main content of a page.
     */
    def content: AnyRef = this(PageContent[AnyRef])
}