/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.charite.compbio.exomiser.core.filter;

/**
 *
 * @author jj8
 */
public class InheritanceFilterScore implements FilterScore {

    private final float score;
    
    public InheritanceFilterScore(float score) {
        this.score = score;
    }

    @Override
    public float getScore() {
        return score;
    }

    @Override
    public String toString() {
        return "InheritanceFilterScore{" + "score=" + score + '}';
    }
}
