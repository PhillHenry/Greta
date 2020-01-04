package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.client.DefaultKubernetesClient

object Commands {

  def listPods(): Unit = {
    val client = new DefaultKubernetesClient()
    println(client.pods())
  }

  def main(args: Array[String]): Unit = {
    listPods()
  }

}
