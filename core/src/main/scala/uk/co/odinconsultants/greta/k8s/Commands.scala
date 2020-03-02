package uk.co.odinconsultants.greta.k8s


object Commands {

  type Port = Int
  type Name = String

  case class Label(name: Name, instance: Name, component: String)

}
