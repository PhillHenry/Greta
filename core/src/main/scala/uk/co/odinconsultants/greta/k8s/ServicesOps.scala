package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.ServiceFluent.SpecNested
import io.fabric8.kubernetes.api.model.{DoneableService, IntOrString}
import uk.co.odinconsultants.greta.k8s.Commands.{Name, Port}

object ServicesOps {

  type SpecPipe = SpecNested[DoneableService] => SpecNested[DoneableService]

  def setPublishNotReadyAddresses(x: Boolean): SpecPipe = _.withNewPublishNotReadyAddresses(x)

  def addClusterIP(x: String): SpecPipe = _.withClusterIP(x)

  def withType(name: Name): SpecPipe = _.withType(name)

  def addPort(name: Name, port: Port, targetPort: Name): SpecPipe =
      _.addNewPort.withName(name).withPort(port).withTargetPort(new IntOrString(name)).endPort


  def addPortWithExternal(name: Name, port: Port, nodePort: Port): SpecPipe = // .addNewPort().withPort(8080).withNodePort(31719).withNewTargetPort().withIntVal(8080).endTargetPort().endPort()
    _.addNewPort.withName(name).withPort(port).withNodePort(nodePort).withNewTargetPort.withNewStrVal(name).endTargetPort.endPort


  def addPort(name: Name, port: Port): SpecPipe =
    _.addNewPort.withName(name).withPort(port).withNewTargetPort.withNewStrVal(name).endTargetPort.endPort

  def addPort(name: Name, port: Port, targetPort: Port): SpecPipe =
    _.addNewPort.withName(name).withPort(port).withNewTargetPort.withIntVal(targetPort).endTargetPort.endPort

  val ClusterIP = "ClusterIP"

  val NodePort = "NodePort"

}
