/*
 * The Exomiser - A tool to annotate and prioritize genomic variants
 *
 * Copyright (c) 2016-2019 Queen Mary University of London.
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

package org.monarchinitiative.exomiser.core.genome.jannovar;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.charite.compbio.jannovar.data.JannovarData;
import de.charite.compbio.jannovar.data.ReferenceDictionary;
import de.charite.compbio.jannovar.data.ReferenceDictionaryBuilder;
import de.charite.compbio.jannovar.reference.GenomeInterval;
import de.charite.compbio.jannovar.reference.Strand;
import de.charite.compbio.jannovar.reference.TranscriptModel;
import org.monarchinitiative.exomiser.core.proto.JannovarProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Ser/de-serialiser for JannovarData to/from protobuf.
 *
 * @author Jules Jacobsen <j.jacobsen@qmul.ac.uk>
 */
public class JannovarProtoConverter {

    private static final Logger logger = LoggerFactory.getLogger(JannovarProtoConverter.class);

    private JannovarProtoConverter() {
        //uninstantiable utility class
    }

    public static JannovarProto.JannovarData toJannovarProto(JannovarData jannovarData) {
        logger.debug("Converting jannovar data...");
        ReferenceDictionary referenceDictionary = jannovarData.getRefDict();
        JannovarProto.ReferenceDictionary protoReferenceDictionary = toProtoReferenceDictionary(referenceDictionary);
        logger.debug("Converted referenceDictionary with {} contigs", protoReferenceDictionary.getContigNameToIdCount());

        Map<String, TranscriptModel> transcriptModelsByAccession = jannovarData.getTmByAccession();
        Set<TranscriptModel> uniqueTranscriptModels = new TreeSet<>(transcriptModelsByAccession.values());
        logger.debug("Converting {} transcript models...", uniqueTranscriptModels.size());
        // sorting and preserving the order leads to much smaller files (~30% of original size) with identical sizes each run.
        Set<JannovarProto.TranscriptModel> protoTranscriptModels = uniqueTranscriptModels
                .parallelStream()
                .sorted()
                .map(toProtoTranscriptModel())
                .collect(ImmutableSet.toImmutableSet());
        logger.debug("Added {} transcript models", protoTranscriptModels.size());

        return JannovarProto.JannovarData.newBuilder()
                .setReferenceDictionary(protoReferenceDictionary)
                .addAllTranscriptModels(protoTranscriptModels)
                .build();
    }

    private static JannovarProto.ReferenceDictionary toProtoReferenceDictionary(ReferenceDictionary referenceDictionary) {
        return JannovarProto.ReferenceDictionary.newBuilder()
                .putAllContigNameToId(referenceDictionary.getContigNameToID())
                .putAllContigIdToLength(referenceDictionary.getContigIDToLength())
                .putAllContigIdToName(referenceDictionary.getContigIDToName())
                .build();
    }

    private static Function<TranscriptModel, JannovarProto.TranscriptModel> toProtoTranscriptModel() {
        return transcriptModel -> JannovarProto.TranscriptModel.newBuilder()
                .setAccession(trimDuplicatedEnsemblVersion(transcriptModel.getAccession()))
                .setGeneSymbol(transcriptModel.getGeneSymbol())
                .setGeneID((transcriptModel.getGeneID() == null || transcriptModel.getGeneID()
                        .equals(".")) ? "" : transcriptModel.getGeneID())
                .putAllAltGeneIds(replaceDotGeneIdWithEmpty(transcriptModel.getAltGeneIDs()))
                .setTranscriptSupportLevel(transcriptModel.getTranscriptSupportLevel())
                .setSequence(transcriptModel.getSequence())
                .setCdsRegion(toProtoGenomeInterval(transcriptModel.getCDSRegion()))
                .setTxRegion(toProtoGenomeInterval(transcriptModel.getTXRegion()))
                .addAllExonRegions(toProtoExonRegions(transcriptModel.getExonRegions()))
                .build();
    }

    private static final Pattern ENST_REPEAT_VERSION = Pattern.compile("ENST[0-9]{11}\\.[0-9]+\\.[0-9]+");

    //TODO: REMOVE THIS for jannovar version 0.33!
    // fix bug in Jannovar 0.29 where the transcript version is duplicated
    static String trimDuplicatedEnsemblVersion(String transcriptAccession) {
        if (ENST_REPEAT_VERSION.matcher(transcriptAccession).matches()) {
            int lastDot = transcriptAccession.lastIndexOf('.');
            String trimmed = transcriptAccession.substring(0, lastDot);
            logger.debug("{} -> {}", transcriptAccession, trimmed);
            return trimmed;
        }
        return transcriptAccession;
    }

