package org.mediahub.album

import org.joda.time.DateTime
import javax.ws.rs._
import core.MediaType._
import java.io.File

@Path("images/latest")
class LatestImages {
  @GET
  @Path("{count}")
  def withLimit(@PathParam("count") @DefaultValue("10") limit: Int) {
  }
}