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

import java.util.Base64;
import java.util.Base64.Encoder;
import javax.json.Json;
import javax.json.JsonObject;

/**
 * Class representing users of the application.
 *
 * @author David Fuchs
 */
public class User {
    private static final Encoder B64E = Base64.getEncoder();
    public final String email;
    
    private final String salt;
    private final String passwordHash;
    
    /**
     * Creates instance of a new user.
     * 
     * @param email
     * @param salt
     * @param passwordHash 
     * @param b64Hash 
     */
    public User(String email, String salt, String passwordHash, boolean b64Hash){
        this.email = email;
        this.salt = salt;
        this.passwordHash = (b64Hash ? passwordHash : B64E.encodeToString(passwordHash.getBytes()));
    }
    
    /**
     * Check if password provided by the user is correct.
     * 
     * @param password
     * @return 
     */
    public boolean checkPasswordHash(String password){
        return this.passwordHash.equals(B64E.encodeToString(Main.PWH.getHash(salt, password).getBytes()));
    }
    
    /**
     * Serialize user information to JSON.
     * 
     * @return 
     */
    public JsonObject serialize(){
        return Json.createObjectBuilder()
                .add("email", email)
                .add("salt", salt)
                .add("passwordHash", passwordHash).build();
    }
}
