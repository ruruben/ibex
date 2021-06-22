package com.ibex35


import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.{Seconds, StreamingContext}


object EjemSparkStreamming {
  def parseFileLine(line: String): Row = {

    val line_splitted = line.split(",");
    Row(line_splitted(0), line_splitted(1),line_splitted(2).toDouble)
  }

  def filterTickers(rdd: RDD[Row]): RDD[Row] = {
    val myTickers = List("ANA", "TEF")
    rdd.filter(x => myTickers.contains(x(0)))
  }

  def exist(rdd:RDD[Row], db: Row): Boolean ={
    val rddf = rdd.filter(x=> x(0).equals(db(1)))
    !rddf.isEmpty()
  }

  def alert(rdd: RDD[Row]): RDD[Row] = {
    val db = Seq(
      Row(1, "ANA", 89.0, 80), Row(1, "TEF", 7.5, 6)
    )
    rdd.sparkContext.parallelize(db)

    //dbrdd.filter(db => rdd.filter(x =>x(0).equals(db(1))).collect().toList.isEmpty )
    //dbrdd.filter(db => !rdd.filter(x => x(0).equals(db(1))).filter(y => y(2).toString.toDouble >= db(2).toString.toDouble || y(2).toString.toDouble <= db(3) ).isEmpty() )
    //rdd.map(x =>  db.filter(y => y(1).equals(x(0))).head)
  }

  def exampleStreamming(): Unit = {
    val conf = new SparkConf().setMaster("local[2]").setAppName("EjemSparkStreamming-kafka")
    val ssc = new StreamingContext(conf, Seconds(5))
    val topics = "IBEX35" // lista de Topic de Kafka
    val brokers = "localhost:9092" // broker de Kafka
    val groupId = "0" // Identificador del grupo.
    // Create direct kafka stream with brokers and topics
    val topicsSet = topics.split(",").toSet
    val kafkaParams = Map[String, Object](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
      ConsumerConfig.GROUP_ID_CONFIG -> groupId,
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer])
    val messages = KafkaUtils.createDirectStream[String, String](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topicsSet, kafkaParams))


    val lines = messages.window(Seconds(5)).map(_.value)
      .map(parseFileLine)
      .transform(x => filterTickers(x))

    val mapa = lines
      .transform(x => alert(x))
    mapa.print()


    ssc.start()
    ssc.awaitTermination()
  }
  def main(args: Array[String]): Unit = {
    exampleStreamming()
  }
}