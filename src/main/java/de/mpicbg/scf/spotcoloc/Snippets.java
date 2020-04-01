// Collection of code snippets

package de.mpicbg.scf.spotcoloc;

import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.util.Arrays;

public class Snippets {
    /**
     * Convert imp to imglib2 type
     * Extract a channel or a crop region from a 4d (!) stack (x,y,c,z)
     * @param imp
     */
    public  static <T extends RealType<T>> void extractChannelOrRegion(ImagePlus imp) {

        System.out.println("Starting conversion");
        // see: https://forum.image.sc/t/how-to-wrap-any-kind-of-imageplus-to-an-imglib2-img-floattype/178/2
        // alternative: Img<RealType> wrapReal = ImageJFunctions.wrapReal(imp); // alternative
        Img<T> wrapT = ImageJFunctions.wrapReal(imp); //then the current function must extend T:  <T extends RealType<T>>
        // can also be returned as RAI:  RandomAccessibleInterval<T> rai = ImageJFunctions.wrap(imp);

        //RandomAccessibleInterval< FloatType > view =
        //        Views.interval( img, new long[] { 200, 200 }, new long[]{ 500, 350 } );
        System.out.println("num dims "+wrapT.numDimensions());

        int ndims = wrapT.numDimensions();
        long[] dims=new long[4];
        wrapT.dimensions(dims);
        System.out.println("Dimensions "+ Arrays.toString(dims)); // [238, 236, 3, 51] // x,y,c,z

        ImageJFunctions.show(wrapT,"wrapped img");

        // extract a channel with views.hyperslice
        RandomAccessibleInterval<T> viewCh = Views.hyperSlice(wrapT, 2, 0);
        ImageJFunctions.show(viewCh,"views ch hyperslice");

        // extract channel with views.interval # intervals start at 0, end interval is inclusive?
        RandomAccessibleInterval<T> viewChInterval = Views.interval(wrapT, new long[]{0, 0, 0, 0}, new long[]{237, 235, 0, 50});
        ImageJFunctions.show(viewChInterval,"views ch interval");


        // crop to a roi with views
//        RandomAccessibleInterval<T> viewCrop = Views.interval(wrapT, new long[]{0, 0,0,0}, new long[]{199,49,2,50});
        RandomAccessibleInterval<T> viewCrop = Views.interval(wrapT, new long[]{0, 0,0,0},
                new long[]{199,49,imp.getNChannels()-1,imp.getNSlices()-1});
        ImageJFunctions.show(viewCrop,"views crop");

    }
}
