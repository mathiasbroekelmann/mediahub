package de.osxp.dali.page

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
}

/**
 * Identifies the main content of the page.
 * 
 * @author Mathias Broekelmann
 *
 * @since 25.12.2009
 *
 */
case class PageContent[A<:Any] extends ContentOfPage[A]

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
     * build page with the given main content.
     */
    def apply[A<:Any](pageContent: A): PageBuilder = apply(PageContent[A], pageContent)
    
    /**
     * build a page with a specific type of content.
     */
    def apply[A<:Any, B<:ContentOfPage[A]](contentOfPage: B, value: A): PageBuilder = {
        val page = new PageBuilder {}
        page(contentOfPage) = value
        page
    }

    /**
     * 
     * @author Mathias Broekelmann
     *
     * @since 25.12.2009
     *
     */
    trait PageBuilder {
        self =>
        
        val content = scala.collection.mutable.Map[ContentOfPage[Any], Any]()
        
        def update[A<:Any, B<:ContentOfPage[A]](contentOfPage: B, value: A): PageBuilder = {
            content(contentOfPage) = value
            this
        }
        
        def build: Page = {
            new Page {
                val content = Map.empty ++ self.content
                
                def apply[A>:Any, B<:ContentOfPage[A]](contentOfPage: B): Option[A] = 
                    content.get(contentOfPage)
                           .orElse(contentOfPage.unspecifiedAt(this))
            }
        }
    }
}

trait Page {
    
    def content: Map[ContentOfPage[Any], Any]
    
    /**
     * get the content of the page for the given content of page type.
     * 
     * @param contentOfPage the type of content in the page
     * 
     * @return None if no content is defined for this page, otherwise Some(content)
     */
    def apply[A>:Any, B<:ContentOfPage[A]](contentOfPage: B): Option[A]
}