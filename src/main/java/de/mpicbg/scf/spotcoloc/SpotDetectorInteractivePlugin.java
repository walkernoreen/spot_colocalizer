package de.mpicbg.scf.spotcoloc;


import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import org.scijava.ItemVisibility;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;



/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 * Date: 2020-06
 */
// ToDo add help button?

/**
 * Interactive spot detection in a single channel.
 * For macro recording use SpotDetectorBatchPlugin.
 */
@Plugin(type = InteractiveCommand.class, initializer = "initialize_spotdetect", menuPath = "Plugins>Spot Colocalization > SpotDetect") // TODO nicer path
public class SpotDetectorInteractivePlugin extends InteractiveCommand   {

    @Parameter
    ImagePlus imp;

    // -- Dialog Parameters --
    // spot detection
    @Parameter(label = "---  Spot detection" , visibility = ItemVisibility.MESSAGE, persist = false, required=false)
    private String m1 = " ---";

    @Parameter(label="channel number")
    private int channel =2;

    @Parameter(label="radius (um)")
    private double radius_um =1.0;

    @Parameter(label="quality threshold")
    private double threshold =100.0;

    @Parameter(label = "median filtering", description="Filtering a large image slows down processing.")
    private boolean doMedian=false;

    // general
    @Parameter(label = "---  General", visibility = ItemVisibility.MESSAGE, persist = false, required=false)
    private String m4=" ---";

    @Parameter(label="clear results table")
    private boolean clearTable=false;

    // processing buttons
    @Parameter(label = "Generate Preview", callback="generatePreview_callback" )
    private Button previewButton;

    @Parameter(label = "Full Spot Dectection", callback="fullAnalysis_callback" )
    private Button analysisButton;

    // -- private fields --
    final private boolean doSubixel=true;

    Roi currentRoi;

    // spot analyzer
    private SpotProcessor spotProcessor;


    /**
     * Initializes plugin. Does sanity checks within the SpotProcessor initializer
     */
    private void initialize_spotdetect(){
        spotProcessor = new SpotProcessor(imp);
    }

    /**
     * Generates spots preview. Triggered by "preview" button.
     */
    private void generatePreview_callback() {
        currentRoi=imp.getRoi();
        imp.setOverlay(null);

        printParameters();

        // show spot detection previews
        if (checkParameters()) {
            spotProcessor.generateDetectionPreviewSingleChannel(channel, radius_um, threshold, doSubixel, doMedian);
        } else {
            IJ.log("Issue with parameters.");
        }
    }


    /**
     * Full spot detection analysis: detects spots and creates a results table. Quite similar to preview, but additional
     * roi overlay + results table.
     * Triggered by "full analysis" button.
     */
    private void fullAnalysis_callback() {
        currentRoi=imp.getRoi();
        imp.setOverlay(null);

        printParametersToLog();

        // do spot detection . displays results table
        if (checkParameters()) {
            spotProcessor.runFullSpotDetection(channel, radius_um, threshold, doSubixel,
                    doMedian, clearTable);
        } else {
            IJ.error("Parameter error in Spot Detector",
                    "Some parameters were not ok. Not running plugin.\n" +
                            "See Log Window for current parameters.");
        }
    }



   /* @Override
    public void preview() {
        // Not implemented. Analysis is called via button callbacks
        super.preview();
    }*/


   /* @Override
    public void run() {
        // Not implemented. Analysis is called via button callbacks
        super.run();
    }
    */




    /**
     * Checks that inputs are not NaN and that neither channel nor radius is zero.
     * Also checks that channel exists.
     * @return whether checks were passed
     */
    private final boolean checkParameters() {
        boolean noNaNs = !(Double.isNaN(channel) || Double.isNaN(radius_um) || Double.isNaN(threshold) );
        boolean noZeros = !(channel ==0 || radius_um ==0  );
        boolean channelOk = channel>=1 && channel<=imp.getNChannels();
        if (!channelOk) {
            IJ.error("Error", "Invalid channel number: "+channel);
        }
        return (noNaNs && noZeros && channelOk);
    }


    /**
     * Pretty printing of all parameters to the console.
     */
    public final void printParameters() {
        System.out.println("\nParameters:");
        System.out.println("channelnumber=" + channel + ", radius_um=" + radius_um + ", threshold=" + threshold +
                ", medianFilter=" + doMedian+", clearTable=" + clearTable +"\n");
    }

    /**
     * Pretty printing of all parameters to the Log.
     */
    public final void printParametersToLog() {
        IJ.log("\nParameters:");
        IJ.log("channelnumber=" + channel + ", radius_um=" + radius_um + ", threshold=" + threshold +
                ", medianFilter=" + doMedian+", clearTable=" + clearTable +"\n");
    }



}
