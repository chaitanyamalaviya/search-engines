/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

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

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean(r);
    } 
    else if (r instanceof RetrievalModelRankedBoolean) {
      return this.getScoreRankedBoolean(r);
    }
    else if (r instanceof RetrievalModelIndri) {
        return this.getScoreIndri(r);
      }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } 
    else {
      return 1.0;
    }
  }
  
  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
	  
	  if (! this.docIteratorHasMatchCache()) {
		  return 0.0;
	  }
	  else{
		  //Iterate over query arguments and return their maximum score
		  double maximum = Double.MIN_VALUE;
		  double score = 0;
		  for (Qry q_i : this.args){
			  if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch()==this.docIteratorGetMatch()){
				  score = ((QrySop) q_i).getScore(r);
				  if (maximum < score)
					  maximum = score;
			  }
		  }
		  return maximum;
	  } 
  }
  
  private double getScoreIndri (RetrievalModel r) throws IOException {
	  if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		}
		else{
			double res = 1.0;
			for (Qry q_i : this.args){
				  if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == this.docIteratorGetMatch())
					  res *= 1- ((QrySop) q_i).getScore(r);
				  else
					  res *= 1 - ((QrySop) q_i).getDefaultScore(r, this.docIteratorGetMatch());
			  }
			return (1 - res);
		}
		
	}
	
	public double getDefaultScore (RetrievalModel r, long docid) throws IOException{
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		}
		else{
			double res = 1.0;
			for (Qry q_i : this.args){
				res *= 1 - ((QrySop) q_i).getDefaultScore(r, docid);
			}
			return (1 - res);
		}
	}
}
