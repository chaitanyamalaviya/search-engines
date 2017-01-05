/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {
	
  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
	  
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } 
    else if (r instanceof RetrievalModelRankedBoolean) {
      return this.getScoreRankedBoolean (r);
    }
    else if (r instanceof RetrievalModelBM25){
      return this.getScoreBM25(r);
    }
    else if (r instanceof RetrievalModelIndri){
        return this.getScoreIndri(r);
      }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } 
    else {
      return 1.0;
    }
  }
  
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
	if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } 
    else {  
//      System.out.println(this.docIteratorGetMatch());
//      System.out.println(((QryIop) this.args.get(0)).invertedList.getTf(this.docIteratorGetMatch()));
//      ((QryIop) this.args.get(0)).invertedList.print();
    	
    	//Since score operator has only one argument, retrieve its 1st argument and 
    	//get the term frequency of the document the iterator points to
    	return ((QryIop) this.args.get(0)).docIteratorGetMatchPosting().tf;
    }
  }
  
  
  //Method to calculate score for the BM25 retrieval model
  public double getScoreBM25( RetrievalModel r) throws IOException{
	  QryIop q = (QryIop)this.args.get(0);
	  
	  double rsj = Math.max(0 , Math.log ( (double)(Idx.getNumDocs() - q.getDf() + 0.5) / (q.getDf() + 0.5)));
	  double b = ((RetrievalModelBM25) r).b;
	  double k1 = ((RetrievalModelBM25) r).k1;
	  double k3 = ((RetrievalModelBM25) r).k3;
	  double doc_len = Idx.getFieldLength(q.field, q.docIteratorGetMatch());
	  double avg_doc_len = (double)Idx.getSumOfFieldLengths(q.field) / Idx.getDocCount(q.field);
	  double tf = q.docIteratorGetMatchPosting().tf ;
	  double frac = (double)doc_len/avg_doc_len;
	  double term_freq = (double) tf / (tf + k1 * ( (1-b) + (b * frac)));
	  double qtf = 1.0;
	  double userweight = (double) (k3 + 1) * qtf / (k3 + qtf);
	  
	  return rsj * term_freq * userweight;
			  
  }
	  
  //Method to calculate score for the Indri retrieval model
  public double getScoreIndri( RetrievalModel r ) throws IOException{
	  QryIop q = (QryIop)this.args.get(0);
	  int docid = q.docIteratorGetMatch();
	  double mu = ((RetrievalModelIndri) r).mu;
	  double lambda = ((RetrievalModelIndri) r).lambda;
	  double p_mle = (double)q.getCtf()/Idx.getSumOfFieldLengths(q.field);
	  double doc_len = Idx.getFieldLength(q.field, docid);
	  double tf = q.docIteratorGetMatchPosting().tf;

	  return (1.0-lambda) * (tf + mu * p_mle) / (doc_len + mu) + lambda * p_mle;

  }
  
  //Method to calculate the default score for the Indri operator
  public double getDefaultScore( RetrievalModel r, long docid) throws IOException{
	  QryIop q = (QryIop)this.args.get(0);
	  double mu = ((RetrievalModelIndri) r).mu;
	  double lambda = ((RetrievalModelIndri) r).lambda;
	  double p_mle = (double)q.invertedList.ctf/Idx.getSumOfFieldLengths(q.field);
	  double doc_len = Idx.getFieldLength(q.field, (int)docid);
	  
	  return (1.0 - lambda) * (mu * p_mle) / (doc_len + mu) + 
			  lambda * p_mle;
  }
  

  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get(0);
    q.initialize (r);
  }

}
