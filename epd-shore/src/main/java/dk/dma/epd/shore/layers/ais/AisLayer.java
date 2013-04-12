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
package dk.dma.epd.shore.layers.ais;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.openmap.event.MapMouseListener;
import com.bbn.openmap.event.ProjectionEvent;
import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.OMList;

import dk.dma.epd.common.prototype.ais.AisTarget;
import dk.dma.epd.common.prototype.ais.AtoNTarget;
import dk.dma.epd.common.prototype.ais.IAisTargetListener;
import dk.dma.epd.common.prototype.ais.SarTarget;
import dk.dma.epd.common.prototype.ais.VesselTarget;
import dk.dma.epd.common.prototype.layers.ais.AisTargetGraphic;
import dk.dma.epd.common.prototype.layers.ais.AtonTargetGraphic;
import dk.dma.epd.common.prototype.layers.ais.IntendedRouteGraphic;
import dk.dma.epd.common.prototype.layers.ais.IntendedRouteLegGraphic;
import dk.dma.epd.common.prototype.layers.ais.IntendedRouteWpCircle;
import dk.dma.epd.common.prototype.layers.ais.SarTargetGraphic;
import dk.dma.epd.common.prototype.layers.ais.TargetGraphic;
import dk.dma.epd.common.prototype.settings.AisSettings;
import dk.dma.epd.common.prototype.settings.NavSettings;
import dk.dma.epd.shore.EPDShore;
import dk.dma.epd.shore.ais.AisHandler;
import dk.dma.epd.shore.event.DragMouseMode;
import dk.dma.epd.shore.event.NavigationMouseMode;
import dk.dma.epd.shore.event.SelectMouseMode;
import dk.dma.epd.shore.gui.views.ChartPanel;
import dk.dma.epd.shore.gui.views.JMapFrame;
import dk.dma.epd.shore.gui.views.MapMenu;
import dk.dma.epd.shore.gui.views.StatusArea;

/**
 * The class AisLayer is the layer containing all AIS targets. The class handles
 * the drawing of vessels on the chartPanel.
 */
