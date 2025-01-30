## Ecommerce User Activity Spark ETL


### 프로젝트 개요
---
이 프로젝트는 [Kaggle의 이커머스 사용자 활동 로그](https://www.kaggle.com/datasets/mkechinov/ecommerce-behavior-data-from-multi-category-store?resource=download)를 ETL(추출, 변환, 적재)하여 Hive 외부 테이블로 제공하는 Apache Spark 기반 애플리케이션입니다. Spark 구현 언어로는 Scala를 사용하였습니다. Scala는 Spark의 개발 언어로 함수형 프로그래밍을 지원하여 간결한 코드로 구현할 수 있습니다. 또한 Spark를 구현할 때 가장 많이 사용하는 언어로 예시가 풍부하여 사용하게 되었습니다.


### 실행 환경
---
- raw 데이터가 1억 건 (약 10GB)가 넘어 sample 데이터를 함께 첨부했습니다.
- 실제 데이터로 실행 시 데이터를 다운받아 압축을 푼 파일을 data/raw/ 폴더에 넣어 실행하시면 됩니다.
```bash
$ curl -L -o ./ecommerce-behavior-data-from-multi-category-store.zip\
  https://www.kaggle.com/api/v1/datasets/download/mkechinov/ecommerce-behavior-data-from-multi-category-store
$ unzip ecommerce-behavior-data-from-multi-category-store.zip
```
- `docker-compose.yml`는 참고 용으로 Local에 Scala를 설치하여 개발을 진행하였습니다.


### Requirements
---
- Scala 3.6.3
- JAVA 11
- org.apache.spark.spark-core 3.5.4
- org.apache.spark.spark-sql 3.5.4


### 실행 방법
---
- Spark ETL 실행
  - csv 파일 읽기 -> 날짜 전처리 -> user 별 session_id 처리 -> parquet 형식 저장
```bash
$ sbt clean update
$ sbt compile 
$ sbt run
```


### 디렉토리 구조
---
```
├── data
│   ├── raw                # 원본 데이터
│   ├── processed          # 처리된 데이터
│   ├── sample             # 샘플 데이터
│   ├── checkpoints        # 체크포인트 데이터
│   ├── lastProcessedTime.txt  # 마지막 처리 시간 기록
├── src
│   ├── main
│   │   ├── scala
│   │   │   ├── com.github.hyedall.ETL
│   │   │   │   ├── ETL.scala  # ETL 로직
│   │   │   │   ├── Main.scala  # Main 실행 파일
├── doc  # 문서
├── docker-compose.yml
├── build.sbt
├── README.md
```


### 데이터 정보
---
| 컬럼명 | 데이터 타입 | 설명 |
|--------|------------|------|
| `event_time` | TIMESTAMP | 이벤트 발생 시간 (UTC) |
| `event_time_kst` | TIMESTAMP | 이벤트 발생 시간 (KST) |
| `event_date` | STRING | 이벤트 발생 날짜 |
| `event_type` | STRING | 이벤트 유형 (`view`, `cart`, `purchase`) |
| `product_id` | INT | 상품 ID |
| `category_id` | BIGINT | 카테고리 ID |
| `category_code` | STRING | 카테고리 코드 (ex. `electronics.smartphone`) |
| `brand` | STRING | 브랜드명 (NULL 가능) |
| `price` | DOUBLE | 상품 가격 |
| `user_id` | INT | 사용자 ID |
| `user_session` | STRING | 원본 사용자 세션 ID |
| `session_id` | STRING | 정제 후 사용자 세션 ID |


### WAU 쿼리 계산
---
```bash
$ docker-compose up -d

# namenode 컨테이너 접속
$ docker-compose exec namenode bash
# HDFS 디렉터리 생성
$ hdfs dfs -mkdir -p /user/hive/warehouse/processed
$ exit

# Hive 서버 접속
$ docker-compose exec hive-server bash
# Hive CLI 실행
$ /opt/hive/bin/beeline -u jdbc:hive2://localhost:10000

```

- 외부 테이블 생성
```
CREATE EXTERNAL TABLE ecommerce_data (
    user_id STRING,
    event_time TIMESTAMP,
    event_time_kst TIMESTAMP,
    event_type STRING,
    product_id STRING,
    category_id STRING,
    category_code STRING,
    price DOUBLE,
    user_session STRING,
    session_id STRING
)
PARTITIONED BY (event_date STRING)
STORED AS PARQUET
LOCATION '/user/hive/warehouse/processed';
```

- WAU 쿼리
```hive
-- user_id 기준 WAU
SELECT
CONCAT(YEAR(event_time_kst), '-W', WEEKOFYEAR(event_time_kst)) AS weekly,
COUNT(DISTINCT user_id) AS WAU
FROM ecommerce_data
GROUP BY YEAR(event_time_kst), WEEKOFYEAR(event_time_kst)
ORDER BY weekly;

-- session_id 기준 WAU
SELECT
CONCAT(YEAR(event_time_kst), '-W', WEEKOFYEAR(event_time_kst)) AS weekly,
COUNT(DISTINCT session_id) AS WAU
FROM ecommerce_data
GROUP BY YEAR(event_time_kst), WEEKOFYEAR(event_time_kst)
ORDER BY weekly;
```