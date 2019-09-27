/*
 * Copyright (C) 2019 User
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.fuchsdavid.annotator;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateRequest;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Main class of the Annotator project, which aims at enabling adding
 * annotations to RDF resources.
 * 
 * @author David Fuchs
 */
public class Main {
    private static final String INDEX = "/www/index.xhtml";
    private static final String CONSTRUCT = "/sparql/construct.sparql";
    private static final Random RNG = new Random();
    private static final Map<String,Collection<Model>> ID2MODEL_LIST = new HashMap<>();
    private static final Map<String,Position> ID2POSITION = new HashMap<>();
    private static final Map<String,Document> CACHED_FILES = new HashMap<>();
    private static final TransformerFactory TF = TransformerFactory.newInstance();
    private static JsonBuilderFactory JF = Json.createBuilderFactory(null);
    
    public static String SPARQLendpoint;
    public static int port = 8080;
    
    private static HttpServer server;
    private static MessageDigest md;
    private static DocumentBuilder builder;
    private static int offset = 0;
    
    static {
        try{
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            SPARQLendpoint = (new URL("http://localhost:3030/fuseki")).toExternalForm();
            CACHED_FILES.put(INDEX,builder.parse(Main.class.getResourceAsStream(INDEX)));
            md = MessageDigest.getInstance("SHA1");
        }
        catch(MalformedURLException | NoSuchAlgorithmException | ParserConfigurationException | SAXException ex){
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
        catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }
    
    /**
     * Application entry point
     * 
     * @param args 
     */
    public static void main(String[] args){
        Argument.parseArguments(args);
        if(!runHTTPServer()) System.exit(1);
    }

    /**
     * Creates and runs HTTP server.
     * 
     * @return
     */
    public static boolean runHTTPServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(port),30);
            server.createContext("/", Main::handleRequest);
            server.createContext("/data", Main::handleDataRequest);
            server.start();
        }
        catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        finally{
            return true;
        }
    }
    
    /**
     * Handles client requests for static content.
     * 
     * @param exchange
     * @throws IOException 
     */
    private static void handleRequest(HttpExchange exchange) throws IOException {
        String session_id = retrieveSessionID(exchange);
        // Dispatch allowed request methods.
        switch(exchange.getRequestMethod()){
            case "GET":   doGet(exchange,session_id);          break;
            default:      exchange.sendResponseHeaders(400, 0);
        }
    }
    
    /**
     * Handles GET requests for static content.
     * 
     * @param exchange
     * @param session_id
     * @throws IOException 
     */
    private static void doGet(HttpExchange exchange, String session_id) throws IOException{
        String path = "/";
        if(exchange.getRequestURI().getPath().endsWith("/")){
            path += "www" + exchange.getRequestURI().getPath() + "index.xhtml";
            // Send new cookie to a new client.
            if(session_id.equals("") || !ID2MODEL_LIST.containsKey(session_id)){
                ArrayList<String> newCookies = new ArrayList<>();
                String hash = session_id = generateHash(Long.toString(RNG.nextLong()));
                newCookies.add("SESSION_ID=" + hash);
                exchange.getResponseHeaders().put("Set-Cookie", newCookies);
                Model m = retrieveTriples(exchange,Main.class.getResource(CONSTRUCT).getFile());
                Collection<Model> rdfCollection = new ArrayList<>();
                rdfCollection.add(m);
                ID2MODEL_LIST.put(hash,rdfCollection);
                ID2POSITION.put(hash, new Position(0));
            }
        }
        else if(!exchange.getRequestURI().getPath().contains(".")){
            path += "www" + exchange.getRequestURI().getPath() + ".xhtml";
        }
        else{
            path += "www" + exchange.getRequestURI().getPath();
        }
        File file = new File(Main.class.getResource(path).getFile());
        if(!file.exists())
            exchange.sendResponseHeaders(404, 0);
        if(CACHED_FILES.keySet().contains(path)){
            try{
                Document document = builder.newDocument();
                Node root = CACHED_FILES.get(path).getDocumentElement().cloneNode(true);
                String license = "";
                if(CACHED_FILES.get(path).getFirstChild().getNodeType() == Node.COMMENT_NODE){
                    license = CACHED_FILES.get(path).getFirstChild().getNodeValue();
                }
                document.adoptNode(root);
                document.appendChild(root);
                document.insertBefore(document.createComment(license), root);
                
                final Node tbody = document.getElementsByTagName("tbody").item(0);
                final Model m;
                if(ID2MODEL_LIST.get(session_id).size() > ID2POSITION.get(session_id).getPosition()){
                    m = (Model)(ID2MODEL_LIST.get(session_id).toArray()[ID2POSITION.get(session_id).getPosition()]);
                    m.listStatements().forEachRemaining(statement -> {
                        Node tr = tbody.appendChild(document.createElement("tr"));
                        Node s = tr.appendChild(document.createElement("td"));
                        s.setTextContent(statement.getSubject().getURI());
                        Node p = tr.appendChild(document.createElement("td"));
                        p.setTextContent(statement.getPredicate().getURI());
                        Node o = tr.appendChild(document.createElement("td"));
                        if(statement.getObject().isResource())
                            o.setTextContent(statement.getObject().toString());
                        else
                            o.setTextContent(statement.getObject().asLiteral().getLexicalForm());
                    });
                }
                else{
                    Node p = document.createElement("p");
                    document.getElementsByTagName("body").item(0).appendChild(p);
                    p.setTextContent("There are no records to show.");
                }
                Transformer t = TF.newTransformer();
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                t.transform(new DOMSource(document), new StreamResult(os));
                InputStream is = new ByteArrayInputStream(os.toByteArray());
                exchange.sendResponseHeaders(200, is.available());
                t.transform(new DOMSource(document), new StreamResult(exchange.getResponseBody()));
                os.close();
                is.close();
                exchange.getResponseBody().close();
            }
            catch(TransformerConfigurationException ex){
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(1);
            }
            catch(TransformerException | DOMException ex){
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(1);
            }
        }
        else{
            InputStream is = new FileInputStream(file);
            OutputStream os = exchange.getResponseBody();
            exchange.sendResponseHeaders(200, is.available());
            IOUtils.copy(is, os);
            os.close();
            is.close();
        }
    }
    
    /**
     * Handles client requests for data in JSON.
     * 
     * @param exchange
     */
    private static void handleDataRequest(HttpExchange exchange){
        String session_id = retrieveSessionID(exchange);
        try{
            switch(exchange.getRequestMethod()){
                case "GET":    doGetData(exchange, session_id);     break;
                case "DELETE": doDeleteData(exchange, session_id);  break;
                case "POST":   doPostData(exchange,session_id);     break;
                default:       exchange.sendResponseHeaders(400, 0);
            }
        }
        catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Handles GET requests.
     * 
     * @param exchange
     * @param session_id 
     */
    private static void doGetData(HttpExchange exchange, String session_id) throws IOException{
        Collection<Model> rdfCollection = ID2MODEL_LIST.get(session_id);
        Map<String,String> params = retrieveQueryParameters(exchange);
        Model m = null;
        switch(params.get("direction")){
            case "forward":
                if(ID2POSITION.get(session_id).getPosition() >= ID2MODEL_LIST.get(session_id).size() - 1){
                    m = retrieveTriples(exchange,Main.class.getResource(CONSTRUCT).getFile());
                    ID2POSITION.get(session_id).preincrement();
                    rdfCollection.add(m);
                }
                else{
                    m = (Model)(rdfCollection.toArray()[ID2POSITION.get(session_id).preincrement()]);
                }
                break;
            case "backward":
                m = (Model)(rdfCollection.toArray()[ID2POSITION.get(session_id).predecrement()]);
                break;
        }
        if(m == null){
            exchange.sendResponseHeaders(400, 0);
            return;
        }
        String json;
        try{
            JsonObjectBuilder object = JF.createObjectBuilder();
            JsonArrayBuilder array = JF.createArrayBuilder();
            m.listStatements().forEachRemaining(statement -> {
                if(statement.getObject().isResource())
                    array.add(JF.createObjectBuilder()
                        .add("subject",  statement.getSubject().toString())
                        .add("predicate",statement.getPredicate().toString())
                        .add("object",   statement.getObject().toString())
                        .build());
                else
                    array.add(JF.createObjectBuilder()
                        .add("subject",  statement.getSubject().toString())
                        .add("predicate",statement.getPredicate().toString())
                        .add("object",  statement.getObject().asLiteral().getLexicalForm())
                        .build());
            });
            object.add("triples", array);
            json = object.build().toString();
        }
        catch(JsonException ex){
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(500, 0);
            return;
        }
        exchange.sendResponseHeaders(200, json.length());
        exchange.getResponseBody().write(json.getBytes());
        exchange.getResponseBody().close();
    }
    
    /**
     * Handles DELETE requests.
     * 
     * @param exchange
     * @throws IOException 
     */
    private static void doDeleteData(HttpExchange exchange, String session_id) throws IOException{
        Map<String,String> params = retrieveQueryParameters(exchange);
        if(params.containsKey("numberOfTriples") && Integer.parseInt(params.get("numberOfTriples")) == 0){
            ID2MODEL_LIST.get(session_id).remove(ID2MODEL_LIST.get(session_id).toArray()[ID2MODEL_LIST.get(session_id).size()-1]);
            ID2POSITION.get(session_id).postdecrement();
            exchange.sendResponseHeaders(202, 0);
        }
        else exchange.sendResponseHeaders(400, 0);
    }
    
    /**
     * Handles POST requests to create new annotations for existing data.
     * 
     * @param exchange
     * @throws IOException 
     */
    private static void doPostData(HttpExchange exchange, String session_id) throws IOException{
        JsonReader input = Json.createReader(exchange.getRequestBody());
        JsonStructure topLevelObject = input.read();
        try{
            JsonArray annotations = topLevelObject.getValue("/annotations").asJsonArray();
            StringBuilder sb = new StringBuilder();
            sb.append("INSERT DATA {\n");
            for(int i=0 ;i<Integer.parseInt(topLevelObject.getValue("/numberOfAnnotations").toString()); i++)
                sb.append("?subject").append(i).append(" a ?concept").append(i).append(" .\n");
            sb.append("}");
            ParameterizedSparqlString pss = new ParameterizedSparqlString(sb.toString());
            for(int i=0 ;i<Integer.parseInt(topLevelObject.getValue("/numberOfAnnotations").toString()); i++){
                JsonValue annotation = annotations.get(i);
                pss.setIri("subject" + i, ((Model)(ID2MODEL_LIST.get(session_id).toArray()[i])).listSubjects().next().getURI());
                switch(annotation.asJsonObject().getValue("/type").toString().replace("\"", "")){
                    case "Work":         pss.setIri("concept" + i, new URL("http://vocab.org/frbr/core.html#term-Work"));          break;
                    case "Item":         pss.setIri("concept" + i, new URL("http://vocab.org/frbr/core.html#term-Item"));          break;
                    case "Manifestation":pss.setIri("concept" + i, new URL("http://vocab.org/frbr/core.html#term-Manifestation")); break;
                    case "Expression":   pss.setIri("concept" + i, new URL("http://vocab.org/frbr/core.html#term-Expression"));    break;
                    default: throw new Exception("Illegal concept suggested by the client.");
                }
            }
            System.out.println(pss);
            UpdateRequest update = pss.asUpdate();
            UpdateExecutionFactory.createRemote(update, SPARQLendpoint).execute();
            ID2MODEL_LIST.remove(session_id);
            ID2MODEL_LIST.keySet().remove(session_id);
            ID2POSITION.remove(session_id);
            ID2POSITION.keySet().remove(session_id);
            exchange.sendResponseHeaders(201, 0);
        }
        catch(JsonException ex){
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(400, 0);
        }
        catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(400, 0);
        }
    }
    
    /**
     * Hashes value of the input.
     * 
     * @param input
     * @return 
     */
    private static String generateHash(String input){
        md.reset();
        StringBuilder sb = new StringBuilder("");
        for(Byte b : md.digest(input.getBytes())){
            sb.append(Integer.toHexString(b));
        }
        return "0x" + sb.toString().replace("0x", "");
    }
    
    /**
     * Retrieves triples from SPARQL endpoint using query loaded from resource file.
     * 
     * @param file
     * @return 
     */
    private static Model retrieveTriples(HttpExchange exchange,String file) throws IOException{
        Model m = null;
        try {
            FileInputStream f = new FileInputStream(new File(file));
            ParameterizedSparqlString pss = new ParameterizedSparqlString(IOUtils.toString(f, StandardCharsets.UTF_8.name()));
            pss.setLiteral("offset", offset++);
            pss.setLiteral("limit", 1);
            Query query = pss.asQuery();
            m = QueryExecutionFactory.sparqlService(SPARQLendpoint,query).execConstruct();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(500, 0);
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(500, 0);
        }
        return m;
    }

    /**
     * Retrieves session ID from HTTP request header.
     * 
     * @param exchange
     * @return 
     */
    private static String retrieveSessionID(HttpExchange exchange) {
        Headers h = exchange.getRequestHeaders();
        if(h.containsKey("Cookie"))
            for(String cookie : h.get("Cookie"))
                if(cookie.startsWith("SESSION_ID="))
                    return cookie.split("=")[1];
        return "";
    }

    /**
     * Retrieves query parameters from HTTP GET request URL.
     * 
     * @param exchange
     * @return 
     */
    private static Map<String,String> retrieveQueryParameters(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        Map<String,String> params = new HashMap<>();
        for(String param : query.split("&")){
            if(query.contains("=")){
                String[] parts = param.split("=");
                params.put(parts[0], parts[1]);
            }
        }
        return params;
    }
}
