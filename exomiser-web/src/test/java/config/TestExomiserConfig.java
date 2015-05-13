/*
 * Copyright (C) 2014 jj8
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
package config;

import de.charite.compbio.exomiser.core.dao.FrequencyDao;
import de.charite.compbio.exomiser.core.dao.PathogenicityDao;
import de.charite.compbio.exomiser.core.factories.SampleDataFactory;
import de.charite.compbio.exomiser.core.factories.VariantDataService;
import de.charite.compbio.exomiser.core.filters.FilterFactory;
import de.charite.compbio.exomiser.core.filters.SparseVariantFilterRunner;
import de.charite.compbio.exomiser.core.Exomiser;
import de.charite.compbio.exomiser.core.dao.DefaultDiseaseDao;
import de.charite.compbio.exomiser.core.dao.DiseaseDao;
import de.charite.compbio.exomiser.core.dao.HumanPhenotypeOntologyDao;
import de.charite.compbio.exomiser.core.dao.MousePhenotypeOntologyDao;
import de.charite.compbio.exomiser.core.dao.ZebraFishPhenotypeOntologyDao;
import de.charite.compbio.exomiser.core.factories.VariantAnnotationsFactory;
import de.charite.compbio.exomiser.core.factories.VariantFactory;
import de.charite.compbio.exomiser.core.prioritisers.PriorityFactory;
import de.charite.compbio.exomiser.core.prioritisers.util.ModelService;
import de.charite.compbio.exomiser.core.prioritisers.util.ModelServiceImpl;
import de.charite.compbio.exomiser.core.prioritisers.util.OntologyService;
import de.charite.compbio.exomiser.core.prioritisers.util.OntologyServiceImpl;
import de.charite.compbio.exomiser.core.prioritisers.util.PriorityService;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

/**
 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
@Configuration
@PropertySource({"classpath:exomiser.properties"})
@Import(value = {TestDaoConfig.class})
public class TestExomiserConfig {
    
    Logger logger = LoggerFactory.getLogger(TestExomiserConfig.class);
    
    @Autowired
    private Environment env;
    
    @Bean
    public Path dataPath() {
        Path dataPath = Paths.get(env.getProperty("dataDir"));
        logger.debug("Root data source directory set to: {}", dataPath.toAbsolutePath());
        
        return dataPath;
    }
    
    @Bean
    public int maxVariants() {
        int maxVariants = Integer.valueOf(env.getProperty("maxVariants"));
        logger.info("Set max variants to {}", maxVariants);
        return maxVariants;
    }
    
    @Bean
    public int maxGenes() {
        int maxGenes = Integer.valueOf(env.getProperty("maxGenes"));
        logger.info("Set max genes to {}", maxGenes);
        return maxGenes;
    }

    @Bean
    public Path ucscFilePath() {
        Path ucscFilePath = dataPath().resolve(env.getProperty("ucscFileName"));
        logger.debug("UCSC data file: {}", ucscFilePath.toAbsolutePath());
        return ucscFilePath;
    }

    @Bean
    public Path phenixDataDirectory() {
        Path phenixDataDirectory = dataPath().resolve(env.getProperty("phenomizerDataDir"));
        logger.info("phenixDataDirectory: {}", phenixDataDirectory.toAbsolutePath());
        return phenixDataDirectory;
    }

    @Bean
    public Path hpoOntologyFilePath() {
        Path hpoOntologyFilePath = phenixDataDirectory().resolve(env.getProperty("hpoOntologyFile"));
        logger.debug("hpoOntologyFilePath: {}", hpoOntologyFilePath.toAbsolutePath());
        return hpoOntologyFilePath;
    }

    @Bean
    public Path hpoAnnotationFilePath() {
        Path hpoAnnotationFilePath = phenixDataDirectory().resolve(env.getProperty("hpoAnnotationFile"));
        logger.debug("hpoAnnotationFilePath: {}", hpoAnnotationFilePath.toAbsolutePath());
        return hpoAnnotationFilePath;
    }

    @Bean
    public Exomiser mockExomiser() {
        return Mockito.mock(Exomiser.class);
    }
    
    @Bean
    public VariantFactory variantFactory() {
        return new VariantFactory(mockVariantAnnotator());
    }
 
    @Bean
    public SampleDataFactory sampleDataFactory() {
        return Mockito.mock(SampleDataFactory.class);
    }
    
    @Bean
    public VariantAnnotationsFactory mockVariantAnnotator() {
        return Mockito.mock(VariantAnnotationsFactory.class);
    }
    
    @Bean
    public FilterFactory mockFilterFactory() {
        return Mockito.mock(FilterFactory.class);
    }
    
    @Bean
    public PriorityFactory mockPriorityFactory() {
        return Mockito.mock(PriorityFactory.class);
    }
    
    //cacheable beans
    @Bean
    public FrequencyDao frequencyDao() {
        return Mockito.mock(FrequencyDao.class);
    }

    @Bean
    public PathogenicityDao pathogenicityDao() {
        return Mockito.mock(PathogenicityDao.class);
    }

    @Bean 
    public SparseVariantFilterRunner sparseVariantFilterer() {
        return Mockito.mock(SparseVariantFilterRunner.class);
    }
    
    @Bean
    public VariantDataService variantEvaluationDataService() {
        return Mockito.mock(VariantDataService.class);
    }
    
    @Bean
    PriorityService priorityService() {
        return new PriorityService();
    }
    
    @Bean
    ModelService modelService() {
        return new ModelServiceImpl();
    }
    
    @Bean
    OntologyService ontologyService() {
        return new OntologyServiceImpl();
    }
    
    @Bean
    DiseaseDao diseaseDao() {
        return new DefaultDiseaseDao();
    }
    
    @Bean
    HumanPhenotypeOntologyDao humanPhenotypeOntologyDao() {
        return new HumanPhenotypeOntologyDao();
    }
    
    @Bean
    MousePhenotypeOntologyDao mousePhenotypeOntologyDao() {
        return new MousePhenotypeOntologyDao();
    }
    
    @Bean
    ZebraFishPhenotypeOntologyDao zebraFishPhenotypeOntologyDao() {
        return new ZebraFishPhenotypeOntologyDao();
    }
}