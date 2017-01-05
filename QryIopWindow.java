import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.Iterator;
import java.util.Arrays;

public class QryIopWindow extends QryIop{
	
	private int distance;
	
	public QryIopWindow(int nearDist){
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

//	        System.out.println("DocId: "+ Idx.getExternalDocid(minDocid));
	        boolean print = false;
//	        if (Idx.getExternalDocid(minDocid).trim().compareTo("clueweb09-en0005-08-29709")==0){
//	        	print = true;
//		        System.out.println("Found DocId: "+ minDocid);
//		        for (Qry q_i: this.args) {
//		        	((QryIop) q_i).invertedList.print();
//		        }
//	        }
	        //List of vectors storing the document locations for each query argument
	        List<Vector<Integer>> positions = new ArrayList<Vector<Integer>>();
	        
	        //window is a flag used to check if the near operator is satisfied 
	        //for a combination of locations for each query argument
	        int window = 1;
	        
	        //Retrieve the locations for each argument in the query and store in positions list
	        //Set window=0 if there is no match for the document with an argument
	        for (Qry q_i: this.args) {
	        	if (q_i.docIteratorHasMatch (null) &&
			              (q_i.docIteratorGetMatch () == minDocid)){
	        		Vector<Integer> locations = ((QryIop) q_i).docIteratorGetMatchPosting().positions;
	        		positions.add(locations);
	        		q_i.docIteratorAdvancePast (minDocid); //Progress doc pointers for q_i that point to minDocid
	        	}
	        	else{ 			//No match on minDocid for this query argument => Set near=0, but don't break yet, 
	        					//continue advancing doc pointers of other arguments
	        		window = 0;		     
	        	}		              
	        }
	        
	        
	        List<Integer> res = new ArrayList<Integer>();
	        
	        if (window == 0)
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
		        		
		        		//Add the current locations of all the arguments in compareArray
		        		List<Integer> compareArray = new ArrayList<Integer>(this.args.size());
		        		for (int j = 0; j<this.args.size(); j++){
		        			compareArray.add(positions.get(j).get(cur_idx[j]));
		        		}
		   
//		        		if (print){
//				        	System.out.println("Compare Array: ");
//				        	for (int j = 0; j<this.args.size(); j++){
//				      			System.out.println(compareArray.get(j));
//				       		}
//			        	}
		        		
		        		Collections.sort(compareArray);
		        		if (1 + compareArray.get(this.args.size()-1) - compareArray.get(0) > this.distance )
		        			flag = 0;
		        		else
		        			flag = 1;
		        		
		        		//Window query satisfied by all pairs of query arguments => Add to result
		        		if (flag==1) {
		        			//Add the max argument's location index to result
		        			int maximum = Integer.MIN_VALUE;
		        			for (int i = 0; i < positions.size(); i++)
		        				if (positions.get(i).get(cur_idx[i]) > maximum)
		        					maximum = positions.get(i).get(cur_idx[i]);
		        			
//		        			res.add(positions.get(this.args.size()-1).get(cur_idx[this.args.size()-1]));
		        			res.add(compareArray.get(this.args.size()-1));
//		        			if (print)
//		        				System.out.println("Added location "+ compareArray.get(this.args.size()-1));
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
//	    this.invertedList.print();

	}		
		
	
	//Override method to display operator name
	public String toString(){
		String result = new String ();

	    for (int i=0; i<this.args.size(); i++)
	      result += this.args.get(i) + " ";

	    return (this.getDisplayName() + "( " + result + ")");
	 
	}
}
