/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dma.epd.ship.layers.ais;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.SwingUtilities;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.openmap.MapBean;
import com.bbn.openmap.event.MapEventUtils;
import com.bbn.openmap.event.MapMouseListener;
import com.bbn.openmap.event.ProjectionEvent;
import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.omGraphics.OMCircle;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.OMList;

import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.prototype.ais.AisTarget;
import dk.dma.epd.common.prototype.ais.AtoNTarget;
import dk.dma.epd.common.prototype.ais.IAisTargetListener;
import dk.dma.epd.common.prototype.ais.SarTarget;
import dk.dma.epd.common.prototype.ais.VesselTarget;
import dk.dma.epd.common.prototype.gui.util.InfoPanel;
import dk.dma.epd.common.prototype.layers.ais.AisTargetSelectionGraphic;
import dk.dma.epd.common.prototype.layers.ais.AtonTargetGraphic;
import dk.dma.epd.common.prototype.layers.ais.IntendedRouteGraphic;
import dk.dma.epd.common.prototype.layers.ais.IntendedRouteLegGraphic;
import dk.dma.epd.common.prototype.layers.ais.IntendedRouteWpCircle;
import dk.dma.epd.common.prototype.layers.ais.PastTrackInfoPanel;
import dk.dma.epd.common.prototype.layers.ais.PastTrackWpCircle;
import dk.dma.epd.common.prototype.layers.ais.SarTargetGraphic;
import dk.dma.epd.common.prototype.layers.ais.SartGraphic;
import dk.dma.epd.common.prototype.layers.ais.TargetGraphic;
import dk.dma.epd.common.prototype.layers.ais.VesselTargetGraphic;
import dk.dma.epd.common.prototype.layers.ais.VesselTargetTriangle;
import dk.dma.epd.common.prototype.sensor.pnt.PntHandler;
import dk.dma.epd.common.prototype.settings.AisSettings;
import dk.dma.epd.common.prototype.settings.NavSettings;
import dk.dma.epd.common.prototype.zoom.ZoomLevel;
import dk.dma.epd.common.util.Util;
import dk.dma.epd.ship.EPDShip;
import dk.dma.epd.ship.ais.AisHandler;
import dk.dma.epd.ship.event.DragMouseMode;
import dk.dma.epd.ship.event.NavigationMouseMode;
import dk.dma.epd.ship.gui.ChartPanel;
import dk.dma.epd.ship.gui.MainFrame;
import dk.dma.epd.ship.gui.MapMenu;
import dk.dma.epd.ship.gui.TopPanel;
import dk.dma.epd.ship.gui.component_panels.AisComponentPanel;
import dk.dma.epd.ship.gui.component_panels.ShowDockableDialog;
import dk.dma.epd.ship.gui.component_panels.ShowDockableDialog.dock_type;

/**
 * AIS layer. Showing AIS targets and intended routes.
 */
