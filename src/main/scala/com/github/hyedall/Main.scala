import com.github.hyedall.ETL.ETL
import org.apache.spark.sql.SparkSession

object Main {
  def main(args: Array[String]): Unit = {
    // 설정 파일 로드
    // val config = ConfigFactory.load("application.conf")
    val useSampleData = ""
    

    // val inputPath = if (useSampleData) config.getString("app.sample.input_path") else config.getString("app.actual.input_path")
    // val outputPath = if (useSampleData) config.getString("app.sample.output_path") else config.getString("app.actual.output_path")

    // val inputPath = "data/sample/*.csv"
    val inputPath = "data/raw/*.csv"
    val outputPath = "data/test/processed/"
    println(s"!-- data file: $inputPath")
    println(s"!-- output path: $outputPath")

    // Spark 세션 생성
    val spark = SparkSession.builder()
      .appName("Ecommerce ETL")
      .master("local[*]")
      .config("spark.sql.streaming.sheckpointLocation", "data/checkpoints")
      .getOrCreate()


    ETL.runETL(spark, inputPath, outputPath)
    println("!-- ETL process done")

    spark.stop()
  }
}