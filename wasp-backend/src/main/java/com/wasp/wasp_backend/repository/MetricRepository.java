package com.wasp.wasp_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Repository
public class MetricRepository {
  private final JdbcTemplate jdbc;

  public MetricRepository(JdbcTemplate jdbc){
    this.jdbc = jdbc;
  }

  public void health() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS person (id INTEGER, name TEXT)");
    jdbc.update("INSERT INTO person VALUES (?, ?)", 1, "leo");
    jdbc.update("INSERT INTO person VALUES (?, ?)", 2, "yui");

    jdbc.query(
      "SELECT * FROM person",
      rs -> {
        System.out.println("id = " + rs.getInt("id"));
        System.out.println("name = " + rs.getString("name"));
      }
    );
  }
}
