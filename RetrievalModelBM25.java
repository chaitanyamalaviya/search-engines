
public class RetrievalModelBM25 extends RetrievalModel {
	protected float k1;
	protected float k3;
	protected float b;
	
	public RetrievalModelBM25(float k1, float b, float k3){
		this.k1 = k1;
		this.b = b;
		this.k3 = k3;
	}
	
	public String defaultQrySopName (){
		return new String("#sum	");
	}
}