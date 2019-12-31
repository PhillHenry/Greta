package uk.co.odinconsultants.greta.app

import cats.effect.{ExitCode, IO, IOApp}
import com.monovore.decline._
import cats.implicits._

object GretaMain extends IOApp {

  case class DeployImageConfig(url: String, image: String)

  override def run(args: List[String]): IO[ExitCode] = {
    ???
  }

  def parse(args: Seq[String]): Either[Help, DeployImageConfig] = {
    import CLIParsing._
    val options = (endpointOpt, imageOpt).mapN { (url, img) =>
      DeployImageConfig(url.head, img.head)
    }
    val command = Command(
      name = "deploy",
      header = "Deploy an image to a container"
    ) {
      options
    }
    command.parse(args)
  }

}
