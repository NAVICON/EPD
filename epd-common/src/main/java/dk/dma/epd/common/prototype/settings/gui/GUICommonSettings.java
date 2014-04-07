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
package dk.dma.epd.common.prototype.settings.gui;

import java.awt.Dimension;
import java.awt.Point;

import dk.dma.epd.common.prototype.settings.ObservedSettings;
import dk.dma.epd.common.prototype.settings.layers.LayerSettings;

/**
 * This class maintains the most abstract GUI settings such as window size and
 * window location. GUI settings are primarily targeted at Swing components such
 * as frames, menus, docks etc. Settings specifying how vessels or other units
 * are to be painted on a layer should be placed in {@link LayerSettings} or any
 * of its subclasses.
 * 
 * @author Janus Varmarken
 */
public class GUICommonSettings<OBSERVER extends IGUISettingsCommonObserver>
        extends ObservedSettings<OBSERVER> {

    /**
     * Setting specifying if the application should run in fullscreen.
     */
    private boolean fullscreen;

    /**
     * Setting specifying if the application window should be maximized.
     */
    private boolean maximized;

    /**
     * Setting specifying the screen coordinates for the top left corner of the
     * application window.
     */
    private Point appLocation = new Point(10, 10);

    /**
     * Setting specifying the application window dimensions.
     */
    private Dimension appDimensions = new Dimension(1280, 800);

    /**
     * Gets the setting specifying if the application should run in full screen
     * mode.
     * 
     * @return {@code true} if the application should run in full screen mode,
     *         {@code false} if the application should not run in full screen
     *         mode.
     */
    public boolean isFullscreen() {
        try {
            this.settingLock.readLock().lock();
            return this.fullscreen;
        } finally {
            this.settingLock.readLock().unlock();
        }
    }

    /**
     * Changes the setting specifying if the application should run in full
     * screen mode.
     * 
     * @param fullscreen
     *            {@code true} if the application should run in full screen
     *            mode, {@code false} if the application should not run in full
     *            screen mode.
     */
    public void setFullscreen(final boolean fullscreen) {
        try {
            this.settingLock.writeLock().lock();
            if (this.fullscreen == fullscreen) {
                // No change, no need to notify observers.
                return;
            }
            // There was a change, update and notify.
            this.fullscreen = fullscreen;
            for (OBSERVER obs : this.observers) {
                obs.isFullscreenChanged(fullscreen);
            }
        } finally {
            this.settingLock.writeLock().unlock();
        }
    }

    /**
     * Gets the setting specifying if the main frame of the application should
     * be maximized.
     * 
     * @return {@code true} if the main frame of the application should be
     *         maximized, {@code false} if the main frame of the application
     *         should not be maximized.
     */
    public boolean isMaximized() {
        try {
            this.settingLock.readLock().lock();
            return this.maximized;
        } finally {
            this.settingLock.readLock().unlock();
        }
    }

    /**
     * Changes the setting specifying if the main frame of the application
     * should be maximized.
     * 
     * @param maximized
     *            {@code true} if the main frame of the application should be
     *            maximized, {@code false} if the main frame of the application
     *            should not be maximized.
     */
    public void setMaximized(final boolean maximized) {
        try {
            this.settingLock.writeLock().lock();
            if (this.maximized == maximized) {
                // No change, no need to notify observers.
                return;
            }
            // There was a change, update and notify.
            this.maximized = maximized;
            for (OBSERVER obs : this.observers) {
                obs.isMaximizedChanged(maximized);
            }
        } finally {
            this.settingLock.writeLock().unlock();
        }
    }

    /**
     * Get the setting specifying the application screen location, i.e. the
     * screen coordinates for the top left corner of the main frame of the
     * application.
     * 
     * @return The screen coordinates of the top left corner of the main frame
     *         of the application wrapped in a {@link Point}. The returned
     *         {@link Point} instance is a copy of the setting value and hence
     *         <b>not</b> a direct reference to the setting value. This is done
     *         in order to protect against reference leak which could result in
     *         unsynchronized access or modification of the setting value.
     */
    public Point getAppLocation() {
        // TODO does YamlBeans handle this getter correctly?
        try {
            this.settingLock.readLock().lock();
            return new Point(this.appLocation);
        } finally {
            this.settingLock.readLock().unlock();
        }

    }

    /**
     * Changes the setting specifying the application screen location, i.e. the
     * screen coordinates for the top left corner of the main frame of the
     * application.
     * 
     * @param newAppLocation
     *            The new application screen location. Note: this setter creates
     *            a copy of this argument so that no client will have a direct
     *            reference to the setting value. This is done in order to
     *            ensure that all accesses to, or modifications of, the fields
     *            of {@code GUICommonSettings} remain synchronized.
     */
    public void setAppLocation(Point newAppLocation) {
        // TODO does YamlBeans handle this setter correctly?
        try {
            this.settingLock.writeLock().lock();
            Point copy = new Point(newAppLocation);
            // Point equality is based on (x,y) comparison, see docs.
            if (this.appLocation.equals(copy)) {
                // No change, no need to notify observers.
                return;
            }
            // There was a change, update and notify.
            this.appLocation = copy;
            for (OBSERVER obs : this.observers) {
                // Feed each observer with its own Point instance.
                obs.appScreenLocationChanged(new Point(this.appLocation));
            }
        } finally {
            this.settingLock.writeLock().unlock();
        }
    }

    /**
     * Get the setting specifying the dimensions of the main frame of the
     * application.
     * 
     * @return The dimensions of the main frame of the application wrapped in a
     *         {@link Dimension} instance. The returned {@link Dimension}
     *         instance is a copy of the setting value and hence <b>not</b> a
     *         direct reference to the setting value. This is done in order to
     *         protect against reference leak which could result in
     *         unsynchronized access or modification of the setting value.
     */
    public Dimension getAppDimensions() {
        // TODO does YamlBeans handle this getter correctly?
        try {
            this.settingLock.readLock().lock();
            return new Dimension(this.appDimensions);
        } finally {
            this.settingLock.readLock().unlock();
        }
    }

    /**
     * Changes the setting specifying the dimensions of the main frame of the
     * application.
     * 
     * @param newAppDimensions
     *            the appDimensions to set
     */
    public void setAppDimensions(Dimension newAppDimensions) {
        // TODO does YamlBeans handle this setter correctly?
        try {
            this.settingLock.writeLock().lock();
            Dimension copy = new Dimension(newAppDimensions);
            // Equality is value based, see docs.
            if (this.appDimensions.equals(copy)) {
                // No change, no need to notify observers.
                return;
            }
            // There was a change, update and notify.
            this.appDimensions = copy;
            for (OBSERVER obs : this.observers) {
                // Feed each observer with its own Dimension instance.
                obs.appDimensionsChanged(new Dimension(this.appDimensions));
            }
        } finally {
            this.settingLock.writeLock().unlock();
        }
    }
}