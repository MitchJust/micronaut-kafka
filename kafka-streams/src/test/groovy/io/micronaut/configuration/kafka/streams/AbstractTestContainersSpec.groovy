package io.micronaut.configuration.kafka.streams

import groovy.util.logging.Slf4j
import io.micronaut.configuration.kafka.streams.optimization.OptimizationStream
import io.micronaut.configuration.kafka.streams.wordcount.WordCountStream
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import io.micronaut.runtime.server.EmbeddedServer
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.testcontainers.containers.KafkaContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@Slf4j
abstract class AbstractTestContainersSpec extends Specification {

    PollingConditions conditions = new PollingConditions(timeout: 60, delay: 1)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer

    @Shared
    @AutoCleanup
    ApplicationContext context

    @Shared
    @AutoCleanup
    static KafkaContainer kafkaContainer = new KafkaContainer("5.4.2")

    def setupSpec() {
        kafkaContainer.start()

        createTopics(getTopics())

        List<Object> config = ["kafka.bootstrap.servers", "${kafkaContainer.getBootstrapServers()}"]
        config.addAll(getConfiguration())

        embeddedServer = ApplicationContext.run(EmbeddedServer,
                CollectionUtils.mapOf(
                        (config as Object[])
                )
        )

        context = embeddedServer.getApplicationContext()
    }

    protected List<Object> getConfiguration() {
        return ['kafka.generic.config', "hello",
                'kafka.streams.my-stream.application.id', 'my-stream',
                'kafka.streams.my-stream.num.stream.threads', 10,
                'kafka.streams.optimization-on.application.id', 'optimization-on',
                'kafka.streams.optimization-on.topology.optimization', 'all',
                'kafka.streams.optimization-off.application.id', 'optimization-off',
                'kafka.streams.optimization-off.topology.optimization', 'none']
    }

    //Override to create different topics on startup
    protected List<String> getTopics() {
        return [WordCountStream.INPUT,
                WordCountStream.OUTPUT,
                WordCountStream.NAMED_WORD_COUNT_INPUT,
                WordCountStream.NAMED_WORD_COUNT_OUTPUT,
                OptimizationStream.OPTIMIZATION_ON_INPUT,
                OptimizationStream.OPTIMIZATION_OFF_INPUT]
    }

    private static void createTopics(List<String> topics) {
        def newTopics = topics.collect { topic -> new NewTopic(topic, 1, (short) 1) }
        def admin = AdminClient.create(["bootstrap.servers": kafkaContainer.getBootstrapServers()])
        admin.createTopics(newTopics)
    }

    def cleanupSpec() {
        try {
            kafkaContainer.stop()
            embeddedServer.stop()
            log.warn("Stopped containers!")
        } catch (Exception ignore) {
            log.error("Could not stop containers")
        }
        if (embeddedServer != null) {
            embeddedServer.close()
        }
    }
}
