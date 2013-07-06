package graph2site;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.kernel.impl.util.FileUtils;
import org.neo4j.tooling.GlobalGraphOperations;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedList;

public class ListOfLinks {

    private static final String DB_PATH = "target/neo4j-lol-db";
    private static final String SITE_PATH = "target/sites/lol/";

    private static enum MyLabels implements Label {WEBPAGE, LINK, COMPONENT, TEMPLATE };
    private static enum MyRelTypes implements RelationshipType {SHOWS};

    private static String PROP_TITLE = "Title";
    private static String PROP_URL = "URL";
    private static String PROP_FILE_NAME = "FileName";

    GraphDatabaseService graphDb;



    public static void main(String[] args) throws IOException {
	    ListOfLinks lol = new ListOfLinks();
        lol.deleteDB();
        lol.createDB();
        lol.populateDB();
        lol.generateWebsite();
        lol.shutDown();
    }

    /**
     * Delete the database by deleting it on the file system
     * Deleting it by iterating through nodes is complicated due to
     * Reference Nodes, Nodes and relations being separate, maybe even labels?
     */
    private void deleteDB() {
        try
        {
            FileUtils.deleteRecursively(new File(DB_PATH));
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }

    }
    private void createDB() {
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(DB_PATH);
        registerShutdownHook(graphDb);
        System.out.println("Database created/connected");
    }


    private void populateDB() {
        //Create a node of type/label WebPage and some links it points to
        Transaction tx = graphDb.beginTx();
        try
        {
            //Create a first web page
            Node page = graphDb.createNode(MyLabels.WEBPAGE);
            page.setProperty(PROP_TITLE, "Home page");
            page.setProperty(PROP_FILE_NAME, "home.html");

            AddLink(page, CreateLink("Link 1", "http://www.example.com/link1.html"));
            Node link2 = CreateLink("Link 2", "http://www.example.com/link2.html");
            AddLink(page, link2);

            //Create a second web page
            page = graphDb.createNode(MyLabels.WEBPAGE);
            page.setProperty(PROP_TITLE, "Page 1");
            page.setProperty(PROP_FILE_NAME, "page1.html");
            AddLink(page, link2); //share a link
            AddLink(page, CreateLink("Link 3", "http://www.example.com/link3.html"));

            tx.success();
        }
        finally {
            tx.finish();
        }
    }

    private Node CreateLink(String title, String url) {
        Node link;
        link = graphDb.createNode(MyLabels.LINK);
        link.setProperty(PROP_TITLE, title);
        link.setProperty(PROP_URL, url);
        return link;
    }

    private void AddLink(Node page,  Node link){
        page.createRelationshipTo(link, MyRelTypes.SHOWS);
    }


    private void generateWebsite() throws IOException {
        //ensure we have a website directory and it is empty
        File websiteDir = new File(SITE_PATH);
        FileUtils.deleteRecursively(websiteDir);
        websiteDir.mkdirs();

        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".tpl");
        resolver.setTemplateMode("HTML5");
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        Context context = new Context();

        for (Node page : GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(MyLabels.WEBPAGE)) {
            String fileName = (String)page.getProperty(PROP_FILE_NAME, "missing_file_name.html");
            String title = (String)page.getProperty(PROP_TITLE, "Missing Title");

            StringWriter writer = new StringWriter();

            context.setVariable("title", title);
            context.setVariable("links", getLinks(page));

            templateEngine.process("webpage", context, writer);


            File pageFile = new File(websiteDir, fileName);

            //spit it out
            new FileWriter(pageFile).append(writer.toString()).close();
        }
    }

    private class WebLink
    {
        public String title;
        public String URL;
    }

    private LinkedList<WebLink> getLinks(Node page) {
        LinkedList<WebLink> result = new LinkedList <WebLink>();
        for (Relationship showRel : page.getRelationships(Direction.OUTGOING, MyRelTypes.SHOWS)) {
            Node linkNode = showRel.getEndNode();
            WebLink link  = new WebLink();
            link.title = (String)linkNode.getProperty(PROP_TITLE);
            link.URL = (String)linkNode.getProperty(PROP_URL);
            result.addLast(link);
        }
        return result;
    }

    private void shutDown() {
        System.out.println("Database shut down!");
    }

    private void registerShutdownHook(final GraphDatabaseService graphDb) {
        //Registers a shutdown hook for the Neo4J instance to shut down nicely o Ctrl-C

        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run(){
                graphDb.shutdown();
            }
        });
    }

}
