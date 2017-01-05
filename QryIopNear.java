
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.Iterator;

public class QryIopNear extends QryIop{
	
	private int distance;
	
	public QryIopNear(int nearDist){
		this.distance = nearDist;
	}
	
	protected void evaluate() throws IOException {
		
		this.invertedList = new InvList (this.getField());
		
	    while (true) {

	        //  Find the minimum next document id.  If there is none, we're done.

	        int minDocid = Qry.INVALID_DOCID;

	        for (Qry q_i: this.args) {
	          if (q_i.docIteratorHasMatch (null)) {
	            
	        	int q_iDocid = q_i.docIteratorGetMatch ();	            
	            if ((minDocid > q_iDocid) ||
	                (minDocid == Qry.INVALID_DOCID)) {
	            		minDocid = q_iDocid;
	            }
	          }
	        }

	        if (minDocid == Qry.INVALID_DOCID)
	          break;				// All docids have been processed.  Done.

	        
	        //List of vectors storing the document locations for each query argument
	        List<Vector<Integer>> positions = new ArrayList<Vector<Integer>>();
	        
	        //near is a flag used to check if the near operator is satisfied 
	        //for a combination of locations for each query argument
	        int near = 1;
	        
	        //Retrieve the locations for each argument in the query and store in positions list
	        //Set near=0 if there is no match for the document with an argument
	        for (Qry q_i: this.args) {
	        	if (q_i.docIteratorHasMatch (null) &&
			              (q_i.docIteratorGetMatch () == minDocid)){
	        		Vector<Integer> locations = ((QryIop) q_i).docIteratorGetMatchPosting().positions;
	        		positions.add(locations);
	        		q_i.docIteratorAdvancePast (minDocid); //Progress doc pointers for q_i that point to minDocid
	        	}
	        	else{ 			//No match on minDocid for this query argument => Set near=0, but don't break yet, 
	        					//continue advancing doc pointers of other arguments
	        		near = 0;		     
	        	}		              
	        }
	        
	        
	        List<Integer> res = new ArrayList<Integer>();
	        
	        if (near==0)
	        	continue;
	        
	        else{
	        		//cur_idx maintains the index of the current location in an argument's location vector
       				int[] cur_idx  = new int[positions.size()];
       				//k is the index of query argument that has been progressed in current iteration
       				int k = 0;
       				//Indicates iteration has finished
       				int over = 0;
       				
       				//while the size of location vector of the query argument progressed in 
       				//previous iteration is greater than its current index 
       				while (cur_idx[k] < positions.get(k).size()){
       					
//       					System.out.print("Current Index Array: ");
//       					for (int j=0;  j < this.args.size(); j++){ 
//       						System.out.print(positions.get(j).get(cur_idx[j])+" ");
//       						System.out.println();
//       					}
       					
		        		int flag = 1;
		        		for (int j = 0; j < this.args.size() - 1; j++){
		        			//check the near operator condition and check that every argument's location index is greater than previous argument's index
		        			if ((positions.get(j+1).get(cur_idx[j+1]) - positions.get(j).get(cur_idx[j]) > this.distance)
		        					|| (positions.get(j+1).get(cur_idx[j+1]) < positions.get(j).get(cur_idx[j]))){
		        				flag = 0; //near condition not satisfied for the combination of location indices of the arguments
		        				break;
		        			}	        			
		        		}
		        		
		        		//Near query satisfied by all adjacent pairs of query arguments => Add to result
		        		if (flag==1) {				
		        			//Add the last argument's location index to result
		        			res.add(positions.get(this.args.size()-1).get(cur_idx[this.args.size()-1]));
		        			for (int i=0; i<positions.size(); i++){   //Progress all pointers
		        				cur_idx[i]++;
		        				
		        				//If the location index has exceeded the size of location vector for any argument, we are done.
		        				if (cur_idx[i] >= positions.get(i).size())
		        					over = 1;
		        				else
		        					k = 0;
		        			}
		        		}
		        		
		        		else{
		        			//Progress the location pointer of argument with minimum location index
		        			int minLoc = Integer.MAX_VALUE;
		        			for (int i=0; i<positions.size(); i++){
		        				if (minLoc > positions.get(i).get(cur_idx[i])){
		        					minLoc = positions.get(i).get(cur_idx[i]);
		        					k = i;
		        				}
		        			}
		        			//Increment minimum location argument's index
		        			cur_idx[k]++;
		        			
		        			
		        			
//		        			cur_idx[0]++;
//		        			if (cur_idx[0]<positions.get(0).size()){
//			        			for (int i=1; i<positions.size(); i++){
//			        				if (cur_idx[k] < positions.get(k).size()){
//				        				if (positions.get(i).get(cur_idx[i]) < positions.get(0).get(cur_idx[0])){
//				        					cur_idx[i]++;
//				        				}
//			        				}
//			        			}
//			        		}
//		        			else
//		        				over=1;
		        			
		        			
		        			
		        			
		        			
		        		}
		        		if (over == 1) 
		        			break;
       				}
	        	}
	        
	        if (res.size()>0){
		        Collections.sort(res);
		        
//		        System.out.print("Adding posting for "+ minDocid + " ");
//		        for (int i = 0; i < res.size(); i++)
//		        	System.out.print( res.get(i) + " " );
//		        System.out.println();
		        
		        this.invertedList.appendPosting (minDocid, res);
		    }
	    }

	}		
	
	
	
	//Override method to display operator name
	public String toString(){
		String result = new String ();

	    for (int i=0; i<this.args.size(); i++)
	      result += this.args.get(i) + " ";

	    return (this.getDisplayName() + "( " + result + ")");
	 
	}
}
