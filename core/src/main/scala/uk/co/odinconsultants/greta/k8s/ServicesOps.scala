package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.NamespaceFluent.MetadataNested
import io.fabric8.kubernetes.api.model.ServiceFluent.SpecNested
import io.fabric8.kubernetes.api.model.{DoneableService, IntOrString, Namespace, NamespaceBuilder, NamespaceFluent, Service, ServiceFluent, ServiceList}
import io.fabric8.kubernetes.client.dsl.{NonNamespaceOperation, ServiceResource}
import uk.co.odinconsultants.greta.k8s.Commands.{Name, Port}

import scala.collection.JavaConverters._

object ServicesOps {

  type SpecPipe = SpecNested[DoneableService] => SpecNested[DoneableService]

  def setPublishNotReadyAddresses(x: Boolean): SpecPipe = _.withNewPublishNotReadyAddresses(x)

  def addClusterIP(x: String): SpecPipe = _.withClusterIP(x)

  def withType(name: Name): SpecPipe = _.withType(name)

  def addPort(name: Name, port: Port, targetPort: Name): SpecPipe =
      _.addNewPort.withName(name).withPort(port).withTargetPort(new IntOrString(name)).endPort

  def addPort(name: Name, port: Port): SpecPipe =
    _.addNewPort.withName(name).withPort(port).withNewTargetPort.withNewStrVal(name).endTargetPort.endPort

  def addPort(name: Name, port: Port, targetPort: Port): SpecPipe =
    _.addNewPort.withName(name).withPort(port).withNewTargetPort.withIntVal(targetPort).endTargetPort.endPort

  val ClusterIP = "ClusterIP"

}
