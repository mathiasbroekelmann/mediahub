package org.mediahub.resources

import java.io.File

import javax.activation.{FileTypeMap, MimeType}

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
  def contentType(location: String): String

  def mimeType(file: File): MimeType = new MimeType(contentType(file))

  def mimeType(location: String): MimeType = new MimeType(contentType(location))
}