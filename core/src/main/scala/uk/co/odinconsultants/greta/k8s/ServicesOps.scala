package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.ServiceFluent.SpecNested
import io.fabric8.kubernetes.api.model.{DoneableService, IntOrString, Namespace, NamespaceBuilder, Service, ServiceList}
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.{NonNamespaceOperation, ServiceResource}
import uk.co.odinconsultants.greta.k8s.Commands.{Name, Port}

import scala.collection.JavaConverters._

object ServicesOps {

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

  type Namespaced = NonNamespaceOperation[Service, ServiceList, DoneableService, ServiceResource[Service, DoneableService]]

  def serviceFrom(namespaced: Namespaced, serviceName: Name): SpecNested[DoneableService] =
    namespaced.createNew.withNewMetadata.withName(serviceName).endMetadata.withNewSpec
}
