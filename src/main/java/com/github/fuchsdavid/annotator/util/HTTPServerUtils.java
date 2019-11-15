/*
 * Copyright (C) 2019 David Fuchs
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
package com.github.fuchsdavid.annotator.util;

import com.github.fuchsdavid.annotator.Main;
import static com.github.fuchsdavid.annotator.Main.*;
import com.github.fuchsdavid.annotator.Position;
import com.github.fuchsdavid.annotator.User;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateRequest;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * This class wraps the functionality provided by a library implementation
 * of a HTTP server and adds application logic.
 * 
 * @author David Fuchs
 */
public class HTTPServerUtils {
    
    private HTTPServerUtils(){}
    

    /**
     * Creates and runs HTTP server.
     * 
     * @return
     */
    public static HttpServer runHTTPServer() {
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(port),30);
            server.createContext("/", HTTPServerUtils::handleRequest);
            server.createContext("/data", HTTPServerUtils::handleDataRequest);
            server.createContext("/auth", HTTPServerUtils::handleAuthentication);
            server.start();
        }
        catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            server = null;
        }
        catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            server = null;
        }
        finally{
            return server;
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
        if(session_id.equals("")) session_id = createSession(exchange);
        // Dispatch allowed request methods.
        switch(exchange.getRequestMethod()){
            case "GET": doGet(exchange,session_id);
                        break;
            default:    exchange.sendResponseHeaders(405, 0);
                        exchange.getResponseBody().close();
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
        if(exchange.getRequestURI().getPath().contains("..")){
            exchange.sendResponseHeaders(403, 0);
            exchange.getResponseBody().close();
            return;
        }
        if((exchange.getRequestURI().getPath().endsWith("/") || !exchange.getRequestURI().getPath().contains("."))
                && !EMAIL2STATE.get(ID2USER.get(session_id).email)){
            exchange.sendResponseHeaders(401, 0);
            exchange.getResponseBody().close();
            return;
        }
        if(exchange.getRequestURI().getPath().endsWith("/"))
            path += "www" + exchange.getRequestURI().getPath() + "index.xhtml";
        else if(!exchange.getRequestURI().getPath().contains("."))
            path += "www" + exchange.getRequestURI().getPath() + ".xhtml";
        else
            path += "www" + exchange.getRequestURI().getPath();
        if(exchange.getRequestURI().getPath().equals("/")){
            try{
                Document document = DOCUMENT_BUILDER.newDocument();
                Node root = CACHED_FILES.get(path).getDocumentElement().cloneNode(true);
                String license = "";
                if(CACHED_FILES.get(path).getFirstChild().getNodeType() == Node.COMMENT_NODE){
                    license = CACHED_FILES.get(path).getFirstChild().getNodeValue();
                }
                document.adoptNode(root);
                document.appendChild(root);
                document.insertBefore(document.createComment(license), root);
                if(ID2MODEL_LIST.get(session_id).size() > ID2POSITION.get(session_id).getPosition()){
                    fillTable(session_id, document);
                }
                else{
                    Node p = document.createElement("p");
                    document.getElementsByTagName("body").item(0).appendChild(p);
                    p.setTextContent("There are no records to show.");
                }
                Transformer t = TF.newTransformer();
                try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                    t.transform(new DOMSource(document), new StreamResult(os));
                    exchange.sendResponseHeaders(200, os.size());
                    exchange.getResponseBody().write(os.toByteArray());
                }
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
            String[] ext = path.split("\\.");
            switch(ext[ext.length-1]){
                case "css": exchange.getResponseHeaders().add("content-type", "text/css");               break;
                case "js":  exchange.getResponseHeaders().add("content-type", "text/javascript+module"); break;
            }
            try (InputStream is = Main.class.getResourceAsStream(path)) {
                exchange.sendResponseHeaders(200, is.available());
                is.transferTo(exchange.getResponseBody());
            }
            exchange.getResponseBody().close();
        }
    }

    /**
     * Fill table with triples in a model.
     * 
     * @param session_id
     * @param document
     * @throws DOMException 
     */
    private static void fillTable(String session_id, Document document) throws DOMException {
        final Node tbody = document.getElementsByTagName("tbody").item(0);
        final Node caption = document.getElementsByTagName("caption").item(0);
        final Model m = (Model)(ID2MODEL_LIST.get(session_id).toArray()[ID2POSITION.get(session_id).getPosition()]);
        final Node c = document.createElement("strong");
        c.appendChild(document.createTextNode("You are currently annotating resource:"));
        caption.appendChild(c);
        caption.appendChild(document.createTextNode(" " + RDFUtils.getPrefixedName(m.listSubjects().next())));
        sortStatementsByPredicates(m).forEach(statement -> {
            Node tr = tbody.appendChild(document.createElement("tr"));
            Node p = tr.appendChild(document.createElement("td"));
            p.setTextContent(RDFUtils.getPrefixedName(statement.getPredicate()));
            Node o = tr.appendChild(document.createElement("td"));
            if(statement.getObject().isResource())
                o.setTextContent(RDFUtils.getPrefixedName(statement.getObject().asResource()));
            else
                o.setTextContent(statement.getObject().asLiteral().getLexicalForm());
        });
    }
    
    /**
     * Return statements sorted by predicate lexicographically.
     * 
     * @param model containing statements
     * @return 
     */
    private static List<Statement> sortStatementsByPredicates(Model m){
        List<Statement> statements = m.listStatements().toList();
        statements.sort((Statement o1, Statement o2) -> {
            return o1.getPredicate().asResource().getURI().compareTo(o2.getPredicate().asResource().getURI());
        });
        return statements;
    }
    
    /**
     * Handles client requests for data in JSON.
     * 
     * @param exchange
     */
    private static void handleDataRequest(HttpExchange exchange){
        String session_id = retrieveSessionID(exchange);
        try{
            if(session_id.equals("") || !EMAIL2STATE.get(ID2USER.get(session_id).email)){
                exchange.sendResponseHeaders(401, 0);
                exchange.getResponseBody().close();
                return;
            }
            switch(exchange.getRequestMethod()){
                case "GET":   doGetData(exchange, session_id);     break;
                case "DELETE":doDeleteData(exchange, session_id);  break;
                case "POST":  doPostData(exchange,session_id);     break;
                default:      exchange.sendResponseHeaders(405, 0);
                              exchange.getResponseBody().close();
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
                    m = RDFUtils.retrieveTriples(exchange,Main.class.getResourceAsStream(CONSTRUCT));
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
            exchange.getResponseBody().close();
            return;
        }
        String json;
        try{
            JsonObjectBuilder object = JF.createObjectBuilder();
            JsonArrayBuilder array = JF.createArrayBuilder();
            sortStatementsByPredicates(m).forEach(statement -> {
                JsonObjectBuilder createTriple = JF.createObjectBuilder()
                    .add("subject", RDFUtils.getPrefixedName(statement.getSubject()));
                createTriple.add("predicate", RDFUtils.getPrefixedName(statement.getPredicate()));
                if(statement.getObject().isResource())
                    createTriple.add("object", RDFUtils.getPrefixedName(statement.getObject().asResource()));
                else
                    createTriple.add("object",statement.getObject().asLiteral().getLexicalForm());
                array.add(createTriple.build());
            });
            object.add("triples", array);
            json = object.build().toString();
        }
        catch(JsonException ex){
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
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
            ID2MODEL_LIST.get(session_id).remove((Model)ID2MODEL_LIST.get(session_id).toArray()[ID2MODEL_LIST.get(session_id).size()-1]);
            ID2POSITION.get(session_id).postdecrement();
            exchange.sendResponseHeaders(202, 0);
            exchange.getResponseBody().close();
        }
        else{
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
        }
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
            int numberOfAnnotatedModels = Integer.parseInt(topLevelObject.getValue("/numberOfAnnotations").toString().replace("\"", ""));
            StringBuilder sb = new StringBuilder();
            sb.append("BASE <http://github.com/Fuchs-David/Annotator/tree/master/src/ontology/>\n");
            sb.append("INSERT DATA {\n");
            for(int i=0 ;i<numberOfAnnotatedModels; i++){
                if(annotations.get(i).getValueType().equals(ValueType.NULL)) continue;
                sb.append("?subject").append(i).append(" a ?concept").append(i).append(" .\n")
                  .append("?subject").append(i).append(" <annotatedBy>").append(" ?mbox").append(" .\n");
            }
            sb.append("}");
            ParameterizedSparqlString pss = new ParameterizedSparqlString(sb.toString());
            pss.setIri("mbox", ID2USER.get(session_id).email);
            for(int i=0 ;i<numberOfAnnotatedModels; i++){
                JsonValue annotation = annotations.get(i);
                if(annotation.getValueType().equals(ValueType.NULL)) continue;
                int p = Integer.parseInt(annotation.asJsonObject().getValue("/order").toString().replace("\"", ""));
                pss.setIri("subject" + i, ((Model)(ID2MODEL_LIST.get(session_id).toArray()[p])).listSubjects().next().getURI());
                switch(annotation.asJsonObject().getValue("/type").toString().replace("\"", "")){
                    case "Work":         pss.setIri("concept" + i, new URL("http://vocab.org/frbr/core.html#term-Work"));          break;
                    case "Item":         pss.setIri("concept" + i, new URL("http://vocab.org/frbr/core.html#term-Item"));          break;
                    case "Manifestation":pss.setIri("concept" + i, new URL("http://vocab.org/frbr/core.html#term-Manifestation")); break;
                    case "Expression":   pss.setIri("concept" + i, new URL("http://vocab.org/frbr/core.html#term-Expression"));    break;
                    default: throw new Exception("Illegal concept suggested by the client.");
                }
            }
            UpdateRequest update = pss.asUpdate();
            UpdateExecutionFactory.createRemote(update, SPARQLendpoint).execute();
            ID2MODEL_LIST.remove(session_id);
            ID2MODEL_LIST.keySet().remove(session_id);
            ID2POSITION.remove(session_id);
            ID2POSITION.keySet().remove(session_id);
            exchange.sendResponseHeaders(201, 0);
            exchange.getResponseBody().close();
        }
        catch(JsonException ex){
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
        }
        catch(InterruptedException ex){
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
        }
    }
    
    /**
     * Handles client authentication.
     * 
     * @param exchange
     * @throws IOException 
     */
    private static void handleAuthentication(HttpExchange exchange) throws IOException {
        String session_id = retrieveSessionID(exchange);
        boolean flag = true;
        if(session_id.equals("")) session_id = createSession(exchange);
        // Dispatch allowed request methods.
        try{
            switch(exchange.getRequestMethod()){
                case "GET": Transformer t = TF.newTransformer();
                            DOMSource document = new DOMSource(CACHED_FILES.get(AUTH));
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            t.transform(document, new StreamResult(os));
                            exchange.sendResponseHeaders(200, os.size());
                            exchange.getResponseBody().write(os.toByteArray());
                            os.close();
                            exchange.getResponseBody().close();
                            break;
                case "POST":JsonReader input = Json.createReader(exchange.getRequestBody());
                            JsonStructure root = input.read();
                            if(root.getValue("/createAccount").getValueType().equals(ValueType.TRUE))
                                if((flag = !createAccount(root, exchange))) return;
                            boolean loginResult = login(session_id, root, exchange, flag);
                            if(loginResult)
                                EMAIL2STATE.put(ID2USER.get(session_id).email, loginResult);
                            break;
                default:    exchange.sendResponseHeaders(405, 0);
                            exchange.getResponseBody().close();
            }
        }
        catch(TransformerConfigurationException ex){
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
        }
        catch(TransformerException | JsonException ex){
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
        }
        catch(Exception ex){
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
        }
    }
    
    /**
     * Create account for new user provided his credentials are syntacticly valid.
     * 
     * @param root
     * @param exchange
     * @return
     * @throws IOException 
     */
    private static boolean createAccount(JsonStructure root, HttpExchange exchange) throws IOException{
        String email = root.getValue("/email").toString().replace("\"", "");
        String password = root.getValue("/password").toString().replace("\"", "");
        String repeatedPassword = root.getValue("/repeatedPassword").toString().replace("\"", "");
        if(!validateEmailAddress(email)){
            exchange.sendResponseHeaders(406, 0);
            exchange.getResponseBody().close();
            return false;
        }
        if(!password.equals(repeatedPassword) || EMAIL2USER.containsKey(email)){
            exchange.sendResponseHeaders(409, 0);
            exchange.getResponseBody().close();
            return false;
        }
        String[] saltAndPasswordHash = PWH.getHash(password);
        User user = new User(email,saltAndPasswordHash[0],saltAndPasswordHash[1],false);
        EMAIL2USER.put(email, user);
        exchange.sendResponseHeaders(201, 0);
        exchange.getResponseBody().close();
        return true;
    }
    
    /**
     * Login existing user if he provided correct credentials.
     * 
     * @param root
     * @param exchange
     * @return
     * @throws IOException 
     */
    private static boolean login(String session_id, JsonStructure root, HttpExchange exchange, boolean sendHeaders) throws IOException{
        String email = root.getValue("/email").toString().replace("\"", "");
        String password = root.getValue("/password").toString().replace("\"", "");
        if(!validateEmailAddress(email) || !EMAIL2USER.containsKey(email)){
            exchange.sendResponseHeaders(404, 0);
            exchange.getResponseBody().close();
            return false;
        }
        if(!EMAIL2USER.get(email).checkPasswordHash(password)){
            exchange.sendResponseHeaders(401, 0);
            exchange.getResponseBody().close();
            return false;
        }
        ID2USER.put(session_id, EMAIL2USER.get(email));
        if(!ID2MODEL_LIST.containsKey(session_id)){
            Model m = RDFUtils.retrieveTriples(exchange,Main.class.getResourceAsStream(CONSTRUCT));
            Collection<Model> rdfCollection = new ArrayList<>();
            rdfCollection.add(m);
            ID2MODEL_LIST.put(session_id,rdfCollection);
            ID2POSITION.put(session_id, new Position(0));
        }
        if(sendHeaders){
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
        return true;
    }
    
    /**
     * Hashes value of the input.
     * 
     * @param input
     * @return 
     */
    private static String generateHash(String input){
        MD.reset();
        StringBuilder sb = new StringBuilder("");
        for(Byte b : MD.digest(input.getBytes())){
            sb.append(Integer.toHexString(b));
        }
        return "0x" + sb.toString().replace("0x", "");
    }

    /**
     * Retrieves session ID from HTTP request header.
     * 
     * @param exchange
     * @return 
     */
    private static String retrieveSessionID(HttpExchange exchange) {
        Headers h = exchange.getRequestHeaders();
        String session_id = "";
        if(h.containsKey("Cookie"))
            for(String cookie : h.get("Cookie"))
                if(!session_id.equals("") && cookie.startsWith("SESSION_ID=") && EMAIL2STATE.get(ID2USER.get(cookie.split("=")[1]).email))
                    session_id = cookie.split("=")[1];
                else if(session_id.equals("") && cookie.startsWith("SESSION_ID="))
                    session_id = cookie.split("=")[1];
        return session_id;
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
    
    /**
     * Creates a new session ID, puts it into the header and returns it as a string.
     * 
     * @param exchange
     * @return 
     */
    private static String createSession(HttpExchange exchange){
        String hash = generateHash(Long.toString(RNG.nextLong()));
        setSessionCookie(hash, exchange);
        return hash;
    }

    /**
     * Set session ID as a cookie.
     * 
     * @param session_id
     * @param exchange 
     */
    private static void setSessionCookie(String session_id, HttpExchange exchange) {
        ArrayList<String> newCookies = new ArrayList<>();
        newCookies.add("SESSION_ID=" + session_id);
        exchange.getResponseHeaders().put("Set-Cookie", newCookies);
    }
    
    /**
     * Checks whether the provided email address is valid.
     * 
     * @param email
     * @return 
     */
    private static boolean validateEmailAddress(String email){
        try {
            InternetAddress address = new InternetAddress(email);
            address.validate();
        } catch (AddressException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }
}
