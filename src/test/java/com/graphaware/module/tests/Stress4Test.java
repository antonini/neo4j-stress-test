
package com.graphaware.module.tests;

import com.graphaware.module.batchinsert.GABatchInserter;
import com.graphaware.module.batchinsert.GACSVFileSource;
import org.junit.Test;
import org.neo4j.graphdb.*;

import java.io.IOException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.Before;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

public class Stress4Test
         //extends GraphAwareApiTest
{

  private static final Logger LOG = LoggerFactory.getLogger(Stress4Test.class);
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

  @Before
  public void setUp()
  {
  }

  @Test
  public void test() throws IOException
  {

    String path = System.getProperty("databaseCSVPath");


    
    String databasePath = "";
    /*if[NEO4J_2_3]
      databasePath = "/tmp/graph_2.3.db";
    end[NEO4J_2_3]*/
      
    /*if[NEO4J_2_2_5]
      databasePath = "/tmp/graph_2.2.5.db";
    end[NEO4J_2_2_5]*/
    
    if (!new File(databasePath).exists())
    {
      GACSVFileSource source = new GACSVFileSource(path, "\t", "id");
      GABatchInserter inserter = new GABatchInserter(source, databasePath);
      long startTime = System.currentTimeMillis();
      inserter.load("User", "id");
      long endTime = System.currentTimeMillis();
      LOG.warn("Time to import: " + (endTime - startTime) + "ms");
    }
    else
    {
      LOG.warn("Database: " + databasePath + " Already exist");
    }
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
    
    LOG.warn("Creating indices");
    //database.execute("CREATE INDEX ON :User(id)");
    
    
    IndexDefinition indexDefinition;
    try ( Transaction tx = database.beginTx() )
    {
        Schema schema = database.schema();
        indexDefinition = schema.indexFor( DynamicLabel.label( "User" ) )
                .on( "id" )
                .create();
        tx.success();
    }
    LOG.warn("... index created");

    LOG.warn("Waiting for index on line ...");
    try ( Transaction tx = database.beginTx() )
    {
        Schema schema = database.schema();
        schema.awaitIndexOnline( indexDefinition, 10, TimeUnit.DAYS );
    }
    LOG.warn("... index on line");

    
    int maxPeople = 100000;
    List<String> people = new ArrayList<>();
    LOG.warn("Creating list");
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
        Map<String, Object> params = new HashMap<String, Object>();
        params.put( "idp", name );
        Result execute = database.execute("MATCH (a:User {id:{idp}})-[:KNOWS]->(b:User)-[:KNOWS]->(c:User) \n"
                + "USING INDEX a:User(id) \n"
                + "WHERE NOT (a)-[:KNOWS]->(c) \n"
                + "RETURN count(c)", params);
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
