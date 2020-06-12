// TODO: avoid multiple time points? -> catch it?
// make functions static?

package de.mpicbg.scf.spotcoloc;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.LogDetector;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.text.TextWindow;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.round;
import static java.lang.Math.sqrt;

public class SpotColocalizer{
    // TODO: or name it SpotProcessor? Make all the channel names + thresholds field variables?

    // 2D or 3D, single time point. for colocalization: at least 2 channels
    private ImagePlus imp;

    final String titleResultsTable="ResultsSpotColocalization";

    // TODO how to handle doSubPixel, doMedian . member, or function argument?

    public SpotColocalizer(final ImagePlus inputImp) {
        imp=inputImp;

        sanityChecks();
    }


    private void sanityChecks() { // TODO more
        // single time point
        if (imp.getNFrames()>1) {
            IJ.error("Spot Colocalizer", "Image must be a single time point.");
        }
    }


    /**
     * Finds spots in a single channel of an image. For spot detection the trackmate LoG detector is used.
     * If the input image has a roi, then spot detection is restricted to this region.
     * @param channelnr which channel to use. count starts at 1
     * @param radius_um Spot radius in um (for LoGDetector).
     * @param threshold Quality threshold for Log detector. `threshold` is first scaled with the heuristic
     *                 radius_um^3 (3D) or radius_um^2 (2D) before usage as cutoff in the LoGDetector.
     *                  Purpose: Decrease dependency of threshold value on radius. The used multiplication factor
     *                  is a heuristic and not perfect.
     * @param doSubpixel for LoG Detector
     * @param doMedian for LogDetector
     */
    public <T extends RealType<T>> List<Spot> detectSpots(int channelnr, double radius_um, double threshold,
                                                          boolean doSubpixel, boolean doMedian) {
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

        try {
            if (detector.process()) {
                // Get the list of detected spots
                List<Spot> spotsraw = detector.getResult();
                IJ.log("\nSpot detection in Channel " + channelnr + " finished.");
                IJ.log("Found " + spotsraw.size() + " spots (in bounding box around roi, or full image if no roi)");

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
            } else {
                IJ.log("The detector could not process the data.");
            }
        }
        catch (ArrayIndexOutOfBoundsException e) {
            IJ.log("The detector could not process the data: Roi outside of image");
        }

        return spots;
    }


    /**
     *  Detects spots and visualizes them as overlay. Useful for previewing. Requires multichannel image.
     * @param previewChA whether to process channel A. if false, all further 'A' paramters are ignored
     * @param previewChB whether to process channel B. if false, all further 'B' paramters are ignored
     * @param channelA number of channel A (1,2,3,..)
     * @param radiusA_um radius of spots in channel A (um)
     * @param thresholdA quality threshold in channel A
     * @param channelB number of channel B
     * @param radiusB_um see above
     * @param thresholdB see above
     * @param doSubPixel LogDetector input
     * @param doMedian LogDetector input
     */
    public void generateDetectionPreviewMultiChannel(boolean previewChA, boolean previewChB,
                                                     int channelA, double radiusA_um, double thresholdA,
                                                     int channelB, double radiusB_um, double thresholdB,
                                                     boolean doSubPixel, boolean doMedian) {
        Overlay ov = new Overlay();

        if (previewChA) {
            List<Spot> spotsA = detectSpots(channelA, radiusA_um, thresholdA, doSubPixel, doMedian);
            ov = createOverlayOfSpots(spotsA, ov, Color.magenta);
        }

        if (previewChB) {
            List<Spot> spotsB = detectSpots(channelB, radiusB_um, thresholdB, doSubPixel, doMedian);
            ov = createOverlayOfSpots(spotsB, ov, Color.green);
        }

        imp.setOverlay(ov);
    }


