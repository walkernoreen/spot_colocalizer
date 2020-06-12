package de.mpicbg.scf.spotcoloc;


import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;


/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 * Date: 2020-06
 */
// ToDo s: also table with spots positions?
// ToDo add help button?

@Plugin(type = Command.class, menuPath = "Plugins>Spot Colocalization > SpotColoc") // TODO nicer path
public class SpotColocalizerPlugin implements Command, MouseListener {

    @Parameter
    private PrefService prefs;

    @Parameter
    ImagePlus imp;

    Roi currentRoi;

    // parameters (values are overwritten with previously stored values in ColocDialog)
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
    ColocDialog colocDialog;

    // spot analyzer
    private SpotColocalizer spotColocalizer;


    @Override
    public void run() {

        // load stored parameters from preferences file
        loadStoredParameters();

        // new analyzer
        spotColocalizer= new SpotColocalizer(imp);

        currentRoi=imp.getRoi();

        imp.setOverlay(null);

        // add mouse listener -> tracks drawing of Rois
        ImageCanvas canvas = imp.getWindow().getCanvas();
        canvas.addMouseListener(this); // prepend with this?:canvas.removeMouseListener(IJ.getInstance()); ?

        // the main processing: dialog for preview + full analysis
        colocDialog = new ColocDialog();
        boolean success = colocDialog.displayAndRun();

        // store parameter values
        if (success) {
            storeParameters();
        }
    }


    /**
     * Plugin dialog
     */
    private class ColocDialog implements DialogListener{

        NonBlockingGenericDialog gd;

        /**
         * Initialize dialog and sets up dialog fields
         */
        ColocDialog() {
            //set up dialog
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
        }


        /**
         * Displays dialog, provides previewing functionaliy and does full processing pipeline
         * preview (preview checkboxes): show spot detection
         * full analysis (ok button): spot detection + colocalization
         * @return whether run finished successfully
         */
        private boolean displayAndRun() {
            // listen to changes: generates spots preview
            gd.addDialogListener(this);

            gd.showDialog();

            // if dialog was canceled
            if (gd.wasCanceled()) {
                return false;
            }

            // dialog was ok'd: full processing
            if (gd.wasOKed()) {
                printParameters();

                // do spot detection + colocalization. displays results table
                if (checkParameters()) {
                    System.out.println("running full analysis.");

                    spotColocalizer.runFullColocalizationAnalysis(channelA, radiusA_um, thresholdA,
                            channelB, radiusB_um, thresholdB, distanceFactorColoc,
                            doSubixel, doMedian, true); // TODO: cleartable? -> expose this option?

                    return true;
                }
                else {
                    IJ.error("Parameter error in Spot Colocalization",
                            "Some parameters were not ok. Not running plugin.\n" +
                                    "See Console Window for current parameters.");
                    return false;
                }
            }
            return false; // shouldn't be reached
        }



        /**
         * Generate spots preview. This function also extracts the parameters from the dialog.
         * Important: Do not extract parameters at any other code location (i.e. not after
         * Dialog was Ok'd or MouseListener reported changes (would try to access fields beyond the
         * number of actually existing fields in dialog (since counter keeps increasing).
         * @param genericDialog which genericDialog is this?
         * @param awtEvent
         * @return
         */
        @Override
        public boolean dialogItemChanged(GenericDialog genericDialog, AWTEvent awtEvent) {
            //TODO for future: only call the spot detector if anything changed compared to previous variables?

            System.out.println("Nested Dialog item changed at: "+java.time.LocalTime.now().toString());

            // extract parameters from dialog
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

            // show spot detection previews
            printParameters();
            if (checkParameters()) {
                System.out.println("preview call triggered");
                spotColocalizer.generateDetectionPreviewMultiChannel(previewA, previewB, channelA, radiusA_um,
                        thresholdA, channelB, radiusB_um, thresholdB, doSubixel, doMedian);
            }

            return true; // keep OK button activated
        }
    }


    /**
     * Checks whether a ROI was updated/removed
     * @param e
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        Roi roi = imp.getRoi();
        if (roi==null) {
            if (currentRoi!=null) {
                System.out.println("Roi was removed. at: "+java.time.LocalTime.now().toString());

                // generate spot detections preview
                printParameters();
                if (checkParameters()) {
                    System.out.println("preview call triggered");
                    spotColocalizer.generateDetectionPreviewMultiChannel(previewA, previewB, channelA, radiusA_um,
                            thresholdA, channelB, radiusB_um, thresholdB, doSubixel, doMedian);
                }

            }
            currentRoi=null;
        }
        else {
            boolean isFinished = (roi.getState()==Roi.NORMAL);
            boolean isDifferent = !roi.equals(currentRoi);

            if (isFinished && isDifferent) {
                System.out.println("Roi was modified. at: "+java.time.LocalTime.now().toString());

                // generate spot detections preview
                printParameters();
                if (checkParameters()) {
                    System.out.println("preview call triggered");
                    spotColocalizer.generateDetectionPreviewMultiChannel(previewA, previewB, channelA, radiusA_um,
                            thresholdA, channelB, radiusB_um, thresholdB, doSubixel, doMedian);
                }
            }
            currentRoi= (Roi) roi.clone();
        }
    }


    /**
     * Loads stored parameter values from disk (from preferences file).
     * If parameter is not found, uses default values.
     */
    private void loadStoredParameters() {
        channelA = prefs.getInt(this.getClass(), "channelA", channelA);
        radiusA_um = prefs.getDouble(this.getClass(),"radiusA_um",radiusA_um);
        thresholdA=prefs.getDouble(this.getClass(),"thresholdA",thresholdA);
        channelB = prefs.getInt(this.getClass(), "channelB", channelB);
        radiusB_um = prefs.getDouble(this.getClass(),"radiusB_um",radiusB_um);
        thresholdB=prefs.getDouble(this.getClass(),"thresholdB",thresholdB);
        distanceFactorColoc=prefs.getDouble(this.getClass(),"distanceFactorColoc",distanceFactorColoc);
    }

    /**
     * Saves parameter values to disk into the preferences file.
     */
    private void storeParameters() {
        prefs.put(this.getClass(), "channelA", channelA);
        prefs.put(this.getClass(), "radiusA_um", radiusA_um);
        prefs.put(this.getClass(), "thresholdA", thresholdA);
        prefs.put(this.getClass(), "channelB", channelB);
        prefs.put(this.getClass(), "radiusB_um", radiusB_um);
        prefs.put(this.getClass(), "thresholdB", thresholdB);
        prefs.put(this.getClass(), "distanceFactorColoc", distanceFactorColoc);
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



    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }


    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }


}