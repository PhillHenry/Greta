package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.{DoneableService, IntOrString}
import io.fabric8.kubernetes.api.model.ServiceFluent.SpecNested
import io.fabric8.kubernetes.client.{DefaultKubernetesClient, KubernetesClient}

import scala.collection.JavaConverters._

object Commands {

  type Port = Int
  type Name = String

  type Chaining = SpecNested[DoneableService] => SpecNested[DoneableService]

  def publishNotReadyAddresses(x: Boolean): Chaining = _.withNewPublishNotReadyAddresses(x)

  def clusterIP(x: String): Chaining = _.withClusterIP(x)

  def withType(name: Name): Chaining = _.withType(name)

  def addPort(name: Name, port: Port, targetPort: Name): Chaining = { x =>
    x.addNewPort.withName(name).withPort(port).withTargetPort(new IntOrString(name)).endPort
  }

  def addPort(name: Name, port: Port, targetPort: Port): Chaining = { x =>
    x.addNewPort.withName(name).withPort(port).withNewTargetPort.withIntVal(targetPort).endTargetPort.endPort
  }

  def listPods(client: KubernetesClient): Unit = {
    val pods    = client.pods().list().getItems().asScala
    println(pods.mkString("\n"))
  }

  def listServices(client: KubernetesClient): Unit = {
    val pods    = client.services().list().getItems().asScala
    println(pods.mkString("\n"))
  }

  def main(args: Array[String]): Unit = {
    val namespace = "phtest"
    val client  = new DefaultKubernetesClient()

    val zookeeperService = withType("ClusterIP") andThen clusterIP("None") andThen
      publishNotReadyAddresses(false) andThen
      addPort("client", 2181, "client") andThen
      addPort("follower", 2888, "follower") andThen
      addPort("election", 3888, "election")

    try {
      zookeeperService(client.services().inNamespace(namespace).createNew().withNewMetadata.withName("ph-release-zookeeper-headless").endMetadata.withNewSpec).endSpec().done()
      listServices(client)
    } finally {
      client.namespaces.withName(namespace).delete()
      client.close()
    }
  }

}