    /**
     * Quantifies which spots in the lists spotsA & spotsB are colocalized. Spots from the two channels are considered
     * 	colocalized if their centers are closer than 'maxdist_um' apart.
     * 	Each spot in channelA is matched to the closest one from channelB, but it is taken care that a channelB spot
     * 	cannot be paired with two channelA spots (could happen if channelA spots are very dense).
     * 	Avoiding double-assignment is done with a greedy algorithm: If a chB spot is the closest to two chA spots,
     * 	always the first chA spot in the loop is used for the pairing.
     * 	This assignment strategy is not globally optimal but should be more than sufficient since spots are usually
     * 	much sparser distributed in a colocalization study.
     * 	A colocalized spot is placed at the average position and with the average radius of the corresponding spot-pair.
     * @param spotsA from detectSpots(...)
     * @param spotsB from detectSpots(...), different channel
     * @param maxdist_um Maximum distance (in um) between spot centers to still be considered colocalized. Typically 1.0*spotradius
     * @return ColocResult with list of non-colocalized and colocalized spots
     */
    public ColocResult findSpotCorrespondences(List<Spot> spotsA, List<Spot> spotsB, double maxdist_um) {
        //TODO some LOGwindow output

        // work with squared distances
        double maxdist2 = maxdist_um * maxdist_um;

        int numspotsA = spotsA.size();
        int numspotsB = spotsB.size();

        // collect spot coordinates in arrays
        double[][] positionsA = new double[numspotsA][3]; // Nx3
        for (int i = 0; i < numspotsA; i++) {
            positionsA[i] = getPositionCalib(spotsA.get(i));
        }

        double[][] positionsB = new double[numspotsB][3]; // Nx3
        for (int i = 0; i < numspotsB; i++) {
            positionsB[i] = getPositionCalib(spotsB.get(i));
        }

        // ===== do pair matching =====
        ArrayList<Integer> idsBurnedSpotsB = new ArrayList<>(); // track the already used ids of spotsB

        // initialize to track ids of colocalized spots
        // spotB ids. spotsBPartnersOfSpotsA[4]=6 means: spotsA[4] corresponds to spotsB[6]
        Integer[] spotsBPartnersOfSpotsA = new Integer[numspotsA];
        for (int i = 0; i < spotsBPartnersOfSpotsA.length; i++) {
            spotsBPartnersOfSpotsA[i] = null;
        }
        // spotsA ids
        Integer[] spotsAPartnersOfSpotsB = new Integer[numspotsB];
        for (int i = 0; i < spotsAPartnersOfSpotsB.length; i++) {
            spotsAPartnersOfSpotsB[i] = null;
        }

        // loop over spotsA and find the closest spot in spotsB
        for (int idxA = 0; idxA < numspotsA; idxA++) {
            double[] currentposA = positionsA[idxA];

            // initialize for finding spot pair
            double mindist2 = Double.POSITIVE_INFINITY; // squared distance
            Integer idColocA = null; // spotsA id which is a match: null or idxA
            Integer idColocB = null; // spotsB id which is a match

            for (int idxB = 0; idxB < numspotsB; idxB++) {
                // greedy algorithm: avoid double assignment of same spotB
                if (idsBurnedSpotsB.contains(idxB)) {
                    continue;
                }

                double[] currentposB = positionsB[idxB];

                double currentdist2 = Math.pow(currentposA[0] - currentposB[0], 2) + Math.pow(currentposA[1] - currentposB[1], 2) +
                        Math.pow(currentposA[2] - currentposB[2], 2);

                // check if new best match was found
                if (currentdist2 <= maxdist2 && currentdist2 < mindist2) {
                    mindist2 = currentdist2;
                    idColocA = idxA;
                    idColocB = idxB;
                }
            }

            // collect ids if match was found
            if (idColocB != null) {
                spotsBPartnersOfSpotsA[idColocA] = idColocB;
                spotsAPartnersOfSpotsB[idColocB] = idColocA;

                idsBurnedSpotsB.add(idColocB);
            }
        }

        // === postprocess: split spots in coloc and non-coloc ===
        // non-colocalized spotsA
        List<Spot> spotsA_noncoloc = new ArrayList<>();
        for (int i = 0; i < numspotsA; i++) {
            if (spotsBPartnersOfSpotsA[i] == null) {
                spotsA_noncoloc.add(spotsA.get(i));
            }
        }

        // non-colocalized spotsB
        List<Spot> spotsB_noncoloc = new ArrayList<>();
        for (int i = 0; i < numspotsB; i++) {
            if (spotsAPartnersOfSpotsB[i] == null) {
                spotsB_noncoloc.add(spotsB.get(i));
            }
        }

        // colocalized spots
        List<Spot> spots_coloc = new ArrayList<>();
        for (int i = 0; i < numspotsA; i++) {
            if (spotsBPartnersOfSpotsA[i] != null) {
                // create a new spot at the average position and with average radius
                Spot spotA = spotsA.get(i);
                Spot spotB = spotsB.get(spotsBPartnersOfSpotsA[i]);

                double[] posA = getPositionCalib(spotA);
                double[] posB = getPositionCalib(spotB);
                Double radA = spotA.getFeature(Spot.RADIUS);
                Double radB = spotB.getFeature(Spot.RADIUS);

                Spot spotAvg = new Spot(0.5 * (posA[0] + posB[0]), 0.5 * (posA[1] + posB[1]), 0.5 * (posA[2] + posB[2]),
                        0.5 * (radA + radB), -1);
                spots_coloc.add(spotAvg);
            }
        }

        IJ.log("\nFinished computing colocalization between spots.\n");

        return new ColocResult(spotsA_noncoloc, spotsB_noncoloc, spots_coloc);
    }


