package de.osxp.dali.frontend.html.desktop

import javax.ws.rs._
import ext.{Provider, MessageBodyWriter}
import core.{MediaType, MultivaluedMap}
import MediaType.{APPLICATION_XHTML_XML, TEXT_HTML}

import java.io.{OutputStream, IOException, OutputStreamWriter}
import java.lang.annotation.Annotation
import java.lang.reflect.Type

import de.osxp.dali.page.{Page, ContentOfPage}

import scala.xml._

/**
 * message body writer for instances of page.
 * 
 * @author Mathias Broekelmann
 *
 * @since 22.12.2009
 *
 */
@Provider
@Produces(Array(TEXT_HTML))
class DesktopHtmlPageWriter extends MessageBodyWriter[Page[_]] {

    def isWriteable(clazz: Class[_], genericType: Type,
            annotations: Array[Annotation], mediaType: MediaType): Boolean = {
        classOf[Page[_]].isAssignableFrom(clazz)
    }
    
    def getSize(page: Page[_], clazz: Class[_], genericType: Type,
            annotations: Array[Annotation], mediaType: MediaType): Long = {
        -1
    }
    
    def writeTo(page: Page[_], clazz: Class[_], genericType: Type,
            annotations: Array[Annotation], mediaType: MediaType,
            httpHeaders: MultivaluedMap[String, Object],
            out: OutputStream): Unit = {
        import page._
        val html = 
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
                    <title>{title.getOrElse("Dali - Web Gallery")}</title>
                    <link href="/css/styles.css" media="screen" rel="stylesheet" type="text/css" />
                </head>
                <body>
                    <div id="header">
                        <h2>HEAD</h2>
                        <div class="nav">
                            <ul>
                                <li>
                                    <a href="#">Home</a>
                                </li>
                                <li class="active">
                                    <a href="#">Alben</a>
                                </li>
                                <li>
                                    <a href="#">Timeline</a>
                                </li>
                                <li>
                                    <a href="#">Kalender</a>
                                </li>
                                <li>
                                    <a href="#">Verwaltung</a>
                                </li>
                            </ul>
                        </div>
                    </div>
                    <!-- header end -->
                    <div id="menu">
                        <h3>MENU</h3>
                        <div class="nav">
                            <ul>
                                <li>
                                    <a href="#">2007</a>
                                </li>
                                <li>
                                    <a href="#">2008</a>
                                </li>
                                <li>
                                    <a href="#"><strong>2009</strong></a>
                                    <ul>
                                        <li>
                                            <a href="#">zu Hause</a>
                                        </li>
                                        <li class="active">
                                            <a href="#"><strong>Urlaub</strong></a>
                                        </li>
                                    </ul>
                                </li>
                            </ul>
                        </div>
                    </div>
                    <!-- end left -->
                    <div id="sidebar">
                        <h3>SIDEBAR</h3>
                        <div class="teaser">
                            <h4>Teaser</h4>
                        </div>
                        <div class="teaser">
                            <h4>Teaser</h4>
                        </div>
                        <div class="teaser">
                            <h4>Teaser</h4>
                        </div>
                    </div>
                    <!-- end right -->
                    <div id="content">
                        <h2>CONTENT</h2>
                        <p>
                            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Duis id hendrerit metus. Fusce in consequat magna. Pellentesque sollicitudin dolor quis arcu hendrerit molestie. Maecenas hendrerit vulputate tellus. Integer velit odio, mollis et tempor a, malesuada at nisl. Fusce lacinia rutrum orci, ornare pretium lectus fringilla vitae. Sed interdum nisi luctus dui ullamcorper egestas. Duis eget ligula urna, at porttitor urna. Quisque et ultricies est. Aliquam ac tellus vel nunc pellentesque euismod.
                        </p>
                    </div>
                     <!-- end content -->
                    <div id="footer">
                        <h4>FOOTER</h4>
                        <p>
                            <img src="/image/xhtml10.gif" alt="" width="80" height="15" border="0" />
                            <br/>
                            <img src="/image/css.gif" alt="css" width="80" height="15" border="0" />
                        </p>
                    </div><!-- end footer -->
                </body>
            </html>
        val writer = new OutputStreamWriter(out)
        XML.write(writer, 
                  html, 
                  "UTF-8", 
                  true,
                  new dtd.DocType("html", 
                                  new dtd.PublicID("-//W3C//DTD XHTML 1.0 Strict//EN", 
                                                   "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"), 
                                  Seq.empty))
        writer.flush
    }
}