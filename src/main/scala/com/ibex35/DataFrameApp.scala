package com.ibex35


import com.ibex35.utils.{finishedBatchesCounter, sparkRedis, ssc, stream}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.functions.{col, count, split}
import org.apache.spark.sql.types._
import org.apache.spark.sql.{SaveMode, SparkSession}

object DataFrameApp {
  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)

    // We get a bunch of metadata from Kafka like partitions, timestamps, etc. Only interested in message payload
    val messages = stream.map(record => record.value)

    // This turns accumulated batch of messages into RDD
    messages.foreachRDD { rdd =>
      // Now we want to turn RDD into DataFrame
      val spark = SparkSession.builder.config(rdd.sparkContext.getConf).getOrCreate()
      //redis


      import spark.implicits._

      // Following 2 lines do all the magic for transforming String RDD into JSON DataFrame
      // Using the schema above
      val rawDF = rdd.toDF("msg")

      val dfkafka = rawDF.withColumn("_tmp", split($"msg", "\\,"))
                         .select( $"_tmp".getItem(0).as("name"),
                                  $"_tmp".getItem(1).as("date"),
                                  $"_tmp".getItem(2).as("value").cast(DoubleType))

      // Cacheamos ahora para acelerar la mayorias de las operaciones
      dfkafka.cache()

      //Incrementamos laiteracion
      finishedBatchesCounter.add(1)
      if(finishedBatchesCounter.count == 1){
        //INICIANILACION DE BD - REDIS
        sparkRedis.read.option("delimiter",";").option("header", true).csv("src/main/scala/resource/redis.csv")
          .write.format("org.apache.spark.sql.redis").option("table", "ibex").mode(SaveMode.Overwrite).save()
      }

      println(s"+------------ Batch ${finishedBatchesCounter.count} ------------+")
      println("KAFKA")
      dfkafka.show()

      //CREAMOS EL DATAFRAME DE REDIS

      val dfRedis = sparkRedis.read.format("org.apache.spark.sql.redis").option("table", "ibex").load()
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

      //if(dfJoin.count() != 0){
        // ACTUALIZAMOS LA BD DE REDIS SI HAY COINCIDENCIAS
        dfJoin.select($"id", $"name", $"value_up", $"value_down")
               .write.format("org.apache.spark.sql.redis").option("table", "ibex").mode(SaveMode.Append).save()
      //}
    }

    ssc.start()
    ssc.awaitTermination()
  }
}
