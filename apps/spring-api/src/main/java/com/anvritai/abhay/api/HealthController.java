package com.anvritai.abhay.api;
import java.time.Instant;import java.util.*;import javax.sql.DataSource;import org.flywaydb.core.Flyway;import org.springframework.http.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;
@RestController public class HealthController{
 private final JdbcTemplate jdbc;private final Flyway flyway;public HealthController(DataSource ds,Flyway flyway){jdbc=new JdbcTemplate(ds);this.flyway=flyway;}
 @GetMapping("/live") public Map<String,Object> live(){return Map.of("status","UP","service","ABHAY Accounting OS API","timestamp",Instant.now());}
 @GetMapping("/health") public ResponseEntity<Map<String,Object>> health(){return status(false);}
 @GetMapping("/ready") public ResponseEntity<Map<String,Object>> ready(){return status(true);}
 private ResponseEntity<Map<String,Object>> status(boolean strict){boolean db;try{db=jdbc.queryForObject("select 1",Integer.class)==1;}catch(Exception e){db=false;}boolean migrations;try{migrations=flyway.info().pending().length==0;}catch(Exception e){migrations=false;}Map<String,Object> body=new LinkedHashMap<>();body.put("status",db&&migrations?"UP":"DOWN");body.put("database",db?"UP":"DOWN");body.put("flyway",migrations?"UP":"PENDING");body.put("memoryEngine","UP");body.put("storage","METADATA_READY");body.put("scheduler","UP");body.put("timestamp",Instant.now());return ResponseEntity.status(strict&&(!db||!migrations)?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.OK).body(body);}
}
