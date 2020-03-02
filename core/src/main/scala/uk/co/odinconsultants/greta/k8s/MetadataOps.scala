package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.NamespaceFluent.MetadataNested
import uk.co.odinconsultants.greta.k8s.Commands.{Labels, Name}

object MetadataOps {

  type MetadataPipe[T] = MetadataNested[T] => MetadataNested[T]

  def withName[T](name: Name): MetadataPipe[T]= _.withName(name)

  def withLabel[T](label: Labels): MetadataPipe[T] = { x =>
    x.addToLabels("app.kubernetes.io/name", label.name)
    x.addToLabels("app.kubernetes.io/instance", label.instance)
    x.addToLabels("app.kubernetes.io/component", label.instance)
  }

}
