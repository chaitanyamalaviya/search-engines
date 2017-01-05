/*
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.2.
 */
import java.io.*;
import java.lang.reflect.Parameter;
import java.util.*;
import java.lang.Object;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
/**
 *  This software illustrates the architecture for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {
    
    //  --------------- Constants and variables ---------------------
    
    
    private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";
    
    
    //section of the document to check
    private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink" };
    
    
    //  --------------- Methods ---------------------------------------
    
    /**
     *  @param args The only argument is the parameter file name.
     *  @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {
        
        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.
        
        Timer timer = new Timer();
        timer.start ();
        
        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.
        
        if (args.length < 1) {
            throw new IllegalArgumentException (USAGE);
        }
        
        Map<String, String> parameters = readParameterFile (args[0]);
        boolean expand = false;
        boolean diversify = false;
        //  Open the index => Store open index readers and store docLengthStore for indexPath
        //  Initialize the retrieval model.
        RetrievalModel model = null;
        Idx.open (parameters.get ("indexPath"));
        if (parameters.containsKey("retrievalAlgorithm")){
            model = initializeRetrievalModel (parameters);
        }
        
        ArrayList<String> combinedQuery = new ArrayList<String>();
        //  Perform experiments.
        
        if (model instanceof RetrievalModelLetor){
            boolean test = false;
            addFeatures(parameters, model, test);
            float k1 = Float.parseFloat(parameters.get("BM25:k_1"));
            float b = Float.parseFloat(parameters.get("BM25:b"));
            float k3 = Float.parseFloat(parameters.get("BM25:k_3"));
            processQueryFile(parameters.get("queryFilePath"), new RetrievalModelBM25(k1, b, k3), false, diversify, 0, parameters.get("trecEvalOutputPath"));
            test = true;
            ArrayList<String> qIdList = addFeatures(parameters, model, test);
            LinkedHashMap<String,ScoreList> scores = readTrecEvalFile(parameters.get("trecEvalOutputPath"), parameters.get("queryFilePath"), 100, model);
            rerank(qIdList, scores, parameters.get("letor:testingDocumentScores"), parameters.get("trecEvalOutputPath"));
        }
        else{
            
            //DIVERSITY RANKINGS
            if (!parameters.containsKey("diversity")){
                diversify = false;
                processQueryFile(parameters.get("queryFilePath"), model, false, diversify, 0, parameters.get("trecEvalOutputPath"));
            }
            
            else if (parameters.get("diversity").equals("false")){
                diversify = false;
                processQueryFile(parameters.get("queryFilePath"), model, false, diversify, 0, parameters.get("trecEvalOutputPath"));
            }
            
            else{
                diversify = true;
                LinkedHashMap<String, ScoreList> initRankings = new LinkedHashMap<String, ScoreList>();
                int maxInputRankings = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
                
                if (parameters.containsKey("diversity:initialRankingFile")){
                    initRankings = readTrecEvalFile(parameters.get("diversity:initialRankingFile"), parameters.get("queryFilePath"), maxInputRankings, model);
                    //    			initRankings.putAll(readTrecEvalFile(parameters.get("diversity:initialRankingFile"), parameters.get("diversity:intentsFile"), maxInputRankings, model));
                }
                
                else{
                    initRankings = processQueryFile(parameters.get("queryFilePath"), model, false, diversify, maxInputRankings, parameters.get("trecEvalOutputPath"));
                    initRankings.putAll(processQueryFile(parameters.get("diversity:intentsFile"), model, false, diversify, maxInputRankings, parameters.get("trecEvalOutputPath")));
                }
                
                ArrayList<String> queries = readQueryFile(parameters.get("queryFilePath"));
                
                //Truncate intent ranking lists if they are longer than initial query ranking list
                for (String qid: queries){
                    
                    Set set = initRankings.entrySet();
                    Iterator iterator = set.iterator();
                    int queryRankingSize = initRankings.get(qid).size();
                    while (iterator.hasNext()){
                        
                        Map.Entry qres = (Map.Entry)iterator.next();
                        String q = (String) qres.getKey();
                        int d = q.indexOf('.');
                        String id = null;
                        if (d!=-1){
                            id = q.substring(0, d).trim();
                        }
                        else{
                            continue;
                        }
                        if (id.equals(qid)){
                            ScoreList rankings = (ScoreList)qres.getValue();
                            rankings.truncate(queryRankingSize);
                            initRankings.put(q, rankings);
                        }
                    }
                }
                
                
                //CHECK NEED FOR NORMALIZATION
                
                if (needNormalization(initRankings, maxInputRankings)){
                    System.out.println("NORMALIZING...");
                    initRankings = normalizeScores(queries, initRankings, maxInputRankings);
                }
                
                float lambda = Float.parseFloat(parameters.get("diversity:lambda"));
                int maxRankings = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
                
                if (parameters.get("diversity:algorithm").equalsIgnoreCase("PM2")){
                    System.out.println("PM2 DIVERSIFY");
                    PM2_Diversify(queries, initRankings, lambda, maxRankings, parameters.get("trecEvalOutputPath"));
                }
                else{
                    XQuad_Diversify(queries, initRankings, lambda, maxRankings, parameters.get("trecEvalOutputPath"));
                }
            }
            
            
            //QUERY EXPANSION
            
            //	    if (!parameters.containsKey("fb")){
            //	    	expand = false;
            //	    	processQueryFile(parameters.get("queryFilePath"), model, expand, false, parameters.get("trecEvalOutputPath"));
            //	    	}
            //
            //	    else if (parameters.get("fb").equals("false")){
            //	    	expand = false;
            //	    	processQueryFile(parameters.get("queryFilePath"), model, expand, false, parameters.get("trecEvalOutputPath"));
            //	    	}
            //
            //	    else{
            //	    	expand = true;
            //	    	if (parameters.containsKey("fbInitialRankingFile")){
            //	    		combinedQuery = expandQuery(readFile(parameters.get("fbInitialRankingFile"), parameters.get("queryFilePath"), model), model);
            //	    		}
            //	    	else{
            //	    		LinkedHashMap<String, ScoreList> docRankings = processQueryFile(parameters.get("queryFilePath"), model, expand, false, parameters.get("trecEvalOutputPath"));
            //	    		combinedQuery = expandQuery(docRankings, model);
            //	    		}
            //	    	}
            
            if (expand==true){
                int i = 0;
                while (i<combinedQuery.size()){
                    String query = combinedQuery.get(i);
                    int d = query.indexOf(':');
                    
                    if (d < 0) {
                        throw new IllegalArgumentException
                        ("Syntax error:  Missing ':' in query line.");
                    }
                    
                    printMemoryUsage(false);
                    
                    //QueryID
                    String qid = query.substring(0, d);
                    //Query String
                    String q = query.substring(d + 1);
                    System.out.println("Combined: " + query);
                    //Obtain score list for each query
                    ScoreList r = null;
                    
                    r = processQuery(q, model);
                    if (r != null) {
                        //write to output file
                        writeResults(qid, r, parameters.get("trecEvalOutputPath"), true);
                        System.out.println();
                    }
                    i += 1;
                }
            }
        }
        //  Clean up.
        
        timer.stop ();
        System.out.println ("Time:  " + timer);
    }
    
    
    public static ArrayList<String> readQueryFile(String queryFilePath) throws IOException{
        
        ArrayList<String> queries = new ArrayList<String>();
        BufferedReader bufferedReader = null;
        String line = null;
        try
        {
            FileReader fileReader = new FileReader(queryFilePath);
            bufferedReader = new BufferedReader(fileReader);
            while((line = bufferedReader.readLine()) != null) {
                int d = line.indexOf(':');
                String qid = line.substring(0, d).trim();
                queries.add(qid);
            }
        }
        catch (IOException e)
        {
            System.err.println("Error: " + e.getMessage());
        }
        finally
        {
            if(bufferedReader != null) {
                bufferedReader.close();
            }
        }
        return queries;
    }
    
    
    public static boolean needNormalization( LinkedHashMap<String, ScoreList> initRankings, int maxInputRankings){
        
        Set set = initRankings.entrySet();
        Iterator iterator = set.iterator();
        while (iterator.hasNext()){
            
            Map.Entry qres = (Map.Entry)iterator.next();
            String q = (String) qres.getKey();
            int d = q.indexOf('.');
            String id = null;
            int maxInput = 1000;
            if (d!=-1){
                id = q.substring(0, d).trim();
                maxInput = Math.min(maxInputRankings, initRankings.get(id).size());
            }
            else{
                maxInput = Math.min(maxInputRankings, initRankings.get(q.trim()).size());
            }
            ScoreList rankings = (ScoreList)qres.getValue();
            maxInput = Math.min(maxInput, rankings.size());
//            System.out.println("Max input rankings: " + maxInputRankings);
            for (int i = 0; i < maxInput; i++){
                if (rankings.getDocidScore(i) > 1.0){
                    return true;
                }
            }
            
        }
        return false;
    }
    
    public static LinkedHashMap<String, ScoreList> normalizeScores(ArrayList<String> queries, LinkedHashMap<String, ScoreList> initRankings, int maxInputRankings){
        
        for (String qid: queries){
            
            Set set = initRankings.entrySet();
            Iterator iterator = set.iterator();
            double maxSum_scoreList = Double.MIN_VALUE;
            ScoreList queryRankings = initRankings.get(qid);
            ArrayList<String> relevantIds = new ArrayList<String>();
            while (iterator.hasNext()){
                
                Map.Entry qres = (Map.Entry)iterator.next();
                String q = (String) qres.getKey();
                int d = q.indexOf('.');
                String id = null;
                if (d!=-1){
                    id = q.substring(0, d).trim();
                }
                else{
                    id = q;
                }
                
                if (id.equals(qid)){
                    
                    ScoreList rankings = (ScoreList)qres.getValue();
                    relevantIds.add(q);
                    int maxInput = Math.min(queryRankings.size(), maxInputRankings);
                    maxInput = Math.min(maxInput, rankings.size());
                    double sum_scoreList = sumScoreList(queryRankings, rankings, maxInput);
                    if (sum_scoreList > maxSum_scoreList){
                        maxSum_scoreList = sum_scoreList;
                    }
                }
            }
            for (String id: relevantIds){
                ScoreList s = initRankings.get(id);
                int maxInput = Math.min(queryRankings.size(), maxInputRankings);
                maxInput = Math.min(maxInputRankings, s.size());
                initRankings.put(id, normalizeScoreList(queryRankings, s, maxSum_scoreList, maxInput));
            }
        }
        
        return initRankings;
    }
    
    
    public static double sumScoreList(ScoreList queryRankings, ScoreList rankings, int maxInputRankings){
        
        double sum = 0.0;
        
        for (int i = 0; i < maxInputRankings; i++){
            int internaldocId = rankings.getDocid(i);
            if (queryRankings.containsDocId(internaldocId)){
                sum += rankings.getDocidScore(i);
            }
        }
        return sum;
    }
    
    //NORMALIZATION CHECK ONLY ORIGINAL QUERY'S DOCUMENTS???
    //NOT JUST BM25 RETRIEVAL MODEL???
    //IF MAXINPUT LENGTH < LENGTH OF ORIGINAL QUERY RANKING LIST
    
    
    public static ScoreList normalizeScoreList(ScoreList queryRankings, ScoreList rankings, double maxSum, int maxInputRankings){
        
        for (int i = 0; i < maxInputRankings; i++){
            int internaldocId = rankings.getDocid(i);
            if (queryRankings.containsDocId(internaldocId)){
                rankings.setDocidScore(i, (double)rankings.getDocidScore(i)/maxSum);
            }
        }
        return rankings;
    }
    
    
    public static void PM2_Diversify(ArrayList<String> queries, LinkedHashMap<String, ScoreList> initRankings, float lambda, int maxRankings, String outputFilePath) throws IOException{
        
        
        for (String qid : queries){
            System.out.println(qid);
            ScoreList diversifiedRankings = new ScoreList();
            ScoreList initScoreList = initRankings.get(qid);
            ArrayList<Double> desired = new ArrayList<Double>();
            ArrayList<Double> coverage = new ArrayList<Double>();
            HashMap<String, Double> priority = new HashMap<String, Double>();
            ArrayList<String> intentIds = new ArrayList<String>();
            
            for (String id: initRankings.keySet()){
                int d = id.indexOf(".");
                if (d!=-1){
                    if (id.substring(0, d).trim().equals(qid)){
                        intentIds.add(id);
                    }
                }
            }
            
            for (int i = 0; i < intentIds.size(); i++){
                desired.add((double)1/intentIds.size() * maxRankings);
                coverage.add(0.0);
            }
            
            int counter = 0;
            while (counter < maxRankings){
                
                int chosenDocId = -1;
                double maxDocScore = -100;
                int i_star = 0;
                String i_star_id = null;
                double maxPriority = -100;
                for (int i = 0; i < intentIds.size(); i++){
                    priority.put(intentIds.get(i), (double)desired.get(i)/(2 * coverage.get(i) + 1));
                    if (maxPriority < priority.get(intentIds.get(i))){
                        maxPriority = priority.get(intentIds.get(i));
                        i_star = i;
                        i_star_id = intentIds.get(i);
                    }
                    //				  if (initRankings.get(intentIds.get(i)).size() > initScoreList.size()){
                    //					  initRankings.get(intentIds.get(i)).truncate(initScoreList.size());
                    //				  }
                }
                
                for (int i = 0; i < initScoreList.size(); i++){
                    int docId = initScoreList.getDocid(i);
                    double docScore = 0.0;
                    if (initRankings.get(i_star_id).containsDocId(docId)){
                        int index = initRankings.get(i_star_id).indexOfDoc(docId);
                        docScore = lambda * priority.get(i_star_id) * initRankings.get(i_star_id).getDocidScore(index);
                    }
                    for (String intentId: intentIds){
                        if (!intentId.equals(i_star_id)){
                            double intentScore = 0.0;
                            if (initRankings.get(intentId).containsDocId(docId)){
                                int index = initRankings.get(intentId).indexOfDoc(docId);
                                intentScore = (initRankings.get(intentId).getDocidScore(index)) * priority.get(intentId);
                            }
                            else{
                                intentScore = 0.0;
                            }
                            docScore += (1 - lambda) * intentScore;
                        }
                    }
                    
                    if (docScore > maxDocScore){
                        maxDocScore = docScore;
                        chosenDocId = docId;
                    }
                }
                
                double denom = 0.0;
                for (int i = 0; i < intentIds.size(); i++){
                    if (initRankings.get(intentIds.get(i)).containsDocId(chosenDocId)){
                        int index = initRankings.get(intentIds.get(i)).indexOfDoc(chosenDocId);
                        denom += initRankings.get(intentIds.get(i)).getDocidScore(index);
                    }
                }
                
                for (int i = 0; i < intentIds.size(); i++){
                    double cur = coverage.get(i);
                    if (initRankings.get(intentIds.get(i)).containsDocId(chosenDocId)){
                        int index = initRankings.get(intentIds.get(i)).indexOfDoc(chosenDocId);
                        coverage.set(i, cur + ((double)initRankings.get(intentIds.get(i)).getDocidScore(index)/denom));
                    }
                }
                diversifiedRankings.add(chosenDocId, maxDocScore);
                initScoreList.removeDoc(chosenDocId);
                counter++;
            }
//            System.out.println("Writing results");
            diversifiedRankings.sort();
            writeResults(qid, diversifiedRankings, outputFilePath, true);
        }
    }
    
    
    
    
    public static void XQuad_Diversify(ArrayList<String> queries, LinkedHashMap<String, ScoreList> initRankings, float lambda, int maxRankings, String outputFilePath) throws IOException{
        
        
        for (String qid : queries){
            
            ScoreList diversifiedRankings = new ScoreList();
            ScoreList initScoreList = initRankings.get(qid);
            
            ArrayList<String> intentIds = new ArrayList<String>();
            for (String id: initRankings.keySet()){
                int d = id.indexOf(".");
                if (d!=-1){
                    if (id.substring(0, d).trim().equals(qid)){
                        intentIds.add(id);
                    }
                }
                
            }
            int counter = 0;
            while (counter < maxRankings){
                
                int chosenDocId = -1;
                double maxDocScore = -100;
                
                //Iterate over all documents in the original query's initial ranking list
                for (int i = 0; i < initScoreList.size(); i++){
                    int docId = initScoreList.getDocid(i);
                    //Compute diversity score
                    double docScore = (1 - lambda) * initScoreList.getDocidScore(i);
                    
                    //Compute relevance score
                    for (String intentId: intentIds){
                        double intentScore = 0.0;
                        double discount = 1.0;
                        
                        //					  if (initRankings.get(intentIds.get(i)).size() > initScoreList.size()){
                        //						  initRankings.get(intentIds.get(i)).truncate(initScoreList.size());
                        //					  }
                        
                        if (initRankings.get(intentId).containsDocId(docId)){
                            
                            int index = initRankings.get(intentId).indexOfDoc(docId);
                            intentScore = initRankings.get(intentId).getDocidScore(index);
                            
                            //Compute diversity score
                            for (int j = 0; j < diversifiedRankings.size(); j++){
                                int internalidx = diversifiedRankings.getDocid(j);
                                if (initRankings.get(intentId).containsDocId(internalidx)){
                                    int idx = initRankings.get(intentId).indexOfDoc(internalidx);
                                    discount *= (double)(1 - initRankings.get(intentId).getDocidScore(idx));
                                }
                            }
                            intentScore *= discount;
                        }
                        
                        else{
                            intentScore = 0.0;
                        }
                        
                        docScore += lambda * (double)1.0/intentIds.size() * intentScore;
                    }
                    
                    
                    
                    if (docScore > maxDocScore){
                        maxDocScore = docScore;
                        chosenDocId = docId;
                    }
                }
                
                diversifiedRankings.add(chosenDocId, maxDocScore);
                initScoreList.removeDoc(chosenDocId);
                counter++;
            }
            diversifiedRankings.sort();
            writeResults(qid, diversifiedRankings, outputFilePath, true);
        }
    }
    
    
    
    public static LinkedHashMap<String,ScoreList> readTrecEvalFile(String InitialRankingFilepath, String queryFilePath, int maxInputRankingsLength, RetrievalModel model) throws NumberFormatException, Exception{
        BufferedReader bufferedReader1 = null;
        BufferedReader bufferedReader2 = null;
        
        String line = null;
        LinkedHashMap<String,ScoreList> r = new LinkedHashMap<String,ScoreList>();
        try
        {
            FileReader fileReader1 = new FileReader(queryFilePath);
            bufferedReader1 = new BufferedReader(fileReader1);
            
            List<String> queries = new ArrayList<String>();
            while((line = bufferedReader1.readLine()) != null) {
                queries.add(line.trim());
            }
            
            
            FileReader fileReader2 = new FileReader(InitialRankingFilepath);
            bufferedReader2 = new BufferedReader(fileReader2);
            
            ScoreList scores = new ScoreList();
            int q = 0;
            String prevqueryID = null;
            while((line = bufferedReader2.readLine()) != null) {
                String[] elem = line.split(" ");
                String queryID = elem[0];
                
                if (r.containsKey(queryID))
                    continue;
                
                if (!queryID.equals(prevqueryID) && prevqueryID!=null){
                    r.put(prevqueryID, scores);
                    scores = new ScoreList();
                }
                
                scores.add(Idx.getInternalDocid(elem[2]), Double.parseDouble(elem[4]));
                
                if (Integer.parseInt(elem[3]) == maxInputRankingsLength){
                    r.put(prevqueryID, scores);
                    scores = new ScoreList();
                    prevqueryID = null;
                }
                else
                    prevqueryID = queryID;
                
                if (model instanceof RetrievalModelLetor){
                    if (Integer.parseInt(elem[3])==maxInputRankingsLength && queries.contains(q)){
                        String query = queries.get(q);
                        int d = query.indexOf(':');
                        String qid = query.substring(0, d);
                        r.put(qid, scores);
                        scores = new ScoreList();
                        q += 1;
                    }
                }
                
                
            }
        }
        catch (IOException e)
        {
            System.err.println("Error: " + e.getMessage());
        }
        finally
        {
            if(bufferedReader1 != null) {
                bufferedReader1.close();
            }
        }
        return r;
    }
    
    /**
     *  Allocate the retrieval model and initialize it using parameters
     *  from the parameter file.
     *  @return The initialized retrieval model
     *  @throws IOException Error accessing the Lucene index.
     */
    private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {
        
        RetrievalModel model = null;
        String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();
        if (modelString.equals("unrankedboolean")) {
            model = new RetrievalModelUnrankedBoolean();
        }
        else if (modelString.equals("rankedboolean")) {
            model = new RetrievalModelRankedBoolean();
        }
        else if (modelString.equals("bm25")){
            //    	System.out.println(parameters.get("bm25:k_1"));
            model = new RetrievalModelBM25(Float.parseFloat(parameters.get("BM25:k_1")),
                                           Float.parseFloat(parameters.get("BM25:b")),
                                           Float.parseFloat(parameters.get("BM25:k_3")));
        }
        else if (modelString.equals("indri")){
            if (parameters.containsKey("fb")){
                if (parameters.get("fb").equals("false"))
                    model = new RetrievalModelIndri(Double.parseDouble(parameters.get("Indri:mu")),
                                                    Double.parseDouble(parameters.get("Indri:lambda")));
                
                else if (parameters.containsKey("fbInitialRankingFile"))
                    model = new RetrievalModelIndri(Double.parseDouble(parameters.get("Indri:mu")),
                                                    Double.parseDouble(parameters.get("Indri:lambda")),
                                                    parameters.get("fb"),
                                                    Integer.parseInt(parameters.get("fbDocs")),
                                                    Integer.parseInt(parameters.get("fbTerms")),
                                                    Integer.parseInt(parameters.get("fbMu")),
                                                    Double.parseDouble(parameters.get("fbOrigWeight")),
                                                    parameters.get("fbInitialRankingFile"),
                                                    parameters.get("fbExpansionQueryFile"));
                
                else if (!parameters.containsKey("fbInitialRankingFile")){
                    model = new RetrievalModelIndri(Double.parseDouble(parameters.get("Indri:mu")),
                                                    Double.parseDouble(parameters.get("Indri:lambda")),
                                                    parameters.get("fb"),
                                                    Integer.parseInt(parameters.get("fbDocs")),
                                                    Integer.parseInt(parameters.get("fbTerms")),
                                                    Integer.parseInt(parameters.get("fbMu")),
                                                    Double.parseDouble(parameters.get("fbOrigWeight")),
                                                    null,
                                                    parameters.get("fbExpansionQueryFile"));
                }
            }
            else
                model = new RetrievalModelIndri(Double.parseDouble(parameters.get("Indri:mu")),
                                                Double.parseDouble(parameters.get("Indri:lambda")));
        }
        else if (modelString.equals("letor")){
            model = new RetrievalModelLetor(Double.parseDouble(parameters.get("Indri:mu")),
                                            Double.parseDouble(parameters.get("Indri:lambda")),
                                            Float.parseFloat(parameters.get("BM25:k_1")),
                                            Float.parseFloat(parameters.get("BM25:b")),
                                            Float.parseFloat(parameters.get("BM25:k_3")));
        }
        else {
            throw new IllegalArgumentException
            ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }
        
        return model;
    }
    
    
    
    
    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     *
     * @param gc
     *          If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc) {
        
        Runtime runtime = Runtime.getRuntime();
        
        if (gc)
            runtime.gc();
        
        System.out.println("Memory used:  "
                           + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }
    
    
    /**
     * Process one query.
     * @param qString A string that contains a query.
     * @param model The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {
        
        String defaultOp = model.defaultQrySopName ();
        qString = defaultOp + "(" + qString + ")";
        //getQuery() returns the Query Tree (after parsing and optimizing query string)
        Qry q = QryParser.getQuery (qString);
        
        // Show the query that is evaluated
        
        System.out.println("    --> " + q);
        
        if (q != null) {
            
            ScoreList r = new ScoreList ();
            
            if (q.args.size () > 0) {		// Ignore empty queries
                
                q.initialize (model);
                
                while (q.docIteratorHasMatch (model)) {
                    int docid = q.docIteratorGetMatch ();
                    //          System.out.println("From eval: " + q.docIteratorGetMatch ());
                    double score = ((QrySop) q).getScore (model);
                    r.add (docid, score);
                    q.docIteratorAdvancePast (docid);
                }
            }
            r.sort();
            return r;
            
        }
        
        else
            return null;
    }
    
    /**
     *  Process the query file.
     *  @param queryFilePath
     *  @param model
     *  @throws IOException Error accessing the Lucene index.
     *  Format of query file:
     *  69:sewing instructions
     *  79:voyager
     *
     */
    static LinkedHashMap<String, ScoreList> processQueryFile(String queryFilePath,
                                                             RetrievalModel model,
                                                             boolean expand,
                                                             boolean diversify,
                                                             int maxRankings,
                                                             String outputFilePath)
    throws IOException {
        
        BufferedReader input = null;
        
        LinkedHashMap<String,ScoreList> docRankings = new LinkedHashMap<String,ScoreList>();
        try {
            String qLine = null;
            
            input = new BufferedReader(new FileReader(queryFilePath));
            //  Each pass of the loop processes one query.
            
            while ((qLine = input.readLine()) != null) {
                
                int d = qLine.indexOf(':');
                
                if (d < 0) {
                    throw new IllegalArgumentException
                    ("Syntax error:  Missing ':' in query line.");
                }
                
                printMemoryUsage(false);
                
                //QueryID
                String qid = qLine.substring(0, d);
                //Query String
                String query = qLine.substring(d + 1);
                System.out.println("From processQueryFile: " + qLine);
                //Obtain score list for each query
                ScoreList r = null;
                
                r = processQuery(query, model);
                if (r != null && expand==false && diversify==false) {
                    //write to output file
                    writeResults(qid, r, outputFilePath, true);
                    System.out.println();
                }
                
                else if (expand==true){
                    String parsedQuery = qid + ": " + model.defaultQrySopName() + "(" + query + ")";
                    docRankings.put(parsedQuery, r);
                }
                
                else if (diversify==true){
                    r.truncate(maxRankings);
                    docRankings.put(qid, r);
                }
                
            }
            
            if (expand==true || diversify==true){
                return docRankings;
            }
            
        }
        
        catch (IOException ex) {
            ex.printStackTrace();
        }
        
        finally {
            input.close();
        }
        
        return docRankings;
    }
    
    
    static ArrayList<String> expandQuery(HashMap<String, ScoreList> retrieved, RetrievalModel model) throws IOException{
        
        Set set = retrieved.entrySet();
        Iterator iterator = set.iterator();
        int fbDocs = ((RetrievalModelIndri) model).fbDocs;
        double fbMu = (double)((RetrievalModelIndri) model).fbMu;
        double fbOrigWeight = ((RetrievalModelIndri) model).fbOrigWeight;
        int fbTerms = ((RetrievalModelIndri) model).fbTerms;
        String fbExpansionQueryFile = ((RetrievalModelIndri) model).fbExpansionQueryFile;
        ArrayList<String> expandedQueries = new ArrayList<String>();
        
        while (iterator.hasNext()){
            
            Map.Entry qres = (Map.Entry)iterator.next();
            ScoreList rankings = (ScoreList)qres.getValue();
            
            TermVector[] tv = new TermVector[fbDocs];
            
            for (int i = 0; i < fbDocs; i++){
                tv[i] = new TermVector(rankings.getDocid(i),"body");
            }
            
            LinkedHashMap<String, Double> vocabScores = new LinkedHashMap<String,Double>();
            for (int i = 0; i < fbDocs; i++){
                
                
                for (int j = 0; j < tv[i].stemsLength(); j++){
                    String stem = tv[i].stemString(j);
                    
                    if (stem!=null || vocabScores.containsKey(stem)){
                        if (stem.contains(".") || stem.contains(","))
                            continue;
                        
                        double weight = 0.0;
                        double ctf = Idx.getTotalTermFreq("body", stem);
                        for (int k = 0; k < fbDocs; k++){
                            int index = tv[k].indexOfStem(stem);
                            double orig_score = rankings.getDocidScore(k);
                            if (index != -1){
                                double pmle = (double)ctf / (double)Idx.getSumOfFieldLengths("body");
                                double num = (double)tv[k].stemFreq( index ) +  (double)(fbMu * pmle);
                                double ptd =  (double)num / (double)(tv[k].positionsLength() + fbMu);
                                weight +=  (double) ptd  * orig_score;
                            }
                            else{
                                double pmle = (double)ctf / (double)Idx.getSumOfFieldLengths("body");
                                double num = (double)(fbMu * pmle);
                                double ptd =  (double)num / (double)(tv[k].positionsLength() + fbMu);
                                weight +=  (double) ptd  * orig_score;
                            }
                        }
                        double idf = (double) Math.log( (double)Idx.getSumOfFieldLengths("body") / ctf );
                        weight *= (double)idf;
                        vocabScores.put(stem, weight );
                    }
                }
            }
            
            //sort vocabScores by value
            HashMap<String, Double> sortedvocabScores = sortHashMap(vocabScores);
            String query = (String)qres.getKey();
            
            int d = query.indexOf(':');
            String qid = query.substring(0, d);
            String orig_query = query.substring(d + 1).trim();
            
            ArrayList<String> expanded = new ArrayList<String>();
            expanded.add(qid + ": #wand (");
            
            Set s = sortedvocabScores.entrySet();
            Iterator it= s.iterator();
            int i = 0;
            while (it.hasNext() && i < fbTerms){
                Map.Entry q_res = (Map.Entry)it.next();
                String q = Double.toString(Math.round( (double)q_res.getValue() * 10000.0 ) / 10000.0) + " " + q_res.getKey() + " ";
                expanded.add(q);
                i += 1;
            }
            expanded.add(")");
            String expandedStr = expanded.toString().replace(",", "").replace("[","").replace("]","");
            writeExpandedQuery(expandedStr, fbExpansionQueryFile);
            d = expandedStr.indexOf(':');
            String combined = qid + ": #wand (" + Double.toString(fbOrigWeight) + " " + orig_query + " " + Double.toString(1.0 - fbOrigWeight) + " " + expandedStr.substring(d+1).trim() + ")";
            expandedQueries.add(combined);
            
        }
        return expandedQueries;
    }
    
    
    @SuppressWarnings("unchecked")
    static HashMap<String,Double> sortHashMap(HashMap<String,Double> vocabScores){
        List list = new LinkedList(vocabScores.entrySet());
        
        Collections.sort(list, new Comparator() {
            public int compare(Object s1, Object s2) {
                return ((Comparable) ((Map.Entry) (s1)).getValue()).compareTo(((Map.Entry) (s2)).getValue());
            }
        });
        
        HashMap<String,Double> sortedHashMap = new LinkedHashMap<String,Double>();
        ListIterator it = list.listIterator(list.size());
        while(it.hasPrevious()) {
            Map.Entry<String,Double> entry = (Map.Entry<String,Double>) it.previous();
            sortedHashMap.put((String)entry.getKey(), (double)entry.getValue());
        }
        return sortedHashMap;
    }
    
    static void writeExpandedQuery(String expanded, String fbExpansionQueryFile) throws IOException{
        
        BufferedWriter out = null;
        
        try
        {
            FileWriter fstream = new FileWriter(fbExpansionQueryFile, true);
            out = new BufferedWriter(fstream);
            out.write(expanded + "\n");
        }
        
        catch (IOException e)
        {
            System.err.println("Error: " + e.getMessage());
        }
        finally
        {
            if(out != null) {
                out.close();
            }
        }
    }
    
    public static class Tuple{
        public int doc;
        public int score;
        public Tuple(int doc, int score){
            this.doc = doc;
            this.score = score;
        }
        public int getDoc(){
            return this.doc;
        }
        
        public int getScore(){
            return this.score;
        }
    }
    
    static ArrayList<String> addFeatures(Map<String, String> parameters, RetrievalModel r, boolean test) throws IOException{
        int featureCount = 18;
        BufferedReader bufferedReader = null;
        BufferedReader bufferedReader1 = null;
        String line = null;
        
        try{
            
            FileReader fileReader = null;
            FileReader fileReader1 = null;
            if (!test){
                fileReader = new FileReader(parameters.get("letor:trainingQueryFile"));
                fileReader1 = new FileReader(parameters.get("letor:trainingQrelsFile"));
            }
            else{
                fileReader = new FileReader(parameters.get("queryFilePath"));
                fileReader1 = new FileReader(parameters.get("trecEvalOutputPath"));
            }
            bufferedReader = new BufferedReader(fileReader);
            bufferedReader1 = new BufferedReader(fileReader1);
            
            List<String> queries = new ArrayList<String>();
            while((line = bufferedReader.readLine()) != null) {
                queries.add(line.trim());
            }
            
            HashMap<String,List<Tuple>> rel_judgements = new HashMap<String,List<Tuple>>();
            ArrayList<Tuple> singlequeryJudgements = new ArrayList<Tuple>();
            String qprev = "NULL";
            while((line = bufferedReader1.readLine()) != null) {
                String[] s = line.split(" ");
                try{
                    Tuple rj = new Tuple(Idx.getInternalDocid(s[2]), Integer.parseInt(s[3]));
                    
                    if (rel_judgements.containsKey(s[0])){
                        singlequeryJudgements.add(rj);
                    }
                    else{
                        if (singlequeryJudgements.size()>0){
                            //						  System.out.println("Adding " + singlequeryJudgements.size() + " to rel hashmap");
                            rel_judgements.put(qprev, singlequeryJudgements);
                            singlequeryJudgements = new ArrayList<Tuple>();
                            singlequeryJudgements.add(rj);
                            rel_judgements.put(s[0], singlequeryJudgements);
                            qprev = s[0];
                        }
                        else{
                            singlequeryJudgements.add(rj);
                            rel_judgements.put(s[0], singlequeryJudgements);
                            qprev = s[0];
                        }
                    }
                }
                catch(Exception ex){
                    continue;
                }
                
            }
            //		  System.out.println("Adding " + singlequeryJudgements.size() + " to rel hashmap");
            rel_judgements.put(qprev, singlequeryJudgements);
            
            HashMap<String,Double> pageRankScores = readPageRankFile(parameters.get("letor:pageRankFile"));
            List<String> disabled  = new ArrayList<String>();
            if (parameters.containsKey("letor:featureDisable")){
                String disabledFeatures = parameters.get("letor:featureDisable");
                String[] banned = disabledFeatures.split(",");
                disabled = Arrays.asList(banned);
            }
            
            
            ArrayList<FeatureVector> normalizedQueryFeatures = new ArrayList<FeatureVector>();
            ArrayList<Integer> relevance = new ArrayList<Integer>();
            ArrayList<String> qIdList = new ArrayList<String>();
            for (String query: queries){
                ArrayList<FeatureVector> queryFeatures = new ArrayList<FeatureVector>();
                int d = query.indexOf(':');
                String qId = query.substring(0, d).trim();
                qIdList.add(qId);
                String queryString = query.substring(d+1);
                String[] tokenized_query = QryParser.tokenizeString(queryString);
                //			  System.out.println(qId);
                //			  System.out.println("Size rj: " + rel_judgements.get(qId).size());
                for (Tuple t: rel_judgements.get(qId)){
                    int docId = t.getDoc();
                    int rel = t.getScore();
                    try{
                        String extId = Idx.getExternalDocid(docId);
                    }
                    catch(IOException ex){
                        continue;
                    }
                    
                    FeatureVector fv = new FeatureVector(qId, docId);
                    TermVector tv_body = new TermVector(docId, "body");
                    //				  int count = 1;
                    
                    //////////////////////////////////////////////////////////
                    if (!disabled.contains(Integer.toString(1))){
                        int spamScore = Integer.parseInt (Idx.getAttribute ("score", docId));
                        fv.addFeature(1, spamScore); //Feature 1
                    }
                    //////////////////////////////////////////////////////////
                    String rawUrl = Idx.getAttribute ("rawUrl", docId);
                    if (!disabled.contains(Integer.toString(2))){
                        int depth = 0;
                        for( int j=0; j<rawUrl.length(); j++ ) {
                            if( rawUrl.charAt(j) == '/' ) {
                                depth++;
                            }
                        }
                        fv.addFeature(2, depth); //Feature 2
                    }
                    //////////////////////////////////////////////////////////
                    if (!disabled.contains(Integer.toString(3))){
                        int wiki = rawUrl.indexOf("wikipedia.org");  //Feature 3
                        if (wiki<0)
                            fv.addFeature(3, 0);
                        else
                            fv.addFeature(3, 1);
                    }
                    
                    //////////////////////////////////////////////////////////
                    if (!disabled.contains(Integer.toString(4)) && pageRankScores.containsKey(Idx.getExternalDocid(docId)))
                        fv.addFeature(4, pageRankScores.get(Idx.getExternalDocid(docId)));  //Feature 4
                    
                    //////////////////////////////////////////////////////////
                    if (tv_body.positionsLength()>0){	//Feature 5, 6, 7
                        if (!disabled.contains(Integer.toString(5)))
                            fv.addFeature(5, featureBM25(tokenized_query, tv_body, r));
                        
                        if (!disabled.contains(Integer.toString(6)))
                            fv.addFeature(6, featureIndri(tokenized_query, tv_body, r));
                        
                        if (!disabled.contains(Integer.toString(7))){
                            double termOverlap = 0.0;
                            int found = 0;
                            for (int j=0; j<tokenized_query.length; j++){
                                int index = tv_body.indexOfStem(tokenized_query[j]);
                                if (index!=-1)
                                    found++;
                            }
                            
                            termOverlap = (double)found/tokenized_query.length;
                            fv.addFeature(7, termOverlap);
                        }
                    }
                    //////////////////////////////////////////////////////////
                    TermVector tv_title = new TermVector(docId, "title");
                    if (tv_title.positionsLength()>0){	//Feature 8, 9, 10
                        if (!disabled.contains(Integer.toString(8)))
                            fv.addFeature(8, featureBM25(tokenized_query, tv_title, r));
                        if (!disabled.contains(Integer.toString(9)))
                            fv.addFeature(9, featureIndri(tokenized_query, tv_title, r));
                        if (!disabled.contains(Integer.toString(10))){
                            double termOverlap = 0.0;
                            int found = 0;
                            for (int j=0; j<tokenized_query.length; j++){
                                int index = tv_title.indexOfStem(tokenized_query[j]);
                                if (index!=-1)
                                    found++;
                            }
                            termOverlap = (double)found/tokenized_query.length;
                            fv.addFeature(10, termOverlap);
                        }
                    }
                    
                    TermVector tv_url = new TermVector(docId, "url");
                    if (tv_url.positionsLength()>0){	//Feature 11, 12, 13
                        if (!disabled.contains(Integer.toString(11)))
                            fv.addFeature(11, featureBM25(tokenized_query, tv_url, r));
                        if (!disabled.contains(Integer.toString(12)))
                            fv.addFeature(12, featureIndri(tokenized_query, tv_url, r));
                        if (!disabled.contains(Integer.toString(13))){
                            double termOverlap = 0.0;	// Feature 13
                            int found = 0;
                            for (int j=0; j < tokenized_query.length; j++){
                                int index = tv_url.indexOfStem(tokenized_query[j]);
                                if (index!=-1)
                                    found++;
                            }
                            termOverlap = (double)found/tokenized_query.length;
                            //						else
                            //							termOverlap = 0.0;
                            fv.addFeature(13, termOverlap);
                        }
                    }
                    
                    
                    TermVector tv_inlink = new TermVector(docId, "inlink");
                    if (tv_inlink.positionsLength()>0){	//Feature 14, 15, 16
                        if (!disabled.contains(Integer.toString(14)))
                            fv.addFeature(14, featureBM25(tokenized_query, tv_inlink, r));
                        if (!disabled.contains(Integer.toString(15)))
                            fv.addFeature(15, featureIndri(tokenized_query, tv_inlink, r));
                        if (!disabled.contains(Integer.toString(16))){
                            double termOverlap = 0.0;	// Feature 16
                            int found = 0;
                            for (int j=0; j < tokenized_query.length; j++){
                                int index = tv_inlink.indexOfStem(tokenized_query[j]);
                                if (index!=-1)
                                    found++;
                            }
                            
                            termOverlap = (double)found/tokenized_query.length;
                            fv.addFeature(16, termOverlap);
                        }
                    }
                    
                    //Ratio of unique terms in body of document
                    if (tv_body.positionsLength()>0){
                        if (!disabled.contains(Integer.toString(17))){
                            int uniqueTerms = 0;	// Feature 17
                            uniqueTerms = tv_body.stemsLength();
                            double uniqueFreq = (double)uniqueTerms/tv_body.positionsLength();
                            fv.addFeature(17, uniqueFreq);
                        }
                    }
                    
                    //Ratio of occurrence of query terms in the body over all terms in the body
                    if (tv_body.positionsLength()>0){
                        if (!disabled.contains(Integer.toString(18))){
                            double freq = 0;
                            int index = -1;
                            for (int j=0; j < tokenized_query.length; j++){
                                index = tv_body.indexOfStem(tokenized_query[j]);
                                if (index==-1){
                                    freq = 0;
                                    break;
                                }
                                else
                                    freq += (double)tv_body.stemFreq(index)/tv_body.positionsLength();
                            }
                            fv.addFeature(18, freq);
                        }
                    }
                    
                    
                    queryFeatures.add(fv);
                    if (!test)
                        relevance.add(rel + 3); //Shift to [1,5]
                    else
                        relevance.add(0);
                }
                
                //			  System.out.println("Size qf:" + queryFeatures.size());
                //			  System.out.println("Feature Count:" + featureCount);
                HashMap<Integer, Double> maxim = getMax(queryFeatures, featureCount);
                HashMap<Integer, Double> minim = getMin(queryFeatures, featureCount);
                //			  for (int k = 1; k <= featureCount ; k++){
                //				  System.out.println("Feature " + k + "max: " + maxim.get(k) + " min: " + minim.get(k));
                //			  }
                
                for (int j = 0; j < queryFeatures.size(); j++){
                    FeatureVector fv_normalized = new FeatureVector(qId, rel_judgements.get(qId).get(j).getDoc());
                    for (int k = 1; k <= featureCount ; k++){
                        double normalized_score = 0.0;
                        
                        if (maxim.get(k) - minim.get(k) < Double.MIN_VALUE ){
                            normalized_score = 0.0;
                        }					  
                        else if (queryFeatures.get(j).scores.containsKey(k) ){
                            normalized_score = (double)(queryFeatures.get(j).getFeatureVal(k) - minim.get(k))/(maxim.get(k) - minim.get(k));
                        }
                        else{
                            normalized_score = 0.0;
                        }
                        fv_normalized.addFeature(k, normalized_score);
                    }
                    normalizedQueryFeatures.add(fv_normalized);
                }
                queryFeatures = new ArrayList<FeatureVector>();
            }
            
            if (!test){
                writeFeatureFile(normalizedQueryFeatures, parameters.get("letor:trainingFeatureVectorsFile"), relevance);
                SVMtrain(parameters.get("letor:trainingFeatureVectorsFile"), parameters.get("letor:svmRankModelFile"), 
                         Double.parseDouble(parameters.get("letor:svmRankParamC")), parameters.get("letor:svmRankLearnPath"));
            }
            
            else{
                writeFeatureFile(normalizedQueryFeatures, parameters.get("letor:testingFeatureVectorsFile"), relevance);
                SVMclassify(parameters.get("letor:testingFeatureVectorsFile"), parameters.get("letor:svmRankModelFile"), 
                            parameters.get("letor:svmRankClassifyPath"), parameters.get("letor:testingDocumentScores"));
            }
            return qIdList;
        }
        
        catch (Exception ex) {
            ex.printStackTrace();
        } 
        
        finally {
            bufferedReader.close();
        }
        return null;
    }
    
    static HashMap<Integer, Double> getMax(ArrayList<FeatureVector> queryFeatures, int featureCount){
        
        HashMap<Integer, Double> featureMax = new HashMap<Integer, Double>();
        
        for (int j = 1; j <= featureCount ; j++){
            double maxim = Double.MIN_VALUE;
            for (int i = 0; i < queryFeatures.size(); i++){
                if (queryFeatures.get(i).scores.containsKey(j)){
                    if (queryFeatures.get(i).getFeatureVal(j) > maxim)
                        maxim = queryFeatures.get(i).getFeatureVal(j);
                }
            }
            featureMax.put(j, maxim);
        }
        return featureMax;
    }
    
    static HashMap<Integer, Double> getMin(ArrayList<FeatureVector> queryFeatures, int featureCount){
        
        HashMap<Integer, Double> featureMin = new HashMap<Integer, Double>();
        
        for (int j = 1; j <= featureCount ; j++){
            double minim = Double.MAX_VALUE;
            for (int i = 0; i < queryFeatures.size(); i++){
                if (queryFeatures.get(i).scores.containsKey(j)){
                    if (queryFeatures.get(i).getFeatureVal(j) < minim)
                        minim = queryFeatures.get(i).getFeatureVal(j);
                }
            }
            featureMin.put(j, minim);
        }
        return featureMin;
    }
    
    
    static double featureBM25 (String[] queryStems, TermVector tv, RetrievalModel r) throws IOException{
        
        double BM25score = 0.0;
        double b = ((RetrievalModelLetor) r).b;
        double k1 = ((RetrievalModelLetor) r).k1;
        double k3 = ((RetrievalModelLetor) r).k3;
        double doc_len = tv.positionsLength();
        double avg_doc_len = (double)Idx.getSumOfFieldLengths(tv.fieldName) / Idx.getDocCount(tv.fieldName);
        double frac = (double)doc_len/avg_doc_len;
        
        for (int i = 0; i < queryStems.length; i++){
            int index = tv.indexOfStem(queryStems[i]);
            if (index!=-1){		          
                double rsj = Math.max(0 , Math.log ( (double)(Idx.getNumDocs() - tv.stemDf(index) + 0.5) / (tv.stemDf(index) + 0.5)));
                double tf = tv.stemFreq(index) ;
                double term_freq = (double) tf / (tf + k1 * ( (1-b) + (b * frac)));
                double qtf = 1.0;
                double userweight = (double) (k3 + 1) * qtf / (k3 + qtf);
                
                BM25score += rsj * term_freq * userweight;
            }
        }
        return BM25score;
    }
    
    static double featureIndri (String[] queryStems, TermVector tv, RetrievalModel r) throws IOException{
        double IndriScore = 1.0;
        double mu = ((RetrievalModelLetor) r).mu;
        double lambda = ((RetrievalModelLetor) r).lambda;
        double tf = 0.0;
        double doc_len = tv.positionsLength();
        double p = (double)1.0/queryStems.length;
        boolean found = false;
        
        for (int i = 0; i < queryStems.length; i++){
            int idxx = tv.indexOfStem(queryStems[i]);
            if (idxx!=-1)
                found = true;
        }
        
        if (found==false)
            return 0.0;
        else{
            for (int i = 0; i < queryStems.length; i++){
                int index = tv.indexOfStem(queryStems[i]);
                double p_mle = (double)Idx.getTotalTermFreq(tv.fieldName, queryStems[i])/Idx.getSumOfFieldLengths(tv.fieldName);	
                if (index!=-1)
                    tf = tv.stemFreq(index) ;
                else
                    tf = 0.0;
                IndriScore *= (double)(1.0-lambda) * ((double)(tf + mu * p_mle) / (doc_len + mu)) + (double)lambda * p_mle;
            }
            return Math.pow (IndriScore, p) ;
        }
    }
    
    
    
    
    static HashMap<String, Double> readPageRankFile(String path) throws IOException{
        BufferedReader bufferedReader = null;
        String line = null;
        HashMap<String, Double> pageRankScores = new HashMap<String, Double>();
        try{
            FileReader fileReader = new FileReader(path);
            bufferedReader = new BufferedReader(fileReader);
            while((line = bufferedReader.readLine()) != null) {
                String[] s = line.split("\t");
                pageRankScores.put(s[0],Double.parseDouble(s[1]));
            }
            return pageRankScores;
        }
        catch (IOException ex) {
            ex.printStackTrace();
        } 
        
        finally {
            bufferedReader.close();
        }
        return pageRankScores;
    }
    
    static void writeFeatureFile(ArrayList<FeatureVector> queryFeatures, String path, ArrayList<Integer> relevance) throws IOException{
        BufferedWriter out = null;
        try  
        {
            FileWriter fstream = new FileWriter(path, true);
            out = new BufferedWriter(fstream);
            if (queryFeatures.size()<1) 
                return;
            
            else{
                for (int i = 0; i < queryFeatures.size(); i++) {		  
                    out.write(relevance.get(i) + " qid:" + queryFeatures.get(i).qId);
                    for (int j = 0; j < queryFeatures.get(i).featureSize(); j++){
                        out.write(" " + Integer.toString(j+1) + ":" + queryFeatures.get(i).getFeatureVal(j + 1));
                    }
                    out.write(" # " + Idx.getExternalDocid(queryFeatures.get(i).docId) + "\n");
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Error: " + e.getMessage());
        }
        finally
        {
            if(out != null) {
                out.close();
            }
        }
    }
    
    
    static void SVMtrain(String qrelsFeatureOutputFile, String modelOutputFile, double c, String SVMLearnPath) throws IOException {
        
        
        Process cmdProc = Runtime.getRuntime().exec(
                                                    new String[] { SVMLearnPath, "-c", String.valueOf(c), qrelsFeatureOutputFile,
                                                        modelOutputFile });
        
        // The stdout/stderr consuming code MUST be included.
        // It prevents the OS from running out of output buffer space and stalling.
        
        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
        String line;
        try{
            while ((line = stdoutReader.readLine()) != null) {
                System.out.println(line);
            }
            // consume stderr and print it for debugging purposes
            BufferedReader stderrReader = new BufferedReader(
                                                             new InputStreamReader(cmdProc.getErrorStream()));
            while ((line = stderrReader.readLine()) != null) {
                System.out.println(line);
            }
            
            // get the return value from the executable. 0 means success, non-zero 
            // indicates a problem
            int retValue = cmdProc.waitFor();
            if (retValue != 0) {
                throw new Exception("SVM Rank crashed.");
            }
        }
        catch(Exception e){
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    static void SVMclassify(String qrelsFeatureFile, String modelFile, String SVMClassifyPath, String testingDocumentScores ) throws IOException {
        
        Process cmdProc = Runtime.getRuntime().exec(
                                                    new String[] { SVMClassifyPath, qrelsFeatureFile,
                                                        modelFile, testingDocumentScores });
        
        // The stdout/stderr consuming code MUST be included.
        // It prevents the OS from running out of output buffer space and stalling.
        
        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
        String line;
        try{
            while ((line = stdoutReader.readLine()) != null) {
                System.out.println(line);
            }
            // consume stderr and print it for debugging purposes
            BufferedReader stderrReader = new BufferedReader(
                                                             new InputStreamReader(cmdProc.getErrorStream()));
            while ((line = stderrReader.readLine()) != null) {
                System.out.println(line);
            }
            
            // get the return value from the executable. 0 means success, non-zero 
            // indicates a problem
            int retValue = cmdProc.waitFor();
            if (retValue != 0) {
                throw new Exception("SVM Rank crashed.");
            }
        }
        catch(Exception e){
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    
    static void rerank(ArrayList<String> qIdList, LinkedHashMap<String,ScoreList> scores, String docScoresPath, String outputPath) throws IOException{
        
        BufferedReader bufferedReader = null;
        String line = null;
        try{
            FileReader fileReader = new FileReader(docScoresPath);
            bufferedReader = new BufferedReader(fileReader);
            int i = 0;
            int q = 0;
            LinkedHashMap<String, Double> docScores = new LinkedHashMap<String, Double>();
            
            String qID = qIdList.get(0);
            ScoreList sl = scores.get(qID);
            ScoreList out = new ScoreList();
            while((line = bufferedReader.readLine()) != null) {
                
                String[] s = line.split(" ");
                int docId =  sl.getDocid(i);
                out.add(docId, Double.parseDouble(s[0]));
                i++;
                
                if (i==100){
                    out.sort();
                    if (q==0)
                        writeResults(qID, out, outputPath, false);
                    else
                        writeResults(qID, out, outputPath, true);
                    q++;
                    if (q >= qIdList.size())
                        break;
                    qID = qIdList.get(q);
                    sl = scores.get(qID);
                    out = new ScoreList();
                    i = 0;
                }
                
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        } 
        
        finally {
            bufferedReader.close();
        }
    }
    
    
    
    
    /**
     * Print the query results.
     * 
     * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
     * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
     * 
     * QueryID Q0 DocID Rank Score RunID
     * 
     * @param queryName
     *          Original query.
     * @param result
     *          A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
    static void writeResults(String queryId, ScoreList result, String outputFilePath, boolean append) throws IOException {
        
        BufferedWriter out = null;
        try  
        {
            FileWriter fstream = new FileWriter(outputFilePath, append);
            out = new BufferedWriter(fstream);
            if (result.size()<1){ //No documents retrieved
                out.write(queryId + " Q0" + " dummy" + " 1" + " 0" + " run-1" + "\n");
            }
            else{
                result.truncate(100);
                for (int i = 0; i < result.size(); i++) {
                    //result.getDocid() gives the internal Docid
                    //				  	System.out.println(queryId + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " "
                    //				            + (i+1) + " " + result.getDocidScore(i) + " run-1" + "\n");
                    out.write(queryId + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " "
                              + (i+1) + " " + result.getDocidScore(i) + " run-1" + "\n");
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Error: " + e.getMessage());
        }
        finally
        {
            if(out != null) {
                out.close();
            }
        }
    }
    
    /**
     *  Read the specified parameter file, and confirm that the required
     *  parameters are present.  The parameters are returned in a
     *  HashMap.  The caller (or its minions) are responsible for processing
     *  them.
     *  @return The parameters, in <key, value> format.
     */
    private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {
        
        Map<String, String> parameters = new HashMap<String, String>();
        
        File parameterFile = new File (parameterFileName);
        
        if (! parameterFile.canRead ()) {
            throw new IllegalArgumentException
            ("Can't read " + parameterFileName);
        }
        
        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split ("=");
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());
        
        scan.close();
        
        if (! (parameters.containsKey ("indexPath") &&
               parameters.containsKey ("queryFilePath") &&
               parameters.containsKey ("trecEvalOutputPath"))){
            //         parameters.containsKey ("retrievalAlgorithm"))){
            //    	   parameters.containsKey("bm25:k_1") &&
            //    	   parameters.containsKey("bm25:b") &&
            //    	   parameters.containsKey("bm25:k_3")) {
            throw new IllegalArgumentException
            ("Required parameters were missing from the parameter file.");
        }
        
        return parameters;
    }
    
}
