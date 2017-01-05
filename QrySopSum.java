/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The SUM operator.
 */
public class QrySopSum extends QrySop {

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
        (r.getClass().getName() + " doesn't support the SUM operator.");
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
  
  private double getScoreIndri (RetrievalModel r) throws IOException {
	  if (! this.docIteratorHasMatchCache()) {
		  return 0.0;
	  }
	  else{
		  double score = 0.0;
		  for (Qry q_i : this.args){
			  if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch()==this.docIteratorGetMatch())
				  score += ((QrySop) q_i).getScore(r);
			  else
				  score += ((QrySop) q_i).getDefaultScore(r, this.docIteratorGetMatch());
		  }
		  return score;
	  }
  }
  
  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {
	  if (! this.docIteratorHasMatchCache()) {
		  return 0.0;
	  }
	  else{
		  double score = 0.0;
		  for (Qry q_i : this.args){
			  score += ((QrySop) q_i).getDefaultScore(r, docid);
		  }
		  return score;
	  }
  }
  
}
