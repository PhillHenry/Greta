package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.{DoneableService, EnvVar, Namespace, NamespaceBuilder, ObjectMetaFluent, Quantity, ServiceFluent}
import io.fabric8.kubernetes.api.model.ServiceFluent.{MetadataNested, SpecNested}
import io.fabric8.kubernetes.api.model.apps.{StatefulSet, StatefulSetBuilder}
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import uk.co.odinconsultants.greta.k8s.ServicesOps._
import uk.co.odinconsultants.greta.k8s.Commands._
import uk.co.odinconsultants.greta.k8s.MetadataOps._

import scala.util.Try
import scala.collection.JavaConverters._

class ZookeeperKafkaMain extends WordSpec with Matchers with BeforeAndAfterAll {

  private val Instance  = "ph-release"
  private val Zookeeper = "zookeeper"
  private val Kafka     = "kafka"
  val ZookeeperLabels   = Labels(Zookeeper, Instance, Zookeeper)
  val KafkaLabels       = Labels(Kafka, Instance, Kafka)

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

  def withMetaData(labels: Labels, name: Name): SpecNested[DoneableService] = {
    type MetaData  = MetadataNested[DoneableService]
    val metadata:           MetaData                = client.services.inNamespace(namespace).createNew.withNewMetadata
    val withLabelsAndName:  MetadataPipe[MetaData]  = withLabel[MetaData](ZookeeperLabels) andThen withName[MetaData](name)
    withLabelsAndName(metadata).and.withNewSpec
  }

  "Zookeeper and Kafka" should {
    "fire up" in {
      createNamespace()

      val services = List[SpecNested[DoneableService]](
        zookeeperHeadless(withMetaData(ZookeeperLabels, headlessZookeeperName)),
        zookeeper(withMetaData(ZookeeperLabels, zookeeperName)),
        kafka(withMetaData(KafkaLabels, kafkaName)),
        kafkaHeadless(withMetaData(KafkaLabels, kafkaHeadlessName))
      )
      services.map(_.endSpec().done())
      val actualServices  = listServices(client)
      val serviceNames    = actualServices.map(_.getMetadata.getName)

      serviceNames should contain (headlessZookeeperName)
      serviceNames should contain (zookeeperName)
      serviceNames should contain (kafkaName)
      serviceNames should contain (kafkaHeadlessName)

      val ss1: StatefulSet = new StatefulSetBuilder()
        .withNewMetadata
          .withName(zookeeperName)
          .withLabels(toMap(ZookeeperLabels).asJava)
        .endMetadata()
        .withNewSpec()
          .withNewServiceName(headlessZookeeperName)
          .withReplicas(1)
          .withNewPodManagementPolicy("Parallel")
          .withNewUpdateStrategy().withNewType("RollingUpdate").endUpdateStrategy
          .withNewSelector.addToMatchLabels(toMap(ZookeeperLabels).asJava).endSelector()
          .withNewTemplate
            .withNewMetadata.withName(zookeeperName).withLabels(toMap(ZookeeperLabels).asJava).endMetadata()
            .withNewSpec
              .addNewContainer
                .withName(Zookeeper)
                .withImage("docker.io/bitnami/zookeeper:3.5.7-debian-10-r0")
                .withImagePullPolicy("IfNotPresent")
                .withNewSecurityContext().withRunAsUser(1001L).endSecurityContext()
                .withCommand("bash", "-ec", """||
                                           |                # Execute entrypoint as usual after obtaining ZOO_SERVER_ID based on POD hostname
                                           |                HOSTNAME=`hostname -s`
                                           |                if [[ $HOSTNAME =~ (.*)-([0-9]+)$ ]]; then
                                           |                  ORD=${BASH_REMATCH[2]}
                                           |                  export ZOO_SERVER_ID=$((ORD+1))
                                           |                else
                                           |                  echo "Failed to get index from hostname $HOST"
                                           |                  exit 1
                                           |                fi
                                           |                exec /entrypoint.sh /run.sh""".stripMargin)
                .withNewResources.withRequests(Map("cpu" -> new Quantity("250m"), "memory" -> new Quantity("256Mi")).asJava).endResources
                .withEnv(zookeeperEnv.asJava)
              .endContainer()
            .endSpec()
          .endTemplate()
        .endSpec()
      .build()

      client.apps().statefulSets().inNamespace(namespace).create(ss1)
    }
  }

  def zookeeperEnv: List[EnvVar] = {
    val envs = Map( "ZOO_TICK_TIME" -> "2000" , "ZOO_INIT_LIMIT" -> "10"
      , "ZOO_SYNC_LIMIT" -> "5"
      , "ZOO_MAX_CLIENT_CNXNS" -> "60"
      , "ZOO_4LW_COMMANDS_WHITELIST" -> "srvr, mntr"
      , "ZOO_LISTEN_ALLIPS_ENABLED" -> "no"
      , "ZOO_SERVERS" -> "ph-release-zookeeper-0.ph-release-zookeeper-headless.default.svc.cluster.local:2888:3888"
      , "ZOO_ENABLE_AUTH" -> "no"
      , "ZOO_HEAP_SIZE" -> "1024"
      , "ZOO_LOG_LEVEL" -> "ERROR"
      , "ALLOW_ANONYMOUS_LOGIN" -> "yes")
    envs.map { case (k, v) => createEnvVar(k, v) }.toList
  }

  private def createEnvVar(key: String, value: String): EnvVar = {
    val env = new EnvVar
    env.setName(key)
    env.setValue(value)
    env
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    Try { deleteNamespace() }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    deleteNamespace()
  }

  private def deleteNamespace() = {
    client.namespaces.withName(namespace).delete()
    client.close()
  }
}
