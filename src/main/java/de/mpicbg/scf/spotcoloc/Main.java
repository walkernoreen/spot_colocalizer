/*
some imglib2 resources and forum posts:
extract channels: https://forum.image.sc/t/how-can-i-work-with-channels-zstacks-in-the-imagej2-jupyter-notebooks/23447/3
convert imp to imglib2 type: https://forum.image.sc/t/how-to-wrap-any-kind-of-imageplus-to-an-imglib2-img-floattype/178/2
 */

package de.mpicbg.scf.spotcoloc;

import fiji.plugin.trackmate.Spot;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.RealType;

import java.awt.*;
import java.io.File;
import java.util.List;

public class Main {
   public static  <T extends RealType<T>> void main(final String... args) throws Exception { //TODO extend T?
   //public static  void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services

        // plugins dir for development: make IJ.run() work
        //String pluginsDir = "/Applications/Fiji/plugins";
        //System.setProperty("plugins.dir", pluginsDir);


        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // load and show image

//        File inputFile=new File("src/main/resources/spots_singlechannel_stack.tif");
        File inputFile=new File("src/main/resources/spots_multichannel_stack.tif");

        ImagePlus imp= IJ.openImage(inputFile.getPath());

       int[] xpoints = {65,185,175};
       int[] ypoints = {124,22,202};
       imp.setRoi(new PolygonRoi(xpoints,ypoints,3,Roi.POLYGON));
       // imp.setRoi(new Roi(100,50,50,80));
        imp.show();

       //
//       Snippets.extractChannelOrRegion(imp);
       SpotColocalizer<T> spotColocalizer = new SpotColocalizer<T>(imp);

       spotColocalizer.runFullColocalizationAnalysis(2,0.4,200,3,0.4,
               250,1,true,false,true);

/*       List<Spot> spotsA = spotColocalizer.detectSpots(2, 0.4, 200, true, false);
       List<Spot> spotsB = spotColocalizer.detectSpots(3, 0.4, 250, true, false);

       SpotColocalizer.SpotsTriplet ST = spotColocalizer.findSpotCorrespondences(spotsA, spotsB, 0.4);

       Overlay ov = spotColocalizer.createOverlayOfSpots(ST.spotsA_noncoloc, imp, Color.red);
       ov=spotColocalizer.createOverlayOfSpots(ST.spots_coloc,imp,ov,Color.yellow);
       ov=spotColocalizer.createOverlayOfSpots(ST.spotsB_noncoloc,imp,ov,Color.green);

       imp.setOverlay(ov);

*/


       // invoke the plugin (IJ2 style)
      //  ij.command().run(SpotColocalizerPlugin.class, true);

        // automatize input for all @Parameters
      /*  Map<String, Object> map = new HashMap<>();
          map.put("segImp", labels);
          map.put("grayImp", imp);

        ij.command().run(Create3DOverlayPlugin.class, true, map);*/

       System.out.println("Done");
    }


}
