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

import java.util.ArrayList;
import java.util.List;

import dk.dma.epd.shore.EPDShore;

public class AisLayerHandler implements Runnable {

    protected List<AisSelectionListener> selectionListeners = new ArrayList<AisSelectionListener>();
    private long selectedMMSI = -1;

    /**
     * Constructor
     */
    public AisLayerHandler() {
        EPDShore.startThread(this, "AisLayerHandler");
    }

    @Override
    public void run() {
        while (true) {
            EPDShore.sleep(30000);
        }
    }

    public synchronized void setSelectedMMSI(long selectedMMSI,
            AisLayer aisLayer) {
        this.selectedMMSI = selectedMMSI;
        publishUpdate(selectedMMSI, aisLayer);
    }

    public synchronized long getSelectedMMSI() {
        return selectedMMSI;
    }

    // public synchronized void setSelectedMMSI(long selectedMMSI) {
    // this.selectedMMSI = selectedMMSI;
    // for (int i = 0; i < mapWindows.size(); i++) {
    // mapWindows.get(i).getChartPanel().forceAisLayerUpdate();
    // }
    // }

    // AisSelectionListener

    // @Override
    // public void selectionChanged(long mmsi) {
    // // TODO Auto-generated method stub
    //
    // }

    /**
     * Add listener to Selection Listeners
     * 
     * @param targetListener
     *            - class that is added to listeners
     */
    public synchronized void addSelectionListener(
            AisSelectionListener targetListener) {
        selectionListeners.add(targetListener);
    }

    /**
     * Publish the update of a target to all listeners
     * 
     * @param mmsi
     * @param aisLayer
     */
    public synchronized void publishUpdate(long mmsi, AisLayer aisLayer) {
        for (AisSelectionListener listener : selectionListeners) {
            if (listener != aisLayer) {
                listener.selectionChanged(mmsi);
            }
        }
    }

    /**
     * Remove a class from being a listener
     * 
     * @param targetListener
     *            target to be removed
     */
    public synchronized void removeSelectionListener(
            AisSelectionListener targetListener) {
        selectionListeners.remove(targetListener);
    }

}
