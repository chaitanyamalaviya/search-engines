
import java.io.*;

public class RetrievalModelLetor extends RetrievalModel {
	
	protected double mu;
	protected double lambda;
	protected float k1;
	protected float k3;
	protected float b;
	
	public RetrievalModelLetor(double mu, double lambda, float k1, float b, float k3){
		this.mu = mu;
		this.lambda = lambda;
		this.k1 = k1;
		this.b = b;
		this.k3 = k3;
	}

	@Override
	public String defaultQrySopName() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
