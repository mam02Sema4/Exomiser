package de.charite.compbio.exomiser.core.analysis;

import de.charite.compbio.exomiser.core.factories.SampleDataFactory;
import de.charite.compbio.exomiser.core.factories.VariantDataService;
import de.charite.compbio.exomiser.core.factories.VariantFactory;
import de.charite.compbio.exomiser.core.filters.*;
import de.charite.compbio.exomiser.core.model.Gene;
import de.charite.compbio.exomiser.core.model.SampleData;
import de.charite.compbio.exomiser.core.model.VariantEvaluation;
import de.charite.compbio.exomiser.core.prioritisers.Prioritiser;
import de.charite.compbio.exomiser.core.prioritisers.PrioritiserRunner;
import de.charite.compbio.exomiser.core.prioritisers.ScoringMode;
import de.charite.compbio.exomiser.core.analysis.util.GeneScorer;
import de.charite.compbio.exomiser.core.analysis.util.InheritanceModeAnalyser;
import de.charite.compbio.exomiser.core.analysis.util.RankBasedGeneScorer;
import de.charite.compbio.exomiser.core.analysis.util.RawScoreGeneScorer;
import de.charite.compbio.jannovar.pedigree.ModeOfInheritance;
import de.charite.compbio.jannovar.pedigree.Pedigree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toList;

