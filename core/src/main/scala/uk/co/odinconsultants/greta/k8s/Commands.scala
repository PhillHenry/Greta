package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.ServiceFluent.SpecNested
import io.fabric8.kubernetes.api.model._
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.{NonNamespaceOperation, ServiceResource}


object Commands {

  type Port = Int
  type Name = String

}