@ThreadSafe
public class AisLayer extends OMGraphicHandlerLayer implements
        IAisTargetListener, Runnable, MapMouseListener {

    private static final Logger LOG = LoggerFactory.getLogger(AisLayer.class);
    private static final long serialVersionUID = 1L;

    private volatile long minRedrawInterval = 5 * 1000; // 5 sec

    private AisHandler aisHandler;
    private MapBean mapBean;
    private MainFrame mainFrame;

    private IntendedRouteInfoPanel intendedRouteInfoPanel = new IntendedRouteInfoPanel();
    private AisTargetInfoPanel aisTargetInfoPanel = new AisTargetInfoPanel();
    private SarTargetInfoPanel sarTargetInfoPanel = new SarTargetInfoPanel();
    private final PastTrackInfoPanel pastTrackInfoPanel = new PastTrackInfoPanel();
    private MapMenu aisTargetMenu;

    private Map<Long, TargetGraphic> targets = new ConcurrentHashMap<>();

    @GuardedBy("graphics")
    private OMGraphicList graphics = new OMGraphicList();

    @GuardedBy("redrawPending")
    private Date lastRedraw = new Date();
    @GuardedBy("redrawPending")
    private Boolean redrawPending = false;

    // Only accessed in event dispatch thread
    private OMGraphic closest;
    private OMGraphic selectedGraphic;
    private ChartPanel chartPanel;
    private OMCircle dummyCircle = new OMCircle();
    private AisComponentPanel aisPanel;
    private AisTargetSelectionGraphic targetSelectionGraphic = new AisTargetSelectionGraphic();

    private volatile long selectedMMSI = -1;
    private volatile boolean showLabels;
    // long selectedMMSI = 230994000;
    private final AisSettings aisSettings = EPDShip.getSettings()
            .getAisSettings();
    private final NavSettings navSettings = EPDShip.getSettings()
            .getNavSettings();

    private TopPanel topPanel;

    private ZoomLevel currentZoomLevel;
    
    public AisLayer() {
        graphics.add(targetSelectionGraphic);
        // graphics.setVague(false);
        new Thread(this).start();

        showLabels = EPDShip.getSettings().getAisSettings().isShowNameLabels();
    }

    @Override
    public void run() {
        while (true) {
            Util.sleep(1000);
            if (isRedrawPending()) {
                updateLayer();
            }
        }
    }

    private void updateLayer() {
        updateLayer(false);
    }

    private void updateLayer(boolean force) {
        if (!force) {
            long elapsed = new Date().getTime() - getLastRedraw().getTime();
            if (elapsed < minRedrawInterval) {
                return;
            }
        }
        doPrepare();
    }

    /**
     * Move the target selection and force it to be painted
     * 
     * @param aisTarget
     */
    public void updateSelection(final AisTarget aisTarget, final boolean clicked) {
        // Run only in event dispath thread
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateSelection(aisTarget, clicked);

                }
            });
            return;
        }

        // If the dock isn't visible should it show it?
        if (!EPDShip.getMainFrame().getDockableComponents()
                .isDockVisible("AIS Target")
                && clicked) {

            // Show it display the message?
            if (EPDShip.getSettings().getGuiSettings().isShowDockMessage()) {
                new ShowDockableDialog(EPDShip.getMainFrame(), dock_type.AIS);
            } else {

                if (EPDShip.getSettings().getGuiSettings().isAlwaysOpenDock()) {
                    EPDShip.getMainFrame().getDockableComponents()
                            .openDock("AIS Target");
                    EPDShip.getMainFrame().getEeINSMenuBar()
                            .refreshDockableMenu();
                }

                // It shouldn't display message but take a default action

            }

        }

        targetSelectionGraphic.setVisible(true);
        targetSelectionGraphic.moveSymbol(((VesselTarget) aisTarget)
                .getPositionData().getPos());

        // doPrepare();

        VesselTarget vessel = (VesselTarget) aisTarget;

        double rhumbLineDistance = -1.0;
        double rhumbLineBearing = -1.0;

        if (vessel.getStaticData() != null) {

            if (aisHandler.getOwnShip() != null
                    && aisHandler.getOwnShip().getPositionData() != null) {
                rhumbLineDistance = aisHandler.getOwnShip().getPositionData()
                        .getPos()
                        .rhumbLineDistanceTo(vessel.getPositionData().getPos());
                rhumbLineBearing = aisHandler.getOwnShip().getPositionData()
                        .getPos()
                        .rhumbLineBearingTo(vessel.getPositionData().getPos()

                        );
            }

            aisPanel.receiveHighlight(vessel.getMmsi(), vessel.getStaticData()
                    .getName(), vessel.getStaticData().getCallsign(), vessel
                    .getPositionData().getCog(), rhumbLineDistance,
                    rhumbLineBearing, vessel.getPositionData().getSog()

            );
        } else {

            // if (vessel.getStaticData() != null) {

            if (aisHandler.getOwnShip() != null
                    && aisHandler.getOwnShip().getPositionData() != null) {
                rhumbLineDistance = aisHandler.getOwnShip().getPositionData()
                        .getPos()
                        .rhumbLineDistanceTo(vessel.getPositionData().getPos());
                rhumbLineBearing = aisHandler.getOwnShip().getPositionData()
                        .getPos()
                        .rhumbLineBearingTo(vessel.getPositionData().getPos()

                        );
            }

            aisPanel.receiveHighlight(vessel.getMmsi(), vessel
                    .getPositionData().getCog(), rhumbLineDistance,
                    rhumbLineBearing, vessel.getPositionData().getSog());
            // }
        }
        if (vessel.getStaticData() != null && aisHandler.getOwnShip() != null
                && aisHandler.getOwnShip().getPositionData() != null) {
            aisPanel.dynamicNogoAvailable(true);
        } else {
            aisPanel.dynamicNogoAvailable(false);
        }

        doPrepare();

    }

    /**
     * Remove the selection ring
     */
    public synchronized void removeSelection() {
        // Run only in event dispath thread
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    removeSelection();
                }
            });
            return;
        }
        targetSelectionGraphic.setVisible(false);
        selectedMMSI = -1;
        aisPanel.resetHighLight();
        doPrepare();
    }

    @Override
    public void targetUpdated(AisTarget aisTarget) {
        if (aisTarget == null) {
            return;
        }
        
        long mmsi = aisTarget.getMmsi();

        TargetGraphic targetGraphic = targets.get(mmsi);
        float mapScale = (this.getProjection() == null) ? 0 : this.getProjection().getScale();

        if (aisTarget.isGone()) {

            if (targetGraphic != null) {
                // Remove target
                // LOG.info("Target has gone: " + mmsi);
                targets.remove(mmsi);
                synchronized (graphics) {
                    graphics.remove(targetGraphic);
                }
                setRedrawPending(true);
                updateLayer();

                if (mmsi == selectedMMSI) {
                    removeSelection();
                }
            }
            return;
        }

        // Create and insert
        if (targetGraphic == null) {
            if (aisTarget instanceof VesselTarget) {
                targetGraphic = new VesselTargetGraphic(showLabels);
            } else if (aisTarget instanceof SarTarget) {
                targetGraphic = new SarTargetGraphic();
            } else if (aisTarget instanceof AtoNTarget) {
                targetGraphic = new AtonTargetGraphic();
            } else {
                LOG.error("Unknown target type");
                return;
            }
            targets.put(mmsi, targetGraphic);
            synchronized (graphics) {
                graphics.add(targetGraphic);
            }
        }

        boolean forceRedraw = false;

        if (aisTarget instanceof VesselTarget) {
            // Maybe we would like to force redraw
            VesselTarget vesselTarget = (VesselTarget) aisTarget;

            VesselTargetGraphic vesselTargetGraphic = (VesselTargetGraphic) targetGraphic;
            if (vesselTarget.getSettings().isShowRoute()
                    && vesselTarget.hasIntendedRoute()
                    && !vesselTargetGraphic.getRouteGraphic().isVisible()) {
                forceRedraw = true;
            } else if (!vesselTarget.getSettings().isShowRoute()
                    && vesselTargetGraphic.getRouteGraphic().isVisible()) {
                forceRedraw = true;
            }


            if(this.currentZoomLevel == null) {
                this.currentZoomLevel = ZoomLevel.getFromScale(mapScale);
            }
            vesselTargetGraphic.update(vesselTarget, aisSettings, navSettings, mapScale);
            if (vesselTarget.getMmsi() == selectedMMSI) {
                updateSelection(aisTarget, false);
            }

        } else if (aisTarget instanceof SarTarget) {
            targetGraphic.update(aisTarget, aisSettings, navSettings, mapScale);
        } else if (aisTarget instanceof AtoNTarget) {
            targetGraphic.update(aisTarget, aisSettings, navSettings, mapScale);
        }

        // Handle past track
        
        
        targetGraphic.project(getProjection());

        setRedrawPending(true);
        updateLayer(forceRedraw);
    }

    private void setRedrawPending(boolean val) {
        synchronized (redrawPending) {
            redrawPending = val;
            if (!val) {
                lastRedraw = new Date();
            }
        }
    }

    public boolean isRedrawPending() {
        synchronized (redrawPending) {
            return redrawPending;
        }
    }

    private Date getLastRedraw() {
        synchronized (redrawPending) {
            return lastRedraw;
        }
    }

    @Override
    public OMGraphicList prepare() {
        // long start = System.nanoTime();
        Iterator<TargetGraphic> it = targets.values().iterator();

        synchronized (graphics) {
            for (OMGraphic omgraphic : graphics) {
                if (omgraphic instanceof IntendedRouteGraphic) {
                    ((IntendedRouteGraphic) omgraphic)
                            .showArrowHeads(getProjection().getScale() < EPDShip
                                    .getSettings().getNavSettings()
                                    .getShowArrowScale());
                }
            }
        }

        while (it.hasNext()) {
            TargetGraphic target = it.next();
            target.setMarksVisible(getProjection(), aisSettings, navSettings);
        }

        setRedrawPending(false);
        synchronized (graphics) {
            graphics.project(getProjection());
        }
        // System.out.println("Finished AisLayer.prepare() in " +
        // EeINS.elapsed(start) + " ms\n---");
        return graphics;
    }

    public long getMinRedrawInterval() {
        return minRedrawInterval;
    }

    public void setMinRedrawInterval(long minRedrawInterval) {
        this.minRedrawInterval = minRedrawInterval;
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        setRedrawPending(false);
    }

    @Override
    public void findAndInit(Object obj) {
        if (obj instanceof AisComponentPanel) {
            aisPanel = (AisComponentPanel) obj;
        }
        if (obj instanceof AisHandler) {
            aisHandler = (AisHandler) obj;
            aisHandler.addListener(this);
        }
        if (obj instanceof MapBean) {
            mapBean = (MapBean) obj;
        }
        if (obj instanceof MainFrame) {
            mainFrame = (MainFrame) obj;
            mainFrame.getGlassPanel().add(intendedRouteInfoPanel);
            mainFrame.getGlassPanel().add(aisTargetInfoPanel);
            mainFrame.getGlassPanel().add(sarTargetInfoPanel);
            mainFrame.getGlassPanel().add(pastTrackInfoPanel);
        }
        if (obj instanceof PntHandler) {
            sarTargetInfoPanel.setPntHandler((PntHandler) obj);
        }
        if (obj instanceof MapMenu) {
            aisTargetMenu = (MapMenu) obj;
        }
        if (obj instanceof ChartPanel) {
            chartPanel = (ChartPanel) obj;
        }
        if (obj instanceof TopPanel) {
            topPanel = (TopPanel) obj;
        }
    }

    @Override
    public void findAndUndo(Object obj) {
        if (obj == aisHandler) {
            aisHandler.removeListener(this);
        }
    }

    @Override
    public MapMouseListener getMapMouseListener() {
        return this;
    }

    @Override
    public String[] getMouseModeServiceList() {
        String[] ret = new String[2];
        ret[0] = NavigationMouseMode.MODE_ID; // "Gestures"
        ret[1] = DragMouseMode.MODE_ID;
        return ret;
    }

    @Override
    public boolean mouseClicked(MouseEvent e) {
        if (this.isVisible()) {
            if (e.getButton() == MouseEvent.BUTTON3
                    || e.getButton() == MouseEvent.BUTTON1) {
                selectedGraphic = null;
                OMList<OMGraphic> allClosest;
                synchronized (graphics) {
                    allClosest = graphics.findAll(e.getX(), e.getY(), 5.0f);
                }
                for (OMGraphic omGraphic : allClosest) {
                    if (omGraphic instanceof IntendedRouteWpCircle
                            || omGraphic instanceof VesselTargetTriangle
                            || omGraphic instanceof IntendedRouteLegGraphic
                            || omGraphic instanceof SartGraphic) {
                        selectedGraphic = omGraphic;
                        break;
                    }
                }
                if (e.getButton() == MouseEvent.BUTTON1) {

                    if (allClosest.size() == 0) {
                        removeSelection();
                    }

                    if (selectedGraphic instanceof VesselTargetTriangle) {
                        VesselTargetTriangle vtt = (VesselTargetTriangle) selectedGraphic;
                        VesselTargetGraphic vesselTargetGraphic = vtt
                                .getVesselTargetGraphic();

                        selectedMMSI = vesselTargetGraphic.getVesselTarget()
                                .getMmsi();
                        updateSelection(vesselTargetGraphic.getVesselTarget(),
                                true);
                    }
                }

                if (e.getButton() == MouseEvent.BUTTON3) {

                    if (selectedGraphic instanceof VesselTargetTriangle) {

                        VesselTargetTriangle vtt = (VesselTargetTriangle) selectedGraphic;
                        VesselTargetGraphic vesselTargetGraphic = vtt
                                .getVesselTargetGraphic();

                        // mainFrame.getGlassPane().setVisible(false);
                        aisTargetMenu.aisMenu(vesselTargetGraphic, topPanel);
                        aisTargetMenu.setVisible(true);
                        aisTargetMenu.show(this, e.getX() - 2, e.getY() - 2);
                        aisTargetInfoPanel.setVisible(false);
                        return true;
                    } else if (selectedGraphic instanceof IntendedRouteWpCircle) {
                        IntendedRouteWpCircle wpCircle = (IntendedRouteWpCircle) selectedGraphic;
                        VesselTarget vesselTarget = wpCircle
                                .getIntendedRouteGraphic().getVesselTarget();
                        mainFrame.getGlassPane().setVisible(false);
                        aisTargetMenu.aisSuggestedRouteMenu(vesselTarget);
                        aisTargetMenu.setVisible(true);
                        aisTargetMenu.show(this, e.getX() - 2, e.getY() - 2);
                        aisTargetInfoPanel.setVisible(false);
                        return true;
                    } else if (selectedGraphic instanceof IntendedRouteLegGraphic) {
                        IntendedRouteLegGraphic wpCircle = (IntendedRouteLegGraphic) selectedGraphic;
                        VesselTarget vesselTarget = wpCircle
                                .getIntendedRouteGraphic().getVesselTarget();
                        mainFrame.getGlassPane().setVisible(false);
                        aisTargetMenu.aisSuggestedRouteMenu(vesselTarget);
                        aisTargetMenu.setVisible(true);
                        aisTargetMenu.show(this, e.getX() - 2, e.getY() - 2);
                        aisTargetInfoPanel.setVisible(false);
                        return true;
                    } else if (selectedGraphic instanceof SartGraphic) {
                        SartGraphic sartGraphic = (SartGraphic) selectedGraphic;
                        SarTarget sarTarget = sartGraphic.getSarTargetGraphic()
                                .getSarTarget();
                        mainFrame.getGlassPane().setVisible(false);
                        aisTargetMenu.sartMenu(this, sarTarget);
                        aisTargetMenu.setVisible(true);
                        aisTargetMenu.show(this, e.getX() - 2, e.getY() - 2);
                        sarTargetInfoPanel.setVisible(false);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseEvent e) {
        return false;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseMoved() {
    }

    /**
     * Sets the given {@code closest} graphics as the new closest.
     * <p>
     * Hides all {@linkplain InfoPanel} panels except the {@code visiblePanel} if this is specified.
     * 
     * @param visiblePanel the panel <i>not to hide</i>.
     */
    private void setActiveInfoPanel(OMGraphic closest, InfoPanel visiblePanel) {
        this.closest = closest;
        InfoPanel[] infoPanels = { intendedRouteInfoPanel, aisTargetInfoPanel, sarTargetInfoPanel, pastTrackInfoPanel };
        for (InfoPanel infoPanel : infoPanels) {
            if (infoPanel != visiblePanel) {
                infoPanel.setVisible(false);
            }
        }
        mainFrame.getGlassPane().setVisible(visiblePanel != null);
    }
    
    /**
     * Handle mouse moved
     */
    @Override
    public boolean mouseMoved(MouseEvent e) {
        if (!this.isVisible()) {
            setActiveInfoPanel(null, null);
            return false;
        }

        OMGraphic newClosest = MapEventUtils.getSelectedGraphic(
                graphics, 
                e, 
                3.0f, 
                IntendedRouteWpCircle.class, 
                IntendedRouteLegGraphic.class, 
                VesselTargetTriangle.class, 
                SartGraphic.class, 
                AtonTargetGraphic.class, 
                PastTrackWpCircle.class);

        if (newClosest != closest) {
            Point containerPoint = SwingUtilities.convertPoint(mapBean,
                    e.getPoint(), mainFrame);

            if (newClosest instanceof IntendedRouteWpCircle) {
                IntendedRouteWpCircle wpCircle = (IntendedRouteWpCircle) newClosest;
                intendedRouteInfoPanel.setPos((int) containerPoint.getX(),
                        (int) containerPoint.getY() - 10);
                intendedRouteInfoPanel.showWpInfo(wpCircle);
                setActiveInfoPanel(newClosest, intendedRouteInfoPanel);
                return true;
                
            } else if (newClosest instanceof PastTrackWpCircle) {
                PastTrackWpCircle wpCircle = (PastTrackWpCircle) newClosest;
                pastTrackInfoPanel.setPos((int) containerPoint.getX(), (int) containerPoint.getY() - 10);
                pastTrackInfoPanel.showWpInfo(wpCircle);
                setActiveInfoPanel(newClosest, pastTrackInfoPanel);
                return true;
                
            } else if (newClosest instanceof IntendedRouteLegGraphic) {
                // lets user see ETA continually along route leg
                Point2D worldLocation = chartPanel.getMap().getProjection()
                        .inverse(e.getPoint());
                IntendedRouteLegGraphic legGraphic = (IntendedRouteLegGraphic) newClosest;
                intendedRouteInfoPanel.setPos((int) containerPoint.getX(),
                        (int) containerPoint.getY() - 10);
                intendedRouteInfoPanel.showLegInfo(legGraphic, worldLocation);
                setActiveInfoPanel(dummyCircle, intendedRouteInfoPanel);
                return true;
                
            } else if (newClosest instanceof VesselTargetTriangle) {
                VesselTargetTriangle vesselTargetTriangle = (VesselTargetTriangle) newClosest;
                VesselTarget vesselTarget = vesselTargetTriangle
                        .getVesselTargetGraphic().getVesselTarget();
                aisTargetInfoPanel.setPos((int) containerPoint.getX(),
                        (int) containerPoint.getY() - 10);
                aisTargetInfoPanel.showAisInfo(vesselTarget);
                setActiveInfoPanel(newClosest, aisTargetInfoPanel);
                return true;
                
            } else if (newClosest instanceof SartGraphic) {
                SartGraphic sartGraphic = (SartGraphic) newClosest;
                sarTargetInfoPanel.setPos((int) containerPoint.getX(),
                        (int) containerPoint.getY() - 10);
                sarTargetInfoPanel.showSarInfo(sartGraphic
                        .getSarTargetGraphic().getSarTarget());
                setActiveInfoPanel(newClosest, sarTargetInfoPanel);
                return true;
                
            } else if (newClosest instanceof AtonTargetGraphic) {
                AtonTargetGraphic aton = (AtonTargetGraphic) newClosest;
                AtoNTarget atonTarget = aton.getAtonTarget();
                aisTargetInfoPanel.setPos((int) containerPoint.getX(),
                        (int) containerPoint.getY() - 10);
                aisTargetInfoPanel.showAtonInfo(atonTarget);
                setActiveInfoPanel(newClosest, aisTargetInfoPanel);
                return true;
                
            } else {
                setActiveInfoPanel(null, null);
            }
        }
        return false;
    }

    @Override
    public boolean mousePressed(MouseEvent e) {
        return false;
    }

    @Override
    public boolean mouseReleased(MouseEvent e) {
        return false;
    }

    public void zoomTo(Position position) {
        mapBean.setCenter(position.getLatitude(), position.getLongitude());
        // mapBean.setScale(EeINS.getSettings().getEnavSettings().getMsiTextboxesVisibleAtScale());
    }

    /**
     * Sets whether to show the vessel name labels or not
     * 
     * @param showLabels show the vessel name labels or not
     */
    public void setShowNameLabels(boolean showLabels) {
        if (this.showLabels == showLabels) {
            return;
        }
        
        this.showLabels = showLabels;
        for (TargetGraphic value : targets.values()) {

            if (value instanceof VesselTargetGraphic) {
                ((VesselTargetGraphic) value).setShowNameLabel(showLabels);
                targetUpdated(((VesselTargetGraphic) value).getVesselTarget());
            }
            doPrepare();
        }

    }

    @Override
    public void projectionChanged(ProjectionEvent pe) {
        super.projectionChanged(pe);
        // the new zoom level
        ZoomLevel updatedZl = ZoomLevel.getFromScale(pe.getProjection().getScale());
        // log if zoom level was changed
        boolean zoomChanged = this.currentZoomLevel != updatedZl;
        this.currentZoomLevel = updatedZl;
        if(zoomChanged) {
            // only need to redraw vessels if zoom level changed
            for(TargetGraphic tg : this.targets.values()) {
                if(tg instanceof VesselTargetGraphic) {
                    ((VesselTargetGraphic)tg).drawAccordingToScale(this.currentZoomLevel);
                }
            }
            // force redraw
            this.updateLayer(true);
        }
    }
}
