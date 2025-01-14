/*
 * The Exomiser - A tool to annotate and prioritize genomic variants
 *
 * Copyright (c) 2016-2018 Queen Mary University of London.
 * Copyright (c) 2012-2016 Charité Universitätsmedizin Berlin and Genome Research Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.monarchinitiative.exomiser.core.writers;

import com.google.common.collect.ImmutableList;
import de.charite.compbio.jannovar.annotation.VariantEffect;
import de.charite.compbio.jannovar.mendel.ModeOfInheritance;
import de.charite.compbio.jannovar.pedigree.Genotype;
import htsjdk.variant.vcf.VCFFileReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monarchinitiative.exomiser.core.analysis.Analysis;
import org.monarchinitiative.exomiser.core.analysis.AnalysisResults;
import org.monarchinitiative.exomiser.core.analysis.util.InheritanceModeOptions;
import org.monarchinitiative.exomiser.core.filters.FilterResult;
import org.monarchinitiative.exomiser.core.filters.FilterType;
import org.monarchinitiative.exomiser.core.genome.TestFactory;
import org.monarchinitiative.exomiser.core.genome.TestVariantFactory;
import org.monarchinitiative.exomiser.core.genome.VariantFactory;
import org.monarchinitiative.exomiser.core.model.Gene;
import org.monarchinitiative.exomiser.core.model.GeneIdentifier;
import org.monarchinitiative.exomiser.core.model.GeneScore;
import org.monarchinitiative.exomiser.core.model.VariantEvaluation;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PathogenicityData;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PolyPhenScore;
import org.monarchinitiative.exomiser.core.prioritisers.OmimPriorityResult;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for the {@link VcfResultsWriter} class.
 * 
 * The {@link VcfResultsWriter} class needs a {@link VCFFileReader} for building its header. Thus, we base our output
 * {@link VcfResultsWriter} on the minimal.vcf file from the test resources.
 * 
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 * @author Manuel Holtgrewe <manuel.holtgrewe@charite.de>
 */
public class VcfResultsWriterTest {

    private final static String EXPECTED_HEADER = "##fileformat=VCFv4.2\n"
            + "##FILTER=<ID=bed,Description=\"Gene panel target region (Bed)\">\n"
            + "##FILTER=<ID=filter,Description=\"Failed previous VCF filters\">\n"
            + "##FILTER=<ID=freq,Description=\"Frequency\">\n"
            + "##FILTER=<ID=gene-id,Description=\"Gene id\">\n"
            + "##FILTER=<ID=gene-priority,Description=\"Gene priority score\">\n"
            + "##FILTER=<ID=inheritance,Description=\"Inheritance\">\n"
            + "##FILTER=<ID=interval,Description=\"Interval\">\n"
            + "##FILTER=<ID=known-var,Description=\"Known variant\">\n"
            + "##FILTER=<ID=path,Description=\"Pathogenicity\">\n"
            + "##FILTER=<ID=quality,Description=\"Quality\">\n"
            + "##FILTER=<ID=reg-feat,Description=\"Regulatory feature\">\n"
            + "##FILTER=<ID=var-effect,Description=\"Variant effect\">\n"
            + "##INFO=<ID=ANN,Number=1,Type=String,Description=\"Functional annotations:'Allele|Annotation|Annotation_Impact|Gene_Name|Gene_ID|Feature_Type|Feature_ID|Transcript_BioType|Rank|HGVS.c|HGVS.p|cDNA.pos / cDNA.length|CDS.pos / CDS.length|AA.pos / AA.length|Distance|ERRORS / WARNINGS / INFO'\">\n"
            + "##INFO=<ID=ExContribAltAllele,Number=A,Type=Flag,Description=\"Exomiser alt allele id contributing to score\">\n"
            + "##INFO=<ID=ExGeneSCombi,Number=A,Type=Float,Description=\"Exomiser gene combined score\">\n"
            + "##INFO=<ID=ExGeneSPheno,Number=A,Type=Float,Description=\"Exomiser gene phenotype score\">\n"
            + "##INFO=<ID=ExGeneSVar,Number=A,Type=Float,Description=\"Exomiser gene variant score\">\n"
            + "##INFO=<ID=ExGeneSymbId,Number=A,Type=String,Description=\"Exomiser gene id\">\n"
            + "##INFO=<ID=ExGeneSymbol,Number=A,Type=String,Description=\"Exomiser gene symbol\">\n"
            + "##INFO=<ID=ExVarEff,Number=A,Type=String,Description=\"Exomiser variant effect\">\n"
            + "##INFO=<ID=ExVarHgvs,Number=A,Type=String,Description=\"Exomiser variant hgvs\">\n"
            + "##INFO=<ID=ExVarScore,Number=A,Type=Float,Description=\"Exomiser variant score\">\n"
            + "##INFO=<ID=ExWarn,Number=A,Type=String,Description=\"Exomiser warning\">\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tsample\n";
    private static final String CHR10_FGFR2_CONTRIBUTING_VARIANT = "chr10\t123256215\t.\tT\tG\t2.20\tPASS\tExContribAltAllele=0;ExGeneSCombi=0.0;ExGeneSPheno=0.0;ExGeneSVar=0.0;ExGeneSymbId=2263;ExGeneSymbol=FGFR2;ExVarEff=missense_variant;ExVarHgvs=10:g.123256215T>G;ExVarScore=1.0;RD=30\tGT:RD\t0/1:30\n";
    private static final String CHR10_FGFR2_PASS_VARIANT = "chr10\t123256214\t.\tA\tG\t2.20\tPASS\tExGeneSCombi=0.0;ExGeneSPheno=0.0;ExGeneSVar=0.0;ExGeneSymbId=2263;ExGeneSymbol=FGFR2;ExVarEff=missense_variant;ExVarHgvs=10:g.123256214A>G;ExVarScore=0.89;RD=30\tGT:RD\t0/1:30\n";

