package no.nav.k9.kafka

import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.k9.ettersending.KomplettEttersending
import no.nav.k9.general.formaterStatuslogging
import no.nav.k9.somJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import org.json.JSONObject
import org.slf4j.LoggerFactory

class KafkaProducer(
    val kafkaConfig: KafkaConfig
) : HealthCheck {
    private val NAME = "SøknadProducer"
    private val ETTERSENDING_MOTTATT_TOPIC = TopicUse(
        name = Topics.MOTTATT_ETTERSENDING_TOPIC,
        valueSerializer = SøknadSerializer()
    )
    private val logger = LoggerFactory.getLogger(KafkaProducer::class.java)
    private val producer = KafkaProducer(
        kafkaConfig.producer(NAME),
        ETTERSENDING_MOTTATT_TOPIC.keySerializer(),
        ETTERSENDING_MOTTATT_TOPIC.valueSerializer
    )

    internal fun produserKafkaMelding(
        komplettEttersending: KomplettEttersending,
        metadata: Metadata
    ) {
        if (metadata.version != 1) throw IllegalStateException("Kan ikke legge melding med versjon ${metadata.version} til prosessering.")
        val recordMetaData = producer.send(
            ProducerRecord(
                ETTERSENDING_MOTTATT_TOPIC.name,
                komplettEttersending.søknadId,
                TopicEntry(
                    metadata = metadata,
                    data = JSONObject(komplettEttersending.somJson())
                )
            )
        ).get()
        logger.info(
            formaterStatuslogging(
                komplettEttersending.søknadId,
                "sendes til topic ${ETTERSENDING_MOTTATT_TOPIC.name} med offset '${recordMetaData.offset()}' til partition '${recordMetaData.partition()}'"
            )
        )
    }

    internal fun stop() = producer.close()

    override suspend fun check(): Result {
        return try {
            producer.partitionsFor(ETTERSENDING_MOTTATT_TOPIC.name)
            Healthy(NAME, "Tilkobling til Kafka OK!")
        } catch (cause: Throwable) {
            logger.error("Feil ved tilkobling til Kafka", cause)
            UnHealthy(NAME, "Feil ved tilkobling mot Kafka. ${cause.message}")
        }
    }
}

private class SøknadSerializer : Serializer<TopicEntry<JSONObject>> {
    override fun serialize(topic: String, data: TopicEntry<JSONObject>): ByteArray {
        val metadata = JSONObject()
            .put("correlationId", data.metadata.correlationId)
            .put("version", data.metadata.version)

        return JSONObject()
            .put("metadata", metadata)
            .put("data", data.data)
            .toString()
            .toByteArray()
    }

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
    override fun close() {}
}

