package uk.co.odinconsultants.greta.k8s

import io.fabric8.kubernetes.api.model.{DoneableService, EnvVar, Namespace, NamespaceBuilder, ObjectMetaFluent, Quantity, Service, ServiceFluent}
import io.fabric8.kubernetes.api.model.ServiceFluent.{MetadataNested, SpecNested}
import io.fabric8.kubernetes.api.model.apps.{StatefulSet, StatefulSetBuilder}
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import uk.co.odinconsultants.greta.k8s.ServicesOps._
import uk.co.odinconsultants.greta.k8s.Commands._
import uk.co.odinconsultants.greta.k8s.MetadataOps._

import scala.util.Try
import scala.collection.JavaConverters._
import scala.collection.mutable

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
    withLabelsAndName(metadata).and.withNewSpec.withSelector(toMap(labels).asJava)
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

      val ssZookeeper: StatefulSet = new StatefulSetBuilder()
        .withNewMetadata // will barf without metadata
          .withName(zookeeperName)
          .withLabels((toMap(ZookeeperLabels) + ("role" -> Zookeeper)).asJava)
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
                .withCommand("bash", "-ec", """|
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
                .addNewPort.withContainerPort(2181).withHostPort(2181).withName("client").endPort()
                .addNewPort.withContainerPort(2888).withName("follower").endPort()
                .addNewPort.withContainerPort(3888).withName("election").endPort()
              .endContainer()
            .endSpec()
          .endTemplate()
        .endSpec()
      .build()


      val ssKafka: StatefulSet = new StatefulSetBuilder()
        .withNewMetadata // will barf without metadata
          .withName(kafkaName)
          .withLabels((toMap(KafkaLabels) + ("role" -> Kafka)).asJava)
        .endMetadata()
        .withNewSpec()
          .withNewSelector.addToMatchLabels(toMap(KafkaLabels).asJava).endSelector()
          .withNewServiceName(kafkaHeadlessName)
          .withNewPodManagementPolicy("Parallel")
          .withReplicas(1)
          .withNewUpdateStrategy().withNewType("RollingUpdate").endUpdateStrategy
          .withNewTemplate
            .withNewMetadata.withName(Kafka).withLabels(toMap(KafkaLabels).asJava).endMetadata()
            .withNewSpec
              .withNewSecurityContext().withFsGroup(0L).withRunAsUser(0L).endSecurityContext()
              .addNewContainer
                .withName(Kafka)
                .withImage("docker.io/bitnami/kafka:2.4.0-debian-10-r25")
                .withImagePullPolicy("IfNotPresent")
                .withEnv(kafkaEnv.asJava)
                .addNewPort.withContainerPort(9092).withHostPort(9092).withName("kafka").endPort()
              .endContainer()
            .endSpec()
         .endTemplate()
       .endSpec()
     .build()

      client.apps().statefulSets().inNamespace(namespace).create(ssZookeeper)
      client.apps().statefulSets().inNamespace(namespace).create(ssKafka)
    }
  }

  def kafkaEnv: List[EnvVar] = {

    val kafka: Service = client.services().list().getItems().asScala.filter(_.getMetadata.getName == kafkaName).head
    val ip = kafka.getSpec.getClusterIP
    val name = s"$kafkaName-0" // TODO - this should not be hardcoded
    val port = kafka.getSpec.getPorts.asScala.head.getPort

    val pods = client.pods().inNamespace(namespace).list().getItems.asScala
    println(s"ip = $ip, name = $name, port = $port, pods = $pods")

    val fixed = Map("BITNAMI_DEBUG" -> "false"
      , "KAFKA_CFG_ZOOKEEPER_CONNECT" -> "ph-release-zookeeper"
      , "KAFKA_PORT_NUMBER" -> "9092"
      , "KAFKA_CFG_LISTENERS" -> "PLAINTEXT://:9092"
      , "KAFKA_CFG_ADVERTISED_LISTENERS" -> s"PLAINTEXT://${name}.ph-release-kafka-headless.${namespace}.svc.cluster.local:${port}"
      , "ALLOW_PLAINTEXT_LISTENER" -> "yes"
      , "KAFKA_CFG_BROKER_ID" -> "-1"
      , "KAFKA_CFG_DELETE_TOPIC_ENABLE" -> "false"
      , "KAFKA_HEAP_OPTS" -> "-Xmx1024m -Xms1024m"
      , "KAFKA_CFG_LOG_FLUSH_INTERVAL_MESSAGES" -> "10000"
      , "KAFKA_CFG_LOG_FLUSH_INTERVAL_MS" -> "1000"
      , "KAFKA_CFG_LOG_RETENTION_BYTES" -> "1073741824"
      , "KAFKA_CFG_LOG_RETENTION_CHECK_INTERVALS_MS" -> "300000"
      , "KAFKA_CFG_LOG_RETENTION_HOURS" -> "168"
      , "KAFKA_CFG_MESSAGE_MAX_BYTES" -> "1000012"
      , "KAFKA_CFG_LOG_SEGMENT_BYTES" -> "1073741824"
      , "KAFKA_CFG_LOG_DIRS" -> "/bitnami/kafka/data"
      , "KAFKA_CFG_DEFAULT_REPLICATION_FACTOR" -> "1"
      , "KAFKA_CFG_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1"
      , "KAFKA_CFG_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" -> "1"
      , "KAFKA_CFG_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM" -> "https"
      , "KAFKA_CFG_TRANSACTION_STATE_LOG_MIN_ISR" -> "1"
      , "KAFKA_CFG_NUM_IO_THREADS" -> "8"
      , "KAFKA_CFG_NUM_NETWORK_THREADS" -> "3"
      , "KAFKA_CFG_NUM_PARTITIONS" -> "1"
      , "KAFKA_CFG_NUM_RECOVERY_THREADS_PER_DATA_DIR" -> "1"
      , "KAFKA_CFG_SOCKET_RECEIVE_BUFFER_BYTES" -> "102400"
      , "KAFKA_CFG_SOCKET_REQUEST_MAX_BYTES" -> "104857600"
      , "KAFKA_CFG_SOCKET_SEND_BUFFER_BYTES" -> "102400"
      , "KAFKA_CFG_ZOOKEEPER_CONNECTION_TIMEOUT_MS" -> "6000"
    )



    val envs = fixed + ("MY_POD_NAME" -> name) + ("MY_POD_IP" -> ip)

    envs.map { case (k, v) => createEnvVar(k, v) }.toList
  }

  def zookeeperEnv: List[EnvVar] = {
    val envs = Map( "ZOO_TICK_TIME" -> "2000"
      , "ZOO_INIT_LIMIT" -> "10"
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