    private static final FilterResult PASS_TARGET_RESULT = FilterResult.pass(FilterType.VARIANT_EFFECT_FILTER);
    private static final FilterResult FAIL_TARGET_RESULT = FilterResult.fail(FilterType.VARIANT_EFFECT_FILTER);
    private static final FilterResult FAIL_FREQUENCY_RESULT = FilterResult.fail(FilterType.FREQUENCY_FILTER);

    private final TestVariantFactory varFactory = new TestVariantFactory();

    private final VcfResultsWriter instance = new VcfResultsWriter();

    private static VCFFileReader reader;

//    private Path outPath;
    private OutputSettings settings;

    private Analysis analysis = Analysis.builder()
            .vcfPath(Paths.get("src/test/resources/minimal.vcf"))
            .inheritanceModeOptions(InheritanceModeOptions.defaults())
            .build();
    /** VariantEvaluation objects used for testing (annotated ones). */
    private VariantEvaluation fgfr2PassMissenseVariant;
    private VariantEvaluation fgfr2ContributingVariant;
    private VariantEvaluation shhIndelVariant;

    /** VariantEvaluation objects used for testing (unannotated ones). */
    private VariantEvaluation unAnnotatedVariantEvaluation1;
    private VariantEvaluation unAnnotatedVariantEvaluation2;

    private Gene fgfr2Gene;
    private Gene shhGene;

    @BeforeEach
    public void setUp() throws IOException {
//        outPath = Files.createTempFile("exomiser_test", "");
//        outPath.toFile().deleteOnExit();
        settings = OutputSettings.builder()
                .outputFormats(EnumSet.of(OutputFormat.VCF))
                .outputPrefix("testWrite")
                .build();

        setUpModel();
    }

    private void setUpModel() {
        setUpFgfr2Gene();
        setUpShhGene();
        unAnnotatedVariantEvaluation1 = VariantEvaluation.builder(5, 11, "AC", "AT").quality(1).build();
        unAnnotatedVariantEvaluation2 = VariantEvaluation.builder(5, 14, "T", "TG").quality(1).build();
    }

