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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;

/**
 * This is a convenience class for accessing RDF data structures provided by APACHE JENA.
 * 
 * @author David Fuchs
 */
public class RDFUtils {
    private static final Logger LOGGER = Logger.getLogger(RDFUtils.class.getName());
    
    public static final PrefixMapping PM = PrefixMapping.Factory.create();
    
    private static String queryForNewAnnotation;
    private static String queryForNumberOfNotAnnotatedResorces;
    private static String queryForNumberOfAnnotationsOfCurrentAnnotator;
    
    static{
        try {
            queryForNewAnnotation = IOUtils.toString(
                                        Main.class.getResourceAsStream("/sparql/resourcesToBeAnnotatedQuery.sparql"),
                                        StandardCharsets.UTF_8.name());
            queryForNumberOfNotAnnotatedResorces = IOUtils.toString(
                                    Main.class.getResourceAsStream("/sparql/numberOfResourcesNotYetAnnotated.sparql"),
                                    StandardCharsets.UTF_8.name());
            queryForNumberOfAnnotationsOfCurrentAnnotator = IOUtils.toString(
                                    Main.class.getResourceAsStream("/sparql/numberOfResourceAnnotatedByCurrentAnnotator.sparql"),
                                    StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            Logger.getLogger(RDFUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private RDFUtils(){}
    /**
     * Retrieves triples from SPARQL endpoint using query loaded from resource file.
     * 
     * @param exchange
     * @param session_id
     * @return 
     * @throws java.io.IOException 
     */
    public static Model retrieveTriples(HttpExchange exchange,String session_id) throws IOException{
        Model m = null;
        try {
            String currentAnnotator = "mailto:" + Main.ID2USER.get(session_id).email;
            do{
                ParameterizedSparqlString pss;
                ParameterizedSparqlString p;
                int offset;
                pss = new ParameterizedSparqlString(queryForNewAnnotation);
                p = new ParameterizedSparqlString(queryForNumberOfNotAnnotatedResorces);
                LOGGER.log(Level.INFO, "{0}: Finding the number of resources not yet annotated.",
                           new Timestamp(System.currentTimeMillis()));
                QueryExecution qe=QueryExecutionFactory.sparqlService(SPARQLendpoint, p.asQuery());
                qe.setTimeout(1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS);
                ResultSet rs = qe.execSelect();
                int numberOfResourcesForAnnotation;
                try{
                    numberOfResourcesForAnnotation = rs.next().getLiteral("count").getInt();
                    qe.close();
                    if(numberOfResourcesForAnnotation <= 0) continue;
                }
                catch(Exception ex){
                    qe.close();
                    continue;
                }
                offset = Main.RNG.nextInt(numberOfResourcesForAnnotation);
                pss.setLiteral("offset", offset);
                pss.setLiteral("limit", 1);
                pss.setIri("current_annotator", new URL(currentAnnotator));
                LOGGER.log(Level.INFO, "{0}: Querying SPARQL endpoint for user: " + Main.ID2USER.get(session_id).email,
                           new Timestamp(System.currentTimeMillis()));
                qe=QueryExecutionFactory.sparqlService(SPARQLendpoint,pss.asQuery());
                qe.setTimeout(1, TimeUnit.SECONDS, 2, TimeUnit.SECONDS);
                m = qe.execConstruct();
                m.setNsPrefixes(PM);
                qe.close();
            }while(m==null || m.isEmpty());
        }
        catch (IOException ex) {
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
    
    public static int getNumberOfAnnotations(HttpExchange exchange, String session_id,String email){
        try {
            String currentAnnotator = "mailto:" + Main.ID2USER.get(session_id).email;
            ParameterizedSparqlString pss = new ParameterizedSparqlString(queryForNumberOfAnnotationsOfCurrentAnnotator);
            pss.setIri("current_annotator", new URL(currentAnnotator));
            QueryExecution qe=QueryExecutionFactory.sparqlService(SPARQLendpoint, pss.asQuery());
            ResultSet rs = qe.execSelect();
            int numberOfAnnotations = rs.next().getLiteral("count").getInt();
            qe.close();
            return numberOfAnnotations;
        } catch (MalformedURLException ex) {
            Logger.getLogger(RDFUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
        return -1;
    }
}
