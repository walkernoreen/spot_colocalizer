
package de.mpicbg.scf.spotcoloc;

/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 */


import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.detection.DetectorKeys;
import fiji.plugin.trackmate.detection.LogDetectorFactory;
import fiji.plugin.trackmate.features.spot.SpotIntensityAnalyzerFactory;
import fiji.plugin.trackmate.features.spot.SpotRadiusEstimatorFactory;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.text.TextWindow;
import net.imglib2.type.numeric.RealType;


import java.awt.*;
import java.util.*;
import java.util.List;

import static java.lang.Math.round;


/**
 * A class for detecting spots and finding colocalization between spots.
 * Spot detection is using the LoG Detector of Trackmate.
 * Spot colocalization is based on the distance between spot centers.
 */
public class SpotProcessor {

    // 2D or 3D, single time point. for colocalization: at least 2 channels
    private final ImagePlus imp;

    final String titleSummaryTable ="Summary Counts Spot Colocalization"; // for coloc
    final String titleDetailedTable="Detailed Results Spot Colocalization"; // for coloc
    final String titleSpotsTable="Results Spot Detection"; // for spot detection


    public SpotProcessor(final ImagePlus inputImp) {
        imp=inputImp;

        checkInput();
    }


    private void checkInput() {
        // single time point
        if (imp.getNFrames()>1) {IJ.error("Spot Colocalizer", "Image must be a single time point. Plugin will not work correctly.");}
    }


// -------------------------------------------------------------------------
// Functions to be called from plugin GUI
// -------------------------------------------------------------------------


    /**
     *  Detects spots and visualizes them as overlay. Useful for previewing results in high level plugin.
     *  Spot detection is done in a single channel, but image may be multichannel
     * @param channel number of channel A (1,2,3,..)
     * @param radius_um radius of spots in channel A (um)
     * @param threshold quality threshold in channel A
     * @param doSubPixel LogDetector input
     * @param doMedian LogDetector input
     */
    public void generateDetectionPreviewSingleChannel(int channel, double radius_um, double threshold,
                                                      boolean doSubPixel, boolean doMedian) {
        Overlay ov = new Overlay();

        List<Spot> spotsA = detectSpots(channel, radius_um, threshold, doSubPixel, doMedian);
        ov = SpotVisualization.createOverlayOfSpots(imp, spotsA, ov, Color.magenta);

        imp.setOverlay(ov);
    }


    /**
     *  Detects spots and visualizes them as overlay. Useful for previewing results in a high level plugin.
     *  Requires multichannel image.
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
            ov = SpotVisualization.createOverlayOfSpots(imp, spotsA, ov, Color.magenta);
        }

        if (previewChB) {
            List<Spot> spotsB = detectSpots(channelB, radiusB_um, thresholdB, doSubPixel, doMedian);
            ov = SpotVisualization.createOverlayOfSpots(imp, spotsB, ov, Color.green);
        }

        imp.setOverlay(ov);
    }



    /** Full colocalization pipeline. Intented to be used by high level plugins.
     * Does spot detection in 2 channels, finds spot correspondendences, displays results as overlay and as counts
     * in results tables (summary + detailed).
     * @param channelA number of channel A (1,2,3,..)
     * @param radiusA_um radius of spots in channel A (um)
     * @param thresholdA quality threshold in channel A
     * @param channelB number of channel B
     * @param radiusB_um see above
     * @param thresholdB see above
     * @param doSubPixel LogDetector input
     * @param doMedian LogDetector input
     * @param clearTable clear results tables before adding results
     * @param addToRoiManager add spots as multi point roi to the roi manager
     */
    public void runFullColocalizationAnalysis(int channelA, double radiusA_um, double thresholdA,
                                              int channelB, double radiusB_um, double thresholdB,
                                              double distanceFactorColoc, boolean doSubPixel, boolean doMedian,
                                              boolean clearTable, boolean addToRoiManager) {

        // find spots
        List<Spot> spotsA = detectSpots(channelA, radiusA_um,thresholdA, doSubPixel, doMedian);
        List<Spot> spotsB = detectSpots(channelB,radiusB_um, thresholdB, doSubPixel, doMedian);


        // detect which spots are colocalized
        double maxdist_um = 0.5 * (radiusA_um + radiusB_um) * distanceFactorColoc;
        ColocResult CR = findSpotCorrespondences(spotsA, spotsB, maxdist_um);

        // create visualization overlay
        Overlay ov = SpotVisualization.createOverlayOfSpots(imp, CR.spotsA_noncoloc, Color.magenta);
        ov= SpotVisualization.createOverlayOfSpots(imp, CR.spotsAvg_coloc, ov,Color.white);
        ov= SpotVisualization.createOverlayOfSpots(imp, CR.spotsB_noncoloc, ov,Color.green);

        // add roi to overlay
        Roi roi = imp.getRoi();
        if (roi!=null) {
            roi.setStrokeColor(Color.yellow);
            ov.add(roi);
        }

        imp.setOverlay(ov);

        //display spots & summary in results tables
        ResultsTable rtdetailed= fillSpotsColocTable(channelA,channelB,CR,clearTable);
        rtdetailed.show(titleDetailedTable);

        ResultsTable rtsummary = fillSummaryColocTable(channelA, channelB, CR, clearTable);
        rtsummary.show(titleSummaryTable);

        // add to roi manager
        if (addToRoiManager) {
            addSpotsToRoiManager(CR.spotsA_coloc,"spots_coloc_ch"+channelA, channelA);
            addSpotsToRoiManager(CR.spotsA_noncoloc,"spots_notcoloc_ch"+channelA, channelA);
            addSpotsToRoiManager(CR.spotsB_coloc,"spots_coloc_ch"+channelB, channelB);
            addSpotsToRoiManager(CR.spotsB_noncoloc,"spots_notcoloc_ch"+channelB, channelB);
        }
    }




