package de.embl.cba.bigDataTools2.dataStreamingGUI;

import de.embl.cba.bigDataTools2.CachedCellImageCreator;
import de.embl.cba.bigDataTools2.fileInfoSource.FileInfoConstants;
import de.embl.cba.bigDataTools2.fileInfoSource.FileInfoSource;
import de.embl.cba.bigDataTools2.saving.SaveCentral;
import de.embl.cba.bigDataTools2.saving.SavingSettings;
import de.embl.cba.bigDataTools2.utils.Utils;
import de.embl.cba.bigDataTools2.viewers.ImageViewer;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RecursiveTask;
import net.imglib2.interpolation.InterpolatorFactory;

public class BigDataConverter {

    public FileInfoSource fileInfoSource;
    public static ExecutorService executorService;  //General thread pool
    public static ExecutorService trackerThreadPool; // Thread pool for tracking
    public int numThreads;
    private ImageViewer imageViewer;// remove it

    public BigDataConverter() {
        //TODO: Determine Voxel Size to display in the Bdv --ashis
        //TODO: have separate shutdown for the executorService. It will not shutdown when ui exeService is shut. --ashis (DONE but needs testing)
        //Ref: https://stackoverflow.com/questions/23684189/java-how-to-make-an-executorservice-running-inside-another-executorservice-not
        System.out.println("Datastreaming constructor");
        kickOffThreadPack(Runtime.getRuntime().availableProcessors() * 2);
    }

    public void kickOffThreadPack(int nIOthreads) {
        this.numThreads = nIOthreads;
        if (executorService != null) {
            return;
        }
        executorService = Executors.newFixedThreadPool(nIOthreads);
    }

    public void shutdownThreadPack() {
        Utils.shutdownThreadPack(executorService, 10);
    }

    public void openFromDirectory(String directory, String namingScheme, String filterPattern, String h5DataSetName, ImageViewer imageViewer) {
        directory = Utils.fixDirectoryFormat(directory);
        this.fileInfoSource = new FileInfoSource(directory, namingScheme, filterPattern, h5DataSetName);
        CachedCellImg cachedCellImg = CachedCellImageCreator.create(this.fileInfoSource, this.executorService);
        this.imageViewer = imageViewer;
        imageViewer.setRai(cachedCellImg);
        imageViewer.setImageName(FileInfoConstants.IMAGE_NAME);
        imageViewer.show();
        imageViewer.addMenus(new BdvMenus());
        Utils.doAutoContrastPerChannel( imageViewer );
    }

    public static RandomAccessibleInterval crop(RandomAccessibleInterval rai,FinalInterval interval){
        return Views.interval(rai, interval);
    }

    public static void saveImage( SavingSettings savingSettings, ImageViewer imageViewer ) { //TODO: No need to get imageVieweer.Use only SavinfgSetting
        String streamName = imageViewer.getImageName();
        RandomAccessibleInterval rai = imageViewer.getRai();
        if (streamName.equalsIgnoreCase(FileInfoConstants.CROPPED_STREAM_NAME)) {
            rai = Views.zeroMin(rai);
        }
        savingSettings.image = rai;
        SaveCentral.interruptSavingThreads = false;
        SaveCentral.goSave(savingSettings, executorService);
    }

    public static void stopSave() {
        SaveCentral.interruptSavingThreads = true;
    }

    public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval shearImage(RandomAccessibleInterval rai,ShearingSettings shearingSettings){
        List<RandomAccessibleInterval<T>> timeTracks = new ArrayList<>();
        int nTimeFrames = (int) rai.dimension(FileInfoConstants.T_AXIS_POSITION);
        int nChannels = (int) rai.dimension(FileInfoConstants.C_AXIS_POSITION);
        System.out.println("Shear Factor X " + shearingSettings.shearingFactorX);
        System.out.println("Shear Factor Y " + shearingSettings.shearingFactorY);
        AffineTransform3D affine = new AffineTransform3D();
        affine.set(shearingSettings.shearingFactorX, 0, 2);
        affine.set(shearingSettings.shearingFactorY, 1, 2);
        List<ApplyShearToRAI> tasks = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        for (int t = 0; t < nTimeFrames; ++t) {
            ApplyShearToRAI task = new ApplyShearToRAI(rai, t, nChannels, affine,shearingSettings.interpolationFactory);
            task.fork();
            tasks.add(task);
        }
        for (ApplyShearToRAI task : tasks) {
            timeTracks.add((RandomAccessibleInterval) task.join());
        }
        RandomAccessibleInterval sheared = Views.stack(timeTracks);
        sheared = Views.permute(sheared, FileInfoConstants.C_AXIS_POSITION, FileInfoConstants.Z_AXIS_POSITION);
        System.out.println("Time elapsed(ms) " + (System.currentTimeMillis() - startTime));
        return sheared;
    }


    private static class ApplyShearToRAI<T extends RealType<T> & NativeType<T>> extends RecursiveTask<RandomAccessibleInterval> {

        private RandomAccessibleInterval rai;
        private int t;
        private final int nChannels;
        private final AffineTransform3D affine;
        private InterpolatorFactory interpolatorFactory;

        public ApplyShearToRAI(RandomAccessibleInterval rai, int time, int nChannels, AffineTransform3D affine,InterpolatorFactory interpolatorFactory) {
            this.rai = rai;
            this.t = time;
            this.nChannels = nChannels;
            this.affine = affine;
            this.interpolatorFactory = interpolatorFactory;
        }

        @Override
        protected RandomAccessibleInterval<T> compute() {
            List<RandomAccessibleInterval<T>> channelTracks = new ArrayList<>();
            RandomAccessibleInterval tStep = Views.hyperSlice(rai, FileInfoConstants.T_AXIS_POSITION, t);
            for (int channel = 0; channel < nChannels; ++channel) {
                RandomAccessibleInterval cStep = Views.hyperSlice(tStep, FileInfoConstants.C_AXIS_POSITION, channel);
                RealRandomAccessible real = Views.interpolate(Views.extendZero(cStep),this.interpolatorFactory);
                AffineRandomAccessible af = RealViews.affine(real, affine);
                FinalRealInterval transformedRealInterval = affine.estimateBounds(cStep);
                FinalInterval transformedInterval = Utils.asIntegerInterval(transformedRealInterval);
                RandomAccessibleInterval intervalView = Views.interval(af, transformedInterval);
                channelTracks.add(intervalView);
            }
            return Views.stack(channelTracks);
        }
    }

    public ImageViewer getImageViewer()
    {
        return imageViewer;
    }

}
