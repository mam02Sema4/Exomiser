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

package org.monarchinitiative.exomiser.autoconfigure.genome;

import de.charite.compbio.jannovar.data.JannovarData;
import org.h2.mvstore.MVStore;
import org.monarchinitiative.exomiser.core.genome.dao.ErrorThrowingTabixDataSource;
import org.monarchinitiative.exomiser.core.genome.dao.TabixDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Jules Jacobsen <j.jacobsen@qmul.ac.uk>
 */
public class GenomeDataSourceLoader {

    private static final Logger logger = LoggerFactory.getLogger(GenomeDataSourceLoader.class);

    private final DataSource dataSource;
    private final JannovarData jannovarData;
    private final MVStore mvStore;

    //TabixDataSources
    private final TabixDataSource localFrequencyTabixDataSource;
    private final TabixDataSource caddSnvTabixDataSource;
    private final TabixDataSource caddIndelTabixDataSource;
    private final TabixDataSource remmTabixDataSource;

    public static GenomeDataSourceLoader load(GenomeDataSources genomeDataSources) {
        return new GenomeDataSourceLoader(genomeDataSources);
    }

    private GenomeDataSourceLoader(GenomeDataSources genomeDataSources) {
        this.dataSource = genomeDataSources.getGenomeDataSource();

        Path transcriptFilePath = genomeDataSources.getTranscriptFilePath();
        logger.debug("Loading transcript data from {}", transcriptFilePath);
        this.jannovarData = JannovarDataSourceLoader.loadJannovarData(transcriptFilePath);

        Path mvStoreAbsolutePath = genomeDataSources.getMvStorePath();
        logger.debug("Opening MVStore from {}", mvStoreAbsolutePath);
        this.mvStore = MvStoreDataSourceLoader.openMvStore(mvStoreAbsolutePath);

        this.localFrequencyTabixDataSource = getTabixDataSourceOrDefault("LOCAL", genomeDataSources.getLocalFrequencyPath());
        this.caddSnvTabixDataSource = getTabixDataSourceOrDefault("CADD snv", genomeDataSources.getCaddSnvPath());
        this.caddIndelTabixDataSource = getTabixDataSourceOrDefault("CADD InDel", genomeDataSources.getCaddIndelPath());
        this.remmTabixDataSource = getTabixDataSourceOrDefault("REMM", genomeDataSources.getRemmPath());
    }

    private TabixDataSource getTabixDataSourceOrDefault(String dataSourceName, Optional<Path> tabixPath) {
        if (tabixPath.isPresent()) {
            Path path = tabixPath.get();
            logger.info("Opening {} data from source: {}", dataSourceName, path);
            return TabixDataSourceLoader.load(path);
        } else {
            logger.warn("Data for {} is not configured. THIS WILL LEAD TO ERRORS IF REQUIRED DURING ANALYSIS. Check the application.properties is pointing to a valid file.", dataSourceName);
            String message = "Data for " + dataSourceName + " is not configured. Check the application.properties is pointing to a valid file.";
            return new ErrorThrowingTabixDataSource(message);
        }
    }

    public DataSource getGenomeDataSource() {
        return dataSource;
    }

    public JannovarData getJannovarData() {
        return jannovarData;
    }

    public MVStore getMvStore() {
        return mvStore;
    }

    public TabixDataSource getLocalFrequencyTabixDataSource() {
        return localFrequencyTabixDataSource;
    }

    public TabixDataSource getCaddSnvTabixDataSource() {
        return caddSnvTabixDataSource;
    }

    public TabixDataSource getCaddIndelTabixDataSource() {
        return caddIndelTabixDataSource;
    }

    public TabixDataSource getRemmTabixDataSource() {
        return remmTabixDataSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenomeDataSourceLoader that = (GenomeDataSourceLoader) o;
        return Objects.equals(dataSource, that.dataSource) &&
                Objects.equals(jannovarData, that.jannovarData) &&
                Objects.equals(mvStore, that.mvStore) &&
                Objects.equals(localFrequencyTabixDataSource, that.localFrequencyTabixDataSource) &&
                Objects.equals(caddSnvTabixDataSource, that.caddSnvTabixDataSource) &&
                Objects.equals(caddIndelTabixDataSource, that.caddIndelTabixDataSource) &&
                Objects.equals(remmTabixDataSource, that.remmTabixDataSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSource, jannovarData, mvStore, localFrequencyTabixDataSource, caddSnvTabixDataSource, caddIndelTabixDataSource, remmTabixDataSource);
    }

}
