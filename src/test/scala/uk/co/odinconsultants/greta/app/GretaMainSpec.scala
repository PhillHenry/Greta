package uk.co.odinconsultants.greta.app

import org.scalatest.{Matchers, WordSpec}
import CLIParsing._

class GretaMainSpec extends WordSpec with Matchers {

  import GretaMain._

  val testEndpoint  = "test_endpoint"
  val testImage     = "test_image"

  val happyArgs: Seq[String] = s"--$endpointName $testEndpoint --$imageName $testImage".split(" ")

  s"Happy path arguments '${happyArgs.mkString(" ")}'" should {
    "result in a configuration object" in {
      parse(happyArgs) shouldBe Right(DeployImageConfig(testEndpoint, testImage))
    }
  }

}
