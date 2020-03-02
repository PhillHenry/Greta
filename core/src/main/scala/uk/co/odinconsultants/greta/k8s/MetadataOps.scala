package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.NamespaceFluent.MetadataNested
import io.fabric8.kubernetes.api.model.ObjectMetaFluent
import uk.co.odinconsultants.greta.k8s.Commands.{Labels, Name}

object MetadataOps {

  type MetadataPipe[T <: ObjectMetaFluent[T]] = ObjectMetaFluent[T] => T

  def withName[T <: ObjectMetaFluent[T]](name: Name): MetadataPipe[T]= _.withName(name)

  def withLabel[T <: ObjectMetaFluent[T]](label: Labels): MetadataPipe[T] = { x =>
    x.addToLabels("app.kubernetes.io/name", label.name)
    x.addToLabels("app.kubernetes.io/instance", label.instance)
    x.addToLabels("app.kubernetes.io/component", label.instance)
  }

}
