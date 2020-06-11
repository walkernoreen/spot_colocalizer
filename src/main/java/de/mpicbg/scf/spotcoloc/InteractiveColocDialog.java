package de.mpicbg.scf.spotcoloc;

import ij.ImagePlus;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;

import java.awt.*;

public class InteractiveColocDialog implements DialogListener {

    // parameters
    // channel A
    private int channelA=1;
    private double radiusA_um=1.0;
    private double thresholdA=100.0;
    // channel B
    private int channelB=2;
    private double radiusB_um=1.0;
    private double thresholdB=100.0;
    // general
    private double distanceFactorColoc=1.0;
    private boolean previewA=false;
    private boolean previewB=false;

    // TODO also add (do subpixel) and do median to the exposed options?
    final private boolean doSubixel=true;
    final private boolean doMedian=false;

    // dialog
    NonBlockingGenericDialog gd;

    // analyzer
    private SpotColocalizer spotColocalizer;


    /**
     * Initialize with current image and parameters from stored preferences
     */
    public InteractiveColocDialog(ImagePlus inputImp) {
        channelA = (int) Prefs.get("spotcoloc.channelA", channelA);
        radiusA_um = Prefs.get("spotcoloc.radiusA_um", radiusA_um);
        thresholdA = Prefs.get("spotcoloc.thresholdA", thresholdA);
        channelB = (int) Prefs.get("spotcoloc.channelA", channelB);
        radiusB_um = Prefs.get("spotcoloc.radiusA_um", radiusB_um);
        thresholdB = Prefs.get("spotcoloc.thresholdA", thresholdB);
        distanceFactorColoc = Prefs.get("spotcoloc.distanceFactorColor", distanceFactorColoc);

        spotColocalizer = new SpotColocalizer(inputImp);
    }

    /**
     * Displays the dialog, does the previewing and full processing
     */
    public void displayAndRun() {
        //  == setup dialog ==
        gd = new NonBlockingGenericDialog("Spot Colocalization");
		// channel A
        gd.addMessage("Channel A (spots displayed magenta)");
        gd.addNumericField("channel number",channelA,0,10,""); // 0,10,"": digits,ncolumns, unit
        gd.addNumericField("radius (um)", radiusA_um, 2, 10, "");
        gd.addNumericField("quality threshold", thresholdA, 2, 10, "");
		// channel B
        gd.addMessage("Channel B (spots displayed green)");
        gd.addNumericField("channel number", channelA, 0, 10, "");
        gd.addNumericField("radius (um)", radiusA_um, 2, 10, "");
        gd.addNumericField("quality threshold", thresholdB, 2, 10, "");
		// general
        gd.addMessage("General");
        gd.addNumericField("Coloc distance factor (default: 1)",distanceFactorColoc,1); // factor of average radius.
        gd.addCheckbox("preview spots in channel A",false);
        gd.addCheckbox("preview spots in channel B",false);

        // == for spots preview ==
        gd.addDialogListener(this);

        gd.showDialog();


        // == dialog was canceled ==
        if (gd.wasCanceled()) {
            return;
        }

        // == dialog was ok'd: full processing ==
        if (gd.wasOKed()) {
            //TODO: get values

            // spot detection + colocalization. shows results table
            spotColocalizer.runFullColocalizationAnalysis(channelA, radiusA_um, thresholdA,
                    channelB, radiusB_um,thresholdB, distanceFactorColoc,
                    doSubixel, doMedian,true); // TODO: cleartable? -> expose option?

            // update parameter Preferences
            Prefs.set("spotcoloc.channel1", channelA);
            Prefs.set("spotcoloc.radius1", radiusA_um);
            Prefs.set("spotcoloc.threshold1", thresholdA);
            Prefs.set("spotcoloc.channel2", channelB);
            Prefs.set("spotcoloc.radius2", radiusB_um);
            Prefs.set("spotcoloc.threshold2", thresholdB);
            Prefs.set("spotcoloc.colocfactor", distanceFactorColoc);
        }


    }

    /**
     * Extracts the parameters from the dialog
     */
    private void getParameters() {
        // channel A
        channelA= (int) gd.getNextNumber();
        radiusA_um = gd.getNextNumber();
        thresholdA = gd.getNextNumber();
        // channel B
        channelB = (int) gd.getNextNumber();
        radiusB_um=gd.getNextNumber();
        thresholdB = gd.getNextNumber();
        //general
        distanceFactorColoc=gd.getNextNumber();
        previewA=gd.getNextBoolean();
        previewB=gd.getNextBoolean();
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
        System.out.println("\nChannel A:");
        System.out.println("channelA="+channelA+", radiusA_um="+radiusA_um+", thresholdA="+thresholdA);
        System.out.println("\nChannel B:");
        System.out.println("channelB="+channelB+", radiusB_um="+radiusB_um+", thresholdB="+thresholdB);
        System.out.println("\nGeneral:");
        System.out.println("distanceFactorColoc="+distanceFactorColoc+", previewA="+previewA+", previewB="+previewB);

    }


    /**
     * Generate spots preview
     * @param genericDialog which genericDialog is this?
     * @param awtEvent
     * @return
     */
    @Override
    public boolean dialogItemChanged(GenericDialog genericDialog, AWTEvent awtEvent) {
        //TODO for future: only call the spot detector if anything changed compared to previous variables?

        getParameters();

        printParameters();

		// show spot detection previews
        if (checkParameters()) {
            spotColocalizer.generateDetectionPreviewMultiChannel(previewA, previewB, channelA, radiusA_um,
                    thresholdA, channelB, radiusB_um, thresholdB, doSubixel, doMedian);
        }

		// somehow wasn't possible to access the wasOKed(), wasCanceled() on the actual active dialog


        return true; // keep OK button activated
    }
}