/**
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
public abstract class AbstractAnalysisRunner implements AnalysisRunner {

    private static final Logger logger = LoggerFactory.getLogger(AbstractAnalysisRunner.class);

    private final SampleDataFactory sampleDataFactory;
    private final VariantDataService variantDataService;
    protected final VariantFilterRunner variantFilterRunner;
    private final GeneFilterRunner geneFilterRunner;
    private final PrioritiserRunner prioritiserRunner;

    public AbstractAnalysisRunner(SampleDataFactory sampleDataFactory, VariantDataService variantDataService, VariantFilterRunner variantFilterRunner, GeneFilterRunner geneFilterRunner) {
        this.sampleDataFactory = sampleDataFactory;
        this.variantDataService = variantDataService;
        this.variantFilterRunner = variantFilterRunner;
        this.geneFilterRunner = geneFilterRunner;
        this.prioritiserRunner = new PrioritiserRunner();
    }

    @Override
    public void runAnalysis(Analysis analysis) {

        final SampleData sampleData = makeSampleDataWithoutGenesOrVariants(analysis);

        logger.info("Running analysis on sample: {}", sampleData.getSampleNames());
        long startAnalysisTimeMillis = System.currentTimeMillis();

        final Pedigree pedigree = sampleData.getPedigree();
        final Path vcfPath = analysis.getVcfPath();
        final List<AnalysisStep> analysisSteps = analysis.getAnalysisSteps();
        new AnalysisStepChecker().check(analysisSteps);

        Map<String, Gene> allGenes = makeKnownGenes();
        List<VariantEvaluation> variantEvaluations = new ArrayList<>();
//        some kind of multi-map with ordered duplicate keys would allow for easy grouping of steps for running the groups together.
        List<List<AnalysisStep>> analysisStepGroups = groupAnalysisStepsByFunction(analysisSteps);
        boolean variantsLoaded = false;
        for (List<AnalysisStep> analysisGroup : analysisStepGroups) {
            //this is admittedly pretty confusing code and I'm sorry. It's easiest to follow if you turn on debugging.
            //The analysis steps are run in groups of VARIANT_FILTER, GENE_ONLY_DEPENDENT or INHERITANCE_MODE_DEPENDENT
            AnalysisStep firstStep = analysisGroup.get(0);
            logger.debug("Running {} group: {}", firstStep.getType(), analysisGroup);
            if (firstStep.isVariantFilter() & !variantsLoaded) {
                //variants take up 99% of all the memory in an analysis - this scales approximately linearly with the sample size
                //so for whole genomes this is best run as a stream to filter out the unwanted variants with as many filters as possible in one go
                variantEvaluations = loadAndFilterVariants(vcfPath, allGenes, analysisGroup);
                variantsLoaded = true;
            } else {
                runSteps(analysisGroup, new ArrayList<>(allGenes.values()), pedigree, analysis.getModeOfInheritance());
            }
        }
        //maybe only the non-variant dependent steps have been run in which case we need to load the variants although
        //the results might be a bit meaningless.
        if (!variantsLoaded) {
            variantEvaluations = loadVariants(vcfPath, allGenes, variantEvaluation -> true, variantEvaluation -> true);
            //TODO: add the gene FilterResults to each variant in the gene too? Required for the VCF file only?
        }

        final List<Gene> genes = getFinalGeneList(allGenes);
        sampleData.setGenes(genes);
        final List<VariantEvaluation> variants = getFinalVariantList(variantEvaluations);
        sampleData.setVariantEvaluations(variants);

        scoreGenes(genes, analysis.getScoringMode(), analysis.getModeOfInheritance());
        logger.info("Analysed {} genes containing {} filtered variants", genes.size(), variantEvaluations.size());

        long endAnalysisTimeMillis = System.currentTimeMillis();
        double analysisTimeSecs = (double) (endAnalysisTimeMillis - startAnalysisTimeMillis) / 1000;
        logger.info("Finished analysis in {} secs", analysisTimeSecs);
    }

    private List<VariantEvaluation> loadAndFilterVariants(Path vcfPath, Map<String, Gene> allGenes, List<AnalysisStep> analysisGroup) {
        Predicate<VariantEvaluation> isInKnownGene = isInKnownGene(allGenes);
        List<VariantFilter> variantFilters = getVariantFilterSteps(analysisGroup);
        Predicate<VariantEvaluation> runVariantFilters = runVariantFilters(variantFilters);

        return loadVariants(vcfPath, allGenes, isInKnownGene, runVariantFilters);
    }

    /**
     *
     */
    protected Predicate<VariantEvaluation> runVariantFilters(List<VariantFilter> variantFilters) {
        return variantEvaluation -> {
            //loop through the filters and run them over the variantEvaluation according to the variantFilterRunner behaviour
            variantFilters.forEach(filter -> variantFilterRunner.run(filter, variantEvaluation));
            return true;
        };
    }

    protected Predicate<VariantEvaluation> isInKnownGene(Map<String, Gene> genes) {
        return variantEvaluation -> genes.containsKey(variantEvaluation.getGeneSymbol());
    }

    private List<VariantEvaluation> loadVariants(Path vcfPath, Map<String, Gene> genes, Predicate<VariantEvaluation> isInKnownGene, Predicate<VariantEvaluation> runVariantFilters) {

        final int[] streamed = {0};
        final int[] passed = {0};

        VariantFactory variantFactory = sampleDataFactory.getVariantFactory();
        List<VariantEvaluation> variantEvaluations;
        try (Stream<VariantEvaluation> variantEvaluationStream = variantFactory.streamVariantEvaluations(vcfPath)) {
            //WARNING!!! THIS IS NOT THREADSAFE DO NOT USE PARALLEL STREAMS
            variantEvaluations = variantEvaluationStream
                    .map(variantEvaluation -> {
                        //yep, logging logic
                        streamed[0]++;
                        if (streamed[0] % 100000 == 0) {
                            logger.info("Loaded {} variants - {} passed variant filters", streamed[0], passed[0]);
                        }
                        return variantEvaluation;
                    })
                    .filter(isInKnownGene)
                    .filter(runVariantFilters)
                    .map(variantEvaluation -> {
                        if (variantEvaluation.passedFilters()) {
                            //more logging logic
                            passed[0]++;
                        }
                        return variantEvaluation;
                    })
                    .map(variantEvaluation -> {
                        Gene gene = genes.get(variantEvaluation.getGeneSymbol());
                        gene.addVariant(variantEvaluation);
                        return variantEvaluation;
                    })
                    .collect(toList());
        }
        logger.info("Loaded {} variants - {} passed variant filters", streamed[0], passed[0]);
        return variantEvaluations;
    }

    /**
     *
     * @param passedGenes
     * @return
     */
    protected List<Gene> getFinalGeneList(Map<String, Gene> passedGenes) {
        return passedGenes.values()
                .stream()
                .filter(gene -> !gene.getVariantEvaluations().isEmpty())
                .collect(toList());
    }

    protected List<VariantEvaluation> getFinalVariantList(List<VariantEvaluation> variants) {
        return variants;
    }

    /**
     * @return a map of genes indexed by gene symbol.
     */
    private Map<String, Gene> makeKnownGenes() {
        return sampleDataFactory.createKnownGenes()
                .parallelStream()
                .collect(toConcurrentMap(Gene::getGeneSymbol, gene -> gene));
    }

    private List<List<AnalysisStep>> groupAnalysisStepsByFunction(List<AnalysisStep> analysisSteps) {
        List<List<AnalysisStep>> groups = new ArrayList<>();
        if (analysisSteps.isEmpty()) {
            logger.debug("No AnalysisSteps to group.");
            return groups;
        }

        AnalysisStep currentGroupStep = analysisSteps.get(0);
        List<AnalysisStep> currentGroup = new ArrayList<>();
        currentGroup.add(currentGroupStep);
        logger.debug("First group is for {} steps", currentGroupStep.getType());
        for (int i = 1; i < analysisSteps.size(); i++) {
            AnalysisStep step = analysisSteps.get(i);

            if (currentGroupStep.getType() != step.getType()) {
                logger.debug("Making new group for {} steps", step.getType());
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentGroupStep = step;
            }

            currentGroup.add(step);
        }
        //make sure the last group is added too
        groups.add(currentGroup);

        return groups;
    }

    private List<VariantFilter> getVariantFilterSteps(List<AnalysisStep> analysisSteps) {
        logger.info("Filtering variants with:");
        return analysisSteps
                .stream()
                .filter(analysisStep -> (analysisStep.isVariantFilter()))
                .map(analysisStep -> {
                    logger.info("{}", analysisStep);
                    return (VariantFilter) analysisStep;
                })
                .collect(toList());
    }

    private SampleData makeSampleDataWithoutGenesOrVariants(Analysis analysis) {
        final SampleData sampleData = sampleDataFactory.createSampleDataWithoutVariantsOrGenes(analysis.getVcfPath(), analysis.getPedPath());
        analysis.setSampleData(sampleData);
        return sampleData;
    }

    private void runSteps(List<AnalysisStep> analysisSteps, List<Gene> genes, Pedigree pedigree, ModeOfInheritance modeOfInheritance) {
        boolean inheritanceModesCalculated = false;
        for (AnalysisStep analysisStep : analysisSteps) {
            if (!inheritanceModesCalculated && analysisStep.isInheritanceModeDependent()) {
                analyseGeneCompatibilityWithInheritanceMode(genes, pedigree, modeOfInheritance);
                inheritanceModesCalculated = true;
            }
            runStep(analysisStep, genes);
        }
    }

    //TODO: would this be better using the Visitor pattern?
    private void runStep(AnalysisStep analysisStep, List<Gene> genes) {
        if (analysisStep.isVariantFilter()) {
            VariantFilter filter = (VariantFilter) analysisStep;
            logger.info("Running VariantFilter: {}", filter);
            for (Gene gene : genes) {
                variantFilterRunner.run(filter, gene.getVariantEvaluations());
            }
            return;
        }
        if (GeneFilter.class.isInstance(analysisStep)) {
            GeneFilter filter = (GeneFilter) analysisStep;
            logger.info("Running GeneFilter: {}", filter);
            geneFilterRunner.run(filter, genes);
            return;
        }
        if (Prioritiser.class.isInstance(analysisStep)) {
            Prioritiser prioritiser = (Prioritiser) analysisStep;
            logger.info("Running Prioritiser: {}", prioritiser);
            prioritiserRunner.run(prioritiser, genes);
        }
    }

    private void analyseGeneCompatibilityWithInheritanceMode(List<Gene> genes, Pedigree pedigree, ModeOfInheritance modeOfInheritance) {
        InheritanceModeAnalyser inheritanceModeAnalyser = new InheritanceModeAnalyser(pedigree, modeOfInheritance);
        logger.info("Checking compatibility with {} inheritance mode for genes which passed filters", modeOfInheritance);
        inheritanceModeAnalyser.analyseInheritanceModes(genes);
    }

    private void scoreGenes(List<Gene> genes, ScoringMode scoreMode, ModeOfInheritance modeOfInheritance) {
        logger.info("Scoring genes");
        GeneScorer geneScorer = getGeneScorer(scoreMode);
        geneScorer.scoreGenes(genes, modeOfInheritance);
    }

    private GeneScorer getGeneScorer(ScoringMode scoreMode) {
        if (scoreMode == ScoringMode.RANK_BASED) {
            return new RankBasedGeneScorer();
        }
        return new RawScoreGeneScorer();
    }

}