package de.mpicbg.scf.spotcoloc;

import fiji.plugin.trackmate.Spot;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.awt.*;
import java.util.ArrayList;

/**
 * Static utility functions
 */
public class Utils {
    /**
     * Extracts a single channel of imp and returns it as imglib2 RandomAccessibleInterval img.
     *
     * @param imp       2D or 3D, single or multi-channel. single time point (multi-timepoint not tested!).
     * @param channelnr channel number. counter starts at 1. If the input has single channel, channelnr is ignored.
     * @return img: single channel img. or null if input was multichannel and the channelnr does not exist
     */
    public static <T extends RealType<T>> RandomAccessibleInterval<T> extractSingleChannelImg(ImagePlus imp, int channelnr) {

        // CAREFUL: Dimension handling in imglib2:
        // Img/RandomAccessibleInterval dimension order is xyczt,
        // but if a dimension is missing it is simply skipped(!): e.g. without c&t: xyz, without z&t: xyc
        // therefore information on nchannels, nslices, .. is lost during wrapping

        int nChannels = imp.getNChannels();

        // convert to imglib2 image
        RandomAccessibleInterval<T> img = ImageJFunctions.wrapReal(imp);

        // extract single channel
        if (nChannels > 1) {
            if (channelnr <= nChannels) {
                img = Views.hyperSlice(img, 2, channelnr - 1); // xyc(zt)
            } else {
                IJ.log("Error: channel " + channelnr + " does not exist in multichannel image.");
                return null;
            }
        } else if (channelnr > 1) {
            IJ.log("Warning: Image has only single channel but a higher channel number was selected. Ignoring channel selection.");
        }

        return img;
    }


    /**
     * Crops img to the bounding box spanned by the roi of imp. If no roi, does nothing.
     * img must be the extracted single(!) channel of imp (see extractSingleChannelImg).
     * @param img result of extractSingleChannelImg(imp, ...)
     * @param imp 2D or 3D, single or multi-channel. single time point (multi-timepoint not tested!).
     * @return interval: img cropped to the roi bounding rect, or not modified if no roi exists.
     */
    public static <T extends RealType<T>> RandomAccessibleInterval<T> restrictSingleChannelImgToRoiRect(RandomAccessibleInterval<T> img, ImagePlus imp) {
        RandomAccessibleInterval<T> interval = img;

        Roi roi = imp.getRoi();
        if (roi != null) {
            Rectangle bounds = roi.getBounds();
            long[] istart = new long[]{bounds.x, bounds.y, 0, 0}; // xy(zt). single channel
            long[] iend = new long[]{bounds.x + bounds.width, bounds.y + bounds.height,
                    imp.getNSlices() - 1, imp.getNFrames() - 1}; // xy(z,t)

            interval = Views.interval(img, istart, iend);
        }

        return interval;
    }
}

