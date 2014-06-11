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
package dk.dma.epd.common.prototype.predictor;

import java.util.concurrent.CopyOnWriteArrayList;

import net.jcip.annotations.ThreadSafe;

import com.bbn.openmap.MapHandlerChild;

import dk.dma.epd.common.prototype.EPD;

/**
 * Class for handling and distributing dynamic prediction information.
 * Clients can receive notifications by implementing {@link IDynamicPredictionsListener} and registering as an observer of a {@link DynamicPredictorHandler}.
 * This class also implements {@link IDynamicPredictionsListener} itself. This is to allow dynamic prediction data to arrive from any source, e.g. an on-ship sensor
 * or the Maritime Cloud. As such, the main purpose of this class is to centralize distribution of dynamic predictions and abstract the data source as part of this.
 */
@ThreadSafe
public class DynamicPredictorHandler extends MapHandlerChild implements Runnable, IDynamicPredictionsListener {

    private static final long TIMEOUT = 30 * 1000; // 30 sec

    private final CopyOnWriteArrayList<IDynamicPredictionsListener> listeners = new CopyOnWriteArrayList<>();

    private volatile long lastPrediction;

    public DynamicPredictorHandler() {
        EPD.startThread(this, "DynamicPredictorHandler");
    }
    
    @Override
    public void receivePredictions(DynamicPrediction prediction) {
        /*
         * TODO this assumes that there is only one prediction source, namely the on-ship sensor
         */
        lastPrediction = System.currentTimeMillis();
        // Delegate to registered listeners.
        for(IDynamicPredictionsListener listener : this.listeners) {
            listener.receivePredictions(prediction);
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
            // Distribute timeout
            if (System.currentTimeMillis() - lastPrediction > TIMEOUT) {
                for (IDynamicPredictionsListener listener : listeners) {
                    listener.receivePredictions(null);
                }
            }
        }

    }

    public void addListener(IDynamicPredictionsListener listener) {
        if (listener == this) {
            throw new IllegalArgumentException("Cannot add self as observer of self.");
        }
        listeners.add(listener);
    }

    public void removeListener(IDynamicPredictionsListener listener) {
        listeners.remove(listener);
    }
    
    @Override
    public void findAndInit(Object obj) {
        super.findAndInit(obj);
    }
}
