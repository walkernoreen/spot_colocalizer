package de.mpicbg.scf.spotcoloc;

/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 */


import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.macro.MacroRunner;
import org.scijava.ItemVisibility;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;



/**
 * Interactive spot detection + colocalization analysis. requires at least 2 channels.
 * For macro recording use SpotColocalizerBatchPlugin.
 */
@Plugin(type = InteractiveCommand.class, initializer = "initialize_spotcoloc", menuPath = "Plugins>Spot Colocalization > SpotColocalizer Interactive")
public class SpotColocalizerInteractivePlugin extends InteractiveCommand   {

    @Parameter
    ImagePlus imp;

    // -- Dialog Parameters --
    @Parameter(label="Online Help", callback = "help_callback")
    private Button helpButton;

    // channel A
    @Parameter(label = "---  Channel A" , visibility = ItemVisibility.MESSAGE, persist = false, required=false)
    private String m1 = " (spots displayed magenta) ---";

    @Parameter(label="channel number")
    private int channelA=2;

    @Parameter(label="radius (um)")
    private double radiusA_um=1.0;

    @Parameter(label="quality threshold")
    private double thresholdA=100.0;

    //channel B
    @Parameter(label = "---  Channel B" , visibility = ItemVisibility.MESSAGE, persist = false, required=false)
    private String m2 = " (spots displayed green) ---";

    @Parameter(label="channel number")
    private int channelB=3;

    @Parameter(label="radius (um)")
    private double radiusB_um=1.0;

    @Parameter(label="quality threshold")
    private double thresholdB=100.0;

    // both channels
    @Parameter(label = "---  Both channels", visibility = ItemVisibility.MESSAGE, persist = false, required=false)
    private String m3=" ---";

    @Parameter(label = "median filtering", description="Filtering a large image slows down processing.")
    private boolean doMedian=false;

    @Parameter(label="Coloc distance factor (default: 1)", description = "Spots are considered colocalized if their centers are closer than distance_factor*0.5*(radiusA+radiusB). factor=1: centers of spot pair are closer than their average radius.")
    private double distanceFactorColoc=1.0;

    // general
    @Parameter(label = "---  General", visibility = ItemVisibility.MESSAGE, persist = false, required=false)
    private String m4=" ---";

    @Parameter(label="clear results tables")
    private boolean clearTable=false;

    @Parameter(label="add spots to Roi Manager")
    private boolean addToRoiManager=false;

    @Parameter(label="Include spots A in preview", persist = false)
    private boolean previewA=true;

    @Parameter(label="Include spots B in preview", persist = false)
    private boolean previewB=true;



    // processing buttons
    @Parameter(label = "Generate Preview", callback="generatePreview_callback" )
    private Button previewButton;

    @Parameter(label = "Full Colocalization Analysis", callback="fullAnalysis_callback" )
    private Button analysisButton;

    // -- private fields --
    final private boolean doSubpixel=true;

    // spot analyzer
    private SpotProcessor spotProcessor;


    /**
     * Launches help webpage. Triggered by "online help" button
     */
    private void help_callback(){
        // from here: https://github.com/imagej/ImageJA/blob/master/src/main/java/ij/gui/GenericDialog.java
        String macro = "run('URL...', 'url=" + Utils.pluginURL+ "');";
        new MacroRunner(macro);
    }


    /**
     * Initializes plugin. Does some sanity checks. More checks done in SpotProcessor initializer
     */
    private void initialize_spotcoloc(){
        if (imp!=null) { // imp==null triggers plugin exit
            // sanity checks
            if (imp.getNChannels() == 1) {
                IJ.error("Spot Colocalizer", "Image must have at least 2 channels.");
            }

            spotProcessor = new SpotProcessor(imp);
        }
    }


    /**
     * Generates spots preview. Triggered by "preview" button.
     */
    private void generatePreview_callback() {
        imp.setOverlay(null);

        printParameters();

        // show spot detection previews
        if (checkParameters()) {
            spotProcessor.generateDetectionPreviewMultiChannel(previewA, previewB, channelA, radiusA_um,
                    thresholdA, channelB, radiusB_um, thresholdB, doSubpixel, doMedian);
        } else {
            IJ.log("Issue with parameters.");
        }

        /* could run in separate thread, then e.g. start the next preview once this one is joined:
        new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("params ok. generating preview");
                    spotProcessor.generateDetectionPreviewMultiChannel(previewA, previewB, channelA, radiusA_um,
                            thresholdA, channelB, radiusB_um, thresholdB, doSubpixel, doMedian);
                }
            }).start();
            */
    }


    /**
     * Full colocalization analysis: detects spots and finds colocalized spots. Creates a results table.
     * Triggered by "full analysis" button.
     */
    private void fullAnalysis_callback() {
        imp.setOverlay(null);

        printParametersToLog();

        // do spot detection + colocalization. displays results table
        if (checkParameters()) {
            spotProcessor.runFullColocalizationAnalysis(channelA, radiusA_um, thresholdA,
                    channelB, radiusB_um, thresholdB, distanceFactorColoc,
                    doSubpixel, doMedian, clearTable, addToRoiManager);
        } else {
            IJ.log("Issue with provided parameters. Not running plugin.");
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




    /**
     * Pretty printing of all parameters to the console.
     */
    public final void printParameters() {
        System.out.println("\nParameters:");
        System.out.println("Channel A: channelA=" + channelA + ", radiusA_um=" + radiusA_um + ", thresholdA=" + thresholdA);
        System.out.println("Channel B: channelB=" + channelB + ", radiusB_um=" + radiusB_um + ", thresholdB=" + thresholdB);
        System.out.println("Both channels: medianFilter=" + doMedian + ", distanceFactorColoc=" + distanceFactorColoc);
        System.out.println("General: clearTable=" + clearTable  +", addToRoiManager=" + addToRoiManager+", previewA=" + previewA + ", previewB=" + previewB + "\n");
    }

    /**
     * Pretty printing of all parameters to the Log.
     */
    public final void printParametersToLog() {
        IJ.log("\nParameters:");
        IJ.log("Channel A: channelA="+channelA+", radiusA_um="+radiusA_um+", thresholdA="+thresholdA);
        IJ.log("Channel B: channelB="+channelB+", radiusB_um="+radiusB_um+", thresholdB="+thresholdB);
        IJ.log("Both channels: medianFilter="+doMedian+", distanceFactorColoc="+distanceFactorColoc);
        IJ.log("General: clearTable="+clearTable+", addToRoiManager=" + addToRoiManager+", previewA="+previewA+", previewB="+previewB+"\n");
    }



}
