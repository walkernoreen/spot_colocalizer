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
     * @param channelnr which channel to use. count starts at 1
     * @param radius_um Spot radius in um (for LoGDetector).
     * @param threshold Quality threshold for Log detector. `threshold` is first scaled with the heuristic
     *                 radius_um^3 (3D) or radius_um^2 (2D) before usage as cutoff in the LoGDetector.
     *                  Purpose: Decrease dependency of threshold value on radius. The used multiplication factor
     *                  is a heuristic and not perfect.
     * @param doSubpixel for LoG Detector
     * @param doMedian for LogDetector
     */
    public List detectSpots(ImagePlus imp, int channelnr, double radius_um, double threshold, boolean doSubpixel,
                            boolean doMedian) {
        // initialize result
        List<Spot> spots = new ArrayList<>();

        // extract single channel for spot detection
        RandomAccessibleInterval<T> img = Utils.extractSingleChannelImg(imp, channelnr);
        if (img==null) { return new ArrayList<Spot>();} // channel didn't exist

        // restrict channel to roi region (bounding box).
        RandomAccessibleInterval<T> interval = Utils.restrictSingleChannelImgToRoiRect(img, imp);


        // get image calibration
        Calibration calib = imp.getCalibration();
        double[] calibration = new double[] {calib.pixelWidth, calib.pixelHeight, calib.pixelDepth};

        // heuristic scaling of threshold to reference radius=1um
        if (imp.getNSlices()>1) {
            threshold=threshold/(radius_um*radius_um*radius_um); // * or / anisotropy (in future?)?
        }
        else {
            threshold=threshold/(radius_um*radius_um);
        }


        // == Detect the spots ==
        LogDetector detector = new LogDetector(img, interval, calibration, radius_um, threshold, doSubpixel, doMedian);

        if (detector.process()) {
            // Get the list of detected spots
            List<Spot> spotsraw = detector.getResult();
            IJ.log("\nSpot detection in Channel " + channelnr + " finished.");
            IJ.log("Spot detection found " + spotsraw.size() + " spots (in full image, resp. roi bounding rectangle if roi exists.)");

            // Filter spots by ROI
            Roi roi = imp.getRoi();
            if (roi == null) {
                spots = spotsraw;
            } else {
                // check if spot pixel position within roi
                double[] position;
                for (Spot currentspot : spotsraw) {
                    position = getPositionPx(currentspot, calib);
                    if (roi.contains((int) position[0], (int) position[1])) {
                        spots.add(currentspot);
                    }
                }
                IJ.log("Filter spots by roi region: kept " + spots.size() + " spots.");
            }
        }
        else {
            IJ.log("The detector could not process the data.");
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
        position[0] = spot.getDoublePosition(0) / calib.pixelWidth;
        position[1] = spot.getDoublePosition(1) / calib.pixelHeight;
        position[2] = spot.getDoublePosition(2) / calib.pixelDepth;
        return position;
    }
}
