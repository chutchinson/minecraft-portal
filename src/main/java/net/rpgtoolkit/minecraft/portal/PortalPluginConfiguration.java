/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.rpgtoolkit.minecraft.portal;

import org.bukkit.Material;

/**
 *
 * @author Chris
 */
public class PortalPluginConfiguration {
    
    private Material portalCapMaterial;
    private Material portalWireMaterial;
    private int portalMaximumDistance;
    private int portalMinimumInteractionTime;
    private int portalMaximumPlayerDistanceFromOrigin;
    
    public PortalPluginConfiguration() {
        this.portalCapMaterial = Material.NOTE_BLOCK;
        this.portalWireMaterial = Material.WOOL;
        this.portalMaximumDistance = 512;   // 32 chunks
        this.portalMinimumInteractionTime = 6000;   // 6 seconds
        this.portalMaximumPlayerDistanceFromOrigin = 3; // 3 blocks
    }
    
    public Material getPortalCapMaterial() {
        return this.portalCapMaterial;
    }
    
    public Material getPortalWireMaterial() {
        return this.portalWireMaterial;
    }
    
    public int getMaximumPortalDistance() {
        return this.portalMaximumDistance;
    }
    
    public int getPortalMinimumInteractionTime() {
        return this.portalMinimumInteractionTime;
    }
    
    public int getPortalMaximumPlayerDistanceFromOrigin() {
        return this.portalMaximumPlayerDistanceFromOrigin;
    }
    
}
