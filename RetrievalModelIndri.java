
public class RetrievalModelIndri extends RetrievalModel {
	protected double mu;
	protected double lambda;
	protected String fb;
	protected int fbDocs;
	protected int fbTerms;
	protected int fbMu;
	protected double fbOrigWeight;
	protected String fbInitialRankingFile;
	protected String fbExpansionQueryFile;
	
	
	public RetrievalModelIndri(double mu, double lambda){
		this.mu = mu;
		this.lambda = lambda;
	}
	

	public RetrievalModelIndri(double mu, double lambda, String fb, int fbDocs, int fbTerms,
			int fbMu, double fbOrigWeight, String fbInitialRankingFile, String fbExpansionQueryFile) {
		
		this.mu = mu;
		this.lambda = lambda;
		this.fb = fb;
		this.fbDocs = fbDocs;
		this.fbTerms = fbTerms;
		this.fbMu = fbMu;
		this.fbOrigWeight = fbOrigWeight;
		this.fbInitialRankingFile = fbInitialRankingFile;
		this.fbExpansionQueryFile = fbExpansionQueryFile;
	}


	public String defaultQrySopName (){
		return new String("#and	");
	}
}
