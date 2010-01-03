package org.mediahub.frontend.resources

import java.io.File

import com.google.inject.Inject

import javax.activation.FileTypeMap

/**
 * Resolves the content type from file sources
 * 
 * @author Mathias Broekelmann
 *
 * @since 26.12.2009
 *
 */
trait ContentTypes {
    
    /**
     * Resolve the content type of the given file.
     */
    def contentType(file: File): String = contentType(file.getName)
    
    /**
     * Resolve the content type of a file by it's name.
     */
    def contentType(filename: String): String
}

class FileTypeMapContentTypes @Inject() (delegate: FileTypeMap) extends ContentTypes {
    def contentType(filename: String): String = delegate.getContentType(filename)
}