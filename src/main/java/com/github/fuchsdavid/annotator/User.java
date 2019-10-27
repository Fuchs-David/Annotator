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

import javax.json.Json;
import javax.json.JsonObject;

/**
 * Class representing users of the application.
 *
 * @author David Fuchs
 */
public class User {
    public final String email;
    
    private final PasswordHasher PWH;
    private final String salt;
    private final String passwordHash;
    
    /**
     * Creates instance of a new user.
     * 
     * @param PWH
     * @param email
     * @param salt
     * @param passwordHash 
     */
    public User(PasswordHasher PWH, String email, String salt, String passwordHash){
        this.PWH = PWH;
        this.email = email;
        this.salt = salt;
        this.passwordHash = passwordHash;
    }
    
    /**
     * Check if password provided by the user is correct.
     * 
     * @param password
     * @return 
     */
    public boolean checkPasswordHash(String password){
        return this.passwordHash.equals(PWH.getHash(salt, password));
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