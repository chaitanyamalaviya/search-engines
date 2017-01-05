
import java.io.*;
import java.util.*;

public class FeatureVector {
	
	public String qId;
	public int docId;
	public HashMap<Integer, Double> scores;
	
	public FeatureVector(String qId, int docId){
		this.qId = qId;
		this.docId = docId;
		this.scores = new HashMap<Integer, Double>();
	}
	public void addFeature(int f_idx, double score){
		this.scores.put(f_idx, score);
	}
	
	public void updateFeature(int f_idx, double score){
		this.scores.put(f_idx, score);
	}
	
	public Double getFeatureVal(int f_idx){
		if (this.scores.containsKey(f_idx))
			return this.scores.get(f_idx);
		else 
			return null;
	}
	
	public int featureSize(){
		return this.scores.size();
	}
}