    /** Detects spots, displays them as overlay and adds them to results table. Intented to be used by high level plugins.
     * Detection is done in a single channel but the image may be multichannel.
     * This function is very similar to generateDetectionPreviewSingleChannel but adds the results table + roi overlay.
     * @param channel number of channel A (1,2,3,..)
     * @param radius_um radius of spots in channel A (um)
     * @param threshold quality threshold in channel A
     * @param doSubPixel LogDetector input
     * @param doMedian LogDetector input
     * @param clearTable clear results table before adding results
     * @param addToRoiManager add spots as multi point roi to the roi manager
     */
    public void runFullSpotDetection(int channel, double radius_um, double threshold,
                                     boolean doSubPixel, boolean doMedian, boolean clearTable, boolean addToRoiManager) {
        // find spots
        List<Spot> spots = detectSpots(channel, radius_um,threshold, doSubPixel, doMedian);

        // create visualization overlay
        Overlay ov = SpotVisualization.createOverlayOfSpots(imp, spots, Color.magenta);

        // add roi to overlay
        Roi roi = imp.getRoi();
        if (roi!=null) {
            roi.setStrokeColor(Color.yellow);
            ov.add(roi);
        }

        imp.setOverlay(ov);

        //display spots in results table
        ResultsTable rtspots= fillSpotsDetectionTable(channel,spots,clearTable);
        rtspots.show(titleSpotsTable);

        // add to roi manager
        if (addToRoiManager) {
            addSpotsToRoiManager(spots,"spots", channel);
        }
    }



// -------------------------------------------------------------------------
// Core processing functionality. Also useful for direct calls from scripts
// -------------------------------------------------------------------------

