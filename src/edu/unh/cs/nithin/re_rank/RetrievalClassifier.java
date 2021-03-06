/**
 * @Author: Nithin Sivakumar <Nithin>
 * @Date:   2019-11-30T17:18:02-05:00
 * @Last modified by:   Nithin
 * @Last modified time: 2019-11-30T17:19:22-05:00
 */
package edu.unh.cs.nithin.re_rank;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class RetrievalClassifier {

	private String outputPath;
	private String indexPath;
	private float predictionConfidence;
	private String pagesFile;
	private String folderPath;

	/**
	 * set output file paths
	 * 
	 * @param indexPath
	 * @param outputPath
	 * @param predConf
	 */
	public RetrievalClassifier(String indexPath, String outputPath, float predConf, String pagesFile,
			String folderPath) {
		setIndexPath(indexPath);
		setOutputPath(outputPath);
		setPredictionConfidence(predConf);
		setPagesFile(pagesFile);
		setFolderPath(folderPath);
	}

	/**
	 * Execute bm25 for section level paragraphs for given category in outlines.cbor
	 * 
	 * @param catName
	 * @throws IOException
	 */
	public void runBm25(String catName) throws IOException {
		String outputDir = getOutputPath() + "/bm25";
		File file = new File(outputDir);
		if (!file.exists())
			file.mkdirs();
		File runfile = new File(outputDir + "/" + catName.replaceAll("[^A-Za-z0-9]", "_"));
		runfile.createNewFile();
		FileWriter writer = new FileWriter(runfile);
		IndexSearcher searcher = setupIndexSearcher(getIndexPath(), "paragraph.lucene");
		searcher.setSimilarity(new BM25Similarity());
		final MyQueryBuilder queryBuilder = new MyQueryBuilder(new StandardAnalyzer());
		final FileInputStream fileInputStream3 = new FileInputStream(new File(getPagesFile()));
		System.out.println("starting searching for sections ...");
		int count = 0;
		for (Data.Page page : DeserializeData.iterableAnnotations(fileInputStream3)) {
			ArrayList<String> categories = page.getPageMetadata().getCategoryNames();
			if (categories.contains(catName)) {
				System.out.println(page.getPageId() + " ----- ");
				for (List<Data.Section> sectionPath : page.flatSectionPaths()) {
					final String queryId = Data.sectionPathId(page.getPageId(), sectionPath);
					String queryStr = buildSectionQueryStr(page, sectionPath);
					System.out.println(queryStr);
					TopDocs tops = searcher.search(queryBuilder.toQuery(queryStr), 500);
					ScoreDoc[] scoreDoc = tops.scoreDocs;
					for (int i = 0; i < scoreDoc.length; i++) {
						ScoreDoc score = scoreDoc[i];
						final Document doc = searcher.doc(score.doc);
						final String paragraphid = doc.getField("paragraphid").stringValue();
						final float searchScore = score.score;
						final int searchRank = i + 1;
						writer.write(queryId + " Q0 " + paragraphid + " " + searchRank + " " + searchScore
								+ " Lucene-BM25\n");
						count++;
					}
				}
			}
		}
		writer.flush();
		writer.close();
		System.out.println("Write " + count + " results\nQuery Done!");
	}

	/**
	 * create respective directories in project-dir and load classifier models Call
	 * classifyRunFile and classifiy each line one by one
	 * 
	 * @param catName
	 * @throws Exception
	 */
	public void classifyRunfiles(String catName) throws Exception {
		String reRankNBPath = getOutputPath() + "/rerank/NaiveBayes";
		File f1 = new File(reRankNBPath);
		if (!f1.exists())
			f1.mkdirs();
		Classifier naiveBayesModel = loadModel(catName, "NaiveBayes");
		classifyRunfile(catName, naiveBayesModel, reRankNBPath);
		deleteDir(new File(getOutputPath() + "/bm25/"));

//		String reRankRFPath = getOutputPath()+ "/rerank/RandomForest";
//		File f2 = new File(reRankRFPath);
//		if(!f2.exists()) f2.mkdirs();
//		classifyRunfile(catName, loadModel(catName, "RandomForest"), reRankRFPath);
	}

	/**
	 * 
	 * @param catName
	 * @param outPath
	 * @param naiveBayesModel
	 * @throws Exception
	 */
	private void classifyRunfile(String catName, Classifier model, String outPath) throws Exception {

		// Load the trainset data format
		String trainsetPath = getFolderPath() + "/trainset/" + catName + ".arff";
		DataSource source = new DataSource(trainsetPath);
		Instances trainingData = source.getDataSet();
		trainingData.setClassIndex(trainingData.numAttributes() - 1);

		// create outfile object and setup writer
		File outRunfile = new File(outPath + "/" + catName);
		outRunfile.createNewFile();
		FileWriter writer = new FileWriter(outRunfile);

		// load the runfile genrated before and classify each result
		String runFile = getOutputPath() + "/bm25/" + catName;
		File file = new File(runFile);
		BufferedReader br = new BufferedReader(new FileReader(file));
		String st;
		while (((st = br.readLine()) != null)) {
			String[] tokens = st.split(" ");
			String paraId = tokens[2];
			String paragraph = getParagraphForId(indexPath, paraId);
			System.out.println(tokens[0]);
			Instances testset = trainingData.stringFreeStructure();
			Instance insta = makeInstance(paragraph, testset);
			double predicted = model.classifyInstance(insta);
			double[] prediction = model.distributionForInstance(insta);
			double predictionRate = prediction[(int) predicted];
			if (predictionRate >= predictionConfidence) {
				String predictedClass = trainingData.classAttribute().value((int) predicted);
				writer.write(predictedClass + " " + predictionRate + " " + paraId + " " + 1 + " " + tokens[4]
						+ " Classifier\n");
				System.out.println(
						predictedClass + predictionRate + paraId + " " + 1 + " " + tokens[4] + " Classifier\n");
			} else {
				writer.write(tokens[0] + " " + tokens[1] + " " + paraId + " " + 1 + " " + tokens[4] + " Classifier\n");
			}
		}
		writer.flush();
		writer.close();
		System.out.println("Writen  classified results\nQuery Done!" + outRunfile.getName());
	}

	/**
	 * Retrive paragraph for paragraph id from lucene idex
	 * 
	 * @param indexPath2
	 * @param paraId
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */
	private String getParagraphForId(String indexPath, String paraId) throws IOException, ParseException {
		Analyzer analyzer = new StandardAnalyzer();
		IndexSearcher searcher = setupIndexSearcher(indexPath, "paragraph.lucene");
		QueryParser qp = new QueryParser("paragraphid", analyzer);
		String paraText = searcher.doc(searcher.search(qp.parse(paraId), 1).scoreDocs[0].doc).getField("text")
				.stringValue();
		return paraText;
	}

	/**
	 * Helper function to make weka format instance to prodict label later
	 * 
	 * @param text
	 * @param data
	 * @return
	 */
	private Instance makeInstance(String text, Instances data) {
		// Create instance of length two.
		Instance instance = new DenseInstance(2);
		// Set value for message attribute
		Attribute messageAtt = data.attribute("text");
		instance.setValue(messageAtt, messageAtt.addStringValue(text));
		// Give instance access to attribute information from the dataset.
		instance.setDataset(data);
		return instance;
	}

	/**
	 * Helper to load classifier model
	 * 
	 * @param catName
	 * @param classifierName
	 * @return loadedModel
	 * @throws Exception
	 */
	private Classifier loadModel(String catName, String classifierName) throws Exception {
		String modelPath = getFolderPath() + "/models/" + classifierName + "/";
		modelPath = modelPath + catName + ".model";
		System.out.println("loading " + classifierName + "Classifier model");
		System.out.println("Model Loading.......................");
		Classifier model = (Classifier) weka.core.SerializationHelper.read(modelPath);
		System.out.println("Model Loaded successfully");
		return model;
	}

	/**
	 * @author Laura Dietz Modified by : Nithin Modified Date : Nov 30, 2019 5:38:40
	 *         PM
	 */
	public static class MyQueryBuilder {

		private final StandardAnalyzer analyzer;
		private List<String> tokens;

		public MyQueryBuilder(StandardAnalyzer standardAnalyzer) {
			analyzer = standardAnalyzer;
			tokens = new ArrayList<>(128);
		}

		public BooleanQuery toQuery(String queryStr) throws IOException {

			TokenStream tokenStream = analyzer.tokenStream("text", new StringReader(queryStr));
			tokenStream.reset();
			tokens.clear();
			while (tokenStream.incrementToken()) {
				final String token = tokenStream.getAttribute(CharTermAttribute.class).toString();
				tokens.add(token);
			}
			tokenStream.end();
			tokenStream.close();
			BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
			for (String token : tokens) {
				booleanQuery.add(new TermQuery(new Term("text", token)), BooleanClause.Occur.SHOULD);
			}
			return booleanQuery.build();
		}
	}

	/**
	 * Initialize the index searcher
	 * 
	 * @param indexPath
	 * @param typeIndex
	 * @return indexsearcher object
	 * @throws IOException
	 */
	private static IndexSearcher setupIndexSearcher(String indexPath, String typeIndex) throws IOException {
		Path path = FileSystems.getDefault().getPath(indexPath, typeIndex);
		Directory indexDir = FSDirectory.open(path);
		IndexReader reader = DirectoryReader.open(indexDir);
		return new IndexSearcher(reader);
	}

	/**
	 * Build english query from unstructured text
	 * 
	 * @param page
	 * @param sectionPath
	 * @return query string
	 */
	private static String buildSectionQueryStr(Data.Page page, List<Data.Section> sectionPath) {
		StringBuilder queryStr = new StringBuilder();
		queryStr.append(page.getPageName());
		for (Data.Section section : sectionPath) {
			queryStr.append(" ").append(section.getHeading());
		}
		return queryStr.toString();
	}
	
	/**
	 * Helper to delete directory and files inside
	 * @param dir
	 * @return boolean
	 */
	public static boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));

				if (!success) {
					return false;
				}
			}
		}
		return dir.delete();
	}

	/**
	 * @return the outputPath
	 */
	public String getOutputPath() {
		return outputPath;
	}

	/**
	 * @param outputPath the outputPath to set
	 */
	public void setOutputPath(String outputPath) {
		this.outputPath = outputPath;
	}

	/**
	 * @return the indexPath
	 */
	public String getIndexPath() {
		return indexPath;
	}

	/**
	 * @param indexPath the indexPath to set
	 */
	public void setIndexPath(String indexPath) {
		this.indexPath = indexPath;
	}

	/**
	 * @return the predictionConfidence
	 */
	public float getPredictionConfidence() {
		return predictionConfidence;
	}

	/**
	 * @param predictionConfidence the predictionConfidence to set
	 */
	public void setPredictionConfidence(float predictionConfidence) {
		this.predictionConfidence = predictionConfidence;
	}

	/**
	 * @return the pagesFile
	 */
	public String getPagesFile() {
		return pagesFile;
	}

	/**
	 * @param pagesFile the pagesFile to set
	 */
	public void setPagesFile(String pagesFile) {
		this.pagesFile = pagesFile;
	}

	/**
	 * @return the folderPath
	 */
	public String getFolderPath() {
		return folderPath;
	}

	/**
	 * @param folderPath the folderPath to set
	 */
	public void setFolderPath(String folderPath) {
		this.folderPath = folderPath;
	}

}
