package uk.co.odinconsultants.greta.app

import cats.data.NonEmptyList
import com.monovore.decline.Opts

object CLIParsing {

  type CliString = Opts[NonEmptyList[String]]

  val endpointName: String      = "endpoint"
  val imageName:    String      = "image"
  val endpointOpt:  CliString   = Opts.options[String](endpointName, help = "The endpoint of the environment manager")
  val imageOpt:     CliString   = Opts.options[String](imageName, help = "The image to deploy")

}