    /**
     * Detects spots in a single channel of an image. For spot detection the trackmate LoG detector is used.
     * If the input image has a roi, then spot detection is restricted to this region.
     * @param channel which channel to use. count starts at 1
     * @param radius_um Spot radius in um (for LoGDetector).
     * @param threshold Quality threshold for Log detector. `threshold` is first scaled with the heuristic
     *                 radius_um^3 (3D) or radius_um^2 (2D) before usage as cutoff in the LoGDetector.
     *                  Purpose: Decrease dependency of threshold value on radius. The used multiplication factor
     *                  is a heuristic and not perfect.
     * @param doSubpixel for LoG Detector
     * @param doMedian for LogDetector
     * @return a list with (trackmate) spot objects
     */
    public <T extends RealType<T>> List<Spot> detectSpots(int channel, double radius_um, double threshold,
                                                          boolean doSubpixel, boolean doMedian) {
        // return this if detection fails
        final List<Spot> emptyspots=new ArrayList<>();

        // heuristic scaling of threshold to reference radius=1um
        if (imp.getNSlices()>1) {
            threshold=threshold/(radius_um*radius_um*radius_um); // * or / anisotropy (in future?)?
        }
        else {
            threshold=threshold/(radius_um*radius_um);
        }

        // == Detect the spots ==
        // adapted from: https://imagej.net/Scripting_TrackMate
        //     and https://github.com/tferr/Scripts/blob/master/BAR/src/main/resources/scripts/BAR/Analysis/LoG-DoG_Spot_Counter.py
        // previous code version used to call LogDetector directly but it's then hard to get additional spot features like intensity

        Settings settings = new Settings();
        settings.setFrom(imp);

        // configure spot detector
        settings.detectorFactory = new LogDetectorFactory<>();

        Map<String, Object> map = new HashMap<>();
        map.put(DetectorKeys.KEY_RADIUS, radius_um);
        map.put(DetectorKeys.KEY_THRESHOLD, threshold);
        map.put(DetectorKeys.KEY_DO_SUBPIXEL_LOCALIZATION, doSubpixel);
        map.put(DetectorKeys.KEY_DO_MEDIAN_FILTERING, doMedian);
        map.put(DetectorKeys.KEY_TARGET_CHANNEL, channel);

        settings.detectorSettings = map;

        // add analyzers
        settings.addSpotAnalyzerFactory(new SpotIntensityAnalyzerFactory<>());
        settings.addSpotAnalyzerFactory(new SpotRadiusEstimatorFactory<>());

        // initialize trackmate
        TrackMate trackmate = new TrackMate(settings);

        //execute
        try {
            // first part of trackmate.process()
            //https://github.com/fiji/TrackMate/blob/7eda4995900469bbec0516f1d32cdbd9f3d84fd4/src/main/java/fiji/plugin/trackmate/TrackMate.java#L610
            if ( !trackmate.execDetection() ) { return emptyspots; } // could report the error messages to log ...
            if ( !trackmate.execInitialSpotFiltering() ) { return emptyspots; }
            if ( !trackmate.computeSpotFeatures( true ) ) { return emptyspots; }

        } catch (ArrayIndexOutOfBoundsException e) {
            IJ.log("The spot detector could not process the data: Roi outside of image");
            return emptyspots;
        }


        //extract results
        SpotCollection spotCollection = trackmate.getModel().getSpots();

        // avoid duplicates (spots at same position - why is this sometimes possible at all?)
        //          (could also do more advanced filtering: if spots very close by (below specified dist, then keep the higher quality one)
        // TODO: posted duplicate-spots issue on image.sc, follow updates: https://forum.image.sc/t/getting-duplicate-spots-with-trackmate-logdetector-scripting/39575
        List<double[]> positions = new ArrayList<double[]>();

        // collect spots
        List<Spot> spots=new ArrayList<>();

        for (final Spot spot : spotCollection.iterable(false)) {
            double[] posSpot = getPositionCalib(spot);

            // check for duplicated position
            boolean duplicate = false;
            for (double[] posTmp : positions) {
                double dist2 = (posTmp[0] - posSpot[0]) * (posTmp[0] - posSpot[0]) + (posTmp[1] - posSpot[1]) * (posTmp[1] - posSpot[1]) +
                        (posTmp[2] - posSpot[2]) * (posTmp[2] - posSpot[2]);
                if (dist2 < 0.00000000000001) {
                    duplicate = true;
                }
            }

            // add spot to list
            if (!duplicate) {
                positions.add(posSpot);
                spots.add(spot);
            } else {
                System.out.println("Skip duplicate: pos=" + Arrays.toString(getPositionCalib(spot)) + ", quality:" + spot.getFeature(Spot.QUALITY));
            }
        }

        IJ.log("Detected spots in channel " + channel + " (within Roi): " + spots.size() + ".");

        return spots;
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
     * @return ColocResult with list of non-colocalized and colocalized spots.
     *              Order for colocalized spots is such that: same list idx -> colocalized spot pair:
     *              spotsAvg_coloc[idx] = mean(spotsA_coloc[idx]+spotsB_coloc[idx])
     */
    public ColocResult findSpotCorrespondences(List<Spot> spotsA, List<Spot> spotsB, double maxdist_um) {
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
        List<Spot> spotsA_coloc = new ArrayList<>();
        List<Spot> spotsB_coloc = new ArrayList<>();
        List<Spot> spotsAvg_coloc = new ArrayList<>(); // spots at the avg position and with avg radius of the colocalized pair
        for (int i = 0; i < numspotsA; i++) {
            if (spotsBPartnersOfSpotsA[i] != null) {
                Spot spotA = spotsA.get(i);
                Spot spotB = spotsB.get(spotsBPartnersOfSpotsA[i]);

                spotsA_coloc.add(spotA);
                spotsB_coloc.add(spotB);

                // create a new spot at the average position and with average radius
                double[] posA = getPositionCalib(spotA);
                double[] posB = getPositionCalib(spotB);
                Double radA = spotA.getFeature(Spot.RADIUS);
                Double radB = spotB.getFeature(Spot.RADIUS);

                Spot spotAvg = new Spot(0.5 * (posA[0] + posB[0]), 0.5 * (posA[1] + posB[1]), 0.5 * (posA[2] + posB[2]),
                        0.5 * (radA + radB), -1);
                spotsAvg_coloc.add(spotAvg);
            }
        }

        IJ.log("Computed colocalization: "+spotsAvg_coloc.size()+" colocalized spots.");

        // should never happen
        if (spotsA.size()!=(spotsA_coloc.size()+spotsA_noncoloc.size()) ||
            spotsB.size()!=(spotsB_coloc.size()+spotsB_noncoloc.size())) {
            IJ.error("Spot Colocalization","Error in findSpotCorrespondences. Counts don't match.");
        }

        return new ColocResult(spotsA_noncoloc, spotsB_noncoloc, spotsA_coloc, spotsB_coloc, spotsAvg_coloc);
    }




    /**
     * Adds all counts of a colocalization analysis (spot detections, colocalized count etc.) to a results table
     * (custom table title). Grabs the open table if available, otherwise creates a new one. Previous results can
     * optionally be cleared.
     * @param channelA which channel id, for this and most other parameters, see runFullColocalizationAnalyis(...)
     * @param channelB
     * @param CR: colocalization result obtained from findSpotCorrespondences(...)
     * @return summary results table
     * */
    private ResultsTable fillSummaryColocTable(int channelA, int channelB, ColocResult CR , boolean clearTable) {

        // add counts to results table
        TextWindow window = (TextWindow) WindowManager.getWindow(titleSummaryTable);
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
        rt.showRowNumbers(true);

        String descrA = "(ch "+channelA+")";
        String descrB="(ch "+channelB+")";

        int countA=CR.spotsA_noncoloc.size()+CR.spotsA_coloc.size();
        int countB=CR.spotsB_noncoloc.size()+CR.spotsB_coloc.size();

        rt.incrementCounter();
        rt.addLabel(imp.getTitle());
        rt.addValue("Count total " + descrA, countA);
        rt.addValue("Count total " + descrB, countB);
        rt.addValue("Count coloc ", CR.spotsAvg_coloc.size());
        rt.addValue("Count not coloc "+descrA, CR.spotsA_noncoloc.size());
        rt.addValue("Count not coloc "+descrB, CR.spotsB_noncoloc.size());
        if (countA>0) {
            rt.addValue("Fraction coloc "+descrA, CR.spotsAvg_coloc.size()/ (float) countA);
        }
        else {
            rt.addValue("Fraction coloc " + descrA, Double.NaN);
        }
        if (countB>0) {
            rt.addValue("Fraction coloc " + descrB, CR.spotsAvg_coloc.size() /(float) countB);
        }
        else {
            rt.addValue("Fraction coloc " + descrB, Double.NaN);
        }

        return rt;
    }




    /**
     * Adds all spots to a results table (custom table title). Added spot properties are channel, position,
     * radius and whether they are colocalized. Use after colocalization analysis.
     *  Grabs the open table if available, otherwise creates a new one. Previous results can optionally be cleared.
     * @param channelA which channel id, for this and most other parameters, see runFullColocalizationAnalyis(...)
     * @param channelB
     * @param CR: colocalization result obtained from findSpotCorrespondences(...)
     * @param clearTable if True, table is emptied before new valueS are added
     * @return detailed results table
     */
    private ResultsTable fillSpotsColocTable(int channelA, int channelB, ColocResult CR, boolean clearTable) {

        // add counts to results table
        TextWindow window = (TextWindow) WindowManager.getWindow(titleDetailedTable);
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
        rt.showRowNumbers(true);

        appendSpotsToTable(rt,CR.spotsA_coloc,channelA,true,true);
        appendSpotsToTable(rt,CR.spotsA_noncoloc,channelA,true,false);
        appendSpotsToTable(rt,CR.spotsB_coloc,channelB,true,true);
        appendSpotsToTable(rt,CR.spotsB_noncoloc,channelB,true,false);

        return rt;
    }


    /**
     * Adds all detected spots to a results table (custom table title). Added spot properties are channel, position,
     * radius. Use after spot detection (in a single channel).
     *  Grabs the open table if available, otherwise creates a new one. Previous results can optionally be cleared.
     * @param channel which channel id was used
     * @param spots: list of spots from detectSpots(...)
     * @param clearTable if True, table is emptied before new valueS are added
     * @return detailed results table
     */
    private ResultsTable fillSpotsDetectionTable(int channel, List<Spot> spots, boolean clearTable) {

        // add counts to results table
        TextWindow window = (TextWindow) WindowManager.getWindow(titleSpotsTable);
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
        rt.showRowNumbers(true);

        appendSpotsToTable(rt,spots,channel,false,false);

        return rt;
    }




    /**
     * Helper for filling results tables
     * addColocInfo: if True, isColocalized (true/false) is added as column, otherwise it's ignored
     */
    private void appendSpotsToTable (ResultsTable rt, List<Spot> spots, int channel, boolean addColocInfo, boolean isColocalized) {
        for (int i = 0; i < spots.size(); i++) {
            Spot spot = spots.get(i);

            double[] positionCalib = getPositionCalib(spot);
            double[] positionPx = getPositionPx(spot, imp.getCalibration());

            rt.incrementCounter();
            rt.addLabel(imp.getTitle());
            rt.addValue("channel", channel);
            rt.addValue("x(um)", positionCalib[0]);
            rt.addValue("y(um)", positionCalib[1]);
            rt.addValue("z(um)", positionCalib[2]);
            rt.addValue("input_radius(um)", spot.getFeature(Spot.RADIUS));
            rt.addValue("estimated_radius(um)", (spot.getFeature(SpotRadiusEstimatorFactory.ESTIMATED_DIAMETER) / 2));
            rt.addValue("mean_intensity (within input_radius)", spot.getFeature(SpotIntensityAnalyzerFactory.MEAN_INTENSITY));
            rt.addValue("x(pixel)", positionPx[0]);
            rt.addValue("y(pixel)", positionPx[1]);
            rt.addValue("z(pixel)", positionPx[2]);
            rt.addValue("x(pixel)", positionPx[0]);
            rt.addValue("y(pixel)", positionPx[1]);
            rt.addValue("z(pixel)", positionPx[2]);
            if (addColocInfo) {
                rt.addValue("is_colocalized", String.valueOf(isColocalized));
            }
        }
    }



    /**
     * Little helper class to collect the lists of colocalized and noncolocalized spots.
     * spotsAvg_coloc: spots are located at the avg position and have the avg radius of the colocalized spot pair from
     *      channel A and B. Basically: spotsAvg_coloc[idx] = mean(spotsA_coloc[idx]+spotsB_coloc[idx])
     */
    public static class ColocResult {
        final List<Spot> spotsA_noncoloc;
        final List<Spot> spotsB_noncoloc;
        final List<Spot> spotsA_coloc;
        final List<Spot> spotsB_coloc;
        final List<Spot> spotsAvg_coloc;

        ColocResult(List<Spot> spotsA_noncoloc, List<Spot> spotsB_noncoloc, List<Spot> spotsA_coloc,
                    List<Spot> spotsB_coloc, List<Spot> spotsAvg_coloc) {
            this.spotsA_noncoloc = spotsA_noncoloc;
            this.spotsB_noncoloc = spotsB_noncoloc;
            this.spotsA_coloc = spotsA_coloc;
            this.spotsB_coloc = spotsB_coloc;
            this.spotsAvg_coloc = spotsAvg_coloc;
        }
    }


    /**
     * Adds the spots as multi point roi to the roi manager. All spots are added to channel 1. (but varying x,y,slice)
     * @param spots
     * @param spotsName name
     * @param channel channel to which the spot belongs
     */
    private void addSpotsToRoiManager(List<Spot> spots, String spotsName, int channel) {
        int oldC = imp.getC();
        int oldZ=imp.getZ();
        int oldT=imp.getT();

        PointRoi points = new PointRoi();

        for (int i = 0; i < spots.size(); i++) {
            Spot spot = spots.get(i);

            double[] positionPx = getPositionPx(spot, imp.getCalibration());
            int slice = (int) round(positionPx[2] + 1);

            imp.setPositionWithoutUpdate(channel, slice, 1);

            points.addPoint(imp, positionPx[0], positionPx[1]);
            points.setName(spotsName);
        }

        RoiManager rm = RoiManager.getRoiManager();
        rm.addRoi(points);

        imp.setPosition(oldC,oldZ,oldT);
    }


    /**
     * Returns x,y,z position of spot object, in pixel units
     * @param spot Trackmate spot
     * @param calib ImageJ calibration object (imp.getCalibration())
     * @return [x,y,z] array in px
     */
    private static double[] getPositionPx(Spot spot, Calibration calib) {
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
    private static double[] getPositionCalib(Spot spot) {
        double[] position = new double[3];
        position[0] = spot.getDoublePosition(0);
        position[1] = spot.getDoublePosition(1);
        position[2] = spot.getDoublePosition(2);
        return position;
    }


}
