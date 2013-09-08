/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.rpgtoolkit.minecraft.portal;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 *
 * @author Chris
 */
public class PortalPlugin
    extends JavaPlugin implements Listener {
        
    private static final BlockFace[] directions = new BlockFace[] {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
        BlockFace.UP, BlockFace.DOWN
    };
    
    private static final Map<UUID, Long> interactions = 
            new WeakHashMap<UUID, Long>();
    
    private PortalPluginConfiguration configuration;
        
    public class PortalTraceResult {
        
        private double distance;
        private boolean ambiguous;
        private Block start;
        private Block end;
        private BlockFace face;
        private long time;
        
        public PortalTraceResult(Block start, Block end, BlockFace dir, long startTime) {
            this.start = start;
            this.end = end;
            this.face = dir;
            this.time = (System.currentTimeMillis() - startTime);
            if (this.start != null && this.end != null) {
                this.distance = this.end.getLocation().distance(
                        this.start.getLocation());
            }
            else {
                this.ambiguous = true;
            }
        }
        
        public boolean isAmbiguous() {
            return this.ambiguous;
        }
        
        public double getDistance() {
            return this.distance;
        }
        
        public Block getStartBlock() {
            return this.start;
        }
        
        public Block getEndBlock() {
            return this.end;
        }
        
        public BlockFace getEndFaceDirection() {
            return this.face;
        }
        
        public long getTraceTime() {
            return this.time;
        }
        
    }

    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        
        final Player player = event.getPlayer();
        final Material cap = this.configuration.getPortalCapMaterial();      
        
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                Block block = event.getClickedBlock();
                if (block.getTypeId() == cap.getId()) {
                    this.teleport(player, block);
                }
            }
        }
        
    }
    
    public void teleport(final Player player, final Block block) {
        
        final World world = player.getWorld();
        final UUID id = player.getUniqueId();
                
        // Limit the rate at which players can initiate a 
        // travel in the world
        
        if (interactions.containsKey(id)) {
            long lastInteractionTime = System.currentTimeMillis() - interactions.get(id);
            if (lastInteractionTime < this.configuration.getPortalMinimumInteractionTime()) {
                return;
            }
        }
        
        // If the player is directly adjacent to the block in any direction
        // then trace the portal path and send them on a journey if there
        // are no amiguities in the path
        
        if (this.isPlayerAdjacent(player, block)) {
            
            this.getLogger().log(Level.INFO, String.format(
                    "Player %s initiated potential portal travel",
                        player.getName()));
            
            final PortalTraceResult result = this.trace(player, block);
                        
            // If the portal trace was successful (not invalid, empty,
            // amibiguous) then initiate a teleport
            
            if (!result.isAmbiguous()) {
                
                this.getLogger().log(Level.INFO, String.format(
                        "Completed portal trace of %s block(s) in %s ms",
                            result.getDistance(), result.getTraceTime()));
                
                final Location origin = player.getLocation();
                final Location destination = this.location(
                        result.getEndBlock(), result.getEndFaceDirection());

                // Ensure the destination is safe
                
                if (!this.isDestinationSafe(player, destination)) {
                    player.sendMessage(ChatColor.RED + "The portal destination is blocked.");
                    return;
                }
                
                // Preserve the player's orientation and introduce the
                // portal effects
                
                destination.setYaw(origin.getYaw());

                player.playSound(origin, Sound.PORTAL_TRAVEL, 1f, 0f);
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.CONFUSION, 150, 0), true);
                
                // Measure last interaction time for this player
                
                interactions.put(id, System.currentTimeMillis());
               
                // Schedule the teleportation and only teleport the player if they
                // are still near the start block and the block is still a
                // portal cap block (e.g. it is not destroyed)
                
                Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                    public void run() {
                        
                        if (isPlayerAdjacent(player, block) && 
                                block.getTypeId() == configuration.getPortalCapMaterial().getId()) {
                            
                            // Log the teleportation

                            PortalPlugin.this.getLogger().log(Level.INFO, String.format(
                                    "Player %s teleported through a portal from %s to %s",
                                        player.getName(), origin, destination));
                            
                            world.playEffect(origin,Effect.ENDER_SIGNAL, 0);
                            
                            player.teleport(destination);
                            
                            world.playSound(destination, Sound.PORTAL, 1f, 0f);
                            world.playEffect(destination, Effect.ENDER_SIGNAL, 0);
                            
                            interactions.remove(id);
                            
                        }
                        
                    }
                }, 75);

                
            }
        }
        
    }
    
    public boolean isDestinationSafe(final Player player, Location destination) {
        
        Block head = player.getWorld().getBlockAt(destination.add(0, 1, 0));
        Block feet = player.getWorld().getBlockAt(destination.add(0, -1, 0));
        
        boolean x = (head.isEmpty() || head.isLiquid() || head.getType().isTransparent());
        boolean y = (feet.isEmpty() || feet.isLiquid() || feet.getType().isTransparent());
        
        return (x && y);
        
    }
    
    public boolean isPlayerAdjacent(Player player, Block block) {
        
        Location playerLocation = player.getLocation();
        Location blockLocation = block.getLocation();
        
        double distance =  playerLocation.distance(blockLocation);
        double distanceMaximum =
                this.configuration.getPortalMaximumPlayerDistanceFromOrigin();
        
        return (distance >= 0 && distance <= distanceMaximum);
        
    }
    
    public boolean isPortalMaterial(Material a, Material b) {
        
        return false;
        
    }
    
    public boolean isPlayerAboveBlock(Player player, Block block) {
        
        Location pl = player.getLocation();
        Location bl = block.getLocation();
        
        if (bl.getBlockX() == pl.getBlockX() && bl.getBlockZ() == pl.getBlockZ()) {
            return (pl.getBlockY() == (bl.getBlockY() + 1));
        }
        
        return false;
        
    }
    
    public Location location(Block block, BlockFace face) {
                
        Location destination = block.getLocation().add(0.5, 0, 0.5);
        
        switch (face.getOppositeFace()) {
            case NORTH:
                destination = destination.add(0, 0, -1);
                break;
            case EAST:
                destination = destination.add(1, 0, 0);
                break;
            case SOUTH:
                destination = destination.add(0, 0, 1);
                break;
            case WEST:
                destination = destination.add(-1, 0, 0);
                break;
            case UP:
                destination = destination.add(0, 1, 0);
                break;
            case DOWN:
                destination = destination.add(0, -2, 0);
                break;
        }
        
        return destination;
        
    }
        
    public PortalTraceResult trace(Player player, Block block) {
                
        int count = 0;
        
        Block start = block;
        Block end = null;
        
        byte type = -1;
        
        Block current = start;
        BlockFace last = null;
        BlockFace cached = null;
        
        boolean moved = true;
        
        Material wire = this.configuration.getPortalWireMaterial();
        Material cap = this.configuration.getPortalCapMaterial();
        
        long traceStartTime = System.currentTimeMillis();
        
        // for each face on the current block
        //      if the face is not the opposite of the last face
        //          get the neighbor block for the face
        //              if the block is not empty and is a portal wire
        //                  if a movement has not been detected yet
        //                      end block = neighbor block
        //                      end face = opposite of the current face
        //                      flag a movement
        //                  increment number of relatives detected
        // if more than one relative was detected
        //      path is ambiguous (branch of circuit detected)
        // else
        //      set last face detected to end face
        //      set current block to check to end block
        //      increment trace count

        while (moved && count < this.configuration.getMaximumPortalDistance()) {

            int relatives = 0;
            
            moved = false;
            
            // Check each neighbor block (except the face
            // connected to the previous block in the trace)
            // for a portal wire:
            
            for (BlockFace face : directions) {
                if (face != last && current != null) {
                    Block relative = current.getRelative(face, 1);
                    if (relative.getTypeId() == wire.getId()) {
                        if (type < 0) {
                            type = relative.getData();
                        }
                        if (relative.getData() == type) {
                            if (!moved) {
                                end = relative;
                                cached = face.getOppositeFace();
                                moved = true;
                            }
                            relatives++;
                        }
                    }
                    else if (relative.getTypeId() == cap.getId()) {
                        if (type >= 0 && count > 0) {
                            end = relative;
                            cached = face.getOppositeFace();
                            moved = false;
                            relatives++;
                        }
                    }
                }
            }
            
            // If we found more than 1 neighbor then there is an
            // ambiguity in the path (circuit or branch)

            if (relatives > 1) {
                return new PortalTraceResult(start, null, null, traceStartTime);
            }
            
            // Update the last checked face and the current
            // block to check based on the results recorded
            // in the neighbor check
            
            last = cached;
            current = end;
            count++;
            
        }
        
        // If a portal end component is found then ensure it is the
        // right type, otherwise clear the data to end up with a
        // failed trace
        
        if (end != null && end.getTypeId() != cap.getId()) {
            end = null;
            last = null;
        }
        
        return new PortalTraceResult(start, end, last, traceStartTime);

    }
    
    @Override
    public void onEnable() {
                
        // TODO: Load configuration from file
        
        this.configuration =  new PortalPluginConfiguration();
        
        // Start listening for events
        
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        
    }
    
    @Override
    public void onDisable() {
        
        PortalPlugin.interactions.clear();
        
    }
    
    
}
