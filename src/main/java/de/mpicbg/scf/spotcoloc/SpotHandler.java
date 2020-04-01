// TODO: avoid multiple time points? -> catch it?
// make functions static?

package de.mpicbg.scf.spotcoloc;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.LogDetector;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import java.awt.*;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpotHandler <T extends RealType<T>>{
    /**
     * Finds spots in a single channel of an image. For spot detection the trackmate LoG detector is used.
     * If the input image has a roi, then spot detection is restricted to this region.
     * @param imp image with at least one channel, 2D or 3D
     * @param channel which channel to use. count starts at 1
     * @param radius_um Spot radius in um (for LoGDetector).
     * @param threshold Quality threshold for Log detector. `threshold` is first scaled with the heuristic
     *                 radius_um^3 (3D) or radius_um^2 (2D) before usage as cutoff in the LoGDetector.
     *                  Purpose: Decrease dependency of threshold value on radius. The used multiplication factor
     *                  is a heuristic and not perfect.
     * @param doSubpixel for LoG Detector
     * @param doMedian for LogDetector
     */
    public List detectSpots(ImagePlus imp, int channel, double radius_um, double threshold, boolean doSubpixel,
                            boolean doMedian) {
        // (adapted from https://imagej.net/Scripting_TrackMate)

        // CAREFUL: Dimension handling in imglib2:
        // Img/RandomAccessibleInterval dimension order is xyczt,
        // but if a dimension is missing it is simply skipped(!): e.g. without c&t: xyz, without z&t: xyc
        // therefore information on nchannels, nslices, .. is lost during wrapping


        // TODO: move the channel extraction to a separate function?
        int nChannels = imp.getNChannels();
        int nSlices = imp.getNSlices();
        int nFrames=imp.getNFrames();

        // convert to imglib2 image
        RandomAccessibleInterval<T> img = ImageJFunctions.wrapReal(imp); // TODO name rai?

        // extract single channel
        if (nChannels>1) {
            if (channel <= nChannels) {
                img = Views.hyperSlice(img, 2, channel - 1); // xyc(zt)
            } else {
                IJ.log("Error: channel " + channel + " does not exist.");
                return new ArrayList<Spot>(); //TODO check this
            }
        }
        else if (channel>1) {
            IJ.log("Warning: Image has only single channel but a higher channel number was selected. Ignoring channel selection.");
        }

        // restrict to roi region (bounding box).
        RandomAccessibleInterval<T> interval=img;

        Roi roi = imp.getRoi();
        if (roi!=null) {
            Rectangle bounds = roi.getBounds();
            long[] istart = new long[]{bounds.x, bounds.y, 0, 0}; // xy(zt). single channel
            long[] iend = new long[]{bounds.x+bounds.width, bounds.y+bounds.height, nSlices - 1, nFrames - 1}; // xy(z,t)

            interval = Views.interval(img, istart, iend);
        }


        // get calibration
        Calibration calib = imp.getCalibration();
        double[] calibration = new double[] {calib.pixelWidth, calib.pixelHeight, calib.pixelDepth};
        System.out.println("Image calibration: "+Arrays.toString(calibration));

        // heuristic scaling of threshold to reduce sensitivity to the used radius. normalized to radius=1m
        if (nSlices>1) {
            threshold=threshold/(radius_um*radius_um*radius_um); // * or / anisotropy (in future?)?
        }
        else {
            threshold=threshold/(radius_um*radius_um);
        }

        //	Setup spot detector (see http://javadoc.imagej.net/Fiji/fiji/plugin/trackmate/detection/LogDetector.html)
        // LogDetector( RandomAccessible, Interval, ...) // blubb
       // LogDetector detector = new LogDetector(img, img, calibration, radius_um, threshold, doSubpixel, doMedian);
        LogDetector detector = new LogDetector(img, interval, calibration, radius_um, threshold, doSubpixel, doMedian);

        // prepare final result
        List<Spot> spots = new ArrayList<>();

        // == Detect the spots ==
        if (detector.process()) {
            // Get the array of spots found
            List<Spot> spotsraw = detector.getResult();

            IJ.log("\nSpot detection in Channel " + channel + " finished.");
            IJ.log("Spot detection found " + spotsraw.size() + " spots (in full image, resp. roi bounding rectangle if roi exists.)");

            // Filter spots by ROI
            if (roi == null) {
                spots = spotsraw;
            } else {
                // check if pixel position within roi
                double[] position;

                for (Spot currentspot : spotsraw) {
                    System.out.println(currentspot);
                    position = getPositionPx(currentspot, calib);
                    if (roi.contains((int) position[0], (int) position[1])) {
                        spots.add(currentspot);
                    }
                }
                IJ.log("Filter spots by ROI region: kept " + spots.size() + " spots.");
            }
        }
        // otherwise:  detection failed
        else {
            System.out.println("The detector could not process the data.");
        }

        return spots;
    }


    /**
     * Returns x,y,z position of spot object, in pixel units
     * @param spot Trackmate spot
     * @param calib ImageJ calibration object (imp.getCalibration())
     * @return [x,y,z] array
     */
    public static double[] getPositionPx(Spot spot, Calibration calib) {
        double[] position = new double[3];
        System.out.println(spot.echo());
        position[0] = spot.getDoublePosition(0) / calib.pixelWidth;
        position[1] = spot.getDoublePosition(1) / calib.pixelHeight;
        position[2] = spot.getDoublePosition(2) / calib.pixelDepth;
        return position;
    }
}
