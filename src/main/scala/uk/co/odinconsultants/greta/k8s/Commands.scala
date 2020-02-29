package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.{DoneableService, IntOrString, Namespace, NamespaceBuilder}
import io.fabric8.kubernetes.api.model.ServiceFluent.SpecNested
import io.fabric8.kubernetes.client.{DefaultKubernetesClient, KubernetesClient}

import scala.collection.JavaConverters._

object Commands {

  type Port = Int
  type Name = String

  type SpecPipe = SpecNested[DoneableService] => SpecNested[DoneableService]

  def publishNotReadyAddresses(x: Boolean): SpecPipe = _.withNewPublishNotReadyAddresses(x)

  def clusterIP(x: String): SpecPipe = _.withClusterIP(x)

  def withType(name: Name): SpecPipe = _.withType(name)

  def addPort(name: Name, port: Port, targetPort: Name): SpecPipe = { x =>
    x.addNewPort.withName(name).withPort(port).withTargetPort(new IntOrString(name)).endPort
  }

  def addPort(name: Name, port: Port, targetPort: Port): SpecPipe = { x =>
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

  def createNamespace(x: Name): KubernetesClient => Namespace = { client: KubernetesClient =>
    val ns = new NamespaceBuilder().withNewMetadata().withName(x).endMetadata().build();
    client.namespaces().create(ns)
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
      createNamespace(namespace)(client)
      zookeeperService(client.services().inNamespace(namespace).createNew().withNewMetadata.withName("ph-release-zookeeper-headless").endMetadata.withNewSpec).endSpec().done()
      listServices(client)
    } finally {
      client.namespaces.withName(namespace).delete()
      client.close()
    }
  }

}
