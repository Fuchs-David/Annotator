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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 *
 * @author David Fuchs
 */
public class PasswordHasher {
    private final SecureRandom RNG;
    private final MessageDigest MD;
    
    /**
     * Instances of this class are used to hash passwords.
     * @throws java.security.NoSuchAlgorithmException
     */
    public PasswordHasher() throws NoSuchAlgorithmException{
        this("SHA-256");
    }
    /**
     * Instances of this class are used to hash passwords.
     * 
     * @param hashingAlgorithm
     * @throws java.security.NoSuchAlgorithmException
     */
    public PasswordHasher(String hashingAlgorithm) throws NoSuchAlgorithmException{
        RNG = new SecureRandom();
        MD = MessageDigest.getInstance(hashingAlgorithm);
    }
    
    /**
     * Get hash of random salt and password.
     * 
     * @param password
     * @return 
     */
    public String[] getHash(String password){
        String[] saltAndHash = new String[2];
        String salt = "";
        saltAndHash[0] = salt += RNG.nextInt();
        saltAndHash[1] = new String(MD.digest((salt + password).getBytes()));
        return saltAndHash;
    }
    
    /**
     * Get hash of a given salt and password.(needed for password validation against
     * database of users)
     * 
     * @param salt
     * @param password
     * @return 
     */
    public String getHash(String salt, String password){
        return new String(MD.digest((salt + password).getBytes()));
    }
}
