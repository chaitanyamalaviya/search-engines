/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

/**
 *  The WSUM operator.
 */
public class QrySopWsum extends QrySop {

  public List<Float> weights = new ArrayList<Float>();
  
  public void addweight (float weight){
		this.weights.add(weight);
	}
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelBM25){
        return this.getScoreBM25(r);
      }
    else if (r instanceof RetrievalModelIndri){
        return this.getScoreIndri(r);
      }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the WSUM operator.");
    }
  }
  
  /**
   *  getScore for the BM25 retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  
  private double getScoreBM25 (RetrievalModel r) throws IOException {
	  if (! this.docIteratorHasMatchCache()) {
		  return 0.0;
	  }
	  else{
		  double score = 0.0;
		  for (Qry q_i : this.args){
			  if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch()==this.docIteratorGetMatch()){
				  score += ((QrySop) q_i).getScore(r);
			  }
		  }
		  return score;
	  }
  }
  
  /**
   *  getScore for the Indri retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreIndri (RetrievalModel r) throws IOException {
	  if (! this.docIteratorHasMatchCache()) {
		  return 0.0;
	  }
	  else{
		  double res = 0.0;
		  int i = 0; //Index of the query argument in iteration
		  float weightsum = 0;
			
		  for (int j = 0; j < this.weights.size(); j++){
			weightsum += this.weights.get(j);
		  }
			
		  for (Qry q_i : this.args){
			  if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch()==this.docIteratorGetMatch())
				  res += this.weights.get(i)*((QrySop) q_i).getScore(r);
			  else  //If unseen word, call getDefaultScore
				  res += this.weights.get(i)*((QrySop) q_i).getDefaultScore(r, this.docIteratorGetMatch());  
			  i++;
		  }
		  return res/weightsum;
	  }
  }
  
  /**
   *  Default getScore method for WSUM.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getDefaultScore (RetrievalModel r, long docid) throws IOException{
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		}
		else{
			double res = 0;
			int i = 0;
			float weightsum = 0;
				
			for (int j = 0; j < this.weights.size(); j++){
				weightsum += this.weights.get(j);
			}
			for (Qry q_i : this.args){
				res += this.weights.get(i)*((QrySop) q_i).getDefaultScore(r, this.docIteratorGetMatch()); 
				i++;
			}
			return res/weightsum;
		}
	}
  
  
}
