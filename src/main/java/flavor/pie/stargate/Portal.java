/*
 * Stargate - A portal plugin for Bukkit
 * Copyright (C) 2011 Shaun (sturmeh)
 * Copyright (C) 2011 Dinnerbone
 * Copyright (C) 2011, 2012 Steven "Drakia" Scott <Contact@TheDgtl.net>
 * Copyright (C) 2017 Adam Spofford <pieflavor.mc@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package flavor.pie.stargate;

import com.flowpowered.math.vector.Vector3d;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import flavor.pie.stargate.event.StargateActivateEvent;
import flavor.pie.stargate.event.StargateCloseEvent;
import flavor.pie.stargate.event.StargateCreateEvent;
import flavor.pie.stargate.event.StargateDeactivateEvent;
import flavor.pie.stargate.event.StargateOpenEvent;
import flavor.pie.stargate.event.StargatePortalEvent;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
//import org.spongepowered.api.text.format.TextColors;
//import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.Axis;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;

public class Portal {
    // Static variables used to store portal lists
    private static final HashMap<Blox, Portal> lookupBlocks = new HashMap<>();
    private static final HashMap<Blox, Portal> lookupEntrances = new HashMap<>();
    private static final HashMap<Blox, Portal> lookupControls = new HashMap<>();
    private static final ArrayList<Portal> allPortals = new ArrayList<>();
    private static final HashMap<String, ArrayList<String>> allPortalsNet = new HashMap<>();
    private static final HashMap<String, HashMap<String, Portal>> lookupNamesNet = new HashMap<>();
    
    // A list of Bungee gates
    private static final HashMap<String, Portal> bungeePortals = new HashMap<>();
    
    // Gate location block info
    private Blox topLeft;
    private int modX;
    private int modZ;
    private float rotX;
    
    // Block references
    private Blox id;
    private Blox button;
    private Blox[] frame;
    private Blox[] entrances;
    
    // Gate information
    private String name;
    private String destination;
    private String lastDest = "";
    private String network;
    private Gate gate;
    private UUID owner = defaultUUID;
    private static final UUID defaultUUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private World world;
    private boolean verified;
    private boolean fixed;
    
    // Options
    private boolean hidden = false;
    private boolean alwaysOn = false;
    private boolean priv = false;
    private boolean free = false;
    private boolean backwards = false;
    private boolean show = false;
    private boolean noNetwork = false;
    private boolean random = false;
    private boolean bungee = false;
    
    // In-use information
    private Player player;
    private Player activePlayer;
    private ArrayList<String> destinations = new ArrayList<>();
    private boolean isOpen = false;
    private long openTime;

    private Portal(Blox topLeft, int modX, int modZ,
            float rotX, Blox id, Blox button,
            String dest, String name,
            boolean verified, String network, Gate gate, UUID owner,
            boolean hidden, boolean alwaysOn, boolean priv, boolean free, boolean backwards, boolean show, boolean noNetwork, boolean random, boolean bungee) {
        this.topLeft = topLeft;
        this.modX = modX;
        this.modZ = modZ;
        this.rotX = rotX;
        this.id = id;
        this.destination = dest;
        this.button = button;
        this.verified = verified;
        this.network = network;
        this.name = name;
        this.gate = gate;
        this.owner = owner;
        this.hidden = hidden;
        this.alwaysOn = alwaysOn;
        this.priv = priv;
        this.free = free;
        this.backwards = backwards;
        this.show = show;
        this.noNetwork = noNetwork;
        this.random = random;
        this.bungee = bungee;
        this.world = topLeft.getWorld();
        this.fixed = dest.length() > 0 || this.random || this.bungee;
        
        if (this.isAlwaysOn() && !this.isFixed()) {
            this.alwaysOn = false;
            Stargate.debug("Portal", "Can not create a non-fixed always-on gate. Setting AlwaysOn = false");
        }
        
        if (this.random && !this.isAlwaysOn()) {
            this.alwaysOn = true;
            Stargate.debug("Portal", "Gate marked as random, set to always-on");
        }
        
        if (verified) {
            this.drawSign();
        }
    }
    
    /**
     * Option Check Functions
     */
    public boolean isOpen() {
        return isOpen || isAlwaysOn();
    }
    
    public boolean isAlwaysOn() {
        return alwaysOn;
    }
    
    public boolean isHidden() {
        return hidden;
    }
    
    public boolean isPrivate() {
        return priv;
    }
    
    public boolean isFree() {
        return free;
    }
    
    public boolean isBackwards() {
        return backwards;
    }
    
    public boolean isShown() {
        return show;
    }
    
    public boolean isNoNetwork() {
        return noNetwork;
    }
    
    public boolean isRandom() {
        return random;
    }
    
    public boolean isBungee() {
        return bungee;
    }
    
    public void setAlwaysOn(boolean alwaysOn) {
        this.alwaysOn = alwaysOn;
    }
    
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
    
    public void setPrivate(boolean priv) {
        this.priv = priv;
    }
    
    public void setFree(boolean free) {
        this.free = free;
    }
    
    public void setBackwards(boolean backwards) {
        this.backwards = backwards;
    }
    
    public void setShown(boolean show) {
        this.show = show;
    }
    
    public void setNoNetwork(boolean noNetwork) {
        this.noNetwork = noNetwork;
    }
    
    public void setRandom(boolean random) {
        this.random = random;
    }
    
    /**
     * Getters and Setters
     */

    public float getRotation() {
        return rotX;
    }
    
    public Player getActivePlayer() {
        return activePlayer;
    }

    public String getNetwork() {
        return network;
    }
    
    public void setNetwork(String network) {
        this.network = network;
    }
    
    public long getOpenTime() {
        return openTime;
    }

    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = filterName(name);
        drawSign();
    }

    public Portal getDestination(Player player) {
        if (isRandom()) {
            destinations = getDestinations(player, getNetwork());
            if (destinations.size() == 0) {
                destinations.clear();
                return null;
            }
            String dest = destinations.get((new Random()).nextInt(destinations.size()));
            destinations.clear();
            return Portal.getByName(dest, getNetwork());
        }
        return Portal.getByName(destination, getNetwork());
    }
    
    public Portal getDestination() {
        return getDestination(null);
    }
    
    public void setDestination(Portal destination) {
        setDestination(destination.getName());
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getDestinationName() {
        return destination;
    }
    
    public Gate getGate() {
        return gate;
    }

    public UUID getOwner() {
        return owner;
    }
    
    public void setOwner(UUID owner) {
        this.owner = owner;
    }
    
    public Blox[] getEntrances() {
        if (entrances == null) {
            RelativeBlockVector[] space = gate.getEntrances();
            entrances = new Blox[space.length];
            int i = 0;

            for (RelativeBlockVector vector : space) {
                entrances[i++] = getBlockAt(vector);
            }
        }
        return entrances;
    }

    public Blox[] getFrame() {
        if (frame == null) {
            RelativeBlockVector[] border = gate.getBorder();
            frame = new Blox[border.length];
            int i = 0;

            for (RelativeBlockVector vector : border) {
                frame[i++] = getBlockAt(vector);
            }
        }

        return frame;
    }
    
    public Location<World> getSign() {
        return id.getBlock();
    }
    
    public World getWorld() {
        return world;
    }
    
    public Blox getButton() {
        return button;
    }
    
    public void setButton(Blox button) {
        this.button = button;
    }
    
    public static ArrayList<String> getNetwork(String network) {
        return allPortalsNet.get(network.toLowerCase());
    }

    public boolean open(boolean force) {
        return open(null, force);
    }

    public boolean open(Player openFor, boolean force) {
        // Call the StargateOpenEvent
        StargateOpenEvent event = new StargateOpenEvent(openFor, this, force);
        Sponge.getEventManager().post(event);
        if (event.isCancelled()) return false;
        force = event.getForce();
        
        if (isOpen() && !force) return false;

        getWorld().loadChunk(topLeft.getBlock().getBlockPosition(), false);

        BlockState openType = gate.getPortalBlockOpen();
        if (openType.supports(Keys.DIRECTION)) {
            openType = openType.with(Keys.DIRECTION, getSign().get(Keys.DIRECTION).get()).get();
        } else if (openType.supports(Keys.AXIS)) {
            Direction dir = getSign().get(Keys.DIRECTION).get();
            if (dir.equals(Direction.EAST) || dir.equals(Direction.WEST)) {
                openType = openType.with(Keys.AXIS, Axis.Z).get();
            } else {
                openType = openType.with(Keys.AXIS, Axis.X).get();
            }
        }
        for (Blox inside : getEntrances()) {
            Stargate.blockPopulatorQueue.add(new BloxPopulator(inside, openType));
        }
        if (button != null) {
            Stargate.blockPopulatorQueue.add(
                    new BloxPopulator(button, button.getData().with(Keys.POWERED, true).get()));
        }

        isOpen = true;
        openTime = System.currentTimeMillis() / 1000;
        Stargate.openList.add(this);
        Stargate.activeList.remove(this);
        
        // Open remote gate
        if (!isAlwaysOn()) {
            player = openFor;

            Portal end = getDestination();
            // Only open dest if it's not-fixed or points at this gate
            if (!random && end != null && (!end.isFixed() || end.getDestinationName().equalsIgnoreCase(getName())) && !end.isOpen()) {
                end.open(openFor, false);
                end.setDestination(this);
                if (end.isVerified()) end.drawSign();
            }
        }

        return true;
    }

    public void close(boolean force) {
        if (!isOpen) return;
        // Call the StargateCloseEvent
        StargateCloseEvent event = new StargateCloseEvent(this, force);
        Sponge.getEventManager().post(event);
        if (event.isCancelled()) return;
        force = event.getForce();
        
        if (isAlwaysOn() && !force) return; // Only close always-open if forced
        
        // Close this gate, then the dest gate.
        BlockState closedType = gate.getPortalBlockClosed();
        for (Blox inside : getEntrances()) {
            Stargate.blockPopulatorQueue.add(new BloxPopulator(inside, closedType));
        }

        player = null;
        isOpen = false;
        Stargate.openList.remove(this);
        Stargate.activeList.remove(this);
        
        if (!isAlwaysOn()) {
            Portal end = getDestination();

            if (end != null && end.isOpen()) {
                end.deactivate(); // Clear it's destination first.
                end.close(false);
            }
        }
        
        deactivate();
    }

    public boolean isOpenFor(Player player) {
        if (!isOpen) {
            return false;
        }
        if ((isAlwaysOn()) || (this.player == null)) {
            return true;
        }
        return (player != null) && (player.getName().equalsIgnoreCase(this.player.getName()));
    }

    public boolean isFixed() {
        return fixed;
    }

    public boolean isPowered() {
        RelativeBlockVector[] controls = gate.getControls();

        for (RelativeBlockVector vector : controls) {
            Location<World> loc = getBlockAt(vector).getBlock();

            if (loc.get(Keys.POWERED).orElse(false))
                return true;
        }

        return false;
    }

    public void teleport(Player player, Portal origin, MoveEntityEvent event) {
        Transform<World> traveller = player.getTransform();
        Transform<World> exit = getExit(traveller);

        // Handle backwards gates
        int adjust = 180;
        if (isBackwards() || origin.isBackwards())
            adjust = 0;
        if (isBackwards() && origin.isBackwards())
            adjust = 180;

        exit = exit.setRotation(new Vector3d(exit.getPitch(), origin.getRotation() - traveller.getYaw() + this.getRotation() + adjust, exit.getRoll()));

        // Call the StargatePortalEvent to allow plugins to change destination
        if (!origin.equals(this)) {
            StargatePortalEvent pEvent = new StargatePortalEvent(player, origin, this, exit);
            Sponge.getEventManager().post(pEvent);
            // Teleport is cancelled
            if (pEvent.isCancelled()) {
                origin.teleport(player, origin, event);
                return;
            }
            // Update exit if needed
            exit = pEvent.getExit();
        }

        // If no event is passed in, assume it's a teleport, and act as such
        if (event == null) {
            exit = exit.setRotation(new Vector3d(exit.getPitch(), this.getRotation(), exit.getRoll()));
            player.setTransform(exit);
        } else {
            // Oh how I wish this didn't actually bork rotation 3/10 times.
//            event.setToTransform(exit);
            Transform<World> _exit = exit;
            Task.builder().execute(() -> player.setTransform(_exit)).submit(Stargate.stargate);
        }
    }

    public void teleport(final Entity vehicle) {
        Transform<World> traveller = new Transform<>(vehicle.getLocation());
        Transform<World> exit = getExit(traveller);
        
        double velocity = vehicle.getVelocity().length();
        
        // Stop and teleport
        vehicle.setVelocity(new Vector3d());
        
        // Get new velocity
        int x = 0, z = 0;
        switch (id.getBlock().get(Keys.DIRECTION).get()) {
            case NORTH:
                z = -1;
                break;
            case SOUTH:
                z = 1;
                break;
            case WEST:
                x = -1;
                break;
            case EAST:
                x = 1;
                break;
            // Just in case
            default:
            	x = 1;
            	break;
        }
        final Vector3d newVelocity = new Vector3d(x, 0, z).mul(velocity);

        if (!vehicle.getPassengers().isEmpty()) {
            final Entity passenger = vehicle.getPassengers().get(0);
//            final Entity v = exit.getLocation().getExtent().spawn(exit, vehicle.getClass());
            vehicle.clearPassengers();
//            vehicle.remove();
//            final Entity v = exit.getExtent().createEntity(vehicle.toContainer(), exit.getPosition()).get();
//            v.setTransform(exit);
//            exit.getExtent().spawnEntity(v, Cause.source(Stargate.stargate).build());
            passenger.setTransform(exit);
            vehicle.setTransform(exit);
            Task.builder().execute(() -> {
                vehicle.addPassenger(passenger);
                vehicle.setVelocity(newVelocity);
            }).delayTicks(1).submit(Stargate.stargate);
        } else {
//            Entity mc = exit.getWorld().spawn(exit, vehicle.getClass());
//            if (mc instanceof StorageMinecart) {
//                StorageMinecart smc = (StorageMinecart)mc;
//                smc.getInventory().setContents(((StorageMinecart)vehicle).getInventory().getContents());
//            }
//            mc.setVelocity(newVelocity);
//            vehicle.remove();
            vehicle.setTransform(exit);
            vehicle.setVelocity(newVelocity);
        }
    }

    public Transform<World> getExit(Transform<World> traveller) {
        Transform<World> loc = null;
        // Check if the gate has an exit block
        if (gate.getExit() != null) {
            Blox exit = getBlockAt(gate.getExit());
            int back = (isBackwards()) ? -1 : 1;
            loc = exit.modRelativeLoc(0D, 0D, 1D, traveller.getYaw(), traveller.getPitch(), modX * back, 1, modZ * back);
        } else {
            Stargate.log.warn("Missing destination point in .gate file " + gate.getFilename());
        }
        
        if (loc != null) {
            if (getWorld().getBlockType(loc.getPosition().toInt()).equals(BlockTypes.STONE_SLAB)) {
                loc = loc.setPosition(new Vector3d(loc.getPosition().getX(), loc.getPosition().getY() + 0.5, loc.getPosition().getZ()));
            }

            loc = loc.setRotation(new Vector3d(traveller.getPitch(), loc.getYaw(), loc.getRoll()));
            return loc;
        }
        return traveller;
    }
    
    public boolean isChunkLoaded() {
        return getWorld().getChunk(topLeft.getBlock().getBlockPosition()).get().isLoaded();
    }
    
    public void loadChunk() {
        getWorld().loadChunk(topLeft.getBlock().getBlockPosition(), false);
    }

    public boolean isVerified() {
        verified = true;
        for (RelativeBlockVector control : gate.getControls())
            verified = verified && getBlockAt(control).getBlock().getBlock().equals(gate.getControlBlock());
        return verified;
    }

    public boolean wasVerified() {
        return verified;
    }

    public boolean checkIntegrity() {
        return gate.matches(topLeft, modX, modZ);
    }
    
    public ArrayList<String> getDestinations(Player player, String network) {
        ArrayList<String> destinations = new ArrayList<>();
        for (String dest : allPortalsNet.get(network.toLowerCase())) {
            Portal portal = getByName(dest, network);
            // Check if dest is a random gate
            if (portal.isRandom()) continue;
            // Check if dest is always open (Don't show if so)
            if (portal.isAlwaysOn() && !portal.isShown()) continue;
            // Check if dest is this portal
            if (dest.equalsIgnoreCase(getName())) continue;
            // Check if dest is a fixed gate not pointing to this gate
            if (portal.isFixed() && !portal.getDestinationName().equalsIgnoreCase(getName())) continue;
            // Allow random use by non-players (Minecarts)
            if (player == null) {
                destinations.add(portal.getName());
                continue;
            }
            // Check if this player can access the dest world
            if (!Stargate.canAccessWorld(player, portal.getWorld().getName())) continue;
            // Visible to this player.
            if (Stargate.canSee(player, portal)) {
                destinations.add(portal.getName());
            }
        }
        return destinations;
    }

    public boolean activate(Player player) {
        destinations.clear();
        destination = "";
        Stargate.activeList.add(this);
        activePlayer = player;
        String network = getNetwork();
        destinations = getDestinations(player, network);
        if (Stargate.config.portal.sortLists) {
            Collections.sort(destinations);
        }
        if (Stargate.config.portal.destMemory && !lastDest.isEmpty() && destinations.contains(lastDest)) {
            destination = lastDest;
        }
        
        StargateActivateEvent event = new StargateActivateEvent(this, player, destinations, destination);
        Sponge.getEventManager().post(event);
        if (event.isCancelled()) {
            Stargate.activeList.remove(this);
            return false;
        }
        destination = event.getDestination();
        destinations = event.getDestinations();
        drawSign();
        return true;
    }

    public void deactivate() {
        StargateDeactivateEvent event = new StargateDeactivateEvent(this);
        Sponge.getEventManager().post(event);
        if (event.isCancelled()) return;
        
        Stargate.activeList.remove(this);
        if (isFixed()) {
            return;
        }
        destinations.clear();
        destination = "";
        activePlayer = null;
        drawSign();
        // I don't know why the button gets stuck down, but I can take advantage of it.
        Stargate.blockPopulatorQueue.add(new BloxPopulator(button, button.getData().with(Keys.POWERED, false).get()));
    }

    public boolean isActive() {
        return isFixed() || (destinations.size() > 0);
    }

    public void cycleDestination(Player player) {
        cycleDestination(player, 1);
    }
    
    public void cycleDestination(Player player, int dir) {
        boolean activate = false;
        if (!isActive() || getActivePlayer() != player) {
            // If the event is cancelled, return
            if (!activate(player)) {
                return;
            }
            Stargate.debug("cycleDestination", "Network Size: " + allPortalsNet.get(network.toLowerCase()).size());
            Stargate.debug("cycleDestination", "Player has access to: " + destinations.size());
            activate = true;
        }
        
        if (destinations.size() == 0) {
            Stargate.sendMessage(player, Stargate.getString("destEmpty"));
            return;
        }

        if (!Stargate.config.portal.destMemory || !activate || lastDest.isEmpty()) {
            int index = destinations.indexOf(destination);
            index += dir;
            if (index >= destinations.size()) 
                index = 0;
            else if (index < 0) 
                index = destinations.size() - 1;
            destination = destinations.get(index);
            lastDest = destination;
        }
        openTime = System.currentTimeMillis() / 1000;
        drawSign();
    }

    public final void drawSign() {
        BlockType sMat = id.getBlock().getBlockType();
        if (!sMat.equals(BlockTypes.STANDING_SIGN) && !sMat.equals(BlockTypes.WALL_SIGN)) {
            Stargate.log.warn("Sign block is not a Sign object");
            Stargate.debug("Portal::drawSign", "Block: " + id.getBlock().getBlockType().getId() + " @ " + id.getBlock().getExtent().getName() + ":" + id.getBlock().getPosition());
            return;
        }
        Sign sign = (Sign) id.getBlock().getTileEntity().get();
        Stargate.setLine(sign, 0, "-" + name + "-");
        int max = destinations.size() - 1;
        int done = 0;

        if (!isActive()) {
            Stargate.setLine(sign, ++done, Stargate.getString("signRightClick"));
            Stargate.setLine(sign, ++done, Stargate.getString("signToUse"));
            if (!noNetwork) {
                Stargate.setLine(sign, ++done, "(" + network + ")");
            }
        } else {
            // Awesome new logic for Bungee gates
            if (isBungee()) {
                Stargate.setLine(sign, ++done, Stargate.getString("bungeeSign"));
                Stargate.setLine(sign, ++done, ">" + destination + "<");
                Stargate.setLine(sign, ++done, "[" + network + "]");
            } else if (isFixed()) {
                if (isRandom()) {
                    Stargate.setLine(sign, ++done, "> " + Stargate.getString("signRandom") + " <");
                } else {
                    Stargate.setLine(sign, ++done, ">" + destination + "<");
                }
                if (noNetwork) {
                    Stargate.setLine(sign, ++done, "");
                } else {
                    Stargate.setLine(sign, ++done, "(" + network + ")");
                }
                Portal dest = Portal.getByName(destination, network);
                if (dest == null && !isRandom()) {
                    Stargate.setLine(sign, ++done, Stargate.getString("signDisconnected"));
                } else {
                    Stargate.setLine(sign, ++done, "");
                }
            } else {
                int index = destinations.indexOf(destination);
                if ((index == max) && (max > 1) && (++done <= 3)) {
                    /*if (iConomyHandler.useiConomy() && Stargate.config.economy.freeGatesGreen) {
                        Portal dest = Portal.getByName(destinations.get(index - 2), network);
                        boolean green = Stargate.isFree(activePlayer, this, dest);
                        Stargate.setLine(sign, done, Text.of((green ? TextColors.DARK_GREEN : ""), destinations.get(index - 2)));
                    } else {
                        Stargate.setLine(sign, done, destinations.get(index - 2));
                    }*/
                	Stargate.setLine(sign, done, destinations.get(index - 2));
                }
                if ((index > 0) && (++done <= 3)) {
                    /*if (iConomyHandler.useiConomy() && Stargate.config.economy.freeGatesGreen) {
                        Portal dest = Portal.getByName(destinations.get(index - 1), network);
                        boolean green = Stargate.isFree(activePlayer, this, dest);
                        Stargate.setLine(sign, done, Text.of((green ? TextColors.DARK_GREEN : ""), destinations.get(index - 1)));
                    } else {
                        Stargate.setLine(sign, done, destinations.get(index - 1));
                    }*/
                	Stargate.setLine(sign, done, destinations.get(index - 1));
                }
                if (++done <= 3) {
                    /*if (iConomyHandler.useiConomy() && Stargate.config.economy.freeGatesGreen) {
                        Portal dest = Portal.getByName(destination, network);
                        boolean green = Stargate.isFree(activePlayer, this, dest);
                        Stargate.setLine(sign, done, Text.of((green ? TextColors.DARK_GREEN : ""), ">" + destination + "<"));
                    } else {
                        Stargate.setLine(sign, done, " >" + destination + "< ");
                    }*/
                	Stargate.setLine(sign, done, " >" + destination + "< ");
                }
                if ((max >= index + 1) && (++done <= 3)) {
                    /*if (iConomyHandler.useiConomy() && Stargate.config.economy.freeGatesGreen) {
                        Portal dest = Portal.getByName(destinations.get(index + 1), network);
                        boolean green = Stargate.isFree(activePlayer, this, dest);
                        Stargate.setLine(sign, done, Text.of((green ? TextColors.DARK_GREEN : ""), destinations.get(index + 1)));
                    } else {
                        Stargate.setLine(sign, done, destinations.get(index + 1));
                    }*/
                	Stargate.setLine(sign, done, destinations.get(index + 1));
                }
                if ((max >= index + 2) && (++done <= 3)) {
                    /*if (iConomyHandler.useiConomy() && Stargate.config.economy.freeGatesGreen) {
                        Portal dest = Portal.getByName(destinations.get(index + 2), network);
                        boolean green = Stargate.isFree(activePlayer, this, dest);
                        Stargate.setLine(sign, done, Text.of((green ? TextColors.DARK_GREEN : ""), destinations.get(index + 2)));
                    } else {
                        Stargate.setLine(sign, done, destinations.get(index + 2));
                    }*/
                	Stargate.setLine(sign, done, destinations.get(index + 2));
                }
            }
        }

        for (done++; done <= 3; done++) {
            sign.offer(sign.lines().set(done, Text.EMPTY));
        }
    }

    public void unregister(boolean removeAll) {
        unregister(removeAll, true);
    }

    public void unregister(boolean removeAll, boolean removeEntrance) {
        Stargate.debug("Unregister", "Unregistering gate " + getName());
        close(true);

        for (Blox block : getFrame()) {
            lookupBlocks.remove(block);
        }
        // Include the sign and button
        lookupBlocks.remove(id);
        if (button != null) {
            lookupBlocks.remove(button);
        }
        
        lookupControls.remove(id);
        if (button != null)
            lookupControls.remove(button);

        if (removeEntrance) {
            BlockState air = BlockTypes.AIR.getDefaultState();
            for (Blox entrance : getEntrances()) {
                lookupEntrances.remove(entrance);
                Stargate.blockPopulatorQueue.add(new BloxPopulator(entrance, air));
            }
        } else {
            for (Blox entrance : getEntrances()) {
                lookupEntrances.remove(entrance);
            }
        }

        if (removeAll)
            allPortals.remove(this);
        
        if (bungee) {
            bungeePortals.remove(getName().toLowerCase());
        } else {
            lookupNamesNet.get(getNetwork().toLowerCase()).remove(getName().toLowerCase());
            allPortalsNet.get(getNetwork().toLowerCase()).remove(getName().toLowerCase());
            
            for (String originName : allPortalsNet.get(getNetwork().toLowerCase())) {
                Portal origin = Portal.getByName(originName, getNetwork());
                if (origin == null) continue;
                if (!origin.getDestinationName().equalsIgnoreCase(getName())) continue;
                if (!origin.isVerified()) continue;
                if (origin.isFixed()) origin.drawSign();
                if (origin.isAlwaysOn()) origin.close(true);
            }
        }

        if (id.getBlock().getBlockType().equals(BlockTypes.WALL_SIGN)) {
            id.getBlock().offer(Keys.SIGN_LINES, ImmutableList.of(Text.of(name), Text.EMPTY, Text.EMPTY, Text.EMPTY));
        }

        saveAllGates(getWorld());
    }

    private Blox getBlockAt(RelativeBlockVector vector) {
        return topLeft.modRelative(vector.getRight(), vector.getDepth(), vector.getDistance(), modX, 1, modZ);
    }

    private void register() {
        fixed = destination.length() > 0 || random || bungee;
        
        // Bungee gates are stored in their own list
        if (isBungee()) {
            bungeePortals.put(getName().toLowerCase(), this);
        } else {
            // Check if network exists in our network list
            if (!lookupNamesNet.containsKey(getNetwork().toLowerCase())) {
                Stargate.debug("register", "Network " + getNetwork() + " not in lookupNamesNet, adding");
                lookupNamesNet.put(getNetwork().toLowerCase(), new HashMap<>());
            }
            lookupNamesNet.get(getNetwork().toLowerCase()).put(getName().toLowerCase(), this);
            
            // Check if this network exists
            if (!allPortalsNet.containsKey(getNetwork().toLowerCase())) {
                Stargate.debug("register", "Network " + getNetwork() + " not in allPortalsNet, adding");
                allPortalsNet.put(getNetwork().toLowerCase(), new ArrayList<>());
            }
            allPortalsNet.get(getNetwork().toLowerCase()).add(getName().toLowerCase());
        }

        for (Blox block : getFrame()) {
            lookupBlocks.put(block, this);
        }
        // Include the sign and button
        lookupBlocks.put(id, this);
        if (button != null) {
            lookupBlocks.put(button, this);
        }
        
        lookupControls.put(id, this);
        if (button != null)
            lookupControls.put(button, this);

        for (Blox entrance : getEntrances()) {
            lookupEntrances.put(entrance, this);
        }

        allPortals.add(this);
    }

    public static Portal createPortal(ChangeSignEvent event, Player player) {
        Blox id = new Blox(event.getTargetTile().getLocation());
        Location<World> idParent = id.getParent();
        if (idParent == null) {
            return null;
        }
        
        if (Gate.getGatesByControlBlock(idParent).length == 0) return null;
        
        if (Portal.getByBlock(idParent) != null) {
            Stargate.debug("createPortal", "idParent belongs to existing gate");
            return null;
        }

        Blox parent = new Blox(player.getWorld(), idParent.getBlockX(), idParent.getBlockY(), idParent.getBlockZ());
        Blox topleft = null;
        String name = filterName(event.getText().get(0).orElse(Text.EMPTY).toPlain());
        String destName = filterName(event.getText().get(1).orElse(Text.EMPTY).toPlain());
        String network = filterName(event.getText().get(2).orElse(Text.EMPTY).toPlain());
        String options = filterName(event.getText().get(3).orElse(Text.EMPTY).toPlain()).toLowerCase();
        boolean hidden = (options.indexOf('h') != -1);
        boolean alwaysOn = (options.indexOf('a') != -1);
        boolean priv = (options.indexOf('p') != -1);
        boolean free = (options.indexOf('f') != - 1);
        boolean backwards = (options.indexOf('b') != -1);
        boolean show = (options.indexOf('s') != -1);
        boolean noNetwork = (options.indexOf('n') != -1);
        boolean random = (options.indexOf('r') != -1);
        boolean bungee = (options.indexOf('u') != -1);
        
        // Check permissions for options.
        if (hidden && !Stargate.canOption(player, "hidden")) hidden = false;
        if (alwaysOn && !Stargate.canOption(player, "alwayson")) alwaysOn = false;
        if (priv && !Stargate.canOption(player, "private")) priv = false;
        if (free && !Stargate.canOption(player, "free")) free = false;
        if (backwards && !Stargate.canOption(player, "backwards")) backwards = false;
        if (show && !Stargate.canOption(player,  "show")) show = false;
        if (noNetwork && !Stargate.canOption(player, "nonetwork")) noNetwork = false;
        if (random && !Stargate.canOption(player, "random")) random = false;
        
        // Can not create a non-fixed always-on gate.
        if (alwaysOn && destName.length() == 0) {
            alwaysOn = false;
        }
        
        // Show isn't useful if A is false
        if (show && !alwaysOn) {
            show = false;
        }
        
        // Random gates are always on and can't be shown
        if (random) {
            alwaysOn = true;
            show = false;
        }
        
        // Bungee gates are always on and don't support Random
        if (bungee) {
            alwaysOn = true;
            random = false;
        }
        
        // Moved the layout check so as to avoid invalid messages when not making a gate
        int modX = 0;
        int modZ = 0;
        float rotX = 0f;
        Direction facing = null;

        if (idParent.getX() > id.getBlock().getX()) {
            modZ -= 1;
            rotX = 90f;
            facing = Direction.WEST;
        } else if (idParent.getX() < id.getBlock().getX()) {
            modZ += 1;
            rotX = 270f;
            facing = Direction.EAST;
        } else if (idParent.getZ() > id.getBlock().getZ()) {
            modX += 1;
            rotX = 180f;
            facing = Direction.NORTH;
        } else if (idParent.getZ() < id.getBlock().getZ()) {
            modX -= 1;
            rotX = 0f;
            facing = Direction.SOUTH;
        }

        Gate[] possibleGates = Gate.getGatesByControlBlock(idParent);
        Gate gate = null;
        RelativeBlockVector buttonVector = null;

        for (Gate possibility : possibleGates) {
            if ((gate == null) && (buttonVector == null)) {
                RelativeBlockVector[] vectors = possibility.getControls();
                RelativeBlockVector otherControl = null;

                for (RelativeBlockVector vector : vectors) {
                    Blox tl = parent.modRelative(-vector.getRight(), -vector.getDepth(), -vector.getDistance(), modX, 1, modZ);

                    if (gate == null) {
                        if (possibility.matches(tl, modX, modZ, true)) {
                            gate = possibility;
                            topleft = tl;

                            if (otherControl != null) {
                                buttonVector = otherControl;
                            }
                        }
                    } else if (otherControl != null) {
                        buttonVector = vector;
                    }

                    otherControl = vector;
                }
            }
        }

        if ((gate == null) || (buttonVector == null)) {
            Stargate.debug("createPortal", "Could not find matching gate layout");
            return null;
        }
        
        // If the player is trying to create a Bungee gate without permissions, drop out here
        // Do this after the gate layout check, in the least
        if (bungee) {
            if (!Stargate.enableBungee) {
                Stargate.sendMessage(player, Stargate.getString("bungeeDisabled"));
                return null;
            } else if (!Stargate.hasPerm(player, "stargate.admin.bungee")) {
                Stargate.sendMessage(player, Stargate.getString("bungeeDeny"));
                return null;
            } else if (destName.isEmpty() || network.isEmpty()) {
                Stargate.sendMessage(player, Stargate.getString("bungeeEmpty"));
                return null;
            }
        }
        
        // Debug
        Stargate.debug("createPortal", "h = " + hidden + " a = " + alwaysOn + " p = " + priv + " f = " + free + " b = " + backwards + " s = " + show + " n = " + noNetwork + " r = " + random + " u = " + bungee);

        if (!bungee && (network.length() < 1 || network.length() > 11)) {
            network = Stargate.getDefaultNetwork();
        }
        
        boolean deny = false;
        String denyMsg = "";
        
        // Check if the player can create gates on this network
        if (!bungee && !Stargate.canCreate(player, network)) {
            Stargate.debug("createPortal", "Player doesn't have create permissions on network. Trying personal");
            if (Stargate.canCreatePersonal(player)) {
                network = player.getName();
                if (network.length() > 11) network = network.substring(0, 11);
                Stargate.debug("createPortal", "Creating personal portal");
                Stargate.sendMessage(player, Stargate.getString("createPersonal"));
            } else {
                Stargate.debug("createPortal", "Player does not have access to network");
                deny = true;
                denyMsg = Stargate.getString("createNetDeny");
                //return null;
            }
        }
        
        // Check if the player can create this gate layout
        String gateName = gate.getFilename().toString();
        gateName = gateName.substring(0, gateName.indexOf('.'));
        if (!deny && !Stargate.canCreateGate(player, gateName)) {
            Stargate.debug("createPortal", "Player does not have access to gate layout");
            deny = true;
            denyMsg = Stargate.getString("createGateDeny");
        }
        
        // Check if the user can create gates to this world.
        if (!bungee && !deny && destName.length() > 0) {
            Portal p = Portal.getByName(destName, network);
            if (p != null) {
                String world = p.getWorld().getName();
                if (!Stargate.canAccessWorld(player, world)) {
                    Stargate.debug("canCreate", "Player does not have access to destination world");
                    deny = true;
                    denyMsg = Stargate.getString("createWorldDeny");
                }
            }
        }
        
        // Bleh, gotta check to make sure none of this gate belongs to another gate. Boo slow.
        for (RelativeBlockVector v : gate.getBorder()) {
            Blox b = topleft.modRelative(v.getRight(), v.getDepth(), v.getDistance(), modX, 1, modZ);
            if (Portal.getByBlock(b.getBlock()) != null) {
                Stargate.debug("createPortal", "Gate conflicts with existing gate");
                Stargate.sendMessage(player, Stargate.getString("createConflict"));
                return null;
            }
        }
        
        Blox button = null;
        Portal portal = new Portal(topleft, modX, modZ, rotX, id, button, destName, name, false, network, gate, player.getUniqueId(), hidden, alwaysOn, priv, free, backwards, show, noNetwork, random, bungee);
        
        
        // Call StargateCreateEvent
        // <<REDO THIS>>
        StargateCreateEvent cEvent = new StargateCreateEvent(player, portal, Lists.transform(event.getText().lines().get(), Text::toPlain), deny, denyMsg);
        Sponge.getEventManager().post(cEvent);
        if (cEvent.isCancelled()) {
            return null;
        }
        if (cEvent.getDeny()) {
            Stargate.sendMessage(player, cEvent.getDenyReason());
            return null;
        }
        
        // Name & Network can be changed in the event, so do these checks here.
        if (portal.getName().length() < 1 || portal.getName().length() > 11) {
            Stargate.debug("createPortal", "Name length error");
            Stargate.sendMessage(player, Stargate.getString("createNameLength"));
            return null;
        }
        
        // Don't do network checks for bungee gates
        if (portal.isBungee()) {
            if (bungeePortals.get(portal.getName().toLowerCase()) != null) {
                Stargate.debug("createPortal::Bungee", "Gate Exists");
                Stargate.sendMessage(player, Stargate.getString("createExists"));
                return null;
            }
        } else { 
            if (getByName(portal.getName(), portal.getNetwork()) != null) {
                Stargate.debug("createPortal", "Name Error");
                Stargate.sendMessage(player,  Stargate.getString("createExists"));
                return null;
            }
            
            // Check if there are too many gates in this network
            ArrayList<String> netList = allPortalsNet.get(portal.getNetwork().toLowerCase());
            if (Stargate.config.portal.maxGates > 0 && netList != null && netList.size() >= Stargate.config.portal.maxGates) {
                Stargate.sendMessage(player, Stargate.getString("createFull"));
                return null;
            }
        }
        
        /*
        if (cost.compareTo(BigDecimal.ZERO) > 0) {
            if (!Stargate.chargePlayer(player, (String) null, cost)) {
                String inFundMsg = Stargate.getString("ecoInFunds");
                inFundMsg = Stargate.replaceVars(inFundMsg, new String[] {"%cost%", "%portal%"}, new String[] {TextSerializers.FORMATTING_CODE.serialize(iConomyHandler.format(cost)), name});
                Stargate.sendMessage(player, inFundMsg);
                Stargate.debug("createPortal", "Insufficient Funds");
                return null;
            }
            String deductMsg = Stargate.getString("ecoDeduct");
            deductMsg = Stargate.replaceVars(deductMsg, new String[] {"%cost%", "%portal%"}, new String[] {TextSerializers.FORMATTING_CODE.serialize(iConomyHandler.format(cost)), name});
            Stargate.sendMessage(player, deductMsg, false);
        }
        */
        
        // No button on an always-open gate.
        if (!alwaysOn) {
            button = topleft.modRelative(buttonVector.getRight(), buttonVector.getDepth(), buttonVector.getDistance() + 1, modX, 1, modZ);
            button.setData(BlockTypes.STONE_BUTTON.getDefaultState().with(Keys.DIRECTION, facing).get());
            portal.setButton(button);
        }
        
        portal.register();
        portal.drawSign();
        // Open always on gate
        if (portal.isRandom() || portal.isBungee()) {
            portal.open(true);
        } else if (portal.isAlwaysOn()) {
            Portal dest = Portal.getByName(destName, portal.getNetwork());
            if (dest != null) {
                portal.open(true);
                dest.drawSign();
            }
        // Set the inside of the gate to its closed material
        } else {
            for (Blox inside : portal.getEntrances()) {
                inside.setData(portal.getGate().getPortalBlockClosed());
            }
        }
        
        // Don't do network stuff for bungee gates
        if (!portal.isBungee()) {
            // Open any always on gate pointing at this gate
            for (String originName : allPortalsNet.get(portal.getNetwork().toLowerCase())) {
                Portal origin = Portal.getByName(originName, portal.getNetwork());
                if (origin == null) continue;
                if (!origin.getDestinationName().equalsIgnoreCase(portal.getName())) continue;
                if (!origin.isVerified()) continue;
                if (origin.isFixed()) origin.drawSign();
                if (origin.isAlwaysOn()) origin.open(true);
            }
        }

        saveAllGates(portal.getWorld());

        return portal;
    }

    public static Portal getByName(String name, String network) {
        if (!lookupNamesNet.containsKey(network.toLowerCase())) return null;
        return lookupNamesNet.get(network.toLowerCase()).get(name.toLowerCase());
        
    }

    public static Portal getByEntrance(Location<World> block) {
        return lookupEntrances.get(new Blox(block));
    }
    
    public static Portal getByControl(Location<World> block) {
        return lookupControls.get(new Blox(block));
    }

    public static Portal getByBlock(Location<World> block) {
        return lookupBlocks.get(new Blox(block));
    }
    
    public static Portal getBungeeGate(String name) {
        return bungeePortals.get(name.toLowerCase());
    }

    public static void saveAllGates(World world) {
        Path loc = Stargate.getSaveLocation().resolve(Paths.get(world.getUniqueId() + ".db"));

        try (BufferedWriter bw = Files.newBufferedWriter(loc)) {

            for (Portal portal : allPortals) {
                UUID worldId = portal.world.getUniqueId();
                if (!worldId.equals(world.getUniqueId())) continue;
                StringBuilder builder = new StringBuilder();
                Blox sign = new Blox(portal.id.getBlock());
                Blox button = portal.button;

                builder.append(portal.name);
                builder.append(':');
                builder.append(sign.toString());
                builder.append(':');
                builder.append((button != null) ? button.toString() : "");
                builder.append(':');
                builder.append(portal.modX);
                builder.append(':');
                builder.append(portal.modZ);
                builder.append(':');
                builder.append(portal.rotX);
                builder.append(':');
                builder.append(portal.topLeft.toString());
                builder.append(':');
                builder.append(portal.gate.getFilename());
                builder.append(':');
                builder.append(portal.isFixed() ? portal.getDestinationName() : "");
                builder.append(':');
                builder.append(portal.getNetwork());
                builder.append(':');
                builder.append(portal.getOwner());
                builder.append(':');
                builder.append(portal.isHidden());
                builder.append(':');
                builder.append(portal.isAlwaysOn());
                builder.append(':');
                builder.append(portal.isPrivate());
                builder.append(':');
                builder.append(portal.world.getName());
                builder.append(':');
                builder.append(portal.isFree());
                builder.append(':');
                builder.append(portal.isBackwards());
                builder.append(':');
                builder.append(portal.isShown());
                builder.append(':');
                builder.append(portal.isNoNetwork());
                builder.append(':');
                builder.append(portal.isRandom());
                builder.append(':');
                builder.append(portal.isBungee());
                
                bw.append(builder.toString());
                bw.newLine();
            }
        } catch (Exception e) {
            Stargate.log.error("Exception while writing stargates to " + loc + ": " + e);
        }
    }
    
    public static void clearGates() {
        lookupBlocks.clear();
        lookupNamesNet.clear();
        lookupEntrances.clear();
        lookupControls.clear();
        allPortals.clear();
        allPortalsNet.clear();
    }

    public static void loadAllGates(World world) {
        Path location = Stargate.getSaveLocation();
        
        Path db = location.resolve(Paths.get(world.getUniqueId() + ".db"));
        if (!Files.exists(db)) {
            db = location.resolve(Paths.get(world.getName() + ".db"));
        }
        
        if (Files.exists(db)) {
            int l = 0;
            int portalCount = 0;
            try {
                Scanner scanner = new Scanner(db);
                while (scanner.hasNextLine()) {
                    l++;
                    String line = scanner.nextLine().trim();
                    if (line.startsWith("#") || line.isEmpty()) {
                        continue;
                    }
                    String[] split = line.split(":");
                    if (split.length < 8) {
                        Stargate.log.info("Invalid line - " + l);
                        continue;
                    }
                    String name = split[0];
                    Blox sign = new Blox(world, split[1]);
                    if (!(sign.getBlock().get(Keys.SIGN_LINES).isPresent())) {
                        Stargate.log.info("Sign on line " + l + " doesn't exist. BlockType = " + sign.getBlock().getBlockType().getId());
                        continue;
                    }
                    Blox button = (split[2].length() > 0) ? new Blox(world, split[2]) : null;
                    int modX = Integer.parseInt(split[3]);
                    int modZ = Integer.parseInt(split[4]);
                    float rotX = Float.parseFloat(split[5]);
                    Blox topLeft = new Blox(world, split[6]);
                    Gate gate = (split[7].contains(";")) ? Gate.getGateByName("nethergate.gate") : Gate.getGateByName(split[7]);
                    if (gate == null) {
                        Stargate.log.info("Gate layout on line " + l + " does not exist [" + split[7] + "]");
                        continue;
                    }

                    String dest = (split.length > 8) ? split[8] : "";
                    String network = (split.length > 9) ? split[9] : Stargate.getDefaultNetwork();
                    if (network.isEmpty()) network = Stargate.getDefaultNetwork();
                    String owner = (split.length > 10) ? split[10] : "";
                    boolean hidden = (split.length > 11) ? split[11].equalsIgnoreCase("true") : false;
                    boolean alwaysOn = (split.length > 12) ? split[12].equalsIgnoreCase("true") : false;
                    boolean priv = (split.length > 13) ? split[13].equalsIgnoreCase("true") : false;
                    boolean free = (split.length > 15) ? split[15].equalsIgnoreCase("true") : false;
                    boolean backwards = (split.length > 16) ? split[16].equalsIgnoreCase("true") : false;
                    boolean show = (split.length > 17) ? split[17].equalsIgnoreCase("true") : false;
                    boolean noNetwork = (split.length > 18) ? split[18].equalsIgnoreCase("true") : false;
                    boolean random = (split.length > 19) ? split[19].equalsIgnoreCase("true") : false;
                    boolean bungee = (split.length > 20) ? split[20].equalsIgnoreCase("true") : false;
                    try {
                        UUID id = UUID.fromString(owner);
                        Portal portal = new Portal(topLeft, modX, modZ, rotX, sign, button, dest, name, false, network, gate, id, hidden, alwaysOn, priv, free, backwards, show, noNetwork, random, bungee);
                        portal.register();
                        portal.close(true);
                    } catch (IllegalArgumentException e) {
                        String network_ = network;
                        Sponge.getServer().getGameProfileManager().get(owner).whenComplete((profile, t) -> Task.builder().execute(() -> {
                            UUID id = t == null && profile != null ? profile.getUniqueId() : defaultUUID;
                            Portal portal = new Portal(topLeft, modX, modZ, rotX, sign, button, dest, name, false, network_, gate, id, hidden, alwaysOn, priv, free, backwards, show, noNetwork, random, bungee);
                            portal.register();
                            portal.close(true);
                        }).submit(Stargate.stargate));
                    }

                }
                scanner.close();
                
                // Open any always-on gates. Do this here as it should be more efficient than in the loop.
                int OpenCount = 0;
                for (Iterator<Portal> iter = allPortals.iterator(); iter.hasNext(); ) {
                    Portal portal = iter.next();
                    if (portal == null) continue;

                    // Verify portal integrity/register portal
                    if (!portal.wasVerified()) {
                        if (!portal.isVerified() || !portal.checkIntegrity()) {
                            // DEBUG
                            for (RelativeBlockVector control : portal.getGate().getControls()) {
                                if (!portal.getBlockAt(control).getData().equals(portal.getGate().getControlBlock())) {
                                    Stargate.debug("loadAllGates", "Control Block Type == " + portal.getBlockAt(control).getData().getId());
                                }
                            }
                            portal.unregister(false, true);
                            iter.remove();
                            Stargate.log.info("Destroying stargate at " + portal.toString());
                            continue;
                        } else {
                            portal.drawSign();
                            portalCount++;
                        }
                    }

                    if (!portal.isFixed()) continue;
                    
                    if (Stargate.enableBungee && portal.isBungee()) {
                        OpenCount++;
                        portal.open(true);
                        portal.drawSign();
                        continue;
                    }
                    
                    Portal dest = portal.getDestination();
                    if (dest != null) {
                        if (portal.isAlwaysOn()) {
                            portal.open(true);
                            OpenCount++;
                        }
                        portal.drawSign();
                        dest.drawSign();
                    }
                }
                Stargate.log.info("{" + world.getName() + "} Loaded " + portalCount + " stargates with " + OpenCount + " set as always-on");
            } catch (Exception e) {
                Stargate.log.error("Exception while reading stargates from " + db.getFileName() + ": " + l);
                e.printStackTrace();
            }
        } else {
            Stargate.log.info("{" + world.getName() + "} No stargates for world ");
        }
    }
    
    public static void closeAllGates() {
        Stargate.log.info("Closing all stargates.");
        for (Portal p : allPortals) {
            if (p == null) continue;
            p.close(true);
        }
    }

    public static String filterName(String input) {
        return input.replaceAll("[|:#]", "").trim();
    }
    
    @Override
    public String toString() {
        return String.format("Portal [id=%s, network=%s name=%s, type=%s]", id, network, name, gate.getFilename());
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((network == null) ? 0 : network.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Portal other = (Portal) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equalsIgnoreCase(other.name))
            return false;
        if (network == null) {
            if (other.network != null)
                return false;
        } else if (!network.equalsIgnoreCase(other.network))
            return false;
        return true;
    }
}
