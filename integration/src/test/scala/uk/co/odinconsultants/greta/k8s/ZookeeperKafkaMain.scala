package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.DoneableService
import io.fabric8.kubernetes.api.model.ServiceFluent.SpecNested
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import uk.co.odinconsultants.greta.k8s.Commands._

class ZookeeperKafkaMain extends WordSpec with Matchers with BeforeAndAfterAll {

  val zookeeper: SpecPipe =
    withType(ClusterIP) andThen
      addPort("client", 2181) andThen
      addPort("follower", 2888) andThen
      addPort("election", 3888)

  val kafka: SpecPipe =
    withType(ClusterIP) andThen
      addPort("kafka", 9092)

  val zookeeperHeadless: SpecPipe = zookeeper andThen
    addClusterIP("None") andThen
    setPublishNotReadyAddresses(false)

  val kafkaHeadless: SpecPipe = kafka andThen withType(ClusterIP)

  val namespace             = "phtest"

  val headlessZookeeperName = "ph-release-zookeeper-headless"
  val zookeeperName         = "ph-release-zookeeper"
  val kafkaName             = "ph-release-kafka"
  val kafkaHeadlessName     = "ph-release-kafka-headless"

  val client                = new DefaultKubernetesClient()

  "Zookeeper and Kafka" should {
    "fire up" in {
      val namespaced: Namespaced = client.services.inNamespace(namespace)

      createNamespace(namespace)(client) // TODO make this FP

      val services = List[SpecNested[DoneableService]](
        zookeeperHeadless(serviceFrom(namespaced, headlessZookeeperName)),
        zookeeper(serviceFrom(namespaced, zookeeperName)),
        kafka(serviceFrom(namespaced, kafkaName)),
        kafkaHeadless(serviceFrom(namespaced, kafkaHeadlessName))
      )
      services.map(_.endSpec().done())
      val actualServices  = listServices(client)
      val serviceNames    = actualServices.map(_.getMetadata.getName)

      serviceNames should contain (headlessZookeeperName)
      serviceNames should contain (zookeeperName)
      serviceNames should contain (kafkaName)
      serviceNames should contain (kafkaHeadlessName)
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    client.namespaces.withName(namespace).delete()
    client.close()
  }

}
