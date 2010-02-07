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
    for(image <- imagesRecursively(new File("/media/fotos"))) {
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
            def withBufferedImage[A](f: java.awt.image.BufferedImage => A) = {
              val bImage = javax.imageio.ImageIO.read(res.inputStream)
              try {
                f(bImage)
              } finally {
                bImage.flush
              }
            }

            lazy val dimension: (Int, Int) = {
              val in = res.inputStream
              try {
                val info = Sanselan.getImageInfo(res.inputStream, res.uri.toString)
                (info.getWidth, info.getHeight)
              } finally {
                in.close
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