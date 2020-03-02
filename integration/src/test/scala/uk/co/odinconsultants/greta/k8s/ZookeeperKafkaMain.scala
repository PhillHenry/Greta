package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.{DoneableService, Namespace, NamespaceBuilder, ServiceFluent}
import io.fabric8.kubernetes.api.model.ServiceFluent.{MetadataNested, SpecNested}
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import uk.co.odinconsultants.greta.k8s.ServicesOps._
import uk.co.odinconsultants.greta.k8s.Commands._
import uk.co.odinconsultants.greta.k8s.MetadataOps._

class ZookeeperKafkaMain extends WordSpec with Matchers with BeforeAndAfterAll {

  val ZookeeperLabels = Commands.Labels("zookeeper", "ph-release", "zookeeper")

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

  def createNamespace(): Namespace = {
    import io.fabric8.kubernetes.api.model.NamespaceFluent.MetadataNested
    val pipe = withName[MetadataNested[NamespaceBuilder]](namespace)
    client.namespaces().create(pipe(new NamespaceBuilder().withNewMetadata).endMetadata().build())
  }

  "Zookeeper and Kafka" should {
    "fire up" in {
      val namespaced: Namespaced = client.services.inNamespace(namespace)

      createNamespace()

      val metadata: ServiceFluent.MetadataNested[DoneableService] = namespaced.createNew.withNewMetadata
      val zookeeperHeadlessMeta = withName[MetadataNested[DoneableService]](headlessZookeeperName) andThen withLabel[MetadataNested[DoneableService]](ZookeeperLabels)
      val zkMetaData: SpecNested[DoneableService] = zookeeperHeadlessMeta(metadata).and.withNewSpec

      val services = List[SpecNested[DoneableService]](
        zookeeperHeadless(zkMetaData),
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

      val ss1 = new StatefulSetBuilder().withNewSpec().withNewServiceName(headlessZookeeperName).withReplicas(1)
        .withNewPodManagementPolicy("Parallel").withNewUpdateStrategy().withNewType("RollingUpdate").endUpdateStrategy()

    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    client.namespaces.withName(namespace).delete()
    client.close()
  }

}
