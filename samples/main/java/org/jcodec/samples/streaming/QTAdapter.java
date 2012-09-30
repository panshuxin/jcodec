package org.jcodec.samples.streaming;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;

import org.jcodec.common.io.AutoRandomAccessFileInputStream;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Rational;
import org.jcodec.common.model.Size;
import org.jcodec.containers.mp4.MP4Demuxer;
import org.jcodec.containers.mp4.MP4Demuxer.DemuxerTrack;
import org.jcodec.containers.mp4.boxes.AudioSampleEntry;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.PixelAspectExt;
import org.jcodec.containers.mp4.boxes.SampleEntry;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.jcodec.containers.mp4.boxes.VideoSampleEntry;
import org.jcodec.player.filters.MediaInfo;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * Streaming adaptor for Quicktime movies
 * 
 * @author The JCodec project
 * 
 */
public class QTAdapter implements Adapter {
    private MP4Demuxer demuxer;
    private ArrayList<AdapterTrack> tracks;

    public QTAdapter(File file) throws IOException {
        demuxer = new MP4Demuxer(new AutoRandomAccessFileInputStream(file));
        tracks = new ArrayList<AdapterTrack>();
        for (DemuxerTrack demuxerTrack : demuxer.getTracks()) {
            if (demuxerTrack.getBox().isAudio())
                tracks.add(new QTAudioAdaptorTrack(demuxerTrack));
            else if (demuxerTrack.getBox().isVideo())
                tracks.add(new QTVideoAdaptorTrack(demuxerTrack));
        }
    }

    @Override
    public AdapterTrack getTrack(int trackNo) {
        return tracks.get(trackNo);
    }

    @Override
    public List<AdapterTrack> getTracks() {
        return tracks;
    }

    public static class QTVideoAdaptorTrack implements VideoAdapterTrack {
        protected DemuxerTrack demuxerTrack;

        public QTVideoAdaptorTrack(DemuxerTrack demuxerTrack) {
            this.demuxerTrack = demuxerTrack;
        }

        public synchronized int search(long pts) throws IOException {
            if (!demuxerTrack.seek(pts))
                return -1;
            return (int) demuxerTrack.getCurFrame();
        }

        @Override
        public MediaInfo getMediaInfo() {
            TrakBox box = demuxerTrack.getBox();
            VideoSampleEntry vse = (VideoSampleEntry) box.getSampleEntries()[0];
            PixelAspectExt pasp = Box.findFirst(vse, PixelAspectExt.class, "pasp");

            Rational r = new Rational(1, 1);
            if (pasp != null) {
                r = pasp.getRational();
            }
            return new MediaInfo.VideoInfo(vse.getFourcc(), box.getTimescale(), box.getMediaDuration(),
                    box.getFrameCount(), r, new Size(vse.getWidth(), vse.getHeight()));

        }

        @Override
        public Packet[] getGOP(int gopId) throws IOException {
            return null;
        }
    }

    public static class QTAudioAdaptorTrack implements AudioAdapterTrack {
        private static final int FRAMES_PER_PCM_PACKET = 2048;

        protected DemuxerTrack demuxerTrack;

        public QTAudioAdaptorTrack(DemuxerTrack demuxerTrack) {
            this.demuxerTrack = demuxerTrack;
        }

        private Packet getFrameNonPCM(int frameNo) throws IOException {
            if (demuxerTrack.getCurFrame() != frameNo)
                demuxerTrack.gotoFrame(frameNo);
            return demuxerTrack.getFrames(1);
        }

        private int searchFramePCM(long pts) throws IOException {
            AudioFormat format = ((AudioSampleEntry) demuxerTrack.getSampleEntries()[0]).getFormat();
            long frameNo = ((pts * (int) format.getFrameRate()) / demuxerTrack.getTimescale() + FRAMES_PER_PCM_PACKET - 1)
                    & ~0x7ff;
            demuxerTrack.gotoFrame(frameNo);

            return (int) (demuxerTrack.getCurFrame() >> 11);
        }

        private Packet getFramePCM(int frameNo) throws IOException {
            frameNo <<= 11;
            if (demuxerTrack.getCurFrame() != frameNo)
                demuxerTrack.gotoFrame(frameNo);
            Packet packet = demuxerTrack.getFrames(FRAMES_PER_PCM_PACKET);
            if (packet == null)
                return null;
            return new Packet(packet.getData(), packet.getPts(), packet.getTimescale(), packet.getDuration(),
                    packet.getFrameNo() >> 11, packet.isKeyFrame(), packet.getTapeTimecode());
        }

        @Override
        public MediaInfo getMediaInfo() {
            TrakBox box = demuxerTrack.getBox();

            AudioSampleEntry se = (AudioSampleEntry) box.getSampleEntries()[0];
            boolean pcm = isPCM(box);
            return new MediaInfo.AudioInfo(se.getFourcc(), box.getTimescale(), box.getMediaDuration(),
                    (box.getFrameCount() >> (pcm ? 11 : 0)), se.getFormat(), pcm ? FRAMES_PER_PCM_PACKET : 1);
        }

        private boolean isPCM(DemuxerTrack track) {
            SampleEntry se = track.getSampleEntries()[0];
            return (se instanceof AudioSampleEntry) && ((AudioSampleEntry) se).isPCM();
        }

        private boolean isPCM(TrakBox track) {
            SampleEntry se = track.getSampleEntries()[0];
            return (se instanceof AudioSampleEntry) && ((AudioSampleEntry) se).isPCM();
        }

        public synchronized int search(long pts) throws IOException {
            if (isPCM(demuxerTrack)) {
                return searchFramePCM(pts);
            } else {
                if (!demuxerTrack.seek(pts))
                    return -1;
                return (int) demuxerTrack.getCurFrame();
            }
        }

        @Override
        public synchronized Packet getFrame(int frameId) throws IOException {
            if (isPCM(demuxerTrack)) {
                return getFramePCM(frameId);
            } else {
                return getFrameNonPCM(frameId);
            }
        }
    }
}