    /** Full colocalization pipeline. Intented to be used by high level plugins.
     * Does spot detection in 2 channels, finds spot correspondendences, displays results as overlay and as counts
     * in results table.
     * @param channelA number of channel A (1,2,3,..)
     * @param radiusA_um radius of spots in channel A (um)
     * @param thresholdA quality threshold in channel A
     * @param channelB number of channel B
     * @param radiusB_um see above
     * @param thresholdB see above
     * @param doSubPixel LogDetector input
     * @param doMedian LogDetector input
     * @param clearTable
     */
    public void runFullColocalizationAnalysis(int channelA, double radiusA_um, double thresholdA,
                                              int channelB, double radiusB_um, double thresholdB,
                                              double distanceFactorColoc, boolean doSubPixel, boolean doMedian,
                                              boolean clearTable) {

        // find spots
        List<Spot> spotsA = detectSpots(channelA, radiusA_um,thresholdA, doSubPixel, doMedian);
        List<Spot> spotsB = detectSpots(channelB,radiusB_um, thresholdB, doSubPixel, doMedian);

        // detect which spots are colocalized
        double maxdist_um = 0.5 * (radiusA_um + radiusB_um) * distanceFactorColoc;
        ColocResult CR = findSpotCorrespondences(spotsA, spotsB, maxdist_um);

        // create visualization overlay
        Overlay ov = createOverlayOfSpots(CR.spotsA_noncoloc, Color.red);
        ov=createOverlayOfSpots(CR.spots_coloc, ov,Color.yellow);
        ov=createOverlayOfSpots(CR.spotsB_noncoloc, ov,Color.green);

        // add roi to overlay
        Roi roi = imp.getRoi();
        if (roi!=null) {
            roi.setStrokeColor(Color.white);
            ov.add(roi);
        }

        imp.setOverlay(ov);

        //display counts in results table
        ResultsTable rt = fillResultsTable(channelA, channelB, spotsA, spotsB, CR.spotsA_noncoloc,
                CR.spotsB_noncoloc, CR.spots_coloc, clearTable);
        rt.show(titleResultsTable);

    }


    /**
     * Adds all counts of a colocalization analysis (spot detections, colocalized count etc.) to a results table
     * (with custom title). Grabs the open table if available, otherwise creates a new one. Previous results can
     * optionally be cleared.
     * @param channelA which channel id, for this and most other parameters, see runFullColocalizationAnalyis(...)
     * @param channelB
     * @param spotsA
     * @param spotsB
     * @param spotsA_noncoloc
     * @param spotsB_noncoloc
     * @param spots_coloc
     * @param clearTable if True, table is emptied before new valuea are added
     * @return
     */
    private ResultsTable fillResultsTable(int channelA, int channelB, List<Spot> spotsA, List<Spot> spotsB,
                                          List<Spot> spotsA_noncoloc, List<Spot> spotsB_noncoloc, List<Spot> spots_coloc,
                                          boolean clearTable) {

        // add counts to results table
        TextWindow window = (TextWindow) WindowManager.getWindow(titleResultsTable);
        ResultsTable rt;
        if (window!=null) {
            rt = window.getTextPanel().getResultsTable();
        } else {
            rt = new ResultsTable();
        }

        if (clearTable) {
            rt.reset();
        }

        rt.setPrecision(4);

        String descrA = "ch "+channelA;
        String descrB="ch "+channelB;

        rt.incrementCounter();
    rt.addLabel(imp.getTitle());
        rt.addValue("Total " + descrA, spotsA.size());
        rt.addValue("Total " + descrB, spotsB.size());
        rt.addValue("Coloc ", spots_coloc.size());
        rt.addValue("Not coloc "+descrA, spotsA_noncoloc.size());
        rt.addValue("Not coloc "+descrB, spotsB_noncoloc.size());
        if (spotsA.size()>0) {
            rt.addValue("Fraction coloc "+descrA, spots_coloc.size()/ (float) spotsA.size());
        }
        else {
            rt.addValue("Fraction coloc " + descrA, Double.NaN);
        }
        if (spotsB.size()>0) {
            rt.addValue("Fraction coloc " + descrB, spots_coloc.size() /(float) spotsB.size());
        }
        else {
            rt.addValue("Fraction coloc " + descrB, Double.NaN);
        }

        return rt;
    }




