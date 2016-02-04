package com.elmakers.mine.bukkit.action.builtin;

import com.elmakers.mine.bukkit.action.ActionHandler;
import com.elmakers.mine.bukkit.action.CompoundAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.effect.EffectPlay;
import com.elmakers.mine.bukkit.api.effect.EffectPlayer;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.api.spell.TargetType;
import com.elmakers.mine.bukkit.math.VectorTransform;
import com.elmakers.mine.bukkit.spell.BaseSpell;
import com.elmakers.mine.bukkit.utility.CompatibilityUtils;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.Target;
import com.elmakers.mine.bukkit.utility.Targeting;
import de.slikey.effectlib.util.DynamicLocation;
import de.slikey.effectlib.util.VectorUtils;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

public class CustomProjectileAction extends CompoundAction
{
    private int interval;
    private int lifetime;
    private int attachDuration;
    private int range;
    private double speed;
    private VectorTransform velocityTransform;
    private double spread;
    private double movementSpread;
    private double maxSpread;
    private int startDistance;
    private String projectileEffectKey;
    private String hitEffectKey;
    private String missEffectKey;
    private String headshotEffectKey;
    private String tickEffectKey;
    private double gravity;
    private double drag;
    private double tickSize;
    private boolean reorient;
    private boolean useWandLocation;
    private boolean useEyeLocation;
    private boolean useTargetLocation;
    private boolean trackEntity;
    private double trackCursorRange;
    private double trackSpeed;
    private int targetSelfTimeout;
    private boolean breaksBlocks;
    private double targetBreakables;
    private int targetBreakableSize;
    private boolean bypassBackfire;
    private boolean reverseDirection;
    private int blockHitLimit;
    private int entityHitLimit;
    private int pitchMin;
    private int pitchMax;
    private boolean ignoreTargeting;

    protected Targeting targeting;
    protected Location launchLocation;
    protected long flightTime;
    protected double distanceTravelled;
    protected double distanceTravelledThisTick;
    protected Vector velocity = null;

    private double effectDistanceTravelled;
    private boolean hasTickActions;
    private boolean hasTickEffects;
    private boolean hasStepEffects;
    private boolean hasBlockMissEffects;
    private boolean hasPreHitEffects;
    private Vector attachedOffset;
    private long attachedDeadline;
    private int entityHitCount;
    private int blockHitCount;
    private boolean missed;
    private long lastUpdate;
    private long nextUpdate;
    private long deadline;
    private long targetSelfDeadline;
    private DynamicLocation effectLocation = null;
    private Collection<EffectPlay> activeProjectileEffects;
    
    private boolean returnToCaster;
    private double returnDistanceSensitivity;
    private Vector returnRelativeOffset = null;
    private Vector returnOffset = null;
    private double returnSpeed;
    private double startingSpeed;
    private boolean resetTimeOnReturn;
    private boolean projectileFollowPlayer;
    private Location magePreviousLocation;
    

    @Override
    public void initialize(Spell spell, ConfigurationSection parameters) {
        super.initialize(spell, parameters);
        targeting = new Targeting();
        if (parameters.isConfigurationSection("velocity_transform")) {
            velocityTransform = new VectorTransform(ConfigurationUtils.getConfigurationSection(parameters, "velocity_transform"));
        } else {
            velocityTransform = null;
        }
    }

    @Override
    public void finish(CastContext context) {
        super.finish(context);
        finishEffects();
    }

