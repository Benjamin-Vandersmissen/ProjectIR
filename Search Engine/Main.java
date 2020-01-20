import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.document.*;

import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    public static Path documents_path = Path.of("../processed");
    public static Path index_path = Path.of("../index");

    public static Analyzer analyzer;
    public static Similarity similarity = new LMDirichletSimilarity(0.5f);

    public static Path output_path = Path.of("../results.txt");
    public static Path title_file = Path.of("../titles.txt");


    //TODO: extra statistic? => average ranking.

    public static void main(String[] args) throws ParserConfigurationException, IOException, SAXException, ParseException {

        String usage = "Usage:\n\t index -docs dir -index dir -feedback true/false: create an index from a directory of XML documents and write it to a given directory\n";
        usage += "\t search -query query -index dir -k k -feedback true/false: search an index for a query and return the top k results, if feedback is true, the index must support feedback as wel\n";
        usage += "\t benchmark -titles titles -index index -k k -tokenfilter tokenfilter -similarity (LM/TF-IDF/Okapi): run a benchmark for titles, index, top-k\n";
        usage += "\t \033[0;31mTo benchmark, you need to have a matching index, using the index command only produces a LMDirichletSimilarity(0.5) index, so be careful with this!\033[0m";

        System.out.println(usage);


        // use WhitespaceAnalyzer to split the query only on whitespaces, inspired by https://stackoverflow.com/questions/17839053/how-to-perform-a-lucene-query-containing-special-character-using-queryparser#comment34054292_17839053
        // This allows us to include (escaped) special characters in our queries
        analyzer = CustomAnalyzer.builder().withTokenizer("whitespace").addTokenFilter("lowercase").build();

        boolean feedback = false;

        if (args.length > 0){
            if (args[0].equals("index")){
                for(int i = 1; i < args.length; ++i){
                    if (args[i].equals("-docs")){
                        documents_path = Path.of(args[i+1]);
                        ++i;
                    }
                    if (args[i].equals("-index")){
                        index_path = Path.of(args[i+1]);
                        ++i;
                    }
                    if (args[i].equals("-feedback") && args[i+1].equals("true")){
                        feedback = true;
                        ++i;
                    }
                }
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setSimilarity(similarity);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE); // overwrite index if needed

                IndexWriter writer = new IndexWriter(FSDirectory.open(index_path), config);
                if (!feedback)
                    indexFiles(writer, documents_path);
                else
                    termIndex(writer, documents_path);

                return;
            }

            if (args[0].equals("search")){
                Integer k = 10;
                String query = "test";
                for(int i = 1; i < args.length; ++i){
                    if (args[i].equals("-index")){
                        index_path = Path.of(args[i+1]);
                        ++i;
                    }
                    if (args[i].equals("-feedback") && args[i+1].equals("true")){
                        feedback = true;
                        ++i;
                    }

                    if (args[i].equals("-k")){
                        k = Integer.valueOf(args[i+1]);
                        ++i;
                    }

                    if (args[i].equals("-query")){
                        query = args[i+1];
                        ++i;
                    }
                }

                List<Document> documents;
                if (!feedback) {
                    documents = searchIndex(query, k, false);
                }
                else{
                    documents = doFeedbackSearch(query, k);
                }
                for (Integer i = 0; i < documents.size(); ++i) {
                    System.out.print(i + 1);
                    System.out.println(": " + documents.get(i).get("title") + " (" + documents.get(i).get("id") + ")");
                }

                return;
            }

            if(args[0].equals("benchmark")){
                Integer k = 10;
                String tokenfilter = "none";
                for(int i = 1; i < args.length; ++i){
                    if (args[i].equals("-titles")){
                        title_file = Path.of(args[i+1]);
                        ++i;
                    }
                    if (args[i].equals("-index")){
                        index_path = Path.of(args[i+1]);
                        ++i;
                    }
                    if (args[i].equals("-k")){
                        k = Integer.valueOf(args[i+1]);
                        ++i;
                    }
                    if (args[i].equals("-tokenfilter")){
                        tokenfilter = args[i+1];
                        ++i;
                    }
                    if(args[i].equals("-similarity")){
                        switch (args[i + 1]) {
                            case "LM":
                                similarity = new LMDirichletSimilarity(0.5F);
                                break;
                            case "TF-IDF":
                                similarity = new ClassicSimilarity();
                                break;
                            case "okapi":
                                similarity = new BM25Similarity();
                                break;
                        }

                        ++i;
                    }
                }
                if (tokenfilter.equals("none"))
                    analyzer = CustomAnalyzer.builder().withTokenizer("whitespace").build();
                else
                    analyzer = CustomAnalyzer.builder().withTokenizer("whitespace").addTokenFilter(tokenfilter).build();
                benchmark(k, false);
            }
        }
