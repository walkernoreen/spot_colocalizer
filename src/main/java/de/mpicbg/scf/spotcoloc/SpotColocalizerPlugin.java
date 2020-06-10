package de.mpicbg.scf.spotcoloc;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.plugin.frame.RoiManager;
import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;


/*
 * Author: Noreen Walker, Scientific Computing Facility, MPI-CBG
 * Date: 2020-04
 */

/**
 * TODO
 */
/*@Plugin(type = Command.class, menuPath = "Plugins>Spot Colocalization > SpotColoc") // TODO nicer path
public class SpotColocalizerPlugin implements Command {

    @Parameter(label = "mask image")
    ImagePlus imp;

    @Override
    public void run() {

        System.out.println("Finished running the plugin");
    }
}
*/


public class SpotColocalizerPlugin implements Command, MouseListener {

    @Parameter
    ImagePlus imp;

    Roi currentRoi;

    @Override
    public void run() {
        currentRoi=imp.getRoi();

        IJ.log("RUN");
        IJ.log("image "+imp);


        ImageCanvas canvas = imp.getWindow().getCanvas();

        canvas.addMouseListener(this); // prepend with a  ? canvas.removeMouseListener(IJ.getInstance()); ?

        int channel1=0;

//        NonBlockingGenericDialog dialog = new NonBlockingGenericDialog("Spot Colocalization");
//        dialog.addMessage("Channel A (spots displayed red)");
 //       dialog.addNumericField("channel number",channel1,0,10,"");
        //dialog.showDialog();

        InteractiveColocDialog interactiveColocDialog = new InteractiveColocDialog(imp);
        interactiveColocDialog.displayAndRun();


    }

    /**
     * Checks whether a ROI was updated/removed
     * @param e
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        //IJ.log("Mouse released");
        Roi roi = imp.getRoi();
        if (roi==null) {
            if (currentRoi!=null) {
                IJ.log("Roi was removed. Calling previewer."); //TODO
            }
            currentRoi=null;
        }
        else {
            //boolean isArea = roi.isArea(); // don't check. would make tracking changes annoying
            boolean isFinished = (roi.getState()==Roi.NORMAL);
            boolean isDifferent = !roi.equals(currentRoi);
            /*try {
                IJ.log("Compare type:"+(roi.getType()==currentRoi.getType()));
                IJ.log("Compare bounds:"+(roi.getBounds()==currentRoi.getBounds()));
                IJ.log(roi.getBounds().toString());
                IJ.log(currentRoi.getBounds().toString());
                IJ.log("Compare length:"+(roi.getLength()==currentRoi.getLength()));
                IJ.log("different:"+isDifferent+"   finished: "+isFinished);
            }
            catch (Exception ee){};*/

            if (isFinished && isDifferent) {
                IJ.log("Roi was modified. Calling previewer."); // TODO: do this

                //SpotColocalizer spotColocalizer = new SpotColocalizer(imp); // blubb
                //spotColocalizer.generateDetectionPreviewMultiChannel(true,false,2,0.4,
                //        200,1,1,1,true,false);

            }
            currentRoi= (Roi) roi.clone();
        }
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