    private void setUpShhGene() {
        shhIndelVariant = varFactory.buildVariant(7, 155604800, "C", "CTT", Genotype.HETEROZYGOUS, 30, 0, 1.0);

        shhGene = TestFactory.newGeneSHH();
        shhGene.addVariant(shhIndelVariant);
        shhGene.addPriorityResult(new OmimPriorityResult(shhGene.getEntrezGeneID(), shhGene.getGeneSymbol(), 1f, Collections.emptyList(), Collections.emptyMap()));
    }

    private void setUpFgfr2Gene() {
        fgfr2PassMissenseVariant = varFactory.buildVariant(10, 123256214, "A", "G", Genotype.HETEROZYGOUS, 30, 0, 2.2);
        fgfr2PassMissenseVariant.setPathogenicityData(PathogenicityData.of(PolyPhenScore.valueOf(0.89f)));
        fgfr2ContributingVariant = varFactory.buildVariant(10, 123256215, "T", "G", Genotype.HETEROZYGOUS, 30, 0, 2.2);
        fgfr2ContributingVariant.setPathogenicityData(PathogenicityData.of(PolyPhenScore.valueOf(1f)));
        fgfr2Gene = TestFactory.newGeneFGFR2();
        fgfr2Gene.addVariant(fgfr2PassMissenseVariant);
        fgfr2Gene.addPriorityResult(new OmimPriorityResult(fgfr2Gene.getEntrezGeneID(), fgfr2Gene.getGeneSymbol(), 1f, Collections.emptyList(), Collections.emptyMap()));
    }

    private AnalysisResults buildAnalysisResults(Gene... genes) {
        return AnalysisResults.builder()
                .genes(Arrays.asList(genes))
                .build();
    }

    /* test that the extended header is written out properly */
    @Test
    public void testWriteHeaderFile() {
        AnalysisResults analysisResults = AnalysisResults.builder()
                .build();
        assertThat(instance.writeString(ModeOfInheritance.ANY, analysis, analysisResults, settings), equalTo(EXPECTED_HEADER));
    }

    /* test writing out unannotated variants */
    @Test
    public void testWriteUnannotatedVariants() {
        AnalysisResults analysisResults = AnalysisResults.builder()
                .variantEvaluations(Arrays.asList(unAnnotatedVariantEvaluation1, unAnnotatedVariantEvaluation2))
                .build();
        
        String vcf = instance.writeString(ModeOfInheritance.ANY, analysis, analysisResults, settings);
        final String expected = EXPECTED_HEADER
                + "chr5\t11\t.\tAC\tAT\t1\t.\tExWarn=VARIANT_NOT_ANALYSED_NO_GENE_ANNOTATIONS\tGT\t0/1\n"
                + "chr5\t14\t.\tT\tTG\t1\t.\tExWarn=VARIANT_NOT_ANALYSED_NO_GENE_ANNOTATIONS\tGT\t0/1\n";
        assertThat(vcf, equalTo(expected));
    }

    /* test writing out annotated variants in two genes */
    @Test
    public void testWriteAnnotatedVariantsNoFiltersApplied() {
        AnalysisResults analysisResults = buildAnalysisResults(fgfr2Gene, shhGene);
        fgfr2Gene.addVariant(fgfr2ContributingVariant);

        String vcf = instance.writeString(ModeOfInheritance.ANY, analysis, analysisResults, settings);
        final String expected = EXPECTED_HEADER
                + "chr10\t123256215\t.\tT\tG\t2.20\t.\tExGeneSCombi=0.0;ExGeneSPheno=0.0;ExGeneSVar=0.0;ExGeneSymbId=2263;ExGeneSymbol=FGFR2;ExVarEff=missense_variant;ExVarHgvs=10:g.123256215T>G;ExVarScore=1.0;RD=30\tGT:RD\t0/1:30\n"
                + "chr10\t123256214\t.\tA\tG\t2.20\t.\tExGeneSCombi=0.0;ExGeneSPheno=0.0;ExGeneSVar=0.0;ExGeneSymbId=2263;ExGeneSymbol=FGFR2;ExVarEff=missense_variant;ExVarHgvs=10:g.123256214A>G;ExVarScore=0.89;RD=30\tGT:RD\t0/1:30\n"
                + "chr7\t155604800\t.\tC\tCTT\t1\t.\tExGeneSCombi=0.0;ExGeneSPheno=0.0;ExGeneSVar=0.0;ExGeneSymbId=6469;ExGeneSymbol=SHH;ExVarEff=frameshift_variant;ExVarHgvs=7:g.155604800C>CTT;ExVarScore=1.0;RD=30\tGT:RD\t0/1:30\n";
        assertThat(vcf, equalTo(expected));
    }