    /**
     * Little helper class to move the lists of noncolocalized & colocalized spots around
     */
    public static class ColocResult {
        List<Spot> spotsA_noncoloc;
        List<Spot> spotsB_noncoloc;
        List<Spot> spots_coloc;

        ColocResult(List<Spot> spotsA_non, List<Spot> spotsB_non, List<Spot> spots_col) {
            spotsA_noncoloc=spotsA_non;
            spotsB_noncoloc=spotsB_non;
            spots_coloc=spots_col;
        }
    }


    /**
     * Returns x,y,z position of spot object, in pixel units
     * @param spot Trackmate spot
     * @param calib ImageJ calibration object (imp.getCalibration())
     * @return [x,y,z] array in px
     */
    public static double[] getPositionPx(Spot spot, Calibration calib) {
        double[] position = new double[3];
        position[0] = spot.getDoublePosition(0) / calib.pixelWidth;
        position[1] = spot.getDoublePosition(1) / calib.pixelHeight;
        position[2] = spot.getDoublePosition(2) / calib.pixelDepth;
        return position;
    }


    /**
     * Returns x,y,z position of spot object, in um units
     * @param spot Trackmate spot
     * @return [x,y,z] array in um
     */
    public static double[] getPositionCalib(Spot spot) {
        double[] position = new double[3];
        position[0] = spot.getDoublePosition(0);
        position[1] = spot.getDoublePosition(1);
        position[2] = spot.getDoublePosition(2);
        return position;
    }


    /** Like createOverlayOfSpots(List, double, Overlay, Color) but with default values.
     * Creates new Overlay.
     */
    public Overlay createOverlayOfSpots(List<Spot> spots, double rad_um, Color color) {
        return createOverlayOfSpots(spots, rad_um, new Overlay(), color);
    }

    /** Like createOverlayOfSpots(List, ImagePlus, double, Overlay, Color) but with default values.
     * Creates new Overlay, uses radius from the spots object.
     */
    public Overlay createOverlayOfSpots(List<Spot> spots, Color color) {
        if (spots.size() == 0) return new Overlay();
        double rad_um = spots.get(0).getFeature(Spot.RADIUS);
        return createOverlayOfSpots(spots, rad_um, new Overlay(), color);
    }

    /** Like createOverlayOfSpots(List, double, Overlay, Color) but with default values.
     * Uses radius from the spots object.
     */
    public Overlay createOverlayOfSpots(List<Spot> spots, Overlay ov, Color color) {
        if (spots.size() == 0) return ov;
        double rad_um = spots.get(0).getFeature(Spot.RADIUS);
        return createOverlayOfSpots(spots, rad_um, ov, color);
    }

    /** Like createOverlayOfSpots(List, double, Overlay, Color) but with default values.
     * Creates new Overlay, uses radius from the spots object, uses color red.
     */
    public Overlay createOverlayOfSpots(List<Spot> spots) {
        if (spots.size() == 0) return new Overlay();
        double rad_um = spots.get(0).getFeature(Spot.RADIUS);
        return createOverlayOfSpots(spots, rad_um, new Overlay(), Color.red);
    }



