package uk.co.odinconsultants.greta.app

import org.scalatest.{Matchers, WordSpec}
import uk.co.odinconsultants.greta.app.CLIParsing._

class GretaMainSpec extends WordSpec with Matchers {

  import GretaMain._

  val testEndpoint  = "test_endpoint"
  val testImage     = "test_image"

  val endpointCLI:  Seq[String] = s"--$endpointName $testEndpoint".split(" ")
  val imageCLI:     Seq[String] = s"--$imageName $testImage".split(" ")
  val happyArgs:    Seq[String] = endpointCLI ++ imageCLI

  s"Happy path arguments '${happyArgs.mkString(" ")}'" should {
    "result in a configuration object" in {
      parse(happyArgs) shouldBe Right(DeployImageConfig(testEndpoint, testImage))
    }
  }

  def expectFailureFrom(x: Parsing): Unit = x.fold(identity, x => fail(s"Was not expecting $x"))

  "Missing arguments" should {
    "result in an error" in {
      expectFailureFrom(parse(endpointCLI))
      expectFailureFrom(parse(imageCLI))
    }
  }

}