    @Test
    public void testAnnotatedVariantGeneSymbolWhitespaceIsReplacedWithUnderscore() {
        GeneIdentifier incorrectGeneSymbol = GeneIdentifier.builder()
                .geneId("6469")
                //this should not have spaces in the VCF file
                .geneSymbol("SHH alpha spaces")
                .hgncId("HGNC:10848")
                .hgncSymbol("SHH")
                .entrezId("6469")
                .ensemblId("ENSG00000164690")
                .ucscId("uc003wmk.2")
                .build();

        Gene gene = new Gene(incorrectGeneSymbol);
        gene.addVariant(shhIndelVariant);
        gene.addPriorityResult(new OmimPriorityResult(gene.getEntrezGeneID(), gene.getGeneSymbol(), 1f, Collections.emptyList(), Collections.emptyMap()));
        gene.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        AnalysisResults analysisResults = buildAnalysisResults(gene);

        String vcf = instance.writeString(ModeOfInheritance.AUTOSOMAL_DOMINANT, analysis, analysisResults, settings);
        final String expected = EXPECTED_HEADER
                + "chr7\t155604800\t.\tC\tCTT\t1\t.\tExGeneSCombi=0.0;ExGeneSPheno=0.0;ExGeneSVar=0.0;ExGeneSymbId=6469;ExGeneSymbol=SHH_alpha_spaces;ExVarEff=frameshift_variant;ExVarHgvs=7:g.155604800C>CTT;ExVarScore=1.0;RD=30\tGT:RD\t0/1:30\n";
        assertThat(vcf, equalTo(expected));
    }

