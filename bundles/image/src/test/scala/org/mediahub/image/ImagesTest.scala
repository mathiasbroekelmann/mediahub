/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mediahub.image

import org.junit._
import Assert._
import org.junit.matchers.JUnitMatchers._
import org.hamcrest.CoreMatchers._

import javax.activation.MimeType

import java.io.File

import org.mediahub.resources._
import Filesystem._

import org.mediahub.util.Dates._

import org.mediahub.jcr.JcrDsl._

import org.apache.sanselan._
import org.apache.sanselan.common._
import org.apache.sanselan.formats.tiff.constants.TiffTagConstants
import org.apache.sanselan.formats.jpeg.JpegImageMetadata
import org.apache.sanselan.formats.tiff.constants.ExifTagConstants
import org.apache.sanselan.formats.jpeg._

import org.mediahub.util.Dates._
import org.joda.time._
import org.joda.time.format.DateTimeFormat

import javax.jcr.{Session => JcrSession}
import javax.jcr.SimpleCredentials
import javax.jcr.{Repository, Node}
import Repository._

import org.apache.jackrabbit.core.TransientRepository

object RichSanselan {

  private lazy val dateformat = DateTimeFormat.forPattern("yyyy:MM:dd HH:mm:ss");

  implicit def jpegmetadataToRichMetadata(meta: JpegImageMetadata) = new {
    

    def creationDate: Option[DateTime] = {
      try {
        for(field <- Option(meta.findEXIFValue(ExifTagConstants.EXIF_TAG_CREATE_DATE));
            value <- Option(field.getValue))
              yield {
            dateformat.parseDateTime(value.toString.trim)
          }
      } catch {
        case ex: IllegalArgumentException => None
      }
    }
  }
}

/**
 * scan a directory recursivly
 * collect all images
 * resolve metadata on each image (width, heigth to start with)
 * persist these data into the jcr repository by using the directory structure as initial structure.
 */
class ImagesTest {

  var session: JcrSession = _
  var repository: Repository = _
  var node: Node = _

  @Before
  def setUp: Unit = {
    //repository = new TransientRepository(new File("target/jcr"))
    //session = login("someuser", "password")
    //node = session.getRootNode() |= "test"
  }

  @After
  def tearDown: Unit = {
    //node.remove
    //session.save
    //session.logout
  }

  def login(username: String, password: String) = {
    val credentials = new SimpleCredentials(username, password.toCharArray)
    repository.login(credentials)
  }

  @Test
  def example {
    val base = new File(System.getProperty("user.home") + "/Bilder")
    for(image <- imagesRecursively(None)(base)) {
      val time = System.currentTimeMillis
      println(image + ", time taken: " + (System.currentTimeMillis - time) + " ms")
    }
  }

  implicit def richDirectoryResource(dir: DirectoryResource) = new {
    def childOf(givenParent: Option[Album]) = new Album {
      def name = dir.name
      override def lastModified = for(date <- dir.lastModified) yield (date)
      override def parent = givenParent
    }
  }
  
  def imagesRecursively(album: Option[Album])(resource: ResourceLike): Traversable[(Option[Album], Image)] = {
    println("imagesRecursively" + album + ", " + resource)
    resource match {
      case dir: DirectoryResource => dir.childs.toStream.flatMap(imagesRecursively(Some(dir.childOf(album))))
      case resource: Resource => for(image <- maybeImage(resource)) yield (album, image)
      case other => Seq.empty
    }
  }

  def maybeImage(res: Resource): Option[Image] = {
    
    def image = new Image {
      import RichSanselan._
      /**
       * run the given function if the image info could be resolved.
       */
      def withImageInfo[A](f: ImageInfo => A): Option[A] = {
        res.read { in =>
          try {
            Some(f(Sanselan.getImageInfo(in, res.uri.toString)))
          } catch {
            // TODO: log error or provide some reporting
            case ex: ImageReadException => None
          }
        }
      }

      /**
       * run the given function if the image info could be resolved.
       */
      def withMetadata[A](pf: PartialFunction[IImageMetadata, Option[A]]): Option[A] = {
        res.read { in =>
          try {
            val metadata = Sanselan.getMetadata(in, res.uri.toString)
            if(pf.isDefinedAt(metadata)) {
              pf(metadata)
            } else {
              None
            }
          } catch {
            // TODO: log error or provide some reporting
            case ex: ImageReadException => None
          }
        }
      }

      override lazy val dimension = withImageInfo { info =>
        Dimension(info.getWidth, info.getHeight)
      }

      override lazy val createdAt = withMetadata {
        case jpeg: JpegImageMetadata => jpeg.creationDate
      }

      lazy val resource = res

      override def toString = "image " + resource + ", " + dimension + ", createdAt: " + createdAt
    }

    if(mimeType(res.name).getPrimaryType == "image") {
      Some(image)
    } else {
      None
    }
  }

  def mimeType(fileName: String): MimeType = {
    val map = new javax.activation.MimetypesFileTypeMap
    new MimeType(map.getContentType(fileName))
  }
}

trait Image {
  def resource: Resource
  def dimension: Option[Dimension] = None
  def createdAt: Option[DateTime] = None
}

case class Dimension(width: Int, height: Int)

trait Album {
  /**
   * the name of the album
   */
  def name: String

  /**
   * the path from the root album down to this album
   */
  def path: String = (parent match {
    case None => Seq(name)
    case Some(p) => name +: p.pathArcs
  }).reverse.mkString("/")

  /**
   * @return the path arcs having this album name as the last element (top->bottom)
   */
  def pathArcs: Seq[String] = (parent match {
    case None => Seq(name)
    case Some(p) => name +: p.pathArcs
  }).reverse

  /**
   * @return the parents of this album from top to bottom
   */
  def parents: Seq[Album] = (parent match {
    case None => Nil
    case Some(p) => p +: p.parents
  }).reverse

  /**
   * the parent of the album. None if there is no parent.
   */
  def parent: Option[Album] = None

  def lastModified: Option[DateTime] = None

  override def toString = "Album at " + path
}