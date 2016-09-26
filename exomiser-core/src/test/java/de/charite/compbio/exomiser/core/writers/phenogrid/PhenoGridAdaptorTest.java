/*
 * The Exomiser - A tool to annotate and prioritize variants
 *
 * Copyright (C) 2012 - 2016  Charite Universitätsmedizin Berlin and Genome Research Ltd.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.charite.compbio.exomiser.core.writers.phenogrid;

import de.charite.compbio.exomiser.core.model.*;
import de.charite.compbio.exomiser.core.prioritisers.HiPhivePriorityResult;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

/**
 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
public class PhenoGridAdaptorTest {

    private PhenoGridAdaptor instance;

    private List<HiPhivePriorityResult> hiPhiveResults;
    private final String phenoGridId = "hiPhive specified phenotypes";

    private DiseaseModel diseaseModel;
    private PhenotypeMatch diseasePhenotypeMatch;
    private PhenoGridMatchScore diseaseScore;
    private final PhenoGridMatchTaxon diseaseTaxon = PhenoGridAdaptor.HUMAN_TAXON;
    private PhenoGridMatch diseasePhenoGridMatch;
            
    private GeneModel mouseModel;
    private PhenotypeMatch mousePhenotypeMatch;
    private PhenoGridMatchScore mouseScore;
    private final PhenoGridMatchTaxon mouseTaxon = PhenoGridAdaptor.MOUSE_TAXON;
    private PhenoGridMatch mousePhenoGridMatch;
    
    private GeneModel fishModel;
    private PhenotypeMatch fishPhenotypeMatch;
    private PhenoGridMatchScore fishScore;
    private final PhenoGridMatchTaxon fishTaxon = PhenoGridAdaptor.FISH_TAXON;
    private PhenoGridMatch fishPhenoGridMatch;

    private PhenotypeTerm queryTerm;
    private List<PhenotypeTerm> queryPhenotypeTerms;
    private Set<String> queryPhenotypeIds;

    private final int allModelEntrezGeneId = 12345;
    private final String allModelGeneSymbol = "ALL1";
    private final double allModelScore = 0.8d;
    private final double allModelWalkerScore = 0.6d;
    private HiPhivePriorityResult allModelsHiPhiveResult;

    private final int onlyDiseaseEntrezGeneId = 23456;
    private final String onlyDiseaseGeneSymbol = "DISEASE1";
    private final double onlyDiseaseModelScore = 0.7d;
    private final double onlyDiseaseModelWalkerScore = 0.5d;
    private HiPhivePriorityResult onlyDiseaseModelHiPhiveResult;


    @Before
    public void setUp() {
        setUpQueryTerms();
        setUpPhenotypeMatches();
        setUpModels();
        setUpHiPhiveResults();
        instance = new PhenoGridAdaptor();
    }

    private void setUpQueryTerms() {
        queryTerm = PhenotypeTerm.of("HP:0001363", "Craniosynostosis");
        queryPhenotypeTerms = Arrays.asList(queryTerm);

        queryPhenotypeIds = new LinkedHashSet<>();
        queryPhenotypeIds.add(queryTerm.getId());

    }

    private void setUpPhenotypeMatches() {
        diseasePhenotypeMatch = makeDiseasePhenotypeMatch();
        mousePhenotypeMatch = makeMousePhenotypeMatch();
        fishPhenotypeMatch = makeFishPhenotypeMatch();
    }

    private PhenotypeMatch makeDiseasePhenotypeMatch() {
        PhenotypeTerm craniosynostosis = PhenotypeTerm.of("HP:0001363", "Craniosynostosis");
        return PhenotypeMatch.builder()
                .query(craniosynostosis)
                .match(craniosynostosis)
                .lcs(craniosynostosis)
                .ic(5.0)
                .simj(1.0)
                .score(3.0)
                .build();
    }

    private PhenotypeMatch makeMousePhenotypeMatch() {
        PhenotypeTerm craniosynostosis = PhenotypeTerm.of("HP:0001363", "Craniosynostosis");
        PhenotypeTerm prematureSutureClosure = PhenotypeTerm.of("MP:0000081", "premature suture closure ");
        return PhenotypeMatch.builder()
                .query(craniosynostosis)
                .match(prematureSutureClosure)
                .lcs(prematureSutureClosure)
                .ic(4.0)
                .simj(0.5)
                .score(2.0)
                .build();
    }

    private PhenotypeMatch makeFishPhenotypeMatch() {
        PhenotypeTerm craniosynostosis = PhenotypeTerm.of("HP:0001363", "Craniosynostosis");
        PhenotypeTerm increasedBoneMineralisation = PhenotypeTerm.of("ZP:0006781", "abnormal(ly) increased process quality bone mineralization");
        PhenotypeTerm abnormalBoneOssification = PhenotypeTerm.of("MP:0008271", "abnormal bone ossification");
        return PhenotypeMatch.builder()
                .query(craniosynostosis)
                .match(increasedBoneMineralisation)
                .lcs(abnormalBoneOssification)
                .ic(4.6)
                .simj(0.2)
                .score(1.0)
                .build();
    }

    private void setUpModels() {
        diseaseModel = new DiseaseModel("OMIM:00000", Organism.HUMAN, allModelEntrezGeneId, allModelGeneSymbol, "OMIM:00000", "Rare disease", new ArrayList());
        diseaseModel.addMatchIfAbsentOrBetterThanCurrent(diseasePhenotypeMatch);
        diseaseModel.setScore(0.99);
        diseaseScore = new PhenoGridMatchScore("hiPhive", 99, 0);

        mouseModel = new GeneModel("1_12345", Organism.MOUSE, allModelEntrezGeneId, allModelGeneSymbol, "MGI:00000", "All1", new ArrayList());
        mouseModel.addMatchIfAbsentOrBetterThanCurrent(mousePhenotypeMatch);
        mouseModel.setScore(0.88);
        mouseScore = new PhenoGridMatchScore("hiPhive", 88, 0);

        fishModel = new GeneModel("2_12345", Organism.FISH, allModelEntrezGeneId, allModelGeneSymbol, "ZDB-GENE-000000-0", "all1", new ArrayList());
        fishModel.addMatchIfAbsentOrBetterThanCurrent(fishPhenotypeMatch);
        fishModel.setScore(0.50);
        fishScore = new PhenoGridMatchScore("hiPhive", 50, 0);
    }

    private void setUpHiPhiveResults() {
        List<Model> allModels = new ArrayList<>();
        allModels.add(diseaseModel);
        allModels.add(mouseModel);
        allModels.add(fishModel);

        allModelsHiPhiveResult = new HiPhivePriorityResult(allModelEntrezGeneId, allModelGeneSymbol, allModelScore, queryPhenotypeTerms, allModels, new ArrayList<Model>(), allModelWalkerScore, false);
    
        List<Model> models = new ArrayList<>();
        models.add(diseaseModel);

        onlyDiseaseModelHiPhiveResult = new HiPhivePriorityResult(onlyDiseaseEntrezGeneId, onlyDiseaseGeneSymbol, onlyDiseaseModelScore, queryPhenotypeTerms, models, new ArrayList<Model>(), onlyDiseaseModelWalkerScore, false);
    
    }

    @Test
    public void testPhenoGridFromEmptyHiPhiveResults() {
        hiPhiveResults = Collections.emptyList();
        PhenoGrid output = instance.makePhenoGridFromHiPhiveResults(phenoGridId, hiPhiveResults);
        assertThat(output, notNullValue());
        PhenoGridQueryTerms queryTerms = output.getPhenoGridQueryTerms();
        assertThat(queryTerms, notNullValue());
        assertThat(queryTerms.getId(), equalTo(phenoGridId));
        assertThat(queryTerms.getPhenotypeIds().isEmpty(), is(true));
        assertThat(output.getPhenoGridMatchGroups().isEmpty(), is(true));
    }
    
    @Test
    public void testDiseaseOnlyPhenoGridMatch() {
        hiPhiveResults = new ArrayList<>();
        hiPhiveResults.add(onlyDiseaseModelHiPhiveResult);
        PhenoGrid output = instance.makePhenoGridFromHiPhiveResults(phenoGridId, hiPhiveResults);
        testOutputHeader(output);
        
        List<PhenoGridMatchGroup> gridMatchGroups = output.getPhenoGridMatchGroups();
        testHasExpectedNumberOfOrganismsUsedForPhenotypeComparison(1, gridMatchGroups);
        testExpectedTaxonomicOrderOfPhenoGridMatchGroups(gridMatchGroups, diseaseTaxon);
        testMatchesAreInDescendingScoreOrder(gridMatchGroups, diseaseScore);
        testPhenoGridMatchGroupCommonValues(gridMatchGroups);        

    }

    @Test
    public void testPhenoGridWithHiPhiveResultContainingAllModelMatchesOneMatchGroupPerOrganism() {
        hiPhiveResults = new ArrayList<>();
        hiPhiveResults.add(allModelsHiPhiveResult);
        PhenoGrid output = instance.makePhenoGridFromHiPhiveResults(phenoGridId, hiPhiveResults);
        testOutputHeader(output);

        List<PhenoGridMatchGroup> gridMatchGroups = output.getPhenoGridMatchGroups();
        testHasExpectedNumberOfOrganismsUsedForPhenotypeComparison(3, gridMatchGroups);
        testExpectedTaxonomicOrderOfPhenoGridMatchGroups(gridMatchGroups, diseaseTaxon, mouseTaxon, fishTaxon);
        testMatchesAreInDescendingScoreOrder(gridMatchGroups, diseaseScore, mouseScore, fishScore);
        testPhenoGridMatchGroupCommonValues(gridMatchGroups);        
    }

    private void testHasExpectedNumberOfOrganismsUsedForPhenotypeComparison(int numberOfOrganismsUsedForPhenotypeComparison, List<PhenoGridMatchGroup> gridMatchGroups) {
        assertThat(gridMatchGroups.size(), equalTo(numberOfOrganismsUsedForPhenotypeComparison));
    }

    private void testOutputHeader(PhenoGrid output) {
        assertThat(output, notNullValue());
        PhenoGridQueryTerms queryTerms = output.getPhenoGridQueryTerms();
        assertThat(queryTerms, notNullValue());
        assertThat(queryTerms.getId(), equalTo(phenoGridId));
        assertThat(queryTerms.getPhenotypeIds(), equalTo(queryPhenotypeIds));
    }

    private void testPhenoGridMatchGroupCommonValues(List<PhenoGridMatchGroup> gridMatchGroups) {
        for (PhenoGridMatchGroup gridMatchGroup : gridMatchGroups) {
            assertThat(gridMatchGroup.getQueryPhenotypeTermIds(), equalTo(queryPhenotypeIds));
            assertThat(gridMatchGroup.getCutOff(), equalTo(10));
            List<PhenoGridMatch> matches = gridMatchGroup.getMatches();
            assertThat(matches.size(), equalTo(1));
        }
    }

    private void testMatchesAreInDescendingScoreOrder(List<PhenoGridMatchGroup> gridMatchGroups, PhenoGridMatchScore... matchScores) {
        List<PhenoGridMatchScore> expectedScores = Arrays.asList(matchScores);

        List<PhenoGridMatchScore> phenogridScores = new ArrayList<>();
        for (PhenoGridMatchGroup gridMatchGroup : gridMatchGroups) {
            for (PhenoGridMatch match : gridMatchGroup.getMatches()) {
                phenogridScores.add(match.getScore());
            }
        }
        assertThat(phenogridScores, equalTo(expectedScores));
    }

    private void testExpectedTaxonomicOrderOfPhenoGridMatchGroups(List<PhenoGridMatchGroup> gridMatchGroups, PhenoGridMatchTaxon... taxons) {
        Set<PhenoGridMatchTaxon> expectedTaxonomicOrderOfMatchGroups = new LinkedHashSet<>(Arrays.asList(taxons));
        Set<PhenoGridMatchTaxon> actualTaxonomicOrderOfMatchGroups = new LinkedHashSet<>();

        for (PhenoGridMatchGroup phenoGridMatchGroup : gridMatchGroups) {
            for (PhenoGridMatch match : phenoGridMatchGroup.getMatches()) {
                actualTaxonomicOrderOfMatchGroups.add(match.getTaxon());
            }
        }
        assertThat(actualTaxonomicOrderOfMatchGroups, equalTo(expectedTaxonomicOrderOfMatchGroups));
    }

}
