package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.client.KubernetesClient

import scala.collection.JavaConverters._

object Commands {

  type Port = Int
  type Name = String

  case class Labels(name: Name, instance: Name, component: String)

  def listServices(client: KubernetesClient): Seq[Service] = {
    val services    = client.services().list().getItems().asScala
    println(services.mkString("\n"))
    services
  }

  def listPods(client: KubernetesClient): Unit = {
    val pods    = client.pods().list().getItems().asScala
    println(pods.mkString("\n"))
  }
}
