/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import java.io.File


object Filesystem {
  implicit def resourceOf(file: File): ResourceLike = {
    someresource(file).getOrElse(error("unknown file type: " + file))
  }

  implicit def someresource(file: File): Option[ResourceLike] = file match {
    case file if file.isFile => Some(new FileResource(file))
    case file if file.isDirectory => Some(new DirectoryResource(file))
    case _ => None
  }
}

import Filesystem._

/**
 * identifies a file system resource.
 */
trait FilesystemResource extends ResourceLike {
  def file: File
  type Parent = DirectoryResource

  def uri = file.toURI
  def exists = file.exists
  override def parent = Option(file.getParentFile).map(new DirectoryResource(_))
  override def lastModified = if(exists && file.lastModified >= 0) Some(file.lastModified) else None
}

/**
 * a directory resource wraps a file which is a directory
 */
case class DirectoryResource(val file: File) extends FilesystemResource with Container {

  type Element = ResourceLike
  type Repr = Traversable[ResourceLike]

  /**
   * return all childs of this directory including files and nested directory. this method is not recursive.
   */
  def childs = {
    file.listFiles match {
      case files: Array[File] => for(file <- files; resource <- someresource(file)) yield resource
      case _ => Nil
    }
  }

  override def toString = format("Directory %s, numberOfChilds=%d", url, childs.size)
}

/**
 * a file resource wraps a file in the local file system.
 */
case class FileResource(val file: File) extends FilesystemResource with Resource {

  override def size = if (exists) file.length else error("file " + this + " does not exist")
  override def toString = format("File %s, size=%d", url, size)
}

object FileResource {
  def apply(uri: java.net.URI): FileResource = FileResource(new File(uri))
}
