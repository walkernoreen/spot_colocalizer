package de.mpicbg.scf.spotcoloc;


import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import org.scijava.ItemVisibility;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.Button;



/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 * Date: 2020-06
 */
// ToDo s: also table with spots positions?
// ToDo add help button?

@Plugin(type = InteractiveCommand.class, initializer = "initialize_spotcoloc", menuPath = "Plugins>Spot Colocalization > SpotColoc") // TODO nicer path
public class SpotColocalizerPlugin extends InteractiveCommand  { //implements MouseListener {

    @Parameter
    private PrefService prefs;

    @Parameter
    ImagePlus imp;

    // -- Dialog Parameters -- // ToDo: rearrange the GUI + update the print fcts with all params
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

    @Parameter(label="Clear results tables")
    private boolean clearTable=false;

    @Parameter(label="Include spots A in preview", persist = false)
    private boolean previewA=false;

    @Parameter(label="Include spots B in preview", persist = false)
    private boolean previewB=false;

    // processing buttons
    @Parameter(label = "Generate Preview", callback="generatePreview_callback" )
    private Button previewButton;

    @Parameter(label = "Full Colocalization Analysis", callback="fullAnalysis_callback" )
    private Button analysisButton;

    // -- private fields --
    final private boolean doSubixel=true;

    Roi currentRoi;

    // spot analyzer
    private SpotColocalizer spotColocalizer;


    /**
     * Initializes plugin. Does sanity checks within the SpotColocalizer initializer
     */
    private void initialize_spotcoloc(){
        spotColocalizer = new SpotColocalizer(imp);
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
            spotColocalizer.generateDetectionPreviewMultiChannel(previewA, previewB, channelA, radiusA_um,
                    thresholdA, channelB, radiusB_um, thresholdB, doSubixel, doMedian);
        } else {
            IJ.log("Issue with parameters.");
        }

        /* could run in separate thread, then e.g. start the next preview once this one is joined:
        new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("params ok. generating preview");
                    spotColocalizer.generateDetectionPreviewMultiChannel(previewA, previewB, channelA, radiusA_um,
                            thresholdA, channelB, radiusB_um, thresholdB, doSubixel, doMedian);
                }
            }).start();
            */
    }


    /**
     * Full colocalization analysis: detects spots and finds colocalized spots. Creates a results table.
     * Triggered by "full analysis" button.
     */
    private void fullAnalysis_callback() {
        currentRoi=imp.getRoi();
        imp.setOverlay(null);

        printParametersToLog();

        // do spot detection + colocalization. displays results table
        if (checkParameters()) {
            spotColocalizer.runFullColocalizationAnalysis(channelA, radiusA_um, thresholdA,
                    channelB, radiusB_um, thresholdB, distanceFactorColoc,
                    doSubixel, doMedian, clearTable);
        } else {
            IJ.error("Parameter error in Spot Colocalization",
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
     * Both can happen while typing, since DialogListener listens to every single character input
     * (e.g deleting or setting radius 0 -> 0. -> 0.4)
     * @return whether checks were passed
     */
    private final boolean checkParameters() {
        boolean noNaNs = !(Double.isNaN(channelA) || Double.isNaN(radiusA_um) || Double.isNaN(thresholdA) ||
                Double.isNaN(channelB) || Double.isNaN(radiusB_um) || Double.isNaN(thresholdB) ||
                Double.isNaN(distanceFactorColoc));
        boolean noZeros = !(channelA==0 || radiusA_um==0 || channelB==0 || radiusB_um==0 );
        return (noNaNs && noZeros);
    }


    /**
     * Pretty printing of all parameters to the console.
     */
    public final void printParameters() {
        System.out.print("\nChannel A: ");
        System.out.println("channelA="+channelA+", radiusA_um="+radiusA_um+", thresholdA="+thresholdA);
        System.out.print("Channel B: ");
        System.out.println("channelB="+channelB+", radiusB_um="+radiusB_um+", thresholdB="+thresholdB);
        System.out.print("General: ");
        System.out.println("distanceFactorColoc="+distanceFactorColoc+", previewA="+previewA+", previewB="+previewB+"\n");
    }

    /**
     * Pretty printing of all parameters to the Log.
     */
    public final void printParametersToLog() {
        IJ.log("\nChannel A: channelA="+channelA+", radiusA_um="+radiusA_um+", thresholdA="+thresholdA);
        IJ.log("Channel B: channelB="+channelB+", radiusB_um="+radiusB_um+", thresholdB="+thresholdB);
        IJ.log("General: distanceFactorColoc="+distanceFactorColoc+", previewA="+previewA+", previewB="+previewB);
    }



}
