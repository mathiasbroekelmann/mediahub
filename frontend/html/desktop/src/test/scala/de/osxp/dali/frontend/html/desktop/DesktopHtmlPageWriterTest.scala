package org.mediahub.frontend.html.desktop

import java.io.{OutputStream, IOException, OutputStreamWriter}

import org.junit._
import org.junit.Assert._
import org.hamcrest.CoreMatchers._

import scala.xml._

class DesktopHtmlPageWriterTest {

    @Test
    def test {
        val html = 
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
                    <title>Dali - Web Gallery</title>
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
                 </body>
            </html>
        val writer = new OutputStreamWriter(System.out)
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
