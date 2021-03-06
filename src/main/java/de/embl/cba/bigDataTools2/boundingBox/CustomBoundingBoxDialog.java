/**
 * @author Ashis Ravindran
 * Code adapted and modified for customization
 * from BigDataViewer core.
 *
 * Original Authors: Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic.
 */

package de.embl.cba.bigDataTools2.boundingBox;

import bdv.tools.boundingbox.BoundingBoxOverlay;
import bdv.tools.boundingbox.BoundingBoxOverlay.BoundingBoxOverlaySource;
import bdv.tools.boundingbox.BoxRealRandomAccessible;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.tools.brightness.SetupAssignments;
import bdv.tools.transformation.TransformedSource;
import bdv.util.ModifiableInterval;
import bdv.util.RealRandomAccessibleSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import bdv.viewer.VisibilityAndGrouping;
import net.imglib2.Interval;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;

// dialog to change bounding box
// while dialog is visible, bounding box is added as a source to the viewer
public class CustomBoundingBoxDialog extends JFrame {
    private static final long serialVersionUID = 1L;
    protected final ModifiableInterval interval;
    protected final BoxRealRandomAccessible<UnsignedShortType> boxRealRandomAccessible;
    protected final CustomBoxSelectionPanel boxSelectionPanel;
    protected final SourceAndConverter<UnsignedShortType> boxSourceAndConverter;
    protected final RealARGBColorConverterSetup boxConverterSetup;
    protected final BoundingBoxOverlay boxOverlay;
    private boolean contentCreated = false;

    public CustomBoundingBoxDialog(
            final Frame owner,
            final String title,
            final ViewerPanel viewer,
            final SetupAssignments setupAssignments,
            final int boxSetupId,
            final Interval initialInterval,
            final Interval rangeInterval,
            final String[] axesToCrop) {
        this(owner, title, viewer, setupAssignments, boxSetupId, initialInterval, rangeInterval,
                true, true, axesToCrop);
    }

    public CustomBoundingBoxDialog(
            final Frame owner,
            final String title,
            final ViewerPanel viewer,
            final SetupAssignments setupAssignments,
            final int boxSetupId,
            final Interval initialInterval,
            final Interval rangeInterval,
            final boolean showBoxSource,
            final boolean showBoxOverlay,
            final String[] axesToCrop) {
        //super(owner, title, false);
        super( title);

        // create a procedural RealRandomAccessible that will render the bounding box
        final UnsignedShortType insideValue = new UnsignedShortType(1000); // inside the box pixel value is 1000
        final UnsignedShortType outsideValue = new UnsignedShortType(0); // outside is 0
        interval = new ModifiableInterval(initialInterval);
        boxRealRandomAccessible = new BoxRealRandomAccessible<>(interval, insideValue, outsideValue);

        // create a bdv.viewer.Source providing data from the bbox RealRandomAccessible
        final RealRandomAccessibleSource<UnsignedShortType> boxSource = new RealRandomAccessibleSource<UnsignedShortType>(boxRealRandomAccessible, new UnsignedShortType(), "selection") {
            @Override
            public Interval getInterval(final int t, final int level) {
                return interval;
            }
        };

        // set up a converter from the source type (UnsignedShortType in this case) to ARGBType
        final RealARGBColorConverter<UnsignedShortType> converter = new RealARGBColorConverter.Imp1<>(0, 3000);
        converter.setColor(new ARGBType(0x00994499)); // set bounding box color to magenta

        // create a ConverterSetup (can be used by the brightness dialog to adjust the converter settings)
        boxConverterSetup = new RealARGBColorConverterSetup(boxSetupId, converter);
        boxConverterSetup.setViewer(viewer);

        // create a SourceAndConverter (can be added to the viewer for display)
        final TransformedSource<UnsignedShortType> ts = new TransformedSource<>(boxSource);
        boxSourceAndConverter = new SourceAndConverter<>(ts, converter);

        // create an Overlay to show 3D wireframe box
        boxOverlay = new BoundingBoxOverlay(new BoundingBoxOverlaySource() {
            @Override
            public void getIntervalTransform(final AffineTransform3D transform) {
                ts.getSourceTransform(0, 0, transform);
            }

            @Override
            public Interval getInterval() {
                return interval;
            }
        });

        // create a JPanel with sliders to modify the bounding box interval (boxRealRandomAccessible.getInterval())
        boxSelectionPanel = new CustomBoxSelectionPanel(
                new CustomBoxSelectionPanel.Box() {
                    @Override
                    public void setInterval(final Interval i) {
                        interval.set(i);
                        viewer.requestRepaint();
                    }

                    @Override
                    public Interval getInterval() {
                        return interval;
                    }
                },
                rangeInterval, axesToCrop);

        // when dialog is made visible, add bbox source
        // when dialog is hidden, remove bbox source
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(final ComponentEvent e) {
                if (showBoxSource) {
                    viewer.addSource(boxSourceAndConverter);
                    setupAssignments.addSetup(boxConverterSetup);
                    boxConverterSetup.setViewer(viewer);

                    final int bbSourceIndex = viewer.getState().numSources() - 1;
                    final VisibilityAndGrouping vg = viewer.getVisibilityAndGrouping();
                    if (vg.getDisplayMode() != DisplayMode.FUSED) {
                        for (int i = 0; i < bbSourceIndex; ++i)
                            vg.setSourceActive(i, vg.isSourceVisible(i));
                        vg.setDisplayMode(DisplayMode.FUSED);
                    }
                    vg.setSourceActive(bbSourceIndex, true);
                    vg.setCurrentSource(bbSourceIndex);
                }
                if (showBoxOverlay) {
                    viewer.getDisplay().addOverlayRenderer(boxOverlay);
                    viewer.addRenderTransformListener(boxOverlay);
                }
            }

            @Override
            public void componentHidden(final ComponentEvent e) {
                if (showBoxSource) {
                    viewer.removeSource(boxSourceAndConverter.getSpimSource());
                    setupAssignments.removeSetup(boxConverterSetup);
                }
                if (showBoxOverlay) {
                    viewer.getDisplay().removeOverlayRenderer(boxOverlay);
                    viewer.removeTransformListener(boxOverlay);
                }
            }
        });


        // make ESC key hide dialog
        final ActionMap am = getRootPane().getActionMap();
        final InputMap im = getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        final Object hideKey = new Object();
        final Action hideAction = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(final ActionEvent e) {
                setVisible(false);
            }
        };
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), hideKey);
        am.put(hideKey, hideAction);

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
//		createContent();
    }

    @Override
    public void setVisible(final boolean b) {
        if (b && !contentCreated) {
            createContent();
            contentCreated = true;
        }
        super.setVisible(b);
    }

    // Override in subclasses
    public void createContent() {
        getContentPane().add(boxSelectionPanel, BorderLayout.NORTH);
        pack();
    }
}
