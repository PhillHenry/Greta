package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.client.{DefaultKubernetesClient, KubernetesClient}

import scala.collection.JavaConverters._

object Commands {

  def listPods(client: KubernetesClient): Unit = {
    val pods    = client.pods().list().getItems().asScala
    println(pods.mkString("\n"))
  }

  def main(args: Array[String]): Unit = {
    val client  = new DefaultKubernetesClient()
    listPods(client)
    client.close()
  }

}
