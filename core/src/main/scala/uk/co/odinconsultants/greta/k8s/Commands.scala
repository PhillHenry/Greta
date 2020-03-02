package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.{DoneableService, IntOrString, Namespace, NamespaceBuilder, Service, ServiceList}
import io.fabric8.kubernetes.api.model.ServiceFluent.SpecNested
import io.fabric8.kubernetes.client.dsl.{NonNamespaceOperation, ServiceResource}
import io.fabric8.kubernetes.client.{DefaultKubernetesClient, KubernetesClient}

import scala.collection.JavaConverters._
import scala.collection.mutable

object Commands {

  type Port = Int
  type Name = String

  type SpecPipe = SpecNested[DoneableService] => SpecNested[DoneableService]

  def setPublishNotReadyAddresses(x: Boolean): SpecPipe = _.withNewPublishNotReadyAddresses(x)

  def addClusterIP(x: String): SpecPipe = _.withClusterIP(x)

  def withType(name: Name): SpecPipe = _.withType(name)

  def addPort(name: Name, port: Port, targetPort: Name): SpecPipe = { x =>
    x.addNewPort.withName(name).withPort(port).withTargetPort(new IntOrString(name)).endPort
  }

  def addPort(name: Name, port: Port): SpecPipe = { x =>
    x.addNewPort.withName(name).withPort(port).withNewTargetPort.withNewStrVal(name).endTargetPort.endPort
  }

  def addPort(name: Name, port: Port, targetPort: Port): SpecPipe = { x =>
    x.addNewPort.withName(name).withPort(port).withNewTargetPort.withIntVal(targetPort).endTargetPort.endPort
  }

  def listPods(client: KubernetesClient): Unit = {
    val pods    = client.pods().list().getItems().asScala
    println(pods.mkString("\n"))
  }

  def listServices(client: KubernetesClient): Seq[Service] = {
    val pods    = client.services().list().getItems().asScala
    println(pods.mkString("\n"))
    pods
  }

  def createNamespace(x: Name): KubernetesClient => Namespace = { client: KubernetesClient =>
    val ns = new NamespaceBuilder().withNewMetadata().withName(x).endMetadata().build();
    client.namespaces().create(ns)
  }

  val ClusterIP = "ClusterIP"

  val zookeeper: SpecPipe =
    withType(ClusterIP) andThen
    addPort("client", 2181) andThen
    addPort("follower", 2888) andThen
    addPort("election", 3888)

  val kafka: SpecPipe =
    withType(ClusterIP) andThen
    addPort("kafka", 9092)

  type Namespaced = NonNamespaceOperation[Service, ServiceList, DoneableService, ServiceResource[Service, DoneableService]]

  def main(args: Array[String]): Unit = {
    val namespace = "phtest"
    val client    = new DefaultKubernetesClient()
    val namespaced: Namespaced = client.services.inNamespace(namespace)

    val zookeeperHeadless: SpecPipe = zookeeper andThen
      addClusterIP("None") andThen
      setPublishNotReadyAddresses(false)

    val kafkaHeadless: SpecPipe = kafka andThen withType(ClusterIP)

    try {
      createNamespace(namespace)(client) // TODO make this FP
      val services = List[SpecNested[DoneableService]](
        zookeeperHeadless(serviceFrom(namespaced, "ph-release-zookeeper-headless")),
        zookeeper(serviceFrom(namespaced, "ph-release-zookeeper")),
        kafka(serviceFrom(namespaced, "ph-release-kafka")),
        kafkaHeadless(serviceFrom(namespaced, "ph-release-kafka-headless"))
      )
      services.map(_.endSpec().done())
      val actualServices = listServices(client)
      val serviceNames = actualServices.map(_.getMetadata.getName)

    } finally {
      client.namespaces.withName(namespace).delete()
      client.close()
    }
  }

  def serviceFrom(namespaced: Namespaced, serviceName: Name): SpecNested[DoneableService] =
    namespaced.createNew.withNewMetadata.withName(serviceName).endMetadata.withNewSpec
}
