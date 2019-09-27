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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Auxiliary class for constants representing command line arguments
 * and a set of methods for parsing them.
 * @author David Fuchs
 */
public class Argument {
    private static final String SPARQL_ENDPOINT = "-SPARQLendpoint";
    private static final String PORT = "-port";
    
    
    /**
     * Auxiliary method for parsing command line arguments.
     * @param args 
     */
    public static void parseArguments(String[] args){
        if(args.length % 2 == 1){
            System.exit(1);
        }
        try{
            for(int i=0;i<args.length;i++){
                switch(args[i]){
                    case SPARQL_ENDPOINT: Main.SPARQLendpoint = (new URL(args[++i])).toExternalForm();
                                          break;
                    case PORT: Main.port = Integer.parseInt(args[++i]);
                                           break;
                    default: throw new IllegalArgumentException("Unknown command line parameter.");
                }
            }
        } catch (MalformedURLException | IllegalArgumentException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }
    
    private Argument(){}
}
