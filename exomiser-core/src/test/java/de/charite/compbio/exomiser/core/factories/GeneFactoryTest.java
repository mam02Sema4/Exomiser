/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.charite.compbio.exomiser.core.factories;

import de.charite.compbio.exomiser.core.model.Gene;
import de.charite.compbio.exomiser.core.model.VariantEvaluation;
import jannovar.exome.Variant;
import java.util.ArrayList;
import java.util.List;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

/**
 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
@RunWith(MockitoJUnitRunner.class)
public class GeneFactoryTest {

    private GeneFactory instance;
    private List<VariantEvaluation> variantEvaluations;

    private static final String GENE1_GENE_SYMBOL = "GENE1";
    @Mock
    private VariantEvaluation offTargetVariantEvaluation;
    @Mock
    private VariantEvaluation firstGene1VariantEvaluation;
    @Mock
    private VariantEvaluation secondGene1VariantEvaluation;

    private static final String GENE2_GENE_SYMBOL = "GENE2";
    @Mock
    private VariantEvaluation gene2VariantEvaluation;
    
    private static final String GENE3_GENE_SYMBOL = "GENE3";
    @Mock
    private VariantEvaluation gene3VariantEvaluation;    
    @Mock
    private VariantEvaluation variantEvaluationInTwoGeneRegions;


    @Before
    public void setUp() {
        instance = new GeneFactory();
        variantEvaluations = new ArrayList<>();

        Mockito.when(offTargetVariantEvaluation.getGeneSymbol()).thenReturn(null);

        Mockito.when(firstGene1VariantEvaluation.getGeneSymbol()).thenReturn(GENE1_GENE_SYMBOL);
        Mockito.when(firstGene1VariantEvaluation.getEntrezGeneID()).thenReturn(123456);

        Mockito.when(secondGene1VariantEvaluation.getGeneSymbol()).thenReturn(GENE1_GENE_SYMBOL);
        Mockito.when(secondGene1VariantEvaluation.getEntrezGeneID()).thenReturn(123456);

        Mockito.when(gene2VariantEvaluation.getGeneSymbol()).thenReturn(GENE2_GENE_SYMBOL);
        Mockito.when(gene2VariantEvaluation.getEntrezGeneID()).thenReturn(654321);
        
        //the variantEvaluation should only return the first gene symbol listed by Jannovar - see VariantEvaluationTest 
        Mockito.when(variantEvaluationInTwoGeneRegions.getGeneSymbol()).thenReturn(GENE3_GENE_SYMBOL);
        Mockito.when(variantEvaluationInTwoGeneRegions.getEntrezGeneID()).thenReturn(9999999);
        
        Mockito.when(gene3VariantEvaluation.getGeneSymbol()).thenReturn(GENE3_GENE_SYMBOL);
        Mockito.when(gene3VariantEvaluation.getEntrezGeneID()).thenReturn(9999999);
    }

    @Test
    public void testGeneFactoryWillReturnAnEmptyResultFromAnEmptyInput() {
        List<VariantEvaluation> emptyVariantEvaluations = new ArrayList<>();
        List<Gene> emptyGenes = new ArrayList<>();

        List<Gene> result = instance.createGenes(emptyVariantEvaluations);
        assertThat(result, equalTo(emptyGenes));
    }

    @Test
    public void testOffTargetVariantReturnsNoGenes() {
        variantEvaluations.add(offTargetVariantEvaluation);

        List<Gene> emptyGenes = new ArrayList<>();

        List<Gene> result = instance.createGenes(variantEvaluations);
        assertThat(result, equalTo(emptyGenes));
    }

    @Test
    public void testOneOnTargetVariantReturnsSingleGene() {

        variantEvaluations.add(firstGene1VariantEvaluation);

        List<Gene> genes = new ArrayList<>();
        genes.add(new Gene(firstGene1VariantEvaluation));

        List<Gene> result = instance.createGenes(variantEvaluations);
        assertThat(result, equalTo(genes));
    }

    @Test
    public void testTwoVariantsInSameGeneReturnsSingleGene() {

        variantEvaluations.add(firstGene1VariantEvaluation);
        variantEvaluations.add(secondGene1VariantEvaluation);

        List<Gene> genes = new ArrayList<>();
        genes.add(new Gene(firstGene1VariantEvaluation));

        List<Gene> result = instance.createGenes(variantEvaluations);
        assertThat(result, equalTo(genes));

        Gene resultGene = result.get(0);
        assertThat(resultGene.getGeneSymbol(), equalTo(GENE1_GENE_SYMBOL));
        assertThat(resultGene.getNumberOfVariants(), equalTo(variantEvaluations.size()));
    }

    @Test
    public void testTwoVariantsInDifferentGeneReturnsTwoGenes() {

        Gene gene1 = new Gene(firstGene1VariantEvaluation);
        variantEvaluations.add(firstGene1VariantEvaluation);

        Gene gene2 = new Gene(gene2VariantEvaluation);
        variantEvaluations.add(gene2VariantEvaluation);

        List<Gene> result = instance.createGenes(variantEvaluations);

        assertThat(result.contains(gene1), is(true));
        assertThat(result.contains(gene2), is(true));
    }

    @Test
    public void testVariantInTwoGeneRegionsReturnsSingleGene() {

        variantEvaluations.add(variantEvaluationInTwoGeneRegions);

        List<Gene> genes = new ArrayList<>();
        genes.add(new Gene(variantEvaluationInTwoGeneRegions));

        List<Gene> result = instance.createGenes(variantEvaluations);
        assertThat(result, equalTo(genes));

    }
    
    @Test
    public void testVariantInTwoGeneRegionsReturnsSingleGeneWithFirstGeneSymbol() {

        variantEvaluations.add(variantEvaluationInTwoGeneRegions);

        List<Gene> result = instance.createGenes(variantEvaluations);
        
        Gene resultGene = result.get(0);
        System.out.println(resultGene);
        assertThat(resultGene.getGeneSymbol(), equalTo(GENE3_GENE_SYMBOL));
        assertThat(resultGene.getNumberOfVariants(), equalTo(variantEvaluations.size()));

    }
    
    @Test
    public void testThreeVariantsInTwoGeneRegionsReturnsTwoGenes() {
        
        variantEvaluations.add(variantEvaluationInTwoGeneRegions);

        Gene gene3 = new Gene(gene3VariantEvaluation);
        variantEvaluations.add(gene3VariantEvaluation);

        Gene gene2 = new Gene(gene2VariantEvaluation);
        variantEvaluations.add(gene2VariantEvaluation);        
        

        List<Gene> result = instance.createGenes(variantEvaluations);
        for (Gene gene : result) {
            System.out.println(gene);

        }
        assertThat(result.size(), equalTo(2));
        assertThat(result.contains(gene2), is(true));
        assertThat(result.contains(gene3), is(true));
    }
}