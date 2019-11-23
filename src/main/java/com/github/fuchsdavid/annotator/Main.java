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
package com.github.fuchsdavid.annotator;

import com.github.fuchsdavid.annotator.util.HTTPServerUtils;
import com.github.fuchsdavid.annotator.util.RDFUtils;
import com.sun.net.httpserver.HttpServer;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonValue.ValueType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerFactory;
import org.apache.jena.query.ARQ;
import org.apache.jena.rdf.model.Model;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Main class of the Annotator project, which aims at enabling adding
 * annotations to RDF resources.
 * 
 * @author David Fuchs
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    
    public static final String INDEX = "/www/index.xhtml";
    public static final String AUTH = "/www/auth.xhtml";
    public static final String PASSWD = "./security/auth.json";
    public static final String CONSTRUCT = "/sparql/construct.sparql";
    public static final String PREFIXES = "/config/known_prefixes.conf";
    public static final Random RNG = new Random();
    
    public static final Map<String,Collection<Model>> ID2MODEL_LIST = new HashMap<>();
    public static final Map<String,Position> ID2POSITION = new HashMap<>();
    public static final Map<String,Boolean> EMAIL2STATE = new HashMap<>();
    public static final Map<String,User> ID2USER = new HashMap<>();
    public static final Map<String,User> EMAIL2USER = new HashMap<>();
    public static final Map<String,Document> CACHED_FILES = new HashMap<>();
    
    public static final TransformerFactory TF = TransformerFactory.newInstance();
    public static final JsonBuilderFactory JF = Json.createBuilderFactory(null);
    
    public static String SPARQLendpoint;
    public static int port = 8080;
    public static PasswordHasher PWH;
    
    public static DocumentBuilder DOCUMENT_BUILDER;
    public static MessageDigest MD;
    public static int offset = 0;
    
    private static HttpServer server;
    
    static {
        ARQ.init();
        try{
            PWH = new PasswordHasher();
            JsonStructure root = Json.createReader(new FileInputStream(PASSWD)).read();
            JsonArray users = null;
            if(root.getValue("/users").getValueType().equals(ValueType.ARRAY))
                users = root.getValue("/users").asJsonArray();
            else 
                System.exit(1);
            for(int i=0; i<users.size(); i++){
                String email = users.get(i).asJsonObject().getValue("/email").toString().replace("\"", "");
                String salt = users.get(i).asJsonObject().getValue("/salt").toString().replace("\"", "");
                String passwordHash = users.get(i).asJsonObject().getValue("/passwordHash").toString().replace("\"", "");
                User u = new User(email, salt, passwordHash,true);
                EMAIL2USER.put(email, u);
            }
            LOGGER.log(Level.INFO, "{0}: Loaded user credentials from storage.", new Timestamp(System.currentTimeMillis()));
            DOCUMENT_BUILDER = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            SPARQLendpoint = (new URL("http://localhost:3030/fuseki")).toExternalForm();
            CACHED_FILES.put(INDEX,DOCUMENT_BUILDER.parse(Main.class.getResourceAsStream(INDEX)));
            CACHED_FILES.put(AUTH,DOCUMENT_BUILDER.parse(Main.class.getResourceAsStream(AUTH)));
            MD = MessageDigest.getInstance("SHA1");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                final JsonArrayBuilder u = Json.createArrayBuilder();
                EMAIL2USER.values().forEach((User user) -> {
                    u.add(user.serialize());
                });
                JsonObject r = Json.createObjectBuilder().add("users", u.build()).build();
                try {
                    Json.createWriter(new FileOutputStream(PASSWD)).write(r);
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }));
            Scanner scanner = new Scanner(Main.class.getResourceAsStream(PREFIXES));
            while(scanner.hasNextLine()){
                String[] line = scanner.nextLine().split("\\t");
                if(line.length != 2)
                    throw new Exception("Failed to parse prefix list");
                RDFUtils.PM.setNsPrefix(line[0], new URL(line[1]).toExternalForm());
            }
        }
        catch(MalformedURLException | NoSuchAlgorithmException | ParserConfigurationException | SAXException ex){
            LOGGER.log(Level.SEVERE, "{0}: " + ex.getMessage(), new Timestamp(System.currentTimeMillis()));
            System.exit(1);
        }
        catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "{0}: " + ex.getMessage(), new Timestamp(System.currentTimeMillis()));
            System.exit(1);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "{0}: " + ex.getMessage(), new Timestamp(System.currentTimeMillis()));
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
        if((server = HTTPServerUtils.runHTTPServer()) == null) System.exit(1);
        LOGGER.log(Level.INFO, "{0}: Started HTTP server on port " + port + ".", new Timestamp(System.currentTimeMillis()));
    }
}