    @Override
    protected void addHandlers(Spell spell, ConfigurationSection parameters) {
        addHandler(spell, "actions");
        addHandler(spell, "headshot");
        addHandler(spell, "miss");
        addHandler(spell, "tick");
    }

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        super.prepare(context, parameters);
        targeting.processParameters(parameters);
        interval = parameters.getInt("interval", 30);
        lifetime = parameters.getInt("lifetime", 8000);
        attachDuration = parameters.getInt("attach_duration", 0);
        reverseDirection = parameters.getBoolean("reverse", false);
        ignoreTargeting = parameters.getBoolean("ignore_targeting", false);
        startDistance = parameters.getInt("start", 0);
        range = parameters.getInt("range", 0);
        projectileEffectKey = parameters.getString("projectile_effects", "projectile");
        headshotEffectKey = parameters.getString("headshot_effects", "headshot");
        hitEffectKey = parameters.getString("hit_effects", "hit");
        missEffectKey = parameters.getString("miss_effects", "miss");
        tickEffectKey = parameters.getString("tick_effects", "tick");
        gravity = parameters.getDouble("gravity", 0);
        drag = parameters.getDouble("drag", 0);
        tickSize = parameters.getDouble("tick_size", 0.5);
        reorient = parameters.getBoolean("reorient", false);
        useWandLocation = parameters.getBoolean("use_wand_location", true);
        useEyeLocation = parameters.getBoolean("use_eye_location", true);
        useTargetLocation = parameters.getBoolean("use_target_location", true);
        trackEntity = parameters.getBoolean("track_target", false);
        targetSelfTimeout = parameters.getInt("target_self_timeout", 0);
        breaksBlocks = parameters.getBoolean("break_blocks", true);
        targetBreakables = parameters.getDouble("target_breakables", 1);
        targetBreakableSize = parameters.getInt("breakable_size", 1);
        bypassBackfire = parameters.getBoolean("bypass_backfire", false);
        spread = parameters.getDouble("spread", 0);
        maxSpread = parameters.getDouble("spread_max", 0);
        movementSpread = parameters.getDouble("spread_movement", 0);
        trackCursorRange = parameters.getDouble("track_range", 0);
        trackSpeed = parameters.getDouble("track_speed", 0);
        int hitLimit = parameters.getInt("hit_count", 1);
        entityHitLimit = parameters.getInt("entity_hit_count", hitLimit);
        blockHitLimit = parameters.getInt("block_hit_count", hitLimit);
        pitchMin = parameters.getInt("pitch_min", 90);
        pitchMax = parameters.getInt("pitch_max", -90);
        
        returnToCaster = parameters.getBoolean("return_to_caster", false);
        returnDistanceSensitivity = parameters.getDouble("return_distance_sensitivity", 0.5);
        returnSpeed = parameters.getDouble("return_speed");
        returnOffset = ConfigurationUtils.getVector(parameters, "return_offset");
        returnRelativeOffset = ConfigurationUtils.getVector(parameters, "return_relative_offset");
        resetTimeOnReturn = parameters.getBoolean("reset_time_on_return", true);
        
        projectileFollowPlayer = parameters.getBoolean("projectile_follow_player", false);
        
        range *= context.getMage().getRangeMultiplier();

        speed = parameters.getDouble("speed", 1);
        speed = parameters.getDouble("velocity", speed * 20);
        
        if (returnToCaster)
        {
            startingSpeed = speed;
            speed = returnSpeed;
        }
        
        // Some parameter tweaks to make sure things are sane
        TargetType targetType = targeting.getTargetType();
        if (targetType == TargetType.NONE) {
            targeting.setTargetType(TargetType.OTHER);
        }

        // Flags to optimize FX calls
        hasTickEffects = context.getEffects(tickEffectKey).size() > 0;
        hasBlockMissEffects = context.getEffects("blockmiss").size() > 0;
        hasStepEffects = context.getEffects("step").size() > 0;
        hasPreHitEffects = context.getEffects("prehit").size() > 0;

