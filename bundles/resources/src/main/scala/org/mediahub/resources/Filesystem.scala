/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.resources

import java.io.File


object Filesystem {
  /**
   * make a ResourceLike instance for a system file.
   */
  implicit def fileToResource(file: File): FilesystemResource = file match {
    case file if file.isFile => new FileResource(file)
    case file if file.isDirectory => new DirectoryResource(file)
    case _ => error("unknown file type")
  }
}

/**
 * identifies a file system resource.
 */
trait FilesystemResource extends ResourceLike {
  def file: File
  type Parent = DirectoryResource

  def uri = file.toURI
  def exists = file.exists
  def name = file.getName
  override def parent = Option(file.getParentFile).map(new DirectoryResource(_))
  override def lastModified = if(exists) Some(file.lastModified) else None
}

/**
 * a directory resource wraps a file which is a directory
 */
case class DirectoryResource(val file: File) extends FilesystemResource with Container {

  type Element = ResourceLike
  type Repr = Iterable[ResourceLike]

  /**
   * return all childs of this directory including files and nested directory. this method is not recursive.
   */
  def childs = {
    def resourceOf(file: File): ResourceLike = Filesystem.fileToResource(file)
    file.listFiles match {
      case files: Array[File] => for(file <- files) yield resourceOf(file)
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
