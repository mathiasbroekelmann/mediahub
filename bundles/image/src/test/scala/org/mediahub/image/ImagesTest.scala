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

import org.apache.sanselan._
import org.apache.sanselan.common._

/**
 * scan a directory recursivly
 * collect all images
 * resolve metadata on each image (width, heigth to start with)
 * persist these data into the jcr repository by using the directory structure as initial structure.
 */
class ImagesTest {

  @Before
  def setUp {
  }

  @After
  def tearDown {
  }

  @Test
  def example {
    for(image <- imagesRecursively(new File("/media/fotos/2010"))) {
      val time = System.currentTimeMillis
      println(image + ", time taken: " + (System.currentTimeMillis - time) + " ms")
    }
  }

  def imagesRecursively(resource: ResourceLike): Traversable[Image] = {
    resource match {
      case dir: DirectoryResource => dir.childs.toStream.flatMap(imagesRecursively)
      case resource: Resource => image(resource).toIterable
      case other => Seq.empty
    }
  }

  def image(res: Resource): Option[Image] = {
    if(mimeType(res.name).getPrimaryType == "image") {
      val in = res.inputStream
      try {
        Some(new Image {
            lazy val dimension: (Int, Int) = {
              res.read { in =>
                val info = Sanselan.getImageInfo(in, res.uri.toString)
                (info.getWidth, info.getHeight)
              }
            }
            
            lazy val resource = res
            
            lazy val width = dimension._1
            lazy val height = dimension._2

            override def toString = "image " + resource + ", width: " + width + ", height: " + height
          })
      } finally {
        in.close
      }
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
  def width: Int
  def height: Int
}

import org.apache.sanselan.formats.tiff.constants.TiffTagConstants
import org.apache.sanselan.formats.jpeg.JpegImageMetadata

object RichSanselan {
  implicit def metadataToRichMetadata(meta: IImageMetadata) = new {

    def resolution = meta match {
      case jpeg: JpegImageMetadata => jpeg.findEXIFValue(TiffTagConstants.TIFF_TAG_XRESOLUTION)
    }

    def date = meta match {
      case jpeg: JpegImageMetadata => jpeg.findEXIFValue(TiffTagConstants.TIFF_TAG_XRESOLUTION)
    }
  }
}