        ActionHandler handler = getHandler("tick");
        hasTickActions = handler != null && handler.size() > 0;
    }

    @Override
    public boolean next(CastContext context) {
        return !missed && entityHitCount < entityHitLimit && blockHitCount < blockHitLimit;
    }

    @Override
    public void reset(CastContext context)
    {
        super.reset(context);
        targeting.reset();
        long now = System.currentTimeMillis();
        nextUpdate = 0;
        distanceTravelled = 0;
        lastUpdate = 0;
        deadline =  now + lifetime;
        targetSelfDeadline = targetSelfTimeout > 0 ? now + targetSelfTimeout : 0;
        effectLocation = null;
        velocity = null;
        activeProjectileEffects = null;
        entityHitCount = 0;
        blockHitCount = 0;
        attachedDeadline = 0;
        attachedOffset = null;
        missed = false;
    }

    @Override
    public SpellResult start(CastContext context) {
        if (movementSpread > 0) {
            Entity sourceEntity = context.getEntity();
            double entitySpeed = sourceEntity != null ? sourceEntity.getVelocity().lengthSquared() : 0;
            if (entitySpeed > 0.01) {
                spread += Math.min(movementSpread * entitySpeed, maxSpread);
                context.getMage().sendDebugMessage(ChatColor.DARK_RED + " Applying spread of " + ChatColor.RED + spread
                    + ChatColor.DARK_RED + " from speed^2 of " + ChatColor.GOLD + entitySpeed, 3);
            }
        }
        return SpellResult.CAST;
    }

	@Override
	public SpellResult step(CastContext context) {
        long now = System.currentTimeMillis();
        if (now < nextUpdate)
        {
            return SpellResult.PENDING;
        }
        if (attachedDeadline > 0)
        {
            Entity targetEntity = actionContext.getTargetEntity();
            if (attachedOffset != null && targetEntity != null)
            {
                Location targetLocation = targetEntity.getLocation();
                targetLocation.add(attachedOffset);
                actionContext.setTargetLocation(targetLocation);
                if (effectLocation != null)
                {
                    effectLocation.updateFrom(targetLocation);
                }
            }
            if (now > attachedDeadline)
            {
                return finishAttach();
            }
            return SpellResult.PENDING;
        }
        if (now > deadline)
        {
            return miss();
        }
        if (targetSelfDeadline > 0 && now > targetSelfDeadline)
        {
            targetSelfDeadline = 0;
            context.setTargetsCaster(true);
        }
        nextUpdate = now + interval;

        // Check for initialization required
        // TODO: Move this to start()?
        Location projectileLocation = null;
        if (velocity == null)
        {
            Location targetLocation = context.getTargetLocation();
            if (useWandLocation) {
                projectileLocation = context.getWandLocation().clone();
            } else if (useEyeLocation) {
                projectileLocation = context.getEyeLocation().clone();
            } else {
                projectileLocation = context.getLocation().clone();
            }
            
            /* This feels confusing however...
             * Looking straight down in Minecraft gives a pitch of 90
             * While looking straight up is a pitch of -90
             * We don't want to normalize these values as other functions need the non-normalize numbers.
             * So if the projectile pitch value is found to be higher or lower than the min or max, it's set to the min or max respectively
             */
            if (pitchMin < projectileLocation.getPitch())
            {
                projectileLocation.setPitch(pitchMin);
            } 
            else if (pitchMax > projectileLocation.getPitch())
            {
                projectileLocation.setPitch(pitchMax);
            }
            launchLocation = projectileLocation.clone();

            if (targetLocation != null && !reorient && useTargetLocation) {
                velocity = targetLocation.toVector().subtract(projectileLocation.toVector()).normalize();
                launchLocation.setDirection(velocity);
            } else {
                velocity = context.getDirection().clone().normalize();
            }

            if (spread > 0) {
                Random random = context.getRandom();
                velocity.setX(velocity.getX() + (random.nextDouble() * spread - spread / 2));
                velocity.setY(velocity.getY() + (random.nextDouble() * spread - spread / 2));
                velocity.setZ(velocity.getZ() + (random.nextDouble() * spread - spread / 2));
                velocity.normalize();
            }

            if (startDistance != 0) {
                projectileLocation.add(velocity.clone().multiply(startDistance));
            }

            if (reverseDirection) {
                velocity = velocity.multiply(-1);
            }

            projectileLocation.setDirection(velocity);
            actionContext.setTargetLocation(projectileLocation);
            actionContext.setTargetEntity(null);
            actionContext.setDirection(velocity);

            // Start up projectile FX
            Collection<EffectPlayer> projectileEffects = context.getEffects(projectileEffectKey);
            for (EffectPlayer apiEffectPlayer : projectileEffects)
            {
                if (effectLocation == null) {
                    effectLocation = new DynamicLocation(projectileLocation);
                    effectLocation.setDirection(velocity);
                }
                if (activeProjectileEffects == null) {
                    activeProjectileEffects = new ArrayList<EffectPlay>();
                }
                // Hrm- this is ugly, but I don't want the API to depend on EffectLib.
                if (apiEffectPlayer instanceof com.elmakers.mine.bukkit.effect.EffectPlayer)
                {
                    com.elmakers.mine.bukkit.effect.EffectPlayer effectPlayer = (com.elmakers.mine.bukkit.effect.EffectPlayer)apiEffectPlayer;
                    effectPlayer.setEffectPlayList(activeProjectileEffects);
                    effectPlayer.startEffects(effectLocation, null);
                }
            }
        }
        else
        {
            projectileLocation = actionContext.getTargetLocation();
            if (effectLocation != null)
            {
                effectLocation.updateFrom(projectileLocation);
                effectLocation.setDirection(velocity);
            }
        }

        // Advance position
        // We default to 50 ms travel time (one tick) for the first iteration.
        long delta = lastUpdate > 0 ? now - lastUpdate : 50;
        lastUpdate = now;
        flightTime += delta;

        // Apply gravity, drag or other velocity modifiers
        Vector targetVelocity = null;
        
        if (returnToCaster) 
        {
            Vector targetLocation = context.getMage().getLocation().toVector();
            
            if (returnOffset != null) 
            {
                targetLocation.add(returnOffset);
            }
            
            if (returnRelativeOffset != null)
            {
                Vector offset = VectorUtils.rotateVector(returnRelativeOffset, context.getMage().getEyeLocation());
                targetLocation.add(offset);
            }
            
            Vector projectileLocationOffset = targetLocation.subtract(projectileLocation.toVector());
            double distance = projectileLocationOffset.clone().length();
            
            if (distance < returnDistanceSensitivity) {
                velocity = new Vector(0,0,0);
                speed = startingSpeed;
                returnToCaster = false;
                launchLocation = context.getMage().getEyeLocation();
                if (resetTimeOnReturn)
                {
                    flightTime = 0;
                }
            }
            else
            {
                velocity = projectileLocationOffset.normalize();
            }
        }
        else 
        {
            if (trackEntity)
            {
                Entity targetEntity = context.getTargetEntity();
                if (targetEntity != null && targetEntity.isValid() && context.canTarget(targetEntity))
                {
                    Location targetLocation = targetEntity instanceof LivingEntity ?
                            ((LivingEntity)targetEntity).getEyeLocation() : targetEntity.getLocation();
                    targetVelocity = targetLocation.toVector().subtract(projectileLocation.toVector()).normalize();
                }
            }
            else if (trackCursorRange > 0)
            {
                /* We need to first find out where the player is looking and multiply it by how far the player wants the whip to extend
            	 * Finally after all that, we adjust the velocity of the projectile to go towards the cursor point
            	 */
                Vector playerCursor = context.getDirection().clone().normalize().multiply(trackCursorRange);
                playerCursor = context.getEyeLocation().toVector().add(playerCursor);
                targetVelocity = playerCursor.subtract(projectileLocation.toVector()).normalize();
            }
            else if (reorient)
            {
                targetVelocity = context.getMage().getDirection().clone().normalize();
            }
            else
            {
                if (gravity > 0) {
                    velocity.setY(velocity.getY() - gravity * delta / 50).normalize();
                }
                if (drag > 0) {
                    speed -= drag * delta / 50;
                    if (speed <= 0) {
                        return miss();
                    }
                }
            }
    
            if (targetVelocity != null)
            {
                if (trackSpeed > 0)
                {
                    double steerDistanceSquared = trackSpeed * trackSpeed;
                    double distanceSquared = targetVelocity.distanceSquared(velocity);
                    if (distanceSquared <= steerDistanceSquared) {
                        velocity = targetVelocity;
                    } else {
                        Vector targetDirection = targetVelocity.subtract(velocity).normalize().multiply(steerDistanceSquared);
                        velocity.add(targetDirection);
                    }
                }
                else
                {
                    velocity = targetVelocity;
                }
                launchLocation.setDirection(velocity);
            }
    
            if (velocityTransform != null)
            {
                targetVelocity = velocityTransform.get(launchLocation, (double)flightTime / 1000);
                // This is expensive, but necessary for variable speed to work properly
                // with targeting and range-checking
                if (targetVelocity != null) {
                    speed = targetVelocity.length();
                    if (speed > 0) {
                        targetVelocity.normalize();
                    } else {
                        targetVelocity.setX(1);
                    }
                    velocity = targetVelocity;
                }
            }
        }

        if (projectileFollowPlayer) 
        {
            if (magePreviousLocation != null) 
            {
                Location mageCurrentLocation = context.getMage().getLocation();
                Vector velocityOffset = mageCurrentLocation.toVector().subtract(magePreviousLocation.toVector());
                
                magePreviousLocation = mageCurrentLocation;
                velocity = velocity.add(velocityOffset);
            } 
            else
            {
                magePreviousLocation = context.getMage().getLocation();
            }
        }
        
        projectileLocation.setDirection(velocity);

        // Advance targeting to find Entity or Block
        distanceTravelledThisTick = speed * delta / 1000;
        if (range > 0) {
            distanceTravelledThisTick = Math.min(distanceTravelledThisTick, range - distanceTravelled);
        }
        context.addWork((int)Math.ceil(distanceTravelledThisTick));
        Location targetLocation;
        Targeting.TargetingResult targetingResult = Targeting.TargetingResult.MISS;
        Target target = null;
        if (!ignoreTargeting) {
            targeting.start(projectileLocation);
            target = targeting.target(actionContext, distanceTravelledThisTick);
            targetingResult = targeting.getResult();
        }
        if (targetingResult == Targeting.TargetingResult.MISS) {
            if (hasBlockMissEffects && target != null) {
                actionContext.setTargetLocation(target.getLocation());
                actionContext.playEffects("blockmiss");
            }

            targetLocation = projectileLocation.clone().add(velocity.clone().multiply(distanceTravelledThisTick));
            context.getMage().sendDebugMessage(ChatColor.DARK_BLUE + "Projectile miss: " + ChatColor.DARK_PURPLE
                    + " at " + targetLocation.getBlock().getType() + " : " + targetLocation.toVector() + " from range of " + distanceTravelledThisTick + " over time " + delta, 7);
        } else {
            if (hasPreHitEffects) {
                actionContext.playEffects("prehit");
            }
            targetLocation = target.getLocation();
            // Debugging
            if (targetLocation == null) {
                targetLocation = projectileLocation;
                context.getLogger().warning("Targeting hit, with no target location: " + targetingResult + " with " + targeting.getTargetType() + " from " + context.getSpell().getName());
            }

            context.getMage().sendDebugMessage(ChatColor.BLUE + "Projectile hit: " + ChatColor.LIGHT_PURPLE + targetingResult.name().toLowerCase()
                + ChatColor.BLUE + " at " + ChatColor.GOLD + targetLocation.getBlock().getType()
                + ChatColor.BLUE + " from " + ChatColor.GRAY + projectileLocation.getBlock() + ChatColor.BLUE + " to "
                + ChatColor.GRAY + targetLocation.toVector() + ChatColor.BLUE
                + " from range of " + ChatColor.GOLD + distanceTravelledThisTick + ChatColor.BLUE + " over time " + ChatColor.DARK_PURPLE + delta, 4);
            distanceTravelledThisTick = targetLocation.distance(projectileLocation);
        }
        distanceTravelled += distanceTravelledThisTick;
        effectDistanceTravelled += distanceTravelledThisTick;

        // Max Height check
        int y = targetLocation.getBlockY();
        boolean maxHeight = y >= targetLocation.getWorld().getMaxHeight();
        boolean minHeight = y <= 0;

        if (maxHeight) {
            targetLocation.setY(targetLocation.getWorld().getMaxHeight());
        } else if (minHeight) {
            targetLocation.setY(0);
        }

        if (hasTickEffects && effectDistanceTravelled > tickSize) {
            // Sane limit here
            Vector speedVector = velocity.clone().multiply(tickSize);
            for (int i = 0; i < 256; i++) {
                actionContext.setTargetLocation(projectileLocation);
                actionContext.playEffects(tickEffectKey);

                projectileLocation.add(speedVector);

                effectDistanceTravelled -= tickSize;
                if (effectDistanceTravelled < tickSize) break;
            }
        }

        actionContext.setTargetLocation(targetLocation);
        if (target != null) {
            actionContext.setTargetEntity(target.getEntity());
        }

        if (hasStepEffects) {
            actionContext.playEffects("step");
        }

        if (maxHeight || minHeight) {
            return miss();
        }

        if (range > 0 && distanceTravelled >= range) {
            return miss();
        }

        Block block = targetLocation.getBlock();
        if (!block.getChunk().isLoaded()) {
            return miss();
        }

        if (targetingResult == Targeting.TargetingResult.BLOCK) {
            return hitBlock(block);
        } else if (targetingResult == Targeting.TargetingResult.ENTITY) {
            entityHitCount++;
            Entity hitEntity = target.getEntity();
            if (hitEntity != null && entityHitLimit > 1) {
                targeting.ignoreEntity(hitEntity);
            }
            if (hitEntity != null) {
                actionContext.playEffects("hit_entity");

                if (hasActions("headshot") && CompatibilityUtils.isHeadshot(hitEntity, targetLocation)) {
                    actionContext.getMage().sendDebugMessage(ChatColor.GOLD + "   Projectile headshot", 3);
                    return headshot();
                }
            }
            return hit();
        }

        if (hasTickActions) {
            return startActions("tick");
        }

		return SpellResult.PENDING;
	}

    protected SpellResult hitBlock(Block block) {
        boolean continueProjectile = false;
        if (!bypassBackfire && actionContext.isReflective(block)) {
            double reflective = actionContext.getReflective(block);
            if (actionContext.getRandom().nextDouble() < reflective) {
                trackEntity = false;
                reorient = false;
                distanceTravelled = 0;
                actionContext.setTargetsCaster(true);

                // Calculate angle of reflection
                Location targetLocation = actionContext.getTargetLocation();
                Vector normal = CompatibilityUtils.getNormal(block, targetLocation);
                velocity.multiply(-1);
                velocity = velocity.subtract(normal.multiply(2 * velocity.dot(normal))).normalize();
                velocity.multiply(-1);

                // Offset position slightly to avoid hitting again
                actionContext.setTargetLocation(targetLocation.add(velocity.clone().multiply(0.05)));
                // actionContext.setTargetLocation(targetLocation.add(normal.normalize().multiply(2)));

                actionContext.getMage().sendDebugMessage(ChatColor.AQUA + "Projectile reflected: " + ChatColor.LIGHT_PURPLE + " at "
                        + ChatColor.GRAY + block + ChatColor.AQUA + " with normal vector of " + ChatColor.LIGHT_PURPLE + normal, 4);

                actionContext.playEffects("reflect");
                continueProjectile = true;
            }
        }
        if (targetBreakables > 0 && breaksBlocks && actionContext.isBreakable(block)) {
            targetBreakables -= targeting.breakBlock(actionContext, block, Math.min(targetBreakableSize, targetBreakables));
            if (targetBreakables > 0) {
                continueProjectile = true;
            }
        }

        actionContext.playEffects("hit_block");

        if (!continueProjectile) {
            blockHitCount++;
        }
        return continueProjectile ? SpellResult.PENDING : hit();
    }

    protected SpellResult finishAttach() {
        attachedDeadline = 0;
        finishEffects();
        return startActions();
    }

    protected void finishEffects() {
        if (activeProjectileEffects != null) {
            for (EffectPlay play : activeProjectileEffects) {
                play.cancel();
            }
        }
    }

    protected SpellResult attach() {
        attachedDeadline = System.currentTimeMillis() + attachDuration;
        Entity targetEntity = actionContext == null ? null : actionContext.getTargetEntity();
        Location targetLocation = actionContext == null ? null : actionContext.getTargetLocation();
        if (targetEntity != null && targetLocation != null)
        {
            attachedOffset = targetLocation.toVector().subtract(targetEntity.getLocation().toVector());
        }
        actionContext.playEffects(hitEffectKey);
        return SpellResult.PENDING;
    }

    protected SpellResult headshot() {
        finishEffects();
        if (actionContext == null) {
            return SpellResult.NO_ACTION;
        }
        actionContext.playEffects(headshotEffectKey);
        return startActions("headshot");
    }

    protected SpellResult hit() {
        if (attachDuration > 0) {
            return attach();
        }
        finishEffects();
        if (actionContext == null) {
            return SpellResult.NO_ACTION;
        }
        actionContext.playEffects(hitEffectKey);
        return startActions();
    }

    protected SpellResult miss() {
        missed = true;
        finishEffects();
        if (actionContext == null) {
            return SpellResult.NO_ACTION;
        }
        actionContext.playEffects(missEffectKey);
        return startActions("miss");
    }

    @Override
    public void getParameterNames(Spell spell, Collection<String> parameters)
    {
        super.getParameterNames(spell, parameters);
        parameters.add("interval");
        parameters.add("lifetime");
        parameters.add("speed");
        parameters.add("start");
        parameters.add("gravity");
        parameters.add("drag");
        parameters.add("target_entities");
        parameters.add("track_target");
        parameters.add("spread");
    }

    @Override
    public void getParameterOptions(Spell spell, String parameterKey, Collection<String> examples)
    {
        super.getParameterOptions(spell, parameterKey, examples);

        if (parameterKey.equals("speed") || parameterKey.equals("lifetime") ||
            parameterKey.equals("interval") || parameterKey.equals("start") || parameterKey.equals("size") ||
            parameterKey.equals("gravity") || parameterKey.equals("drag") || parameterKey.equals("tick_size") ||
            parameterKey.equals("spread")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_SIZES));
        } else if (parameterKey.equals("target_entities") || parameterKey.equals("track_target")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_BOOLEANS));
        }
    }
}
