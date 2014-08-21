package de.kp.spark.weblog
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

import org.apache.spark.rdd.RDD

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

object LogEvaluator {

  /*
   * A set of indicators to specified whether a certain conversion goal 
   * has been achieved or not
   */
  val FLOW_NOT_ENTERED = 0  
  val FLOW_ENTERED     = 1
  val FLOW_COMPLETED   = 2

  /**
   * This method is directly applied to the extraction result; it specifies a first aggregation 
   * step for the raw click data and determines the time spent on a certain page in seconds, and 
   * assigns a specific rating from a predefined time rating (see configuration)
   * 
   * Input: session = (sessionid,timestamp,userid,pageurl,visittime,referrer)
   */
  def eval1(source:RDD[(String,Long,String,String,String,String)]):RDD[String] = {

    val sc = source.context
    val ratings = sc.broadcast(Configuration.ratings)
    
    /* Group source by sessionid */
    val dataset = source.groupBy(group => group._1)
    dataset.flatMap(valu => {
      
      /* Sort session data by timestamp */
      val data = valu._2.toList.sortBy(_._2)
      
      /* Compute page time = difference between two session events */ 
      var sessid:String = null
      var userid:String = null
      
      var lasturl:String  = null
      var referrer:String = null
      
      var starttime:Long = 0
      var endtime:Long   = 0
      
      var visittime:String = null
      
      var first = true
      
      val output = ArrayBuffer.empty[String]

      for (entry <- data) {
        
        if (first) {     
          
          var (sessid,starttime,userid,lasturl,visittime,referrer) = entry
          
          endtime = starttime
          first = false
          
        } else {     

          val timespent  = (entry._2 - endtime) / 1000

          /* Compute rating from pagetime */
          var rating = 0
          breakable {for (entry <- ratings.value) {
            
            if (timespent < entry._1) {
              rating = entry._2
              break
            }
            
          }} 
          
          rating = if (rating == 0) ratings.value.last._2 else rating
          
          val out = sessid + "|" + userid + "|" + starttime + "|" + lasturl + "|" + visittime + "|" + referrer + "|" + timespent + "|" + rating
          output += out

          endtime = entry._2
          lasturl = entry._4
        
          visittime = entry._5
      
        }
      
      }
      
      /* Last page */
      val rating    = 0
      val timespent = 0
        
      val out = sessid + "|" + userid + "|" + starttime + "|" + lasturl + "|" + visittime + "|" + referrer + "|" + timespent + "|" + rating
      output += out

      output
      
    })
     
  }
  /**
   * This method is directly applied to the extraction result; it specifies another aggregation 
   * step for the raw click data 
   * 
   * Sample evaluation:
   * 
   * Checkout abandonment is an important metric. It’s the ratio of the number of sessions 
   * that abandoned a checkout process and the total number of sessions that entered the 
   * checkout process.
   * 
   * Although the focus is on conversion, some other important and insightful metrics 
   * can be derived. Here are some further examples:  
   * 
   * - Bounce rate, i.e., number of sessions that end after the landing page
   * - Average session duration
   * - Site penetration i.e., average number of pages visited per session 
   * - User visit time distribution in a 24 hour period 
   *
   * - Conversion rate i.e., percentage of unique users converting
   * - Average number of visits before conversion
   * - Average number of visits per month
   * - Average time gap between visits, which is indicative of customer loyalty
   * - Average number of purchases per year, which is also a good metric for customer loyalty
   * - Average time gap between purchases
   * 
   * 
   * Input: session = (sessionID,timestamp,userID,pageURL,visitTime,referrer)
   * 
   */
  def eval2(source:RDD[(String,Long,String,String,String,String)],flow:Array[String]):RDD[(String,String,Int,Long,Long,String,String,Int)] = {
 
    /* Group source by sessionid */
    val dataset = source.groupBy(group => group._1)
    dataset.map(valu => {
      
      /* Sort session data by timestamp */
      val data = valu._2.toList.sortBy(_._2)

      val pages = ArrayBuffer.empty[String]

      var sessid:String = null
      var userid:String = null
      
      var lasturl:String  = null
      var referrer:String = null
      
      var starttime:Long = 0
      var endtime:Long   = 0
      
      var visittime:String = null
      
      var first = true
      for (entry <- data) {

        if (first) {
          
          var (sessid,starttime,userid,lasturl,visittime,referrer) = entry         
          first = false
          
        } else {

          endtime = entry._2
          
        }
          
        pages += entry._4
       
      }
      
      /* Total number of page clicks */
      val total = pages.size
      
      /* Total time spent for session */
      val timespent = (if (total > 1) (endtime - starttime) / 1000 else 0)
      val exiturl = pages(total - 1)
      
      /*
       * This is a simple session evaluation to determine whether the sequence of
       * pages per session matches with a predefined page flow
       */
      val flowstatus = checkFlow(pages)      
      (sessid,userid,total,starttime,timespent,referrer,exiturl,flowstatus)
      
    })
    
  }

  private def checkFlow(pages:ArrayBuffer[String]):Int = { 			
    		
    val FLOW = Configuration.flow
    var j = 0
    var	flowStat = FLOW_NOT_ENTERED
    		
    var matched = false;
    		
    for (i <- 0 until FLOW.length) {
    			
      breakable {while (j < pages.size) {
    				
        matched = false
        /*
         * We expect that a certain page url has to start with the 
         * configured url part of the flow
         */
    	if (pages(j).startsWith(FLOW(i))) {
    	  flowStat = (if (i == FLOW.length - 1) FLOW_COMPLETED else FLOW_ENTERED)
    	  matched = true
    				
    	}
    	j += 1
    	if (matched) break
    			
      }}
    
    }

    flowStat
    
  }

}