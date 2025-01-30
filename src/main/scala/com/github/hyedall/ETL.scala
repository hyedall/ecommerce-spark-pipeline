package com.github.hyedall.ETL

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.Dataset
import com.typesafe.config.ConfigFactory
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}
import scala.io.Source
import org.apache.spark.sql.{Encoder, Encoders}
import scala.reflect.runtime.universe._

import java.util.UUID

@SerialVersionUID(100L)
case class Event(
  user_id: Int, event_time: java.sql.Timestamp, event_type: String, product_id: Int, category_id: Long, category_code: String, price: Double, user_session: String, event_time_kst: java.sql.Timestamp, event_date: String
) extends Serializable

@SerialVersionUID(100L)
case class SessionEvent(
  user_id: Int, event_time: java.sql.Timestamp, event_type: String, product_id: Int, category_id: Long, category_code: String, price: Double, user_session: String, event_time_kst: java.sql.Timestamp, event_date: String, session_id: String
) extends Serializable


object ETL {
  def runETL(spark: SparkSession, inputPath: String, outputPath: String): Unit = {
    println(s"ETL process started with input: $inputPath and output: $outputPath")
    
    import spark.implicits._

    val lastProcessedTimeFilePath = Paths.get(System.getProperty("user.dir"), "data", "lastProcessedTime.txt").toString
    val lastProcessedTime = getLastProcessedTime(lastProcessedTimeFilePath)
    println("!--lastProcessedTimeFilePath" + lastProcessedTimeFilePath)

    val lastProcessedDateFilePath = Paths.get(System.getProperty("user.dir"), "data", "lastProcessedDate.txt").toString
    val lastProcessedDate = getLastProcessedDate(lastProcessedDateFilePath)

    val checkpointPath = "data/checkpoint"


    // 원본 데이터 읽기
    val rawDf = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(inputPath)
      .dropDuplicates("user_id", "event_time")
      // .filter(col("event_time") > lit(lastProcessedTime).cast("timestamp"))
      .cache()

    // 날짜 정제
    val processedDf = rawDf
      .withColumn("event_time", to_timestamp(col("event_time")))
      .withColumn("event_time_kst", from_utc_timestamp(col("event_time"), "Asia/Seoul"))
      .withColumn("event_date", date_format(col("event_time_kst"), "yyyy-MM-dd"))

    println("==getNumPartitions" + rawDf.rdd.getNumPartitions)

    // 처리할 날짜 목록 가져오기
    val datesToProcess = processedDf
      .filter(col("event_date") > lit(lastProcessedDate))
      .select("event_date").distinct()
      .as[String].collect().sorted

    println(s"!-- Processing dates: ${datesToProcess.mkString(", ")}")

    // 날짜별로 ETL 실행
    for (date <- datesToProcess) {
      println(s"!-- Processing date: $date")
      processData(spark, processedDf, outputPath, checkpointPath, lastProcessedTimeFilePath, lastProcessedDateFilePath, date)
      
    }
    spark.stop()
  }

  /** 날짜별 데이터 처리 함수 */
  def processData(
    spark: SparkSession,
    processedDf: DataFrame,
    outputPath: String,
    checkpointPath: String,
    lastProcessedTimeFilePath: String,
    lastProcessedDateFilePath: String,
    date: String
  ): Unit = {
    import spark.implicits._
    // 특정 날짜에 해당하는 데이터 필터링
    val dailyDf = processedDf.filter(col("event_date") === lit(date))

    // Dataset 변환
    import org.apache.spark.sql.Encoders

    val ds: Dataset[Event] = dailyDf
      .select(
        col("user_id"),
        col("event_time"),
        col("event_type"),
        col("product_id"),
        col("category_id"),
        col("category_code"),
        col("price"),
        col("user_session"),
        col("event_time_kst"),
        col("event_date")
        ).as[Event](eventEncoder)
    ds.show()

    // 세션 ID 전처리
    val sessionizedDs = ds.groupByKey(_.user_id) 
      .mapGroups { (userId: Int, iter: Iterator[Event]) =>  
        // 이전 이벤트 정보
        var prevSessionId: String = ""
        var prevUserSession: String = ""
        var prevTime: Option[Long] = None

        iter.toSeq.sortBy(_.event_time.getTime).map { event =>
          val eventTime = event.event_time.getTime
          var currentSessionId = event.user_session

          // 이전 이벤트가 없음
          if (prevTime.isEmpty) {
            currentSessionId = event.user_session
            prevSessionId = event.user_session
            prevUserSession = event.user_session
          }
          else if ((eventTime - prevTime.get) >= 300000) {
            // 5분 이상 && 세션 id 이전과 동일
            if (prevSessionId == event.user_session) {
              currentSessionId = UUID.randomUUID().toString
            }
            // 5분 이상 && 세션 id 이전과 상이
            else {
              currentSessionId = event.user_session
            }
          }
          // 5분 이내 이전 연산에서 id 변경
          else if (prevSessionId != prevUserSession && prevUserSession == event.user_session) {
            currentSessionId = prevSessionId
          }
          else {
            currentSessionId = event.user_session
          }

          // 시간 갱신
          prevSessionId = currentSessionId
          prevUserSession = event.user_session
          prevTime = Some(event.event_time.getTime)
          SessionEvent(
            event.user_id, event.event_time, event.event_type, event.product_id, event.category_id, event.category_code, event.price, event.user_session, event.event_time_kst, event.event_date, currentSessionId
          )
        }
      }

    val finalDf = sessionizedDs.flatMap(identity).toDF()
    
    // pqrquet 포맷 저장, snappy 압축
    finalDf.write
      .mode("append")
      .partitionBy("event_date")
      .format("parquet")
      .option("compression", "snappy")
      .option("checkpointLocation", checkpointPath) 
      .save(outputPath)
      // .save("hdfs://218.236.36.173:8020/user/hive/warehouse")

    val maxEventTime = finalDf.agg(max("event_time")).collect()(0)(0).toString
    saveLastProcessedTime(lastProcessedTimeFilePath, maxEventTime)


    // 처리된 마지막 날짜 저장
    saveLastProcessedDate(lastProcessedDateFilePath, date)
  }

  def getLastProcessedTime(filePath: String): String = {
    val path = Paths.get(filePath)
    println("==load filePath" + filePath)

    if (Files.exists(path)) {
      val lastProcessedTime = Source.fromFile(filePath).mkString.trim
      lastProcessedTime
    } else {
      "1970-01-01 00:00:00"
    }
  }
  def saveLastProcessedTime(filePath: String, time: String): Unit = {
    val file = new File(filePath)
    file.getParentFile.mkdirs() 

    val writer = new PrintWriter(file)
    writer.write(time)
    writer.flush() 
    println("==save filePath" + filePath)
    writer.close()
  }

  def getLastProcessedDate(filePath: String): String = {
    val path = Paths.get(filePath)
    println("==load filePath" + filePath)

    if (Files.exists(path)) {
      val lastProcessedDate = Source.fromFile(filePath).mkString.trim
      lastProcessedDate
    } else {
      "1970-01-01"
    }
  }
  def saveLastProcessedDate(filePath: String, date: String): Unit = {
    val file = new File(filePath)
    file.getParentFile.mkdirs()
    val writer = new PrintWriter(file)
    writer.write(date)
    writer.flush()
    println("==save filePath" + filePath)
    writer.close()
  }
}