    /* test writing out a variant PASSing all filters */
    @Test
    public void testWritePassVariants() {
        fgfr2PassMissenseVariant.addFilterResult(PASS_TARGET_RESULT);
        fgfr2PassMissenseVariant.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        fgfr2ContributingVariant.addFilterResult(PASS_TARGET_RESULT);
        fgfr2ContributingVariant.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));
        fgfr2ContributingVariant.setContributesToGeneScoreUnderMode(ModeOfInheritance.AUTOSOMAL_DOMINANT);
        GeneScore geneScore = GeneScore.builder()
                .modeOfInheritance(ModeOfInheritance.AUTOSOMAL_DOMINANT)
                .geneIdentifier(fgfr2Gene.getGeneIdentifier())
                .contributingVariants(ImmutableList.of(fgfr2ContributingVariant))
                .build();

        fgfr2Gene.addVariant(fgfr2ContributingVariant);
        fgfr2Gene.addGeneScore(geneScore);
        fgfr2Gene.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        AnalysisResults analysisResults = buildAnalysisResults(fgfr2Gene);

        String vcf = instance.writeString(ModeOfInheritance.AUTOSOMAL_DOMINANT, analysis, analysisResults, settings);
        String expected = EXPECTED_HEADER + CHR10_FGFR2_CONTRIBUTING_VARIANT + CHR10_FGFR2_PASS_VARIANT;
        assertThat(vcf, equalTo(expected));
    }

    /* test writing out a variant failing the target filter */
    @Test
    public void testWriteFailTargetVariant() {
        EnumSet<ModeOfInheritance> autosomalDominantCompatible = EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT);
        fgfr2PassMissenseVariant.addFilterResult(FAIL_TARGET_RESULT);
        fgfr2PassMissenseVariant.setCompatibleInheritanceModes(autosomalDominantCompatible);
        fgfr2Gene.setCompatibleInheritanceModes(autosomalDominantCompatible);

        AnalysisResults analysisResults = buildAnalysisResults(fgfr2Gene);

        String vcfAD = instance.writeString(ModeOfInheritance.AUTOSOMAL_DOMINANT, analysis, analysisResults, settings);
        assertThat(vcfAD, equalTo(failedFgfr2VariantWithFilterField("var-effect")));

        String vcfAR = instance.writeString(ModeOfInheritance.AUTOSOMAL_RECESSIVE, analysis, analysisResults, settings);
        assertThat(vcfAR, equalTo(failedFgfr2VariantWithFilterField("inheritance;var-effect")));
    }

    private String failedFgfr2VariantWithFilterField(String filterFieldAD) {
        return EXPECTED_HEADER
                    + "chr10\t123256214\t.\tA\tG\t2.20\t" + filterFieldAD + "\tExGeneSCombi=0.0;ExGeneSPheno=0.0;ExGeneSVar=0.0;ExGeneSymbId=2263;ExGeneSymbol=FGFR2;ExVarEff=missense_variant;ExVarHgvs=10:g.123256214A>G;ExVarScore=0.89;RD=30\tGT:RD\t0/1:30\n";
    }

    @Test
    public void testWriteContributingVariantsOnlyContainsOnlyContributingVariantLine() {

        shhIndelVariant.addFilterResult(FAIL_TARGET_RESULT);
        shhIndelVariant.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        fgfr2PassMissenseVariant.addFilterResult(PASS_TARGET_RESULT);
        fgfr2PassMissenseVariant.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        fgfr2ContributingVariant.addFilterResult(PASS_TARGET_RESULT);
        fgfr2ContributingVariant.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));
        fgfr2ContributingVariant.setContributesToGeneScoreUnderMode(ModeOfInheritance.AUTOSOMAL_DOMINANT);
        GeneScore geneScore = GeneScore.builder()
                .modeOfInheritance(ModeOfInheritance.AUTOSOMAL_DOMINANT)
                .geneIdentifier(fgfr2Gene.getGeneIdentifier())
                .contributingVariants(ImmutableList.of(fgfr2ContributingVariant))
                .build();

        fgfr2Gene.addGeneScore(geneScore);
        fgfr2Gene.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        AnalysisResults analysisResults = buildAnalysisResults(fgfr2Gene, shhGene);

        OutputSettings outputContributingVariantsOnly = OutputSettings.builder()
                .outputFormats(EnumSet.of(OutputFormat.VCF))
                .outputContributingVariantsOnly(true)
                .build();
        
        String output = instance.writeString(ModeOfInheritance.AUTOSOMAL_DOMINANT, analysis, analysisResults, outputContributingVariantsOnly);
        String expected = EXPECTED_HEADER + CHR10_FGFR2_CONTRIBUTING_VARIANT;
        assertThat(output, equalTo(expected));
    }

    @Test
    public void testWritePassVariantsWithNoPassingVariants() {
        fgfr2PassMissenseVariant.addFilterResult(FAIL_TARGET_RESULT);
        fgfr2PassMissenseVariant.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        shhIndelVariant.addFilterResult(FAIL_TARGET_RESULT);
        shhIndelVariant.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        fgfr2Gene.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));
        shhGene.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        AnalysisResults analysisResults = buildAnalysisResults(fgfr2Gene, shhGene);

        OutputSettings outputPassVariantsOnlySettings = OutputSettings.builder()
                .outputFormats(EnumSet.of(OutputFormat.VCF))
                .outputContributingVariantsOnly(true)
                .build();

        String output = instance.writeString(ModeOfInheritance.AUTOSOMAL_DOMINANT, analysis, analysisResults, outputPassVariantsOnlySettings);
        assertThat(output, equalTo(EXPECTED_HEADER));
    }
    
    @Test
    public void testAlternativeAllelesAreWrittenOnSuccessiveLines() {
        TestVariantFactory varFactory = new TestVariantFactory();
        VariantEvaluation alt1 = varFactory.buildVariant(1, 120612040, "T", "TCCGCCG", Genotype.HETEROZYGOUS, 30, 0, 258.62);
        VariantEvaluation alt2 = varFactory.buildVariant(1, 120612040, "T", "TCCTCCGCCG", Genotype.HOMOZYGOUS_ALT, 30, 0, 258.62);
        Gene gene = new Gene("TEST", 12345);
        gene.addVariant(alt1);
        gene.addVariant(alt2);
        gene.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        AnalysisResults analysisResults = buildAnalysisResults(gene);

        String output = instance.writeString(ModeOfInheritance.AUTOSOMAL_DOMINANT, analysis, analysisResults, settings);
        System.out.println(output);
        String expected = EXPECTED_HEADER
                + "chr1\t120612040\t.\tT\tTCCGCCG\t258.62\t.\tExGeneSCombi=0.0;ExGeneSPheno=0.0;ExGeneSVar=0.0;ExGeneSymbId=12345;ExGeneSymbol=TEST;ExVarEff=intergenic_variant;ExVarHgvs=1:g.120612040T>TCCGCCG;ExVarScore=0.0;RD=30\tGT:RD\t0/1:30\n"
                + "chr1\t120612040\t.\tT\tTCCTCCGCCG\t258.62\t.\tExGeneSCombi=0.0;ExGeneSPheno=0.0;ExGeneSVar=0.0;ExGeneSymbId=12345;ExGeneSymbol=TEST;ExVarEff=intergenic_variant;ExVarHgvs=1:g.120612040T>TCCTCCGCCG;ExVarScore=0.0;RD=30\tGT:RD\t1/1:30\n";
        assertThat(output, equalTo(expected));
    }

    @Test
    public void testHomozygousAltAlleleOutputVcfContainsConcatenatedVariantScoresOnOneLine() {
        Path vcfPath = Paths.get("src/test/resources/multiAlleleGenotypes.vcf");
        VariantFactory variantFactory = TestFactory.buildDefaultVariantFactory();
        List<VariantEvaluation> variants = variantFactory.createVariantEvaluations(vcfPath).collect(toList());
        variants.forEach(System.out::println);
        // 1/2 HETEROZYGOUS_ALT - needs to be written back out as a single line
        VariantEvaluation altAlleleOne = variants.get(3);
        //change the variant effect from MISSENSE so that the score is different and the order can be tested on the output line
        //variant score is 0.95 - contributes to score
        altAlleleOne.setVariantEffect(VariantEffect.FRAMESHIFT_VARIANT);
        altAlleleOne.setContributesToGeneScoreUnderMode(ModeOfInheritance.AUTOSOMAL_DOMINANT);

        //variant score is 0.6
        VariantEvaluation altAlleleTwo = variants.get(4);

        Gene gene = new Gene(GeneIdentifier.builder()
                .geneSymbol(altAlleleOne.getGeneSymbol())
                .geneId(altAlleleOne.getGeneId())
                .build());
        gene.addVariant(altAlleleOne);
        gene.addVariant(altAlleleTwo);
        gene.setCompatibleInheritanceModes(EnumSet.of(ModeOfInheritance.AUTOSOMAL_DOMINANT));

        AnalysisResults analysisResults = buildAnalysisResults(gene);

        String output = instance.writeString(ModeOfInheritance.AUTOSOMAL_DOMINANT, analysis, analysisResults, settings);
        System.out.println(output);
        //expected should have concatenated variant score for multi-allele line: ExVarSCombi=0.85,0.6
        String expected = EXPECTED_HEADER
                + "10\t123256215\t.\tT\tG,A\t100\t.\tExContribAltAllele=0;ExGeneSCombi=0.0;ExGeneSPheno=0.0;ExGeneSVar=0.0;ExGeneSymbId=2263;ExGeneSymbol=FGFR2;ExVarEff=frameshift_variant,missense_variant;ExVarHgvs=10:g.123256215T>G,10:g.123256215T>A;ExVarScore=1.0,0.6;GENE=FGFR2;INHERITANCE=AD;MIM=101600\tGT\t1/2\n";
        assertThat(output, equalTo(expected));
    }
}
