package org.jcodec.containers.mp4;

import static java.util.Arrays.copyOfRange;

import org.jcodec.containers.mp4.boxes.AudioSampleEntry;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.ChunkOffsets64Box;
import org.jcodec.containers.mp4.boxes.ChunkOffsetsBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.SampleDescriptionBox;
import org.jcodec.containers.mp4.boxes.SampleSizesBox;
import org.jcodec.containers.mp4.boxes.SampleToChunkBox;
import org.jcodec.containers.mp4.boxes.SampleToChunkBox.SampleToChunkEntry;
import org.jcodec.containers.mp4.boxes.TimeToSampleBox;
import org.jcodec.containers.mp4.boxes.TimeToSampleBox.TimeToSampleEntry;
import org.jcodec.containers.mp4.boxes.TrakBox;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author The JCodec project
 *
 */
public class ChunkReader {
    private int curChunk;
    private int sampleNo;
    private int s2cIndex;
    private int ttsInd = 0;
    private int ttsSubInd = 0;
    private long chunkTv = 0;
    private long[] chunkOffsets;
    private SampleToChunkEntry[] sampleToChunk;
    private SampleSizesBox stsz;
    private TimeToSampleEntry[] tts;
    private SampleDescriptionBox stsd;

    public ChunkReader(TrakBox trakBox) {
        TimeToSampleBox stts = NodeBox.findFirst(trakBox, TimeToSampleBox.class, "mdia", "minf", "stbl", "stts");
        tts = stts.getEntries();
        ChunkOffsetsBox stco = NodeBox.findFirst(trakBox, ChunkOffsetsBox.class, "mdia", "minf", "stbl", "stco");
        ChunkOffsets64Box co64 = NodeBox.findFirst(trakBox, ChunkOffsets64Box.class, "mdia", "minf", "stbl", "co64");
        stsz = NodeBox.findFirst(trakBox, SampleSizesBox.class, "mdia", "minf", "stbl", "stsz");
        SampleToChunkBox stsc = NodeBox.findFirst(trakBox, SampleToChunkBox.class, "mdia", "minf", "stbl", "stsc");

        if (stco != null)
            chunkOffsets = stco.getChunkOffsets();
        else
            chunkOffsets = co64.getChunkOffsets();
        sampleToChunk = stsc.getSampleToChunk();
        stsd = NodeBox.findFirst(trakBox, SampleDescriptionBox.class, "mdia", "minf", "stbl", "stsd");
    }

    public boolean hasNext() {
        return curChunk < chunkOffsets.length;
    }

    public Chunk next() {
        if (curChunk >= chunkOffsets.length)
            return null;

        if (s2cIndex + 1 < sampleToChunk.length && curChunk + 1 == sampleToChunk[s2cIndex + 1].getFirst())
            s2cIndex++;
        int sampleCount = sampleToChunk[s2cIndex].getCount();

        int[] samplesDur = null;
        int sampleDur = 0;
        if (ttsSubInd + sampleCount <= tts[ttsInd].getSampleCount()) {
            sampleDur = tts[ttsInd].getSampleDuration();
            ttsSubInd += sampleCount;
        } else {
            samplesDur = new int[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                if (ttsSubInd >= tts[ttsInd].getSampleCount() && ttsInd < tts.length - 1) {
                    ttsSubInd = 0;
                    ++ttsInd;
                }
                samplesDur[i] = tts[ttsInd].getSampleDuration();
                ++ttsSubInd;
            }
        }

        int size = 0;
        int[] sizes = null;
        if (stsz.getDefaultSize() > 0) {
            size = getFrameSize();
        } else {
            sizes = copyOfRange(stsz.getSizes(), sampleNo, sampleNo + sampleCount);
        }

        int dref = sampleToChunk[s2cIndex].getEntry();
        Chunk chunk = new Chunk(chunkOffsets[curChunk], chunkTv, sampleCount, size, sizes, sampleDur, samplesDur, dref);

        chunkTv += chunk.getDuration();
        sampleNo += sampleCount;
        ++curChunk;
        return chunk;
    }

    private int getFrameSize() {
        int size = stsz.getDefaultSize();
        Box box = stsd.getBoxes().get(sampleToChunk[s2cIndex].getEntry() - 1);
        if (box instanceof AudioSampleEntry) {
            return ((AudioSampleEntry) box).calcFrameSize();
        }
        return size;
    }

    public int size() {
        return chunkOffsets.length;
    }
}