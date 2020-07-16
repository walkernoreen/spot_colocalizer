/*
some imglib2 resources and forum posts:
extract channels: https://forum.image.sc/t/how-can-i-work-with-channels-zstacks-in-the-imagej2-jupyter-notebooks/23447/3
convert imp to imglib2 type: https://forum.image.sc/t/how-to-wrap-any-kind-of-imageplus-to-an-imglib2-img-floattype/178/2
 */

package de.mpicbg.scf.spotcoloc;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.RealType;

import java.io.File;

public class Main {
   public static  <T extends RealType<T>> void main(final String... args) throws Exception {
   //public static  void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services


        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // load and show image

        File inputFile=new File("src/main/resources/NIP3_GFP_PGL3_mCherry_40x-Sil_Neo_crop.tif");

      ImagePlus imp= IJ.openImage(inputFile.getPath());

       int[] xpoints = {32,69,121,43};
       int[] ypoints = {134,184,116,45};
       imp.setRoi(new PolygonRoi(xpoints,ypoints,3,Roi.POLYGON));
       int nSlices = imp.getNSlices();
       imp.setPosition(1, (int) Math.max(1,0.2*nSlices),1);
       imp.show();

       // invoke the plugin (IJ2 style)
        ij.command().run(SpotColocalizerInteractivePlugin.class, true);

       //System.out.println("Done");
    }


}
