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

package org.monarchinitiative.exomiser.core.model;

import com.google.common.collect.ImmutableMap;
import de.charite.compbio.jannovar.impl.intervals.IntervalArray;
import de.charite.compbio.jannovar.impl.intervals.IntervalEndExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

/**
 * Interval tree-backed index for chromosomal regions. It enables extremely fast in-memory lookups to find the regions
 * in which a variant can be found.
 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
public class ChromosomalRegionIndex<T extends ChromosomalRegion> {

    private static final ChromosomalRegionIndex EMPTY = new ChromosomalRegionIndex<>(ImmutableMap.of());

    private static final Logger logger = LoggerFactory.getLogger(ChromosomalRegionIndex.class);

    private final Map<Integer, IntervalArray<T>> index;

    public static <T extends ChromosomalRegion> ChromosomalRegionIndex<T> of(Collection<T> chromosomalRegions) {
        Map<Integer, Set<T>> regionIndex = chromosomalRegions.stream().collect(groupingBy(T::getChromosome, toSet()));

        Map<Integer, IntervalArray<T>> intervalTreeIndex = new HashMap<>();
        for (Map.Entry<Integer, Set<T>> entry : regionIndex.entrySet()) {
            intervalTreeIndex.put(entry.getKey(), new IntervalArray<>(entry.getValue(), new ChromosomalRegionEndExtractor<>()));
        }
        logger.debug("Created index for {} chromosomes totalling {} regions", intervalTreeIndex.keySet().size(), intervalTreeIndex.values().stream().mapToInt(IntervalArray::size).sum());

        return new ChromosomalRegionIndex<>(intervalTreeIndex);
    }

    private ChromosomalRegionIndex(Map<Integer, IntervalArray<T>> index) {
        this.index = index;
    }

    /**
     * Returns an empty index. Useful for testing.
     * @return An empty index
     * @since 11.0.0
     */
    // Casting to any type is safe because the index will never hold any elements.
    @SuppressWarnings("unchecked")
    public static <T extends ChromosomalRegion> ChromosomalRegionIndex<T> empty() {
        return (ChromosomalRegionIndex<T>) EMPTY;
    }

    public boolean hasRegionContainingVariant(VariantCoordinates variant) {
        return !getRegionsContainingVariant(variant).isEmpty();
    }

    /**
     *
     * @param chromosome chromosome of the position of interest
     * @param position 1-based position to be tested for inclusion within the intervals of the index
     * @return true if the position is contained within a region in the index, otherwise false
     * @since 11.0.0
     */
    public boolean hasRegionContainingPosition(int chromosome, int position) {
        return !getRegionsOverlappingPosition(chromosome, position).isEmpty();
    }

    public List<T> getRegionsContainingVariant(VariantCoordinates variantCoordinates) {
        int chromosome = variantCoordinates.getChromosome();
        int position = variantCoordinates.getPosition();
        return getRegionsOverlappingPosition(chromosome, position);
    }

    /**
     * Use one-based co-ordinates for this method.
     *
     * @param chromosome
     * @param position
     * @return
     */
    public List<T> getRegionsOverlappingPosition(int chromosome, int position) {
        IntervalArray<T> intervalTree = index.get(chromosome);
        if (intervalTree == null) {
            return Collections.emptyList();
        }
        IntervalArray<T>.QueryResult queryResult = intervalTree.findOverlappingWithPoint(position - 1);
        return queryResult.getEntries();
    }

    /**
     * Returns the number of intervals stored in the index.
     * @return the number of intervals stored in the index.
     * @since 11.0.0
     */
    public int size() {
        return index.values().stream().mapToInt(IntervalArray::size).sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChromosomalRegionIndex<?> that = (ChromosomalRegionIndex<?>) o;
        return Objects.equals(index, that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }

    private static class ChromosomalRegionEndExtractor<T extends ChromosomalRegion> implements IntervalEndExtractor<T> {

        @Override
        public int getBegin(T region) {
            return region.getStart() - 1;
        }

        @Override
        public int getEnd(T region) {
            return region.getEnd();
        }
    }

}
