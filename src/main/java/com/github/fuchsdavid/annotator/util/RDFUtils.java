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
import static com.github.fuchsdavid.annotator.Main.SPARQLendpoint;
import com.sun.net.httpserver.HttpExchange;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;

/**
 * This is a convenience class for accessing RDF data structures provided by APACHE JENA.
 * 
 * @author David Fuchs
 */
public class RDFUtils {
    private static final int NUMBER_OF_LINKS_DBPEDIA2WD = 2669;
    private static final Logger LOGGER = Logger.getLogger(RDFUtils.class.getName());
    public static final PrefixMapping PM = PrefixMapping.Factory.create();
    
    private RDFUtils(){}
    /**
     * Retrieves triples from SPARQL endpoint using query loaded from resource file.
     * 
     * @param exchange
     * @param file
     * @param session_id
     * @return 
     * @throws java.io.IOException 
     */
    public static Model retrieveTriples(HttpExchange exchange,InputStream file,String session_id) throws IOException{
        Model m = null;
        try {
            String constructQuery = IOUtils.toString(file, StandardCharsets.UTF_8.name());
            String currentAnnotator = "mailto:" + Main.ID2USER.get(session_id).email;
            do{
                ParameterizedSparqlString pss = new ParameterizedSparqlString(constructQuery);
                int offset;
                do{
                    offset = Main.RNG.nextInt(NUMBER_OF_LINKS_DBPEDIA2WD);
                }
                while(Main.OFFSETS.contains(offset));
                Main.OFFSETS.add(offset);
                pss.setLiteral("offset", offset);
                pss.setLiteral("limit", 1);
                pss.setIri("current_annotator", new URL(currentAnnotator));
                Query query = pss.asQuery();
                LOGGER.log(Level.INFO, "{0}: Querying SPARQL endpoint for user: " + Main.ID2USER.get(session_id).email,
                           new Timestamp(System.currentTimeMillis()));
                m = QueryExecutionFactory.sparqlService(SPARQLendpoint,query).execConstruct();
                m.setNsPrefixes(PM);
            }while(m.isEmpty());
        } catch (FileNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "{0}: " + ex.getMessage(), new Timestamp(System.currentTimeMillis()));
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "{0}: " + ex.getMessage(), new Timestamp(System.currentTimeMillis()));
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        }
        return m;
    }
    
    /**
     * Get prefixed name of a URI resource.
     * 
     * @param resource
     * @return 
     */
    public static String getPrefixedName(Resource resource){
        String prefix = PM.getNsURIPrefix(resource.getNameSpace());
        return (prefix == null ? resource.getURI() : prefix + ":" + resource.getLocalName());
    }
}
