package com.ibex35


import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.sql.functions.{col, count, split}
import org.apache.spark.sql.types._
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.util.LongAccumulator
import org.apache.spark.{SparkConf, SparkContext}

object DataFrameApp {
  def main(args: Array[String]): Unit = {
    import org.apache.log4j.{Level, Logger}

    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)

    val r = scala.util.Random
    // Generate a new Kafka Consumer group id every run
    val groupId = "0"//s"stream-checker-v${r.nextInt.toString}"

    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> "localhost:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> groupId
    )

    object FinishedBatchesCounter {
      @volatile private var instance: LongAccumulator = null

      def getInstance(sc: SparkContext): LongAccumulator = {
        if (instance == null) {
          synchronized {
            if (instance == null) {
              instance = sc.longAccumulator("FinishedBatchesCounter")
            }
          }
        }
        instance
      }
    }

    // 'sc' is a SparkContext, here it's provided by Zeppelin
    val conf = new SparkConf().setMaster("local[2]").setAppName(groupId)
    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(5))

    val topics = "IBEX35".split(",").toSet

    val stream = KafkaUtils.createDirectStream[String, String](
      ssc,
      PreferConsistent,
      Subscribe[String, String](topics, kafkaParams)
    )

    // We get a bunch of metadata from Kafka like partitions, timestamps, etc. Only interested in message payload
    val messages = stream.map(record => record.value)

    // This turns accumulated batch of messages into RDD
    messages.foreachRDD { rdd =>
      // Now we want to turn RDD into DataFrame
      val spark = SparkSession.builder.config(rdd.sparkContext.getConf).getOrCreate()
      //redis
      val spark2 = SparkSession.builder().appName("redis-df").master("local[*]")
        .config("spark.redis.host", "localhost").config("spark.redis.port", "6379")
        .getOrCreate()

      import spark.implicits._

      // Following 2 lines do all the magic for transforming String RDD into JSON DataFrame
      // Using the schema above
      val rawDF = rdd.toDF("msg")

      val dfkafka = rawDF.withColumn("_tmp", split($"msg", "\\,")).select(
        $"_tmp".getItem(0).as("name"),
        $"_tmp".getItem(1).as("date"),
        $"_tmp".getItem(2).as("value").cast(DoubleType)
      )

      // Cacheamos ahora para acelerar la mayorias de las operaciones
      //dfkafka.cache()


      val finishedBatchesCounter = FinishedBatchesCounter.getInstance(sc)
      finishedBatchesCounter.add(1)

      if(finishedBatchesCounter.count == 1){
        //INICIANILACION DE BD
        spark2.read.option("delimiter",";").option("header", true).csv("src/main/scala/resource/redis.csv")
          .write.format("org.apache.spark.sql.redis").option("table", "ibex").mode(SaveMode.Overwrite).save()
      }

      println(s"+------------ Batch ${finishedBatchesCounter.count} ------------+")
      println("KAFKA")
      dfkafka.show()

      //CREAMOS EL DATAFRAME DE REDIS

      val dfRedis = spark2.read.format("org.apache.spark.sql.redis").option("table", "ibex").load()
        .groupBy($"id", $"name", $"value_up", $"value_down")
        .agg(count("*").alias("count"))
        .filter(col("count") < 2)
        .select($"id", $"name", $"value_up", $"value_down")
      dfRedis.show()
      println("REDIS")


      val dfJoin = dfRedis.join(dfkafka, dfRedis.col("name") === dfkafka.col("name"), "left")
        .where($"value_down" >= $"value" || $"value" >= $"value_up" )
        .select($"id", dfRedis.col("name"), $"value_up", $"value_down", $"value")
        .distinct
      dfJoin.show()
      println("REDIS and KAFKA")

      if(dfJoin.count() != 0){
        // ACTUALIZAMOS LA BD DE REDIS
        dfJoin.select($"id", $"name", $"value_up", $"value_down")
               .write.format("org.apache.spark.sql.redis").option("table", "ibex").mode(SaveMode.Append).save()
      }
      dfJoin.rdd
    }

    ssc.start()
    ssc.awaitTermination()
  }
}
