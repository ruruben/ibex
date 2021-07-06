package com.ibex35.service

import org.apache.spark.SparkContext
import org.apache.spark.sql.functions.{col, current_timestamp, round}
import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}
import org.apache.spark.util.LongAccumulator

object IbexService {
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

  def writeRedis(df: DataFrame) = {
    df.select("id", "ticker", "value_up", "value_down")
      .write.format("org.apache.spark.sql.redis")
      .option("table", "ibex")
      .mode(SaveMode.Append)
      .save()
  }

  def startRedisDB(ss: SparkSession): Unit = {
    ss.read.option("delimiter", ";").option("header", true).csv("src/main/scala/resource/redis.csv")
      .write.format("org.apache.spark.sql.redis").option("table", "ibex").mode(SaveMode.Overwrite).save()
  }

  def writePostgre(df: DataFrame) = {
    df.withColumn("renta", round((col("sale")-(((col("value_up") - col("value_down"))/2)+ col("value_down")))*100/(((col("value_up") - col("value_down"))/2) + col("value_down")), 2))
      .withColumn("timestamp", current_timestamp())
      .write
      .format("jdbc")
      .option("url", "jdbc:postgresql://localhost:5432/postgres")
      .option("dbtable", "ibex")
      .option("user", "postgres")
      .option("password", "postgres")
      .mode(SaveMode.Append)
      .save()
  }
}
