package org.jcodec.scale;

import org.jcodec.common.model.Picture;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author The JCodec project
 * 
 */
public class RgbToYuv422 implements Transform {

    private int upShift;
    private int downShift;
    private int downShiftChr;

    public RgbToYuv422(int upShift, int downShift) {
        this.upShift = upShift;
        this.downShift = downShift;
        this.downShiftChr = downShift + 1;
    }

    public void transform(Picture img, Picture dst) {

        int[] y = img.getData()[0];
        int[][] dstData = img.getData();

        int off = 0, offSrc = 0;
        for (int i = 0; i < img.getHeight(); i++) {
            for (int j = 0; j < img.getWidth() >> 1; j++) {
                int offY = off << 1;

                RgbToYuv420.rgb2yuv(y[offSrc++], y[offSrc++], y[offSrc++], dstData[0], offY, dstData[1], off,
                        dstData[2], off);
                dstData[0][offY] = (dstData[0][offY] << upShift) >> downShift;

                RgbToYuv420.rgb2yuv(y[offSrc++], y[offSrc++], y[offSrc++], dstData[0], offY + 1, dstData[1], off,
                        dstData[2], off);
                dstData[0][offY + 1] = (dstData[0][offY + 1] << upShift) >> downShift;

                dstData[1][off] = (dstData[1][off] << upShift) >> downShiftChr;
                dstData[2][off] = (dstData[2][off] << upShift) >> downShiftChr;
                ++off;
            }
        }
    }
}