# twelvedata

## Test
```shell
API_KEY=xxxxxx && sbt ~"testOnly test.com.dounine.tractor.net.StockTimeSerieTest"
```

## Build
```shell
sbt clean docker:publishLocal
#or exit images
sbt clean docker:clean docker:publishLocal 
```

## Deploy
```shell
docker run -d --rm -e JDBC_URL="jdbc:mysql://192.168.1.182:3306/db_jb?useUnicode=true&useSSL=false&characterEncoding=utf-8" -e JDBC_USERNAME=root -e JDBC_PASSWORD=root -e API_KEY="demo" -p 30003:30000 twelvedata:0.1.0-SNAPSHOT
```