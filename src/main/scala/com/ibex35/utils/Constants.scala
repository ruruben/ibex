package com.ibex35.utils

import com.ibex35.service.IbexService
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.codehaus.jackson.map.ser.std.StringSerializer

object Constants {

  val conf = new SparkConf().setMaster("local[2]").setAppName("0").set("es.index.auto.create", "true")

  val sc = new SparkContext(conf)

  val ssc = new StreamingContext(sc, Seconds(5))

  val kafkaParamsConsumer = Map[String, Object](
    "bootstrap.servers" -> "localhost:9092",
    "key.deserializer" -> classOf[StringDeserializer],
    "value.deserializer" -> classOf[StringDeserializer],
    "group.id" -> "0"
  )

  val topics = "IBEX35".split(",").toSet

  val streamInput = KafkaUtils.createDirectStream[String, String](
    ssc,
    PreferConsistent,
    Subscribe[String, String](topics, kafkaParamsConsumer)
  )

  val kafkaParamsProducer = Map[String, Object](
    "bootstrap.servers" -> "localhost:9092",
    "key.serializer" -> classOf[StringSerializer],
    "value.deserializer" -> classOf[StringSerializer],
    "group.id" -> "0"
  )

  val sparkRedis = SparkSession.builder().appName("redis-df").master("local[*]")
    .config("spark.redis.host", "localhost").config("spark.redis.port", "6379")
    .getOrCreate()

  val finishedBatchesCounter = IbexService.getInstance(sc)
}
