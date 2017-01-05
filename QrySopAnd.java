
import java.io.*;

public class QrySopAnd extends QrySop{
	
	public boolean docIteratorHasMatch(RetrievalModel r){
		if (r instanceof RetrievalModelIndri)
			return this.docIteratorHasMatchMin(r);
		else
			return this.docIteratorHasMatchAll(r);
	}
	
	public double getScore(RetrievalModel r) throws IOException {
		if (r instanceof RetrievalModelUnrankedBoolean){
			return this.getScoreUnrankedBoolean(r);
		}
		
		else if (r instanceof RetrievalModelRankedBoolean){
			return this.getScoreRankedBoolean(r);
		}
		
		else if (r instanceof RetrievalModelIndri){
			return this.getScoreIndri(r);
		}
	
		else{
			throw new IllegalArgumentException
	        (r.getClass().getName() + " doesn't support the AND operator.");
		}
	}
	
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
			  //Iterate over query arguments and return their minimum score
			  double minimum = Double.MAX_VALUE;
			  double score = 0;
			  for (Qry q_i : this.args){
				  if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch()==this.docIteratorGetMatch()){
					  score = ((QrySop) q_i).getScore(r);
					  if (minimum > score)
						  minimum = score;
				  }
			  }
			 return minimum; 
		}
	}
	
	
//	private double getScoreIndri (RetrievalModel r) throws IOException {
//		if (! this.docIteratorHasMatchCache()) {
//			return 0.0;
//		}
//		else{
//			double res = 1.0;
//			double score = 1.0;
//			int docid = this.docIteratorGetMatch();
//			double p = (double)1.0/this.args.size();
////			System.out.println("Docid: "+docid);
//			for (Qry q_i : this.args){
//				  if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid){
////					  System.out.println(Math.pow(((QrySop) q_i).getScore(r), 1.0/this.args.size()));
//					  res = ((QrySop) q_i).getScore(r);
//					  }
//				  else{
//					  res = ((QrySop) q_i).getDefaultScore(r, docid);
////					  if (res == 0.0)
////						  continue;
//					  }
//				  System.out.println("Docid: " + docid + " and score: " + res);
//				  score *= res;
//			  }
//			return Math.pow(score, p);
//		}
//		
//	}
//	
//	public double getDefaultScore (RetrievalModel r, long docid) throws IOException{
//		if (! this.docIteratorHasMatchCache()) {
//			return 0.0;
//		}
//		else{
//			double res = 1.0;
//			double score = 1.0;
//			double p = (double)1.0/this.args.size();
//			for (Qry q_i : this.args){
//				res = ((QrySop) q_i).getDefaultScore(r, docid);
//				score *= res;
//			}
//			return Math.pow(score, p);
//		}
//	}
	private double getScoreIndri (RetrievalModel r) throws IOException {
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		}
		else{
			double res = 1.0;
			double score = 1.0;
			int docid = this.docIteratorGetMatch();
			double p = (double)1.0/this.args.size();
//			System.out.println("Docid: "+docid);
			for (Qry q_i : this.args){
				  if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid){
//					  System.out.println(Math.pow(((QrySop) q_i).getScore(r), 1.0/this.args.size()));
					  res = (double)Math.pow(((QrySop) q_i).getScore(r), p);
					  }
				  else{
					  res = (double)Math.pow(((QrySop) q_i).getDefaultScore(r, docid), p);
//					  if (res == 0.0)
//						  continue;
					  }
				  score *= res;
			  }
//			System.out.println("Score for " + docid + ": " + res);
			return score;
		}
	}
	
	public double getDefaultScore (RetrievalModel r, long docid) throws IOException{
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		}
		else{
			double res = 1.0;
			double score = 1.0;
			double p = (double)1.0/this.args.size();
			for (Qry q_i : this.args){
				res = (double)Math.pow(((QrySop) q_i).getDefaultScore(r, docid), p);
//				System.out.println("Default score: " + res);
//				if (res==0)
//					continue;
				score *= res;
			}
			return score;
		}
	}
}
