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

/**
 * Helper class to overcome the limitations of java class @code{Map}.
 * @author David Fuchs
 */
public class Position {
    private int position = 0;
    
    /**
     * Create new position;
     * @param position 
     */
    public Position(int position){
        if(position < 0)
            throw new IllegalArgumentException("Position must be a positive integer.");
        this.position = position;
    }
    
    /**
     * Preincrement operation with overflow check
     * @return 
     */
    public int preincrement(){
        return (position == Integer.MAX_VALUE ? position : ++position);
    }
    
    /**
     * Predecrement operation with lowerbound check
     * @return 
     */
    public int predecrement(){
        return (position == 0 ? position : --position);
    }
    
    /**
     * Postincrement operation with overflow check
     * @return 
     */
    public int postincrement(){
        return (position == Integer.MAX_VALUE ? position : position++);
    }
    
    /**
     * Postdecrement operation with lowerbound check
     * @return 
     */
    public int postdecrement(){
        return (position == 0 ? position : position--);
    }
    
    /**
     * Simple getter
     * @return 
     */
    public int getPosition(){
        return position;
    }
}
