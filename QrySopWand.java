
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class QrySopWand extends QrySop{
	
	public List<Float> weights = new ArrayList<Float>();
	
	public void addweight (float weight){
		this.weights.add(weight);
	}
	
	public boolean docIteratorHasMatch(RetrievalModel r){
		return this.docIteratorHasMatchMin(r);
	}
	
	public double getScore(RetrievalModel r) throws IOException {
		if (r instanceof RetrievalModelIndri){
			return this.getScoreIndri(r);
		}
	
		else{
			throw new IllegalArgumentException
	        (r.getClass().getName() + " doesn't support the WAND operator.");
		}
	}
	
	  /**
	   *  getScore method for Indri retrieval model
	   *  @param r The retrieval model that determines how scores are calculated.
	   *  @return The document score.
	   *  @throws IOException Error accessing the Lucene index
	   */
	private double getScoreIndri (RetrievalModel r) throws IOException {
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		}
		else{
			double res = 1.0;
			int i = 0;
			float weightsum = 0;
			
			for (int j = 0; j < this.weights.size(); j++){
				weightsum += this.weights.get(j);
			}
			for (Qry q_i : this.args){
				  if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == this.docIteratorGetMatch())
					  res *= Math.pow(((QrySop) q_i).getScore(r), (double)this.weights.get(i)/weightsum);
				  else  //If unseen word in the document, call default score method
					  res *= Math.pow(((QrySop) q_i).getDefaultScore(r, this.docIteratorGetMatch()), (double)this.weights.get(i)/weightsum);
				  i++;
			}
			return res;
		}
	}
	
	  /**
	   *  Default getScore method for WAND.
	   *  @param r The retrieval model that determines how scores are calculated.
	   *  @return The document score.
	   *  @throws IOException Error accessing the Lucene index
	   */
	public double getDefaultScore (RetrievalModel r, long docid) throws IOException{
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		}
		else{
			double res = 1.0;
			float weightsum = 0;
			int i = 0;
			
			for (int j = 0; j < this.weights.size(); j++){
				weightsum += this.weights.get(j);
			}
			
			for (Qry q_i : this.args){
				res *= Math.pow(((QrySop) q_i).getDefaultScore(r, docid), (double)this.weights.get(i)/weightsum);
				i++;
			}
			return res;
		}
	}
	
}