//        IndexWriterConfig config = new IndexWriterConfig(analyzer);
//        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE); // overwrite index if needed
//
//        IndexWriter writer = new IndexWriter(FSDirectory.open(Path.of("../small_index")), config);
//        termIndex(writer, Path.of("../small"));


//        for (Double alpha = 0.7D; alpha < 1.0D; alpha += 0.2D) {
//            Double beta = 1D - alpha;
//            Double gamma = 0.1D;
//
//            Files.write(output_path, new String("Alpha: " + alpha + " Beta: " + beta + " Gamma: " + gamma + " k: 10 " + benchmarkFeedback(10, 1, alpha, beta, gamma).toString() + "\n").getBytes(), StandardOpenOption.APPEND);
//            Files.write(output_path, new String("Alpha: " + alpha + " Beta: " + beta + " Gamma: " + gamma + " k: 50 " + benchmarkFeedback(50, 1, alpha, beta, gamma).toString() + "\n").getBytes(), StandardOpenOption.APPEND);
//            Files.write(output_path, new String("Alpha: " + alpha + " Beta: " + beta + " Gamma: " + gamma + " k: 100 " + benchmarkFeedback(100, 1, alpha, beta, gamma).toString() + "\n").getBytes(), StandardOpenOption.APPEND);
//        }
//        Double alpha = 0.5D;
//        Double beta = 1D - alpha;
//        Double gamma = 0.1D;
//        Files.write(output_path, new String("Alpha: " + alpha + " Beta: " + beta + " Gamma: " + gamma + " k: 10 " + benchmarkFeedback(10, 1, alpha, beta, gamma).toString() + "\n").getBytes(), StandardOpenOption.APPEND);
//        Files.write(output_path, new String("Alpha: " + alpha + " Beta: " + beta + " Gamma: " + gamma + " k: 50 " + benchmarkFeedback(50, 1, alpha, beta, gamma).toString() + "\n").getBytes(), StandardOpenOption.APPEND);
//        Files.write(output_path, new String("Alpha: " + alpha + " Beta: " + beta + " Gamma: " + gamma + " k: 100 " + benchmarkFeedback(100, 1, alpha, beta, gamma).toString() + "\n").getBytes(), StandardOpenOption.APPEND);

