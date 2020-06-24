package de.mpicbg.scf.spotcoloc;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;



/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 * Date: 2020-06
 */


/**
 * Batch processible + macro recordable version of SpotColocalizerInteractivePlugin
 */
@Plugin(type = Command.class, menuPath = "Plugins>Spot Colocalization > SpotDetectBatch") // TODO nicer path
public class SpotDetectorBatchPlugin implements Command {

    @Parameter
    ImagePlus imp;

    // -- Dialog Parameters --
    // removed message items, see: https://forum.image.sc/t/imagej2-command-macro/29650/3
    @Parameter(label = "channel number")
    private int channel = 2;

    @Parameter(label = "radius (um)")
    private double radius_um = 1.0;

    @Parameter(label = "quality threshold")
    private double threshold = 100.0;

    @Parameter(label = "median filtering", description = "Filtering a large image slows down processing.")
    private boolean doMedian = false;

    // general
    @Parameter(label = "clear results table")
    private boolean clearTable = false;// channel A

    // -- private fields --
    final private boolean doSubixel = true;

    Roi currentRoi;

    // spot analyzer
    private SpotProcessor spotProcessor;


    @Override
    public void run() {
        spotProcessor = new SpotProcessor(imp);
        currentRoi = imp.getRoi();
        imp.setOverlay(null);

        // do spot detection . displays results table
        if (checkParameters()) {
            spotProcessor.runFullSpotDetection(channel, radius_um, threshold, doSubixel,
                    doMedian, clearTable);
        } else {
            IJ.error("Parameter error in Spot Detector",
                    "Some parameters were not ok. Not running plugin.");
        }
    }


    /**
     * Checks that inputs are not NaN and that neither channel nor radius is zero.
     * Also checks that channel exists.
     *
     * @return whether checks were passed
     */
    private final boolean checkParameters() {
        boolean noNaNs = !(Double.isNaN(channel) || Double.isNaN(radius_um) || Double.isNaN(threshold));
        boolean noZeros = !(channel == 0 || radius_um == 0);
        boolean channelOk = channel >= 1 && channel <= imp.getNChannels();
        if (!channelOk) {
            IJ.error("Error", "Invalid channel number: " + channel);
        }
        return (noNaNs && noZeros && channelOk);
    }
}
