
package com.graphaware.module.tests;

import org.junit.Test;
import org.neo4j.graphdb.*;

import java.io.IOException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.Before;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

public class Stress3Test
{

  private static final Logger LOG = LoggerFactory.getLogger(Stress3Test.class);
  private GraphDatabaseService database;

  //@Override
  protected String neo4jConfigFile()
  {
    return "neo4j.properties";
  }

  protected String propertiesFile()
  {
    return "src/test/resources/" + neo4jConfigFile();
  }

  protected String neo4jServerConfigFile()
  {
    return "neo4j-server.properties";
  }




  @Test
  public void test() throws IOException
  {

    String path = System.getProperty("databaseCSVPath");
    int maxPeople = 10000;
    String databasePath = "/tmp/graph_" + System.currentTimeMillis() + ".db";
    
    /*if[NEO4J_2_3]
      database = new TestGraphDatabaseFactory()
              .newEmbeddedDatabaseBuilder(new File(databasePath))
              .loadPropertiesFromFile(this.getClass().getClassLoader().getResource("neo4j.properties").getPath())
              .newGraphDatabase();
    end[NEO4J_2_3]*/
      
    /*if[NEO4J_2_2_5]
      database = new TestGraphDatabaseFactory()
              .newEmbeddedDatabaseBuilder(databasePath)
              .loadPropertiesFromFile(this.getClass().getClassLoader().getResource("neo4j.properties").getPath())
              .newGraphDatabase();
    end[NEO4J_2_2_5]*/
    
    database.execute("CREATE INDEX ON :User(id)");
    
    LOG.warn("Start importing ...");
    {
      long startTime = System.currentTimeMillis();
      Result result = database.execute("USING PERIODIC COMMIT 5000\n" +
      "LOAD CSV FROM \"file://"+path+"\" AS line\n" +
      "FIELDTERMINATOR \"\\t\"\n" +
      "WITH line[0] as a, line[1] as b LIMIT 10000\n" +
      "MERGE (p:User {id: a})\n" +
      "MERGE (p2:User {id:b})\n" +
      "MERGE (p)-[:KNOWS]->(p2)");
      long endTime = System.currentTimeMillis();
      LOG.warn("Time to import: " + (endTime - startTime) + "ms");
    }
    LOG.warn("... end");
    
    database.shutdown();
    /*if[NEO4J_2_3]
      database = new TestGraphDatabaseFactory()
              .newEmbeddedDatabaseBuilder(new File(databasePath))
              .loadPropertiesFromFile(this.getClass().getClassLoader().getResource("neo4j-noga.properties").getPath())
              .newGraphDatabase();
    end[NEO4J_2_3]*/
      
    /*if[NEO4J_2_2_5]
      database = new TestGraphDatabaseFactory()
              .newEmbeddedDatabaseBuilder(databasePath)
              .loadPropertiesFromFile(this.getClass().getClassLoader().getResource("neo4j-noga.properties").getPath())
              .newGraphDatabase();
    end[NEO4J_2_2_5]*/
    
    
    List<String> people = new ArrayList<>();
    
    try (Transaction tx = database.beginTx())
    {
      Result result = database.execute("MATCH (n:User) return n.id LIMIT " + maxPeople);
      while (result.hasNext())
      {
        Map<String, Object> row = result.next();
        people.add((String) row.get("n.id"));
      }
      tx.success();
    }

    SummaryStatistics timeStatistics = new SummaryStatistics();
    SummaryStatistics resultStatistics = new SummaryStatistics();
    for (String name : people)
    {
      try (Transaction tx = database.beginTx())
      {
        long start = System.currentTimeMillis();
        Result execute = database.execute("MATCH (a:User {id:\"" + name + "\"})-[:KNOWS]->(b:User)-[:KNOWS]->(c:User) \n"
                + "WHERE NOT (a)-[:KNOWS]->(c) \n"
                + "RETURN count(c)");
        if (execute.hasNext())
        {
          Long res = (Long) execute.next().get("count(c)");
          resultStatistics.addValue(res);
        }
        tx.success();
        long end = System.currentTimeMillis();
        timeStatistics.addValue(end - start);
      }
      if (resultStatistics.getN() % 10000 == 0)
        LOG.warn("Processed: " + resultStatistics.getN());
    }
    LOG.warn("\nTotal time: " + timeStatistics.getSum() + "ms\n"
            + "Queries: " + timeStatistics.getN() + "\n"
            + "Mean: " + timeStatistics.getMean() + "ms\n"
            + "Min: " + timeStatistics.getMin() + "ms\n"
            + "Max: " + timeStatistics.getMax() + "ms\n"
            + "Variance: " + timeStatistics.getVariance() + "ms\n"
            + "Results Min: " + resultStatistics.getMin() + "\n"
            + "Results Max: " + resultStatistics.getMax() + "\n"
            + "Results Mean: " + resultStatistics.getMean() + "\n");
  }
}