//        for(int i = 0; i < 2; ++i){
//            CustomAnalyzer.Builder builder = CustomAnalyzer.builder().withTokenizer("whitespace");
////            analyzer = CustomAnalyzer.builder().withTokenizer("whitespace")/*.addTokenFilter("lowercase")*/.build();
//
//
//            String filter = "none";
//            if (i%2 == 1){
//                builder.addTokenFilter("lowercase");
//                filter = "lowercase";
//            }
//
//            String sim = "Okapi";
//            if (i >= 2 && i < 4){
//                similarity = new ClassicSimilarity();
//                sim = "TF-IDF";
//            }
//            if (i >= 4){
//                similarity = new LMJelinekMercerSimilarity(0.5f);
//                sim = "Language";
//            }
//            analyzer = builder.build();
//
//            if (i != 2){
//                Directory dir = FSDirectory.open(index_path);
//
//                IndexWriterConfig config = new IndexWriterConfig(analyzer);
//                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE); // overwrite index if needed
//
//                IndexWriter writer = new IndexWriter(dir, config);
//                indexFiles(writer, documents_path);
//
//                Files.write(output_path, new String("Similarity: "+ sim  + " Filter: " + filter + " k: 10 " + benchmark(10, false).toString()).getBytes(), StandardOpenOption.APPEND);
//                Files.write(output_path, new String("Similarity: "+ sim  + " Filter: " + filter + " k: 50 " + benchmark(50, false).toString()).getBytes(), StandardOpenOption.APPEND);
//                Files.write(output_path, new String("Similarity: "+ sim  + " Filter: " + filter + " k: 100 " + benchmark(100, false).toString()).getBytes(), StandardOpenOption.APPEND);
//
//            }
//
//            Files.write(output_path, new String("Similarity: "+ sim  + " Filter: " + filter + " k: 10 " + benchmark(10, true).toString()).getBytes(), StandardOpenOption.APPEND);
//            Files.write(output_path, new String("Similarity: "+ sim  + " Filter: " + filter + " k: 50 " + benchmark(50, true).toString()).getBytes(), StandardOpenOption.APPEND);
//            Files.write(output_path, new String("Similarity: "+ sim  + " Filter: " + filter + " k: 100 " + benchmark(100, true).toString()).getBytes(), StandardOpenOption.APPEND);
//
//        }
//        searchAndPrint("QT application crashes on startup (c++ windows)", 10);
    }

    public static void indexFiles(IndexWriter writer, Path path) throws IOException{
        // Call indexXML for each file in the path
        final Integer[] i = {0};
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes basicFileAttributes) throws IOException {
                try {
                    indexXML(writer, file);
                    i[0] += 1;

                    if (i[0] % 10000 == 0)
                        System.out.println("Indexing progress : " + i[0].toString() + " / 2000000");
                } catch (IOException | ParserConfigurationException | SAXException ignored){

                }
                return FileVisitResult.CONTINUE;
            }
        });
        writer.commit();
        writer.close();
    }

    public static void indexXML(IndexWriter writer, Path path) throws IOException, ParserConfigurationException, SAXException {
        // XML parsing code based on https://www.mkyong.com/java/how-to-read-xml-file-in-java-dom-parser/

        File xmlFile = path.toFile();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document xmlDoc = builder.parse(xmlFile);

        NodeList nodeList = xmlDoc.getDocumentElement().getChildNodes();
        List<String> answers = new ArrayList<String>();
        Node node = nodeList.item(1);
        Element elem = (Element) node;

        String title = elem.getElementsByTagName("Title")
                .item(0).getChildNodes().item(0).getNodeValue();

        String question_body = elem.getElementsByTagName("Body").item(0)
                .getChildNodes().item(0).getNodeValue();

        String tags = elem.getElementsByTagName("Tags").item(0)
                .getChildNodes().item(0).getNodeValue();

        for (int i = 2; i < nodeList.getLength(); i++) {
            node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elem = (Element) node;

                String body = elem.getElementsByTagName("Body").item(0)
                        .getChildNodes().item(0).getNodeValue();

                answers.add(body);
            }
        }
        Document luceneDoc = new Document();
        FieldType Stored_Not_Indexed = new FieldType();
        Stored_Not_Indexed.setStored(true);
        Field titleField = new Field("title", title, Stored_Not_Indexed);  // include title in the index for checking but don't use it for searching
        luceneDoc.add(titleField);
        luceneDoc.add(new Field("id", path.getFileName().toString().split("\\.")[0], Stored_Not_Indexed)); //ID = filename MINUS extension
        luceneDoc.add(new TextField("tags", tags, Field.Store.NO));
        luceneDoc.add(new TextField("question", question_body, Field.Store.NO));
        luceneDoc.add(new TextField("answers", String.join("\n", answers), Field.Store.NO));
        writer.addDocument(luceneDoc);

    }

    public static Integer averagePosition() throws IOException {
        Path title_file = Path.of("../titles.txt");

        AtomicReference<Integer> count = new AtomicReference<>(0);
        // File iteration based on https://www.artificialworlds.net/blog/2017/02/09/iterating-over-the-lines-of-a-file-in-java/ (Comment by Scott Shipp)
        AtomicReference<Integer> total = new AtomicReference<>(0);

        Files.lines(title_file).forEach((title)->{
            Integer score = positionOfDocument(title, false);
            if (score > -1) {
                count.updateAndGet(v -> v = 1);
                total.updateAndGet(v -> v + positionOfDocument(title, false));
            }
        });
        System.out.println("Count: " + count.get());
        return total.get()/count.get();
    }

    public static Integer positionOfDocument(String title, boolean multifield) {
        try {
            IndexReader reader = DirectoryReader.open(FSDirectory.open(index_path));
            IndexSearcher searcher = new IndexSearcher(reader);


            try {
                QueryParser parser;
                if (multifield)
                    parser = new MultiFieldQueryParser(new String[]{"question", "answers", "tags"}, analyzer);
                else
                    parser = new QueryParser(new String("question"), analyzer);

                Query query = parser.parse(QueryParser.escape(title));

                int k = searcher.count(query);
                ScoreDoc[] docs = searcher.search(query, k).scoreDocs;
                for (int i = 0; i < k; ++i) {
                    ScoreDoc scoreDoc = docs[i];
                    Document document = searcher.doc(scoreDoc.doc);
                    if (document.get("title").equals(title))
                        return i;
                }
            } catch (ParseException e) {
                // based on https://stackoverflow.com/questions/5762491/how-to-print-color-in-console-using-system-out-println
                System.out.println("\033[0;31m" + "Can't parse query " + title + "\033[0m");
            }
        }
        catch (IOException ignored){

        }
        return -1;
    }

    public static List<Document> searchIndex(String searchterm, Integer k, boolean multiField) throws IOException {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(index_path));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);

        List<Document> returnDocuments = new ArrayList<Document>();

        try {
            QueryParser parser;
            if (multiField)
                parser = new MultiFieldQueryParser(new String[]{"question", "answers", "tags"}, analyzer);
            else
                parser = new QueryParser(new String("question"), analyzer);

            Query query = parser.parse(QueryParser.escape(searchterm));

            ScoreDoc[] docs = searcher.search(query, k).scoreDocs;
            for (ScoreDoc scoreDoc : docs) {
                returnDocuments.add(searcher.doc(scoreDoc.doc));
            }
        }
        catch (ParseException e){
            System.out.println("\033[0;31m" + "Can't parse query " + searchterm + "\033[0m");
        }
        return returnDocuments;
    }

    public static Integer benchmark(Integer k, boolean multifield) throws IOException {
         //Titles are stored in a file, try for each title if one of the found documents is the same as the searched (i.e it has the same title)

        AtomicReference<Integer> count = new AtomicReference<>(0);
        AtomicReference<Integer> i = new AtomicReference<Integer>(0);

        // based on https://stackoverflow.com/a/35523560
        long title_count = Files.lines(title_file).count();
        // File iteration based on https://www.artificialworlds.net/blog/2017/02/09/iterating-over-the-lines-of-a-file-in-java/ (Comment by Scott Shipp)
        System.out.println("\033[0;32mBenchmarking started!\033[0m");
        Files.lines(title_file).forEach((title)->{
            try {
                List<Document> results = searchIndex(title, k, multifield);
                i.updateAndGet(v -> v + 1);
                for(Document doc : results){
                    if(doc.get("title").equals(title)){
//                        System.out.println(doc.toString());
                        count.updateAndGet(v -> v + 1);
                        break;
                    }
                }
                if (i.get() % 10 == 0)
                    System.out.println("benchmarking progress: " + i.get().toString() + " / " + title_count);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        System.out.println("k: " + k + " count: " + count);
        return count.get();
    }

    public static Integer benchmarkFeedback(Integer k, Integer iterations, Double alpha, Double beta, Double gamma) throws IOException {
        //Titles are stored in a file, try for each title if one of the found documents is the same as the searched (i.e it has the same title)

        Path title_file = Path.of("../titles.txt");

        AtomicReference<Integer> count = new AtomicReference<>(0);
        AtomicReference<Integer> i = new AtomicReference<Integer>(0);
        // File iteration based on https://www.artificialworlds.net/blog/2017/02/09/iterating-over-the-lines-of-a-file-in-java/ (Comment by Scott Shipp)

        Files.lines(title_file).forEach((title)->{
            try {
                List<Document> results = feedbackExpansionTesting(title, k, iterations, alpha, beta, gamma);
                i.updateAndGet(v -> v + 1);
                for(Document doc : results){
                    if(doc.get("title").equals(title)){
//                        System.out.println(doc.toString());
                        count.updateAndGet(v -> v + 1);
                        break;
                    }
                }
                if (i.get() % 100 == 0)
                    System.out.println("benchmarking progress: " + i.get().toString() + " / 1000");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        System.out.println("k: " + k + " count: " + count);
        return count.get();
    }

    public static List<Document> doFeedbackSearch(String query, Integer k) throws IOException {
        String[] words = query.split(" ");

        IndexReader reader = DirectoryReader.open(FSDirectory.open(index_path));

        HashMap<String, Double> query_terms = new HashMap<String, Double>();
        for(String word : words){
            if (query_terms.containsKey(word))
                query_terms.replace(word, query_terms.get(word) + 1);
            else
                query_terms.put(word, 1D);
        }
        List<Integer> docIDs = feedbackSearch(query_terms, reader, k);

        List<Integer> relevant = new ArrayList<>();
        List<Integer> irrelevant = new ArrayList<>();


        // based on https://data-flair.training/blogs/read-java-console-input/
        Scanner in = new Scanner(System.in);
        System.out.println("Do you want to use relevance feedback? (Y/N)");
        if(in.nextLine().equals("Y")) {
            System.out.println("\n\033[0;32mType after each title, Y for relevant, N for irrelevant\033[0m");
            for (Integer i = 0; i < docIDs.size() ; ++i) {
                Integer docID = docIDs.get(i);
                String title = reader.document(docID).get("title");
                System.out.print((i + 1) + ": " + title + "  : ");
                if (in.nextLine().equals("Y"))
                    relevant.add(docID);
                else
                    irrelevant.add(docID);
            }
            System.out.println("\nDoing relevance feedback\n");
            query_terms = Expand(query_terms, reader, relevant, irrelevant, 0.9, 0.1, 0D);
            docIDs = feedbackSearch(query_terms, reader, k);
        }

        List<Document> docs = new ArrayList<>();
        for(Integer docID : docIDs){
            docs.add(reader.document(docID));
        }

        return docs;

    }

    public static List<Integer> feedbackSearch(HashMap<String, Double> query, IndexReader reader, Integer k) throws IOException {
        List<Double> scores = new ArrayList<>();
        List<Integer> documents = new ArrayList<>();

        for(int i = 0; i < reader.numDocs(); ++i){
            HashMap<String, Double> document_terms = TermVectorOfDocument(reader, i);
            Double score = 0D;
            Double query_norm = 0D;
            Double document_norm = 0D;
            for(String key: query.keySet()) {
                if (document_terms.containsKey(key)) {
                    score += query.get(key) * document_terms.get(key);
                }
                query_norm += Math.pow(query.get(key), 2);
            }

            for(String key: document_terms.keySet()){
                document_norm += Math.pow(document_terms.get(key), 2);
            }

            query_norm = Math.sqrt(query_norm);
            document_norm = Math.sqrt(document_norm);
            score /= (query_norm*document_norm);

//            if (i % 100 == 0)
//                System.out.println("Scoring " + i + " of " + reader.numDocs());

            if (score == 0) continue;
            boolean inserted = false;
            for(Integer j = 0; j < scores.size(); ++j){
                if(score > scores.get(j)) {
                    scores.add(j, score);
                    documents.add(j, i);
                    inserted = true;
                    break;
                }
            }
            if (scores.size() < k && !inserted) {
                scores.add(score);
                documents.add(i);
            }
            else if (scores.size() > k ) {
                scores.remove(k.intValue());
                documents.remove(k.intValue());
            }
        }
//        System.out.println("\u001B[1m\033[0;32mScoring done\033[0m");
        return documents;
    }

    public static List<Document> feedbackExpansionTesting(String query, Integer k, Integer cycles, Double alpha, Double beta, Double gamma) throws IOException {
//        IndexWriterConfig config = new IndexWriterConfig(analyzer);
//        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE); // overwrite index if needed
//
//        IndexWriter writer = new IndexWriter(FSDirectory.open(Path.of("../small_index")), config);
//        termIndex(writer, Path.of("../small"));

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Path.of("../small_index")));
        String[] words = query.split(" ");

        HashMap<String, Double> query_terms = new HashMap<String, Double>();
        for(String word : words){
            if (query_terms.containsKey(word))
                query_terms.replace(word, query_terms.get(word) + 1);
            else
                query_terms.put(word, 1D);
        }
        List<Integer> docIDs = feedbackSearch(query_terms, reader, k);

        for(Integer i = 0; i < cycles; ++i){
            List<Integer> relevant = new ArrayList<>();
            List<Integer> irrelevant = new ArrayList<>();

            for(Integer docID : docIDs){
                List<String> document_terms = new ArrayList<>(Arrays.asList(reader.document(docID).get("title").split(" ")));

                List<String> remaining = new ArrayList<>();
                for(String term : document_terms)
                    if (query_terms.containsKey(term)) remaining.add(term);

                if (remaining.size() >= 2)
                    relevant.add(docID);
                else
                    irrelevant.add(docID);
            }
            query_terms = Expand(query_terms, reader, relevant, irrelevant, alpha, beta, gamma);
            docIDs = feedbackSearch(query_terms, reader, k);
        }

        List<Document> docs = new ArrayList<>();
        for(Integer docID : docIDs){
            docs.add(reader.document(docID));
        }

        return docs;
    }

    // Do Rocchio's feedback Expansion
    public static HashMap<String, Double> Expand(HashMap<String, Double> query, IndexReader reader, List<Integer> relevant, List<Integer> irrelevant, Double alpha, Double beta, Double gamma) throws IOException {

        HashMap<String, Double> retValue = new HashMap<>();
        for(String key : query.keySet()){
            retValue.put(key, alpha * query.get(key));
        }

        for(Integer docID : relevant){
            HashMap<String, Double> representation = TermVectorOfDocument(reader, docID);
            for(String key : representation.keySet()){
                if (retValue.containsKey(key)){
                    retValue.put(key, retValue.get(key) + beta*1D/relevant.size() * representation.get(key));
                }
                else{
                    retValue.put(key, beta*1D/relevant.size() * representation.get(key));
                }
            }
        }

        if (gamma == 0) return retValue;

        for(Integer docID : irrelevant){
            HashMap<String, Double> representation = TermVectorOfDocument(reader, docID);
            for(String key : representation.keySet()){
                if (retValue.containsKey(key)){
                    retValue.put(key, retValue.get(key) - gamma*1D/irrelevant.size() * representation.get(key));
                }
                else{
                    retValue.put(key, gamma*1D/irrelevant.size() * representation.get(key));
                }
                if(retValue.get(key) == 0D) // remove useless weights
                    retValue.remove(key);
            }
        }
        return retValue;
    }


    // Get a tf-idf vector for the document given by docID
    public static HashMap<String, Double> TermVectorOfDocument(IndexReader reader, int docID) throws IOException {
        Terms terms = reader.getTermVector(docID, "question");
        TermsEnum iterator = terms.iterator();

        HashMap<String, Double> mapping = new HashMap<String, Double>();
        while(true){ // iterate over all terms in document
            BytesRef bytes = iterator.next();
            if (bytes == null)
                break;
            String term = bytes.utf8ToString();

            Integer docs_containing_term = reader.docFreq(new Term("question", term));
            Integer total_docs=  reader.numDocs();
            Double idf = Math.log(total_docs.doubleValue()/docs_containing_term.doubleValue());
            mapping.put(term, iterator.totalTermFreq()*idf);

        }
        return mapping;
    }

    // Indexing for Relevance feedback
    public static void termIndex(IndexWriter writer, Path path) throws IOException{
        // Call termIndexXML for each file in the path
        final Integer[] i = {0};
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes basicFileAttributes) throws IOException {
                try {
                    termIndexXML(writer, file);
                    i[0] += 1;

                    if (i[0] % 10000 == 0)
                        System.out.println("Indexing progress : " + i[0].toString() + " files indexed");
                } catch (IOException | ParserConfigurationException | SAXException ignored){

                }
                return FileVisitResult.CONTINUE;
            }
        });
        writer.commit();
        writer.close();
    }

    // Index an XML for Relevance feedback, with a smaller file saved and saving the terms of the document
    public static void termIndexXML(IndexWriter writer, Path path) throws IOException, ParserConfigurationException, SAXException {
        // XML parsing code based on https://www.mkyong.com/java/how-to-read-xml-file-in-java-dom-parser/

        File xmlFile = path.toFile();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document xmlDoc = builder.parse(xmlFile);

        NodeList nodeList = xmlDoc.getDocumentElement().getChildNodes();
        Node node = nodeList.item(1);
        Element elem = (Element) node;

        String title = elem.getElementsByTagName("Title")
                .item(0).getChildNodes().item(0).getNodeValue();

        String question_body = elem.getElementsByTagName("Body").item(0)
                .getChildNodes().item(0).getNodeValue();

        Document luceneDoc = new Document();
        FieldType Stored_Not_Indexed = new FieldType();
        Stored_Not_Indexed.setStored(true);

        FieldType Indexed_terms = new FieldType();
        Indexed_terms.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        Indexed_terms.setTokenized(true);
        Indexed_terms.setStoreTermVectors(true);
        Field titleField = new Field("title", title, Stored_Not_Indexed);  // include title in the index for checking but don't use it for searching
        luceneDoc.add(titleField);
        luceneDoc.add(new Field("id", path.getFileName().toString().split("\\.")[0], Stored_Not_Indexed)); //ID = filename MINUS extension
        luceneDoc.add(new Field("question", question_body, Indexed_terms));
        writer.addDocument(luceneDoc);
    }
}
