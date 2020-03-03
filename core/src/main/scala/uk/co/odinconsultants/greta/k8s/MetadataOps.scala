package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.ObjectMetaFluent
import uk.co.odinconsultants.greta.k8s.Commands.{Labels, Name}

object MetadataOps {

  type MetadataPipe[T <: ObjectMetaFluent[T]] = ObjectMetaFluent[T] => T

  def withName[T <: ObjectMetaFluent[T]](name: Name): MetadataPipe[T]= _.withName(name)

  val NameKey = "app.kubernetes.io/name"

  val InstanceKey = "app.kubernetes.io/instance"

  val ComponentKey = "app.kubernetes.io/component"

  def withLabel[T <: ObjectMetaFluent[T]](label: Labels): MetadataPipe[T] = { x =>
    x.addToLabels(NameKey, label.name)
      .addToLabels(InstanceKey, label.instance)
      .addToLabels(ComponentKey, label.instance)
  }

  def toMap(l: Labels): Map[Name, Name] = Map(NameKey -> l.name, InstanceKey -> l.instance, ComponentKey -> l.component)
}