public class AisLayer extends OMGraphicHandlerLayer implements
        IAisTargetListener, Runnable, MapMouseListener, AisSelectionListener {

    private static final Logger LOG = LoggerFactory.getLogger(AisLayer.class);
    private static final long serialVersionUID = 1L;

    private long minRedrawInterval = 5 * 1000; // 5 sec

    private AisLayerHandler aisLayerHandler;
    private AisHandler aisHandler;
    // private MainFrame mainFrame;
    private IntendedRouteInfoPanel intendedRouteInfoPanel = new IntendedRouteInfoPanel();
    private AisInfoPanel aisInfoPanel;
    private MapMenu aisTargetMenu;

    private HashMap<Long, TargetGraphic> targets = new HashMap<Long, TargetGraphic>();
    private OMGraphicList graphics = new OMGraphicList();

    private Date lastRedraw = new Date();
    private Boolean redrawPending = false;

    private OMGraphic closest;
    private ChartPanel chartPanel;

    AisTargetGraphic aisTargetGraphic = new AisTargetGraphic();

    private AisSettings aisSettings = EPDShore.getSettings().getAisSettings();
    private NavSettings navSettings = EPDShore.getSettings().getNavSettings();
    volatile boolean shouldRun = true;

    private StatusArea statusArea;
    private JMapFrame jMapFrame;

    // private List<AisMessageExtended> shipList;

    // private Vessel vesselComponent;
    // private VesselPositionData location;

    // private float mapScale;
    // private boolean selectionOnScreen;

    private PastTrackInfoPanel pastTrackInfoPanel = new PastTrackInfoPanel();

    Thread aisThread;

    // private OMGraphic highlighted;
    // private VesselLayer highlightedVessel;

    /**
     * Starts the AisLayer thread
     */
    public AisLayer() {
        graphics.add(aisTargetGraphic);
        aisThread = new Thread(this);
        aisThread.start();
    }

    /**
     * Keeps the AisLayer thread alive
     */
    // @Override
    // public void run() {
    //
    // while (shouldRun) {
    // try {
    // // drawVessels();
    // Thread.sleep(1000);
    // } catch (InterruptedException e) {
    // drawVessels();
    // }
    //
    // }
    // drawnVessels.clear();
    // graphics.clear();
    // graphics.add(aisTargetGraphic);
    // }

    // updateIcons

    @Override
    public void run() {
        while (shouldRun) {

            try {
                Thread.sleep(1000);
                if (isRedrawPending()) {
                    updateLayer();
                }
            } catch (InterruptedException e) {
                System.out.println("Updating from interrupt");
                // updateIcons();
            }
        }
        aisLayerHandler.removeSelectionListener(this);
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

    public void updateIcons() {
        for (TargetGraphic vessel : targets.values()) {
            ((ShoreTargetGraphic) vessel)
                    .update(chartPanel.getMap().getScale());
        }
    }

    /**
     * Move the target selection and force it to be painted
     * 
     * @param aisTarget
     */
    public void updateSelection(AisTarget aisTarget) {

        aisTargetGraphic.setVisible(true);
        aisTargetGraphic.moveSymbol(((VesselTarget) aisTarget)
                .getPositionData().getPos());

        doPrepare();

    }

    /**
     * Remove the selection ring
     */
    public void removeSelection(boolean internal) {
        aisTargetGraphic.setVisible(false);

        if (aisLayerHandler.getSelectedMMSI() != -1
                && targets.containsKey(aisLayerHandler.getSelectedMMSI())) {

            ((ShoreTargetGraphic) targets
                    .get(aisLayerHandler.getSelectedMMSI()))
                    .getPastTrackGraphic().setVisible(false);

        } else {
            for (TargetGraphic vessel : targets.values()) {

                if (vessel instanceof ShoreTargetGraphic) {
                    ((ShoreTargetGraphic) vessel).getPastTrackGraphic()
                            .setVisible(false);
                }
            }

        }
        if (!internal) {
            aisLayerHandler.setSelectedMMSI(-1, this);
        }

        // selectedMMSI = -1;

        statusArea.removeHighlight();

        doPrepare();
    }

    @Override
    public synchronized void targetUpdated(AisTarget aisTarget) {

        long mmsi = aisTarget.getMmsi();

        TargetGraphic targetGraphic = targets.get(mmsi);

        if (aisTarget.isGone()) {

            if (targetGraphic != null) {
                // Remove target
                // LOG.info("Target has gone: " + mmsi);
                targets.remove(mmsi);
                graphics.remove(targetGraphic);
                setRedrawPending(true);
                updateLayer();

                if (mmsi == aisLayerHandler.getSelectedMMSI()) {
                    removeSelection(false);
                }
            }
            return;
        }

        // Create and insert
        if (targetGraphic == null) {
            if (aisTarget instanceof VesselTarget) {
                // targetGraphic = new VesselTargetGraphic(showLabels);
                targetGraphic = new ShoreTargetGraphic(true);
            } else if (aisTarget instanceof SarTarget) {
                targetGraphic = new SarTargetGraphic();
            } else if (aisTarget instanceof AtoNTarget) {
                targetGraphic = new AtonTargetGraphic();
            } else {
                LOG.error("Unknown target type");
                return;
            }
            targets.put(mmsi, targetGraphic);
            graphics.add(targetGraphic);

        }

        boolean forceRedraw = false;

        if (aisTarget instanceof VesselTarget) {
            // Maybe we would like to force redraw
            VesselTarget vesselTarget = (VesselTarget) aisTarget;

            ShoreTargetGraphic vesselTargetGraphic = (ShoreTargetGraphic) targetGraphic;
            if (vesselTarget.getSettings().isShowRoute()
                    && vesselTarget.hasIntendedRoute()
                    && !vesselTargetGraphic.getRouteGraphic().isVisible()) {
                System.out.println("Intended route on TARGET");
                forceRedraw = true;
            } else if (!vesselTarget.getSettings().isShowRoute()
                    && vesselTargetGraphic.getRouteGraphic().isVisible()) {
                forceRedraw = true;
            }

            targetGraphic.update(vesselTarget, aisSettings, navSettings,
                    chartPanel.getMap().getScale());

            if (aisLayerHandler != null && aisHandler != null) {

                if (vesselTarget.getMmsi() == aisLayerHandler.getSelectedMMSI()) {
                    updateSelection(aisTarget);
                    vesselTargetGraphic.updatePastTrack(aisHandler
                            .getPastTrack().get(
                                    aisLayerHandler.getSelectedMMSI()));
                }
            }

        } else if (aisTarget instanceof SarTarget) {
            targetGraphic.update(aisTarget, aisSettings, navSettings,
                    chartPanel.getMap().getScale());
        } else if (aisTarget instanceof AtoNTarget) {
            targetGraphic.update(aisTarget, aisSettings, navSettings,
                    chartPanel.getMap().getScale());
        }

        targetGraphic.project(getProjection());

        // System.out.println("targets.size() : " + targets.size());
        // System.out.println("graphics.size(): " + graphics.size() + "\n---");

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
    public synchronized OMGraphicList prepare() {
        // long start = System.nanoTime();
        Iterator<TargetGraphic> it = targets.values().iterator();

        for (OMGraphic omgraphic : graphics) {
            if (omgraphic instanceof IntendedRouteGraphic) {
                ((IntendedRouteGraphic) omgraphic)
                        .showArrowHeads(getProjection().getScale() < EPDShore
                                .getSettings().getNavSettings()
                                .getShowArrowScale());
            }
        }

        while (it.hasNext()) {
            TargetGraphic target = it.next();
            target.setMarksVisible(getProjection(), aisSettings, navSettings);
        }

        setRedrawPending(false);
        graphics.project(getProjection());
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
        // long start = System.nanoTime();
        super.paint(g);
        setRedrawPending(false);
        // System.out.println("Finished AisLayer.paint() in " +
        // EeINS.elapsed(start) + " ms\n---");
    }

    // @Override
    // public void findAndUndo(Object obj) {
    // System.out.println(obj);
    // if (obj == aisHandler) {
    // aisHandler.removeListener(this);
    // System.out.println("Removing listener");
    // }
    // }

    @Override
    public MapMouseListener getMapMouseListener() {
        return this;
    }

    @Override
    public String[] getMouseModeServiceList() {
        String[] ret = new String[3];
        ret[0] = DragMouseMode.MODEID; // "DragMouseMode"
        ret[1] = NavigationMouseMode.MODEID; // "ZoomMouseMode"
        ret[2] = SelectMouseMode.MODEID; // "SelectMouseMode"
        return ret;
    }

    public Thread getAisThread() {
        return aisThread;
    }

    public void setAisThread(Thread aisThread) {
        this.aisThread = aisThread;
    }

    /**
     * Kills the AisLayer thread
     */
    public void stop() {
        shouldRun = false;
    }

    /**
     * Clears all targets from the map and in the local memory
     */
    public void mapClearTargets() {
        graphics.clear();
        graphics.add(aisTargetGraphic);
        targets.clear();
    }

    //
    // /**
    // * Draws or updates the vessels on the map
    // */
    // private void drawVessels() {
    // if (aisHandler != null) {
    //
    // selectionOnScreen = false;
    //
    // if (chartPanel != null) {
    //
    // if (chartPanel.getMap().getScale() != mapScale) {
    // mapScale = chartPanel.getMap().getScale();
    // mapClearTargets();
    // }
    //
    // // if ((highlightedMMSI != 0 && highlightedMMSI !=
    // // statusArea.getHighlightedVesselMMSI())
    // // || statusArea.getHighlightedVesselMMSI() == -1) {
    // // highlightInfoPanel.setVisible(false);
    // // highlighted = null;
    // // highlightedMMSI = 0;
    // // }
    //
    // Point2D lr = chartPanel.getMap().getProjection()
    // .getLowerRight();
    // Point2D ul = chartPanel.getMap().getProjection().getUpperLeft();
    // double lrlat = lr.getY();
    // double lrlon = lr.getX();
    // double ullat = ul.getY();
    // double ullon = ul.getX();
    //
    // shipList = aisHandler.getShipList();
    // for (int i = 0; i < shipList.size(); i++) {
    // if (aisHandler.getVesselTargets().containsKey(
    // shipList.get(i).MMSI)) {
    // // Get information
    // AisMessageExtended vessel = shipList.get(i);
    // VesselTarget vesselTarget = aisHandler
    // .getVesselTargets().get(vessel.MMSI);
    // location = vesselTarget.getPositionData();
    //
    // // Check if vessel is near map coordinates or it's
    // // sending
    // // an intended route
    // boolean t1 = location.getPos().getLatitude() >= lrlat;
    // boolean t2 = location.getPos().getLatitude() <= ullat;
    // boolean t3 = location.getPos().getLongitude() >= ullon;
    // boolean t4 = location.getPos().getLongitude() <= lrlon;
    //
    // if (!(t1 && t2 && t3 && t4)) {
    //
    // if (!vesselTarget.hasIntendedRoute()) {
    // continue;
    // }
    // }
    //
    // double trueHeading = location.getTrueHeading();
    // if (trueHeading == 511) {
    // trueHeading = location.getCog();
    // }
    //
    // if (!targets.containsKey(vessel.MMSI)) {
    // vesselComponent = new Vessel(vessel.MMSI);
    // graphics.add(vesselComponent);
    // targets.put(vessel.MMSI, vesselComponent);
    // }
    // targets.get(vessel.MMSI).updateLayers(trueHeading,
    // location.getPos().getLatitude(),
    // location.getPos().getLongitude(),
    // vesselTarget.getStaticData(),
    // location.getSog(),
    // Math.toRadians(location.getCog()), mapScale,
    // vesselTarget);
    //
    // if (vesselTarget.getMmsi() == mainFrame
    // .getSelectedMMSI()) {
    // aisTargetGraphic.moveSymbol(vesselTarget
    // .getPositionData().getPos());
    // selectionOnScreen = true;
    //
    // // if (mainFrame.getSelectedMMSI() != -1 &&
    // // drawnVessels.containsKey(mainFrame.getSelectedMMSI())){
    // targets
    // .get(mainFrame.getSelectedMMSI())
    // .updatePastTrack(
    // aisHandler.getPastTrack()
    // .get(mainFrame
    // .getSelectedMMSI()));
    // // System.out.println("hide it");
    // // }
    //
    // setStatusAreaTxt();
    // }
    //
    // }
    //
    // }
    // }
    //
    // // if (mainFrame.getSelectedMMSI() != -1 &&
    // // drawnVessels.containsKey(mainFrame.getSelectedMMSI())){
    // //
    // drawnVessels.get(mainFrame.getSelectedMMSI()).updatePastTrack(aisHandler.getPastTrack().get(mainFrame.getSelectedMMSI()));
    // // System.out.println("hide it");
    // // }
    //
    // if (!selectionOnScreen) {
    // aisTargetGraphic.setVisible(false);
    //
    // for (Vessel vessel : targets.values()) {
    // vessel.getPastTrackGraphic().setVisible(false);
    // }
    // }
    //
    // doPrepare();
    // }
    // }

    @Override
    public void findAndInit(Object obj) {
        if (obj instanceof AisLayerHandler) {
            aisLayerHandler = (AisLayerHandler) obj;
            aisLayerHandler.addSelectionListener(this);
        }
        if (obj instanceof AisHandler) {
            aisHandler = (AisHandler) obj;
            aisHandler.addListener(this);
        }
        if (obj instanceof ChartPanel) {
            chartPanel = (ChartPanel) obj;
        }
        if (obj instanceof StatusArea) {
            statusArea = (StatusArea) obj;
        }
        if (obj instanceof JMapFrame) {
            jMapFrame = (JMapFrame) obj;
            // highlightInfoPanel = new HighlightInfoPanel();
            // jMapFrame.getGlassPanel().add(highlightInfoPanel);
            aisInfoPanel = new AisInfoPanel();
            jMapFrame.getGlassPanel().add(aisInfoPanel);
            jMapFrame.getGlassPanel().add(intendedRouteInfoPanel);
            jMapFrame.getGlassPanel().add(pastTrackInfoPanel);
            jMapFrame.getGlassPanel().setVisible(true);
        }
        // if (obj instanceof MainFrame) {
        // mainFrame = (MainFrame) obj;
        // }
        if (obj instanceof MapMenu) {
            aisTargetMenu = (MapMenu) obj;
        }
    }

    @Override
    public boolean mouseReleased(MouseEvent e) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean mouseClicked(MouseEvent e) {
        OMGraphic newClosest = null;
        OMList<OMGraphic> allClosest = graphics.findAll(e.getX(), e.getY(),
                3.0f);

        for (OMGraphic omGraphic : allClosest) {

            if (omGraphic instanceof VesselLayer
                    || omGraphic instanceof IntendedRouteWpCircle
                    || omGraphic instanceof IntendedRouteLegGraphic) {
                newClosest = omGraphic;
                break;
            }
        }

        if (e.getButton() == MouseEvent.BUTTON1) {

            if (allClosest.size() == 0) {
                removeSelection(false);
            }

            if (newClosest != null && newClosest instanceof VesselLayer) {

                VesselLayer vtt = (VesselLayer) newClosest;
                ShoreTargetGraphic vesselTargetGraphic = vtt.getVessel();

                // Hide previous
                if (aisLayerHandler.getSelectedMMSI() != -1
                        && targets.containsKey(aisLayerHandler
                                .getSelectedMMSI())) {
                    ((ShoreTargetGraphic) targets.get(aisLayerHandler
                            .getSelectedMMSI())).getPastTrackGraphic()
                            .setVisible(false);
                }

                aisLayerHandler.setSelectedMMSI(vesselTargetGraphic
                        .getVesselTarget().getMmsi(), this);

                aisTargetGraphic.setVisible(true);

                aisTargetGraphic.moveSymbol(vesselTargetGraphic
                        .getVesselTarget().getPositionData().getPos());

                long mmsi = vesselTargetGraphic.getVesselTarget().getMmsi();

                // Hide all past tracks
                // if (aisLayerHandler.getSelectedMMSI() != -1
                // && targets.containsKey(aisLayerHandler.getSelectedMMSI())) {
                // ((ShoreTargetGraphic) targets.get(aisLayerHandler
                // .getSelectedMMSI())).getPastTrackGraphic()
                // .setVisible(false);
                // System.out.println("Hiding?");
                //
                // }

                if (aisHandler.getPastTrack().get(mmsi) != null) {
                    //
                    ((VesselLayer) newClosest).getVessel().updatePastTrack(
                            aisHandler.getPastTrack().get(mmsi));
                    //
                    // // for (int i = 0; i <
                    // // aisHandler.getPastTrack().get(mmsi).size(); i++) {
                    // //
                    // System.out.println(aisHandler.getPastTrack().get(mmsi).get(i));
                    // // }
                }

                doPrepare();

                setStatusAreaTxt();

            }

        }

        if (e.getButton() == MouseEvent.BUTTON3 && newClosest != null) {

            if (newClosest instanceof VesselLayer) {

                VesselLayer vesselLayer = (VesselLayer) newClosest;

                aisTargetMenu
                        .aisMenu(vesselLayer.getVessel().getVesselTarget());

                // aisTargetMenu.aisSuggestedRouteMenu(vesselLayer.getVessel().getVesselTarget());

                aisTargetMenu.setVisible(true);
                aisTargetMenu.show(this, e.getX() - 2, e.getY() - 2);

                return true;

            } else if (newClosest instanceof IntendedRouteWpCircle) {

                IntendedRouteWpCircle wpCircle = (IntendedRouteWpCircle) newClosest;
                VesselTarget vesselTarget = wpCircle.getIntendedRouteGraphic()
                        .getVesselTarget();

                aisTargetMenu.aisSuggestedRouteMenu(vesselTarget);
                aisTargetMenu.setVisible(true);
                aisTargetMenu.show(this, e.getX() - 2, e.getY() - 2);

                return true;
            } else if (newClosest instanceof IntendedRouteLegGraphic) {

                IntendedRouteLegGraphic wpCircle = (IntendedRouteLegGraphic) newClosest;
                VesselTarget vesselTarget = wpCircle.getIntendedRouteGraphic()
                        .getVesselTarget();
                aisTargetMenu.aisSuggestedRouteMenu(vesselTarget);
                aisTargetMenu.setVisible(true);
                aisTargetMenu.show(this, e.getX() - 2, e.getY() - 2);

                return true;
            }

        }
        return false;
    }

    private void setStatusAreaTxt() {
        HashMap<String, String> info = new HashMap<String, String>();
        ShoreTargetGraphic vessel = (ShoreTargetGraphic) this.targets
                .get(aisLayerHandler.getSelectedMMSI());
        if (vessel != null) {

            info.put("MMSI", Long.toString(vessel.getMMSI()));
            info.put("Name", vessel.getName());
            info.put("COG", vessel.getHeading());
            info.put("Call sign", vessel.getCallSign());
            info.put("LAT", vessel.getLat());
            info.put("LON", vessel.getLon());
            info.put("SOG", vessel.getSog());
            info.put("ETA", vessel.getEta());
            info.put("Type", vessel.getShipType());
            statusArea.receiveHighlight(info, vessel.getMMSI());

        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseExited(MouseEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean mouseDragged(MouseEvent e) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean mouseMoved(MouseEvent e) {
        OMGraphic newClosest = null;
        OMList<OMGraphic> allClosest = graphics.findAll(e.getX(), e.getY(),
                3.0f);
        for (OMGraphic omGraphic : allClosest) {
            newClosest = omGraphic;
            break;
        }

        if (allClosest.size() == 0) {
            aisInfoPanel.setVisible(false);
            intendedRouteInfoPanel.setVisible(false);
            pastTrackInfoPanel.setVisible(false);
            closest = null;
            return false;
        }

        if (newClosest != closest) {
            Point containerPoint = SwingUtilities.convertPoint(chartPanel,
                    e.getPoint(), jMapFrame);

            if (newClosest instanceof PastTrackWpCircle) {
                closest = newClosest;
                PastTrackWpCircle wpCircle = (PastTrackWpCircle) newClosest;
                pastTrackInfoPanel.setPos((int) containerPoint.getX(),
                        (int) containerPoint.getY() - 10);
                pastTrackInfoPanel.showWpInfo(wpCircle);
                pastTrackInfoPanel.setVisible(true);
            }

            if (newClosest instanceof IntendedRouteWpCircle) {
                closest = newClosest;
                IntendedRouteWpCircle wpCircle = (IntendedRouteWpCircle) newClosest;
                intendedRouteInfoPanel.setPos((int) containerPoint.getX(),
                        (int) containerPoint.getY() - 10);
                intendedRouteInfoPanel.showWpInfo(wpCircle);
            }

            if (newClosest instanceof VesselLayer) {
                jMapFrame.getGlassPane().setVisible(true);
                closest = newClosest;
                VesselLayer vessel = (VesselLayer) newClosest;
                int x = (int) containerPoint.getX() + 10;
                int y = (int) containerPoint.getY() + 10;
                ShoreTargetGraphic shoreTargetGraphic = (ShoreTargetGraphic) this.targets
                        .get(vessel.getMMSI());
                aisInfoPanel.showAisInfo(shoreTargetGraphic);
                if (chartPanel.getMap().getProjection().getWidth() - x < aisInfoPanel
                        .getWidth()) {
                    x -= aisInfoPanel.getWidth() + 20;
                }
                if (chartPanel.getMap().getProjection().getHeight() - y < aisInfoPanel
                        .getHeight()) {
                    y -= aisInfoPanel.getHeight() + 20;
                }
                aisInfoPanel.setPos(x, y);
                aisInfoPanel.setVisible(true);

                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseMoved() {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean mousePressed(MouseEvent arg0) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void selectionChanged(long mmsi) {
        if (mmsi == -1) {
            removeSelection(true);
        } else {
            for (TargetGraphic vessel : targets.values()) {

                if (vessel instanceof ShoreTargetGraphic){
                ((ShoreTargetGraphic) vessel).getPastTrackGraphic().setVisible(
                        false);
                }
            }

            updateSelection(aisHandler.getTarget(mmsi));

            if (aisHandler.getPastTrack().get(mmsi) != null && aisHandler.getPastTrack().get(mmsi) != null) {
                ((ShoreTargetGraphic) targets.get(mmsi))
                        .updatePastTrack(aisHandler.getPastTrack().get(mmsi));
            }

        }

    }

    @Override
    public void projectionChanged(ProjectionEvent e) {
        super.projectionChanged(e);
        updateIcons();

    }
}
