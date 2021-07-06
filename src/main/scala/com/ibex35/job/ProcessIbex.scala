package com.ibex35.job

import com.ibex35.service.IbexService.{startRedisDB, writePostgre, writeRedis}
import com.ibex35.utils.Constants.{finishedBatchesCounter, sparkRedis, ssc, streamInput}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{DataTypes, DoubleType}

object ProcessIbex {
  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)

    val messages = streamInput.map(record => record.value)
    //spark.conf.set("spark.sql.session.timeZone", "UTC")
    messages.foreachRDD { rdd =>
      val spark = SparkSession.builder.config(rdd.sparkContext.getConf).getOrCreate()
      import spark.implicits._

      val dfkafka = rdd.toDF("msg").withColumn("_tmp", split(col("msg"), "\\,"))
                       .select($"_tmp".getItem(0).as("ticker"),
                       to_date($"_tmp".getItem(1), "dd/MM/yyyy").as("date"),
                               $"_tmp".getItem(2).as("value").cast(DoubleType))
                       .orderBy(desc("date"))
                       .limit(3)

      //dfkafka.cache()

      //Incrementamos laiteracion
      finishedBatchesCounter.add(1)
      if (finishedBatchesCounter.count == 1) { startRedisDB(sparkRedis) }

      println(s"+------------ Batch ${finishedBatchesCounter.count} ------------+")
      dfkafka.show()
      println("KAFKA")

      //CREAMOS EL DATAFRAME DE REDIS

      val dfRedis = sparkRedis.read.format("org.apache.spark.sql.redis").option("table", "ibex").load()
                              .groupBy($"id", $"ticker", $"value_up", $"value_down")
                              .agg(count("*").alias("count"))
                              .filter(col("count") < 2)
                              .select($"id".cast(DataTypes.IntegerType), $"ticker", $"value_up".cast(DataTypes.DoubleType), $"value_down".cast(DataTypes.DoubleType))
      dfRedis.show()
      println("REDIS")

      //OBTENEMOS LAS ACCIONES QUE ESTAN COMPRENDIDAS EN NUESTRO BASE DE DATOS
      val dfRedisJKafka = dfRedis.join(dfkafka, dfRedis.col("ticker") === dfkafka.col("ticker"), "left")
                                    .where($"value_down" >= $"value" || $"value" >= $"value_up")
                                    .select($"id", dfRedis("ticker"), $"value_up", $"value_down", $"value".as("sale"))
                                    .distinct

      dfRedisJKafka.show()
      println("REDIS and KAFKA")

      writePostgre(dfRedisJKafka)

      // ACTUALIZAMOS LA BD DE REDIS SI HAY COINCIDENCIAS
      writeRedis(dfRedisJKafka)

    }
    ssc.start()
    ssc.awaitTermination()
  }
}