    private static Map<String, String> replaceDotGeneIdWithEmpty(Map<String, String> altGeneIds) {
        String entrezId = altGeneIds.getOrDefault("ENTREZ_ID", "");
        if (entrezId.equals(".")) {
            LinkedHashMap<String, String> altGeneIdsCopy = new LinkedHashMap<>(altGeneIds);
            altGeneIdsCopy.replace("ENTREZ_ID", ".", "");
            return altGeneIdsCopy;
        }
        return altGeneIds;
    }

    private static JannovarProto.GenomeInterval toProtoGenomeInterval(GenomeInterval genomeInterval) {
        return JannovarProto.GenomeInterval.newBuilder()
                .setChr(genomeInterval.getChr())
                .setStrand((genomeInterval.getStrand() == Strand.FWD) ? JannovarProto.Strand.FWD : JannovarProto.Strand.REV)
                .setBeginPos(genomeInterval.getBeginPos())
                .setEndPos(genomeInterval.getEndPos())
                .build();
    }

    private static List<JannovarProto.GenomeInterval> toProtoExonRegions(List<GenomeInterval> genomeIntervals) {
        List<JannovarProto.GenomeInterval> intervals = new ArrayList<>();
        genomeIntervals.forEach(interval -> intervals.add(toProtoGenomeInterval(interval)));
        return intervals;
    }

    public static JannovarData toJannovarData(JannovarProto.JannovarData protoJannovarData) {
        logger.debug("Converting to jannovar data...");
        ReferenceDictionary referenceDictionary = toReferenceDictionary(protoJannovarData.getReferenceDictionary());
        ImmutableList<TranscriptModel> transcriptModels = protoJannovarData.getTranscriptModelsList()
                .parallelStream()
                .map(toTranscriptModel(referenceDictionary))
                .collect(ImmutableList.toImmutableList());
        logger.debug("Done");
        return new JannovarData(referenceDictionary, transcriptModels);
    }

    private static ReferenceDictionary toReferenceDictionary(JannovarProto.ReferenceDictionary protoRefDict) {
        ReferenceDictionaryBuilder referenceDictionaryBuilder = new ReferenceDictionaryBuilder();
        protoRefDict.getContigNameToIdMap().forEach(referenceDictionaryBuilder::putContigID);
        protoRefDict.getContigIdToNameMap().forEach(referenceDictionaryBuilder::putContigName);
        protoRefDict.getContigIdToLengthMap().forEach(referenceDictionaryBuilder::putContigLength);
        return referenceDictionaryBuilder.build();
    }

    private static Function<JannovarProto.TranscriptModel, TranscriptModel> toTranscriptModel(ReferenceDictionary referenceDictionary) {
        return protoTranscriptModel -> new TranscriptModel(
                protoTranscriptModel.getAccession(),
                protoTranscriptModel.getGeneSymbol(),
                toGenomeInterval(referenceDictionary, protoTranscriptModel.getTxRegion()),
                toGenomeInterval(referenceDictionary, protoTranscriptModel.getCdsRegion()),
                toExonRegions(referenceDictionary, protoTranscriptModel.getExonRegionsList()),
                protoTranscriptModel.getSequence(),
                protoTranscriptModel.getGeneID(),
                protoTranscriptModel.getTranscriptSupportLevel(),
                protoTranscriptModel.getAltGeneIdsMap()
                // TODO - add new Alignment and Anchor messages to jannovar.proto
//                toAlignment(protoTranscriptModel.getAlignment());
        );
    }

    private static GenomeInterval toGenomeInterval(ReferenceDictionary refDict, JannovarProto.GenomeInterval protoGenomeInterval) {
        return new GenomeInterval(
                refDict,
                (protoGenomeInterval.getStrand() == JannovarProto.Strand.FWD) ? Strand.FWD : Strand.REV,
                protoGenomeInterval.getChr(),
                protoGenomeInterval.getBeginPos(),
                protoGenomeInterval.getEndPos()
        );
    }

    private static ImmutableList<GenomeInterval> toExonRegions(ReferenceDictionary refDict, List<JannovarProto.GenomeInterval> genomeIntervals) {
        ImmutableList.Builder<GenomeInterval> intervals = ImmutableList.builder();
        genomeIntervals.forEach(interval -> intervals.add(toGenomeInterval(refDict, interval)));
        return intervals.build();
    }
}
