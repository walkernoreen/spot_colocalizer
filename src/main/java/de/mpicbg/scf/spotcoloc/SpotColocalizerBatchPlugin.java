package de.mpicbg.scf.spotcoloc;

/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 */

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;



/**
 * Batch processible + macro recordable version of SpotColocalizerInteractivePlugin
 */
@Plugin(type = Command.class, initializer = "initialize_inputChecks", menuPath = "Plugins>Spot Colocalization > Macro Recordable > SpotColocalizer")
public class SpotColocalizerBatchPlugin implements Command {

    @Parameter
    ImagePlus imp;

    // -- Dialog Parameters --
    // removed message items, see: https://forum.image.sc/t/imagej2-command-macro/29650/3
    // channel A
    @Parameter(label = "channel A: channel number", description="overlay in magenta")
    private int channelA = 2;

    @Parameter(label = "channel A: radius (um)")
    private double radiusA_um = 1.0;

    @Parameter(label = "channel A: quality threshold")
    private double thresholdA = 100.0;

    //channel B
    @Parameter(label = "channel B: channel number",description="overlay in green")
    private int channelB = 3;

    @Parameter(label = "channel B: radius (um)")
    private double radiusB_um = 1.0;

    @Parameter(label = "channel B: quality threshold")
    private double thresholdB = 100.0;

    // both channels
    @Parameter(label = "median filtering", description = "Filtering a large image slows down processing.")
    private boolean doMedian = false;

    @Parameter(label = "Coloc distance factor (default: 1)", description = "Spots are considered colocalized if their centers are closer than distance_factor*0.5*(radiusA+radiusB). factor=1: centers of spot pair are closer than their average radius.")
    private double distanceFactorColoc = 1.0;

    // general
    @Parameter(label = "clear results tables")
    private boolean clearTable = false;

    @Parameter(label="add spots to Roi Manager")
    private boolean addToRoiManager=false;


    // -- private fields --
    final private boolean doSubpixel = true;


    // spot analyzer
    private SpotProcessor spotProcessor;



    private void initialize_inputChecks() {
        if (imp!=null) { // imp==null triggers plugin exit
            if (imp.getNChannels() == 1) {
                IJ.error("Spot Colocalizer", "Image must have at least 2 channels.");
            }
        }
    }


    @Override
    public void run() {
        // cannot avoid dialog in this case, but then don't execute the processing
        if (imp.getNChannels()==1){
            IJ.log("Image has only one channel. Returning.");
            return;
        }

        // initialization
        spotProcessor = new SpotProcessor(imp);
        imp.setOverlay(null);

        // do spot detection + colocalization. displays results table
        if (checkParameters()) {
            spotProcessor.runFullColocalizationAnalysis(channelA, radiusA_um, thresholdA,
                    channelB, radiusB_um, thresholdB, distanceFactorColoc,
                    doSubpixel, doMedian, clearTable, addToRoiManager);
        } else {
            IJ.log("Issue with provided parameters. Not running plugin.");
        }
    }

    /**
     * Checks that inputs are not NaN and that neither channel nor radius is zero.
     * Also checks that channels exists.
     * @return whether checks were passed
     */
    private final boolean checkParameters() {
        boolean noNaNs = !(Double.isNaN(channelA) || Double.isNaN(radiusA_um) || Double.isNaN(thresholdA) ||
                Double.isNaN(channelB) || Double.isNaN(radiusB_um) || Double.isNaN(thresholdB) ||
                Double.isNaN(distanceFactorColoc));
        boolean noZeros = !(channelA==0 || radiusA_um==0 || channelB==0 || radiusB_um==0 );
        boolean channelOk = channelA>=1 && channelA<=imp.getNChannels() && channelB>=1 && channelB<=imp.getNChannels();
        if (!channelOk) {
            IJ.error("Error", "One or more invalid channel numbers: "+channelA+", "+channelB);
        }
        return (noNaNs && noZeros && channelOk);
    }


}