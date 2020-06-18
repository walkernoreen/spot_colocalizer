package de.mpicbg.scf.spotcoloc;


import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import org.scijava.Cancelable;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.command.DynamicCommand;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import org.scijava.widget.Button;


/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 * Date: 2020-06
 */
// ToDo s: also table with spots positions?
// ToDo add help button?

@Plugin(type = InteractiveCommand.class, menuPath = "Plugins>Spot Colocalization > SpotColoc") // TODO nicer path
public class SpotColocalizerPlugin extends InteractiveCommand  { //implements MouseListener {

    @Parameter
    private PrefService prefs;

    @Parameter
    ImagePlus imp;

    // -- Dialog Parameters --
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

    // general
    @Parameter(label = "---  General", visibility = ItemVisibility.MESSAGE, persist = false, required=false)
    private String m3=" ---";

    @Parameter(label="Coloc distance factor (default: 1)", description = "Spots are considered colocalized if their centers are closer than distance_factor*0.5*(radiusA+radiusB). factor=1: centers of spot pair are closer than their average radius.")
    private double distanceFactorColoc=1.0;

    @Parameter(label="Include spots A in preview", persist = false)
    private boolean previewA=false;

    @Parameter(label="Include spots B in preview", persist = false)
    private boolean previewB=false;

    // processing buttons
    @Parameter(label = "Generate Preview", callback="generatePreview_callback" )
    private Button previewButton;

    @Parameter(label = "Full Colocalization Analysis", callback="fullAnalysis_callback" )
    private Button analysisButton;

    // -- more private fields --
    // TODO also add (do subpixel) and do median to the exposed options? (+rt display options?)
    final private boolean doSubixel=true;
    final private boolean doMedian=false;

    Roi currentRoi;

    // spot analyzer
    private SpotColocalizer spotColocalizer;


    /**
     * Generates spots preview
     */
    // TODO: new thread? and IJ update with swingutilities?
    private void generatePreview_callback() {
        // reset image, grab roi
        prepare();

        System.out.println("Preview callback");
        printParameters();

        // show spot detection previews
        if (checkParameters()) {
            System.out.println("params ok. generating preview");
            spotColocalizer.generateDetectionPreviewMultiChannel(previewA, previewB, channelA, radiusA_um,
                    thresholdA, channelB, radiusB_um, thresholdB, doSubixel, doMedian);
        }
    }


    /**
     * Full colocalization analysis: detects spots and finds colocalized spots. Creates a results table.
     */
    private void fullAnalysis_callback() {
        // reset image, grab roi
        prepare();

        printParameters();

        // do spot detection + colocalization. displays results table
        if (checkParameters()) {
            System.out.println("params ok. running full analysis.");

            spotColocalizer.runFullColocalizationAnalysis(channelA, radiusA_um, thresholdA,
                    channelB, radiusB_um, thresholdB, distanceFactorColoc,
                    doSubixel, doMedian, true); // TODO: cleartable? -> expose this option?
            // ToDo make an option that returns the results?
        } else {
            IJ.error("Parameter error in Spot Colocalization",
                    "Some parameters were not ok. Not running plugin.\n" +
                            "See Console Window for current parameters.");
        }
    }


    /**
     * Helper to update data before generating preview or doing full analysis
     */
    private void prepare() {
        spotColocalizer=new SpotColocalizer(imp);             //TODO: initialie only once?
        currentRoi=imp.getRoi();
        imp.setOverlay(null);
    }


    @Override
    public void preview() {
        super.preview();
        System.out.println("Previewing - but doing nothing (but saving params)");
    }


    @Override
    public void run() {
            System.out.println("Running - but doing nothing (but saving params)");
        }




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
        System.out.println("distanceFactorColoc="+distanceFactorColoc+", previewA="+previewA+", previewB="+previewB);
    }

}
