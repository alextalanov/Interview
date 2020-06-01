**Hadoop MR job run steps:**

0. Run `./gradlew`
1. Go to directory `cd docker`
2. Up cluster `docker-compose up -d`
3. Go to container `docker exec -it resourcemanager bash`
4. create input dir on HDFS `hadoop fs -mkdir -p /jobs/tcv_to_orc_converter/input`
5. put a file to HDFS using `hadoop fs -put /root/hadoop/jobs/input/fixed_data.tsv /jobs/tcv_to_orc_converter/input` 
6. Run job using `hadoop jar /root/hadoop/jobs/HadoopMR-1.0-all.jar -i /jobs/tcv_to_orc_converter/input -o /jobs/tcv_to_orc_converter/output`

**Hive table creation using orc file:**

0. Go to container `docker exec -it postgres bash`
1. Run `psql -U postgres` and `create database metastore`
2. Go to container `docker exec -it hive bash`
3. Create schema `schematool -dbType postgres -initSchema`
4. Run metastore server `hive --service metastore &`
5. Run `beeline -u jdbc:hive2://`
6. Create table `CREATE EXTERNAL TABLE data (
                     id int,
                     first_name string,
                     last_name string,
                     account_number int,
                     email string
                 ) 
                   STORED AS ORC
                   LOCATION 'hdfs://namenode:9000/jobs/tcv_to_orc_converter/output';`
