package de.kp.spark.weblog.sink
/* Copyright (c) 2014 Dr. Krusche & Partner PartG
* 
* This file is part of the Spark-Weblog project
* (https://github.com/skrusche63/spark-weblog).
* 
* Spark-Weblog is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* Spark-Weblog is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* Spark-Weblog. 
* 
* If not, see <http://www.gnu.org/licenses/>.
*/

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import org.apache.spark.sql.SQLContext

import org.apache.hadoop.fs.{FileSystem,Path}
import org.apache.hadoop.conf.{Configuration => HadoopConf}

import de.kp.spark.core.Names
import de.kp.spark.core.model._

import de.kp.spark.weblog.{Configuration,BayesianModel}
import de.kp.spark.weblog.model._

class W3LogSink(@transient sc:SparkContext) extends Serializable {
  /**
   * Bayesian based click-conversion correlations are saved and retrieved
   * using a Redis instance
   */
  def getClickModel(req:ServiceRequest):BayesianModel = {

    val sink = new RedisSink()
    val predictions = Serializer.deserializeClickPredictions(sink.model(req))
    
    val probabilities = predictions.items.map(entry => (entry.clicks,entry.probability)).toMap
    new BayesianModel(probabilities)
  
  }

  def saveClickModel(req:ServiceRequest,model:BayesianModel) {
    
    val sink = new RedisSink()
    val predictions = ClickPredictions(model.probabilities.map(entry => ClickPrediction(entry._1,entry._2)).toList)
    
    sink.addModel(req,Serializer.serializeClickPredictions(predictions))
    
  }
  
  def saveLogFlows(req:ServiceRequest,dataset:RDD[LogFlow]) {
    
    req.data(Names.REQ_SINK) match {
      
      case Sinks.PARQUET => {    
        /*
         * Delete file on the file system 
         */
        val flowfile = (Configuration.output(0) + "/flows/" + req.data(Names.REQ_UID))
      
        val fs = FileSystem.get(new HadoopConf())      
        fs.delete(new Path(flowfile), true)     

        val sqlCtx = new SQLContext(sc)
        import sqlCtx.createSchemaRDD

        /* 
         * The RDD is implicitly converted to a SchemaRDD by createSchemaRDD, 
         * allowing it to be stored using Parquet. 
         */
        dataset.saveAsParquetFile(flowfile)
        
      }
      
      case _ => throw new Exception("Data sink is not supported.")
    }
    
  }
  
  def saveLogPages(req:ServiceRequest,dataset:RDD[LogPage]) {
    
    req.data(Names.REQ_SINK) match {
      
      case Sinks.PARQUET => {    
        /*
         * Delete file on the file system 
         */
        val pagefile = (Configuration.output(0) + "/pages/" + req.data(Names.REQ_UID))
      
        val fs = FileSystem.get(new HadoopConf())      
        fs.delete(new Path(pagefile), true)     

        val sqlCtx = new SQLContext(sc)
        import sqlCtx.createSchemaRDD

        /* 
         * The RDD is implicitly converted to a SchemaRDD by createSchemaRDD, 
         * allowing it to be stored using Parquet. 
         */
        dataset.saveAsParquetFile(pagefile)
        
      }
      
      case _ => throw new Exception("Data sink is not supported.")
    }
    
  }
  
}