    /** TODO move this to a separate SpotVisualizer static class (the whole family)?.
     * Draws the spots as 3-dimensional spheres/circles into an overlay. Works also for 2D.
     * See also createOverlayOfSpots(..) variants where rad_um, ov and color are optional.
     * Inspired by trackmate spot visualization.
     * Variables in this function are in pixel units unless appended  by um.
     * @param spots list of trackmate spot objects. obtained from detectSpots(..)
     * @param rad_um spot radius in um
     * @param ov existing overlay to which spots will be added.
     * @param color
     * @return ov with added spots
     */
    public Overlay createOverlayOfSpots(List<Spot> spots, double rad_um, Overlay ov, Color color) {
        // pretty plotting
        double xoffset = 0.5; // px (overlay seems shifted when being drawn)
        double yoffset = 0.5;
        double strokewidth = 1; // 0.5 or 1

        // get image properties
        Calibration calib = imp.getCalibration();
        int nChannels = imp.getNChannels();

        // if no spots exist, we're already done
        if (spots.size() == 0) return ov;

        // spot radius in px
        double rad0xy = rad_um / calib.pixelWidth;
        double rad0z = rad_um / calib.pixelDepth;

        // == process all spots ==
        for (Spot spot : spots) {
            // spot center in px
            double[] pos = getPositionPx(spot, calib); //x,y,z

            // draw circle into central slice
            int slice_ctr = (int) round(pos[2] + 1); // slice=z+1
            ov = singleCircleToOverlay(ov, pos[0], pos[1], slice_ctr, rad0xy, nChannels, color, strokewidth, xoffset, yoffset);

            // draw circles into slices above and below
            for (int deltaz = 1; deltaz < rad0z + 2; deltaz++) { //step through slices (circle extends to maximally slice_ctr+-rad0x +=rounding error)
                double radxy;

                //slice shift in um
                double deltaz_um = deltaz * calib.pixelDepth;

                // compute px radius of circle in this slice
                if (deltaz_um < rad_um) {
                    radxy = (sqrt(rad_um * rad_um - deltaz_um * deltaz_um)) / calib.pixelWidth;
                } else {
                    break;
                }

                // draw circle rois
                if (slice_ctr - deltaz > 0) {
                    ov = singleCircleToOverlay(ov, pos[0], pos[1], slice_ctr - deltaz, radxy, nChannels, color, strokewidth, xoffset, yoffset);
                }
                if (slice_ctr + deltaz < imp.getNSlices() + 1) {
                    ov = singleCircleToOverlay(ov, pos[0], pos[1], slice_ctr + deltaz, radxy, nChannels, color, strokewidth, xoffset, yoffset);
                }
            }
        }
        return ov;
    }




    /**
     * Adds a single circle to the overlay. Overlay is added to all channels.
     * See also function variant with default values.
     * @param ov overlay to which circle will be added
     * @param xctr in px
     * @param yctr in px
     * @param slice one-based
     * @param radius in px
     * @param color
     * @param strokewidth
     * @param xoffset shift-correction in x during plotting. xctr -> xtr+xoffset
     * @param yoffset shift-correction in y ...
     * @return overlay with added circle
     */
    private static Overlay singleCircleToOverlay(Overlay ov, double xctr, double yctr, int slice, double radius,
                                     int nChannels, Color color, double strokewidth, double xoffset,double yoffset) {
        for (int channel = 1; channel < nChannels+1; channel++) {
            double xleft = xctr + xoffset - radius;
            double ytop = yctr + yoffset - radius;

            OvalRoi spotroi = new OvalRoi(xleft, ytop, 2 * radius, 2 * radius);
            spotroi.setPosition(channel, slice, 1);
            spotroi.setStrokeColor(color);
            spotroi.setStrokeWidth(strokewidth);
            ov.add(spotroi);
        }
        return ov;
    }


    /**
     * Like singleCircleToOverlay(Overlay, double, double, int, double, int, Color, double, double, double)
     * but with default parameters
     */
    private static Overlay singleCircleToOverlay(Overlay ov, double xctr, double yctr, int slice, double radius,
                                                 int nChannels, Color color) {
        return singleCircleToOverlay(ov,xctr,yctr, slice,radius,nChannels,color, 0,0,0 );
    }
}
